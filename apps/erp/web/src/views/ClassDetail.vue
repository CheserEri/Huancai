<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftOutlined, DownOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import {
  addClassStudents,
  closeClass,
  fetchClassAttendance,
  fetchClassComments,
  fetchClassDetail,
  fetchClassOptions,
  fetchClassPhotos,
  fetchClassSchedules,
  fetchClassStudents,
  fetchGradeClassExams,
  fetchHomeworks,
  fetchStudents,
  removeClassStudent,
  updateClass,
  type ClassAttendanceRow,
  type ClassCommentRow,
  type ClassFormPayload,
  type ClassOptions,
  type ClassPhoto,
  type ClassRow,
  type ClassScheduleRow,
  type ClassStudentRow,
  type GradeClassExam,
  type HomeworkRow,
  type StudentRow,
} from '../api/client'

const route = useRoute()
const router = useRouter()

const classId = computed(() => Number(route.params.id))

const detail = ref<ClassRow | null>(null)
const students = ref<ClassStudentRow[]>([])
const loading = ref(false)
const studentLoading = ref(false)
const keyword = ref('')
const showInactive = ref(false)
const selectedKeys = ref<number[]>([])
const visibleOptional = ref<string[]>(['relation', 'status'])

const tabs = [
  { key: 'students', label: '学员' },
  { key: 'trial', label: '试听学员' },
  { key: 'schedule', label: '排课' },
  { key: 'records', label: '上课记录' },
  { key: 'score', label: '成绩' },
  { key: 'comment', label: '课堂点评' },
  { key: 'homework', label: '作业' },
  { key: 'album', label: '班级相册' },
  { key: 'attendance', label: '点名表' },
  { key: 'info', label: '信息表' },
]
const tab = ref('students')

const schedules = ref<ClassScheduleRow[]>([])
const records = ref<ClassScheduleRow[]>([])
const comments = ref<ClassCommentRow[]>([])
const scheduleLoading = ref(false)
const recordLoading = ref(false)
const commentLoading = ref(false)
const loadedTabs = ref<Record<string, boolean>>({})

const gradeExams = ref<GradeClassExam[]>([])
const gradeLoading = ref(false)
const gradeColumns = [
  { title: '考试名称', dataIndex: 'examName' },
  { title: '类型', dataIndex: 'examType', width: 96 },
  { title: '考试日期', dataIndex: 'examDate', width: 110 },
  { title: '录入进度', dataIndex: 'progress', width: 100, align: 'center' as const },
  { title: '平均分', dataIndex: 'avgScore', width: 90, align: 'right' as const },
  { title: '最高分', dataIndex: 'maxScore', width: 90, align: 'right' as const },
  { title: '最低分', dataIndex: 'minScore', width: 90, align: 'right' as const },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', dataIndex: 'action', width: 100 },
]

const recordPeriod = ref<'week' | 'month' | 'custom'>('custom')
const recordRange = ref<string[] | null>(null)
const recordTimeSlot = ref('all')
const tuitionCalc = ref(false)

const attendanceOpen = ref(false)
const attendanceTitle = ref('')
const attendanceRows = ref<ClassAttendanceRow[]>([])
const attendanceLoading = ref(false)

const classOptions = ref<ClassOptions>({
  campuses: [],
  courses: [],
  teachers: [],
  rooms: [],
  lessonClasses: [],
})

const addOpen = ref(false)
const addKeyword = ref('')
const addLoading = ref(false)
const addSaving = ref(false)
const addCandidates = ref<StudentRow[]>([])
const addSelected = ref<number[]>([])

const editOpen = ref(false)
const editSaving = ref(false)
const editForm = ref({
  name: '',
  campusId: undefined as number | undefined,
  courseId: undefined as number | undefined,
  teacherId: undefined as number | undefined,
  assistName: '',
  lessonClassName: undefined as string | undefined,
  room: '',
  scheduleText: '',
  capacity: 0,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  status: '已开班',
  enrolling: 1,
  feeStandard: '',
})

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : value
}

function dateOf(value: string | null | undefined) {
  return value ? String(value).slice(0, 10) : '—'
}

function lessons(value: number | null | undefined) {
  return `${Number(value ?? 0).toFixed(2)}课时`
}

const statusText = computed(() => {
  const s = detail.value?.status
  if (!s || s === '已开班') return '开班中'
  if (s === '已结班') return '已结班'
  return s
})

const activeCount = computed(() => students.value.filter((row) => row.status === '在读').length)

const fullRate = computed(() => {
  const d = detail.value
  if (!d || !d.capacity) return '—'
  return `${(d.enrolledCount * 100 / d.capacity).toFixed(2)}%`
})

const rateOf = (status: string) => {
  const total = students.value.length
  if (!total) return '0.00%'
  const count = students.value.filter((row) => row.status === status).length
  return `${(count * 100 / total).toFixed(2)}%`
}

const newRate = computed(() => {
  const d = detail.value
  const total = students.value.length
  if (!d || !d.startDate || !total) return '0.00%'
  const start = String(d.startDate).slice(0, 10)
  const fresh = students.value.filter((row) => (row.joinDate ?? '').slice(0, 10) >= start).length
  return `${(fresh * 100 / total).toFixed(2)}%`
})

const attendanceRateText = computed(() => {
  const rate = detail.value?.attendanceRate
  return rate === null || rate === undefined ? '—' : `${Number(rate).toFixed(2)}%`
})

const infoRows = computed(() => {
  const d = detail.value
  if (!d) return []
  const rows: Array<[string, unknown]> = [
    ['班级名称', d.name],
    ['所属课程', d.courseName],
    ['课程分类', d.courseCategory],
    ['上课校区', d.campusName],
    ['班主任', d.teacherName],
    ['助教', d.assistName],
    ['班型', d.lessonClassName],
    ['教室', d.room],
    ['上课时间', d.scheduleText],
    ['开班日期', dateOf(d.startDate)],
    ['结班日期', dateOf(d.endDate)],
    ['班级容量', d.capacity],
    ['班级人数', d.enrolledCount],
    ['招生状态', d.enrolling ? '招生中' : '停止招生'],
    ['收费标准', d.feeStandard],
    ['班级状态', statusText.value],
    ['数据来源', d.source],
    ['校宝班级ID', d.spClassId],
    ['创建时间', d.createdAt ? String(d.createdAt).replace('T', ' ').slice(0, 16) : null],
  ]
  return rows.map(([label, value]) => ({ key: label, label, value: show(value) }))
})

function timeOf(row: ClassScheduleRow) {
  const start = row.startTime ? String(row.startTime).slice(0, 5) : ''
  const end = row.endTime ? String(row.endTime).slice(0, 5) : ''
  return start && end ? `${start}-${end}` : show(start)
}

const scheduleColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '上课时间', dataIndex: 'time', width: 120 },
  { title: '课程', dataIndex: 'courseName', width: 150, ellipsis: true },
  { title: '教室', dataIndex: 'room', width: 100 },
  { title: '课程主题', dataIndex: 'topic', width: 160, ellipsis: true },
  { title: '教师', dataIndex: 'teacherName', width: 90 },
  { title: '应到/实到', dataIndex: 'counts', width: 100, align: 'center' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', dataIndex: 'action', width: 84, fixed: 'right' },
]

const recordColumns = [
  {
    title: '上课日期',
    dataIndex: 'lessonDate',
    width: 106,
    sorter: (a: ClassScheduleRow, b: ClassScheduleRow) =>
      String(a.lessonDate).localeCompare(String(b.lessonDate)),
  },
  { title: '教师', dataIndex: 'teacherName', width: 86 },
  { title: '助教', dataIndex: 'assistantName', width: 76 },
  { title: '科目', dataIndex: 'courseName', width: 130, ellipsis: true },
  { title: '实到/应到', dataIndex: 'counts', width: 92, align: 'center' },
  { title: '出勤率', dataIndex: 'attendanceRate', width: 88, align: 'right' },
  { title: '到课', dataIndex: 'presentCount', width: 62, align: 'right' },
  { title: '请假', dataIndex: 'leaveCount', width: 62, align: 'right' },
  { title: '旷课', dataIndex: 'absentCount', width: 62, align: 'right' },
  { title: '迟到', dataIndex: 'lateCount', width: 62, align: 'right' },
  { title: '学员课时', dataIndex: 'lessonCount', width: 84, align: 'right' },
  { title: '上课内容', dataIndex: 'topic', ellipsis: true },
  {
    title: '创建日期',
    dataIndex: 'createdAt',
    width: 104,
    sorter: (a: ClassScheduleRow, b: ClassScheduleRow) =>
      String(a.createdAt ?? '').localeCompare(String(b.createdAt ?? '')),
  },
  { title: '操作', dataIndex: 'action', width: 110, fixed: 'right' },
]

const commentColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '教师', dataIndex: 'teacherName', width: 90 },
  { title: '评价', dataIndex: 'rating', width: 90 },
  { title: '点评内容', dataIndex: 'content', ellipsis: true },
  { title: '阅读状态', dataIndex: 'readAt', width: 90 },
]

const attendanceLessonColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '上课时间', dataIndex: 'time', width: 120 },
  { title: '课程主题', dataIndex: 'topic', width: 160, ellipsis: true },
  { title: '教师', dataIndex: 'teacherName', width: 90 },
  { title: '应到/实到', dataIndex: 'counts', width: 100, align: 'center' },
  { title: '点名人数', dataIndex: 'attendanceCount', width: 90, align: 'right' },
  { title: '操作', dataIndex: 'action', width: 84 },
]

const attendanceDetailColumns = [
  { title: '学员姓名', dataIndex: 'studentName', width: 120 },
  { title: '出勤状态', dataIndex: 'status', width: 90 },
  { title: '扣减课时', dataIndex: 'lessons', width: 100, align: 'right' },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
]

const attendanceLessons = computed(() => schedules.value.filter((row) => row.attendanceCount > 0))

function fmtDate(value: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
}

function applyRecordPeriod(period: 'week' | 'month') {
  recordPeriod.value = period
  const now = new Date()
  if (period === 'week') {
    const day = now.getDay()
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (day === 0 ? 6 : day - 1))
    const end = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 6)
    recordRange.value = [fmtDate(start), fmtDate(end)]
  } else {
    recordRange.value = [
      fmtDate(new Date(now.getFullYear(), now.getMonth(), 1)),
      fmtDate(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
    ]
  }
}

function onRecordRangeChange() {
  recordPeriod.value = 'custom'
}

const filteredRecords = computed(() => {
  const [start, end] = recordRange.value ?? []
  return records.value.filter((row) => {
    const date = String(row.lessonDate ?? '').slice(0, 10)
    if (start && date < start) return false
    if (end && date > end) return false
    const hour = Number(String(row.startTime ?? '').slice(0, 2))
    if (recordTimeSlot.value === 'am' && !(hour < 12)) return false
    if (recordTimeSlot.value === 'pm' && !(hour >= 12 && hour < 18)) return false
    if (recordTimeSlot.value === 'night' && !(hour >= 18)) return false
    return true
  })
})

const recordLessonTotal = computed(() =>
  filteredRecords.value.reduce((sum, row) => sum + Number(row.lessonCount ?? 0), 0),
)

const recordRateTotal = computed(() => {
  const total = filteredRecords.value.reduce((sum, row) => sum + Number(row.attendanceCount ?? 0), 0)
  const present = filteredRecords.value.reduce((sum, row) => sum + Number(row.presentCount ?? 0), 0)
  return total ? `${(present * 100 / total).toFixed(2)}%` : '—'
})

function exportRecords() {
  if (!filteredRecords.value.length) {
    message.info('当前没有可导出的上课记录')
    return
  }
  const header = ['上课日期', '教师', '助教', '科目', '实到', '应到', '出勤率', '到课', '请假', '旷课', '迟到', '学员课时', '上课内容', '创建日期']
  const lines = filteredRecords.value.map((row) => [
    String(row.lessonDate ?? '').slice(0, 10),
    row.teacherName || '',
    row.assistantName || '',
    row.courseName || '',
    row.actualCount ?? '',
    row.shouldCount ?? '',
    row.attendanceRate == null ? '' : `${Number(row.attendanceRate).toFixed(2)}%`,
    row.presentCount,
    row.leaveCount,
    row.absentCount,
    row.lateCount,
    Number(row.lessonCount ?? 0),
    row.topic || '',
    String(row.createdAt ?? '').slice(0, 10),
  ])
  const csv = [header, ...lines]
    .map((cols) => cols.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const url = URL.createObjectURL(new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `${detail.value?.name ?? '班级'}_上课记录_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

const studentColumns = computed(() => {
  const list: Record<string, unknown>[] = [
    { title: '学员姓名', dataIndex: 'studentName', width: 130, fixed: 'left' },
    { title: '性别', dataIndex: 'gender', width: 70 },
  ]
  if (visibleOptional.value.includes('relation')) list.push({ title: '关系', dataIndex: 'relation', width: 86 })
  list.push({ title: '联系方式', dataIndex: 'phone', width: 140 })

  if (visibleOptional.value.includes('status')) list.push({ title: '学员状态', dataIndex: 'status', width: 90 })
  list.push({ title: '已用数量', dataIndex: 'usedLessons', width: 100, align: 'right' })
  list.push({ title: '剩余数量', dataIndex: 'remainLessons', width: 100, align: 'right' })
  list.push({ title: '操作', dataIndex: 'action', width: 84, fixed: 'right' })
  return list
})

const filteredStudents = computed(() => {
  const kw = keyword.value.trim()
  return students.value.filter((row) => {
    if (!showInactive.value && row.status !== '在读') return false
    if (!kw) return true
    return (row.studentName ?? '').includes(kw) || (row.phone ?? '').includes(kw)
  })
})

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await fetchClassDetail(classId.value)
  } catch (e) {
    message.error((e as Error).message || '班级信息加载失败')
  } finally {
    loading.value = false
  }
}

/** 结班：必须先让班里所有学员结课，未结课时后端会返回具体原因。 */
function confirmClose() {
  const d = detail.value
  if (!d) return
  Modal.confirm({
    title: '确认结班？',
    content: `结班后「${d.name}」将停止招生，结班日期记为今天。需所有学员均已结课才能结班。`,
    okText: '结班',
    cancelText: '取消',
    onOk: async () => {
      try {
        await closeClass(d.id)
        message.success('已结班')
        await loadDetail()
      } catch (e) {
        message.error((e as Error).message || '结班失败')
      }
    },
  })
}

async function loadStudents() {
  studentLoading.value = true
  try {
    students.value = await fetchClassStudents(classId.value)
  } catch (e) {
    console.error(e)
  } finally {
    studentLoading.value = false
  }
}

function onAddSelectChange(keys: (string | number)[]) {
  addSelected.value = keys.map((key) => Number(key))
}

function onSelectChange(keys: (string | number)[]) {
  selectedKeys.value = keys.map((key) => Number(key))
}

function openEdit() {
  const d = detail.value
  if (!d) return
  editForm.value = {
    name: d.name,
    campusId: d.campusId,
    courseId: d.courseId ?? undefined,
    teacherId: d.teacherId ?? undefined,
    assistName: d.assistName ?? '',
    lessonClassName: d.lessonClassName ?? undefined,
    room: d.room ?? '',
    scheduleText: d.scheduleText ?? '',
    capacity: d.capacity,
    startDate: d.startDate ?? undefined,
    endDate: d.endDate ?? undefined,
    status: d.status,
    enrolling: d.enrolling,
    feeStandard: d.feeStandard ?? '',
  }
  editOpen.value = true
}

async function submitEdit() {
  if (!editForm.value.name.trim()) {
    message.warning('请填写班级名称')
    return
  }
  if (!editForm.value.campusId) {
    message.warning('请选择开班校区')
    return
  }
  const payload: ClassFormPayload = {
    name: editForm.value.name.trim(),
    campusId: editForm.value.campusId,
    courseId: editForm.value.courseId ?? null,
    teacherId: editForm.value.teacherId ?? null,
    assistName: editForm.value.assistName,
    lessonClassName: editForm.value.lessonClassName,
    room: editForm.value.room,
    scheduleText: editForm.value.scheduleText,
    capacity: editForm.value.capacity,
    startDate: editForm.value.startDate,
    endDate: editForm.value.endDate,
    status: editForm.value.status,
    enrolling: editForm.value.enrolling,
    feeStandard: editForm.value.feeStandard,
  }
  editSaving.value = true
  try {
    await updateClass(classId.value, payload)
    message.success('班级已更新')
    editOpen.value = false
    await loadDetail()
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    editSaving.value = false
  }
}

async function openAddStudents() {
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
  if (!addSelected.value.length) {
    message.warning('请选择要加入班级的学员')
    return
  }
  addSaving.value = true
  try {
    const res = await addClassStudents(classId.value, addSelected.value)
    message.success(`已分班 ${res.added} 名学员，当前班级人数 ${res.enrolled} 人`)
    addOpen.value = false
    await Promise.all([loadDetail(), loadStudents()])
  } catch (e) {
    message.error((e as Error).message || '分班失败')
  } finally {
    addSaving.value = false
  }
}

function removeStudent(record: ClassStudentRow) {
  const d = detail.value
  if (!d) return
  Modal.confirm({
    title: '确认移出班级？',
    content: `将「${record.studentName}」移出「${d.name}」，学员档案与课时不受影响。`,
    okText: '移出',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const enrolled = await removeClassStudent(classId.value, record.studentId)
        message.success(`已移出，当前班级人数 ${enrolled} 人`)
        await Promise.all([loadDetail(), loadStudents()])
      } catch (e) {
        message.error((e as Error).message || '移出失败')
      }
    },
  })
}

function exportStudents() {
  if (!filteredStudents.value.length) {
    message.info('当前没有可导出的学员')
    return
  }
  const header = ['学员姓名', '性别', '关系', '联系方式', '学员状态', '已用数量', '剩余数量']
  const lines = filteredStudents.value.map((row) => [
    row.studentName,
    row.gender || '',
    row.relation || '',
    row.phone || '',
    row.status,
    lessons(row.usedLessons),
    lessons(row.remainLessons),
  ])
  const csv = [header, ...lines]
    .map((cols) => cols.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const url = URL.createObjectURL(new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `${detail.value?.name ?? '班级'}_学员_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function notSupported(action: string) {
  message.info(`${action}暂未迁移到本系统`)
}

function onStudentMenu(key: string | number, record: ClassStudentRow) {
  if (String(key) === 'profile') {
    router.push('/student')
  } else if (String(key) === 'remove') {
    removeStudent(record)
  }
}

async function loadSchedules() {
  if (loadedTabs.value.schedule) return
  scheduleLoading.value = true
  try {
    schedules.value = await fetchClassSchedules(classId.value)
    loadedTabs.value.schedule = true
  } catch (e) {
    message.error((e as Error).message || '排课数据加载失败')
  } finally {
    scheduleLoading.value = false
  }
}

async function loadRecords() {
  if (loadedTabs.value.records) return
  recordLoading.value = true
  try {
    records.value = await fetchClassSchedules(classId.value, true)
    loadedTabs.value.records = true
    if (!recordRange.value) {
      if (records.value.length) {
        const dates = records.value.map((row) => String(row.lessonDate).slice(0, 10))
        recordRange.value = [dates[dates.length - 1], dates[0]]
      } else {
        applyRecordPeriod('week')
      }
    }
  } catch (e) {
    message.error((e as Error).message || '上课记录加载失败')
  } finally {
    recordLoading.value = false
  }
}

async function loadComments() {
  if (loadedTabs.value.comment) return
  commentLoading.value = true
  try {
    comments.value = await fetchClassComments(classId.value)
    loadedTabs.value.comment = true
  } catch (e) {
    message.error((e as Error).message || '课堂点评加载失败')
  } finally {
    commentLoading.value = false
  }
}

const photos = ref<ClassPhoto[]>([])
const photoLoading = ref(false)

async function loadPhotos() {
  photoLoading.value = true
  try {
    photos.value = await fetchClassPhotos(classId.value)
  } catch (e) {
    message.error((e as Error).message || '班级相册加载失败')
  } finally {
    photoLoading.value = false
  }
}

const homeworks = ref<HomeworkRow[]>([])
const homeworkLoading = ref(false)
const homeworkColumns = [
  { title: '发布日期', dataIndex: 'publishDate', width: 110 },
  { title: '作业标题', dataIndex: 'title', width: 220, ellipsis: true },
  { title: '作业内容', dataIndex: 'content', ellipsis: true },
  { title: '截止日期', dataIndex: 'dueDate', width: 110 },
  { title: '发布人', dataIndex: 'publisher', width: 90 },
  { title: '状态', dataIndex: 'status', width: 80 },
]

async function loadHomeworks() {
  if (loadedTabs.value.homework) return
  homeworkLoading.value = true
  try {
    const res = await fetchHomeworks({ classId: classId.value, page: 1, size: 100 })
    homeworks.value = res.records
    loadedTabs.value.homework = true
  } catch (e) {
    message.error((e as Error).message || '作业加载失败')
  } finally {
    homeworkLoading.value = false
  }
}

function photoTime(value: string | null | undefined) {
  if (!value) return ''
  const text = String(value)
  return text.length >= 16 ? `${text.slice(0, 10)} ${text.slice(11, 16)}` : text.slice(0, 10)
}

async function loadGradeExams() {
  if (loadedTabs.value.score) return
  gradeLoading.value = true
  try {
    gradeExams.value = await fetchGradeClassExams(classId.value)
    loadedTabs.value.score = true
  } catch (e) {
    message.error((e as Error).message || '成绩数据加载失败')
  } finally {
    gradeLoading.value = false
  }
}

function goGradeEntry(record: GradeClassExam) {
  router.push({ path: '/gradeEntry', query: { examId: record.examId, classId: classId.value } })
}

async function switchTab(key: string) {
  tab.value = key
  if (key === 'schedule' || key === 'attendance') await loadSchedules()
  if (key === 'records') await loadRecords()
  if (key === 'comment') await loadComments()
  if (key === 'homework') await loadHomeworks()
  if (key === 'score') await loadGradeExams()
  if (key === 'album') await loadPhotos()
}

async function openAttendance(record: ClassScheduleRow) {
  attendanceTitle.value = `${record.lessonDate} ${timeOf(record)} ${record.topic || record.courseName || ''}`
  attendanceOpen.value = true
  attendanceLoading.value = true
  try {
    attendanceRows.value = await fetchClassAttendance(classId.value, record.id)
  } catch (e) {
    message.error((e as Error).message || '点名表加载失败')
  } finally {
    attendanceLoading.value = false
  }
}

// 切回浏览器标签页时刷新，保证其他标签页分班后班级人数即时同步
let lastVisibleRefresh = 0
function refreshWhenVisible() {
  if (document.visibilityState !== 'visible') return
  const now = Date.now()
  if (now - lastVisibleRefresh < 800) return
  lastVisibleRefresh = now
  loadDetail()
  loadStudents()
}

onMounted(async () => {
  await Promise.all([loadDetail(), loadStudents()])
  document.addEventListener('visibilitychange', refreshWhenVisible)
  window.addEventListener('focus', refreshWhenVisible)
  classOptions.value = await fetchClassOptions().catch(() => classOptions.value)
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', refreshWhenVisible)
  window.removeEventListener('focus', refreshWhenVisible)
})
</script>

<template>
  <div class="class-detail">
    <a-spin :spinning="loading">
      <div v-if="detail" class="detail-head">
        <a class="back-btn" @click="router.push('/class')"><ArrowLeftOutlined /></a>
        <span class="head-title">{{ detail.name }}</span>
        <span class="status-pill" :class="{ closed: detail.status === '已结班' }">{{ statusText }}</span>
        <span class="head-spacer" />
        <a-button v-if="detail.status !== '已结班'" size="small" @click="confirmClose">结班</a-button>
        <a-button size="small" @click="openEdit">编辑</a-button>
      </div>

      <div v-if="detail" class="info-grid">
        <div class="info-item">
          <span class="info-label">所属课程</span>
          <span class="info-value link">{{ show(detail.courseName) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">收费标准</span>
          <span class="info-value ellipsis" :title="detail.feeStandard ?? ''">{{ show(detail.feeStandard) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">上课校区</span>
          <span class="info-value">{{ show(detail.campusName) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">开班日期</span>
          <span class="info-value">{{ dateOf(detail.startDate) }}</span>
        </div>

        <div class="info-item">
          <span class="info-label">结班日期</span>
          <span class="info-value">{{ dateOf(detail.endDate) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">班级人数</span>
          <span class="info-value people">
            {{ detail.enrolledCount }}/{{ detail.capacity }}
            <a-progress
              :percent="detail.capacity ? Math.min(detail.enrolledCount * 100 / detail.capacity, 100) : 0"
              :show-info="false"
              size="small"
              class="people-bar"
            />
            <span class="people-state">{{ detail.enrolling ? '（招生中）' : '（停止招生）' }}</span>
          </span>
        </div>
        <div class="info-item">
          <span class="info-label">班主任</span>
          <span class="info-value">{{ show(detail.teacherName) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">助教</span>
          <span class="info-value">{{ show(detail.assistName) }}</span>
        </div>

        <div class="info-item">
          <span class="info-label">教室</span>
          <span class="info-value">{{ show(detail.room) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">上课时间</span>
          <span class="info-value">{{ show(detail.scheduleText) }}</span>
        </div>
        <div class="info-item span-2">
          <span class="info-label">备注</span>
          <span class="info-value">—</span>
        </div>
      </div>

      <div v-if="detail" class="stat-line">
        <span class="stat-label">班级统计</span>
        <span class="stat-item">在读学生：<b>{{ activeCount }}</b></span>
        <span class="stat-item">满班率：<b>{{ fullRate }}</b></span>
        <span class="stat-item">新生率：<b>{{ newRate }}</b></span>
        <span class="stat-item">转出率：<b>{{ rateOf('转出') }}</b></span>
        <span class="stat-item">退费率：<b>{{ rateOf('退费') }}</b></span>
        <span class="stat-item">停课率：<b>{{ rateOf('停课') }}</b></span>
        <span class="stat-item">升班率：<b>{{ detail.upRate || '0.00%' }}</b></span>
        <span class="stat-item">出勤率：<b>{{ attendanceRateText }}</b></span>
      </div>

      <div class="detail-tabs">
        <span
          v-for="item in tabs"
          :key="item.key"
          class="detail-tab"
          @click="switchTab(item.key)"
        >
          <span class="detail-tab-text" :class="{ active: tab === item.key }">{{ item.label }}</span>
        </span>
      </div>

      <template v-if="tab === 'students'">
        <div class="toolbar">
          <a-button type="primary" class="primary-btn" @click="openAddStudents">添加学员</a-button>
          <a-button @click="exportStudents">导出</a-button>
          <span class="toolbar-spacer" />
          <a-input
            v-model:value="keyword"
            class="search-input"
            placeholder="请输入姓名或关键字搜索"
            allow-clear
          >
            <template #suffix><SearchOutlined class="search-icon" /></template>
          </a-input>
        </div>

        <div class="batch-row">
          <span class="toolbar-spacer" />

          <a-checkbox v-model:checked="showInactive" class="closed-check">显示转、退、停、结学员</a-checkbox>

          <a-popover trigger="click" placement="bottomRight">
            <template #content>
              <div class="column-picker">
                <a-checkbox-group
                  v-model:value="visibleOptional"
                  :options="[
                    { label: '关系', value: 'relation' },
                    { label: '学员状态', value: 'status' },
                  ]"
                />
              </div>
            </template>
            <a-button type="primary" size="small" class="custom-btn">自定义显示列</a-button>
          </a-popover>
        </div>

        <div class="result-line">当前结果：共计学员 {{ filteredStudents.length }} 个</div>

        <a-table
          :columns="studentColumns"
          :data-source="filteredStudents"
          :loading="studentLoading"
          :pagination="false"
          :row-selection="{ selectedRowKeys: selectedKeys, onChange: onSelectChange }"
          row-key="id"
          size="small"
          :scroll="{ x: 720 }"
        >
          <template #bodyCell="{ column, record }">
            <router-link
              v-if="column.dataIndex === 'studentName'"
              class="student-name"
              :to="`/studentDetail/${record.studentId}`"
            >{{ record.studentName }}</router-link>
            <span v-else-if="column.dataIndex === 'gender'">{{ record.gender || '未知' }}</span>
            <span v-else-if="column.dataIndex === 'relation'">{{ show(record.relation) }}</span>
            <span v-else-if="column.dataIndex === 'phone'">{{ show(record.phone) }}</span>
            <span v-else-if="column.dataIndex === 'status'">
              <a-tag :color="record.status === '在读' ? 'green' : 'default'">{{ record.status }}</a-tag>
            </span>
            <span v-else-if="column.dataIndex === 'usedLessons'" class="used-text">{{ lessons(record.usedLessons) }}</span>
            <span v-else-if="column.dataIndex === 'remainLessons'" class="remain-text">{{ lessons(record.remainLessons) }}</span>
            <a-dropdown v-else-if="column.dataIndex === 'action'" :trigger="['click']">
              <a class="op-link" @click.stop>操作 <DownOutlined /></a>
              <template #overlay>
                <a-menu @click="onStudentMenu($event.key, record)">
                  <a-menu-item key="profile">学员档案</a-menu-item>
                  <a-menu-item key="remove" danger>移出班级</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
          <template #emptyText>
            <a-empty description="暂无学员" />
          </template>
        </a-table>
        <div v-if="filteredStudents.length" class="table-foot">没有更多信息</div>
      </template>

      <template v-else-if="tab === 'schedule'">
        <div class="result-line">共计排课 {{ schedules.length }} 节</div>
        <a-table
          :columns="scheduleColumns"
          :data-source="schedules"
          :loading="scheduleLoading"
          :pagination="{ pageSize: 10, size: 'small', showSizeChanger: false }"
          row-key="id"
          size="small"
          :scroll="{ x: 1120 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'lessonDate'">{{ dateOf(record.lessonDate) }}</span>
            <span v-else-if="column.dataIndex === 'time'">{{ timeOf(record) }}</span>
            <span v-else-if="column.dataIndex === 'courseName'">{{ show(record.courseName) }}</span>
            <span v-else-if="column.dataIndex === 'room'">{{ show(record.room) }}</span>
            <span v-else-if="column.dataIndex === 'topic'" :title="record.topic ?? ''">{{ show(record.topic) }}</span>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ show(record.teacherName) }}</span>
            <span v-else-if="column.dataIndex === 'counts'">{{ show(record.shouldCount) }} / {{ show(record.actualCount) }}</span>
            <span v-else-if="column.dataIndex === 'status'">
              <a-tag
                :color="
                  record.status === '已上课'
                    ? 'green'
                    : record.status === '休课'
                      ? 'orange'
                      : record.status === '已取消'
                        ? 'default'
                        : 'blue'
                "
              >
                {{ record.status }}
              </a-tag>
            </span>
            <a v-else-if="column.dataIndex === 'action'" class="op-link" @click="openAttendance(record)">点名表</a>
          </template>
          <template #emptyText>
            <a-empty description="暂无排课数据" />
          </template>
        </a-table>
      </template>

      <template v-else-if="tab === 'records'">
        <div class="record-toolbar">
          <a-button size="small" class="outline-btn" @click="notSupported('排课记上课')">排课记上课</a-button>
          <a-button size="small" class="outline-btn" @click="notSupported('未排课记上课')">未排课记上课</a-button>
          <a-button size="small" class="outline-btn" @click="router.push('/attendance')">考勤记录</a-button>
          <a-button size="small" class="outline-btn" @click="exportRecords">导出记录</a-button>
          <span class="toolbar-spacer" />
          <span class="period-group">
            <span
              class="period-btn"
              :class="{ active: recordPeriod === 'week' }"
              @click="applyRecordPeriod('week')"
            >本周</span>
            <span
              class="period-btn"
              :class="{ active: recordPeriod === 'month' }"
              @click="applyRecordPeriod('month')"
            >本月</span>
          </span>
          <a-select v-model:value="recordTimeSlot" size="small" class="time-select">
            <a-select-option value="all">上课时间</a-select-option>
            <a-select-option value="am">上午</a-select-option>
            <a-select-option value="pm">下午</a-select-option>
            <a-select-option value="night">晚上</a-select-option>
          </a-select>
          <a-range-picker
            v-model:value="recordRange"
            size="small"
            class="range-picker"
            value-format="YYYY-MM-DD"
            :allow-clear="false"
            @change="onRecordRangeChange"
          />
        </div>
        <div class="record-toolbar tuition-row">
          <span class="toolbar-spacer" />
          <a-checkbox v-model:checked="tuitionCalc" class="tuition-check">学费计算</a-checkbox>
        </div>
        <div class="result-line">
          当前结果：学员课时总计 {{ recordLessonTotal }} 课时 | 出勤率总计 {{ recordRateTotal }}
        </div>
        <a-table
          :columns="recordColumns"
          :data-source="filteredRecords"
          :loading="recordLoading"
          :pagination="false"
          row-key="id"
          size="small"
          :scroll="{ x: 1240 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'lessonDate'">{{ dateOf(record.lessonDate) }}</span>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ show(record.teacherName) }}</span>
            <span v-else-if="column.dataIndex === 'assistantName'">{{ show(record.assistantName) }}</span>
            <span v-else-if="column.dataIndex === 'courseName'">{{ show(record.courseName) }}</span>
            <span v-else-if="column.dataIndex === 'counts'">{{ show(record.actualCount) }}/{{ show(record.shouldCount) }}</span>
            <span v-else-if="column.dataIndex === 'attendanceRate'">
              {{ record.attendanceRate == null ? '—' : `${Number(record.attendanceRate).toFixed(2)}%` }}
            </span>
            <span v-else-if="column.dataIndex === 'presentCount'">{{ record.presentCount }}</span>
            <span v-else-if="column.dataIndex === 'leaveCount'">{{ record.leaveCount }}</span>
            <span v-else-if="column.dataIndex === 'absentCount'">{{ record.absentCount }}</span>
            <span v-else-if="column.dataIndex === 'lateCount'">{{ record.lateCount }}</span>
            <span v-else-if="column.dataIndex === 'lessonCount'">{{ Number(record.lessonCount ?? 0) }}</span>
            <span v-else-if="column.dataIndex === 'topic'" :title="record.topic ?? ''">{{ show(record.topic) }}</span>
            <span v-else-if="column.dataIndex === 'createdAt'">{{ dateOf(record.createdAt) }}</span>
            <span v-else-if="column.dataIndex === 'action'" class="record-actions">
              <a class="op-link" @click="notSupported('编辑上课记录')">编辑</a>
              <span class="op-sep">|</span>
              <a class="op-link" @click="notSupported('删除上课记录')">删除</a>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="暂无上课记录" />
          </template>
        </a-table>
        <div class="table-foot">没有更多信息</div>
      </template>

      <template v-else-if="tab === 'score'">
        <div class="result-line">共计 {{ gradeExams.length }} 场考试</div>
        <a-table
          :columns="gradeColumns"
          :data-source="gradeExams"
          :loading="gradeLoading"
          :pagination="false"
          row-key="examId"
          size="small"
          :scroll="{ x: 960 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'examDate'">{{ record.examDate ?? '—' }}</span>
            <span v-else-if="column.dataIndex === 'examType'">
              <a-tag color="blue">{{ record.examType }}</a-tag>
            </span>
            <span v-else-if="column.dataIndex === 'progress'">{{ record.enteredCount }} / {{ record.studentCount }}</span>
            <span v-else-if="column.dataIndex === 'avgScore'">{{ record.avgScore ?? '—' }}</span>
            <span v-else-if="column.dataIndex === 'maxScore'">{{ record.maxScore ?? '—' }}</span>
            <span v-else-if="column.dataIndex === 'minScore'">{{ record.minScore ?? '—' }}</span>
            <span v-else-if="column.dataIndex === 'status'">{{ record.status }}</span>
            <a v-else-if="column.dataIndex === 'action'" class="op-link" @click="goGradeEntry(record)">查看成绩</a>
          </template>
          <template #emptyText>
            <a-empty description="暂无该班级的考试安排，可在「成绩中心 - 考试管理」创建" />
          </template>
        </a-table>
      </template>

      <template v-else-if="tab === 'comment'">
        <div class="result-line">共计课堂点评 {{ comments.length }} 条</div>
        <a-table
          :columns="commentColumns"
          :data-source="comments"
          :loading="commentLoading"
          :pagination="{ pageSize: 10, size: 'small', showSizeChanger: false }"
          row-key="id"
          size="small"
          :scroll="{ x: 980 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'lessonDate'">{{ dateOf(record.lessonDate) }}</span>
            <router-link
              v-else-if="column.dataIndex === 'studentName'"
              class="student-name"
              :to="`/studentDetail/${record.studentId}`"
            >{{ record.studentName }}</router-link>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ show(record.teacherName) }}</span>
            <span v-else-if="column.dataIndex === 'rating'">{{ show(record.rating) }}</span>
            <span v-else-if="column.dataIndex === 'content'" :title="record.content ?? ''">{{ show(record.content) }}</span>
            <span v-else-if="column.dataIndex === 'readAt'">
              <a-tag :color="record.readAt ? 'default' : 'orange'">{{ record.readAt ? '已读' : '未读' }}</a-tag>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="暂无课堂点评" />
          </template>
        </a-table>
      </template>

      <template v-else-if="tab === 'attendance'">
        <div class="result-line">共计 {{ attendanceLessons.length }} 节课有考勤记录</div>
        <a-table
          :columns="attendanceLessonColumns"
          :data-source="attendanceLessons"
          :loading="scheduleLoading"
          :pagination="{ pageSize: 10, size: 'small', showSizeChanger: false }"
          row-key="id"
          size="small"
          :scroll="{ x: 1000 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'lessonDate'">{{ dateOf(record.lessonDate) }}</span>
            <span v-else-if="column.dataIndex === 'time'">{{ timeOf(record) }}</span>
            <span v-else-if="column.dataIndex === 'topic'" :title="record.topic ?? ''">{{ show(record.topic) }}</span>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ show(record.teacherName) }}</span>
            <span v-else-if="column.dataIndex === 'counts'">{{ show(record.shouldCount) }} / {{ show(record.actualCount) }}</span>
            <span v-else-if="column.dataIndex === 'attendanceCount'">{{ record.attendanceCount }}</span>
            <a v-else-if="column.dataIndex === 'action'" class="op-link" @click="openAttendance(record)">查看</a>
          </template>
          <template #emptyText>
            <a-empty description="暂无点名记录" />
          </template>
        </a-table>
      </template>

      <template v-else-if="tab === 'homework'">
        <div class="result-line">共计 {{ homeworks.length }} 份作业 · 作业由「家校服务 - 作业」统一布置与编辑</div>
        <a-table
          :columns="homeworkColumns"
          :data-source="homeworks"
          :loading="homeworkLoading"
          :pagination="{ pageSize: 10, size: 'small', showSizeChanger: false }"
          row-key="id"
          size="small"
          :scroll="{ x: 900 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'publishDate'">{{ String(record.publishDate ?? '').slice(0, 10) || '—' }}</span>
            <span v-else-if="column.dataIndex === 'title'" :title="record.title">{{ record.title }}</span>
            <span v-else-if="column.dataIndex === 'content'" :title="record.content">{{ show(record.content) }}</span>
            <span v-else-if="column.dataIndex === 'dueDate'">{{ record.dueDate ? String(record.dueDate).slice(0, 10) : '—' }}</span>
            <span v-else-if="column.dataIndex === 'publisher'">{{ show(record.publisher) }}</span>
            <span v-else-if="column.dataIndex === 'status'">
              <a-tag color="green" style="margin: 0">{{ record.status }}</a-tag>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="暂无作业，可在「家校服务 - 作业」布置" />
          </template>
        </a-table>
      </template>

      <template v-else-if="tab === 'album'">
        <div class="toolbar">
          <span class="album-hint">班级相册由「家校服务 - 班级相册」统一上传与删除，此处仅供查看</span>
          <span class="toolbar-spacer" />
          <span class="album-hint">共 {{ photos.length }} 张</span>
        </div>
        <a-spin :spinning="photoLoading">
          <div v-if="photos.length" class="album-grid">
            <div v-for="photo in photos" :key="photo.id" class="album-item">
              <a-image :src="photo.url" class="album-image" />
              <div class="album-meta">
                <span class="album-uploader" :title="photo.uploader || ''">{{ photo.uploader || '未知' }}</span>
                <span class="album-time">{{ photoTime(photo.createdAt) }}</span>
              </div>
            </div>
          </div>
          <a-empty v-else description="暂无照片，可在「家校服务 - 班级相册」上传" />
        </a-spin>
      </template>

      <template v-else-if="tab === 'info'">
        <a-descriptions bordered size="small" :column="2" class="info-table">
          <a-descriptions-item v-for="row in infoRows" :key="row.key" :label="row.label">
            {{ row.value }}
          </a-descriptions-item>
        </a-descriptions>
      </template>

      <div v-else class="tab-empty">
        <a-empty :description="`「${tabs.find((item) => item.key === tab)?.label}」暂无数据`" />
      </div>
    </a-spin>

    <a-drawer v-model:open="attendanceOpen" :title="attendanceTitle" placement="right" width="620">
      <a-table
        :columns="attendanceDetailColumns"
        :data-source="attendanceRows"
        :loading="attendanceLoading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ y: 520 }"
      >
        <template #bodyCell="{ column, record }">
          <router-link
            v-if="column.dataIndex === 'studentName'"
            class="student-name"
            :to="`/studentDetail/${record.studentId}`"
          >{{ record.studentName }}</router-link>
          <span v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === '出勤' ? 'green' : record.status === '未记录' ? 'default' : 'orange'">
              {{ record.status }}
            </a-tag>
          </span>
          <span v-else-if="column.dataIndex === 'lessons'">{{ lessons(record.lessons) }}</span>
          <span v-else-if="column.dataIndex === 'remark'">{{ show(record.remark) }}</span>
        </template>
        <template #emptyText>
          <a-empty description="暂无点名详情" />
        </template>
      </a-table>
    </a-drawer>

    <a-modal
      v-model:open="addOpen"
      title="添加学员"
      width="640px"
      :confirm-loading="addSaving"
      ok-text="确定"
      cancel-text="取消"
      @ok="submitAddStudents"
    >
      <div class="add-search">
        <a-input
          v-model:value="addKeyword"
          placeholder="搜索学员姓名或电话"
          allow-clear
          @press-enter="searchCandidates"
        >
          <template #suffix><SearchOutlined class="search-icon" /></template>
        </a-input>
        <a-button type="primary" class="primary-btn" @click="searchCandidates">搜索</a-button>
      </div>
      <span class="muted">仅展示在读学员，已在本班的学员会自动过滤</span>
      <a-table
        :data-source="addCandidates"
        :loading="addLoading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ y: 360 }"
        :row-selection="{
          selectedRowKeys: addSelected,
          onChange: onAddSelectChange,
        }"
      >
        <a-table-column title="学员" data-index="name" width="110" />
        <a-table-column title="性别" data-index="gender" width="70" />
        <a-table-column title="电话" data-index="phone" width="140" />
        <a-table-column title="年级" data-index="grade" width="90" />
        <a-table-column title="校区" data-index="campusText" ellipsis />
      </a-table>
    </a-modal>

    <a-modal
      v-model:open="editOpen"
      title="编辑班级"
      width="720"
      :confirm-loading="editSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitEdit"
    >
      <a-form layout="vertical" class="edit-form">
        <div class="edit-grid">
          <a-form-item label="班级名称" required>
            <a-input v-model:value="editForm.name" placeholder="请输入班级名称" :maxlength="64" />
          </a-form-item>
          <a-form-item label="开班校区" required>
            <a-select v-model:value="editForm.campusId" placeholder="请选择校区" allow-clear>
              <a-select-option v-for="item in classOptions.campuses" :key="item.id" :value="item.id">
                {{ item.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="所属课程">
            <a-select v-model:value="editForm.courseId" placeholder="请选择课程" allow-clear>
              <a-select-option v-for="item in classOptions.courses" :key="item.id" :value="item.id">
                {{ item.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="班主任">
            <a-select v-model:value="editForm.teacherId" placeholder="请选择班主任" allow-clear>
              <a-select-option v-for="item in classOptions.teachers" :key="item.id" :value="item.id">
                {{ item.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="助教">
            <a-input v-model:value="editForm.assistName" placeholder="请输入助教姓名" />
          </a-form-item>
          <a-form-item label="班型">
            <a-select v-model:value="editForm.lessonClassName" placeholder="请选择班型" allow-clear>
              <a-select-option v-for="item in classOptions.lessonClasses" :key="item" :value="item">{{ item }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="教室">
            <a-input v-model:value="editForm.room" placeholder="请输入教室" />
          </a-form-item>
          <a-form-item label="上课时间">
            <a-input v-model:value="editForm.scheduleText" placeholder="如：每周六 09:00-10:30" />
          </a-form-item>
          <a-form-item label="班级容量">
            <a-input-number v-model:value="editForm.capacity" :min="0" :precision="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="开班日期">
            <a-input v-model:value="editForm.startDate" placeholder="YYYY-MM-DD" />
          </a-form-item>
          <a-form-item label="结班日期">
            <a-input v-model:value="editForm.endDate" placeholder="YYYY-MM-DD" />
          </a-form-item>
          <a-form-item label="班级状态">
            <a-select v-model:value="editForm.status">
              <a-select-option value="已开班">已开班</a-select-option>
              <a-select-option value="已结班">已结班</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="招生状态">
            <a-select v-model:value="editForm.enrolling">
              <a-select-option :value="1">开放招生</a-select-option>
              <a-select-option :value="0">停止招生</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="收费标准">
            <a-input v-model:value="editForm.feeStandard" placeholder="如：40元/课时，12课时=480元" />
          </a-form-item>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.class-detail {
  padding: 0 4px 40px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0 10px;
}

.back-btn {
  color: #1677ff;
  font-size: 16px;
  padding: 0 2px;
}

.head-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1, #262626);
}

.status-pill {
  padding: 1px 10px;
  border-radius: 10px;
  font-size: 12px;
  color: #389e0d;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.status-pill.closed {
  color: #8c8c8c;
  background: #fafafa;
  border-color: #d9d9d9;
}

.head-spacer {
  flex: 1;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 22px;
  padding: 10px 4px 12px;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  min-width: 0;
}

.info-item.span-2 {
  grid-column: span 2;
}

.info-label {
  color: var(--text-2, #8c8c8c);
  white-space: nowrap;
}

.info-value {
  color: var(--text-1, #262626);
  min-width: 0;
}

.info-value.link {
  color: #1677ff;
}

.info-value.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-value.people {
  display: flex;
  align-items: center;
  gap: 8px;
}

.people-bar {
  width: 90px;
  margin: 0;
}

.people-state {
  color: var(--text-2, #8c8c8c);
  font-size: 12px;
}

.stat-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 18px;
  padding: 10px 4px;
  font-size: 12px;
  color: var(--text-2, #8c8c8c);
  border-bottom: 1px solid var(--border-color, #f0f0f0);
}

.stat-label {
  color: var(--text-1, #262626);
  font-weight: 500;
}

.stat-item b {
  color: var(--text-1, #262626);
  font-weight: 500;
}

.detail-tabs {
  display: flex;
  align-items: stretch;
  padding: 6px 0 0;
  border-bottom: 1px solid var(--border-color, #f0f0f0);
  overflow-x: auto;
}

.detail-tab {
  flex: 1 0 auto;
  min-width: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 8px 13px;
  cursor: pointer;
}

.detail-tab-text {
  position: relative;
  padding-bottom: 10px;
  font-size: 14px;
  line-height: 22px;
  color: var(--text-2, #595959);
  white-space: nowrap;
}

.detail-tab-text.active {
  color: #1677ff;
  font-weight: 500;
}

.detail-tab-text.active::after {
  content: '';
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  min-width: 28px;
  height: 2px;
  background: #1677ff;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 4px 8px;
}

.toolbar-spacer {
  flex: 1;
}

.record-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 4px 6px;
  flex-wrap: wrap;
}

.record-toolbar.tuition-row {
  padding: 0 4px 6px;
  min-height: 22px;
}

.outline-btn {
  color: #1677ff;
  border-color: #91caff;
  background: #fff;
}

.period-group {
  display: inline-flex;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  overflow: hidden;
}

.period-btn {
  padding: 2px 16px;
  font-size: 12px;
  line-height: 20px;
  color: #595959;
  background: #fff;
  border-right: 1px solid #d9d9d9;
  cursor: pointer;
}

.period-btn:last-child {
  border-right: none;
}

.period-btn.active {
  color: #fff;
  background: #1677ff;
  border-color: #1677ff;
}

.time-select {
  width: 108px;
}

.range-picker {
  width: 230px;
}

.tuition-check {
  font-size: 12px;
  color: var(--text-2, #8c8c8c);
}

.op-sep {
  margin: 0 4px;
  color: #d9d9d9;
  font-size: 12px;
}

.search-input {
  width: 260px;
}

.search-icon {
  color: #bfbfbf;
}

.batch-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
}

.ghost-btn {
  color: #1677ff;
  border-color: #d6e4ff;
  background: #f5f9ff;
}

.closed-check {
  font-size: 12px;
  color: var(--text-2, #595959);
}

.custom-btn {
  background: #1677ff;
  border-color: #1677ff;
}

.column-picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.result-line {
  padding: 6px 4px;
  font-size: 12px;
  color: var(--text-2, #8c8c8c);
}

.student-name {
  color: #1677ff;
  text-decoration: none;
  cursor: pointer;
}

.student-name:hover {
  text-decoration: underline;
}

.used-text {
  color: #f5222d;
}

.remain-text {
  color: #389e0d;
}

.op-link {
  color: #1677ff;
}

.table-foot {
  padding: 18px;
  text-align: center;
  font-size: 12px;
  color: #bfbfbf;
}

.tab-empty {
  padding: 48px 0;
}

.info-table {
  margin-top: 12px;
}

.add-search {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.muted {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--text-2, #8c8c8c);
}

.edit-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.album-hint {
  font-size: 12px;
  color: var(--text-2, #8c8c8c);
}

.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  margin-top: 4px;
}

.album-item {
  border: 1px solid #eef0f4;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.album-image {
  display: block;
  width: 100%;
  height: 150px;
  overflow: hidden;
}

.album-image :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.album-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: #8c8c8c;
}

.album-uploader {
  max-width: 80px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #595959;
}

.album-time {
  flex: 1;
}

.album-delete {
  color: #ff4d4f;
}

.album-delete:hover {
  color: #ff7875;
}

@media (max-width: 1200px) {
  .info-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
