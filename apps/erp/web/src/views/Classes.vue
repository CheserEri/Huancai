<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DownOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import FilterBar from '../components/FilterBar.vue'
import {
  addClassStudents,
  batchDeleteClasses,
  batchUpdateClassStatus,
  closeClass,
  createClass,
  fetchClassCategories,
  fetchClassCourses,
  fetchClassOptions,
  fetchClassRooms,
  fetchClassStudents,
  fetchClassSummary,
  fetchClassTeachers,
  fetchClasses,
  fetchStudents,
  removeClassStudent,
  updateClass,
  type ClassFormPayload,
  type ClassOptions,
  type ClassRow,
  type ClassStudentRow,
  type ClassSummary,
  type StudentRow,
} from '../api/client'

const router = useRouter()

const tab = ref<'list' | 'log'>('list')

const keyword = ref('')
const campus = ref<number | undefined>(undefined)
const room = ref<string | undefined>(undefined)
const category = ref<string | undefined>(undefined)
const course = ref<string | undefined>(undefined)
const weekday = ref<string | undefined>(undefined)
const status = ref<string | undefined>(undefined)

const teacher = ref<string | undefined>(undefined)
const enrolling = ref<number | undefined>(undefined)
const includeClosed = ref(false)

const sortField = ref<string | undefined>(undefined)
const sortOrder = ref<string | undefined>(undefined)

const rows = ref<ClassRow[]>([])
const summary = ref<ClassSummary | null>(null)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedKeys = ref<number[]>([])

const teachers = ref<string[]>([])
const courses = ref<string[]>([])
const categories = ref<string[]>([])
const rooms = ref<string[]>([])

const drawerOpen = ref(false)
const detail = ref<ClassRow | null>(null)
const students = ref<ClassStudentRow[]>([])
const studentLoading = ref(false)

interface ClassFormState {
  name: string
  campusId?: number
  courseId?: number
  teacherId?: number
  assistName: string
  lessonClassName?: string
  room: string
  scheduleText: string
  capacity: number
  startDate?: string
  endDate?: string
  status: string
  enrolling: number
  feeStandard: string
}

function emptyForm(): ClassFormState {
  return {
    name: '',
    campusId: undefined,
    courseId: undefined,
    teacherId: undefined,
    assistName: '',
    lessonClassName: undefined,
    room: '',
    scheduleText: '',
    capacity: 0,
    startDate: undefined,
    endDate: undefined,
    status: '已开班',
    enrolling: 1,
    feeStandard: '',
  }
}

const classOptions = ref<ClassOptions>({
  campuses: [],
  courses: [],
  teachers: [],
  rooms: [],
  lessonClasses: [],
})
/** 扁平选项，按 label 搜索，避免 a-select-option 在 v4 下搜不到中文 */
const courseFormOptions = computed(() =>
  classOptions.value.courses.map((item) => ({ value: item.id, label: item.name }))
)
const teacherFormOptions = computed(() =>
  classOptions.value.teachers.map((item) => ({ value: item.id, label: item.name }))
)

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSaving = ref(false)
const editingId = ref<number | null>(null)
const form = ref<ClassFormState>(emptyForm())

// ---------------- 上课时间表 ----------------
const weekdayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

interface ScheduleEntry {
  day: string
  enabled: boolean
  start: string
  end: string
}

const scheduleOpen = ref(false)
const scheduleEntries = ref<ScheduleEntry[]>([])

function emptyScheduleEntries(): ScheduleEntry[] {
  return weekdayNames.map((day) => ({ day, enabled: false, start: '17:30', end: '19:00' }))
}

function parseSchedule(text: string): ScheduleEntry[] {
  const entries = emptyScheduleEntries()
  const pattern = /(周[一二三四五六日])\s*(\d{1,2}):(\d{2})\s*[-~至]\s*(\d{1,2}):(\d{2})/g
  let matched: RegExpExecArray | null
  while ((matched = pattern.exec(text)) !== null) {
    const entry = entries.find((item) => item.day === matched![1])
    if (!entry) continue
    entry.enabled = true
    entry.start = `${matched[2].padStart(2, '0')}:${matched[3]}`
    entry.end = `${matched[4].padStart(2, '0')}:${matched[5]}`
  }
  return entries
}

function formatSchedule(entries: ScheduleEntry[]) {
  return entries
    .filter((entry) => entry.enabled)
    .map((entry) => `${entry.day} ${entry.start}-${entry.end}`)
    .join('、')
}

function openSchedule() {
  scheduleEntries.value = parseSchedule(form.value.scheduleText)
  scheduleOpen.value = true
}

function applySchedule() {
  const selected = scheduleEntries.value.filter((entry) => entry.enabled)
  if (!selected.length) {
    message.warning('请至少选择一个上课日')
    return
  }
  for (const entry of selected) {
    if (!entry.start || !entry.end) {
      message.warning(`请填写${entry.day}的上课时间`)
      return
    }
    if (entry.start >= entry.end) {
      message.warning(`${entry.day}的结束时间必须晚于开始时间`)
      return
    }
  }
  form.value.scheduleText = formatSchedule(scheduleEntries.value)
  scheduleOpen.value = false
}

const batchOpen = ref(false)
const batchSaving = ref(false)
const batchForm = ref<{ enrolling?: number; status?: string }>({})

const addOpen = ref(false)
const addKeyword = ref('')
const addLoading = ref(false)
const addSaving = ref(false)
const addCandidates = ref<StudentRow[]>([])
const addSelected = ref<number[]>([])

const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const optionalColumns = [
  { key: 'lessonClass', title: '班型' },
  { key: 'room', title: '教室' },
  { key: 'assist', title: '助教' },
  { key: 'stuRate', title: '满班率' },
  { key: 'upRate', title: '升班率' },
  { key: 'scheduleCount', title: '课次数' },
  { key: 'endDate', title: '结班日期' },
  { key: 'createBy', title: '创建人' },
  { key: 'createdAt', title: '创建时间' },
  { key: 'feeStandard', title: '收费标准' },
]
const visibleOptional = ref<string[]>([])

function baseQuery() {
  return {
    keyword: keyword.value.trim() || undefined,
    campusId: campus.value,
    room: room.value || undefined,
    courseCategory: category.value || undefined,
    courseName: course.value || undefined,
    weekday: weekday.value || undefined,
    status: status.value || undefined,
    teacher: teacher.value || undefined,
    enrolling: enrolling.value === undefined ? undefined : Number(enrolling.value),
    includeClosed: includeClosed.value,
  }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const base = baseQuery()
    const [list, sum] = await Promise.all([
      fetchClasses({
        ...base,
        sortField: sortField.value,
        sortOrder: sortOrder.value,
        page: page.value,
        size: pageSize.value,
      }),
      fetchClassSummary(base),
    ])
    rows.value = list.records
    total.value = list.total
    summary.value = sum
  } catch (e) {
    console.error(e)
    if (!silent) message.error('班级数据加载失败')
  } finally {
    if (!silent) loading.value = false
  }
}

function search() {
  page.value = 1
  selectedKeys.value = []
  load()
}

function reset() {
  keyword.value = ''
  campus.value = undefined
  room.value = undefined
  category.value = undefined
  course.value = undefined
  weekday.value = undefined
  status.value = undefined
  teacher.value = undefined
  enrolling.value = undefined
  includeClosed.value = false
  sortField.value = undefined
  sortOrder.value = undefined
  page.value = 1
  load()
}

function onTableChange(pagination: any, _filters: any, sorter: any) {
  page.value = pagination.current ?? 1
  pageSize.value = pagination.pageSize ?? 20
  const s = Array.isArray(sorter) ? sorter[0] : sorter
  if (s && s.order) {
    sortField.value = s.field
    sortOrder.value = s.order === 'ascend' ? 'asc' : 'desc'
  } else {
    sortField.value = undefined
    sortOrder.value = undefined
  }
  load()
}

function openDetailPage(record: ClassRow) {
  router.push(`/classDetail/${record.id}`)
}

async function assignStudents(record: ClassRow) {
  detail.value = record
  studentLoading.value = true
  try {
    students.value = await fetchClassStudents(record.id)
  } catch (e) {
    console.error(e)
  } finally {
    studentLoading.value = false
  }
  await openAddStudents()
}

function confirmClose(record: ClassRow) {
  Modal.confirm({
    title: '确认结班？',
    content: `结班后「${record.name}」将停止招生，仍可在班级列表查看。所有学员结课后才能结班。`,
    okText: '结班',
    cancelText: '取消',
    onOk: async () => {
      try {
        await closeClass(record.id)
        message.success('已结班')
        await load()
      } catch (e) {
        message.error((e as Error).message || '结班失败')
      }
    },
  })
}

const detailActions = [
  { key: 'lesson', label: '排课/记上课' },
  { key: 'lessonUnscheduled', label: '未排课记上课' },
  { key: 'assign', label: '分配学员' },
  { key: 'upgrade', label: '升班' },
  { key: 'schedule', label: '排课' },
  { key: 'close', label: '结班' },
  { key: 'edit', label: '编辑' },
  { key: 'delete', label: '删除班级', danger: true },
]

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  form.value = emptyForm()
  formOpen.value = true
}

function openEdit(record: ClassRow) {
  formMode.value = 'edit'
  editingId.value = record.id
  form.value = {
    name: record.name,
    campusId: record.campusId,
    courseId: record.courseId ?? undefined,
    teacherId: record.teacherId ?? undefined,
    assistName: record.assistName ?? '',
    lessonClassName: record.lessonClassName ?? undefined,
    room: record.room ?? '',
    scheduleText: record.scheduleText ?? '',
    capacity: record.capacity,
    startDate: record.startDate ?? undefined,
    endDate: record.endDate ?? undefined,
    status: record.status,
    enrolling: record.enrolling,
    feeStandard: record.feeStandard ?? '',
  }
  formOpen.value = true
}

async function submitForm() {
  if (!form.value.name.trim()) {
    message.warning('请填写班级名称')
    return
  }
  if (!form.value.campusId) {
    message.warning('请选择开班校区')
    return
  }
  const payload: ClassFormPayload = {
    name: form.value.name.trim(),
    campusId: form.value.campusId,
    courseId: form.value.courseId ?? null,
    teacherId: form.value.teacherId ?? null,
    assistName: form.value.assistName,
    lessonClassName: form.value.lessonClassName,
    room: form.value.room,
    scheduleText: form.value.scheduleText,
    capacity: form.value.capacity,
    startDate: form.value.startDate,
    endDate: form.value.endDate,
    status: form.value.status,
    enrolling: form.value.enrolling,
    feeStandard: form.value.feeStandard,
  }
  formSaving.value = true
  try {
    if (formMode.value === 'create') {
      const res = await createClass(payload)
      message.success(`已创建班级「${res.name}」`)
      page.value = 1
      await load()
    } else if (editingId.value !== null) {
      await updateClass(editingId.value, payload)
      message.success('班级已更新')
      await load()
      if (detail.value && detail.value.id === editingId.value) {
        const fresh = rows.value.find((row) => row.id === editingId.value)
        if (fresh) detail.value = fresh
      }
    }
    formOpen.value = false
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    formSaving.value = false
  }
}

function onMenuClick(event: { key?: string | number }, record: ClassRow) {
  const key = String(event.key ?? '')
  if (key === 'edit') {
    openEdit(record)
  } else if (key === 'delete') {
    confirmDelete([record.id], record.name)
  } else if (key === 'assign') {
    assignStudents(record)
  } else if (key === 'upgrade') {
    router.push({ path: '/classUpgrade', query: { sourceId: String(record.id) } })
  } else if (key === 'close') {
    confirmClose(record)
  } else if (key === 'schedule') {
    router.push('/schedule')
  } else if (key === 'lesson' || key === 'lessonUnscheduled') {
    router.push('/teachingLog')
  } else {
    openDetailPage(record)
  }
}

function confirmDelete(ids: number[], label: string) {
  Modal.confirm({
    title: '确认删除班级？',
    content: `将删除「${label}」，删除后不可恢复；已有排课记录的班级不允许删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const count = await batchDeleteClasses(ids)
        message.success(`已删除 ${count} 个班级`)
        selectedKeys.value = []
        await load()
      } catch (e) {
        message.error((e as Error).message || '删除失败')
      }
    },
  })
}

function batchDelete() {
  if (!selectedKeys.value.length) {
    message.warning('请先选择要删除的班级')
    return
  }
  confirmDelete([...selectedKeys.value], `选中的 ${selectedKeys.value.length} 个班级`)
}

function openBatchEdit() {
  if (!selectedKeys.value.length) {
    message.warning('请先选择要编辑的班级')
    return
  }
  batchForm.value = {}
  batchOpen.value = true
}

async function submitBatchEdit() {
  if (batchForm.value.enrolling === undefined && !batchForm.value.status) {
    message.warning('请选择要批量修改的内容')
    return
  }
  batchSaving.value = true
  try {
    const count = await batchUpdateClassStatus(
      selectedKeys.value,
      batchForm.value.enrolling,
      batchForm.value.status,
    )
    message.success(`已批量更新 ${count} 个班级`)
    batchOpen.value = false
    selectedKeys.value = []
    await load()
  } catch (e) {
    message.error((e as Error).message || '批量编辑失败')
  } finally {
    batchSaving.value = false
  }
}

function onSelectChange(keys: number[]) {
  selectedKeys.value = keys
}

function onAddSelectChange(keys: number[]) {
  addSelected.value = keys
}

async function refreshDetail() {
  if (!detail.value) return
  const classId = detail.value.id
  students.value = await fetchClassStudents(classId)
  await load()
  const fresh = rows.value.find((row) => row.id === classId)
  if (fresh) detail.value = fresh
}

async function openAddStudents() {
  if (!detail.value) return
  addKeyword.value = ''
  addSelected.value = []
  addOpen.value = true
  await searchCandidates()
}

async function searchCandidates() {
  addLoading.value = true
  try {
    const res = await fetchStudents({
      keyword: addKeyword.value.trim() || undefined,
      status: '在读',
      size: 50,
    })
    const existing = new Set(students.value.map((row) => row.studentId))
    addCandidates.value = res.records.filter((row) => !existing.has(row.id))
  } catch (e) {
    console.error(e)
    message.error('学员搜索失败')
  } finally {
    addLoading.value = false
  }
}

async function submitAddStudents() {
  if (!detail.value) return
  if (!addSelected.value.length) {
    message.warning('请选择要加入班级的学员')
    return
  }
  addSaving.value = true
  try {
    const res = await addClassStudents(detail.value.id, addSelected.value)
    message.success(`已分班 ${res.added} 名学员，当前班级人数 ${res.enrolled} 人`)
    addOpen.value = false
    await refreshDetail()
  } catch (e) {
    message.error((e as Error).message || '分班失败')
  } finally {
    addSaving.value = false
  }
}

function removeStudent(record: ClassStudentRow) {
  if (!detail.value) return
  Modal.confirm({
    title: '确认移出班级？',
    content: `将「${record.studentName}」移出「${detail.value.name}」，学员档案与课时不受影响。`,
    okText: '移出',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const enrolled = await removeClassStudent(detail.value!.id, record.studentId)
        message.success(`已移出，当前班级人数 ${enrolled} 人`)
        await refreshDetail()
      } catch (e) {
        message.error((e as Error).message || '移出失败')
      }
    },
  })
}

function notSupported(action: string) {
  message.info(`${action}需在平台端操作，本系统仅同步展示班级数据`)
}

function openFaq() {
  message.info('原系统「班级常见问题」帮助内容未迁移到本系统')
}

function dateOf(value: string | null) {
  return value ? value.slice(0, 10) : '—'
}

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : value
}

const summaryText = computed(() => {
  const s = summary.value
  return {
    total: s?.total ?? 0,
    active: s?.active ?? 0,
    studentCount: s?.studentCount ?? 0,
    memberCount: s?.memberCount ?? 0,
  }
})

const columns = computed<any[]>(() => {
  const list: any[] = [
    {
      title: '班级名称',
      dataIndex: 'name',
      sorter: true,
      ellipsis: true,
    },
    { title: '人数', dataIndex: 'enrolled', width: '6%', sorter: true },
    { title: '班主任', dataIndex: 'teacherName', width: '7%', ellipsis: true },
    { title: '所属课程', dataIndex: 'courseName', width: '13%', ellipsis: true },
    { title: '开班校区', dataIndex: 'campusName', width: '10%', ellipsis: true },
    { title: '开班日期', dataIndex: 'startDate', width: '9%', sorter: true },
    { title: '上课时间', dataIndex: 'scheduleText', width: '13%', ellipsis: true },
    { title: '招生状态', dataIndex: 'enrolling', width: '8%' },
  ]

  const extras: Record<string, Record<string, unknown>> = {
    lessonClass: { title: '班型', dataIndex: 'lessonClassName', width: 110, ellipsis: true },
    room: { title: '教室', dataIndex: 'room', width: 100, ellipsis: true },
    assist: { title: '助教', dataIndex: 'assistName', width: 92, ellipsis: true },
    stuRate: { title: '满班率', dataIndex: 'stuRate', width: 88, align: 'right' },
    upRate: { title: '升班率', dataIndex: 'upRate', width: 88, align: 'right' },
    scheduleCount: { title: '课次数', dataIndex: 'scheduleCount', width: 88, align: 'right' },
    endDate: { title: '结班日期', dataIndex: 'endDate', width: 112 },
    createBy: { title: '创建人', dataIndex: 'createBy', width: 92, ellipsis: true },
    createdAt: { title: '创建时间', dataIndex: 'createdAt', width: 112 },
    feeStandard: { title: '收费标准', dataIndex: 'feeStandard', width: 200, ellipsis: true },
  }

  for (const item of optionalColumns) {
    if (visibleOptional.value.includes(item.key)) list.push(extras[item.key])
  }

  list.push({ title: '操作', dataIndex: 'action', width: 64 })
  return list
})

/** 默认 9 列按百分比铺满容器；只有额外勾选自定义列时才需要横向滚动。 */
const tableScroll = computed(() =>
  visibleOptional.value.length > 0 ? { x: 'max-content' as const } : undefined,
)

const emptyText = computed(() =>
  loading.value ? '' : '当前筛选条件下暂无班级',
)

function exportClasses() {
  if (!rows.value.length) {
    message.info('当前没有可导出的班级')
    return
  }
  const header = ['班级名称', '人数', '班主任', '所属课程', '开班校区', '开班日期', '上课时间', '招生状态']
  const lines = rows.value.map((row) => [
    row.name,
    `${row.enrolledCount}/${row.capacity}`,
    row.teacherName || '',
    row.courseName || '',
    row.campusName || '',
    row.startDate || '',
    row.scheduleText || '',
    row.enrolling ? '开放招生' : '停止招生',
  ])
  const csv = [header, ...lines]
    .map((cols) => cols.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `班级列表_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

// 切回浏览器标签页时静默刷新，保证其他标签页分班/报名后人数即时同步
let lastVisibleRefresh = 0
function refreshWhenVisible() {
  if (document.visibilityState !== 'visible') return
  const now = Date.now()
  if (now - lastVisibleRefresh < 800) return
  lastVisibleRefresh = now
  load(true)
}

onMounted(async () => {
  load()
  document.addEventListener('visibilitychange', refreshWhenVisible)
  window.addEventListener('focus', refreshWhenVisible)
  const [t, cs, ca, rm] = await Promise.all([
    fetchClassTeachers().catch(() => [] as string[]),
    fetchClassCourses().catch(() => [] as string[]),
    fetchClassCategories().catch(() => [] as string[]),
    fetchClassRooms().catch(() => [] as string[]),
  ])
  teachers.value = t
  courses.value = cs
  categories.value = ca
  rooms.value = rm
  classOptions.value = await fetchClassOptions().catch(() => classOptions.value)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', refreshWhenVisible)
  window.removeEventListener('focus', refreshWhenVisible)
})
</script>

<template>
  <div class="class-page">
    <div class="page-head">
      <span class="head-label">班级</span>
      <div class="head-tabs">
        <span class="head-tab" :class="{ active: tab === 'list' }" @click="tab = 'list'">班级列表</span>
        <span class="head-tab" :class="{ active: tab === 'log' }" @click="tab = 'log'">分班操作日志</span>
      </div>
      <a class="help-link" @click="openFaq">班级常见问题</a>
    </div>

    <template v-if="tab === 'list'">
      <div class="action-row">
        <a-button type="primary" class="primary-btn" @click="openCreate">新建班级</a-button>
        <a-button @click="notSupported('导入班级')">导入班级</a-button>
      </div>

      <FilterBar @search="search" @reset="reset">
        <a-select
          v-model:value="campus"
          class="filter-select"
          placeholder="全部校区"
          allow-clear
          style="width: 150px"
          @change="search"
        >
          <a-select-option v-for="item in classOptions.campuses" :key="item.id" :value="item.id">
            {{ item.name }}
          </a-select-option>
        </a-select>

        <a-select
          v-model:value="room"
          class="filter-select"
          placeholder="教室"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option v-for="item in rooms" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <a-select
          v-model:value="category"
          class="filter-select"
          placeholder="课程类别"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option v-for="item in categories" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <a-select
          v-model:value="course"
          class="filter-select"
          placeholder="课程"
          allow-clear
          style="width: 130px"
          @change="search"
        >
          <a-select-option v-for="item in courses" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <a-select
          v-model:value="weekday"
          class="filter-select"
          placeholder="上课星期"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option v-for="item in weekdays" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <a-select
          v-model:value="status"
          class="filter-select"
          placeholder="班级状态"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option value="已开班">已开班</a-select-option>
          <a-select-option value="已结班">已结班</a-select-option>
        </a-select>

        <a-select
          v-model:value="teacher"
          class="filter-select"
          placeholder="班主任"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option v-for="item in teachers" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <a-select
          v-model:value="enrolling"
          class="filter-select"
          placeholder="招生状态"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option :value="1">开放招生</a-select-option>
          <a-select-option :value="0">停止招生</a-select-option>
        </a-select>

        <template #search>
          <a-input
            v-model:value="keyword"
            class="filter-search"
            placeholder="搜索班级名称"
            allow-clear
            @press-enter="search"
          >
            <template #prefix>
              <SearchOutlined class="search-icon" />
            </template>
          </a-input>
        </template>
      </FilterBar>

      <div class="bulk-row">
        <a-button size="small" class="bulk-btn" @click="batchDelete">批量删除</a-button>
        <a-button size="small" class="bulk-btn" @click="openBatchEdit">批量编辑</a-button>
        <a-button size="small" class="bulk-btn" @click="exportClasses">导出班级</a-button>

        <span class="bulk-spacer" />

        <a-checkbox v-model:checked="includeClosed" class="closed-check" @change="search">
          显示已结班级
        </a-checkbox>

        <a-popover trigger="click" placement="bottomRight">
          <template #content>
            <div class="column-picker">
              <a-checkbox-group v-model:value="visibleOptional" :options="optionalColumns.map((c) => ({ label: c.title, value: c.key }))" />
            </div>
          </template>
          <a-button type="primary" size="small" class="custom-btn">自定义显示列</a-button>
        </a-popover>
      </div>

      <div class="result-line">
        当前结果：共计 <b>{{ summaryText.total }}</b> 个班级 | <b>{{ summaryText.active }}</b> 班级正在开班；共计在读学员
        <b>{{ summaryText.studentCount }}</b> 名 | 共计在读人次 <b>{{ summaryText.memberCount }}</b> 个
      </div>

      <a-table
        class="class-table"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['20', '50', '100'],
          showTotal: (t: number) => `共 ${t} 条`,
          size: 'small',
        }"
        row-key="id"
        size="small"
        table-layout="fixed"
        :scroll="tableScroll"
        :row-selection="{
          selectedRowKeys: selectedKeys,
          onChange: onSelectChange,
        }"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <a v-if="column.dataIndex === 'name'" class="class-name" @click.stop="openDetailPage(record)">{{ record.name }}</a>
          <span v-else-if="column.dataIndex === 'enrolled'" class="enrolled">
            <span class="enrolled-num">{{ record.enrolledCount }}</span>/{{ record.capacity }}
          </span>
          <span v-else-if="column.dataIndex === 'teacherName'">{{ show(record.teacherName) }}</span>
          <span v-else-if="column.dataIndex === 'courseName'" class="course-link">{{ show(record.courseName) }}</span>
          <span v-else-if="column.dataIndex === 'campusName'" class="ellipsis">{{ show(record.campusName) }}</span>
          <span v-else-if="column.dataIndex === 'startDate'">{{ dateOf(record.startDate) }}</span>
          <span v-else-if="column.dataIndex === 'scheduleText'">{{ show(record.scheduleText) }}</span>
          <span v-else-if="column.dataIndex === 'enrolling'">
            <span v-if="record.enrolling" class="pill-open">开放招生</span>
            <span v-else class="pill-closed">停止招生</span>
          </span>
          <span v-else-if="column.dataIndex === 'lessonClassName'">{{ show(record.lessonClassName) }}</span>
          <span v-else-if="column.dataIndex === 'room'">{{ show(record.room) }}</span>
          <span v-else-if="column.dataIndex === 'assistName'">{{ show(record.assistName) }}</span>
          <span v-else-if="column.dataIndex === 'stuRate'">{{ record.stuRate === null ? '—' : `${record.stuRate}%` }}</span>
          <span v-else-if="column.dataIndex === 'upRate'">{{ show(record.upRate) }}</span>
          <span v-else-if="column.dataIndex === 'scheduleCount'">{{ record.scheduleCount }}</span>
          <span v-else-if="column.dataIndex === 'endDate'">{{ dateOf(record.endDate) }}</span>
          <span v-else-if="column.dataIndex === 'createBy'">{{ show(record.createBy) }}</span>
          <span v-else-if="column.dataIndex === 'createdAt'">{{ dateOf(record.createdAt) }}</span>
          <span v-else-if="column.dataIndex === 'feeStandard'" class="ellipsis">{{ show(record.feeStandard) }}</span>
          <a-dropdown v-else-if="column.dataIndex === 'action'" :trigger="['click']">
            <a class="op-link" @click.stop>操作 <DownOutlined /></a>
            <template #overlay>
              <a-menu @click="onMenuClick($event, record)">
                <a-menu-item v-for="item in detailActions" :key="item.key" :danger="item.danger">{{ item.label }}</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template #emptyText>
          <a-empty :description="emptyText" />
        </template>
      </a-table>
    </template>

    <div v-else class="log-panel">
      <a-empty description="暂无操作记录" />
    </div>

    <a-drawer v-model:open="drawerOpen" :width="820" :title="detail ? `${detail.name} · 班级详情` : '班级详情'">
      <template v-if="detail">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="班级名称" :span="2">{{ detail.name }}</a-descriptions-item>
          <a-descriptions-item label="班型">{{ show(detail.lessonClassName) }}</a-descriptions-item>
          <a-descriptions-item label="课程">{{ show(detail.courseName) }}</a-descriptions-item>
          <a-descriptions-item label="课程类别">{{ show(detail.courseCategory) }}</a-descriptions-item>
          <a-descriptions-item label="校区">{{ detail.campusName }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ show(detail.status) }}</a-descriptions-item>
          <a-descriptions-item label="班主任">{{ show(detail.teacherName) }}</a-descriptions-item>
          <a-descriptions-item label="助教">{{ show(detail.assistName) }}</a-descriptions-item>
          <a-descriptions-item label="上课时间">{{ show(detail.scheduleText) }}</a-descriptions-item>
          <a-descriptions-item label="教室">{{ show(detail.room) }}</a-descriptions-item>
          <a-descriptions-item label="人数">{{ detail.enrolledCount }} / {{ detail.capacity }} 人</a-descriptions-item>
          <a-descriptions-item label="满班率">{{ detail.stuRate === null ? '—' : `${detail.stuRate}%` }}</a-descriptions-item>
          <a-descriptions-item label="升班率">{{ show(detail.upRate) }}</a-descriptions-item>
          <a-descriptions-item label="课次数">{{ detail.scheduleCount }}</a-descriptions-item>
          <a-descriptions-item label="招生状态">{{ detail.enrolling ? '开放招生' : '停止招生' }}</a-descriptions-item>
          <a-descriptions-item label="开班日期">{{ dateOf(detail.startDate) }}</a-descriptions-item>
          <a-descriptions-item label="结班日期">{{ dateOf(detail.endDate) }}</a-descriptions-item>
          <a-descriptions-item label="创建人">{{ show(detail.createBy) }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ dateOf(detail.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="收费标准" :span="2">{{ show(detail.feeStandard) }}</a-descriptions-item>
          <a-descriptions-item label="班级来源">{{ detail.source }}</a-descriptions-item>
          <a-descriptions-item label="最近上课">{{ dateOf(detail.lastLessonDate) }}</a-descriptions-item>
          <a-descriptions-item label="排课记录数">{{ detail.lessonCount }} 节</a-descriptions-item>
          <a-descriptions-item label="学员名单">{{ detail.studentCount }} 人</a-descriptions-item>
        </a-descriptions>

        <div class="drawer-title">
          班级学员
          <span class="muted">共 {{ students.length }} 人</span>
          <span class="drawer-title-actions">
            <a-button type="link" size="small" @click="openEdit(detail)">编辑班级</a-button>
            <a-button type="primary" size="small" class="primary-btn" @click="openAddStudents">分班（添加学员）</a-button>
          </span>
        </div>

        <a-spin :spinning="studentLoading">
          <a-table :data-source="students" :pagination="false" row-key="id" size="small" :scroll="{ y: 360 }">
            <a-table-column title="学员" data-index="studentName" width="100" />
            <a-table-column title="性别" data-index="gender" width="60">
              <template #default="{ record }">{{ record.gender || '—' }}</template>
            </a-table-column>
            <a-table-column title="电话" data-index="phone" width="120">
              <template #default="{ record }">{{ record.phone || '—' }}</template>
            </a-table-column>
            <a-table-column title="状态" data-index="status" width="70">
              <template #default="{ record }">
                <a-tag>{{ record.status }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="报名日期" data-index="joinDate" width="105">
              <template #default="{ record }">{{ dateOf(record.joinDate) }}</template>
            </a-table-column>
            <a-table-column title="总课时" data-index="totalLessons" width="75" />
            <a-table-column title="已用" data-index="usedLessons" width="65" />
            <a-table-column title="剩余" data-index="remainLessons" width="65" />
            <a-table-column title="备注" data-index="remark" ellipsis>
              <template #default="{ record }">{{ record.remark || '—' }}</template>
            </a-table-column>
            <a-table-column title="操作" width="70" align="center">
              <template #default="{ record }">
                <a-button type="link" size="small" danger class="row-op" @click="removeStudent(record)">移出</a-button>
              </template>
            </a-table-column>
          </a-table>
          <a-empty
            v-if="!studentLoading && students.length === 0"
            description="该班级暂无学员，可通过右上角「分班（添加学员）」加入"
          />
        </a-spin>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="formOpen"
      :title="formMode === 'create' ? '新建班级' : '编辑班级'"
      :confirm-loading="formSaving"
      width="640"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitForm"
    >
      <a-form layout="vertical" class="class-form">
        <div class="form-grid">
          <a-form-item label="班级名称" required>
            <a-input v-model:value="form.name" placeholder="请输入班级名称" :maxlength="64" />
          </a-form-item>
          <a-form-item label="开班校区" required>
            <a-select v-model:value="form.campusId" placeholder="请选择校区" allow-clear>
              <a-select-option v-for="item in classOptions.campuses" :key="item.id" :value="item.id">
                {{ item.name }}
              </a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="所属课程">
            <a-select
              v-model:value="form.courseId"
              placeholder="请选择课程"
              allow-clear
              show-search
              :options="courseFormOptions"
              option-filter-prop="label"
            />
          </a-form-item>
          <a-form-item label="班主任">
            <a-select
              v-model:value="form.teacherId"
              placeholder="请选择班主任"
              allow-clear
              show-search
              :options="teacherFormOptions"
              option-filter-prop="label"
            />
          </a-form-item>

          <a-form-item label="助教">
            <a-input v-model:value="form.assistName" placeholder="请输入助教姓名" />
          </a-form-item>
          <a-form-item label="班型">
            <a-select v-model:value="form.lessonClassName" placeholder="请选择班型" allow-clear>
              <a-select-option v-for="item in classOptions.lessonClasses" :key="item" :value="item">
                {{ item }}
              </a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="教室">
            <a-input v-model:value="form.room" placeholder="如 301 教室" />
          </a-form-item>
          <a-form-item label="上课时间">
            <div class="schedule-picker" @click="openSchedule">
              <span v-if="form.scheduleText" class="schedule-text">{{ form.scheduleText }}</span>
              <span v-else class="schedule-placeholder">点击选择上课时间</span>
              <span class="schedule-arrow">›</span>
            </div>
          </a-form-item>

          <a-form-item label="收费标准">
            <a-input v-model:value="form.feeStandard" placeholder="如 1980 元 / 16 课时" />
          </a-form-item>

          <a-form-item label="班级状态">
            <a-select v-model:value="form.status">
              <a-select-option value="已开班">已开班</a-select-option>
              <a-select-option value="开放招生">开放招生</a-select-option>
              <a-select-option value="满班">满班</a-select-option>
              <a-select-option value="已结班">已结班</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="招生状态">
            <a-radio-group v-model:value="form.enrolling">
              <a-radio :value="1">开放招生</a-radio>
              <a-radio :value="0">停止招生</a-radio>
            </a-radio-group>
          </a-form-item>
        </div>
      </a-form>
    </a-modal>

    <!-- 上课时间表选择 -->
    <a-modal
      v-model:open="scheduleOpen"
      title="选择上课时间"
      width="560"
      ok-text="确定"
      cancel-text="取消"
      destroy-on-close
      @ok="applySchedule"
    >
      <div class="schedule-tip">勾选上课日并设置时间段，可同时选择多个上课日。</div>
      <div class="schedule-table">
        <div class="schedule-head">
          <span>星期</span>
          <span>上课</span>
          <span>开始时间</span>
          <span></span>
          <span>结束时间</span>
        </div>
        <div
          v-for="entry in scheduleEntries"
          :key="entry.day"
          class="schedule-row"
          :class="{ active: entry.enabled }"
        >
          <span class="schedule-day">{{ entry.day }}</span>
          <a-checkbox v-model:checked="entry.enabled" />
          <a-time-picker
            v-model:value="entry.start"
            format="HH:mm"
            value-format="HH:mm"
            :minute-step="5"
            :disabled="!entry.enabled"
            :allow-clear="false"
            style="width: 112px"
          />
          <span class="schedule-sep">至</span>
          <a-time-picker
            v-model:value="entry.end"
            format="HH:mm"
            value-format="HH:mm"
            :minute-step="5"
            :disabled="!entry.enabled"
            :allow-clear="false"
            style="width: 112px"
          />
        </div>
      </div>
    </a-modal>

    <a-modal
      v-model:open="batchOpen"
      title="批量编辑班级"
      :confirm-loading="batchSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitBatchEdit"
    >
      <p class="batch-tip">已选择 {{ selectedKeys.length }} 个班级，仅修改已填写的项。</p>
      <a-form layout="vertical">
        <a-form-item label="招生状态">
          <a-select v-model:value="batchForm.enrolling" placeholder="不修改" allow-clear>
            <a-select-option :value="1">开放招生</a-select-option>
            <a-select-option :value="0">停止招生</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="班级状态">
          <a-select v-model:value="batchForm.status" placeholder="不修改" allow-clear>
            <a-select-option value="已开班">已开班</a-select-option>
            <a-select-option value="已结班">已结班</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="addOpen"
      title="分班 · 添加学员"
      :confirm-loading="addSaving"
      width="760"
      ok-text="加入班级"
      cancel-text="取消"
      @ok="submitAddStudents"
    >
      <div class="add-toolbar">
        <a-input-search
          v-model:value="addKeyword"
          placeholder="搜索学员姓名或电话"
          allow-clear
          style="width: 260px"
          @search="searchCandidates"
        />
        <span class="muted">仅展示在读学员，已在本班的学员会自动过滤</span>
      </div>
      <a-table
        :data-source="addCandidates"
        :loading="addLoading"
        row-key="id"
        size="small"
        :pagination="false"
        :scroll="{ y: 320 }"
        :row-selection="{ selectedRowKeys: addSelected, onChange: onAddSelectChange }"
      >
        <a-table-column title="学员" data-index="name" width="110" />
        <a-table-column title="性别" data-index="gender" width="60" />
        <a-table-column title="电话" data-index="phone" width="130">
          <template #default="{ record }">{{ record.phone || '—' }}</template>
        </a-table-column>
        <a-table-column title="年级" data-index="grade" width="90">
          <template #default="{ record }">{{ record.grade || '—' }}</template>
        </a-table-column>
        <a-table-column title="校区" data-index="campusName" width="140" />
        <a-table-column title="状态" data-index="status" width="70" />
      </a-table>
    </a-modal>
  </div>
</template>

<style scoped>
.class-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.page-head {
  height: 54px;
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 0 -24px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line);
}

.head-label {
  font-size: 13px;
  color: var(--text-3);
  padding-right: 16px;
  border-right: 1px solid var(--line);
  line-height: 20px;
}

.head-tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 100%;
}

.head-tab {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
}

.head-tab.active {
  color: var(--brand);
  font-weight: 500;
}

.head-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 56px;
  height: 2px;
  transform: translateX(-50%);
  background: var(--brand);
  border-radius: 1px;
}

.help-link {
  margin-left: auto;
  font-size: 13px;
  cursor: pointer;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0 12px;
}

.primary-btn {
  background: var(--brand);
}

.search-icon {
  color: var(--text-2);
}

.bulk-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0 12px;
}

.bulk-btn {
  color: var(--text-2);
  border-color: #e5e6eb;
}

.bulk-spacer {
  flex: 1;
}

.closed-check {
  font-size: 13px;
  color: var(--text-2);
}

.custom-btn {
  background: var(--brand);
  margin-left: 12px;
}

.column-picker {
  min-width: 180px;
  display: flex;
  flex-direction: column;
}

.column-picker :deep(.ant-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.result-line b {
  color: var(--text);
  font-weight: 600;
  padding: 0 2px;
}

.class-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.class-table :deep(.ant-table-tbody > tr > td) {
  padding: 10px 8px;
  font-size: 13px;
}

.class-table :deep(.ant-table-tbody > tr > td) {
  border-right: 1px solid #f5f6f7;
}

.class-name {
  color: var(--brand);
  cursor: pointer;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.enrolled {
  white-space: nowrap;
}

.enrolled-num {
  color: #f53f3f;
  font-weight: 600;
}

.course-link {
  color: var(--brand);
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.pill-open {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 10px;
  background: #00b42a;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
}

.pill-closed {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 10px;
  background: #f2f3f5;
  color: var(--text-3);
  font-size: 12px;
  line-height: 18px;
}

.op-link {
  font-size: 13px;
  white-space: nowrap;
}

.log-panel {
  padding: 80px 0;
}

.drawer-title {
  margin: 20px 0 12px;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.drawer-title-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
}

.muted {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-3);
}

.row-op {
  padding: 0;
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.class-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.batch-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--text-2);
}

.add-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

/* ---------- 上课时间选择 ---------- */
.schedule-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s;
}

.schedule-picker:hover {
  border-color: var(--brand);
}

.schedule-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-placeholder {
  flex: 1;
  color: #bfbfbf;
}

.schedule-arrow {
  color: #bfbfbf;
  font-size: 16px;
  line-height: 1;
}

.schedule-tip {
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--text-2);
}

.schedule-table {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.schedule-head,
.schedule-row {
  display: grid;
  grid-template-columns: 60px 52px 112px 28px 112px;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
}

.schedule-head {
  background: #fafafa;
  font-size: 12px;
  color: var(--text-2);
}

.schedule-row {
  border-top: 1px solid #f5f5f5;
}

.schedule-row.active {
  background: #f5f9ff;
}

.schedule-day {
  font-size: 13px;
  color: var(--text);
}

.schedule-sep {
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
}
</style>
