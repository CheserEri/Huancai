<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DownloadOutlined, SettingOutlined } from '@ant-design/icons-vue'
import {
  downloadTeacherHourReport,
  fetchTeacherHourMonth,
  fetchTeacherHourRates,
  saveTeacherHourRates,
  updateTeacherHourAmount,
  type TeacherHourMonth,
  type TeacherHourRecord,
} from '../api/client'

const GRADE_LABELS = ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级', '七年级', '八年级', '九年级']
const GRADE_OPTIONS = GRADE_LABELS.map((label, index) => ({ value: index + 1, label }))

interface RateDraft {
  key: number
  teacherId: number
  teacherName: string
  gradeStart: number
  gradeEnd: number
  unitPrice: number | null
  thresholdStudents: number | null
  fixedAmount: number | null
}

const month = ref(currentMonth())
const teacherId = ref<number | null>(null)
const campusId = ref<number | null>(null)
const loading = ref(false)
const exporting = ref(false)
const data = ref<TeacherHourMonth>({
  records: [],
  teachers: [],
  teacherOptions: [],
  campusOptions: [],
  lessonCount: 0,
  studentCount: 0,
  amount: 0,
  missingRateCount: 0,
})

const rateOpen = ref(false)
const rateLoading = ref(false)
const rateSaving = ref(false)
const rateDrafts = ref<RateDraft[]>([])
const rateTeacherId = ref<number | null>(null)
let rateKeySeed = 0

const adjustOpen = ref(false)
const adjustSaving = ref(false)
const adjustRow = ref<TeacherHourRecord | null>(null)
const adjustAmount = ref<number | null>(null)
const adjustRemark = ref('')

const monthOptions = (() => {
  const now = new Date()
  const list: Array<{ value: string; label: string }> = []
  for (let i = 0; i < 12; i++) {
    const date = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const value = `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
    list.push({ value, label: `${date.getFullYear()}年${date.getMonth() + 1}月` })
  }
  return list
})()

const teacherFilterOptions = computed(() =>
  data.value.teacherOptions.map((item) => ({ value: item.id, label: item.name })),
)
const campusFilterOptions = computed(() =>
  data.value.campusOptions.map((item) => ({ value: item.id, label: item.name })),
)

const columns = [
  { title: '日期', dataIndex: 'lessonDate', width: 116 },
  { title: '时间', dataIndex: 'time', width: 108 },
  { title: '教师', dataIndex: 'teacherName', width: 96 },
  { title: '班级', dataIndex: 'className', width: 180 },
  { title: '课程', dataIndex: 'courseName', width: 120 },
  { title: '年级', dataIndex: 'grade', width: 88 },
  { title: '应到', dataIndex: 'shouldCount', width: 68, align: 'right' as const },
  { title: '上课人数', dataIndex: 'studentCount', width: 84, align: 'right' as const },
  { title: '课酬规则', dataIndex: 'rate', width: 170 },
  { title: '课酬', dataIndex: 'amount', width: 96, align: 'right' as const },
  { title: '备注', dataIndex: 'adjustRemark', width: 150 },
  { title: '操作', dataIndex: 'action', width: 80 },
]

const summaryColumns = [
  { title: '教师', dataIndex: 'teacherName', width: 120 },
  { title: '课时数', dataIndex: 'lessonCount', width: 100, align: 'right' as const },
  { title: '上课人次', dataIndex: 'studentCount', width: 110, align: 'right' as const },
  { title: '课酬', dataIndex: 'amount', width: 130, align: 'right' as const },
  { title: '明细', dataIndex: 'detail' },
]

const rateColumns = [
  { title: '教师', dataIndex: 'teacherName', width: 100 },
  { title: '适用年级', dataIndex: 'gradeRange', width: 220 },
  { title: '单价（元/人）', dataIndex: 'unitPrice', width: 140 },
  { title: '人数额度', dataIndex: 'thresholdStudents', width: 120 },
  { title: '额度后固定金额', dataIndex: 'fixedAmount', width: 150 },
  { title: '操作', dataIndex: 'action', width: 70 },
]

const activeTab = ref('records')

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`
}

function money(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function timeText(row: TeacherHourRecord) {
  const start = (row.startTime ?? '').slice(0, 5)
  const end = (row.endTime ?? '').slice(0, 5)
  return start && end ? `${start}-${end}` : start || '—'
}

function dateText(value: string) {
  const [year, monthText, dayText] = value.split('-').map(Number)
  const date = new Date(year, monthText - 1, dayText)
  const week = ['日', '一', '二', '三', '四', '五', '六'][date.getDay()]
  return `${monthText}月${dayText}日 周${week}`
}

function rateText(row: TeacherHourRecord) {
  if (!row.rateConfigured) return '未配置单价'
  const unit = money(row.unitPrice)
  const fixed = money(row.fixedAmount)
  return `${unit}元/人；满${row.thresholdStudents ?? 0}人${fixed}元`
}

async function load() {
  loading.value = true
  try {
    data.value = await fetchTeacherHourMonth(month.value, teacherId.value, campusId.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载教师课时失败')
  } finally {
    loading.value = false
  }
}

async function exportReport() {
  exporting.value = true
  try {
    const blob = await downloadTeacherHourReport(month.value, teacherId.value, campusId.value)
    const [year, monthText] = month.value.split('-')
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `${year.slice(2)}年${Number(monthText)}月教师课时费.xlsx`
    link.click()
    URL.revokeObjectURL(link.href)
    message.success('已导出月度教师课时费')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '导出失败')
  } finally {
    exporting.value = false
  }
}

async function openRates() {
  rateOpen.value = true
  rateLoading.value = true
  try {
    if (!data.value.teacherOptions.length) {
      await load()
    }
    const rates = await fetchTeacherHourRates()
    rateDrafts.value = rates.map((rate) => ({
      key: ++rateKeySeed,
      teacherId: rate.teacherId,
      teacherName: rate.teacherName || teacherNameOf(rate.teacherId),
      gradeStart: rate.gradeStart,
      gradeEnd: rate.gradeEnd,
      unitPrice: Number(rate.unitPrice),
      thresholdStudents: rate.thresholdStudents,
      fixedAmount: Number(rate.fixedAmount),
    }))
    if (!rateTeacherId.value) {
      rateTeacherId.value = data.value.teacherOptions[0]?.id ?? null
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载课酬规则失败')
  } finally {
    rateLoading.value = false
  }
}

function teacherNameOf(id: number) {
  return data.value.teacherOptions.find((item) => item.id === id)?.name ?? ''
}

function gradeLabel(level: number) {
  return GRADE_LABELS[level - 1] ?? `${level}年级`
}

function addRateDraft() {
  const teacher = data.value.teacherOptions.find((item) => item.id === rateTeacherId.value)
  if (!teacher) {
    message.warning('请先选择教师')
    return
  }
  rateDrafts.value.push({
    key: ++rateKeySeed,
    teacherId: teacher.id,
    teacherName: teacher.name,
    gradeStart: 1,
    gradeEnd: 3,
    unitPrice: null,
    thresholdStudents: 10,
    fixedAmount: null,
  })
}

function removeRateDraft(row: RateDraft) {
  rateDrafts.value = rateDrafts.value.filter((draft) => draft.key !== row.key)
}

async function submitRates() {
  const invalid = rateDrafts.value.find(
    (draft) => draft.unitPrice === null && draft.fixedAmount === null,
  )
  if (invalid) {
    message.warning(`请填写「${invalid.teacherName}」${gradeLabel(invalid.gradeStart)}的单价或固定金额`)
    return
  }
  rateSaving.value = true
  try {
    const payload = rateDrafts.value.map((draft) => ({
      teacherId: draft.teacherId,
      teacherName: draft.teacherName,
      gradeStart: Math.min(draft.gradeStart, draft.gradeEnd),
      gradeEnd: Math.max(draft.gradeStart, draft.gradeEnd),
      unitPrice: draft.unitPrice ?? 0,
      thresholdStudents: draft.thresholdStudents ?? 10,
      fixedAmount: draft.fixedAmount ?? 0,
    }))
    await saveTeacherHourRates(payload)
    message.success('课酬规则已保存')
    rateOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存课酬规则失败')
  } finally {
    rateSaving.value = false
  }
}

function openAdjust(row: TeacherHourRecord) {
  adjustRow.value = row
  adjustAmount.value = row.adjustAmount === null ? Number(row.amount) : Number(row.adjustAmount)
  adjustRemark.value = row.adjustRemark ?? ''
  adjustOpen.value = true
}

async function submitAdjust() {
  if (!adjustRow.value) return
  adjustSaving.value = true
  try {
    await updateTeacherHourAmount(adjustRow.value.scheduleId, adjustAmount.value, adjustRemark.value.trim() || null)
    message.success('该节课课酬已更新')
    adjustOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '更新课酬失败')
  } finally {
    adjustSaving.value = false
  }
}

async function resetAdjust() {
  if (!adjustRow.value) return
  adjustSaving.value = true
  try {
    await updateTeacherHourAmount(adjustRow.value.scheduleId, null, null)
    message.success('已恢复按规则计算')
    adjustOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '恢复失败')
  } finally {
    adjustSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="teacher-hour-page">
    <div class="filter-line">
      <a-select v-model:value="month" :options="monthOptions" style="width: 128px" @change="load" />
      <a-select
        v-model:value="teacherId"
        :options="teacherFilterOptions"
        allow-clear
        placeholder="全部教师"
        style="width: 140px"
        @change="load"
      />
      <a-select
        v-model:value="campusId"
        :options="campusFilterOptions"
        allow-clear
        placeholder="全部校区"
        style="width: 170px"
        @change="load"
      />
      <a-button size="small" type="primary" class="with-icon" :loading="exporting" @click="exportReport">
        <DownloadOutlined />导出月度课时费
      </a-button>
      <a-button size="small" class="with-icon" @click="openRates">
        <SettingOutlined />课酬规则
      </a-button>
      <span class="filter-label">只统计「已上课」课次，上课人数取实到人数</span>
    </div>

    <div class="stat-line">
      <div class="stat-card">
        <span class="stat-label">课时数</span>
        <span class="stat-value">{{ data.lessonCount }}<em>节</em></span>
      </div>
      <div class="stat-card">
        <span class="stat-label">上课人次</span>
        <span class="stat-value">{{ data.studentCount }}<em>人次</em></span>
      </div>
      <div class="stat-card">
        <span class="stat-label">课酬合计</span>
        <span class="stat-value brand">¥{{ money(data.amount) }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-label">教师人数</span>
        <span class="stat-value">{{ data.teachers.length }}<em>人</em></span>
      </div>
      <div v-if="data.missingRateCount" class="stat-tip">
        {{ data.missingRateCount }} 位教师未配置课酬规则，可点击「课酬规则」设置
      </div>
    </div>

    <a-tabs v-model:activeKey="activeTab" size="small">
      <a-tab-pane key="records" tab="上课记录">
        <a-table
          class="hour-table"
          :columns="columns"
          :data-source="data.records"
          :loading="loading"
          :pagination="{ pageSize: 100, hideOnSinglePage: true, showSizeChanger: false }"
          row-key="scheduleId"
          size="small"
          :scroll="{ x: 1320 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'lessonDate'">{{ dateText(record.lessonDate) }}</span>
            <span v-else-if="column.dataIndex === 'time'">{{ timeText(record) }}</span>
            <span v-else-if="column.dataIndex === 'className'">{{ record.className || record.courseName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'grade'">{{ record.grade || '—' }}</span>
            <span v-else-if="column.dataIndex === 'rate'">
              <span :class="record.rateConfigured ? 'rate-text' : 'rate-missing'">{{ rateText(record) }}</span>
            </span>
            <span v-else-if="column.dataIndex === 'amount'">
              <span class="amount-text">¥{{ money(record.amount) }}</span>
              <span v-if="record.adjustAmount !== null" class="adjust-flag">手工</span>
            </span>
            <span v-else-if="column.dataIndex === 'adjustRemark'">{{ record.adjustRemark || '—' }}</span>
            <span v-else-if="column.dataIndex === 'action'" class="row-actions">
              <a-button type="link" size="small" class="row-btn" @click="openAdjust(record)">调整</a-button>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="该月份暂无已上课记录" />
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="summary" tab="教师汇总">
        <a-table
          class="hour-table"
          :columns="summaryColumns"
          :data-source="data.teachers"
          :loading="loading"
          :pagination="false"
          row-key="teacherId"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'teacherName'" class="teacher-name">{{ record.teacherName }}</span>
            <span v-else-if="column.dataIndex === 'lessonCount'">{{ record.lessonCount }} 节</span>
            <span v-else-if="column.dataIndex === 'studentCount'">{{ record.studentCount }} 人次</span>
            <span v-else-if="column.dataIndex === 'amount'" class="amount-text">¥{{ money(record.amount) }}</span>
            <span v-else-if="column.dataIndex === 'detail'">{{ record.detail || '—' }}</span>
          </template>
          <template #emptyText>
            <a-empty description="该月份暂无教师课时数据" />
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-drawer v-model:open="rateOpen" title="课酬规则（教师 + 适用年级）" width="800">
      <div class="rate-hint">
        可按教师自由添加规则，每条规则设置适用年级区间（如一年级~三年级、四年级~六年级）。课次年级落在区间内即命中，区间越窄越优先。
        未达到人数额度：课酬 = 上课人数 × 单价；达到/超过人数额度：按该节课固定金额。
      </div>
      <div class="rate-toolbar">
        <span class="rate-toolbar-label">添加规则到</span>
        <a-select
          v-model:value="rateTeacherId"
          :options="teacherFilterOptions"
          placeholder="选择教师"
          style="width: 160px"
          size="small"
        />
        <a-button type="primary" ghost size="small" class="with-icon" @click="addRateDraft">添加规则</a-button>
      </div>
      <a-spin :spinning="rateLoading">
        <a-table
          :columns="rateColumns"
          :data-source="rateDrafts"
          :pagination="false"
          :row-key="(row: RateDraft) => row.key"
          size="small"
          class="rate-table"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'teacherName'" class="teacher-name">{{ record.teacherName }}</span>
            <span v-else-if="column.dataIndex === 'gradeRange'" class="grade-range">
              <a-select v-model:value="record.gradeStart" :options="GRADE_OPTIONS" size="small" style="width: 92px" />
              <span class="grade-sep">~</span>
              <a-select v-model:value="record.gradeEnd" :options="GRADE_OPTIONS" size="small" style="width: 92px" />
            </span>
            <a-input-number
              v-else-if="column.dataIndex === 'unitPrice'"
              v-model:value="record.unitPrice"
              :min="0"
              :precision="2"
              size="small"
              style="width: 100%"
              placeholder="元/人"
            />
            <a-input-number
              v-else-if="column.dataIndex === 'thresholdStudents'"
              v-model:value="record.thresholdStudents"
              :min="1"
              :precision="0"
              size="small"
              style="width: 100%"
              placeholder="人数"
            />
            <a-input-number
              v-else-if="column.dataIndex === 'fixedAmount'"
              v-model:value="record.fixedAmount"
              :min="0"
              :precision="2"
              size="small"
              style="width: 100%"
              placeholder="元/节"
            />
            <a-button
              v-else-if="column.dataIndex === 'action'"
              type="link"
              size="small"
              danger
              @click="removeRateDraft(record)"
            >
              删除
            </a-button>
          </template>
          <template #emptyText>
            <a-empty description="暂无课酬规则，先选择教师后点击「添加规则」" />
          </template>
        </a-table>
      </a-spin>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="rateOpen = false">取消</a-button>
          <a-button type="primary" :loading="rateSaving" @click="submitRates">保存规则</a-button>
        </div>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="adjustOpen"
      title="调整单节课酬"
      width="480"
      :confirm-loading="adjustSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitAdjust"
    >
      <div v-if="adjustRow" class="adjust-body">
        <div class="adjust-meta">
          {{ dateText(adjustRow.lessonDate) }} · {{ adjustRow.teacherName }} · {{ adjustRow.className || adjustRow.courseName }}
        </div>
        <div class="adjust-meta sub">上课人数 {{ adjustRow.studentCount }} 人，按规则计算 ¥{{ money(adjustRow.amount) }}</div>
        <a-form layout="vertical">
          <a-form-item label="本节课酬（元）">
            <a-input-number v-model:value="adjustAmount" :min="0" :precision="2" style="width: 100%" />
          </a-form-item>
          <a-form-item label="备注">
            <a-input v-model:value="adjustRemark" :maxlength="100" placeholder="例如：试听课不计课时" />
          </a-form-item>
        </a-form>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <a-button v-if="adjustRow?.adjustAmount !== null && adjustRow?.adjustAmount !== undefined" danger @click="resetAdjust">
            恢复按规则计算
          </a-button>
          <span class="footer-gap"></span>
          <a-button @click="adjustOpen = false">取消</a-button>
          <a-button type="primary" :loading="adjustSaving" @click="submitAdjust">保存</a-button>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.teacher-hour-page {
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

.stat-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 132px;
  padding: 10px 16px;
  background: #fafbfc;
  border-radius: 6px;
  border: 1px solid #f0f1f2;
}

.stat-label {
  font-size: 12px;
  color: var(--text-2);
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--text);
}

.stat-value em {
  margin-left: 4px;
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
  color: var(--text-2);
}

.stat-value.brand {
  color: var(--brand);
}

.stat-tip {
  flex: 1;
  font-size: 12px;
  color: #faad14;
}

.hour-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.hour-table :deep(.ant-table-tbody > tr > td) {
  padding: 9px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
}

.teacher-name {
  font-weight: 500;
}

.amount-text {
  color: var(--brand);
  font-weight: 500;
}

.adjust-flag {
  margin-left: 4px;
  padding: 0 4px;
  font-size: 11px;
  color: #faad14;
  background: #fff7e6;
  border-radius: 3px;
}

.rate-text {
  color: var(--text-2);
}

.rate-missing {
  color: #ff4d4f;
}

.row-actions {
  display: inline-flex;
  gap: 4px;
}

.rate-hint {
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-2);
}

.rate-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.rate-toolbar-label {
  font-size: 12px;
  color: var(--text-2);
}

.grade-range {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.grade-sep {
  color: var(--text-2);
  font-size: 12px;
}

.rate-table :deep(.ant-table-tbody > tr > td) {
  padding: 6px 8px;
  font-size: 13px;
}

.drawer-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-gap {
  flex: 1;
}

.adjust-meta {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--text);
}

.adjust-meta.sub {
  color: var(--text-2);
  font-size: 12px;
}

.adjust-body :deep(.ant-form-item) {
  margin-bottom: 14px;
  margin-top: 12px;
}
</style>
