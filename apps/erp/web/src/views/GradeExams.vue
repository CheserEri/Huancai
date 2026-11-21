<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { DownOutlined, InfoCircleOutlined, PlusOutlined } from '@ant-design/icons-vue'
import {
  archiveGradeExam,
  copyGradeExam,
  createGradeExam,
  deleteGradeExam,
  fetchGradeClassOptions,
  fetchGradeExams,
  publishGradeExam,
  unlockGradeExam,
  updateGradeExam,
  type GradeClassOption,
  type GradeExamPayload,
  type GradeExamRow,
  type GradePart,
  type GradeType,
} from '../api/client'

const router = useRouter()

const rows = ref<GradeExamRow[]>([])
const classOptions = ref<GradeClassOption[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
/** 用户是否手动修改过及格线，未修改时随满分按 60% 自动调整。 */
const passTouched = ref(false)

const keyword = ref('')
const typeFilter = ref<string | undefined>(undefined)
const statusFilter = ref<string | undefined>(undefined)
const classFilter = ref<number | undefined>(undefined)

const typeOptions = [
  { label: '周考（机构）', value: '周考' },
  { label: '月考（机构）', value: '月考' },
  { label: '学校考试', value: '学校考试' },
]
const statusOptions = ['草稿', '已发布', '已归档'].map((item) => ({ label: item, value: item }))
const classSelectOptions = computed(() =>
  classOptions.value.map((item) => ({
    label: item.courseName ? `${item.name}（${item.courseName}）` : item.name,
    value: item.id,
  })),
)

/** 适用班级按课程分组，直接多选班级。 */
const classGroupOptions = computed(() => {
  const groups = new Map<string, { label: string; options: { label: string; value: number }[] }>()
  classOptions.value.forEach((item) => {
    const groupName = item.courseName || '未绑定课程'
    if (!groups.has(groupName)) groups.set(groupName, { label: groupName, options: [] })
    groups.get(groupName)?.options.push({ label: item.name, value: item.id })
  })
  return Array.from(groups.values())
})

const allClassIds = computed(() =>
  classGroupOptions.value.flatMap((group) => group.options.map((item) => item.value)),
)

const form = reactive<{
  name: string
  type: GradeType
  /** 兼容历史数据：弹窗不再选择课程，编辑时原样保留。 */
  courseId: number | null
  examDate: string | null
  fullScore: number
  passScore: number
  remark: string
  classIds: number[]
  /** 分项不再在新建/编辑弹窗中维护，编辑时原样保留历史配置。 */
  parts: GradePart[]
}>({
  name: '',
  type: '周考',
  courseId: null,
  examDate: null,
  fullScore: 100,
  passScore: 60,
  remark: '',
  classIds: [],
  parts: [],
})

const columns = [
  { title: '考试名称', dataIndex: 'name', width: 180, ellipsis: true },
  { title: '类型', dataIndex: 'type', width: 92 },
  { title: '课程', dataIndex: 'courseName', width: 96, ellipsis: true },
  { title: '考试日期', dataIndex: 'examDate', width: 104 },
  { title: '适用班级', dataIndex: 'classNames', width: 230 },
  { title: '满分', dataIndex: 'fullScore', width: 64, align: 'right' as const },
  { title: '录入进度', dataIndex: 'progress', width: 90, align: 'center' as const },
  { title: '状态', dataIndex: 'status', width: 82 },
  { title: '创建人', dataIndex: 'creator', width: 78 },
  { title: '操作', dataIndex: 'action', width: 168 },
]

function typeColor(type: string) {
  switch (type) {
    case '周考':
      return 'blue'
    case '月考':
      return 'purple'
    case '学校考试':
      return 'green'
    default:
      return 'orange'
  }
}

function statusClass(status: string) {
  if (status === '已发布') return 'status-published'
  if (status === '已归档') return 'status-archived'
  return 'status-draft'
}

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchGradeExams({
      keyword: keyword.value.trim() || undefined,
      type: typeFilter.value,
      status: statusFilter.value,
      classId: classFilter.value,
    })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载考试列表失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    classOptions.value = await fetchGradeClassOptions()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载班级选项失败')
  }
}

function resetForm() {
  form.name = ''
  form.type = '周考'
  form.courseId = null
  form.examDate = null
  form.fullScore = 100
  form.passScore = 60
  form.remark = ''
  form.classIds = []
  form.parts = []
  passTouched.value = false
}

function onFullScoreChange(value: number | null) {
  const full = Number(value ?? 0)
  if (!passTouched.value || form.passScore > full) {
    form.passScore = Math.max(1, Math.round(full * 0.6))
  }
}

function onPassScoreChange() {
  passTouched.value = true
}

function openModal(record?: GradeExamRow) {
  editingId.value = record?.id ?? null
  resetForm()
  if (record) {
    form.name = record.name
    form.type = record.type
    form.courseId = record.courseId
    form.examDate = record.examDate
    form.fullScore = Number(record.fullScore)
    form.passScore = record.passScore != null
      ? Number(record.passScore)
      : Math.round(Number(record.fullScore) * 0.6)
    passTouched.value = record.passScore != null
    form.remark = record.remark ?? ''
    form.classIds = [...record.classIds]
    form.parts = record.parts.map((part) => ({
      name: part.name,
      weight: Number(part.weight),
      fullScore: Number(part.fullScore),
      sort: part.sort,
    }))
  }
  modalOpen.value = true
}

function selectAllClasses() {
  form.classIds = [...allClassIds.value]
}

async function submit() {
  if (!form.name.trim()) {
    message.warning('请填写考试名称')
    return
  }
  if (!form.classIds.length) {
    message.warning('请选择适用班级')
    return
  }
  if (!(Number(form.fullScore) > 0)) {
    message.warning('请填写大于 0 的满分')
    return
  }
  if (!(Number(form.passScore) > 0) || Number(form.passScore) > Number(form.fullScore)) {
    message.warning('及格线需大于 0 且不超过满分')
    return
  }
  const payload: GradeExamPayload = {
    name: form.name.trim(),
    type: form.type,
    courseId: form.courseId,
    examDate: form.examDate,
    fullScore: Number(form.fullScore || 100),
    passScore: Number(form.passScore),
    scoringMode: '分数',
    remark: form.remark.trim() || undefined,
    classIds: form.classIds,
    parts: form.parts.map((part, index) => ({
      name: part.name,
      weight: Number(part.weight || 0),
      fullScore: Number(part.fullScore || 0),
      sort: index,
    })),
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateGradeExam(editingId.value, payload)
      message.success('考试已更新')
    } else {
      await createGradeExam(payload)
      message.success('考试已创建')
    }
    modalOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存考试失败')
  } finally {
    saving.value = false
  }
}

function goEntry(record: GradeExamRow) {
  const classId = record.classIds[0]
  router.push({ path: '/gradeEntry', query: { examId: record.id, classId } })
}

function confirmAction(title: string, content: string, action: () => Promise<unknown>, successText: string) {
  Modal.confirm({
    title,
    content,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await action()
        message.success(successText)
        await load()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '操作失败')
      }
    },
  })
}

function publish(record: GradeExamRow) {
  confirmAction(
    '发布成绩',
    `发布后「${record.name}」的成绩将锁定并计算排名，修改需先解锁。确认发布？`,
    () => publishGradeExam(record.id),
    '成绩已发布',
  )
}

function unlock(record: GradeExamRow) {
  confirmAction(
    '解锁成绩',
    `解锁后「${record.name}」回到草稿状态，可继续修改，并会在备注中记录解锁人。确认解锁？`,
    () => unlockGradeExam(record.id),
    '成绩已解锁',
  )
}

function archive(record: GradeExamRow) {
  confirmAction('归档考试', `确认归档「${record.name}」？归档后不能再录入成绩。`, () => archiveGradeExam(record.id), '考试已归档')
}

function copy(record: GradeExamRow) {
  confirmAction('复制考试', `将复制「${record.name}」的班级与分项配置（不含成绩）。`, () => copyGradeExam(record.id), '考试已复制')
}

function remove(record: GradeExamRow) {
  Modal.confirm({
    title: '删除考试',
    content: `确认删除「${record.name}」？仅草稿状态的考试可删除，删除后不可恢复。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteGradeExam(record.id)
        message.success('考试已删除')
        await load()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除考试失败')
      }
    },
  })
}

onMounted(async () => {
  await Promise.all([load(), loadOptions()])
})
</script>

<template>
  <div class="grade-page">
    <div class="filter-line">
      <a-button size="small" type="primary" class="with-icon" @click="openModal()">
        <PlusOutlined />新建考试
      </a-button>
      <a-input-search
        v-model:value="keyword"
        size="small"
        placeholder="搜索考试名称"
        allow-clear
        style="width: 180px"
        @search="load"
        @change="!keyword && load()"
      />
      <a-select
        v-model:value="typeFilter"
        size="small"
        style="width: 120px"
        placeholder="全部类型"
        allow-clear
        :options="typeOptions"
        @change="load"
      />
      <a-select
        v-model:value="statusFilter"
        size="small"
        style="width: 110px"
        placeholder="全部状态"
        allow-clear
        :options="statusOptions"
        @change="load"
      />
      <a-select
        v-model:value="classFilter"
        size="small"
        style="width: 180px"
        placeholder="全部班级"
        allow-clear
        show-search
        :options="classSelectOptions"
        @change="load"
      />
      <a-tooltip title="周考、月考（机构）与学校考试共用一套成绩体系">
        <InfoCircleOutlined class="filter-hint" />
      </a-tooltip>
    </div>

    <div class="summary-line">当前结果：共 {{ rows.length }} 场考试</div>

    <a-table
      class="grade-table"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{ pageSize: 20, showSizeChanger: false }"
      :scroll="{ x: 1184 }"
      table-layout="fixed"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <span v-if="column.dataIndex === 'name'" class="ellipsis" :title="record.name">{{ record.name }}</span>
        <span v-else-if="column.dataIndex === 'type'">
          <a-tag :color="typeColor(record.type)">{{ record.type }}</a-tag>
        </span>
        <span v-else-if="column.dataIndex === 'courseName'" class="ellipsis" :title="show(record.courseName)">
          {{ show(record.courseName) }}
        </span>
        <span v-else-if="column.dataIndex === 'examDate'" class="nowrap-cell">{{ show(record.examDate) }}</span>
        <span v-else-if="column.dataIndex === 'classNames'" class="class-cell">
          <template v-if="record.classNames.length">
            <span
              v-for="name in record.classNames.slice(0, 2)"
              :key="name"
              class="class-chip"
              :title="name"
            >{{ name }}</span>
            <a-tooltip v-if="record.classNames.length > 2" :title="record.classNames.join('、')">
              <span class="class-more">+{{ record.classNames.length - 2 }}</span>
            </a-tooltip>
          </template>
          <span v-else>—</span>
        </span>
        <span v-else-if="column.dataIndex === 'fullScore'">{{ record.fullScore }}</span>
        <span v-else-if="column.dataIndex === 'progress'">
          {{ record.enteredCount }} / {{ record.studentCount }}
        </span>
        <span v-else-if="column.dataIndex === 'status'">
          <a-tag :class="statusClass(record.status)">{{ record.status }}</a-tag>
        </span>
        <span v-else-if="column.dataIndex === 'creator'">{{ show(record.creator) }}</span>
        <span v-else-if="column.dataIndex === 'action'" class="row-actions">
          <a-button type="link" size="small" class="row-btn" @click="goEntry(record)">
            {{ record.status === '草稿' ? '录入成绩' : '查看成绩' }}
          </a-button>
          <a-button
            v-if="record.status === '草稿'"
            type="link"
            size="small"
            class="row-btn"
            @click="openModal(record)"
          >
            编辑
          </a-button>
          <a-dropdown>
            <a-button type="link" size="small" class="row-btn">
              更多<DownOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item v-if="record.status === '草稿'" @click="publish(record)">发布成绩</a-menu-item>
                <a-menu-item v-if="record.status === '已发布'" @click="unlock(record)">解锁修改</a-menu-item>
                <a-menu-item v-if="record.status === '已发布'" @click="archive(record)">归档考试</a-menu-item>
                <a-menu-item @click="copy(record)">复制考试</a-menu-item>
                <a-menu-item v-if="record.status === '草稿'" danger @click="remove(record)">删除考试</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </span>
      </template>
      <template #emptyText>
        <a-empty description="暂无考试安排，点击左上角「新建考试」创建" />
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑考试' : '新建考试'"
      :width="640"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form layout="vertical" class="exam-form">
        <div class="form-grid">
          <a-form-item label="考试名称" required>
            <a-input v-model:value="form.name" :maxlength="120" placeholder="例如：2026 秋季书法期中测试" />
          </a-form-item>
          <a-form-item label="成绩类型">
            <a-select v-model:value="form.type" :options="typeOptions" />
          </a-form-item>
        </div>
        <div class="form-grid three">
          <a-form-item label="考试日期（选填）">
            <a-date-picker
              v-model:value="form.examDate"
              value-format="YYYY-MM-DD"
              placeholder="选填，可不填"
              allow-clear
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="满分">
            <a-input-number
              v-model:value="form.fullScore"
              :min="1"
              :step="10"
              style="width: 100%"
              @change="onFullScoreChange"
            />
          </a-form-item>
          <a-form-item label="及格线">
            <a-input-number
              v-model:value="form.passScore"
              :min="1"
              :max="form.fullScore"
              :step="5"
              style="width: 100%"
              @change="onPassScoreChange"
            />
            <div class="class-hint">默认满分的 60%，用于自动评级</div>
          </a-form-item>
        </div>
        <a-form-item label="适用班级（可多选）" required>
          <a-select
            v-model:value="form.classIds"
            mode="multiple"
            placeholder="按课程分组直接多选班级"
            option-filter-prop="label"
            :options="classGroupOptions"
          />
          <div class="class-tools">
            <span class="class-hint">已选 {{ form.classIds.length }} 个班级</span>
            <a-button type="link" size="small" @click="selectAllClasses">全选所有班级</a-button>
            <a-button type="link" size="small" @click="form.classIds = []">清空</a-button>
          </div>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" :maxlength="255" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>
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

.filter-hint {
  color: var(--text-3);
  font-size: 14px;
  cursor: help;
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.grade-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
  white-space: nowrap;
}

.grade-table :deep(.ant-table-tbody > tr > td) {
  padding: 9px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
  white-space: nowrap;
  overflow: hidden;
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.nowrap-cell {
  white-space: nowrap;
}

.class-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

.class-chip {
  display: inline-block;
  flex: 0 1 auto;
  min-width: 0;
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 6px;
  font-size: 12px;
  line-height: 20px;
  color: var(--text-2);
  background: #f5f7fa;
  border-radius: 3px;
}

.class-more {
  flex: none;
  padding: 0 6px;
  font-size: 12px;
  line-height: 20px;
  color: #1677ff;
  background: #e8f1ff;
  border-radius: 3px;
  cursor: pointer;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.status-draft {
  color: #8f959e;
  background: #f5f6f7;
  border-color: #e5e6eb;
}

.status-published {
  color: #0e9f6e;
  background: #e8f8f1;
  border-color: #b7e9d2;
}

.status-archived {
  color: #a8071a;
  background: #fff1f0;
  border-color: #ffccc7;
}

.exam-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.form-grid.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 14px;
}

.class-tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 6px;
}

.class-tools .class-hint {
  margin-right: auto;
}

.class-hint {
  font-size: 12px;
  color: var(--text-3);
}
</style>
