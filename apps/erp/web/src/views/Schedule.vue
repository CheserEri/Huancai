<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { CalendarOutlined, LeftOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import {
  cancelSchedule,
  createSchedule,
  fetchScheduleClasses,
  fetchScheduleCourses,
  fetchScheduleOptions,
  fetchScheduleRooms,
  fetchScheduleSummary,
  fetchScheduleTeachers,
  fetchSchedules,
  fetchStudents,
  restoreSchedule,
  updateSchedule,
  type ScheduleFormPayload,
  type ScheduleOption,
  type ScheduleOptions,
  type ScheduleRow,
  type ScheduleSummary,
  type StudentRow,
} from '../api/client'

const weekStart = ref(startOfWeek(new Date()))
const campus = ref<'all' | number>('all')
const teacher = ref('all')
const classId = ref<'all' | number>('all')
const courseId = ref<'all' | number>('all')
const room = ref('all')
const status = ref('all')
const lessonMode = ref<'all' | number>('all')
const keyword = ref('')
const view = ref<'week' | 'day' | 'list'>('week')
const dayIndex = ref(0)
const loading = ref(false)
const rows = ref<ScheduleRow[]>([])
const summary = ref<ScheduleSummary | null>(null)
const teachers = ref<string[]>([])
const classes = ref<ScheduleOption[]>([])
const courses = ref<ScheduleOption[]>([])
const rooms = ref<string[]>([])
const current = ref<ScheduleRow | null>(null)

const options = ref<ScheduleOptions | null>(null)
/** 扁平选项，按 label 搜索，避免 a-select-option 在 v4 下搜不到中文 */
const classFilterOptions = computed(() => [
  { value: 'all', label: '全部班级' },
  ...classes.value.map((item) => ({ value: item.id, label: item.name })),
])
const classFormOptions = computed(() =>
  (options.value?.classes ?? []).map((item) => ({ value: item.id, label: item.name }))
)
const courseFilterOptions = computed(() => [
  { value: 'all', label: '全部课程' },
  ...courses.value.map((item) => ({ value: item.id, label: item.name })),
])
const courseFormOptions = computed(() =>
  (options.value?.courses ?? []).map((item) => ({ value: item.id, label: item.name }))
)
const teacherFormOptions = computed(() =>
  (options.value?.teachers ?? []).map((item) => ({ value: item.id, label: item.name }))
)
const studentFormOptions = computed(() =>
  students.value.map((item) => ({
    value: item.id,
    label: `${item.name}（${item.phone || '无电话'}）`,
  }))
)
const roomFormOptions = computed(() =>
  (options.value?.rooms ?? []).map((item) => ({ value: item, label: item }))
)
const students = ref<StudentRow[]>([])
const studentLoading = ref(false)

interface ScheduleFormState {
  campusId?: number
  lessonMode: number
  classId?: number
  studentId?: number
  courseId?: number
  teacherId?: number
  lessonDate: string
  startTime: string
  endTime: string
  room: string
  topic: string
}

function emptyScheduleForm(date?: string): ScheduleFormState {
  return {
    campusId: undefined,
    lessonMode: 1,
    classId: undefined,
    studentId: undefined,
    courseId: undefined,
    teacherId: undefined,
    lessonDate: date ?? nowDate(),
    startTime: '09:00',
    endTime: '10:30',
    room: '',
    topic: '',
  }
}

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSaving = ref(false)
const editingId = ref<number | null>(null)
const form = ref<ScheduleFormState>(emptyScheduleForm())

function nowDate() {
  return fmt(new Date())
}

function startOfWeek(d: Date) {
  const x = new Date(d)
  const day = (x.getDay() + 6) % 7
  x.setDate(x.getDate() - day)
  x.setHours(0, 0, 0, 0)
  return x
}

function fmt(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const days = computed(() => {
  const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  return labels.map((label, i) => {
    const d = new Date(weekStart.value)
    d.setDate(d.getDate() + i)
    return { label, date: fmt(d), short: `${d.getMonth() + 1}-${String(d.getDate()).padStart(2, '0')}` }
  })
})

const rangeLabel = computed(() => `${days.value[0].date} ~ ${days.value[6].date}`)

function baseQuery() {
  return {
    start: days.value[0].date,
    end: days.value[6].date,
    campusId: campus.value === 'all' ? undefined : Number(campus.value),
    teacher: teacher.value === 'all' ? undefined : teacher.value,
    classId: classId.value === 'all' ? undefined : Number(classId.value),
    courseId: courseId.value === 'all' ? undefined : Number(courseId.value),
    room: room.value === 'all' ? undefined : room.value,
    status: status.value === 'all' ? undefined : status.value,
    lessonMode: lessonMode.value === 'all' ? undefined : Number(lessonMode.value),
    keyword: keyword.value.trim() || undefined,
  }
}

async function load() {
  loading.value = true
  try {
    const query = baseQuery()
    const [list, sum] = await Promise.all([fetchSchedules(query), fetchScheduleSummary(query)])
    rows.value = list.records
    summary.value = sum
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function search() {
  await load()
}

function reset() {
  campus.value = 'all'
  teacher.value = 'all'
  classId.value = 'all'
  courseId.value = 'all'
  room.value = 'all'
  status.value = 'all'
  lessonMode.value = 'all'
  keyword.value = ''
  load()
}

function shiftWeek(n: number) {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + n * 7)
  weekStart.value = d
  load()
}

function shiftDay(n: number) {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + n)
  weekStart.value = startOfWeek(d)
  dayIndex.value = ((d.getDay() + 6) % 7)
  load()
}

function today() {
  weekStart.value = startOfWeek(new Date())
  dayIndex.value = (new Date().getDay() + 6) % 7
  load()
}

const slots = computed(() => {
  const set = new Set<string>()
  rows.value.forEach((r) => set.add(timeOf(r.startTime)))
  return Array.from(set).sort()
})

function timeOf(value: string) {
  return value ? value.slice(0, 5) : ''
}

function statusColor(value: string) {
  if (value === '已上课') return 'green'
  if (value === '休课') return 'orange'
  return 'default'
}

const grid = computed(() => {
  const map = new Map<string, ScheduleRow[]>()
  rows.value.forEach((r) => {
    const key = `${r.lessonDate}-${timeOf(r.startTime)}`
    const list = map.get(key) ?? []
    list.push(r)
    map.set(key, list)
  })
  return map
})

function cell(date: string, slot: string) {
  return grid.value.get(`${date}-${slot}`) ?? []
}

const dayRows = computed(() => rows.value.filter((r) => r.lessonDate === days.value[dayIndex.value].date))
const daySlots = computed(() => {
  const set = new Set<string>()
  dayRows.value.forEach((r) => set.add(timeOf(r.startTime)))
  return Array.from(set).sort()
})
function dayCell(slot: string) {
  return dayRows.value.filter((r) => timeOf(r.startTime) === slot)
}

const palette = ['#007AFF', '#13C2C2', '#722ED1', '#FA8C16', '#52C41A', '#EB2F96']
function colorOf(row: ScheduleRow) {
  const key = row.courseName ?? row.className ?? ''
  let h = 0
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) % 997
  return palette[h % palette.length]
}

function attendanceRate() {
  const should = summary.value?.shouldStudents ?? 0
  const actual = summary.value?.actualStudents ?? 0
  return should > 0 ? `${((actual / should) * 100).toFixed(1)}%` : '—'
}

function rateOf(row: ScheduleRow) {
  const should = row.shouldCount ?? 0
  const actual = row.actualCount ?? 0
  return should > 0 ? `${((actual / should) * 100).toFixed(0)}%` : '—'
}

function rowProps(record: ScheduleRow) {
  return { onClick: () => { current.value = record } }
}

async function ensureOptions(force = false) {
  if (options.value && !force) return
  options.value = await fetchScheduleOptions().catch(() => null)
}

async function loadStudents() {
  if (students.value.length) return
  studentLoading.value = true
  try {
    const res = await fetchStudents({ status: '在读', size: 200 })
    students.value = res.records
  } catch (e) {
    console.error(e)
  } finally {
    studentLoading.value = false
  }
}

async function openCreate() {
  await ensureOptions(true)
  formMode.value = 'create'
  editingId.value = null
  form.value = emptyScheduleForm()
  formOpen.value = true
}

async function openAdjust(row: ScheduleRow) {
  await ensureOptions(true)
  formMode.value = 'edit'
  editingId.value = row.id
  form.value = {
    campusId: row.campusId,
    lessonMode: row.lessonMode ?? 1,
    classId: row.classId ?? undefined,
    studentId: row.studentId ?? undefined,
    courseId: row.courseId ?? undefined,
    teacherId: row.teacherId ?? undefined,
    lessonDate: row.lessonDate,
    startTime: timeOf(row.startTime),
    endTime: timeOf(row.endTime),
    room: row.room ?? '',
    topic: row.topic ?? '',
  }
  current.value = null
  formOpen.value = true
}

watch(
  () => form.value.classId,
  (id) => {
    if (!id || !options.value) return
    const cls = options.value.classes.find((c) => c.id === id)
    if (!cls) return
    if (cls.campusId) form.value.campusId = cls.campusId
    if (cls.courseId) form.value.courseId = cls.courseId
    if (cls.teacherId) form.value.teacherId = cls.teacherId
    if (cls.room && !form.value.room) form.value.room = cls.room
  },
)

watch(
  () => form.value.lessonMode,
  (mode) => {
    if (mode === 1) {
      form.value.studentId = undefined
      loadStudents()
    } else {
      form.value.classId = undefined
      loadStudents()
    }
  },
)

async function submitForm() {
  if (!form.value.campusId) {
    message.warning('请选择校区')
    return
  }
  if (form.value.lessonMode === 1 && !form.value.classId) {
    message.warning('请选择班级')
    return
  }
  if (form.value.lessonMode === 2 && !form.value.studentId) {
    message.warning('请选择一对一学员')
    return
  }
  if (!form.value.lessonDate) {
    message.warning('请选择上课日期')
    return
  }
  if (!form.value.startTime || !form.value.endTime) {
    message.warning('请填写上课时间')
    return
  }
  const payload: ScheduleFormPayload = {
    campusId: form.value.campusId,
    lessonMode: form.value.lessonMode,
    lessonDate: form.value.lessonDate,
    startTime: form.value.startTime,
    endTime: form.value.endTime,
    room: form.value.room,
    topic: form.value.topic,
  }
  if (formMode.value === 'create') {
    payload.classId = form.value.classId ?? null
    payload.studentId = form.value.studentId ?? null
    payload.courseId = form.value.courseId ?? null
    payload.teacherId = form.value.teacherId ?? null
  } else {
    payload.teacherId = form.value.teacherId ?? null
    payload.courseId = form.value.courseId ?? null
  }
  formSaving.value = true
  try {
    if (formMode.value === 'create') {
      const res = await createSchedule(payload)
      message.success(`已新增 ${res.lessonDate} ${timeOf(res.startTime)} 的课次`)
    } else if (editingId.value !== null) {
      await updateSchedule(editingId.value, payload)
      message.success('调课已保存')
    }
    formOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    formSaving.value = false
  }
}

function confirmCancel(row: ScheduleRow) {
  Modal.confirm({
    title: '确认停课？',
    content: `将停掉 ${row.lessonDate} ${timeOf(row.startTime)} 的「${row.className || row.courseName || '课次'}」，停课后可在排课列表恢复。`,
    okText: '停课',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await cancelSchedule(row.id)
        message.success('已停课')
        current.value = null
        await load()
      } catch (e) {
        message.error((e as Error).message || '停课失败')
      }
    },
  })
}

async function restore(row: ScheduleRow) {
  try {
    await restoreSchedule(row.id)
    message.success('课次已恢复')
    current.value = null
    await load()
  } catch (e) {
    message.error((e as Error).message || '恢复失败')
  }
}

const listColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '时间', dataIndex: 'startTime', width: 110 },
  { title: '班级', dataIndex: 'className', width: 240, ellipsis: true },
  { title: '课程', dataIndex: 'courseName', width: 180, ellipsis: true },
  { title: '课程主题', dataIndex: 'topic', width: 160, ellipsis: true },
  { title: '教师', dataIndex: 'teacherName', width: 90 },
  { title: '助教', dataIndex: 'assistantName', width: 90 },
  { title: '教室', dataIndex: 'room', width: 90 },
  { title: '应到', dataIndex: 'shouldCount', width: 70, align: 'right' as const },
  { title: '实到', dataIndex: 'actualCount', width: 70, align: 'right' as const },
  { title: '出勤率', dataIndex: 'rate', width: 80, align: 'right' as const },
  { title: '点名', dataIndex: 'teachingLogId', width: 80 },
  { title: '状态', dataIndex: 'status', width: 80 },
]

onMounted(async () => {
  load()
  const [t, c, co, r] = await Promise.all([
    fetchScheduleTeachers().catch(() => [] as string[]),
    fetchScheduleClasses().catch(() => [] as ScheduleOption[]),
    fetchScheduleCourses().catch(() => [] as ScheduleOption[]),
    fetchScheduleRooms().catch(() => [] as string[]),
  ])
  teachers.value = t
  classes.value = c
  courses.value = co
  rooms.value = r
})
</script>

<template>
  <div class="page">
    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>校区</span>
          <a-select v-model:value="campus" size="small" style="width: 150px">
            <a-select-option value="all">全部校区</a-select-option>
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <span>教师</span>
          <a-select v-model:value="teacher" size="small" style="width: 120px" show-search allow-clear placeholder="全部">
            <a-select-option value="all">全部教师</a-select-option>
            <a-select-option v-for="name in teachers" :key="name" :value="name">{{ name }}</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <span>班级</span>
          <a-select v-model:value="classId" size="small" style="width: 210px" show-search :options="classFilterOptions" option-filter-prop="label" allow-clear placeholder="全部" />
        </div>
        <div class="filter-item">
          <span>课程</span>
          <a-select v-model:value="courseId" size="small" style="width: 180px" show-search :options="courseFilterOptions" option-filter-prop="label" allow-clear placeholder="全部" />
        </div>
        <div class="filter-item">
          <span>教室</span>
          <a-select v-model:value="room" size="small" style="width: 110px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option v-for="item in rooms" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <span>状态</span>
          <a-select v-model:value="status" size="small" style="width: 100px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option value="已上课">已上课</a-select-option>
            <a-select-option value="待上课">待上课</a-select-option>
            <a-select-option value="休课">休课</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <span>模式</span>
          <a-select v-model:value="lessonMode" size="small" style="width: 100px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option :value="1">班课</a-select-option>
            <a-select-option :value="2">一对一</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <a-input v-model:value="keyword" size="small" style="width: 180px" placeholder="班级 / 课程 / 主题 / 教师 / 教室" allow-clear @press-enter="search" />
        </div>
        <a-button type="primary" size="small" :loading="loading" @click="search"><SearchOutlined />查询</a-button>
        <a-button size="small" @click="reset">重置</a-button>
      </div>
      <div class="source-note">
        数据来源：历史排课（2019-05 至 2027-01，共 9,290 节课）。历史排课页按校内网络访问，筛选与列按已同步数据组织。
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <a-button size="small" @click="view === 'day' ? shiftDay(-1) : shiftWeek(-1)"><LeftOutlined /></a-button>
          <span class="week-label">{{ view === 'day' ? `${days[dayIndex].date} ${days[dayIndex].label}` : rangeLabel }}</span>
          <a-button size="small" @click="view === 'day' ? shiftDay(1) : shiftWeek(1)"><RightOutlined /></a-button>
          <a-button size="small" @click="today"><CalendarOutlined />本周</a-button>
          <a-radio-group v-model:value="view" size="small" button-style="solid">
            <a-radio-button value="week">周课表</a-radio-button>
            <a-radio-button value="day">日课表</a-radio-button>
            <a-radio-button value="list">列表</a-radio-button>
          </a-radio-group>
          <a-button type="primary" size="small" class="primary-btn" @click="openCreate">新增排课</a-button>
        </div>
        <div class="summary">
          <span>课次 <b>{{ summary?.total ?? 0 }}</b></span>
          <span>已上课 <b>{{ summary?.done ?? 0 }}</b></span>
          <span>待上课 <b>{{ summary?.pending ?? 0 }}</b></span>
          <span>应到 <b>{{ summary?.shouldStudents ?? 0 }}</b></span>
          <span>实到 <b>{{ summary?.actualStudents ?? 0 }}</b></span>
          <span>出勤率 <b>{{ attendanceRate() }}</b></span>
          <span>班级 <b>{{ summary?.classes ?? 0 }}</b></span>
        </div>
      </div>

      <a-spin :spinning="loading">
        <div v-if="view === 'week'" class="schedule">
          <div class="head-cell">时间</div>
          <div v-for="day in days" :key="day.date" class="head-cell day-head">
            <span>{{ day.label }}</span>
            <span class="date">{{ day.short }}</span>
          </div>

          <template v-for="slot in slots" :key="slot">
            <div class="time-cell">{{ slot }}</div>
            <div v-for="day in days" :key="`${day.date}-${slot}`" class="grid-cell">
              <div
                v-for="lesson in cell(day.date, slot)"
                :key="lesson.id"
                class="lesson"
                :style="{ borderLeftColor: colorOf(lesson) }"
                @click="current = lesson"
              >
                <div class="lesson-name">{{ lesson.className || lesson.courseName }}</div>
                <div v-if="lesson.topic" class="lesson-topic">{{ lesson.topic }}</div>
                <div class="lesson-meta">
                  {{ timeOf(lesson.startTime) }}-{{ timeOf(lesson.endTime) }} · {{ lesson.teacherName }}
                </div>
                <div class="lesson-meta">
                  <span>{{ lesson.room || '未指定教室' }}</span>
                  <a-tag :color="statusColor(lesson.status)" style="margin: 0">
                    {{ lesson.status }}
                  </a-tag>
                  <span class="count">{{ lesson.actualCount ?? 0 }}/{{ lesson.shouldCount ?? 0 }}</span>
                </div>
              </div>
            </div>
          </template>
          <div v-if="!slots.length" class="empty">本周暂无排课记录</div>
        </div>

        <div v-else-if="view === 'day'" class="schedule day-grid">
          <div class="head-cell">时间</div>
          <div class="head-cell day-head">
            <span>{{ days[dayIndex].label }}</span>
            <span class="date">{{ days[dayIndex].date }}</span>
          </div>
          <template v-for="slot in daySlots" :key="slot">
            <div class="time-cell">{{ slot }}</div>
            <div class="grid-cell">
              <div
                v-for="lesson in dayCell(slot)"
                :key="lesson.id"
                class="lesson"
                :style="{ borderLeftColor: colorOf(lesson) }"
                @click="current = lesson"
              >
                <div class="lesson-name">{{ lesson.className || lesson.courseName }}</div>
                <div v-if="lesson.topic" class="lesson-topic">{{ lesson.topic }}</div>
                <div class="lesson-meta">
                  {{ timeOf(lesson.startTime) }}-{{ timeOf(lesson.endTime) }} · {{ lesson.teacherName }} · {{ lesson.room || '未指定教室' }}
                </div>
                <div class="lesson-meta">
                  <a-tag :color="statusColor(lesson.status)" style="margin: 0">{{ lesson.status }}</a-tag>
                  <span class="count">实到 {{ lesson.actualCount ?? 0 }}/应到 {{ lesson.shouldCount ?? 0 }}（{{ rateOf(lesson) }}）</span>
                </div>
              </div>
            </div>
          </template>
          <div v-if="!daySlots.length" class="empty">当日暂无排课记录</div>
        </div>

        <a-table
          v-else
          :columns="listColumns"
          :data-source="rows"
          :pagination="{ pageSize: 20, showSizeChanger: true, pageSizeOptions: ['20', '50', '100'], showTotal: (t: number) => `共 ${t} 条` }"
          row-key="id"
          size="small"
          :scroll="{ x: 1700 }"
          :custom-row="rowProps"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'startTime'">{{ timeOf(record.startTime) }}-{{ timeOf(record.endTime) }}</span>
            <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
            <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'topic'">{{ record.topic || '—' }}</span>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'assistantName'" class="muted">{{ record.assistantName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'room'">{{ record.room || '—' }}</span>
            <span v-else-if="column.dataIndex === 'rate'">{{ rateOf(record) }}</span>
            <span v-else-if="column.dataIndex === 'teachingLogId'">
              <a-tag v-if="record.teachingLogId" color="green">已点名</a-tag>
              <span v-else class="muted">未点名</span>
            </span>
            <a-tag v-else-if="column.dataIndex === 'status'" :color="statusColor(record.status)">
              {{ record.status }}
            </a-tag>
            <span v-else>{{ record[column.dataIndex] ?? '—' }}</span>
          </template>
          <template #emptyText>
            <a-empty description="当前筛选条件下暂无排课记录" />
          </template>
        </a-table>
      </a-spin>
    </div>

    <a-drawer :open="!!current" :width="480" title="排课详情" @close="current = null">
      <template v-if="current">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="上课日期">{{ current.lessonDate }}</a-descriptions-item>
          <a-descriptions-item label="时间">{{ timeOf(current.startTime) }}-{{ timeOf(current.endTime) }}</a-descriptions-item>
          <a-descriptions-item label="班级" :span="2">{{ current.className || '—' }}</a-descriptions-item>
          <a-descriptions-item label="课程" :span="2">{{ current.courseName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="课程主题" :span="2">{{ current.topic || '—' }}</a-descriptions-item>
          <a-descriptions-item label="校区">{{ current.campusName }}</a-descriptions-item>
          <a-descriptions-item label="教室">{{ current.room || '—' }}</a-descriptions-item>
          <a-descriptions-item label="教师">{{ current.teacherName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="助教">{{ current.assistantName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="应到">{{ current.shouldCount ?? 0 }} 人</a-descriptions-item>
          <a-descriptions-item label="实到">{{ current.actualCount ?? 0 }} 人</a-descriptions-item>
          <a-descriptions-item label="出勤率">{{ rateOf(current) }}</a-descriptions-item>
          <a-descriptions-item label="点名">
            <a-tag v-if="current.teachingLogId" color="green">已点名</a-tag>
            <span v-else class="muted">未点名</span>
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusColor(current.status)">{{ current.status }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="模式">{{ current.lessonMode === 2 ? '一对一' : '班课' }}</a-descriptions-item>
        </a-descriptions>
        <div class="drawer-actions">
          <a-button type="primary" size="small" :disabled="!current.teachingLogId">查看点名记录</a-button>
          <a-button
            size="small"
            :disabled="current.status === '已上课' || current.status === '已取消' || current.status === '休课'"
            @click="openAdjust(current)"
          >
            调课
          </a-button>
          <a-button
            v-if="current.status !== '已取消' && current.status !== '休课'"
            size="small"
            danger
            :disabled="current.status === '已上课'"
            @click="confirmCancel(current)"
          >
            停课
          </a-button>
          <a-button v-else-if="current.status === '已取消'" size="small" @click="restore(current)">恢复课次</a-button>
          <span v-else class="muted">该课次已休课，请到「班级休课」中恢复</span>
        </div>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="formOpen"
      :title="formMode === 'create' ? '新增排课' : '调课'"
      :confirm-loading="formSaving"
      width="680"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitForm"
    >
      <a-form layout="vertical" class="schedule-form">
        <div class="form-grid">
          <a-form-item label="模式" required>
            <a-radio-group v-model:value="form.lessonMode" :disabled="formMode === 'edit'">
              <a-radio :value="1">班课</a-radio>
              <a-radio :value="2">一对一</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="校区" required>
            <a-select v-model:value="form.campusId" placeholder="请选择校区" allow-clear>
              <a-select-option v-for="item in options?.campuses ?? []" :key="item.id" :value="item.id">
                {{ item.name }}
              </a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item v-if="form.lessonMode === 1" label="班级" required>
            <a-select
              v-model:value="form.classId"
              placeholder="请选择班级"
              show-search
              :options="classFormOptions"
              option-filter-prop="label"
              allow-clear
              :disabled="formMode === 'edit'"
            />
          </a-form-item>
          <a-form-item v-else label="一对一学员" required>
            <a-select
              v-model:value="form.studentId"
              placeholder="搜索学员姓名"
              show-search
              :options="studentFormOptions"
              option-filter-prop="label"
              allow-clear
              :loading="studentLoading"
              :disabled="formMode === 'edit'"
            />
          </a-form-item>

          <a-form-item label="课程">
            <a-select
              v-model:value="form.courseId"
              placeholder="请选择课程"
              show-search
              :options="courseFormOptions"
              option-filter-prop="label"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="教师">
            <a-select
              v-model:value="form.teacherId"
              placeholder="请选择教师"
              show-search
              :options="teacherFormOptions"
              option-filter-prop="label"
              allow-clear
            />
          </a-form-item>

          <a-form-item label="上课日期" required>
            <a-date-picker v-model:value="form.lessonDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
          <a-form-item label="教室">
            <a-select v-model:value="form.room" placeholder="请选择或输入教室" allow-clear show-search :options="roomFormOptions" option-filter-prop="label" />
          </a-form-item>

          <a-form-item label="上课时间" required>
            <a-time-picker v-model:value="form.startTime" value-format="HH:mm" format="HH:mm" style="width: 100%" />
          </a-form-item>
          <a-form-item label="下课时间" required>
            <a-time-picker v-model:value="form.endTime" value-format="HH:mm" format="HH:mm" style="width: 100%" />
          </a-form-item>

          <a-form-item label="课程主题" class="span-2">
            <a-input v-model:value="form.topic" placeholder="如 第三单元 · 阅读理解" :maxlength="128" />
          </a-form-item>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.week-label {
  font-size: 14px;
  font-weight: 500;
  min-width: 200px;
  text-align: center;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.span-2 {
  grid-column: span 2;
}

.schedule-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.source-note {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-2);
}

.summary {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--text-2);
}

.summary b {
  color: var(--text);
  font-weight: 600;
}

.schedule {
  display: grid;
  grid-template-columns: 70px repeat(7, 1fr);
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}

.schedule.day-grid {
  grid-template-columns: 70px 1fr;
}

.head-cell {
  background: #fafafa;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-2);
  border-bottom: 1px solid var(--line);
  border-right: 1px solid var(--line);
}

.day-head {
  flex-direction: column;
  gap: 2px;
  font-weight: 500;
  color: var(--text);
}

.day-head .date {
  font-size: 11px;
  color: var(--text-3);
  font-weight: 400;
}

.time-cell {
  min-height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-3);
  border-bottom: 1px solid var(--line);
  border-right: 1px solid var(--line);
  background: #fcfcfd;
}

.grid-cell {
  min-height: 84px;
  padding: 4px;
  border-bottom: 1px solid var(--line);
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.lesson {
  background: #f7faff;
  border-left: 3px solid #007aff;
  border-radius: 3px;
  padding: 5px 6px;
  cursor: pointer;
}

.lesson:hover {
  background: #eef5ff;
}

.lesson-name {
  font-size: 12px;
  color: var(--text);
  line-height: 1.4;
}

.lesson-topic {
  font-size: 11px;
  color: #007aff;
  margin-top: 2px;
  line-height: 1.4;
}

.lesson-meta {
  font-size: 11px;
  color: var(--text-3);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.count {
  font-size: 11px;
  color: var(--text-3);
}

.empty {
  grid-column: 1 / -1;
  padding: 40px;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

.drawer-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}
</style>
