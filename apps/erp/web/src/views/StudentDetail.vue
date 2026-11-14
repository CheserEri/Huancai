<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { UserOutlined } from '@ant-design/icons-vue'
import LocalOrderModal from '../components/LocalOrderModal.vue'
import { authState } from '../stores/auth'
import {
  assignClassStudents,
  consumeLessons,
  createClass,
  createMaterialPurchase,
  deleteStudent,
  fetchClassAssignOptions,
  fetchClassOptions,
  fetchCourseUpgrades,
  fetchDataFeeOptions,
  fetchGradeExam,
  fetchGradeStudentScores,
  fetchLessonPrice,
  fetchLocalOrderOptions,
  fetchMaterialOptions,
  fetchMaterialStock,
  fetchSpOrderPage,
  fetchStudentDetail,
  fetchStudentLessonTxns,
  fetchUpgradeQuote,
  printEnrollmentTicket,
  payDataFee,
  refundStudentBatch,
  upgradeLocalOrder,
  updateEnrollmentExpire,
  updateEnrollmentStatus,
  updateStudent,
  type ClassAssignOptions,
  type ClassFormPayload,
  type ClassOptions,
  type CourseUpgradeRow,
  type DataFeeOptions,
  type GradePart,
  type GradeStudentItem,
  type LessonPriceInfo,
  type MaterialOptions,
  type MaterialStockRow,
  type SpOrderRow,
  type StudentDetail,
  type StudentFormPayload,
  type StudentLessonTxn,
  type UpgradeQuote,
} from '../api/client'

const route = useRoute()
const router = useRouter()

const studentId = Number(route.params.id)
const detail = ref<StudentDetail | null>(null)
const loading = ref(false)

const tabs = [
  { key: 'chat', label: '沟通互动' },
  { key: 'order', label: '订单' },
  { key: 'course', label: '课程' },
  { key: 'schedule', label: '课程表' },
  { key: 'attendance', label: '上课记录' },
  { key: 'growth', label: '成长记录' },
  { key: 'score', label: '成绩' },
  { key: 'account', label: '学员账户' },
  { key: 'log', label: '操作日志' },
]
const initialTab = typeof route.query.tab === 'string' ? route.query.tab : ''
const tab = ref(tabs.some((item) => item.key === initialTab) ? initialTab : 'course')

/* ---------------- 成绩页签 ---------------- */
const gradeItems = ref<GradeStudentItem[]>([])
const gradeLoading = ref(false)
const gradeLoaded = ref(false)
const radarParts = ref<{ name: string; fullScore: number; value: number }[]>([])
const radarExamName = ref('')

const gradeColumns = [
  { title: '考试', dataIndex: 'examName' },
  { title: '类型', dataIndex: 'examType', width: 96 },
  { title: '考试日期', dataIndex: 'examDate', width: 110 },
  { title: '班级', dataIndex: 'className', width: 150 },
  { title: '成绩', dataIndex: 'score', width: 110, align: 'right' as const },
  { title: '班级排名', dataIndex: 'rankClass', width: 90, align: 'center' as const },
  { title: '校区排名', dataIndex: 'rankCampus', width: 90, align: 'center' as const },
  { title: '评语', dataIndex: 'remark' },
]

async function loadGrades() {
  if (gradeLoaded.value || gradeLoading.value) return
  gradeLoading.value = true
  try {
    gradeItems.value = await fetchGradeStudentScores(studentId)
    gradeLoaded.value = true
    await loadRadar()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载学员成绩失败')
  } finally {
    gradeLoading.value = false
  }
}

async function loadRadar() {
  const item = gradeItems.value.find(
    (row) => row.status === '正常' && Object.keys(row.partScores ?? {}).length >= 3,
  )
  if (!item) {
    radarParts.value = []
    return
  }
  try {
    const exam = await fetchGradeExam(item.examId)
    radarParts.value = (exam.parts as GradePart[])
      .filter((part) => part.id !== undefined && part.name)
      .map((part) => ({
        name: part.name,
        fullScore: Number(part.fullScore || 100),
        value: Number(item.partScores[String(part.id)] ?? 0),
      }))
      .filter((part) => part.value > 0)
    radarExamName.value = item.examName
  } catch {
    radarParts.value = []
  }
}

watch(tab, (value) => {
  if (value === 'score') loadGrades()
  if (value === 'order') loadStudentOrders()
})

const gradeTrend = computed(() => {
  const items = gradeItems.value
    .filter((item) => item.status === '正常' && item.totalScore !== null)
    .slice()
    .reverse()
  if (items.length < 2) return null
  const width = 560
  const height = 190
  const pad = { left: 36, right: 16, top: 20, bottom: 28 }
  const plotW = width - pad.left - pad.right
  const plotH = height - pad.top - pad.bottom
  const points = items.map((item, index) => {
    const percent = (Number(item.totalScore) / Number(item.fullScore || 100)) * 100
    const x = pad.left + (items.length === 1 ? plotW / 2 : (plotW * index) / (items.length - 1))
    const y = pad.top + plotH - (Math.min(percent, 100) / 100) * plotH
    return { x, y, percent: Math.round(percent * 10) / 10, label: item.examName }
  })
  return { width, height, pad, plotH, points }
})

const radarLayout = computed(() => {
  const parts = radarParts.value
  if (parts.length < 3) return null
  const size = 240
  const cx = 120
  const cy = 118
  const radius = 76
  const step = (Math.PI * 2) / parts.length
  const point = (index: number, ratio: number) => {
    const angle = -Math.PI / 2 + step * index
    return { x: cx + Math.cos(angle) * radius * ratio, y: cy + Math.sin(angle) * radius * ratio }
  }
  const axes = parts.map((part, index) => ({
    ...point(index, 1),
    label: part.name,
    labelPoint: point(index, 1.26),
  }))
  const polygon = parts
    .map((part, index) => point(index, Math.min(part.value / (part.fullScore || 100), 1)))
    .map((item) => `${item.x},${item.y}`)
    .join(' ')
  const rings = [0.25, 0.5, 0.75, 1].map((ratio) =>
    parts
      .map((_, index) => point(index, ratio))
      .map((item) => `${item.x},${item.y}`)
      .join(' '),
  )
  return { size, cx, cy, axes, polygon, rings }
})

const student = computed(() => detail.value?.student ?? null)

/** 积分兑换：弹窗中直接带入当前学员，兑换成功后刷新积分。 */
const pointsOpen = ref(false)
/** 办理中心快捷入口跳转过来时，直接打开该学员的充值/补费弹窗。 */
const localOrderOpen = ref(false)
const localOrderMode = ref<'recharge' | 'subsidy'>('recharge')
const pointsStudent = computed(() => {
  const item = student.value
  if (!item) return undefined
  return {
    id: item.id,
    name: item.name,
    phone: item.phone,
    points: item.points,
    balance: item.balance,
    debt: item.debt,
    campusId: item.campusId,
  }
})

function onPointsSaved() {
  load()
}
const enrollments = computed(() => detail.value?.enrollments ?? [])
const activeEnrollments = computed(() => enrollments.value.filter((item) => item.status === '生效'))
const primaryGuardian = computed(() => detail.value?.guardians?.find((item) => item.isPrimary) ?? null)

const basicFields = computed(() => [
  { label: '磁卡卡号', value: show(student.value?.cardNo) },
  { label: '微信号', value: show(student.value?.wechat) },
  { label: '生日', value: show(student.value?.birthday) },
  { label: '公立学校', value: show(student.value?.school) },
  { label: '年级', value: show(student.value?.grade) },
  { label: '公立校班级', value: show(student.value?.publicSchoolClass) },
  { label: '居住区域', value: show(student.value?.address) },
  { label: '其他信息', value: show(student.value?.extraInfo) },
])

function formatMoney(value: number | null | undefined) {
  return `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

function moneyText(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function signedMoney(value: number | null | undefined) {
  const n = Number(value ?? 0)
  const text = Math.abs(n).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
  return n < 0 ? `-¥${text}` : `¥${text}`
}

function formatDateTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function dayText(value: string | null | undefined) {
  return value ? value.slice(0, 10) : '—'
}

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : value
}

function statusClass(status: string | null | undefined) {
  if (status === '生效' || status === '在读') return 'is-ok'
  if (status === '停用' || status === '停课') return 'is-warn'
  return 'is-muted'
}

// ---------------- 分班 ----------------
const assignOpen = ref(false)
const assignSaving = ref(false)
const assignLoading = ref(false)
const assignItem = ref<StudentDetail['enrollments'][number] | null>(null)
const assignOptions = ref<ClassAssignOptions | null>(null)
const assignTargetClassId = ref<number | undefined>()
const assignSelected = ref<number[]>([])
const assignKeyword = ref('')

const assignClassOptions = computed(() =>
  (assignOptions.value?.classes ?? []).map((row) => {
    const meta = [
      row.campusName,
      `已分 ${row.enrolledCount ?? 0}${row.capacity ? `/${row.capacity}` : ''} 人`,
    ].filter(Boolean).join(' · ')
    return { label: `${row.name}（${meta}）`, value: row.id }
  }),
)

const assignStudents = computed(() => {
  const list = assignOptions.value?.students ?? []
  const keyword = assignKeyword.value.trim()
  if (!keyword) return list
  return list.filter(
    (row) => row.studentName.includes(keyword) || (row.phone ?? '').includes(keyword),
  )
})

const assignColumns = [
  { title: '学员', dataIndex: 'studentName', width: 140 },
  { title: '联系电话', dataIndex: 'phone', width: 150 },
  { title: '当前班级', dataIndex: 'className', width: 200 },
  { title: '剩余课时', dataIndex: 'remainLessons', width: 100, align: 'right' },
]

function onAssignSelect(keys: Array<string | number>) {
  assignSelected.value = keys.map((key) => Number(key))
}

async function openAssign(item: StudentDetail['enrollments'][number]) {
  if (!item.courseId) {
    message.warning('该报读缺少课程信息，无法办理分班')
    return
  }
  if (item.status !== '生效') {
    message.warning('仅生效中的报读可以办理分班')
    return
  }
  assignItem.value = item
  assignTargetClassId.value = undefined
  assignSelected.value = [studentId]
  assignKeyword.value = ''
  assignOpen.value = true
  await loadAssignOptions()
}

async function loadAssignOptions() {
  const courseId = assignItem.value?.courseId
  if (!courseId) return
  assignLoading.value = true
  try {
    assignOptions.value = await fetchClassAssignOptions(courseId)
  } catch (e) {
    message.error((e as Error).message || '加载分班信息失败')
  } finally {
    assignLoading.value = false
  }
}

async function submitAssign() {
  const courseId = assignItem.value?.courseId
  if (!courseId) return
  if (!assignTargetClassId.value) {
    message.warning('请选择目标班级')
    return
  }
  if (!assignSelected.value.length) {
    message.warning('请勾选需要分班的学员')
    return
  }
  assignSaving.value = true
  try {
    const res = await assignClassStudents({
      courseId,
      targetClassId: assignTargetClassId.value,
      studentIds: assignSelected.value,
    })
    message.success(`已将 ${res.moved} 名学员分到「${res.className}」，未勾选学员留在原班级`)
    assignOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '分班失败')
  } finally {
    assignSaving.value = false
  }
}

// ---------------- 分班弹窗内新建班级 ----------------
const newClassOpen = ref(false)
const newClassSaving = ref(false)
const newClassLoading = ref(false)
const classOptions = ref<ClassOptions | null>(null)
const newClassForm = reactive<{
  name: string
  campusId?: number
  courseId?: number
  teacherId?: number
  capacity: number
  startDate?: string
  scheduleText: string
  room?: string
}>({
  name: '',
  campusId: undefined,
  courseId: undefined,
  teacherId: undefined,
  capacity: 0,
  startDate: undefined,
  scheduleText: '',
  room: undefined,
})

const newClassCampusOptions = computed(() =>
  (classOptions.value?.campuses ?? []).map((item) => ({ label: item.name, value: item.id }),
  ),
)
const newClassTeacherOptions = computed(() =>
  (classOptions.value?.teachers ?? []).map((item) => ({ label: item.name, value: item.id }),
  ),
)
const newClassRoomOptions = computed(() =>
  (classOptions.value?.rooms ?? []).map((item) => ({ label: item, value: item }),
  ),
)

async function openNewClass() {
  newClassForm.name = ''
  newClassForm.campusId = student.value?.campusId ?? undefined
  newClassForm.courseId = assignItem.value?.courseId ?? undefined
  newClassForm.teacherId = undefined
  newClassForm.capacity = 0
  newClassForm.startDate = new Date().toISOString().slice(0, 10)
  newClassForm.scheduleText = ''
  newClassForm.room = undefined
  newClassOpen.value = true
  newClassLoading.value = true
  try {
    classOptions.value = await fetchClassOptions()
    if (!newClassForm.campusId) newClassForm.campusId = classOptions.value.campuses[0]?.id
  } catch (e) {
    message.error((e as Error).message || '加载班级选项失败')
  } finally {
    newClassLoading.value = false
  }
}

async function submitNewClass() {
  if (!newClassForm.name.trim()) {
    message.warning('请填写班级名称')
    return
  }
  if (!newClassForm.campusId) {
    message.warning('请选择开班校区')
    return
  }
  if (!newClassForm.courseId) {
    message.warning('该报读缺少课程信息，无法新建班级')
    return
  }
  const payload: ClassFormPayload = {
    name: newClassForm.name.trim(),
    campusId: newClassForm.campusId,
    courseId: newClassForm.courseId,
    teacherId: newClassForm.teacherId,
    capacity: Number(newClassForm.capacity ?? 0),
    startDate: newClassForm.startDate,
    scheduleText: newClassForm.scheduleText.trim() || undefined,
    room: newClassForm.room || undefined,
    status: '已开班',
    enrolling: 1,
  }
  newClassSaving.value = true
  try {
    const res = await createClass(payload)
    message.success(`班级「${res.name}」已创建，可直接用于本次分班`)
    newClassOpen.value = false
    await loadAssignOptions()
    assignTargetClassId.value = res.id
  } catch (e) {
    message.error((e as Error).message || '新建班级失败')
  } finally {
    newClassSaving.value = false
  }
}

// ---------------- 删除学员 ----------------
function confirmDelete() {
  if (!student.value) return
  Modal.confirm({
    title: '删除学员',
    content: `确定删除学员「${student.value.name}」吗？有报读记录或订单记录的学员不能删除。`,
    okText: '确认删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteStudent(studentId)
        message.success('学员已删除')
        router.push('/student')
      } catch (e) {
        message.error((e as Error).message || '删除失败')
        throw e
      }
    },
  })
}

// ---------------- 打印听课证 ----------------
async function printTicket(item: StudentDetail['enrollments'][number]) {
  const s = student.value
  if (!s) return
  try {
    await printEnrollmentTicket(Number(s.id), Number(item.id))
    message.success('听课证已发送到小票打印机')
  } catch (e) {
    message.error((e as Error).message || '听课证打印失败')
  }
}

// ---------------- 停课 / 复课 / 结课 ----------------
async function changeEnrollmentStatus(
  item: StudentDetail['enrollments'][number],
  status: string,
  successText?: string,
) {
  try {
    await updateEnrollmentStatus(studentId, item.id, status)
    message.success(successText || (status === '生效' ? '已恢复为生效' : `已${status}`))
    await load()
  } catch (e) {
    message.error((e as Error).message || '状态更新失败')
    throw e
  }
}

function toggleStop(item: StudentDetail['enrollments'][number]) {
  const target = item.status === '停课' ? '生效' : '停课'
  Modal.confirm({
    title: target === '停课' ? '停课确认' : '复课确认',
    content:
      target === '停课'
        ? `确定将「${item.courseName}」停课吗？停课后该报读不计入在读课程。`
        : `确定将「${item.courseName}」恢复为生效吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: () => changeEnrollmentStatus(item, target),
  })
}

function confirmFinish(item: StudentDetail['enrollments'][number]) {
  Modal.confirm({
    title: '结课确认',
    content: `结课后，剩余课时将失效，剩余学费转入「账户剩余未用学费」，结课为终态不可恢复。确认将「${item.courseName}」结课？`,
    okText: '确认结课',
    cancelText: '取消',
    onOk: () => changeEnrollmentStatus(item, '结课'),
  })
}

// ---------------- 设置有效期 ----------------
const expireOpen = ref(false)
const expireSaving = ref(false)
const expireForm = reactive<{ enrollmentId?: number; courseName: string; expireDate?: string }>({
  enrollmentId: undefined,
  courseName: '',
  expireDate: undefined,
})

function openExpire(item: StudentDetail['enrollments'][number]) {
  expireForm.enrollmentId = item.id
  expireForm.courseName = item.courseName
  expireForm.expireDate = item.expireDate || undefined
  expireOpen.value = true
}

async function submitExpire() {
  if (!expireForm.enrollmentId) return
  expireSaving.value = true
  try {
    await updateEnrollmentExpire(studentId, expireForm.enrollmentId, expireForm.expireDate || null)
    message.success(expireForm.expireDate ? '有效期已更新' : '有效期已清空')
    expireOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '设置有效期失败')
  } finally {
    expireSaving.value = false
  }
}

// ---------------- 退费（按课程 / 账户余额，可多选全退） ----------------
interface RefundRow {
  key: string
  kind: 'course' | 'balance'
  name: string
  remainLessons: number
  amount: number
  disabled: boolean
}

const refundOpen = ref(false)
const refundSaving = ref(false)
const refundSelected = ref<string[]>([])
const refundForm = reactive({ payMethod: '现金', reason: '' })

const refundRows = computed<RefundRow[]>(() => {
  const rows: RefundRow[] = enrollments.value
    .filter((item) => item.status === '生效' || item.status === '停课')
    .map((item) => {
      const amount = Math.max(0, Number(item.accountBalance ?? 0))
      return {
        key: `course-${item.id}`,
        kind: 'course' as const,
        name: item.courseName,
        remainLessons: Math.max(0, Number(item.remainLessons ?? 0)),
        amount: round2(amount),
        disabled: amount <= 0,
      }
    })
  const balance = Math.max(0, Number(student.value?.balance ?? 0))
  rows.push({
    key: 'balance',
    kind: 'balance',
    name: '账户剩余未用学费',
    remainLessons: 0,
    amount: round2(balance),
    disabled: balance <= 0,
  })
  return rows
})

const refundSelectableRows = computed(() => refundRows.value.filter((row) => !row.disabled))
const refundAllChecked = computed(
  () =>
    refundSelectableRows.value.length > 0 &&
    refundSelectableRows.value.every((row) => refundSelected.value.includes(row.key)),
)
const refundIndeterminate = computed(() => {
  const count = refundSelectableRows.value.filter((row) => refundSelected.value.includes(row.key)).length
  return count > 0 && count < refundSelectableRows.value.length
})
const refundTotal = computed(() =>
  round2(
    refundRows.value
      .filter((row) => !row.disabled && refundSelected.value.includes(row.key))
      .reduce((sum, row) => sum + row.amount, 0),
  ),
)

function round2(value: number) {
  return Math.round((Number(value) || 0) * 100) / 100
}

function openRefund() {
  const available = refundSelectableRows.value
  if (!available.length) {
    message.warning('该学员暂无可退费的课程或账户余额')
    return
  }
  refundForm.payMethod = '现金'
  refundForm.reason = ''
  const balanceRow = available.find((row) => row.kind === 'balance')
  refundSelected.value = balanceRow ? [balanceRow.key] : []
  refundOpen.value = true
}

function onToggleRefundAll(event: { target?: { checked?: boolean } }) {
  const checked = Boolean(event?.target?.checked)
  refundSelected.value = checked ? refundSelectableRows.value.map((row) => row.key) : []
}

async function submitRefund() {
  const selected = refundRows.value.filter(
    (row) => !row.disabled && refundSelected.value.includes(row.key),
  )
  if (!selected.length) {
    message.warning('请选择要退费的课程或账户余额')
    return
  }
  const enrollmentIds = enrollments.value
    .filter((item) => selected.some((row) => row.kind === 'course' && row.key === `course-${item.id}`))
    .map((item) => item.id)
  const balanceAmount = round2(
    selected.filter((row) => row.kind === 'balance').reduce((sum, row) => sum + row.amount, 0),
  )
  if (!enrollmentIds.length && !(balanceAmount > 0)) {
    message.warning('所选项目没有可退金额')
    return
  }
  refundSaving.value = true
  try {
    const res = await refundStudentBatch({
      studentId,
      enrollmentIds,
      balanceAmount,
      payMethod: refundForm.payMethod,
      reason: refundForm.reason.trim() || undefined,
    })
    message.success(`退费成功，应退合计 ￥${moneyText(refundTotal.value)}（订单 ${res.orderNo}）`)
    refundOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '退费失败')
  } finally {
    refundSaving.value = false
  }
}

// ---------------- 资料费缴纳（A4 打印资料，无库存） ----------------
const dataFeeOpen = ref(false)
const dataFeeLoading = ref(false)
const dataFeeSaving = ref(false)
const dataFeeOptions = ref<DataFeeOptions | null>(null)
const dataFeeForm = reactive<{
  enrollmentId?: number
  templateId?: number
  payMethod: string
  handler: string
  remark: string
}>({
  enrollmentId: undefined,
  templateId: undefined,
  payMethod: '现金',
  handler: '',
  remark: '',
})

const dataFeeEnrollments = computed(() => dataFeeOptions.value?.enrollments ?? [])
const dataFeeTemplates = computed(() => dataFeeOptions.value?.templates ?? [])
const dataFeeEnrollment = computed(
  () => dataFeeEnrollments.value.find((item) => item.id === dataFeeForm.enrollmentId) ?? null,
)
const dataFeeTemplate = computed(
  () => dataFeeTemplates.value.find((item) => item.id === dataFeeForm.templateId) ?? null,
)
const dataFeePrice = computed(() => Number(dataFeeTemplate.value?.price ?? 0))
const dataFeeLastPaid = computed(() => {
  const time = dataFeeEnrollment.value?.lastPaidAt
  return time ? time.replace('T', ' ').slice(0, 10) : null
})

function cycleText(months: number | null | undefined) {
  const value = Number(months ?? 1)
  if (value === 1) return '每月'
  if (value === 2) return '每2个月'
  if (value === 3) return '每季度'
  if (value === 6) return '每半年'
  if (value === 12) return '每年'
  return `每${value}个月`
}

async function loadDataFeeOptions() {
  dataFeeLoading.value = true
  try {
    const data = await fetchDataFeeOptions(studentId)
    dataFeeOptions.value = data
    if (!dataFeeForm.enrollmentId || !data.enrollments.some((item) => item.id === dataFeeForm.enrollmentId)) {
      dataFeeForm.enrollmentId = data.enrollments[0]?.id
    }
    if (!dataFeeForm.templateId || !data.templates.some((item) => item.id === dataFeeForm.templateId)) {
      dataFeeForm.templateId = data.templates[0]?.id
    }
    if (!data.payMethods.includes(dataFeeForm.payMethod)) {
      dataFeeForm.payMethod = data.payMethods[0] ?? '现金'
    }
  } catch (e) {
    message.error((e as Error).message || '加载资料费模板失败')
  } finally {
    dataFeeLoading.value = false
  }
}

async function openDataFee() {
  const payable = enrollments.value.find((item) => item.status === '生效' || item.status === '停课')
  dataFeeForm.enrollmentId = payable?.id
  dataFeeForm.handler = authState.user?.name ?? ''
  dataFeeForm.remark = ''
  dataFeeOpen.value = true
  await loadDataFeeOptions()
}

async function submitDataFee() {
  if (!dataFeeForm.enrollmentId) {
    message.warning('请选择要缴纳资料费的课程')
    return
  }
  if (!dataFeeForm.templateId) {
    message.warning('请选择资料费模板')
    return
  }
  dataFeeSaving.value = true
  try {
    const res = await payDataFee({
      studentId,
      enrollmentId: dataFeeForm.enrollmentId,
      templateId: dataFeeForm.templateId,
      payMethod: dataFeeForm.payMethod,
      handler: dataFeeForm.handler.trim() || undefined,
      remark: dataFeeForm.remark.trim() || undefined,
    })
    message.success(`资料费缴纳成功，订单 ${res.orderNo}（周期 ${res.periodStart} 至 ${res.periodEnd}）`)
    dataFeeOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '资料费缴纳失败')
  } finally {
    dataFeeSaving.value = false
  }
}

// ---------------- 查看报读明细 ----------------
const viewOpen = ref(false)
const viewItem = ref<StudentDetail['enrollments'][number] | null>(null)

function openView(item: StudentDetail['enrollments'][number]) {
  viewItem.value = item
  viewOpen.value = true
}

// ---------------- 课时详情 ----------------
const lessonDetailOpen = ref(false)
const lessonDetailLoading = ref(false)
const lessonDetailItem = ref<StudentDetail['enrollments'][number] | null>(null)
const lessonDetailTxns = ref<StudentLessonTxn[]>([])

const lessonDetailGranted = computed(() => Number(lessonDetailItem.value?.totalLessons ?? 0))
const lessonDetailGift = computed(() => Number(lessonDetailItem.value?.giftLessons ?? 0))
const lessonDetailUsed = computed(() => Number(lessonDetailItem.value?.usedLessons ?? 0))
const lessonDetailRemain = computed(() => Number(lessonDetailItem.value?.remainLessons ?? 0))

async function openLessonDetail(item: StudentDetail['enrollments'][number]) {
  lessonDetailItem.value = item
  lessonDetailTxns.value = []
  lessonDetailOpen.value = true
  lessonDetailLoading.value = true
  try {
    lessonDetailTxns.value = await fetchStudentLessonTxns(studentId, item.id)
  } catch (e) {
    message.error((e as Error).message || '加载课时明细失败')
  } finally {
    lessonDetailLoading.value = false
  }
}

// ---------------- 升期 ----------------
const upgradeOpen = ref(false)
const upgradeSaving = ref(false)
const upgradeLoading = ref(false)
const upgradeQuoteLoading = ref(false)
const upgradeUpgrades = ref<CourseUpgradeRow[]>([])
const upgradePayMethods = ref<string[]>([])
const upgradeQuote = ref<UpgradeQuote | null>(null)
const upgradeForm = reactive<{
  enrollmentId?: number
  fromCourseName: string
  toCourseId?: number
  remainLessons: number
  lessons: number | null
  unitPrice: number | null
  extraPay: number | null
  deferPay: boolean
  payMethod?: string
  remark: string
}>({
  enrollmentId: undefined,
  fromCourseName: '',
  toCourseId: undefined,
  remainLessons: 0,
  lessons: null,
  unitPrice: null,
  extraPay: 0,
  deferPay: false,
  payMethod: undefined,
  remark: '',
})

const upgradeCourseOptions = computed(() =>
  upgradeUpgrades.value.flatMap((row) =>
    row.toCourseId === null
      ? []
      : [{ label: row.toCourseName || `课程#${row.toCourseId}`, value: row.toCourseId }],
  ),
)

const upgradePayOptions = computed(() =>
  upgradePayMethods.value.map((item) => ({ label: item, value: item })),
)

/** 未付差价：开启挂账时全部挂账，否则为应补差价减实收。 */
const upgradeOwed = computed(() => {
  const supplement = Number(upgradeQuote.value?.supplement ?? 0)
  if (upgradeForm.deferPay) return supplement
  return Math.max(0, supplement - Number(upgradeForm.extraPay ?? 0))
})

const upgradeSurplus = computed(() => Number(upgradeQuote.value?.surplus ?? 0))

async function openUpgrade(item: StudentDetail['enrollments'][number]) {
  upgradeForm.enrollmentId = item.id
  upgradeForm.fromCourseName = item.courseName
  upgradeForm.toCourseId = undefined
  upgradeForm.remainLessons = Number(item.remainLessons ?? 0)
  upgradeForm.lessons = null
  upgradeForm.unitPrice = null
  upgradeForm.extraPay = 0
  upgradeForm.deferPay = false
  upgradeForm.payMethod = undefined
  upgradeForm.remark = ''
  upgradeQuote.value = null
  upgradeOpen.value = true
  upgradeLoading.value = true
  try {
    const [list, options] = await Promise.all([fetchCourseUpgrades(), fetchLocalOrderOptions(studentId)])
    upgradeUpgrades.value = item.courseId
      ? list.filter((row) => row.fromCourseId === item.courseId)
      : list
    upgradePayMethods.value = options.payMethods ?? []
    upgradeForm.payMethod = upgradePayMethods.value[0]
  } catch (e) {
    message.error((e as Error).message || '获取升期信息失败')
  } finally {
    upgradeLoading.value = false
  }
}

/** 拉取升期换算：原剩余价值 ÷ 目标单价 = 目标课时，并计算应补差价。 */
async function refreshUpgradeQuote(autoLessons: boolean) {
  if (!upgradeForm.enrollmentId || !upgradeForm.toCourseId) {
    upgradeQuote.value = null
    return
  }
  upgradeQuoteLoading.value = true
  try {
    const requestLessons = autoLessons ? undefined : Number(upgradeForm.lessons ?? 0)
    const quote = await fetchUpgradeQuote({
      studentId,
      fromEnrollmentId: upgradeForm.enrollmentId,
      toCourseId: upgradeForm.toCourseId,
      toLessons: requestLessons && requestLessons > 0 ? requestLessons : undefined,
    })
    upgradeQuote.value = quote
    upgradeForm.unitPrice = quote.toUnitPrice
    upgradeForm.lessons = quote.targetLessons
    upgradeForm.extraPay = upgradeForm.deferPay ? 0 : quote.supplement
  } catch (e) {
    upgradeQuote.value = null
    message.error((e as Error).message || '升期课时换算失败')
  } finally {
    upgradeQuoteLoading.value = false
  }
}

async function onUpgradeCourseChange() {
  upgradeQuote.value = null
  upgradeForm.lessons = null
  if (!upgradeForm.toCourseId) return
  await refreshUpgradeQuote(true)
}

async function onUpgradeLessonsChange() {
  await refreshUpgradeQuote(false)
}

function onUpgradeDeferChange(checked: boolean) {
  upgradeForm.extraPay = checked ? 0 : Number(upgradeQuote.value?.supplement ?? 0)
}

async function submitUpgrade() {
  if (!upgradeForm.enrollmentId) return
  const toCourseId = upgradeForm.toCourseId
  if (!toCourseId) {
    message.warning('请选择升期目标课程')
    return
  }
  const toLessons = Number(upgradeForm.lessons ?? 0)
  if (!(toLessons > 0)) {
    message.warning('请填写大于 0 的升期课时')
    return
  }
  const toUnitPrice = Number(upgradeForm.unitPrice ?? 0)
  if (!(toUnitPrice > 0)) {
    message.warning('未获取到升期课程单价，请先配置价格模板')
    return
  }
  upgradeSaving.value = true
  try {
    await upgradeLocalOrder({
      studentId,
      campusId: student.value?.campusId,
      fromEnrollmentId: upgradeForm.enrollmentId,
      toCourseId,
      toLessons,
      toUnitPrice,
      extraPay: Number(upgradeForm.extraPay ?? 0),
      payMethod: upgradeForm.payMethod,
      remark: upgradeForm.remark.trim() || '升期',
    })
    message.success(
      upgradeOwed.value > 0
        ? `升期已办理，挂账 ${signedMoney(-upgradeOwed.value)}，已计入学员欠费`
        : '升期已办理',
    )
    upgradeOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '升期办理失败')
  } finally {
    upgradeSaving.value = false
  }
}

// ---------------- 买教材 ----------------
interface MaterialCartItem {
  name: string
  salePrice: number
  quantity: number
  unitPrice: number
}

const materialOpen = ref(false)
const materialSaving = ref(false)
const materialLoading = ref(false)
const materialOptions = ref<MaterialOptions | null>(null)
const materialStock = ref<MaterialStockRow[]>([])
const materialKeyword = ref('')
const materialPayMethods = ref<string[]>([])
const materialForm = reactive<{
  roomName?: string
  payMethod?: string
  items: MaterialCartItem[]
  useBalance: boolean
  balanceAmount: number | null
  handler: string
  remark: string
}>({
  roomName: undefined,
  payMethod: undefined,
  items: [],
  useBalance: false,
  balanceAmount: null,
  handler: '',
  remark: '',
})

const materialBalance = computed(() => Math.max(0, Number(student.value?.balance ?? 0)))
const materialCashPay = computed(() =>
  Math.max(0, Number((materialTotal.value - Number(materialForm.balanceAmount ?? 0)).toFixed(2))),
)

const materialPickOptions = computed(() => {
  const keyword = materialKeyword.value.trim()
  return (materialOptions.value?.materials ?? []).filter(
    (item) => item.status !== '停用' && (!keyword || item.name.includes(keyword)),
  )
})
const materialRoomOptions = computed(() =>
  (materialOptions.value?.rooms ?? []).map((item) => ({ label: item, value: item })),
)
const materialPayOptions = computed(() =>
  materialPayMethods.value.map((item) => ({ label: item, value: item })),
)

const materialItemColumns = [
  { title: '教材', dataIndex: 'name', ellipsis: true },
  { title: '原价', dataIndex: 'salePrice', width: 88, align: 'right' as const },
  { title: '数量', dataIndex: 'quantity', width: 92 },
  { title: '单价', dataIndex: 'unitPrice', width: 112 },
  { title: '金额', dataIndex: 'amount', width: 90, align: 'right' as const },
  { title: '', dataIndex: 'action', width: 52 },
]

const materialTotal = computed(() =>
  Number(
    materialForm.items
      .reduce((sum, item) => sum + Number(item.quantity ?? 0) * Number(item.unitPrice ?? 0), 0)
      .toFixed(2),
  ),
)
const materialOriginTotal = computed(() =>
  Number(
    materialForm.items
      .reduce((sum, item) => sum + Number(item.quantity ?? 0) * Number(item.salePrice ?? 0), 0)
      .toFixed(2),
  ),
)
const materialQuantityTotal = computed(() =>
  materialForm.items.reduce((sum, item) => sum + Number(item.quantity ?? 0), 0),
)

function materialItemAmount(item: MaterialCartItem) {
  return Number((Number(item.quantity ?? 0) * Number(item.unitPrice ?? 0)).toFixed(2))
}

function materialRoomStock(name: string) {
  const row = materialStock.value.find((item) => item.name === name)
  if (!row) return null
  if (materialForm.roomName === '总库') return row.warehouse
  if (materialForm.roomName === '鲅鱼圈幻彩国学堂') return row.bayuquan
  if (materialForm.roomName === '新民幻彩国学堂') return row.xinmin
  return null
}

function isMaterialSelected(name: string) {
  return materialForm.items.some((item) => item.name === name)
}

function toggleMaterial(name: string, checked: boolean) {
  if (checked) {
    if (isMaterialSelected(name)) return
    const option = (materialOptions.value?.materials ?? []).find((item) => item.name === name)
    const salePrice = Number(option?.salePrice ?? 0)
    materialForm.items.push({ name, salePrice, quantity: 1, unitPrice: salePrice })
  } else {
    materialForm.items = materialForm.items.filter((item) => item.name !== name)
  }
}

function onMaterialCheck(name: string, event: { target: { checked: boolean } }) {
  toggleMaterial(name, event.target.checked)
}

function removeMaterial(name: string) {
  materialForm.items = materialForm.items.filter((item) => item.name !== name)
}

function toggleMaterialBalance(checked: boolean) {
  materialForm.balanceAmount = checked
    ? Number(Math.min(materialBalance.value, materialTotal.value).toFixed(2))
    : null
}

async function openMaterial() {
  materialForm.roomName = undefined
  materialForm.payMethod = undefined
  materialForm.items = []
  materialForm.useBalance = false
  materialForm.balanceAmount = null
  materialForm.handler = authState.user?.name || '管理员'
  materialForm.remark = ''
  materialKeyword.value = ''
  materialOpen.value = true
  materialLoading.value = true
  try {
    const [options, stock, orderOptions] = await Promise.all([
      fetchMaterialOptions(),
      fetchMaterialStock({ page: 1, size: 500 }),
      fetchLocalOrderOptions(studentId),
    ])
    materialOptions.value = options
    materialStock.value = stock.records ?? []
    materialPayMethods.value = orderOptions.payMethods ?? []
    materialForm.payMethod = materialPayMethods.value[0]
    const campus = student.value?.campusText
    materialForm.roomName =
      campus && options.rooms.includes(campus) ? campus : options.rooms[0]
  } catch (e) {
    message.error((e as Error).message || '加载教材信息失败')
  } finally {
    materialLoading.value = false
  }
}

async function submitMaterial() {
  if (!materialForm.items.length) {
    message.warning('请选择教材')
    return
  }
  if (!materialForm.roomName) {
    message.warning('请选择出库校区')
    return
  }
  if (!materialForm.payMethod) {
    message.warning('请选择支付方式')
    return
  }
  for (const item of materialForm.items) {
    const quantity = Number(item.quantity ?? 0)
    if (!(quantity > 0)) {
      message.warning(`「${item.name}」购买数量必须大于 0`)
      return
    }
    if (Number(item.unitPrice ?? 0) < 0) {
      message.warning(`「${item.name}」单价不能为负数`)
      return
    }
    const stock = materialRoomStock(item.name)
    if (stock !== null && quantity > stock) {
      message.warning(`「${item.name}」库存不足，当前校区剩余 ${stock} 本`)
      return
    }
  }
  const balanceAmount = materialForm.useBalance ? Number(materialForm.balanceAmount ?? 0) : 0
  if (balanceAmount < 0) {
    message.warning('抵扣金额不能为负数')
    return
  }
  if (balanceAmount > materialBalance.value) {
    message.warning(`账户剩余未用学费不足，当前可用 ${formatMoney(materialBalance.value)}`)
    return
  }
  if (balanceAmount > materialTotal.value) {
    message.warning('抵扣金额不能大于本次教材金额')
    return
  }
  materialSaving.value = true
  try {
    const res = await createMaterialPurchase({
      studentId,
      roomName: materialForm.roomName,
      payMethod: materialForm.payMethod,
      items: materialForm.items.map((item) => ({
        materialName: item.name,
        quantity: Number(item.quantity ?? 0),
        unitPrice: Number(item.unitPrice ?? 0),
      })),
      balanceAmount: balanceAmount > 0 ? Number(balanceAmount.toFixed(2)) : undefined,
      handler: materialForm.handler.trim() || undefined,
      remark: materialForm.remark.trim() || undefined,
    })
    const extra = balanceAmount > 0 ? `，账户余额抵扣 ${formatMoney(balanceAmount)}` : ''
    message.success(
      `已购买 ${res.itemCount} 种教材共 ${res.quantity} 本，金额 ${formatMoney(res.totalAmount)}${extra}`,
    )
    materialOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '购买教材失败')
  } finally {
    materialSaving.value = false
  }
}

async function load() {
  if (!Number.isFinite(studentId) || studentId <= 0) {
    message.error('学员编号无效')
    router.replace('/student')
    return
  }
  loading.value = true
  try {
    detail.value = await fetchStudentDetail(studentId)
  } catch (e) {
    message.error((e as Error).message || '加载学员资料失败')
  } finally {
    loading.value = false
  }
}

function goOrder() {
  router.push('/order')
}

// ---------------- 学员订单 ----------------
const orderLoading = ref(false)
const orderRows = ref<SpOrderRow[]>([])
const orderTotal = ref(0)
const orderPageIndex = ref(1)
const orderPageSize = 10

const orderColumns = [
  { title: '订单号', dataIndex: 'orderNo', width: 150 },
  { title: '订单类型', dataIndex: 'typeName', width: 96 },
  { title: '交易内容', dataIndex: 'content', ellipsis: true },
  { title: '应收/应退', dataIndex: 'shouldPay', width: 100, align: 'right' as const },
  { title: '实收/实退', dataIndex: 'realPay', width: 100, align: 'right' as const },
  { title: '付款方式', dataIndex: 'payMethod', width: 96 },
  { title: '订单状态', dataIndex: 'statusName', width: 96 },
  { title: '操作时间', dataIndex: 'createdTime', width: 160 },
]

const orderPagination = computed(() => ({
  current: orderPageIndex.value,
  pageSize: orderPageSize,
  total: orderTotal.value,
  showSizeChanger: false,
  size: 'small' as const,
}))

async function loadStudentOrders() {
  if (!Number.isFinite(studentId) || studentId <= 0) return
  orderLoading.value = true
  try {
    const page = await fetchSpOrderPage({
      studentId,
      pageIndex: orderPageIndex.value,
      pageSize: orderPageSize,
    })
    orderRows.value = page.list
    orderTotal.value = page.total
  } catch (e) {
    message.error((e as Error).message || '加载学员订单失败')
  } finally {
    orderLoading.value = false
  }
}

function handleOrderPageChange(pagination: { current?: number }) {
  orderPageIndex.value = pagination.current ?? 1
  loadStudentOrders()
}

// ---------------- 记课时（扣减） ----------------
const consumeOpen = ref(false)
const consumeSaving = ref(false)
const consumeForm = reactive<{
  enrollmentId?: number
  lessons: number | null
  unitPrice: number | null
  remark: string
}>({ enrollmentId: undefined, lessons: null, unitPrice: null, remark: '' })
const priceInfo = ref<LessonPriceInfo | null>(null)
const priceLoading = ref(false)

const selectedEnrollment = computed(
  () => activeEnrollments.value.find((item) => item.id === consumeForm.enrollmentId) ?? null,
)

const consumeAmount = computed(() => {
  const lessons = Number(consumeForm.lessons ?? 0)
  const price = Number(consumeForm.unitPrice ?? 0)
  if (!(lessons > 0) || !(price > 0)) return 0
  const remain = Math.max(Number(selectedEnrollment.value?.remainLessons ?? 0), 0)
  const within = Math.min(lessons, remain)
  const overflow = Math.max(lessons - within, 0)
  const original = Math.max(Number(priceInfo.value?.originalUnitPrice ?? price), price)
  return Number((within * price + overflow * original).toFixed(2))
})

const projectedRemain = computed(() =>
  Number((Number(selectedEnrollment.value?.remainLessons ?? 0) - Number(consumeForm.lessons ?? 0)).toFixed(2)),
)

const projectedBalance = computed(() =>
  Number((Number(selectedEnrollment.value?.accountBalance ?? 0) - consumeAmount.value).toFixed(2)),
)

async function loadConsumePrice() {
  priceInfo.value = null
  if (!consumeForm.enrollmentId) return
  priceLoading.value = true
  try {
    priceInfo.value = await fetchLessonPrice(studentId, consumeForm.enrollmentId)
    consumeForm.unitPrice = priceInfo.value.unitPrice ?? null
  } catch (e) {
    message.error((e as Error).message || '获取课时单价失败')
  } finally {
    priceLoading.value = false
  }
}

async function submitConsume() {
  if (!consumeForm.enrollmentId) return
  const lessons = Number(consumeForm.lessons ?? 0)
  if (!(lessons > 0)) {
    message.warning('请填写大于 0 的扣减课时')
    return
  }
  if (!(Number(consumeForm.unitPrice ?? 0) > 0)) {
    message.warning('请填写大于 0 的课时单价')
    return
  }
  consumeSaving.value = true
  try {
    const res = await consumeLessons({
      studentId,
      enrollmentId: consumeForm.enrollmentId,
      lessons,
      unitPrice: Number(consumeForm.unitPrice),
      remark: consumeForm.remark.trim() || undefined,
    })
    message.success(
      `已扣 ${lessons} 课时，金额 ${signedMoney(res.amount)}；剩余 ${res.remainLessons} 课时，账户余额 ${signedMoney(res.accountBalance)}${
        Number(res.debt) > 0 ? `，欠费 ${signedMoney(-res.debt)}` : ''
      }`,
    )
    consumeOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '记课时失败')
  } finally {
    consumeSaving.value = false
  }
}

// ---------------- 编辑资料 ----------------
type StudentFormState = {
  name: string
  gender: string
  birthday?: string
  guardianName: string
  primaryRelation?: string
  phone: string
  campusId?: number
  status: string
  grade?: string
  school?: string
  address?: string
  wechat?: string
  remark?: string
}

const formOpen = ref(false)
const saving = ref(false)
const form = reactive<StudentFormState>({
  name: '',
  gender: '未知',
  guardianName: '',
  phone: '',
  campusId: 1,
  status: '在读',
})

const relationOptions = ['父亲', '母亲', '祖父母', '其他']

function openEdit() {
  if (!student.value) return
  Object.assign(form, {
    name: student.value.name ?? '',
    gender: student.value.gender || '未知',
    birthday: student.value.birthday ?? undefined,
    guardianName: primaryGuardian.value?.name ?? '',
    primaryRelation: student.value.primaryRelation ?? undefined,
    phone: student.value.phone ?? '',
    campusId: student.value.campusId ?? 1,
    status: student.value.status || '在读',
    grade: student.value.grade ?? undefined,
    school: student.value.school ?? undefined,
    address: student.value.address ?? undefined,
    wechat: student.value.wechat ?? undefined,
    remark: student.value.remark ?? undefined,
  })
  formOpen.value = true
}

async function saveForm() {
  if (!form.name.trim()) {
    message.warning('请填写学员姓名')
    return
  }
  if (!form.campusId) {
    message.warning('请选择报读校区')
    return
  }
  const payload: StudentFormPayload = {
    name: form.name.trim(),
    gender: form.gender,
    birthday: form.birthday || undefined,
    guardianName: form.guardianName.trim() || undefined,
    primaryRelation: form.primaryRelation || undefined,
    phone: form.phone.trim() || undefined,
    campusId: form.campusId,
    status: form.status,
    grade: form.grade || undefined,
    school: form.school || undefined,
    address: form.address || undefined,
    wechat: form.wechat || undefined,
    remark: form.remark || undefined,
  }
  saving.value = true
  try {
    await updateStudent(studentId, payload)
    message.success('学员资料已保存')
    formOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 从办理中心快捷入口跳转时，进入该学员后自动打开对应功能。 */
function runPendingAction() {
  const action = typeof route.query.action === 'string' ? route.query.action : ''
  if (!action) return
  const query = Object.fromEntries(
    Object.entries(route.query).filter(([key]) => key !== 'action'),
  )
  router.replace({ path: route.path, query })
  if (action === 'refund') {
    openRefund()
  } else if (action === 'material') {
    openMaterial()
  } else if (action === 'points') {
    pointsOpen.value = true
  } else if (action === 'recharge' || action === 'subsidy') {
    localOrderMode.value = action
    localOrderOpen.value = true
  }
}

async function onLocalOrderSaved() {
  await load()
}

onMounted(async () => {
  await load()
  if (tab.value === 'order') loadStudentOrders()
  runPendingAction()
})
</script>

<template>
  <div class="sd-page">
    <a-spin :spinning="loading">
      <template v-if="student">
        <!-- 学员信息头部 -->
        <div class="sd-head">
          <div class="sd-head-bar">
            <div class="sd-head-left">
              <span class="sd-head-text">
                账户剩余未用学费:{{ moneyText(student.balance) }}元
              </span>
              <span class="sd-head-text sd-head-points">
                积分：{{ Number(student.points ?? 0).toLocaleString('zh-CN') }} 积分
                <a class="sd-points-action" @click="pointsOpen = true">积分兑换</a>
              </span>
              <a class="sd-head-buy" @click="openMaterial">买教材</a>
            </div>
            <div class="sd-head-right">
              <a class="sd-head-link" @click="openEdit">编辑</a>
              <span class="sd-head-sep">|</span>
              <a class="sd-head-link" @click="confirmDelete">删除</a>
            </div>
          </div>

          <div class="sd-profile">
            <div class="sd-avatar">
              <a-avatar :size="65" :style="{ background: '#f2f4f7', color: '#b7bec7' }">
                <template #icon><user-outlined /></template>
              </a-avatar>
            </div>
            <div class="sd-name-line">
              <span class="sd-name">{{ student.name }}</span>
              <span class="sd-relation">（{{ student.primaryRelation || '主要联系人' }}:{{ student.phone || '—' }}）</span>
              <span class="sd-badge">{{ student.status }}</span>
              <span v-if="Number(student.debt) > 0" class="sd-debt">欠费 {{ signedMoney(-Number(student.debt)) }}</span>
            </div>

            <ul class="sd-fields">
              <li v-for="item in basicFields" :key="item.label" class="sd-field">
                <label>{{ item.label }}</label>
                <span class="sd-field-value">{{ item.value }}</span>
              </li>
            </ul>
          </div>
        </div>

        <!-- 标签栏 -->
        <ul class="sd-nav">
          <li
            v-for="item in tabs"
            :key="item.key"
            class="sd-nav-item"
            :class="{ current: tab === item.key }"
            @click="tab = item.key"
          >{{ item.label }}</li>
        </ul>

        <!-- 标签内容 -->
        <div class="sd-body">
          <template v-if="tab === 'course'">
            <div class="sd-toolbar">
              <a-button
                type="primary"
                @click="router.push({ path: '/enroll', query: { studentId: String(studentId) } })"
              >报 名</a-button>
              <a-button @click="openRefund()">退 费</a-button>
              <a-button @click="openDataFee">资料费缴纳</a-button>
            </div>

            <a-empty v-if="!enrollments.length" description="该学员暂无报读记录，可在办理中心「订单列表」办理报名">
              <a-button type="primary" @click="goOrder">前往办理报名</a-button>
            </a-empty>

            <div v-for="item in enrollments" :key="item.id" class="sd-lesson-card">
              <div class="sd-card-head">
                <div class="sd-card-left">
                  <div class="sd-card-tags">
                    <span class="sd-chip">班</span>
                    <span class="sd-chip">按课时</span>
                  </div>
                  <div class="sd-card-main">
                    <div class="sd-card-title">
                      <span class="sd-course">{{ item.courseName }}</span>
                    </div>
                    <div class="sd-campus">校区：{{ student.campusText || '—' }}</div>
                  </div>
                </div>
                <div class="sd-card-actions">
                  <a-button size="small" @click="printTicket(item)">打印听课证</a-button>
                  <template v-if="item.status !== '结课'">
                    <a-button size="small" @click="openUpgrade(item)">升 期</a-button>
                    <a-button
                      size="small"
                      @click="router.push({ path: '/enroll', query: { studentId: String(studentId), courseId: String(item.courseId ?? ''), enrollmentId: String(item.id), classId: String(item.classId ?? '') } })"
                    >续 费</a-button>
                    <a-button
                      size="small"
                      @click="router.push({ path: '/transfer', query: { studentId: String(studentId), enrollmentId: String(item.id) } })"
                    >转 课</a-button>
                    <a-button
                      size="small"
                      @click="toggleStop(item)"
                    >
                      {{ item.status === '停课' ? '复 课' : '停 课' }}
                    </a-button>
                    <a-button size="small" @click="confirmFinish(item)">结 课</a-button>
                    <a-button size="small" @click="openAssign(item)">分 班</a-button>
                  </template>
                </div>
              </div>

              <div class="sd-card-body">
                <ul class="sd-card-info">
                  <li class="sd-info-row">
                    <label>报名时间</label>
                    <span>{{ dayText(item.enrollTime) }}</span>
                  </li>
                  <li class="sd-info-row">
                    <label>剩余课时</label>
                    <span :class="{ 'sd-danger': Number(item.remainLessons) < 0 }">
                      {{ item.remainLessons }}
                    </span>
                    <a class="sd-inline-link" @click="openLessonDetail(item)">查询详情</a>
                    <a class="sd-inline-link" @click="openExpire(item)">设置有效期</a>
                  </li>
                  <li class="sd-info-row">
                    <label>绑定学费</label>
                    <span :class="{ 'sd-danger': Number(item.accountBalance) < 0 }">
                      {{ moneyText(item.accountBalance) }}
                    </span>
                    <a class="sd-inline-link" @click="openView(item)">查看</a>
                  </li>
                  <li class="sd-info-row">
                    <label>资料费</label>
                    <span :class="{ 'sd-muted': !item.lastDataFeePaidAt }">
                      {{ item.lastDataFeePaidAt ? `上次缴纳 ${dayText(item.lastDataFeePaidAt)}` : '未缴纳' }}
                    </span>
                  </li>
                </ul>

                <table class="sd-card-table">
                  <thead>
                    <tr>
                      <th class="col-status">状态</th>
                      <th>班级名称</th>
                      <th class="col-teacher">教师</th>
                      <th class="col-date">开班日期</th>
                      <th class="col-used">已用课时</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td class="col-status">
                        <span class="sd-status" :class="statusClass(item.status)">{{ item.status }}</span>
                      </td>
                      <td>{{ item.className || '未分班' }}</td>
                      <td class="col-teacher">{{ item.teacher || '—' }}</td>
                      <td class="col-date">{{ item.startDate || '—' }}</td>
                      <td class="col-used">{{ item.usedLessons }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </template>

          <template v-else-if="tab === 'account'">
            <div class="sd-summary">
              <span>账户余额 <b>{{ formatMoney(student.balance) }}</b></span>
              <span>欠费 <b :class="{ 'sd-danger': Number(student.debt) > 0 }">{{ signedMoney(-Number(student.debt)) }}</b></span>
              <span>在读课程 <b>{{ student.courseCount ?? 0 }} 门</b></span>
            </div>
            <a-table
              :columns="[
                { title: '课程', dataIndex: 'courseName' },
                { title: '班级', dataIndex: 'className', width: 180 },
                { title: '总课时', dataIndex: 'totalLessons', width: 90, align: 'right' },
                { title: '赠送', dataIndex: 'giftLessons', width: 80, align: 'right' },
                { title: '剩余', dataIndex: 'remainLessons', width: 90, align: 'right' },
                { title: '实收', dataIndex: 'paidAmount', width: 110, align: 'right' },
                { title: '单价', dataIndex: 'unitPrice', width: 90, align: 'right' },
                { title: '账户余额', dataIndex: 'accountBalance', width: 110, align: 'right' },
                { title: '有效期至', dataIndex: 'expireDate', width: 120 },
              ]"
              :data-source="enrollments"
              :pagination="false"
              :scroll="{ x: 1080 }"
              size="small"
              row-key="id"
            >
              <template #bodyCell="{ column, record }">
                <span v-if="column.dataIndex === 'paidAmount'">{{ formatMoney(record.paidAmount) }}</span>
                <span v-else-if="column.dataIndex === 'unitPrice'">
                  {{ record.unitPrice === null || record.unitPrice === undefined ? '—' : formatMoney(record.unitPrice) }}
                </span>
                <span
                  v-else-if="column.dataIndex === 'accountBalance'"
                  :class="{ 'sd-danger': Number(record.accountBalance) < 0 }"
                >{{ signedMoney(record.accountBalance) }}</span>
                <span v-else-if="column.dataIndex === 'remainLessons'">
                  <b :class="{ 'sd-danger': Number(record.remainLessons) < 0 }">{{ record.remainLessons }}</b> 课时
                </span>
                <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
                <span v-else-if="column.dataIndex === 'expireDate'">{{ record.expireDate || '—' }}</span>
                <span v-else>{{ record[column.dataIndex] }}</span>
              </template>
            </a-table>
          </template>

          <template v-else-if="tab === 'log'">
            <a-timeline v-if="detail?.txns?.length">
              <a-timeline-item
                v-for="item in detail.txns"
                :key="item.id"
                :color="Number(item.lessons) > 0 ? 'green' : 'blue'"
              >
                <div class="sd-txn-line">
                  <span class="sd-txn-time">{{ formatDateTime(item.createdAt) }}</span>
                  <b :class="Number(item.lessons) > 0 ? 'sd-txn-add' : 'sd-txn-sub'">
                    {{ Number(item.lessons) > 0 ? '+' : '' }}{{ item.lessons }} 课时
                  </b>
                  <span class="sd-muted">{{ item.note || item.bizType || '—' }}</span>
                </div>
              </a-timeline-item>
            </a-timeline>
            <a-empty v-else description="暂无课时流水记录" />
          </template>

          <template v-else-if="tab === 'chat'">
            <a-descriptions :column="1" size="small" bordered>
              <a-descriptions-item
                v-for="item in detail?.guardians ?? []"
                :key="item.id"
                :label="`${item.relation || '联系人'}${item.isPrimary ? '（主要）' : ''}`"
              >{{ item.name }} · {{ item.phone }}</a-descriptions-item>
            </a-descriptions>
            <p class="sd-empty-note">暂无沟通互动记录</p>
          </template>

          <template v-else-if="tab === 'order'">
            <div class="sd-order-head">
              <span>共 {{ orderTotal }} 笔订单</span>
              <a @click="goOrder">前往订单列表</a>
            </div>
            <a-table
              :columns="orderColumns"
              :data-source="orderRows"
              :loading="orderLoading"
              :pagination="orderPagination"
              row-key="id"
              size="small"
              :scroll="{ x: 990 }"
              @change="handleOrderPageChange"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'content'">
                  <span :title="record.content || ''">{{ record.content || '—' }}</span>
                </template>
                <template v-else-if="column.dataIndex === 'shouldPay'">
                  {{ signedMoney(record.shouldPay) }}
                </template>
                <template v-else-if="column.dataIndex === 'realPay'">
                  {{ signedMoney(record.realPay) }}
                </template>
                <template v-else-if="column.dataIndex === 'payMethod'">
                  {{ record.payMethod || '—' }}
                </template>
                <template v-else-if="column.dataIndex === 'createdTime'">
                  {{ formatDateTime(record.createdTime) }}
                </template>
              </template>
            </a-table>
          </template>

          <template v-else-if="tab === 'schedule'">
            <a-empty description="暂无课程表" />
          </template>

          <template v-else-if="tab === 'attendance'">
            <a-empty description="暂无上课记录">
              <a-button @click="router.push('/teachingLog')">前往记上课</a-button>
            </a-empty>
          </template>

          <template v-else-if="tab === 'growth'">
            <a-empty description="暂无成长档案记录" />
          </template>

          <template v-else-if="tab === 'score'">
            <a-spin :spinning="gradeLoading">
              <template v-if="gradeItems.length">
                <div class="grade-toolbar">
                  <span class="grade-toolbar-text">仅展示已发布的考试成绩</span>
                  <a-button size="small" @click="router.push('/gradeQuery')">前往成绩查询</a-button>
                </div>
                <div v-if="gradeTrend" class="grade-chart">
                  <div class="grade-chart-title">成绩趋势（按满分百分比）</div>
                  <svg :viewBox="`0 0 ${gradeTrend.width} ${gradeTrend.height}`" class="grade-svg">
                    <line
                      :x1="gradeTrend.pad.left"
                      :y1="gradeTrend.pad.top + gradeTrend.plotH"
                      :x2="gradeTrend.width - gradeTrend.pad.right"
                      :y2="gradeTrend.pad.top + gradeTrend.plotH"
                      stroke="#e5e6eb"
                    />
                    <polyline
                      :points="gradeTrend.points.map((point) => `${point.x},${point.y}`).join(' ')"
                      fill="none"
                      stroke="#3b6ef6"
                      stroke-width="2"
                    />
                    <g v-for="(point, index) in gradeTrend.points" :key="index">
                      <circle :cx="point.x" :cy="point.y" r="3.5" fill="#3b6ef6" />
                      <text :x="point.x" :y="point.y - 9" text-anchor="middle" class="grade-svg-text">
                        {{ point.percent }}%
                      </text>
                    </g>
                  </svg>
                </div>
                <div v-if="radarLayout" class="grade-chart">
                  <div class="grade-chart-title">能力雷达 · {{ radarExamName }}</div>
                  <svg viewBox="0 0 240 240" class="grade-radar">
                    <polygon
                      v-for="(ring, index) in radarLayout.rings"
                      :key="`ring-${index}`"
                      :points="ring"
                      fill="none"
                      stroke="#e5e6eb"
                    />
                    <line
                      v-for="(axis, index) in radarLayout.axes"
                      :key="`axis-${index}`"
                      :x1="radarLayout.cx"
                      :y1="radarLayout.cy"
                      :x2="axis.x"
                      :y2="axis.y"
                      stroke="#e5e6eb"
                    />
                    <polygon :points="radarLayout.polygon" fill="rgba(59,110,246,0.18)" stroke="#3b6ef6" stroke-width="2" />
                    <text
                      v-for="(axis, index) in radarLayout.axes"
                      :key="`label-${index}`"
                      :x="axis.labelPoint.x"
                      :y="axis.labelPoint.y"
                      text-anchor="middle"
                      class="grade-svg-text"
                    >
                      {{ axis.label }}
                    </text>
                  </svg>
                </div>
                <a-table
                  :columns="gradeColumns"
                  :data-source="gradeItems"
                  :pagination="false"
                  row-key="examId"
                  size="small"
                >
                  <template #bodyCell="{ column, record }">
                    <span v-if="column.dataIndex === 'examDate'">{{ record.examDate ?? '—' }}</span>
                    <span v-else-if="column.dataIndex === 'className'">{{ record.className ?? '—' }}</span>
                    <span v-else-if="column.dataIndex === 'score'">
                      {{ record.status !== '正常' ? record.status
                        : record.scoringMode === '等级' ? (record.level ?? '—')
                        : `${record.totalScore ?? '—'} / ${record.fullScore}` }}
                    </span>
                    <span v-else-if="column.dataIndex === 'rankClass'">{{ record.rankClass ?? '—' }}</span>
                    <span v-else-if="column.dataIndex === 'rankCampus'">{{ record.rankCampus ?? '—' }}</span>
                    <span v-else-if="column.dataIndex === 'remark'">{{ record.remark ?? '—' }}</span>
                  </template>
                </a-table>
              </template>
              <a-empty v-else-if="!gradeLoading" description="暂无已发布的考试成绩，成绩录入并发布后会展示在这里" />
            </a-spin>
          </template>
        </div>
      </template>

      <a-empty v-else-if="!loading" description="未找到该学员" />
    </a-spin>

    <!-- 退费：可只退某门课程或账户余额，也可多选全退 -->
    <a-modal
      v-model:open="refundOpen"
      title="退费"
      width="660px"
      ok-text="确认退费"
      cancel-text="取消"
      :confirm-loading="refundSaving"
      :ok-button-props="{ disabled: refundSaving || refundTotal <= 0 }"
      @ok="submitRefund"
    >
      <div class="rf-toolbar">
        <a-checkbox
          :checked="refundAllChecked"
          :indeterminate="refundIndeterminate"
          :disabled="!refundSelectableRows.length"
          @change="onToggleRefundAll"
        >全选（全退）</a-checkbox>
        <span class="rf-tip">可只退一门课程或账户余额，也可多选一次性全退</span>
      </div>
      <a-checkbox-group v-model:value="refundSelected" class="rf-list">
        <div
          v-for="row in refundRows"
          :key="row.key"
          class="rf-row"
          :class="{ 'is-disabled': row.disabled }"
        >
          <a-checkbox :value="row.key" :disabled="row.disabled">
            <span class="rf-name">{{ row.name }}</span>
            <span v-if="row.kind === 'course'" class="rf-sub">剩余 {{ row.remainLessons }} 课时</span>
            <span v-else class="rf-sub">账户余额</span>
          </a-checkbox>
          <span class="rf-amount">
            {{ row.disabled && row.amount <= 0 ? '无可退金额' : `￥${moneyText(row.amount)}` }}
          </span>
        </div>
      </a-checkbox-group>
      <div class="rf-total">
        <span>合计应退</span>
        <b>￥{{ moneyText(refundTotal) }}</b>
      </div>
      <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 19 }" size="small" class="rf-form">
        <a-form-item label="退款方式">
          <a-select v-model:value="refundForm.payMethod">
            <a-select-option value="现金">现金</a-select-option>
            <a-select-option value="微信">微信</a-select-option>
            <a-select-option value="支付宝">支付宝</a-select-option>
            <a-select-option value="银行转账">银行转账</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="退费原因">
          <a-input v-model:value="refundForm.reason" placeholder="选填，将写入退费订单" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 资料费缴纳：按课程收取 A4 打印资料费，不占用教材库存 -->
    <a-modal
      v-model:open="dataFeeOpen"
      title="资料费缴纳"
      width="540px"
      ok-text="确认缴纳"
      cancel-text="取消"
      :confirm-loading="dataFeeSaving"
      :ok-button-props="{
        disabled: dataFeeSaving || !dataFeeForm.enrollmentId || !dataFeeForm.templateId,
      }"
      @ok="submitDataFee"
    >
      <a-spin :spinning="dataFeeLoading">
        <div v-if="!dataFeeEnrollments.length" class="df-empty">
          该学员没有在读课程，无法缴纳资料费
        </div>
        <div v-else-if="!dataFeeTemplates.length" class="df-empty">
          暂无启用中的资料费模板，请先到「库存管理 → 资料费模板」新建模板
        </div>
        <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }" size="small">
          <a-form-item label="课程" required>
            <a-select
              v-model:value="dataFeeForm.enrollmentId"
              placeholder="请选择课程"
              :options="dataFeeEnrollments.map((item) => ({ value: item.id, label: item.courseName }))"
            />
          </a-form-item>
          <a-form-item label="资料费模板" required>
            <a-select
              v-model:value="dataFeeForm.templateId"
              placeholder="请选择模板"
              :options="dataFeeTemplates.map((item) => ({
                value: item.id,
                label: `${item.name}（￥${moneyText(item.price)} / ${cycleText(item.cycleMonths)}）`,
              }))"
            />
          </a-form-item>
          <a-form-item label="本次金额">
            <b class="df-amount">￥{{ moneyText(dataFeePrice) }}</b>
            <span class="df-hint">按模板价格收取，资料为 A4 打印件，不占用教材库存</span>
          </a-form-item>
          <a-form-item label="上次缴纳">
            <span>{{ dataFeeLastPaid ? `上次缴纳 ${dataFeeLastPaid}` : '该课程尚未缴纳资料费' }}</span>
          </a-form-item>
          <a-form-item label="缴纳周期">
            <span>{{ dataFeeTemplate ? cycleText(dataFeeTemplate.cycleMonths) : '—' }}</span>
          </a-form-item>
          <a-form-item label="缴费方式">
            <a-select
              v-model:value="dataFeeForm.payMethod"
              :options="(dataFeeOptions?.payMethods ?? []).map((item) => ({ value: item, label: item }))"
            />
          </a-form-item>
          <a-form-item label="经办人">
            <a-input v-model:value="dataFeeForm.handler" :maxlength="32" placeholder="默认当前登录账号" />
          </a-form-item>
          <a-form-item label="备注">
            <a-input v-model:value="dataFeeForm.remark" :maxlength="120" placeholder="选填，将写入订单说明" allow-clear />
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>

    <!-- 记课时（扣减） -->
    <a-modal
      v-model:open="consumeOpen"
      title="记课时（扣减）"
      width="540px"
      ok-text="确认扣减"
      cancel-text="取消"
      :confirm-loading="consumeSaving"
      @ok="submitConsume"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 17 }" size="small">
        <a-form-item label="报读记录" required>
          <a-select
            v-model:value="consumeForm.enrollmentId"
            placeholder="请选择报读的课程"
            @change="loadConsumePrice"
          >
            <a-select-option v-for="item in activeEnrollments" :key="item.id" :value="item.id">
              {{ item.courseName }}（剩余 {{ item.remainLessons }} 课时 / 余额 {{ signedMoney(item.accountBalance) }}）
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="扣减课时" required>
          <a-input-number
            v-model:value="consumeForm.lessons"
            :min="0.01"
            :step="1"
            :precision="2"
            style="width: 100%"
            placeholder="可超过剩余课时，超出部分自动转为欠费"
          />
        </a-form-item>
        <a-form-item label="课时单价" required>
          <a-input-number
            v-model:value="consumeForm.unitPrice"
            :min="0.01"
            :precision="2"
            style="width: 100%"
            placeholder="元 / 课时"
          />
          <div class="sd-price-hint">
            <template v-if="priceLoading">正在获取预填单价…</template>
            <template v-else-if="priceInfo">
              <span v-if="priceInfo.unitPrice !== null">
                预填单价来源：{{ priceInfo.source }}，可手动调整
              </span>
              <span v-else class="sd-danger">该报读未设置单价，请手动填写单价</span>
            </template>
          </div>
          <div
            v-if="priceInfo && Number(priceInfo.originalUnitPrice ?? 0) > Number(consumeForm.unitPrice ?? 0)"
            class="sd-price-hint"
          >
            超出剩余课时的部分按课程原价 {{ formatMoney(priceInfo.originalUnitPrice) }} / 课时计费
          </div>
        </a-form-item>
        <a-form-item label="本次金额">
          <b>{{ signedMoney(consumeAmount) }}</b>
        </a-form-item>
        <a-form-item label="扣后结果">
          <span>
            剩余 <b :class="{ 'sd-danger': projectedRemain < 0 }">{{ projectedRemain }}</b> 课时，账户余额
            <b :class="{ 'sd-danger': projectedBalance < 0 }">{{ signedMoney(projectedBalance) }}</b>
            <span v-if="projectedBalance < 0" class="sd-danger">
              （超出实收，计入欠费 {{ signedMoney(projectedBalance) }}）
            </span>
          </span>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="consumeForm.remark" placeholder="选填，将写入课时流水" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分班 -->
    <a-modal
      v-model:open="assignOpen"
      title="分 班"
      width="760px"
      ok-text="确定分班"
      cancel-text="取消"
      :confirm-loading="assignSaving"
      @ok="submitAssign"
    >
      <a-spin :spinning="assignLoading">
        <div class="sd-assign-info">
          <span>学员：<b>{{ student?.name || '—' }}</b></span>
          <span>课程：<b>{{ assignItem?.courseName || '—' }}</b></span>
          <span>当前班级：<b>{{ assignItem?.className || '未分班' }}</b></span>
        </div>
        <div class="sd-assign-target">
          <span class="sd-assign-label">目标班级</span>
          <a-select
            v-model:value="assignTargetClassId"
            :options="assignClassOptions"
            option-filter-prop="label"
            show-search
            allow-clear
            placeholder="请选择要调整到的班级"
            style="width: 360px"
            :not-found-content="assignLoading ? undefined : '该课程暂无可选班级，可先新建班级'"
          />
          <a-button @click="openNewClass">新建班级</a-button>
        </div>
        <div class="sd-assign-tip">
          勾选需要调班的学员，未勾选的学员保留在原班级；选择「新建班级」可当场为该课程创建班级并直接选中。
        </div>
        <div class="sd-assign-toolbar">
          <a-input
            v-model:value="assignKeyword"
            placeholder="搜索学员姓名 / 电话"
            allow-clear
            style="width: 240px"
          />
          <span class="sd-assign-count">已勾选 {{ assignSelected.length }} 人</span>
        </div>
        <a-table
          size="small"
          row-key="studentId"
          :columns="assignColumns"
          :data-source="assignStudents"
          :pagination="false"
          :scroll="{ y: 300 }"
          :row-selection="{ selectedRowKeys: assignSelected, onChange: onAssignSelect }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'className'">{{ record.className || '未分班' }}</span>
            <span v-else-if="column.dataIndex === 'phone'">{{ record.phone || '—' }}</span>
            <span v-else>{{ record[column.dataIndex] }}</span>
          </template>
        </a-table>
      </a-spin>
    </a-modal>

    <!-- 分班弹窗内新建班级 -->
    <a-modal
      v-model:open="newClassOpen"
      title="新建班级"
      width="520px"
      ok-text="创建并选中"
      cancel-text="取消"
      :confirm-loading="newClassSaving"
      @ok="submitNewClass"
    >
      <a-spin :spinning="newClassLoading">
        <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" size="small">
          <a-form-item label="班级名称" required>
            <a-input v-model:value="newClassForm.name" placeholder="请输入班级名称" allow-clear />
          </a-form-item>
          <a-form-item label="开班校区" required>
            <a-select
              v-model:value="newClassForm.campusId"
              :options="newClassCampusOptions"
              placeholder="请选择开班校区"
            />
          </a-form-item>
          <a-form-item label="所属课程">
            <span>{{ assignItem?.courseName || '—' }}</span>
          </a-form-item>
          <a-form-item label="班主任">
            <a-select
              v-model:value="newClassForm.teacherId"
              :options="newClassTeacherOptions"
              option-filter-prop="label"
              show-search
              allow-clear
              placeholder="请选择班主任"
            />
          </a-form-item>
          <a-form-item label="满班人数">
            <a-input-number v-model:value="newClassForm.capacity" :min="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="开班日期">
            <a-date-picker
              v-model:value="newClassForm.startDate"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              placeholder="请选择开班日期"
            />
          </a-form-item>
          <a-form-item label="上课时间">
            <a-input v-model:value="newClassForm.scheduleText" placeholder="如：每周六 09:00-10:30" allow-clear />
          </a-form-item>
          <a-form-item label="教室">
            <a-select
              v-model:value="newClassForm.room"
              :options="newClassRoomOptions"
              option-filter-prop="label"
              show-search
              allow-clear
              placeholder="请选择教室"
            />
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>

    <!-- 编辑学员 -->
    <a-modal
      v-model:open="formOpen"
      title="编辑学员"
      width="680px"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="saveForm"
    >
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }" size="small">
        <a-form-item label="学员姓名" required>
          <a-input v-model:value="form.name" placeholder="请输入学员姓名" allow-clear />
        </a-form-item>
        <a-form-item label="性别">
          <a-radio-group v-model:value="form.gender" :options="['男', '女', '未知']" />
        </a-form-item>
        <a-form-item label="生日">
          <a-date-picker
            v-model:value="form.birthday"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="请选择生日"
          />
        </a-form-item>
        <a-form-item label="家长姓名">
          <a-input v-model:value="form.guardianName" placeholder="主要联系人姓名" allow-clear />
        </a-form-item>
        <a-form-item label="所属关系">
          <a-select v-model:value="form.primaryRelation" placeholder="请选择" allow-clear>
            <a-select-option v-for="item in relationOptions" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="form.phone" placeholder="主要联系电话" allow-clear />
        </a-form-item>
        <a-form-item label="报读校区" required>
          <a-select v-model:value="form.campusId" placeholder="请选择校区">
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="学员状态">
          <a-select v-model:value="form.status">
            <a-select-option value="在读">在读</a-select-option>
            <a-select-option value="停课">停课</a-select-option>
            <a-select-option value="结课">结课</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="年级">
          <a-select v-model:value="form.grade" placeholder="请选择" allow-clear>
            <a-select-option v-for="item in ['中班', '一年级', '二年级', '三年级', '四年级', '五年级', '六年级', '初一', '初二', '初三']" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="公立学校">
          <a-input v-model:value="form.school" placeholder="公立学校名称" allow-clear />
        </a-form-item>
        <a-form-item label="居住区域">
          <a-input v-model:value="form.address" placeholder="居住区域" allow-clear />
        </a-form-item>
        <a-form-item label="微信号">
          <a-input v-model:value="form.wechat" placeholder="微信号" allow-clear />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="3" placeholder="备注信息" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 设置有效期 -->
    <a-modal
      v-model:open="expireOpen"
      title="设置有效期"
      width="460px"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="expireSaving"
      @ok="submitExpire"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" size="small">
        <a-form-item label="课程">
          <span>{{ expireForm.courseName }}</span>
        </a-form-item>
        <a-form-item label="有效期至">
          <a-date-picker
            v-model:value="expireForm.expireDate"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="选择日期，留空表示不限"
            allow-clear
          />
          <div class="sd-price-hint">留空保存会清空该报读的有效期。</div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 升期 -->
    <a-modal
      v-model:open="upgradeOpen"
      title="升期"
      width="560px"
      ok-text="确认升期"
      cancel-text="取消"
      :confirm-loading="upgradeSaving"
      :ok-button-props="{ disabled: upgradeLoading }"
      @ok="submitUpgrade"
    >
      <a-spin :spinning="upgradeLoading || upgradeQuoteLoading">
        <a-form :label-col="{ span: 7 }" :wrapper-col="{ span: 16 }" size="small">
          <a-form-item label="原课程">
            <div>{{ upgradeForm.fromCourseName }}（剩余 {{ upgradeForm.remainLessons }} 课时）</div>
            <div v-if="upgradeQuote" class="sd-price-hint">
              课时单价 <b>{{ formatMoney(upgradeQuote.fromUnitPrice) }}</b>，剩余价值
              <b>{{ formatMoney(upgradeQuote.fromValue) }}</b>
            </div>
          </a-form-item>
          <a-form-item label="升期课程" required>
            <a-select
              v-model:value="upgradeForm.toCourseId"
              :options="upgradeCourseOptions"
              option-filter-prop="label"
              placeholder="请选择升期目标课程"
              allow-clear
              @change="onUpgradeCourseChange"
            />
            <div v-if="!upgradeLoading && !upgradeCourseOptions.length" class="sd-price-hint sd-danger">
              该课程未配置升期关系，请先在「课程升期设置」中配置。
            </div>
          </a-form-item>
          <a-form-item label="目标单价">
            <span v-if="upgradeQuote">{{ formatMoney(upgradeQuote.toUnitPrice) }} / 课时</span>
            <span v-else>—</span>
          </a-form-item>
          <a-form-item label="升期后课时" required>
            <a-input-number
              v-model:value="upgradeForm.lessons"
              :min="0.01"
              :precision="1"
              :disabled="!upgradeForm.toCourseId"
              style="width: 100%"
              @change="onUpgradeLessonsChange"
            />
            <div class="sd-price-hint">
              <template v-if="upgradeQuote">
                按剩余价值 {{ formatMoney(upgradeQuote.fromValue) }} ÷ 目标单价自动换算为
                <b>{{ upgradeQuote.autoLessons }}</b> 课时，可手动调整。
              </template>
              <template v-else>选择升期课程后自动换算课时。</template>
            </div>
          </a-form-item>
          <a-form-item label="应补差价">
            <span v-if="upgradeQuote" :class="upgradeQuote.supplement > 0 ? 'sd-danger' : ''">
              {{ formatMoney(upgradeQuote.supplement) }}
            </span>
            <span v-else>—</span>
            <div v-if="upgradeSurplus > 0" class="sd-price-hint">
              本次升期结余 {{ formatMoney(upgradeSurplus) }}，将保留在该报读余额中。
            </div>
          </a-form-item>
          <a-form-item label="实收补差价">
            <a-input-number
              v-model:value="upgradeForm.extraPay"
              :min="0"
              :precision="2"
              :disabled="upgradeForm.deferPay"
              style="width: 100%"
            />
          </a-form-item>
          <a-form-item label="家长暂不交费">
            <a-switch v-model:checked="upgradeForm.deferPay" @change="onUpgradeDeferChange" />
            <span class="sd-price-hint" style="margin-left: 8px">开启后未付差价挂账，账户显示负数并标红</span>
          </a-form-item>
          <a-form-item v-if="upgradeOwed > 0" label="挂账金额">
            <b class="sd-danger">{{ signedMoney(-upgradeOwed) }}</b>
            <span class="sd-price-hint" style="margin-left: 8px">将计入学员欠费</span>
          </a-form-item>
          <a-form-item label="支付方式">
            <a-select
              v-model:value="upgradeForm.payMethod"
              :options="upgradePayOptions"
              placeholder="请选择支付方式"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="备注">
            <a-input v-model:value="upgradeForm.remark" placeholder="选填" allow-clear />
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>

    <!-- 购买教材 -->
    <a-modal
      v-model:open="materialOpen"
      title="购买教材"
      width="760px"
      ok-text="确认购买"
      cancel-text="取消"
      :confirm-loading="materialSaving"
      :ok-button-props="{ disabled: materialLoading }"
      @ok="submitMaterial"
    >
      <a-spin :spinning="materialLoading">
        <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 19 }" size="small">
          <a-form-item label="出库校区" required>
            <a-select
              v-model:value="materialForm.roomName"
              :options="materialRoomOptions"
              placeholder="请选择出库校区"
            />
          </a-form-item>
          <a-form-item label="支付方式" required>
            <a-select
              v-model:value="materialForm.payMethod"
              :options="materialPayOptions"
              placeholder="请选择支付方式"
            />
          </a-form-item>
          <a-form-item label="教材" required>
            <a-input v-model:value="materialKeyword" placeholder="搜索教材名称" allow-clear />
            <div class="sd-material-picker">
              <div
                v-for="item in materialPickOptions"
                :key="item.name"
                class="sd-material-option"
                :class="{ selected: isMaterialSelected(item.name) }"
                @click="toggleMaterial(item.name, !isMaterialSelected(item.name))"
              >
                <a-checkbox
                  :checked="isMaterialSelected(item.name)"
                  @click.stop
                  @change="onMaterialCheck(item.name, $event)"
                />
                <span class="sd-material-name" :title="item.name">{{ item.name }}</span>
                <span class="sd-material-meta">原价 {{ formatMoney(item.salePrice) }}</span>
                <span
                  class="sd-material-meta"
                  :class="{ 'sd-danger': (materialRoomStock(item.name) ?? 0) <= 0 }"
                >
                  库存 {{ materialRoomStock(item.name) ?? '—' }} 本
                </span>
              </div>
              <div v-if="!materialPickOptions.length" class="sd-material-empty">未找到匹配的教材</div>
            </div>
            <div class="sd-price-hint">可多选，已选 {{ materialForm.items.length }} 种教材</div>
          </a-form-item>
          <a-form-item label="已选教材">
            <a-table
              :data-source="materialForm.items"
              :columns="materialItemColumns"
              :pagination="false"
              :locale="{ emptyText: '尚未选择教材' }"
              size="small"
              row-key="name"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'name'">
                  <span :title="record.name">{{ record.name }}</span>
                </template>
                <template v-else-if="column.dataIndex === 'salePrice'">
                  {{ formatMoney(record.salePrice) }}
                </template>
                <template v-else-if="column.dataIndex === 'quantity'">
                  <a-input-number
                    v-model:value="record.quantity"
                    :min="1"
                    :precision="0"
                    size="small"
                    style="width: 100%"
                  />
                </template>
                <template v-else-if="column.dataIndex === 'unitPrice'">
                  <a-input-number
                    v-model:value="record.unitPrice"
                    :min="0"
                    :precision="2"
                    size="small"
                    style="width: 100%"
                  />
                </template>
                <template v-else-if="column.dataIndex === 'amount'">
                  {{ formatMoney(materialItemAmount(record)) }}
                </template>
                <template v-else-if="column.dataIndex === 'action'">
                  <a class="sd-danger" @click="removeMaterial(record.name)">移除</a>
                </template>
              </template>
            </a-table>
          </a-form-item>
          <a-form-item label="合计">
            <b>{{ formatMoney(materialTotal) }}</b>
            <span class="sd-price-hint" style="margin-left: 8px">共 {{ materialQuantityTotal }} 本</span>
            <span
              v-if="materialOriginTotal !== materialTotal"
              class="sd-price-hint"
              style="margin-left: 8px"
            >
              原价合计 {{ formatMoney(materialOriginTotal) }}
            </span>
          </a-form-item>
          <a-form-item label="账户余额">
            <a-checkbox
              v-model:checked="materialForm.useBalance"
              :disabled="materialBalance <= 0"
              @change="(e: { target: { checked: boolean } }) => toggleMaterialBalance(e.target.checked)"
            >
              使用账户剩余未用学费抵扣（可用 {{ formatMoney(materialBalance) }}）
            </a-checkbox>
            <a-input-number
              v-if="materialForm.useBalance"
              v-model:value="materialForm.balanceAmount"
              :min="0"
              :max="materialBalance"
              :precision="2"
              style="width: 100%; margin-top: 6px"
              placeholder="本次抵扣金额"
            />
            <div v-if="materialForm.useBalance" class="sd-price-hint">
              需现金支付 <b>{{ formatMoney(materialCashPay) }}</b>
            </div>
          </a-form-item>
          <a-form-item label="经办人">
            <a-input v-model:value="materialForm.handler" placeholder="选填，默认本地系统" allow-clear />
          </a-form-item>
          <a-form-item label="备注">
            <a-input v-model:value="materialForm.remark" placeholder="选填" allow-clear />
          </a-form-item>
        </a-form>
      </a-spin>
    </a-modal>

    <!-- 查看报读明细 -->
    <a-drawer v-model:open="viewOpen" title="报读明细" width="520">
      <a-descriptions v-if="viewItem" :column="1" size="small" bordered>
        <a-descriptions-item label="课程">{{ viewItem.courseName }}</a-descriptions-item>
        <a-descriptions-item label="班级">{{ viewItem.className || '未分班' }}</a-descriptions-item>
        <a-descriptions-item label="教师">{{ viewItem.teacher || '—' }}</a-descriptions-item>
        <a-descriptions-item label="报名时间">{{ dayText(viewItem.enrollTime) }}</a-descriptions-item>
        <a-descriptions-item label="开班日期">{{ viewItem.startDate || '—' }}</a-descriptions-item>
        <a-descriptions-item label="总课时">
          {{ viewItem.totalLessons }}（赠送 {{ viewItem.giftLessons }}）
        </a-descriptions-item>
        <a-descriptions-item label="已用 / 剩余">
          {{ viewItem.usedLessons }} / {{ viewItem.remainLessons }}
        </a-descriptions-item>
        <a-descriptions-item label="实收金额">{{ formatMoney(viewItem.paidAmount) }}</a-descriptions-item>
        <a-descriptions-item label="已消金额">{{ formatMoney(viewItem.consumedAmount) }}</a-descriptions-item>
        <a-descriptions-item label="课时单价">
          {{ viewItem.unitPrice === null || viewItem.unitPrice === undefined ? '—' : formatMoney(viewItem.unitPrice) }}
        </a-descriptions-item>
        <a-descriptions-item label="账户余额">
          <span :class="{ 'sd-danger': Number(viewItem.accountBalance) < 0 }">
            {{ signedMoney(viewItem.accountBalance) }}
          </span>
        </a-descriptions-item>
        <a-descriptions-item label="有效期至">{{ viewItem.expireDate || '未设置' }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ viewItem.status }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ viewItem.remark || '—' }}</a-descriptions-item>
      </a-descriptions>
  </a-drawer>

    <!-- 课时详情 -->
    <a-modal
      v-model:open="lessonDetailOpen"
      :title="`课时详情 · ${lessonDetailItem?.courseName ?? ''}`"
      width="720px"
      :footer="null"
    >
      <div v-if="lessonDetailItem" class="ld-summary">
        <div class="ld-stat">
          <span class="ld-stat-label">充值课时</span>
          <b class="ld-stat-value">{{ lessonDetailGranted }}</b>
          <span v-if="lessonDetailGift > 0" class="ld-stat-hint">含赠送 {{ lessonDetailGift }}</span>
        </div>
        <div class="ld-stat">
          <span class="ld-stat-label">已扣课时</span>
          <b class="ld-stat-value is-used">{{ lessonDetailUsed }}</b>
        </div>
        <div class="ld-stat">
          <span class="ld-stat-label">剩余课时</span>
          <b class="ld-stat-value is-remain" :class="{ 'sd-danger': lessonDetailRemain < 0 }">
            {{ lessonDetailRemain }}
          </b>
        </div>
      </div>
      <a-table
        :data-source="lessonDetailTxns"
        :loading="lessonDetailLoading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ y: 320 }"
      >
        <a-table-column title="时间" data-index="createdAt" :width="150">
          <template #default="{ record }">{{ formatDateTime(record.createdAt) }}</template>
        </a-table-column>
        <a-table-column title="类型" data-index="type" :width="110" />
        <a-table-column title="课时" data-index="lessons" :width="90" align="right">
          <template #default="{ record }">
            <span :class="Number(record.lessons) < 0 ? 'ld-minus' : 'ld-plus'">
              {{ Number(record.lessons) > 0 ? `+${record.lessons}` : record.lessons }}
            </span>
          </template>
        </a-table-column>
        <a-table-column title="结余" data-index="balanceAfter" :width="80" align="right" />
        <a-table-column title="说明" data-index="note">
          <template #default="{ record }">{{ record.note || record.bizType || '—' }}</template>
        </a-table-column>
      </a-table>
    </a-modal>

    <local-order-modal
      v-model:open="pointsOpen"
      mode="points"
      :creator="authState.user?.name || '管理员'"
      :preset-student="pointsStudent"
      @saved="onPointsSaved"
    />

    <local-order-modal
      v-model:open="localOrderOpen"
      :mode="localOrderMode"
      :creator="authState.user?.name || '管理员'"
      :preset-student="pointsStudent"
      @saved="onLocalOrderSaved"
    />
  </div>
</template>

<style scoped>
.ld-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.ld-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  background: #f7f9fb;
  border-radius: 6px;
}

.ld-stat-label {
  font-size: 13px;
  color: #6b7a89;
}

.ld-stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #1f2d3d;
}

.ld-stat-value.is-used {
  color: #d4380d;
}

.ld-stat-value.is-remain {
  color: var(--brand);
}

.ld-stat-hint {
  font-size: 12px;
  color: #8f959e;
}

.ld-plus {
  color: var(--brand);
}

.ld-minus {
  color: #d4380d;
}

.sd-page {
  min-height: 100%;
  padding-bottom: 24px;
  background: #f4f6f9;
  font-size: 14px;
  color: #30404f;
}

/* ---------- 头部信息条 ---------- */
.sd-head-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 10px 15px;
  background: #263f5f;
  color: #fff;
  font-size: 14px;
  line-height: 30px;
}
.sd-head-left {
  display: flex;
  align-items: center;
}
.sd-head-text {
  margin-right: 40px;
  color: #fff;
  font-size: 14px;
}
.sd-head-points {
  display: inline-block;
  margin-right: 10px;
  padding: 1px 10px;
  border-radius: 12px;
  background: rgba(250, 140, 22, 0.18);
  border: 1px solid rgba(255, 197, 133, 0.55);
  color: #ffd8a8;
  font-size: 13px;
}
.sd-points-action {
  margin-left: 8px;
  padding-left: 8px;
  border-left: 1px solid rgba(255, 197, 133, 0.55);
  color: #fff;
  cursor: pointer;
}
.sd-points-action:hover {
  color: #fff;
  opacity: 0.8;
}
.sd-head-buy {
  display: inline-flex;
  align-items: center;
  padding: 1px 10px;
  border-radius: 12px;
  background: rgba(47, 107, 255, 0.18);
  border: 1px solid rgba(140, 180, 255, 0.55);
  color: #cfe3ff;
  font-size: 13px;
  cursor: pointer;
}
.sd-head-buy:hover {
  color: #e6f1ff;
  border-color: rgba(190, 215, 255, 0.85);
}
.sd-head-right {
  display: flex;
  align-items: center;
}
.sd-head-link {
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}
.sd-head-link:hover {
  color: #fff;
  opacity: 0.8;
}
.sd-head-sep {
  margin: 0 10px;
  color: rgba(255, 255, 255, 0.55);
}

.sd-profile {
  position: relative;
  padding-top: 35px;
}
.sd-avatar {
  position: absolute;
  top: -35px;
  left: 50%;
  z-index: 1;
  width: 65px;
  height: 65px;
  margin-left: -32.5px;
  border-radius: 50%;
  overflow: hidden;
  background: #f2f4f7;
}
.sd-name-line {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 27px;
  margin-bottom: 11px;
  line-height: 27px;
  color: #2c3e50;
}
.sd-name {
  font-size: 18px;
  font-weight: 700;
  color: #2c3e50;
}
.sd-relation {
  font-size: 14px;
  color: #2c3e50;
}
.sd-badge {
  display: inline-block;
  height: 20px;
  padding: 0 4px;
  margin-left: 8px;
  border-radius: 10px;
  background: #129ca9;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
}
.sd-debt {
  margin-left: 8px;
  font-size: 12px;
  color: #f5222d;
}

.sd-fields {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  column-gap: 20px;
  row-gap: 12px;
  margin: 0;
  padding: 20px 24px 14px;
  background: #fff;
  list-style: none;
}
.sd-field {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-height: 20px;
  line-height: 20px;
  min-width: 0;
}
.sd-field label {
  flex: 0 0 72px;
  width: 72px;
  color: #adb4bc;
  font-size: 14px;
  text-align: right;
  white-space: nowrap;
}
.sd-field-value {
  flex: 1 1 auto;
  min-width: 0;
  color: #30404f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 标签栏 ---------- */
.sd-nav {
  display: flex;
  align-items: center;
  height: 50px;
  margin: 15px 0 0;
  padding: 0;
  background: #fff;
  list-style: none;
  overflow-x: auto;
}
.sd-nav-item {
  position: relative;
  flex: 1 1 0;
  min-width: 80px;
  height: 50px;
  font-size: 14px;
  line-height: 50px;
  text-align: center;
  color: #30404f;
  cursor: pointer;
  white-space: nowrap;
}
.sd-nav-item.current {
  color: #006ee6;
}
.sd-nav-item.current::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: #006ee6;
}

/* ---------- 内容区 ---------- */
.sd-body {
  min-height: 280px;
  padding: 20px 20px 0;
}
.sd-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
}
.sd-toolbar :deep(.ant-btn) {
  min-width: 64px;
  height: 32px;
  padding: 0 12px;
  border-radius: 4px;
  font-size: 14px;
}

/* ---------- 报读卡片 ---------- */
.sd-lesson-card {
  margin-bottom: 10px;
  border: 1px solid #e8e8e8;
  border-radius: 2px;
  background: #fff;
}
.sd-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 59px;
  padding: 0 16px;
  border-bottom: 1px solid #f0f0f0;
}
.sd-card-left {
  flex: 1 1 auto;
  display: flex;
  align-items: center;
  min-width: 0;
}
.sd-card-tags {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 5px;
  margin-right: 20px;
}
.sd-card-main {
  min-width: 0;
}
.sd-card-title {
  display: flex;
  align-items: center;
  gap: 15px;
  height: 20px;
  font-size: 14px;
}
.sd-chip {
  display: inline-block;
  height: 20px;
  padding: 0 4px;
  border: 1px solid #a8ceff;
  border-radius: 4px;
  background: #eaf5fa;
  color: #007aff;
  font-size: 12px;
  line-height: 18px;
}
.sd-chip-weak {
  border-color: #e8e8e8;
  background: #fafafa;
  color: #bfbfbf;
}
.sd-course {
  min-width: 0;
  font-size: 14px;
  font-weight: 700;
  color: #30404f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sd-campus {
  margin-top: 2px;
  font-size: 14px;
  line-height: 20px;
  color: rgba(25, 31, 37, 0.65);
}
.sd-card-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px 10px;
}
.sd-card-actions :deep(.ant-btn) {
  height: 24px;
  padding: 0 7px;
  border-radius: 4px;
  border-color: #d9d9d9;
  background: #fff;
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}
.sd-card-actions :deep(.ant-btn:hover) {
  border-color: #007aff;
  color: #007aff;
}

.sd-card-body {
  display: flex;
  align-items: flex-start;
  padding: 16px;
}
.sd-card-info {
  flex: 0 0 360px;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}
.sd-info-row {
  display: flex;
  align-items: baseline;
  height: 20px;
  margin-bottom: 5px;
  line-height: 20px;
  color: #30404f;
}
.sd-info-row label {
  flex: 0 0 56px;
  width: 56px;
  color: #adb4bc;
  font-size: 14px;
  white-space: nowrap;
}
.sd-inline-link {
  margin-left: 8px;
  font-size: 14px;
  color: #007aff;
  cursor: pointer;
}
.sd-card-table {
  flex: 1 1 0;
  min-width: 0;
  width: 100%;
  border: 1px solid #e8e8e8;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 14px;
  color: #30404f;
}
.sd-card-table th,
.sd-card-table td {
  height: 40px;
  padding: 0 10px;
  text-align: left;
  font-weight: 400;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sd-card-table th {
  background: rgba(82, 149, 231, 0.05);
  border-bottom: 1px solid #e8e8e8;
  color: #30404f;
}
.sd-card-table th + th {
  border-left: 1px solid #f0f3f5;
}
.sd-card-table .col-status {
  width: 96px;
  text-align: center;
}
.sd-card-table .col-teacher {
  width: 110px;
}
.sd-card-table .col-date {
  width: 140px;
}
.sd-card-table .col-used {
  width: 100px;
}
.sd-status {
  display: inline-block;
  height: 20px;
  padding: 0 7px;
  border-radius: 2px;
  background: #8c8c8c;
  color: #fff;
  font-size: 14px;
  line-height: 20px;
}
.sd-status.is-ok {
  background: #59c06c;
}
.sd-status.is-warn {
  background: #fa8c16;
}
.sd-status.is-muted {
  background: #8c8c8c;
}

/* ---------- 其它标签 ---------- */
.sd-summary {
  display: flex;
  gap: 28px;
  margin-bottom: 12px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
}
.sd-summary b {
  color: rgba(0, 0, 0, 0.85);
}
.sd-txn-line {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  flex-wrap: wrap;
}
.sd-txn-time {
  width: 145px;
  color: #adb4bc;
}
.sd-txn-add {
  color: #52c41a;
}
.sd-txn-sub {
  color: #007aff;
}
.sd-muted {
  color: #adb4bc;
  font-size: 12px;
}
.sd-empty-note {
  margin-top: 12px;
  font-size: 12px;
  color: #adb4bc;
}
.sd-danger {
  color: #f5222d;
}
.sd-order-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
  color: #8f959e;
}
.sd-price-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #adb4bc;
  line-height: 1.5;
}

/* ---------- 购买教材 ---------- */
.sd-material-picker {
  margin-top: 6px;
  max-height: 190px;
  overflow-y: auto;
  border: 1px solid #eef0f3;
  border-radius: 6px;
  background: #fff;
}
.sd-material-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  cursor: pointer;
  border-bottom: 1px solid #f5f6f8;
}
.sd-material-option:last-child {
  border-bottom: none;
}
.sd-material-option:hover {
  background: #f7f9fc;
}
.sd-material-option.selected {
  background: #eef5ff;
}
.sd-material-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sd-material-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: #87909a;
  white-space: nowrap;
}
.sd-material-empty {
  padding: 18px 0;
  text-align: center;
  font-size: 12px;
  color: #adb4bc;
}

/* ---------- 分班弹窗 ---------- */
.sd-assign-info {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding: 10px 12px;
  margin-bottom: 14px;
  background: #f7f9fc;
  border-radius: 4px;
  font-size: 14px;
  color: #30404f;
}
.sd-assign-target {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.sd-assign-label {
  flex: 0 0 auto;
  color: #30404f;
}
.sd-assign-tip {
  margin-bottom: 12px;
  font-size: 12px;
  color: #adb4bc;
  line-height: 1.6;
}
.sd-assign-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sd-assign-count {
  font-size: 12px;
  color: #007aff;
}

/* ---------- 退费弹窗 ---------- */
.rf-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}
.rf-tip {
  font-size: 12px;
  color: #adb4bc;
}
.rf-list {
  display: block;
  max-height: 260px;
  overflow-y: auto;
}
.rf-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 2px;
  border-bottom: 1px dashed #f2f4f7;
}
.rf-row :deep(.ant-checkbox-wrapper) {
  display: flex;
  align-items: center;
}
.rf-name {
  color: #30404f;
  font-weight: 500;
}
.rf-sub {
  margin-left: 10px;
  font-size: 12px;
  color: #98a2ad;
}
.rf-amount {
  color: #ff6b00;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.rf-row.is-disabled .rf-name,
.rf-row.is-disabled .rf-sub,
.rf-row.is-disabled .rf-amount {
  color: #c0c6cd;
  font-weight: 400;
}
.rf-total {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 12px;
  padding: 12px 2px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
  color: #30404f;
}
.rf-total b {
  color: #f5222d;
  font-size: 16px;
}
.rf-form {
  margin-top: 14px;
}

/* 资料费缴纳 */
.df-amount {
  color: #ff6b00;
  font-size: 15px;
  margin-right: 10px;
  font-variant-numeric: tabular-nums;
}
.df-hint {
  color: #98a2ad;
  font-size: 12px;
}
.df-empty {
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #fff7e6;
  color: #d46b08;
  font-size: 13px;
}

/* ---------- 成绩页签 ---------- */
.grade-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.grade-toolbar-text {
  color: #adb4bc;
  font-size: 12px;
}

.grade-chart {
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}

.grade-chart-title {
  margin-bottom: 8px;
  color: #30404f;
  font-size: 13px;
  font-weight: 600;
}

.grade-svg {
  width: 100%;
  max-width: 620px;
  height: auto;
}

.grade-radar {
  width: 260px;
  height: 260px;
}

.grade-svg-text {
  fill: #8f959e;
  font-size: 11px;
}
</style>
