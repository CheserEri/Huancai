<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { CalendarOutlined, LeftOutlined, RightOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import {
  cancelSchedule,
  createSchedule,
  createScheduleBatch,
  fetchScheduleClasses,
  fetchScheduleCourses,
  fetchScheduleOptions,
  fetchScheduleRooms,
  fetchScheduleSummary,
  fetchScheduleTeachers,
  fetchSchedules,
  restoreSchedule,
  updateSchedule,
  type ScheduleOption,
  type ScheduleOptions,
  type ScheduleRow,
  type ScheduleSummary,
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
const campusNameById = computed(
  () => new Map((options.value?.campuses ?? []).map((item) => [item.id, item.name])),
)
const classFormOptions = computed(() =>
  (options.value?.classes ?? []).map((item) => {
    const campusName = item.campusId ? campusNameById.value.get(item.campusId) : ''
    return { value: item.id, label: campusName ? `${item.name}（${campusName}）` : item.name }
  }),
)
const courseFilterOptions = computed(() => [
  { value: 'all', label: '全部课程' },
  ...courses.value.map((item) => ({ value: item.id, label: item.name })),
])
const teacherFormOptions = computed(() =>
  (options.value?.teachers ?? []).map((item) => ({ value: item.id, label: item.name })),
)

type RepeatType = 'week' | 'biweek' | 'altDay'

interface ScheduleFormState {
  classId?: number
  method: 'free' | 'repeat'
  lessonDate: string
  startDate: string
  endDate: string
  repeatType: RepeatType
  weekdays: number[]
  holidayFilter: 'skip' | 'allow'
  startTime: string
  endTime: string
  teacherId?: number
  assistantName: string
  room: string
}

const weekdayOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]
const weekdayByName: Record<string, number> = {
  周一: 1,
  周二: 2,
  周三: 3,
  周四: 4,
  周五: 5,
  周六: 6,
  周日: 7,
}
const extraRooms = ref<string[]>([])
const roomOpen = ref(false)
const roomName = ref('')

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSaving = ref(false)
const editingId = ref<number | null>(null)
const form = ref<ScheduleFormState>(emptyScheduleForm())

const roomFormOptions = computed(() =>
  Array.from(new Set([...(options.value?.rooms ?? []), ...extraRooms.value])).map((item) => ({
    value: item,
    label: item,
  })),
)
const repeatCount = computed(() => repeatDates(form.value).length)

function nowDate() {
  return fmt(new Date())
}

function parseDate(value: string) {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function addDays(value: string, days: number) {
  const d = parseDate(value)
  d.setDate(d.getDate() + days)
  return fmt(d)
}

function emptyScheduleForm(): ScheduleFormState {
  const today = nowDate()
  return {
    classId: undefined,
    method: 'repeat',
    lessonDate: today,
    startDate: today,
    endDate: addDays(today, 90),
    repeatType: 'week',
    weekdays: [],
    holidayFilter: 'allow',
    startTime: '09:00',
    endTime: '10:30',
    teacherId: undefined,
    assistantName: '',
    room: '',
  }
}

/** 按重复规则推算上课日期，与后端生成逻辑保持一致。 */
function repeatDates(state: ScheduleFormState) {
  const result: string[] = []
  if (!state.startDate || !state.endDate) {
    return result
  }
  const start = parseDate(state.startDate)
  const end = parseDate(state.endDate)
  if (end < start) {
    return result
  }
  if (state.repeatType === 'altDay') {
    for (const d = new Date(start); d <= end; d.setDate(d.getDate() + 2)) {
      result.push(fmt(d))
    }
  } else {
    const startWeek = startOfWeek(start)
    for (const d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const day = d.getDay() === 0 ? 7 : d.getDay()
      if (!state.weekdays.includes(day)) {
        continue
      }
      if (state.repeatType === 'biweek') {
        const weeks = Math.round((startOfWeek(d).getTime() - startWeek.getTime()) / (7 * 86400000))
        if (weeks % 2 !== 0) {
          continue
        }
      }
      result.push(fmt(d))
    }
  }
  if (state.holidayFilter === 'skip') {
    return result.filter((date) => {
      const day = parseDate(date).getDay()
      return day !== 0 && day !== 6
    })
  }
  return result
}

/** 选中班级后，用班级已配置的上课时间自动带出周几与时段。 */
function applyClassSchedule(text?: string | null) {
  if (!text) {
    return
  }
  const pattern = /(周[一二三四五六日])\s*(\d{1,2}):(\d{2})\s*[-~至]\s*(\d{1,2}):(\d{2})/g
  const days: number[] = []
  let first: { start: string; end: string } | null = null
  let matched: RegExpExecArray | null
  while ((matched = pattern.exec(text)) !== null) {
    const day = weekdayByName[matched[1]]
    if (day) {
      days.push(day)
    }
    if (!first) {
      first = {
        start: `${matched[2].padStart(2, '0')}:${matched[3]}`,
        end: `${matched[4].padStart(2, '0')}:${matched[5]}`,
      }
    }
  }
  if (days.length) {
    form.value.weekdays = Array.from(new Set(days)).sort()
  }
  if (first) {
    form.value.startTime = first.start
    form.value.endTime = first.end
  }
}

function toggleAllWeekdays(checked: boolean) {
  form.value.weekdays = checked ? weekdayOptions.map((item) => item.value) : []
}

function onToggleAllWeekdays(event: any) {
  toggleAllWeekdays(Boolean(event?.target?.checked ?? event))
}

function openRoomCreate() {
  roomName.value = ''
  roomOpen.value = true
}

function confirmRoom() {
  const name = roomName.value.trim()
  if (!name) {
    message.warning('请输入教室名称')
    return
  }
  if (!extraRooms.value.includes(name)) {
    extraRooms.value.push(name)
  }
  form.value.room = name
  roomOpen.value = false
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

async function openCreate() {
  await ensureOptions(true)
  formMode.value = 'create'
  editingId.value = null
  extraRooms.value = []
  form.value = emptyScheduleForm()
  formOpen.value = true
}

async function openAdjust(row: ScheduleRow) {
  await ensureOptions(true)
  formMode.value = 'edit'
  editingId.value = row.id
  form.value = {
    classId: row.classId ?? undefined,
    method: 'free',
    lessonDate: row.lessonDate,
    startDate: row.lessonDate,
    endDate: row.lessonDate,
    repeatType: 'week',
    weekdays: [],
    holidayFilter: 'allow',
    startTime: timeOf(row.startTime),
    endTime: timeOf(row.endTime),
    teacherId: row.teacherId ?? undefined,
    assistantName: row.assistantName ?? '',
    room: row.room ?? '',
  }
  current.value = null
  formOpen.value = true
}

watch(
  () => form.value.classId,
  (id) => {
    if (formMode.value !== 'create' || !id || !options.value) return
    const cls = options.value.classes.find((c) => c.id === id)
    if (!cls) return
    if (cls.teacherId) form.value.teacherId = cls.teacherId
    form.value.assistantName = cls.assistName ?? ''
    form.value.room = cls.room ?? ''
    applyClassSchedule(cls.scheduleText)
  },
)

async function submitForm() {
  if (formMode.value === 'edit') {
    if (!form.value.lessonDate) {
      message.warning('请选择上课日期')
      return
    }
    if (!form.value.startTime || !form.value.endTime) {
      message.warning('请选择上课时段')
      return
    }
    if (form.value.startTime >= form.value.endTime) {
      message.warning('下课时间必须晚于上课时间')
      return
    }
    if (editingId.value === null) return
    formSaving.value = true
    try {
      await updateSchedule(editingId.value, {
        lessonDate: form.value.lessonDate,
        startTime: form.value.startTime,
        endTime: form.value.endTime,
        room: form.value.room,
        assistantName: form.value.assistantName,
        teacherId: form.value.teacherId ?? null,
      })
      message.success('调课已保存')
      formOpen.value = false
      await load()
    } catch (e) {
      message.error((e as Error).message || '保存失败')
    } finally {
      formSaving.value = false
    }
    return
  }

  if (!form.value.classId) {
    message.warning('请选择班级')
    return
  }
  if (!form.value.startTime || !form.value.endTime) {
    message.warning('请选择上课时段')
    return
  }
  if (form.value.startTime >= form.value.endTime) {
    message.warning('下课时间必须晚于上课时间')
    return
  }
  const cls = options.value?.classes.find((c) => c.id === form.value.classId)
  const className = cls?.name ?? '班级'

  if (form.value.method === 'free') {
    if (!form.value.lessonDate) {
      message.warning('请选择上课日期')
      return
    }
    formSaving.value = true
    try {
      const res = await createSchedule({
        campusId: cls?.campusId ?? undefined,
        classId: form.value.classId,
        lessonDate: form.value.lessonDate,
        startTime: form.value.startTime,
        endTime: form.value.endTime,
        teacherId: form.value.teacherId ?? null,
        assistantName: form.value.assistantName,
        room: form.value.room,
        lessonMode: 1,
      })
      message.success(`已为「${className}」添加 ${res.lessonDate} ${timeOf(res.startTime)} 的课次`)
      formOpen.value = false
      await load()
    } catch (e) {
      message.error((e as Error).message || '排课失败')
    } finally {
      formSaving.value = false
    }
    return
  }

  if (!form.value.startDate) {
    message.warning('请选择开始日期')
    return
  }
  if (!form.value.endDate) {
    message.warning('请选择结束日期')
    return
  }
  if (form.value.endDate < form.value.startDate) {
    message.warning('结束日期不能早于开始日期')
    return
  }
  if (form.value.repeatType !== 'altDay' && !form.value.weekdays.length) {
    message.warning('请选择周几上课')
    return
  }
  if (!repeatCount.value) {
    message.warning('所选条件下没有可排课的日期')
    return
  }
  formSaving.value = true
  try {
    const res = await createScheduleBatch({
      classId: form.value.classId,
      startDate: form.value.startDate,
      endDate: form.value.endDate,
      repeatType: form.value.repeatType,
      weekdays: form.value.weekdays,
      skipWeekend: form.value.holidayFilter === 'skip',
      startTime: form.value.startTime,
      endTime: form.value.endTime,
      teacherId: form.value.teacherId ?? null,
      assistantName: form.value.assistantName,
      room: form.value.room,
    })
    message.success(
      `已为「${res.className}」添加 ${res.created} 节课次` +
        (res.skipped ? `，跳过 ${res.skipped} 节已存在课次` : ''),
    )
    formOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '排课失败')
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
          <a-input v-model:value="keyword" size="small" style="width: 180px" placeholder="班级 / 课程 / 教师 / 教室" allow-clear @press-enter="search" />
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
          <a-button type="primary" size="small" class="primary-btn" @click="openCreate">新建排课</a-button>
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
          :scroll="{ x: 1540 }"
          :custom-row="rowProps"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'startTime'">{{ timeOf(record.startTime) }}-{{ timeOf(record.endTime) }}</span>
            <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
            <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
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
      :title="formMode === 'create' ? '新建排课' : '调课'"
      :confirm-loading="formSaving"
      centered
      wrap-class-name="schedule-modal"
      width="720"
      ok-text="确定"
      cancel-text="取消"
      @ok="submitForm"
    >
      <a-form layout="vertical" class="schedule-form">
        <div class="form-grid">
          <a-form-item v-if="formMode === 'create'" label="排课方式" required>
            <a-radio-group v-model:value="form.method">
              <a-radio value="free">自由排课</a-radio>
              <a-radio value="repeat">重复排课</a-radio>
            </a-radio-group>
          </a-form-item>

          <a-form-item label="班级" required>
            <a-select
              v-model:value="form.classId"
              placeholder="搜索并选择已建班级"
              show-search
              :options="classFormOptions"
              option-filter-prop="label"
              allow-clear
              :disabled="formMode === 'edit'"
            />
          </a-form-item>

          <template v-if="formMode === 'edit' || form.method === 'free'">
            <a-form-item label="上课日期" required>
              <a-date-picker v-model:value="form.lessonDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </template>

          <template v-if="formMode === 'create' && form.method === 'repeat'">
            <a-form-item label="开始日期" required>
              <a-date-picker v-model:value="form.startDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
            <a-form-item label="结束方式" required>
              <a-select value="date" style="width: 100%">
                <a-select-option value="date">按日期结束</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="结束日期" required>
              <a-date-picker v-model:value="form.endDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
            <a-form-item label="重复方式" required>
              <a-radio-group v-model:value="form.repeatType">
                <a-radio value="week">每周重复</a-radio>
                <a-radio value="biweek">间隔周重复</a-radio>
                <a-radio value="altDay">隔天重复</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item label="周几上课" required class="span-2">
              <div class="weekday-row">
                <a-checkbox
                  :checked="form.weekdays.length === weekdayOptions.length"
                  :indeterminate="form.weekdays.length > 0 && form.weekdays.length < weekdayOptions.length"
                  :disabled="form.repeatType === 'altDay'"
                  @change="onToggleAllWeekdays"
                >
                  全选
                </a-checkbox>
                <a-checkbox-group
                  v-model:value="form.weekdays"
                  :options="weekdayOptions"
                  :disabled="form.repeatType === 'altDay'"
                />
              </div>
            </a-form-item>
            <a-form-item label="节假日过滤" required class="span-2">
              <a-radio-group v-model:value="form.holidayFilter">
                <a-radio value="skip">节假日不排课</a-radio>
                <a-radio value="allow">节假日排课</a-radio>
              </a-radio-group>
              <span class="form-hint">节假日不排课将跳过周六、周日</span>
            </a-form-item>
            <div class="form-hint span-2 repeat-hint">按当前规则预计生成 {{ repeatCount }} 节课次</div>
          </template>

          <a-form-item label="上课时段" required class="span-2">
            <div class="slot-row">
              <a-time-picker
                v-model:value="form.startTime"
                value-format="HH:mm"
                format="HH:mm"
                :minute-step="5"
                :allow-clear="false"
                style="width: 120px"
              />
              <span class="slot-sep">至</span>
              <a-time-picker
                v-model:value="form.endTime"
                value-format="HH:mm"
                format="HH:mm"
                :minute-step="5"
                :allow-clear="false"
                style="width: 120px"
              />
            </div>
          </a-form-item>

          <a-form-item label="上课教师">
            <a-select
              v-model:value="form.teacherId"
              placeholder="请选择上课教师"
              show-search
              :options="teacherFormOptions"
              option-filter-prop="label"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="助教">
            <a-input v-model:value="form.assistantName" placeholder="选填" :maxlength="20" />
          </a-form-item>

          <a-form-item label="上课教室" class="span-2">
            <div class="slot-row">
              <a-select
                v-model:value="form.room"
                placeholder="请选择上课教室"
                allow-clear
                show-search
                :options="roomFormOptions"
                option-filter-prop="label"
                style="width: 240px"
              />
              <a-button @click="openRoomCreate">创建教室</a-button>
            </div>
          </a-form-item>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="roomOpen"
      title="创建教室"
      width="420"
      ok-text="确定"
      cancel-text="取消"
      destroy-on-close
      @ok="confirmRoom"
    >
      <a-input
        v-model:value="roomName"
        placeholder="请输入教室名称，如 301 教室"
        :maxlength="32"
        @press-enter="confirmRoom"
      />
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

.schedule-modal .ant-modal {
  max-width: calc(100vw - 32px);
}

.schedule-modal .ant-modal-body {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
  padding-right: 6px;
}

.schedule-form .ant-form-item {
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.span-2 {
  grid-column: span 2;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .span-2 {
    grid-column: span 1;
  }
}

.weekday-row,
.slot-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.slot-sep {
  color: var(--text-3);
  font-size: 12px;
}

.form-hint {
  font-size: 12px;
  color: var(--text-3);
  margin-left: 8px;
}

.repeat-hint {
  margin: -8px 0 16px;
  padding-left: 4px;
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
