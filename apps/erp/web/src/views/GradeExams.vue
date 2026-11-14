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
  fetchGradePartTemplates,
  publishGradeExam,
  unlockGradeExam,
  updateGradeExam,
  type GradeClassOption,
  type GradeExamPayload,
  type GradeExamRow,
  type GradePart,
  type GradePartTemplate,
  type GradeScoringMode,
  type GradeType,
} from '../api/client'

const router = useRouter()

const rows = ref<GradeExamRow[]>([])
const classOptions = ref<GradeClassOption[]>([])
const templates = ref<GradePartTemplate[]>([])
const loading = ref(false)
const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

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
const courseSelectOptions = computed(() => {
  const map = new Map<number, string>()
  classOptions.value.forEach((item) => {
    if (item.courseId && item.courseName) map.set(item.courseId, item.courseName)
  })
  return Array.from(map.entries()).map(([value, label]) => ({ label, value }))
})

const form = reactive<{
  name: string
  type: GradeType
  courseId: number | null
  examDate: string | null
  fullScore: number
  scoringMode: GradeScoringMode
  remark: string
  classIds: number[]
  parts: GradePart[]
}>({
  name: '',
  type: '周考',
  courseId: null,
  examDate: null,
  fullScore: 100,
  scoringMode: '分数',
  remark: '',
  classIds: [],
  parts: [],
})

/** 学校考试不绑定课程，直接批量选择班级。 */
const schoolExam = computed(() => form.type === '学校考试')

/** 机构周考/月考：选中课程后，其下全部班级自动适用。 */
const autoClassOptions = computed(() =>
  form.courseId
    ? classOptions.value
        .filter((item) => item.courseId === form.courseId)
        .map((item) => ({ label: item.name, value: item.id }))
    : [],
)

/** 学校考试：按课程分组批量选择班级。 */
const schoolClassGroups = computed(() => {
  const groups = new Map<string, { label: string; options: { label: string; value: number }[] }>()
  classOptions.value.forEach((item) => {
    const groupName = item.courseName || '未绑定课程'
    if (!groups.has(groupName)) groups.set(groupName, { label: groupName, options: [] })
    groups.get(groupName)?.options.push({ label: item.name, value: item.id })
  })
  return Array.from(groups.values())
})

/** 本次考试已适用的班级（机构考试随课程自动带出）。 */
const appliedClassOptions = computed(() =>
  form.classIds.map((id) => ({
    value: id,
    label: classOptions.value.find((item) => item.id === id)?.name ?? `班级#${id}`,
  })),
)

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
  try {
    templates.value = await fetchGradePartTemplates()
  } catch {
    templates.value = []
  }
}

function resetForm() {
  form.name = ''
  form.type = '周考'
  form.courseId = null
  form.examDate = null
  form.fullScore = 100
  form.scoringMode = '分数'
  form.remark = ''
  form.classIds = []
  form.parts = []
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
    form.scoringMode = record.scoringMode
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

/** 机构考试：选中课程后批量带出该课程下的全部班级。 */
function onCourseChange() {
  if (schoolExam.value) return
  form.classIds = autoClassOptions.value.map((item) => item.value)
}

/** 切换类型：学校考试不绑定课程，需重新批量选择班级。 */
function onTypeChange(value: GradeType) {
  if (value === '学校考试') {
    form.courseId = null
    form.classIds = []
    return
  }
  // 从学校考试切回机构考试时，需要重新选课程带出班级；周考/月考之间保留已选内容
  if (!form.courseId) form.classIds = []
}

function selectAllSchoolClasses() {
  form.classIds = schoolClassGroups.value.flatMap((group) => group.options.map((item) => item.value))
}

function addPart() {
  form.parts.push({ name: '', weight: 0, fullScore: 0 })
}

function removePart(index: number) {
  form.parts.splice(index, 1)
}

function applyTemplate(templateId: number) {
  const template = templates.value.find((item) => item.id === templateId)
  if (!template) return
  form.parts = template.parts.map((part) => ({
    name: part.name,
    weight: Number(part.weight),
    fullScore: Number(part.fullScore),
  }))
}

async function submit() {
  if (!form.name.trim()) {
    message.warning('请填写考试名称')
    return
  }
  if (!schoolExam.value && !form.courseId) {
    message.warning('请选择课程，系统会自动适用该课程下的全部班级')
    return
  }
  if (!form.classIds.length) {
    message.warning(schoolExam.value ? '请选择适用班级' : '该课程下暂无班级，请先在班级管理中添加')
    return
  }
  const parts = form.parts.filter((part) => part.name.trim())
  const weightSum = parts.reduce((sum, part) => sum + Number(part.weight || 0), 0)
  if (parts.length && Math.abs(weightSum - 100) > 0.01) {
    message.warning(`分项权重合计应为 100%，当前为 ${weightSum}%`)
    return
  }
  const payload: GradeExamPayload = {
    name: form.name.trim(),
    type: form.type,
    courseId: schoolExam.value ? null : form.courseId,
    courseName: schoolExam.value
      ? null
      : courseSelectOptions.value.find((item) => item.value === form.courseId)?.label ?? null,
    examDate: form.examDate,
    fullScore: Number(form.fullScore || 100),
    scoringMode: form.scoringMode,
    remark: form.remark.trim() || undefined,
    classIds: form.classIds,
    parts: parts.map((part, index) => ({
      name: part.name.trim(),
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
            <a-select v-model:value="form.type" :options="typeOptions" @change="onTypeChange" />
          </a-form-item>
        </div>
        <div class="form-grid">
          <a-form-item v-if="!schoolExam" label="课程" required>
            <a-select
              v-model:value="form.courseId"
              placeholder="选择课程，自动适用其下全部班级"
              :options="courseSelectOptions"
              @change="onCourseChange"
            />
          </a-form-item>
          <a-form-item label="考试日期">
            <a-date-picker v-model:value="form.examDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
        </div>
        <div class="form-grid">
          <a-form-item label="满分">
            <a-input-number v-model:value="form.fullScore" :min="1" :precision="1" style="width: 100%" />
          </a-form-item>
          <a-form-item label="计分方式">
            <a-radio-group v-model:value="form.scoringMode">
              <a-radio value="分数">分数</a-radio>
              <a-radio value="等级">等级（等级 + 评语）</a-radio>
            </a-radio-group>
          </a-form-item>
        </div>
        <a-form-item
          :label="schoolExam ? '适用班级（可批量选择）' : '适用班级（随课程自动带出）'"
          required
        >
          <template v-if="schoolExam">
            <a-select
              v-model:value="form.classIds"
              mode="multiple"
              placeholder="按课程分组批量选择班级"
              option-filter-prop="label"
              :options="schoolClassGroups"
            />
            <div class="class-tools">
              <a-button type="link" size="small" @click="selectAllSchoolClasses">全选所有班级</a-button>
              <a-button type="link" size="small" @click="form.classIds = []">清空</a-button>
            </div>
          </template>
          <template v-else>
            <div v-if="!form.courseId" class="class-hint">选择课程后，该课程下的全部班级会自动适用</div>
            <div v-else-if="!appliedClassOptions.length" class="class-hint">该课程下暂无在读班级</div>
            <div v-else class="class-tags">
              <a-tag v-for="item in appliedClassOptions" :key="item.value" color="blue">{{ item.label }}</a-tag>
              <span class="class-hint">共 {{ appliedClassOptions.length }} 个班级</span>
            </div>
          </template>
        </a-form-item>
        <a-form-item label="分项（可选，录入分项后自动汇总总分）">
          <div class="part-toolbar">
            <a-button size="small" @click="addPart">+ 添加分项</a-button>
            <a-select
              v-if="templates.length"
              size="small"
              style="width: 200px"
              placeholder="从分项模板导入"
              :options="templates.map((item) => ({ label: item.name, value: item.id }))"
              @change="(value: number) => applyTemplate(value)"
            />
          </div>
          <div v-if="!form.parts.length" class="part-empty">未配置分项时直接录入总分</div>
          <div v-for="(part, index) in form.parts" :key="index" class="part-row">
            <a-input v-model:value="part.name" placeholder="分项名称，如 笔画" style="flex: 1; min-width: 0" />
            <a-input-number v-model:value="part.weight" :min="0" :max="100" addon-after="%" style="width: 110px" />
            <a-input-number v-model:value="part.fullScore" :min="0" placeholder="分项满分" style="width: 110px" />
            <a-button type="link" size="small" danger @click="removePart(index)">删除</a-button>
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
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.class-tools {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 2px;
}

.class-hint {
  font-size: 12px;
  color: var(--text-3);
}

.class-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
}

.part-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.part-empty {
  padding: 6px 10px;
  background: #fafbfc;
  border: 1px dashed #e5e6eb;
  border-radius: 4px;
  color: var(--text-2);
  font-size: 12px;
}

.part-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
