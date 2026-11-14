<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import { CameraOutlined, DeleteOutlined, SettingOutlined } from '@ant-design/icons-vue'
import GradeSettings from './GradeSettings.vue'
import {
  deleteGradePaperImage,
  fetchGradeEntry,
  fetchGradeExams,
  fetchGradeRules,
  publishGradeEntry,
  saveGradeEntry,
  updateGradeExamFullScore,
  uploadGradePaperImage,
  type GradeEntryData,
  type GradeEntryStudent,
  type GradeExamRow,
  type GradeRule,
  type GradeScoreRowPayload,
  type GradeScoreSavePayload,
} from '../api/client'

type EntryRow = Omit<GradeEntryStudent, 'partScores'> & { partScores: Record<string, number | null> }

const route = useRoute()

const exams = ref<GradeExamRow[]>([])
const rules = ref<GradeRule[]>([])
const examId = ref<number | null>(route.query.examId ? Number(route.query.examId) : null)
const classId = ref<number | null>(route.query.classId ? Number(route.query.classId) : null)
const entry = ref<GradeEntryData | null>(null)
const rows = ref<EntryRow[]>([])
const loading = ref(false)
const saving = ref(false)
const fillValue = ref<number | null>(null)
const fullScoreDraft = ref<number | null>(null)
const savingFullScore = ref(false)
const settingsOpen = ref(false)
const batchOpen = ref(false)
const batchText = ref('')
const batchTarget = ref('total')

const examSelectOptions = computed(() =>
  exams.value.map((exam) => ({
    label: `${exam.name}（${exam.type}${exam.status === '草稿' ? ' · 草稿' : ''}）`,
    value: exam.id,
  })),
)

const currentExam = computed(() => exams.value.find((exam) => exam.id === examId.value) ?? null)

const paperImages = computed(() => currentExam.value?.paperImages ?? [])

const classSelectOptions = computed(() => {
  const exam = currentExam.value
  if (!exam) return []
  return exam.classIds.map((id, index) => ({ label: exam.classNames[index] ?? `班级#${id}`, value: id }))
})

const readonly = computed(() => {
  const status = entry.value?.status
  return status === '已发布' || status === '已归档'
})

const usingParts = computed(() => (entry.value?.parts.length ?? 0) > 0)
const levelMode = computed(() => entry.value?.scoringMode === '等级')

const levelOptions = computed(() => {
  const names = rules.value.map((rule) => rule.name)
  const fallback = ['优秀', '良好', '合格', '待提高']
  return (names.length ? names : fallback).map((item) => ({ label: item, value: item }))
})

const batchTargetOptions = computed(() => {
  const parts = entry.value?.parts ?? []
  return [
    { label: parts.length ? '总分（自动汇总）' : '总分', value: 'total' },
    ...parts
      .filter((part) => part.id !== undefined)
      .map((part) => ({ label: `分项：${part.name}`, value: String(part.id) })),
  ]
})

const columns = computed(() => {
  const parts = entry.value?.parts ?? []
  const list: { title: string; dataIndex: string; width?: number; align?: 'left' | 'right' | 'center' }[] = [
    { title: '学员', dataIndex: 'studentName', width: 130 },
  ]
  parts.forEach((part) => {
    list.push({ title: `${part.name}（${part.fullScore}）`, dataIndex: `part-${part.id}`, width: 120 })
  })
  list.push({ title: '总分', dataIndex: 'totalScore', width: 100, align: 'right' })
  if (levelMode.value) {
    list.push({ title: '等级', dataIndex: 'level', width: 120 })
  }
  list.push({ title: '状态', dataIndex: 'status', width: 100 })
  if (readonly.value) {
    list.push({ title: '班级排名', dataIndex: 'rankClass', width: 90, align: 'center' })
  }
  list.push({ title: '评语 / 备注', dataIndex: 'remark', width: 200 })
  return list
})

const enteredCount = computed(() => rows.value.filter((row) => row.status !== '未考').length)

function totalOf(row: EntryRow) {
  const parts = entry.value?.parts ?? []
  if (!parts.length) {
    return row.totalScore === null || row.totalScore === undefined ? null : Number(row.totalScore)
  }
  let sum = 0
  let has = false
  parts.forEach((part) => {
    const value = row.partScores[String(part.id)]
    if (value !== null && value !== undefined && String(value) !== '') {
      sum += Number(value)
      has = true
    }
  })
  return has ? Math.round(sum * 100) / 100 : null
}

function onTotalChange(row: EntryRow, value: number | null) {
  row.totalScore = value
  if (value !== null && row.status === '未考') row.status = '正常'
}

function onPartChange(row: EntryRow, partId: number | undefined, value: number | null) {
  if (partId === undefined) return
  row.partScores[String(partId)] = value
  if (value !== null && row.status === '未考') row.status = '正常'
}

function onStatusChange(row: EntryRow, value: string) {
  if (value !== '正常') {
    row.totalScore = null
    row.level = null
    if (usingParts.value) {
      Object.keys(row.partScores).forEach((key) => {
        row.partScores[key] = null
      })
    }
  }
}

function applyFill() {
  if (fillValue.value === null) {
    message.warning('请先填写要批量填充的分数')
    return
  }
  let count = 0
  rows.value.forEach((row) => {
    if (row.status === '缺考' || row.status === '免考') return
    if (row.status === '未考') {
      row.status = '正常'
      row.totalScore = fillValue.value
      count++
    } else if (row.totalScore === null) {
      row.totalScore = fillValue.value
      count++
    }
  })
  message.success(count ? `已为 ${count} 名未录入学员填入 ${fillValue.value} 分` : '没有需要填充的学员')
}

function replaceExam(updated: GradeExamRow) {
  const index = exams.value.findIndex((item) => item.id === updated.id)
  if (index >= 0) exams.value[index] = updated
  else exams.value.push(updated)
}

async function saveFullScore() {
  const exam = currentExam.value
  if (!exam) return
  if (readonly.value) {
    message.warning('成绩已发布，请先在考试管理中解锁后再修改满分')
    return
  }
  const value = Number(fullScoreDraft.value)
  if (!Number.isFinite(value) || value <= 0) {
    message.warning('满分必须大于 0')
    return
  }
  if (Number(exam.fullScore) === value) {
    message.info('满分未变化')
    return
  }
  savingFullScore.value = true
  try {
    const updated = await updateGradeExamFullScore(exam.id, value)
    replaceExam(updated)
    if (entry.value) entry.value.fullScore = updated.fullScore
    fullScoreDraft.value = Number(updated.fullScore)
    message.success(`满分已更新为 ${updated.fullScore} 分`)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新满分失败')
  } finally {
    savingFullScore.value = false
  }
}

const uploadPaper: UploadProps['customRequest'] = async (option) => {
  const exam = currentExam.value
  if (!exam) return
  if (paperImages.value.length >= 9) {
    message.warning('每场考试最多上传 9 张试卷图片')
    option.onError?.(new Error('limit'))
    return
  }
  try {
    const updated = await uploadGradePaperImage(exam.id, option.file as File)
    replaceExam(updated)
    option.onSuccess?.(updated)
    message.success('试卷图片已上传')
  } catch (e) {
    const error = e instanceof Error ? e : new Error('上传试卷图片失败')
    option.onError?.(error)
    message.error(error.message)
  }
}

function removePaper(url: string) {
  const exam = currentExam.value
  if (!exam) return
  Modal.confirm({
    title: '删除试卷图片',
    content: '确认删除这张试卷图片？删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const updated = await deleteGradePaperImage(exam.id, url)
        replaceExam(updated)
        message.success('试卷图片已删除')
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除试卷图片失败')
      }
    },
  })
}

function parseBatchInput(text: string) {
  const result: { name: string; score: number }[] = []
  text.split(/\r?\n/).forEach((line) => {
    const trimmed = line.trim()
    if (!trimmed) return
    const match = trimmed.match(/^(.+?)[\s,，、\t]+(\d+(?:\.\d+)?)$/)
    if (!match) return
    result.push({ name: match[1].trim(), score: Number(match[2]) })
  })
  return result
}

function applyBatch() {
  const items = parseBatchInput(batchText.value)
  if (!items.length) {
    message.warning('未解析到有效数据，请按「姓名 分数」每行一条粘贴')
    return
  }
  if (levelMode.value && batchTarget.value === 'total') {
    message.warning('等级制考试请选择具体分项后批量录入')
    return
  }
  const target = batchTarget.value
  const part = entry.value?.parts.find((item) => String(item.id) === target)
  const limit =
    target === 'total' ? Number(entry.value?.fullScore ?? 100) : Number(part?.fullScore ?? 100)
  let matched = 0
  const missed: string[] = []
  const overflow: string[] = []
  items.forEach((item) => {
    const row = rows.value.find(
      (candidate) =>
        candidate.studentName === item.name ||
        (candidate.studentNo && candidate.studentNo === item.name),
    )
    if (!row) {
      missed.push(item.name)
      return
    }
    if (item.score > limit) {
      overflow.push(item.name)
      return
    }
    row.status = '正常'
    if (target === 'total') row.totalScore = item.score
    else row.partScores[target] = item.score
    matched++
  })
  batchOpen.value = false
  batchText.value = ''
  const tips = [`已批量录入 ${matched} 人${target === 'total' ? '总分' : `「${part?.name ?? '分项'}」`}`]
  if (missed.length) {
    tips.push(`未匹配 ${missed.length} 人：${missed.slice(0, 5).join('、')}${missed.length > 5 ? ' 等' : ''}`)
  }
  if (overflow.length) tips.push(`${overflow.length} 人分数超过上限已跳过`)
  message.success(tips.join('；'))
}

async function reloadAfterSettings() {
  await loadRules()
}

function openBatch() {
  batchTarget.value = 'total'
  batchOpen.value = true
}

async function loadExams() {
  try {
    exams.value = await fetchGradeExams()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载考试列表失败')
  }
}

async function loadRules() {
  try {
    rules.value = await fetchGradeRules()
  } catch {
    rules.value = []
  }
}

async function loadEntry() {
  if (!examId.value || !classId.value) {
    entry.value = null
    rows.value = []
    return
  }
  loading.value = true
  try {
    const data = await fetchGradeEntry(examId.value, classId.value)
    applyEntry(data)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载成绩失败')
  } finally {
    loading.value = false
  }
}

function applyEntry(data: GradeEntryData) {
  entry.value = data
  fullScoreDraft.value = Number(data.fullScore)
  rows.value = data.students.map((student) => {
    const partScores: Record<string, number | null> = {}
    data.parts.forEach((part) => {
      if (part.id !== undefined) {
        const value = student.partScores?.[String(part.id)]
        partScores[String(part.id)] = value === undefined || value === null ? null : Number(value)
      }
    })
    return { ...student, partScores }
  })
}

function onExamChange() {
  const exam = currentExam.value
  classId.value = exam?.classIds[0] ?? null
  loadEntry()
}

function buildPayload(): GradeScoreSavePayload | null {
  if (!examId.value || !classId.value || !entry.value) return null
  const parts = entry.value.parts
  const payloadRows: GradeScoreRowPayload[] = rows.value.map((row) => {
    const partScores: Record<string, number> = {}
    if (parts.length) {
      parts.forEach((part) => {
        if (part.id === undefined) return
        const value = row.partScores[String(part.id)]
        if (value !== null && value !== undefined && String(value) !== '') {
          partScores[String(part.id)] = Number(value)
        }
      })
    }
    return {
      studentId: row.studentId,
      enrollmentId: row.enrollmentId,
      totalScore: row.status === '正常' ? totalOf(row) : null,
      level: row.status === '正常' && row.level ? row.level : null,
      partScores,
      status: row.status,
      remark: row.remark?.trim() || undefined,
    }
  })
  return { examId: examId.value, classId: classId.value, rows: payloadRows }
}

async function save() {
  const payload = buildPayload()
  if (!payload) {
    message.warning('请先选择考试与班级')
    return
  }
  saving.value = true
  try {
    const data = await saveGradeEntry(payload)
    applyEntry(data)
    message.success('成绩草稿已保存')
    await loadExams()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存成绩失败')
  } finally {
    saving.value = false
  }
}

function publish() {
  const payload = buildPayload()
  if (!payload) {
    message.warning('请先选择考试与班级')
    return
  }
  Modal.confirm({
    title: '发布成绩',
    content: '发布后本场考试成绩将锁定并计算排名，如需修改需在考试管理中解锁。确认发布？',
    okText: '确认发布',
    cancelText: '取消',
    onOk: async () => {
      saving.value = true
      try {
        const data = await publishGradeEntry(payload)
        applyEntry(data)
        message.success('成绩已发布')
        await loadExams()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '发布成绩失败')
      } finally {
        saving.value = false
      }
    },
  })
}

watch([examId, classId], () => {
  if (examId.value && classId.value) loadEntry()
})

onMounted(async () => {
  await Promise.all([loadExams(), loadRules()])
  if (examId.value && !classId.value) {
    const exam = currentExam.value
    if (exam) classId.value = exam.classIds[0] ?? null
  }
  await loadEntry()
})
</script>

<template>
  <div class="grade-page">
    <div class="filter-line">
      <a-select
        v-model:value="examId"
        size="small"
        style="width: 260px"
        placeholder="选择考试"
        show-search
        :options="examSelectOptions"
        @change="onExamChange"
      />
      <a-select
        v-model:value="classId"
        size="small"
        style="width: 180px"
        placeholder="选择班级"
        :options="classSelectOptions"
        :disabled="!examId"
      />
      <template v-if="entry">
        <a-tag :color="entry.scoringMode === '等级' ? 'purple' : 'blue'">
          {{ entry.examType }} · {{ entry.scoringMode }}制
        </a-tag>
        <span class="full-score">
          满分
          <a-input-number
            v-model:value="fullScoreDraft"
            size="small"
            :min="0.5"
            :precision="1"
            :disabled="readonly"
            style="width: 84px"
          />
          分
          <a-button
            v-if="!readonly"
            type="link"
            size="small"
            :loading="savingFullScore"
            @click="saveFullScore"
          >
            保存
          </a-button>
        </span>
        <span class="filter-label">已录入 {{ enteredCount }} / {{ rows.length }} 人</span>
      </template>
      <a-button size="small" class="settings-btn" @click="settingsOpen = true">
        <SettingOutlined />
        成绩设置
      </a-button>
    </div>

    <div v-if="entry" class="paper-panel">
      <div class="paper-head">
        <span class="paper-title">试卷图片</span>
        <span class="paper-hint">
          支持手机拍照或上传，最多 9 张{{ readonly ? '（已锁定，仅可查看）' : '' }}
        </span>
      </div>
      <div class="paper-list">
        <div v-for="url in paperImages" :key="url" class="paper-item">
          <a-image :src="url" :width="92" :height="68" class="paper-img" />
          <a-button
            v-if="!readonly"
            class="paper-delete"
            type="text"
            size="small"
            danger
            @click="removePaper(url)"
          >
            <DeleteOutlined />
          </a-button>
        </div>
        <a-upload
          v-if="!readonly && paperImages.length < 9"
          :show-upload-list="false"
          accept="image/*"
          :multiple="true"
          :custom-request="uploadPaper"
        >
          <div class="paper-add">
            <CameraOutlined />
            <span>拍照 / 上传</span>
          </div>
        </a-upload>
        <span v-if="!paperImages.length && readonly" class="filter-label">暂无试卷图片</span>
      </div>
    </div>

    <div v-if="!entry" class="empty-panel">
      <a-empty description="请选择要录入成绩的考试与班级" />
    </div>

    <template v-else>
      <a-alert
        v-if="readonly"
        class="lock-alert"
        type="warning"
        show-icon
        :message="entry.status === '已归档' ? '该考试已归档，成绩只读' : '该考试成绩已发布并锁定，如需修改请到「考试管理」解锁'"
      />
      <div v-else class="toolbar-line">
        <a-input-number
          v-model:value="fillValue"
          size="small"
          :min="0"
          :max="Number(entry.fullScore)"
          placeholder="批量填充分数"
          style="width: 150px"
        />
        <a-button size="small" :disabled="usingParts" @click="applyFill">填充到未录入学员</a-button>
        <a-button size="small" @click="openBatch">按姓名批量录入</a-button>
        <span v-if="usingParts" class="filter-label">已配置分项，请逐项录入，总分自动汇总</span>
        <div class="toolbar-right">
          <a-button size="small" :loading="saving" @click="save">保存草稿</a-button>
          <a-button size="small" type="primary" :loading="saving" @click="publish">发布成绩</a-button>
        </div>
      </div>

      <a-table
        class="grade-table"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 'max-content' }"
        row-key="studentId"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'studentName'">
            <span class="stu-name">{{ record.studentName }}</span>
            <span class="stu-no">{{ record.studentNo || '' }}</span>
          </template>
          <template v-else-if="String(column.dataIndex).startsWith('part-')">
            <a-input-number
              :value="record.partScores[String(column.dataIndex).slice(5)]"
              :min="0"
              :max="Number(entry.fullScore)"
              :disabled="readonly || record.status !== '正常'"
              :precision="1"
              size="small"
              style="width: 96px"
              @change="(value: number | null) => onPartChange(record, Number(String(column.dataIndex).slice(5)), value)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'totalScore'">
            <a-input-number
              v-if="!usingParts"
              :value="record.totalScore"
              :min="0"
              :max="Number(entry.fullScore)"
              :disabled="readonly || record.status !== '正常'"
              :precision="1"
              size="small"
              style="width: 90px"
              @change="(value: number | null) => onTotalChange(record, value)"
            />
            <span v-else class="total-text">{{ totalOf(record) ?? '—' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'level'">
            <a-select
              :value="record.level"
              size="small"
              style="width: 100px"
              placeholder="选择等级"
              :options="levelOptions"
              :disabled="readonly || record.status !== '正常'"
              allow-clear
              @change="(value: string) => (record.level = value ?? null)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-select
              :value="record.status"
              size="small"
              style="width: 88px"
              :disabled="readonly"
              :options="[
                { label: '正常', value: '正常' },
                { label: '缺考', value: '缺考' },
                { label: '免考', value: '免考' },
              ]"
              @change="(value: string) => onStatusChange(record, value)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'rankClass'">
            {{ record.rankClass ?? '—' }}
          </template>
          <template v-else-if="column.dataIndex === 'remark'">
            <a-input
              :value="record.remark"
              :maxlength="255"
              :disabled="readonly"
              size="small"
              placeholder="评语 / 备注"
              @change="(event: Event) => (record.remark = (event.target as HTMLInputElement).value)"
            />
          </template>
        </template>
        <template #emptyText>
          <a-empty description="该班级暂无在读学员，请先在班级中分班" />
        </template>
      </a-table>
    </template>

    <a-modal
      v-model:open="batchOpen"
      title="按姓名批量录入"
      :width="560"
      ok-text="导入成绩"
      cancel-text="取消"
      @ok="applyBatch"
    >
      <p class="batch-tip">
        选择录入项后，每行一条「学员姓名 分数」，可直接从 Excel 粘贴两列；仅对未发布成绩的考试生效。
      </p>
      <a-select
        v-model:value="batchTarget"
        size="small"
        style="width: 220px; margin-bottom: 10px"
        :options="batchTargetOptions"
      />
      <a-textarea
        v-model:value="batchText"
        :rows="8"
        placeholder="张三 92&#10;李四 88.5&#10;王五 76"
      />
    </a-modal>

    <a-drawer
      v-model:open="settingsOpen"
      title="等级规则与分项模板"
      width="720"
      placement="right"
      :body-style="{ padding: '0 24px 24px' }"
      @close="reloadAfterSettings"
    >
      <GradeSettings />
    </a-drawer>
  </div>
</template>

<style scoped>
.grade-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 0 12px;
  font-size: 13px;
}

.filter-label {
  color: var(--text-2);
}

.settings-btn {
  margin-left: auto;
}

.full-score {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-2);
}

.paper-panel {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px dashed #e5e6eb;
  border-radius: 6px;
}

.paper-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}

.paper-title {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.paper-hint {
  color: var(--text-3);
  font-size: 12px;
}

.paper-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.paper-item {
  position: relative;
}

.paper-img {
  overflow: hidden;
  border-radius: 4px;
}

.paper-delete {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(255, 255, 255, 0.85);
}

.paper-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 92px;
  height: 68px;
  color: var(--text-2);
  font-size: 12px;
  background: #fafbfc;
  border: 1px dashed #d9dce0;
  border-radius: 4px;
  cursor: pointer;
}

.paper-add:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.batch-tip {
  margin-bottom: 10px;
  color: var(--text-2);
  font-size: 13px;
}

.empty-panel {
  padding: 80px 0;
}

.lock-alert {
  margin-bottom: 12px;
}

.toolbar-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
}

.toolbar-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.grade-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.grade-table :deep(.ant-table-tbody > tr > td) {
  padding: 7px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
}

.stu-name {
  font-weight: 500;
}

.stu-no {
  margin-left: 6px;
  color: var(--text-2);
  font-size: 12px;
}

.total-text {
  font-weight: 600;
  color: var(--brand);
}
</style>
