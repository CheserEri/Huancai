import { clearLogin } from '../stores/auth'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export const TOKEN_KEY = 'hc_token'
export const USER_KEY = 'hc_user'

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY)
  // FormData 由浏览器自动生成带 boundary 的 Content-Type
  const isFormData = options.body instanceof FormData
  const res = await fetch(`/api${path}`, {
    ...options,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {}),
    },
  })
  if (res.status === 401) {
    // 登录过期或账号被停用：清除本地登录态并回到登录页
    clearLogin()
    if (!path.startsWith('/auth/login')) {
      window.location.hash = '#/login'
    }
    throw new Error('登录已失效，请重新登录')
  }
  if (res.status === 403) {
    // 已登录但当前账号没有该功能对应的菜单权限
    let message = '当前账号无权访问该功能，请联系管理员分配菜单权限'
    try {
      const body = (await res.json()) as ApiResult<T>
      if (body?.message) message = body.message
    } catch {
      // 响应体不是标准 JSON 时使用默认提示
    }
    throw new Error(message)
  }
  if (!res.ok) {
    let message = `请求失败（HTTP ${res.status}）`
    try {
      const body = (await res.json()) as ApiResult<T>
      if (body?.message) message = body.message
    } catch {
      // 响应体不是标准 JSON 时使用默认提示
    }
    throw new Error(message)
  }
  const body = (await res.json()) as ApiResult<T>
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export interface LoginResult {
  token: string
  staffId: number
  name: string
  phone?: string
  account?: string
  title?: string
  role: string
  campusId?: number
  campusName?: string
  /** 可见菜单路径；["*"] 表示全部菜单。 */
  permissions: string[]
}

export type ProfileResult = Omit<LoginResult, 'token'>

export function login(account: string, password: string) {
  return request<LoginResult>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password }),
  })
}

/** 刷新当前账号信息与最新菜单权限。 */
export function fetchProfile() {
  return request<ProfileResult>('/auth/me')
}

export function logout() {
  return request<null>('/auth/logout', { method: 'POST' })
}

/** 当前登录账号自行修改密码。 */
export function changePassword(oldPassword: string, newPassword: string) {
  return request<null>('/auth/password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}

export interface StudentRow {
  id: number
  name: string
  gender: string
  phone: string
  primaryRelation: string | null
  secondaryRelation: string | null
  secondaryPhone: string | null
  otherRelation: string | null
  otherPhone: string | null
  followStatus: number
  cardNo: string | null
  faceStatus: string | null
  isStar: number
  status: string
  campusId: number
  campusText: string | null
  campusName: string
  balance: number | null
  debt: number | null
  debtLessons: number | null
  courseCount: number
  points: number
  channelName: string | null
  ageText: string | null
  grade: string | null
  wechat: string | null
  birthday: string | null
  school: string | null
  publicSchoolClass: string | null
  address: string | null
  extraInfo: string | null
  source: string | null
  enrollDate: string | null
}

export interface StudentSummary {
  count: number
  balance: number
  debt: number
  points: number
}

export interface StudentQuery {
  page?: number
  size?: number
  keyword?: string
  campusId?: number
  status?: string
  isStar?: number
  followStatus?: number
  grade?: string
  gender?: string
  cardBound?: number
  faceEntered?: number
  hasDebt?: number
  studentType?: string
  channel?: string
  sort?: string
}

function toQuery(params: Record<string, unknown>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '' && value !== 'all') {
      search.set(key, String(value))
    }
  })
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

export function fetchStudents(query: StudentQuery) {
  return request<PageResult<StudentRow>>(`/students${toQuery({ ...query })}`)
}

export function fetchStudentSummary(query: Omit<StudentQuery, 'page' | 'size'>) {
  return request<StudentSummary>(`/students/summary${toQuery({ ...query })}`)
}

export interface StudentDetail {
  student: {
    id: number
    name: string
    gender: string
    birthday: string | null
    ageText: string | null
    phone: string
    primaryRelation: string | null
    secondaryRelation: string | null
    secondaryPhone: string | null
    otherRelation: string | null
    otherPhone: string | null
    followStatus: number
    cardNo: string | null
    faceStatus: string | null
    isStar: number
    balance: number | null
    debt: number | null
    points: number
    courseCount: number
    status: string
    source: string | null
    channelName: string | null
    school: string | null
    publicSchoolClass: string | null
    grade: string | null
    wechat: string | null
    address: string | null
    extraInfo: string | null
    campusText: string | null
    enrollDate: string | null
    remark: string | null
    campusId: number
  }
  guardians: Array<{
    id: number
    name: string
    relation: string | null
    phone: string
    isPrimary: number | null
  }>
  enrollments: Array<{
    id: number
    courseId: number | null
    classId: number | null
    courseName: string
    className: string | null
    teacher: string | null
    startDate: string | null
    enrollTime: string | null
    totalLessons: number
    giftLessons: number
    remainLessons: number
    usedLessons: number
    paidAmount: number
    consumedAmount: number
    unitPrice: number | null
    accountBalance: number
    expireDate: string | null
    status: string
    remark: string | null
    /** 上次资料费缴纳时间 */
    lastDataFeePaidAt: string | null
  }>
  txns: Array<{
    id: number
    type: string
    lessons: number
    balanceAfter: number
    bizType: string | null
    note: string | null
    createdAt: string
  }>
}

export function fetchStudentDetail(id: number) {
  return request<StudentDetail>(`/students/${id}`)
}

export interface StudentLessonTxn {
  id: number
  enrollmentId: number | null
  type: string
  lessons: number
  unitPrice: number | null
  amount: number | null
  balanceAfter: number | null
  bizType: string | null
  note: string | null
  createdAt: string
}

/** 查询某个报读的课时流水（不传 enrollmentId 则为该学员全部流水）。 */
export function fetchStudentLessonTxns(studentId: number, enrollmentId?: number) {
  const query = enrollmentId ? `?enrollmentId=${enrollmentId}` : ''
  return request<StudentLessonTxn[]>(`/students/${studentId}/lesson-txns${query}`)
}

export interface StudentFormPayload {
  name?: string
  gender?: string
  birthday?: string
  phone?: string
  primaryRelation?: string
  secondaryRelation?: string
  secondaryPhone?: string
  otherRelation?: string
  otherPhone?: string
  campusId?: number
  status?: string
  grade?: string
  school?: string
  publicSchoolClass?: string
  address?: string
  wechat?: string
  source?: string
  channelName?: string
  extraInfo?: string
  remark?: string
  enrollDate?: string
  guardianName?: string
}

export function createStudent(payload: StudentFormPayload) {
  return request<{ id: number; name: string }>('/students', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateStudent(id: number, payload: StudentFormPayload) {
  return request<void>(`/students/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteStudent(id: number) {
  return request<void>(`/students/${id}`, { method: 'DELETE' })
}

export function updateEnrollmentStatus(studentId: number, enrollmentId: number, status: string) {
  return request<unknown>(`/students/${studentId}/enrollments/${enrollmentId}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  })
}

export function updateEnrollmentExpire(studentId: number, enrollmentId: number, expireDate: string | null) {
  return request<unknown>(`/students/${studentId}/enrollments/${enrollmentId}/expire`, {
    method: 'PUT',
    body: JSON.stringify({ expireDate }),
  })
}

export interface DashboardSummary {
  students: number
  activeStudents: number
  studyingStudents: number
  suspendedStudents: number
  pausedStudents: number
  arrearsStudents: number
  remainLessons: number
  debt: number
  revenue: number
  classes: number
  todayClasses: number
  todayOrders: number
  enrollTrend: Array<{ date: string; count: number }>
  topCourses: Array<{ courseName: string; students: number; remainLessons: number }>
}

export function fetchDashboard() {
  return request<DashboardSummary>('/dashboard/summary')
}

export interface ScheduleRow {
  id: number
  campusId: number
  campusName: string
  classId: number | null
  className: string | null
  studentId?: number | null
  courseId: number | null
  courseName: string | null
  teacherId?: number | null
  teacherName: string | null
  assistantName: string | null
  lessonDate: string
  startTime: string
  endTime: string
  room: string | null
  topic: string | null
  status: string
  lessonMode: number | null
  teachingLogId: number | null
  shouldCount: number | null
  actualCount: number | null
}

export interface ScheduleSummary {
  total: number
  done: number
  pending: number
  shouldStudents: number
  actualStudents: number
  classes: number
}

export interface ScheduleQuery {
  start?: string
  end?: string
  campusId?: number
  teacher?: string
  classId?: number
  courseId?: number
  room?: string
  keyword?: string
  status?: string
  lessonMode?: number
  page?: number
  size?: number
}

export interface ScheduleOption {
  id: number
  name: string
}

export function fetchSchedules(query: ScheduleQuery) {
  return request<PageResult<ScheduleRow>>(`/schedules${toQuery({ size: 1000, ...query })}`)
}

export function fetchScheduleSummary(query: Omit<ScheduleQuery, 'page' | 'size'>) {
  return request<ScheduleSummary>(`/schedules/summary${toQuery({ ...query })}`)
}

export function fetchScheduleTeachers() {
  return request<string[]>('/schedules/teachers')
}

export function fetchScheduleClasses() {
  return request<ScheduleOption[]>('/schedules/classes')
}

export function fetchScheduleCourses() {
  return request<ScheduleOption[]>('/schedules/courses')
}

export function fetchScheduleRooms() {
  return request<string[]>('/schedules/rooms')
}

export interface ScheduleOptions {
  campuses: Array<{ id: number; name: string }>
  classes: Array<{
    id: number
    name: string
    campusId: number | null
    courseId: number | null
    teacherId: number | null
    teacherText: string | null
    room: string | null
  }>
  courses: Array<{ id: number; name: string }>
  teachers: Array<{ id: number; name: string }>
  rooms: string[]
}

export function fetchScheduleOptions() {
  return request<ScheduleOptions>('/schedules/options')
}

export interface ScheduleFormPayload {
  campusId?: number
  classId?: number | null
  courseId?: number | null
  teacherId?: number | null
  studentId?: number | null
  lessonDate?: string
  startTime?: string
  endTime?: string
  room?: string
  topic?: string
  lessonMode?: number
}

export function createSchedule(payload: ScheduleFormPayload) {
  return request<{ id: number; lessonDate: string; startTime: string }>('/schedules', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateSchedule(id: number, payload: ScheduleFormPayload) {
  return request<void>(`/schedules/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function cancelSchedule(id: number) {
  return request<void>(`/schedules/${id}/cancel`, { method: 'POST' })
}

export function restoreSchedule(id: number) {
  return request<void>(`/schedules/${id}/restore`, { method: 'POST' })
}

export interface SpOrderRow {
  id: number
  spId: number | null
  orderNo: string
  campusId: number | null
  schoolId: number | null
  campusName: string | null
  studentId: number | null
  stuName: string
  stuTel: string | null
  typeCode: number
  typeName: string
  content: string | null
  shouldPay: number
  realPay: number
  remain: number
  arrearage: number
  credit: number
  sourceType: number | null
  sourceName: string
  salesman: string | null
  creator: string | null
  salesSource: string | null
  tags: string | null
  isReturn: boolean
  statusCode: number
  statusName: string
  contractStatus: number
  payMethod: string | null
  comment: string | null
  commentOuter: string | null
  hasConfirmedDetail: boolean
  hasTextbook: boolean
  isOnlinePayment: boolean
  hasMultiLessonEnroll: boolean
  dealDate: string
  createdTime: string
  expiryDate: string | null
  /** 作废原因与作废时间，本地作废时写入 */
  voidReason: string | null
  voidAt: string | null
  rawJson?: string | null
}

export interface SpOrderSummary {
  count: number
  shouldPay: number
  realPay: number
  remain: number
  arrearage: number
  credit: number
}

export interface SpOrderPage {
  total: number
  list: SpOrderRow[]
  summary: SpOrderSummary
}

export interface SpOrderQuery {
  pageIndex?: number
  pageSize?: number
  /** 按学员过滤（学员详情页订单列表使用） */
  studentId?: number | null
  dealStart?: string
  dealEnd?: string
  statuses?: number[]
  types?: number[]
  sourceType?: number | null
  contractStatus?: number | null
  campusIds?: number[]
  creators?: string[]
  salesmen?: string[]
  tags?: string[]
  courses?: string[]
  items?: string[]
  isArrearage?: string | null
  followUpType?: string | null
  keyword?: string
  keywordType?: string
  sortField?: string
  sortOrder?: string
}

export interface SpOrderOptions {
  campuses: Array<{ id: number; name: string }>
  creators: string[]
  salesmen: string[]
  courses: string[]
  items: string[]
  tags: string[]
  followUpTypes: string[]
}

export function fetchSpOrderPage(query: SpOrderQuery) {
  return request<SpOrderPage>('/sp-orders/query', {
    method: 'POST',
    body: JSON.stringify(query),
  })
}

export function fetchSpOrderDetail(id: number) {
  return request<SpOrderRow>(`/sp-orders/${id}`)
}

/** 作废订单：状态改为已作废并记录原因，作废后不可还原 */
export function voidSpOrder(id: number, reason: string) {
  return request<SpOrderRow>(`/sp-orders/${id}/void`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function fetchSpOrderOptions() {
  return request<SpOrderOptions>('/sp-orders/options')
}

/** 小票打印：后端直发 ESC/POS 到 USB 小票机 */
export interface PrintResult {
  device: string
  bytes: number
}

export function printOrderReceipt(orderId: number) {
  return request<PrintResult>(`/print/receipt/${orderId}`, { method: 'POST' })
}

/** 小票打印：听课证直发 ESC/POS */
export function printEnrollmentTicket(studentId: number, enrollmentId: number) {
  return request<PrintResult>(`/print/ticket/${studentId}/${enrollmentId}`, { method: 'POST' })
}

/** 日结收费交接单：单日 xlsx（26-11-1/7/8 格式）。 */
export async function downloadDailySettlement(date: string): Promise<Blob> {
  const token = localStorage.getItem(TOKEN_KEY)
  const res = await fetch(`/api/sp-orders/daily-settlement?date=${encodeURIComponent(date)}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (res.status === 401) {
    clearLogin()
    window.location.hash = '#/login'
    throw new Error('登录已失效，请重新登录')
  }
  if (!res.ok) {
    throw new Error(res.status === 403 ? '当前账号无权导出日结表' : `导出失败（HTTP ${res.status}）`)
  }
  return res.blob()
}

/** ---------------- 教师课时（办理中心） ---------------- */

export interface TeacherHourOption {
  id: number
  name: string
}

export interface TeacherHourRecord {
  scheduleId: number
  lessonDate: string
  startTime: string | null
  endTime: string | null
  teacherId: number
  teacherName: string
  campusId: number | null
  campusName: string | null
  classId: number | null
  className: string | null
  courseName: string | null
  grade: string | null
  shouldCount: number
  studentCount: number
  unitPrice: number | null
  thresholdStudents: number | null
  fixedAmount: number | null
  rateConfigured: boolean
  adjustAmount: number | null
  adjustRemark: string | null
  amount: number
}

export interface TeacherHourSummary {
  teacherId: number
  teacherName: string
  lessonCount: number
  studentCount: number
  amount: number
  detail: string
  remarks: string[]
}

export interface TeacherHourMonth {
  records: TeacherHourRecord[]
  teachers: TeacherHourSummary[]
  teacherOptions: TeacherHourOption[]
  campusOptions: TeacherHourOption[]
  lessonCount: number
  studentCount: number
  amount: number
  missingRateCount: number
}

export interface TeacherHourRate {
  id?: number
  teacherId: number
  teacherName?: string | null
  /** 适用年级起始（1=一年级） */
  gradeStart: number
  /** 适用年级结束（含） */
  gradeEnd: number
  unitPrice: number
  thresholdStudents: number
  fixedAmount: number
  remark?: string | null
}

function teacherHourQuery(month: string, teacherId?: number | null, campusId?: number | null) {
  const params = new URLSearchParams({ month })
  if (teacherId) params.set('teacherId', String(teacherId))
  if (campusId) params.set('campusId', String(campusId))
  return params.toString()
}

export function fetchTeacherHourMonth(month: string, teacherId?: number | null, campusId?: number | null) {
  return request<TeacherHourMonth>(`/teacher-hours/month?${teacherHourQuery(month, teacherId, campusId)}`)
}

export function fetchTeacherHourRates() {
  return request<TeacherHourRate[]>('/teacher-hours/rates')
}

export function saveTeacherHourRates(rates: TeacherHourRate[]) {
  return request<void>('/teacher-hours/rates', { method: 'PUT', body: JSON.stringify(rates) })
}

export function updateTeacherHourAmount(scheduleId: number, amount: number | null, remark: string | null) {
  return request<void>(`/teacher-hours/records/${scheduleId}/amount`, {
    method: 'PUT',
    body: JSON.stringify({ amount, remark }),
  })
}

/** 月度教师课时费 xlsx（汇总 + 每位教师一页）。 */
export async function downloadTeacherHourReport(
  month: string,
  teacherId?: number | null,
  campusId?: number | null,
): Promise<Blob> {
  const token = localStorage.getItem(TOKEN_KEY)
  const res = await fetch(`/api/teacher-hours/export?${teacherHourQuery(month, teacherId, campusId)}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (res.status === 401) {
    clearLogin()
    window.location.hash = '#/login'
    throw new Error('登录已失效，请重新登录')
  }
  if (!res.ok) {
    throw new Error(res.status === 403 ? '当前账号无权导出教师课时费' : `导出失败（HTTP ${res.status}）`)
  }
  return res.blob()
}

/** ---------------- 本地闭环办理（不写原系统） ---------------- */

export type LocalOrderMode =
  | 'enroll'
  | 'recharge'
  | 'subsidy'
  | 'refund'
  | 'transfer'
  | 'material'
  | 'points'

export interface LocalOrderCourse {
  id: number
  name: string
  campusId: number | null
  listPrice: number | null
  defaultPriceTemplateId: number | null
}

export interface LocalOrderEnrollment {
  id: number
  courseId: number | null
  courseName: string | null
  campusId: number | null
  classId: number | null
  totalLessons: number
  remainLessons: number
  paidAmount: number
  unitPrice: number | null
  priceTemplateId: number | null
  priceTemplateName: string | null
  arrears: number
  accountBalance: number
  expireDate: string | null
  status: string
}

export interface LocalOrderClass {
  id: number
  name: string
  courseId: number | null
  campusId: number | null
}

/** 活动模板页维护的优惠模板（活动效果统计按此聚合）。 */
export interface LocalOrderActivityTemplate {
  id: number
  name: string
  type: '节日活动' | '报课优惠'
  startDate: string | null
  endDate: string | null
  rules: Array<{
    lessons: number
    discountMode: '折扣' | '立减' | '赠送课时'
    discountValue: number
  }>
}

/** 报读可用的基础价格模板（含课时档位）。 */
export interface LocalOrderPriceTemplate {
  id: number
  name: string
  campusScope: string
  campusIds: number[] | string | null
  classType: string | null
  materialFee: number
  validDays: number | null
  courseIds: number[]
  prices: PriceTemplateTier[]
}

export interface LocalOrderOptions {
  campuses: Array<{ id: number; name: string }>
  courses: LocalOrderCourse[]
  classes: LocalOrderClass[]
  payMethods: string[]
  enrollments: LocalOrderEnrollment[]
  activityTemplates: LocalOrderActivityTemplate[]
  priceTemplates: LocalOrderPriceTemplate[]
}

export interface LocalOrderResult {
  orderId: number
  orderNo: string
  typeName: string
  summary: string
  balance: number | null
  remainLessons: number | null
}

export interface LocalEnrollMaterial {
  materialId: number | null
  name: string
  price: number
  quantity: number
}

export interface LocalEnrollPayload {
  studentId: number
  campusId?: number
  courseId: number
  classId?: number | null
  lessons: number
  giftLessons?: number
  unitPrice?: number
  discount?: number
  shouldPay?: number
  received?: number
  materials?: LocalEnrollMaterial[]
  expireDate?: string | null
  payMethod?: string
  creator?: string
  remark?: string
  tags?: string
  priceTemplateId?: number
  localActivityTemplateIds?: number[]
}

export interface LocalRechargePayload {
  studentId: number
  campusId?: number
  amount: number
  payMethod?: string
  creator?: string
  remark?: string
  tags?: string
}

export interface LocalSubsidyPayload {
  studentId: number
  campusId?: number
  enrollmentId: number
  amount: number
  payMethod?: string
  creator?: string
  remark?: string
  tags?: string
}

export interface LocalRefundPayload {
  studentId: number
  campusId?: number
  enrollmentId?: number
  amount: number
  lessons?: number
  payMethod?: string
  creator?: string
  reason?: string
}

export interface LocalBatchRefundPayload {
  studentId: number
  campusId?: number
  /** 要整单退掉的报读（可多选）。 */
  enrollmentIds?: number[]
  /** 同时退掉的账户剩余未用学费金额，0 或缺省表示不退余额。 */
  balanceAmount?: number
  payMethod?: string
  creator?: string
  reason?: string
}

export interface LocalTransferPayload {
  studentId: number
  campusId?: number
  fromEnrollmentId: number
  toCourseId: number
  toClassId?: number | null
  /** 全部 / 部分 */
  transferType?: string
  /** 本次转出课时；全部转出时后端取剩余课时。 */
  lessons?: number
  inLessons?: number
  inUnitPrice?: number
  discount?: number
  /** 差额应缴时的实收金额。 */
  extraPay?: number
  /** 差额应退时的实退现金。 */
  refundCash?: number
  /** 差额应退时转入账户余额的金额。 */
  balanceAdd?: number
  expireDate?: string | null
  payMethod?: string
  creator?: string
  remark?: string
  outerRemark?: string
  tags?: string
}

export function fetchLocalOrderOptions(studentId?: number) {
  return request<LocalOrderOptions>(`/local-orders/options${toQuery({ studentId })}`)
}

export function enrollLocalOrder(payload: LocalEnrollPayload) {
  return request<LocalOrderResult>('/local-orders/enroll', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function rechargeLocalOrder(payload: LocalRechargePayload) {
  return request<LocalOrderResult>('/local-orders/recharge', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function subsidyLocalOrder(payload: LocalSubsidyPayload) {
  return request<LocalOrderResult>('/local-orders/subsidy', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function refundLocalOrder(payload: LocalRefundPayload) {
  return request<LocalOrderResult>('/local-orders/refund', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** 批量退费：可只退某门课程、账户余额，或多选一次性全退。 */
export function refundStudentBatch(payload: LocalBatchRefundPayload) {
  return request<LocalOrderResult>('/local-orders/batch-refund', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function transferLocalOrder(payload: LocalTransferPayload) {
  return request<LocalOrderResult>('/local-orders/transfer', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function refundStudentBalance(payload: {
  studentId: number
  amount: number
  payMethod?: string
  reason?: string
}) {
  return request<LocalOrderResult>('/local-orders/balance-refund', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface UpgradeQuote {
  fromRemainLessons: number
  fromUnitPrice: number
  fromValue: number
  toUnitPrice: number
  autoLessons: number
  targetLessons: number
  targetValue: number
  supplement: number
  surplus: number
}

export function fetchUpgradeQuote(params: {
  studentId: number
  fromEnrollmentId: number
  toCourseId: number
  toLessons?: number
  toUnitPrice?: number
}) {
  return request<UpgradeQuote>(`/local-orders/upgrade-quote${toQuery({ ...params })}`)
}

export interface LocalUpgradePayload {
  studentId: number
  campusId?: number
  fromEnrollmentId: number
  toCourseId: number
  toLessons: number
  toUnitPrice: number
  extraPay?: number
  payMethod?: string
  creator?: string
  remark?: string
}

export function upgradeLocalOrder(payload: LocalUpgradePayload) {
  return request<LocalOrderResult>('/local-orders/upgrade', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface LessonPriceInfo {
  unitPrice: number | null
  originalUnitPrice?: number | null
  source: string
}

export function fetchLessonPrice(studentId: number, enrollmentId: number) {
  return request<LessonPriceInfo>(`/local-orders/lesson-price${toQuery({ studentId, enrollmentId })}`)
}

export interface ConsumeLessonsPayload {
  studentId: number
  enrollmentId: number
  lessons: number
  unitPrice?: number
  remark?: string
}

export interface ConsumeLessonsResult {
  enrollmentId: number
  remainLessons: number
  unitPrice: number
  amount: number
  accountBalance: number
  debt: number
}

export function consumeLessons(payload: ConsumeLessonsPayload) {
  return request<ConsumeLessonsResult>('/local-orders/consume-lessons', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface CommentRow {
  id: number
  campusId: number
  campusName: string
  classId: number | null
  className: string | null
  courseId: number | null
  courseName: string | null
  studentId: number
  studentName: string
  teacherName: string | null
  scheduleId: number | null
  lessonDate: string | null
  startTime: string | null
  content: string | null
  rating: string | null
  readAt: string | null
  createdAt: string | null
}

export interface CommentSummary {
  total: number
  students: number
  classes: number
  unread: number
  readCount: number
}

export interface CommentLessonRow {
  scheduleId: number
  campusId: number
  campusName: string
  classId: number | null
  className: string | null
  courseId: number | null
  courseName: string | null
  teacherName: string | null
  lessonDate: string | null
  startTime: string | null
  endTime: string | null
  shouldCount: number | null
  actualCount: number | null
  commentCount: number
  readCount: number
}

export interface CommentLessonSummary {
  lessons: number
  commentedLessons: number
  comments: number
  readComments: number
  shouldStudents: number
  actualStudents: number
}

export interface CommentTeacherStat {
  teacherName: string
  comments: number
  students: number
  lessons: number
  readComments: number
}

export interface CommentQuery {
  keyword?: string
  campusId?: number
  classId?: number
  courseId?: number
  teacher?: string
  rating?: string
  readStatus?: string
  commented?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export function fetchComments(query: CommentQuery) {
  return request<PageResult<CommentRow>>(`/comments${toQuery({ ...query })}`)
}

export function fetchCommentSummary(query: Omit<CommentQuery, 'page' | 'size'>) {
  return request<CommentSummary>(`/comments/summary${toQuery({ ...query })}`)
}

export function fetchCommentLessons(query: CommentQuery) {
  return request<PageResult<CommentLessonRow>>(`/comments/lessons${toQuery({ ...query })}`)
}

export function fetchCommentLessonSummary(query: Omit<CommentQuery, 'page' | 'size'>) {
  return request<CommentLessonSummary>(`/comments/lessons/summary${toQuery({ ...query })}`)
}

export function fetchCommentTeacherStats(query: Omit<CommentQuery, 'page' | 'size'>) {
  return request<CommentTeacherStat[]>(`/comments/teacher-stats${toQuery({ ...query })}`)
}

export function fetchCommentTeachers() {
  return request<string[]>('/comments/teachers')
}

export interface ClassRow {
  id: number
  campusId: number
  campusName: string
  courseId: number | null
  courseName: string | null
  courseCategory: string | null
  teacherId: number | null
  teacherName: string | null
  assistName: string | null
  name: string
  lessonClassName: string | null
  room: string | null
  scheduleText: string | null
  startDate: string | null
  endDate: string | null
  status: string
  capacity: number
  enrolledCount: number
  stuRate: number | null
  upRate: string | null
  scheduleCount: number
  enrolling: number
  createBy: string | null
  createdAt: string | null
  feeStandard: string | null
  source: string
  spClassId: number | null
  studentCount: number
  lessonCount: number
  lastLessonDate: string | null
  attendanceRate: number | null
}

export interface ClassSummary {
  total: number
  active: number
  studentCount: number
  memberCount: number
}

export interface ClassStudentRow {
  id: number
  studentId: number
  studentName: string
  phone: string | null
  relation: string | null
  followStatus: number
  gender: string | null
  status: string
  joinDate: string | null
  totalLessons: number
  usedLessons: number
  remainLessons: number
  studentNo: string | null
  debt: number
  remark: string | null
}

/** 升班：原班级学员分批升入多个新班级 */
export interface ClassUpgradePayload {
  sourceClassId: number
  targets: Array<{ classId: number; studentIds: number[] }>
}

export interface ClassUpgradeResult {
  moved: number
  kept: number
  removed: number
  sourceEnrolled: number
  targets: Array<{ classId: number; name: string; count: number }>
}

export interface ClassQuery {
  keyword?: string
  campusId?: number
  teacher?: string
  status?: string
  source?: string
  enrolling?: number
  lessonClass?: string
  room?: string
  courseCategory?: string
  courseName?: string
  weekday?: string
  timePeriod?: string
  includeClosed?: boolean
  sortField?: string
  sortOrder?: string
  page?: number
  size?: number
}

export function fetchClasses(query: ClassQuery) {
  return request<PageResult<ClassRow>>(`/classes${toQuery({ ...query })}`)
}

export function fetchClassSummary(query: Omit<ClassQuery, 'page' | 'size'>) {
  return request<ClassSummary>(`/classes/summary${toQuery({ ...query })}`)
}

export function fetchClassTeachers() {
  return request<string[]>('/classes/teachers')
}

export function fetchLessonClasses() {
  return request<string[]>('/classes/lesson-classes')
}

export function fetchClassCourses() {
  return request<string[]>('/classes/courses')
}

export function fetchClassCategories() {
  return request<string[]>('/classes/categories')
}

export function fetchClassRooms() {
  return request<string[]>('/classes/rooms')
}

export function fetchClassStudents(classId: number) {
  return request<ClassStudentRow[]>(`/classes/${classId}/students`)
}

export function fetchClassDetail(classId: number) {
  return request<ClassRow>(`/classes/${classId}`)
}

export interface ClassPhoto {
  id: number
  classId: number
  url: string
  uploader: string | null
  createdAt: string
}

export function fetchClassPhotos(classId: number) {
  return request<ClassPhoto[]>(`/classes/${classId}/photos`)
}

export function uploadClassPhoto(classId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<ClassPhoto>(`/class-album/${classId}/photos`, { method: 'POST', body: form })
}

export function deleteClassPhoto(photoId: number) {
  return request<void>(`/class-album/photos/${photoId}`, { method: 'DELETE' })
}

export interface ClassScheduleRow {
  id: number
  lessonDate: string
  startTime: string
  endTime: string
  courseName: string | null
  room: string | null
  topic: string | null
  status: string
  teacherName: string | null
  assistantName: string | null
  shouldCount: number | null
  actualCount: number | null
  attendanceCount: number
  presentCount: number
  leaveCount: number
  absentCount: number
  lateCount: number
  lessonCount: number
  attendanceRate: number | null
  commentCount: number
  createdAt: string | null
}

export interface ClassAttendanceRow {
  id: number
  scheduleId: number | null
  studentId: number
  studentName: string
  lessonDate: string
  topic: string | null
  teacherName: string | null
  status: string
  lessons: number
  remark: string | null
}

export interface ClassCommentRow {
  id: number
  studentId: number
  studentName: string
  teacherName: string | null
  lessonDate: string | null
  content: string | null
  rating: string | null
  readAt: string | null
  createdAt: string | null
}

export function fetchClassSchedules(classId: number, doneOnly = false) {
  return request<ClassScheduleRow[]>(`/classes/${classId}/schedules${doneOnly ? '?doneOnly=true' : ''}`)
}

export function fetchClassAttendance(classId: number, scheduleId?: number) {
  return request<ClassAttendanceRow[]>(
    `/classes/${classId}/attendance${scheduleId ? `?scheduleId=${scheduleId}` : ''}`,
  )
}

export function fetchClassComments(classId: number) {
  return request<ClassCommentRow[]>(`/classes/${classId}/comments`)
}

export interface HomeworkRow {
  id: number
  classId: number
  className: string | null
  teacherId: number | null
  teacherName: string | null
  title: string
  content: string
  dueDate: string | null
  publishDate: string
  publisher: string | null
  status: string
  createdAt: string | null
  updatedAt: string | null
}

export interface HomeworkQuery {
  classId?: number
  keyword?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export interface HomeworkPayload {
  classId: number
  title: string
  content: string
  dueDate?: string
  publishDate?: string
}

export function fetchHomeworks(query: HomeworkQuery) {
  return request<PageResult<HomeworkRow>>(`/homeworks${toQuery({ ...query })}`)
}

export function createHomework(payload: HomeworkPayload) {
  return request<HomeworkRow>('/homeworks', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateHomework(id: number, payload: HomeworkPayload) {
  return request<void>(`/homeworks/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function deleteHomework(id: number) {
  return request<void>(`/homeworks/${id}`, { method: 'DELETE' })
}

export interface CommentWriteRow {
  id: number
  classId: number
  studentId: number
  studentName: string | null
  scheduleId: number
  lessonDate: string | null
  startTime: string | null
  endTime: string | null
  teacherId: number | null
  teacherName: string | null
  content: string | null
  rating: string | null
  readAt: string | null
  createdAt: string | null
}

export interface CommentRosterRow {
  studentId: number
  studentName: string
  commentId: number | null
  rating: string | null
  content: string | null
}

export interface CommentBatchItem {
  studentId: number
  rating?: string
  content?: string
}

export function fetchClassCommentRows(query: { classId: number; start?: string; end?: string }) {
  return request<CommentWriteRow[]>(`/class-comments${toQuery({ ...query })}`)
}

export function fetchCommentRoster(classId: number, scheduleId: number) {
  return request<CommentRosterRow[]>(`/class-comments/roster${toQuery({ classId, scheduleId })}`)
}

export function saveClassComments(payload: {
  classId: number
  scheduleId: number
  items: CommentBatchItem[]
}) {
  return request<number>('/class-comments/batch', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface ClassOption {
  id: number
  name: string
}

export interface ClassOptions {
  campuses: ClassOption[]
  courses: ClassOption[]
  teachers: ClassOption[]
  rooms: string[]
  lessonClasses: string[]
}

export function fetchClassOptions() {
  return request<ClassOptions>('/classes/options')
}

export interface ClassFormPayload {
  name?: string
  campusId?: number
  courseId?: number | null
  teacherId?: number | null
  assistName?: string
  lessonClassName?: string
  room?: string
  scheduleText?: string
  capacity?: number
  startDate?: string
  endDate?: string
  status?: string
  enrolling?: number
  feeStandard?: string
}

export function createClass(payload: ClassFormPayload) {
  return request<{ id: number; name: string }>('/classes', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateClass(id: number, payload: ClassFormPayload) {
  return request<void>(`/classes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

/** 班级结班：所有学员结课后才允许结班。 */
export function closeClass(id: number) {
  return request<void>(`/classes/${id}/close`, { method: 'POST' })
}

export function batchUpdateClassStatus(ids: number[], enrolling?: number, status?: string) {
  return request<number>('/classes/batch-status', {
    method: 'POST',
    body: JSON.stringify({ ids, enrolling, status }),
  })
}

export function batchDeleteClasses(ids: number[]) {
  return request<number>('/classes/batch-delete', {
    method: 'POST',
    body: JSON.stringify({ ids }),
  })
}

export function addClassStudents(classId: number, studentIds: number[]) {
  return request<{ added: number; enrolled: number }>(`/classes/${classId}/students`, {
    method: 'POST',
    body: JSON.stringify({ studentIds }),
  })
}

export function removeClassStudent(classId: number, studentId: number) {
  return request<number>(`/classes/${classId}/students/${studentId}`, {
    method: 'DELETE',
  })
}

export function upgradeClasses(payload: ClassUpgradePayload) {
  return request<ClassUpgradeResult>('/classes/upgrade', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface ClassAssignStudent {
  studentId: number
  studentName: string
  phone: string | null
  classId: number | null
  className: string | null
  totalLessons: number
  remainLessons: number
  enrollStatus: string
}

export interface ClassAssignOptions {
  classes: ClassRow[]
  students: ClassAssignStudent[]
}

export function fetchClassAssignOptions(courseId: number) {
  return request<ClassAssignOptions>(`/classes/assign-options${toQuery({ courseId })}`)
}

export interface ClassAssignPayload {
  courseId: number
  targetClassId: number
  studentIds: number[]
}

export function assignClassStudents(payload: ClassAssignPayload) {
  return request<{ moved: number; className: string; enrolled: number }>('/classes/assign', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface AttendanceRow {
  id: number
  campusId: number
  campusName: string
  lessonDate: string
  studentName: string
  className: string | null
  courseName: string | null
  teacherName: string | null
  status: string
  lessons: number
  checkedAt: string | null
  remark: string | null
}

export interface AttendanceSummary {
  total: number
  present: number
  leaveCount: number
  absent: number
  lessons: number
}

export interface AttendanceQuery {
  keyword?: string
  campusId?: number
  teacher?: string
  status?: string
  start?: string
  end?: string
}

export function fetchAttendance(query: AttendanceQuery & { page?: number; size?: number }) {
  return request<PageResult<AttendanceRow>>(`/attendance${toQuery({ ...query })}`)
}

export function fetchAttendanceSummary(query: AttendanceQuery) {
  return request<AttendanceSummary>(`/attendance/summary${toQuery({ ...query })}`)
}

export function fetchAttendanceTeachers() {
  return request<string[]>('/attendance/teachers')
}

export interface TeachingLogRow {
  id: number
  classId: number | null
  lessonDate: string
  startTime: string
  endTime: string
  className: string | null
  courseName: string | null
  teacherName: string | null
  room: string | null
  lessonMode: number | null
  status: string
  campusId: number
  campusName: string | null
  shouldCount: number | null
  actualCount: number | null
  recordCount: number
}

export interface TeachingLogQuery {
  date?: string
  supplement?: boolean
  campusId?: number
  room?: string
  lessonMode?: number
  courseId?: number
  teacher?: string
  keyword?: string
  status?: string
}

export interface TeachingLogStudent {
  studentId: number
  studentName: string
  studentStatus: string
  enrollmentId: number | null
  courseName: string | null
  remainLessons: number | null
  unitPrice: number | null
  recordStatus: string | null
  recordLessons: number | null
  recordRemark: string | null
}

export interface TeachingLogRecordPayload {
  studentId: number
  enrollmentId: number | null
  status: string
  lessons: number | null
  remark?: string | null
}

export function fetchTeachingLogs(query: TeachingLogQuery) {
  return request<TeachingLogRow[]>(`/teaching-logs${toQuery({ ...query })}`)
}

export function fetchTeachingLogRoster(scheduleId: number) {
  return request<TeachingLogStudent[]>(`/teaching-logs/${scheduleId}/roster`)
}

export function saveTeachingLog(scheduleId: number, records: TeachingLogRecordPayload[]) {
  return request<{ recorded: number; present: number }>(`/teaching-logs/${scheduleId}`, {
    method: 'POST',
    body: JSON.stringify({ records }),
  })
}

export interface CourseRow {
  id: number
  name: string
  category: string | null
  subject: string | null
  lessonMode: string | null
  chargeMode: string | null
  campusScope: string | null
  campusId: number | null
  campusName: string | null
  campusCount: number
  studentCount: number
  activeStudentCount: number
  buyLessons: number
  usedLessons: number
  remainLessons: number
  paidAmount: number
  debt: number
  classCount: number
  scheduleCount: number
  lastLessonDate: string | null
  materialFee: number | null
  createdAt: string | null
  status: number
  remark: string | null
}

export interface CourseSummary {
  total: number
  active: number
  opening: number
  enrollCount: number
  remainLessons: number
  paidAmount: number
  debt: number
}

export interface CourseQuery {
  keyword?: string
  campusIds?: string
  categories?: string
  subjects?: string
  status?: number
  lessonMode?: string
  chargeMode?: string
  sortField?: string
  sortOrder?: string
}

export interface CoursePriceRow {
  id: number
  campusId: number | null
  campusName: string | null
  lessons: number
  totalPrice: number | null
}

export interface CourseClassRow {
  id: number
  name: string
  teacherName: string | null
  status: string | null
  enrolledCount: number
  capacity: number
  scheduleText: string | null
  startDate: string | null
}

export interface CourseDetail {
  id: number
  name: string
  category: string | null
  subject: string | null
  lessonMode: string | null
  campusScope: string | null
  campusId: number | null
  campusName: string | null
  lessonCharge: number
  timeCharge: number
  periodCharge: number
  materialFee: number | null
  remark: string | null
  status: number
  createdAt: string | null
  prices: CoursePriceRow[]
  classes: CourseClassRow[]
}

export interface CourseOptionRow {
  id: number
  name: string
}

export interface CourseCategoryRow {
  id: number
  name: string
  courseCount: number
}

export interface CourseSubjectRow {
  id: number
  name: string
  status: number
  createdAt: string | null
}

export interface CourseUpgradeRow {
  id: number
  name: string
  fromCourseId: number | null
  fromCourseName: string | null
  toCourseId: number | null
  toCourseName: string | null
  periodCount: number
  courseCount: number
  createdAt: string | null
}

export interface CourseOptions {
  campuses: CourseOptionRow[]
  categories: CourseCategoryRow[]
  subjects: CourseSubjectRow[]
}

export interface CoursePricePayload {
  campusId?: number | null
  lessons: number
  totalPrice?: number | null
}

export interface CourseFormPayload {
  name?: string
  category?: string
  subject?: string | null
  lessonMode?: string
  campusScope?: string
  campusId?: number | null
  lessonCharge?: boolean
  timeCharge?: boolean
  periodCharge?: boolean
  prices?: CoursePricePayload[]
  materialFee?: number
  remark?: string | null
}

export function fetchCoursePage(query: CourseQuery & { page?: number; size?: number }) {
  return request<PageResult<CourseRow>>(`/courses/page${toQuery({ ...query })}`)
}

export function fetchCourseSummary(query: CourseQuery) {
  return request<CourseSummary>(`/courses/summary${toQuery({ ...query })}`)
}

export function fetchCourseOptions() {
  return request<CourseOptions>('/courses/options')
}

export function fetchCourseDetail(id: number) {
  return request<CourseDetail>(`/courses/${id}`)
}

export function fetchCourseClasses(id: number) {
  return request<CourseClassRow[]>(`/courses/${id}/classes`)
}

export function createCourse(payload: CourseFormPayload) {
  return request<{ id: number; name: string }>('/courses', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateCourse(id: number, payload: CourseFormPayload) {
  return request<null>(`/courses/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteCourse(id: number) {
  return request<null>(`/courses/${id}`, { method: 'DELETE' })
}

export function batchCourses(ids: number[], action: 'delete' | 'on' | 'off') {
  return request<number>('/courses/batch', {
    method: 'POST',
    body: JSON.stringify({ ids, action }),
  })
}

export function fetchCourseCategories(keyword?: string) {
  return request<CourseCategoryRow[]>(`/courses/categories${toQuery({ keyword })}`)
}

export function createCourseCategory(name: string) {
  return request<CourseCategoryRow>('/courses/categories', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function renameCourseCategory(id: number, name: string) {
  return request<null>(`/courses/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export function deleteCourseCategory(id: number) {
  return request<null>(`/courses/categories/${id}`, { method: 'DELETE' })
}

export function fetchCourseSubjects() {
  return request<CourseSubjectRow[]>('/courses/subjects')
}

export function createCourseSubject(name: string) {
  return request<CourseSubjectRow>('/courses/subjects', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function deleteCourseSubject(id: number) {
  return request<null>(`/courses/subjects/${id}`, { method: 'DELETE' })
}

export function fetchCourseUpgrades(keyword?: string) {
  return request<CourseUpgradeRow[]>(`/courses/upgrades${toQuery({ keyword })}`)
}

export function createCourseUpgrade(payload: {
  name: string
  fromCourseId: number
  toCourseId?: number | null
  periodCount?: number
}) {
  return request<CourseUpgradeRow>('/courses/upgrades', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteCourseUpgrade(id: number) {
  return request<null>(`/courses/upgrades/${id}`, { method: 'DELETE' })
}

export interface StaffRow {
  id: number
  name: string
  phone: string | null
  account: string | null
  role: string
  /** 兼任角色，不含主角色。 */
  extraRoles: string[]
  title: string | null
  status: number
  campusId: number | null
  campusName: string | null
  classCount: number
  scheduleCount: number
  commentCount: number
  /** 可见菜单路径；["*"] 表示全部菜单。 */
  permissions: string[]
}

export interface StaffSummary {
  total: number
  active: number
  teachers: number
  consultants: number
}

export interface StaffQuery {
  keyword?: string
  campusId?: number
  role?: string
  status?: number
}

export function fetchStaff(query: StaffQuery) {
  return request<StaffRow[]>(`/staff${toQuery({ ...query })}`)
}

export function fetchStaffSummary(query: StaffQuery) {
  return request<StaffSummary>(`/staff/summary${toQuery({ ...query })}`)
}

export interface StaffOptions {
  campuses: Array<{ id: number; name: string; status: number }>
  roles: string[]
}

export function fetchStaffOptions() {
  return request<StaffOptions>('/staff/options')
}

export interface StaffFormPayload {
  name: string
  phone?: string
  account?: string
  password?: string
  role: string
  extraRoles?: string[]
  title?: string
  permissions?: string[]
  campusId?: number | null
  status?: number
}

export function createStaff(payload: StaffFormPayload) {
  return request<StaffRow>('/staff', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateStaff(id: number, payload: StaffFormPayload) {
  return request<StaffRow>(`/staff/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateStaffStatus(id: number, status: number) {
  return request<StaffRow>(`/staff/${id}/status?status=${status}`, { method: 'POST' })
}

/** 组织架构页调整员工可见菜单，仅管理员可调用。 */
export function updateStaffPermissions(id: number, permissions: string[]) {
  return request<string[]>(`/staff/${id}/permissions`, {
    method: 'PUT',
    body: JSON.stringify({ permissions }),
  })
}

export interface PermissionTemplateRow {
  id: number
  name: string
  /** 适用角色，null 表示通用。 */
  role: string | null
  /** 1 内置角色模板，0 自定义模板。 */
  builtin: number
  permissions: string[]
  updatedAt: string
}

export interface PermissionTemplatePayload {
  name: string
  role?: string | null
  permissions: string[]
}

export function fetchPermissionTemplates() {
  return request<PermissionTemplateRow[]>('/permission-templates')
}

export function createPermissionTemplate(payload: PermissionTemplatePayload) {
  return request<PermissionTemplateRow>('/permission-templates', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePermissionTemplate(id: number, payload: PermissionTemplatePayload) {
  return request<PermissionTemplateRow>(`/permission-templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deletePermissionTemplate(id: number) {
  return request<void>(`/permission-templates/${id}`, { method: 'DELETE' })
}

export interface PaymentRow {
  id: number
  campusId: number
  campusName: string
  studentId: number
  studentName: string
  orderNo: string | null
  amount: number
  method: string | null
  paidAt: string
  operatorName: string | null
  voucherNo: string | null
  remark: string | null
}

export interface FinanceSummary {
  totalReceived: number
  monthReceived: number
  lastMonthReceived: number
  paymentCount: number
  studentCount: number
  orderCount: number
  debt: number
}

export interface MonthlyRow {
  month: string
  count: number
  amount: number
}

export interface FinanceQuery {
  keyword?: string
  campusId?: number
  start?: string
  end?: string
}

export function fetchPayments(query: FinanceQuery & { page?: number; size?: number }) {
  return request<PageResult<PaymentRow>>(`/finance/payments${toQuery({ ...query })}`)
}

export function fetchFinanceSummary(query: FinanceQuery) {
  return request<FinanceSummary>(`/finance/summary${toQuery({ ...query })}`)
}

export function fetchFinanceMonthly(params: { campusId?: number; keyword?: string; months?: number }) {
  return request<MonthlyRow[]>(`/finance/monthly${toQuery({ ...params })}`)
}

export interface ReportQuery {
  campusId?: number
  start?: string
  end?: string
}

export interface CampusCompareRow {
  campusName: string
  receivable: number
  received: number
  payments: number
  lessons: number
  activeStudents: number
}

export interface BusinessReport {
  consults: number
  deals: number
  receivable: number
  received: number
  discount: number
  refund: number
  periodPayments: number
  balance: number
  activeStudents: number
  enrollmentCount: number
  remainLessons: number
  periodLessons: number
  debt: number
  materialIncome: number
  campuses: CampusCompareRow[]
}

export interface TeacherWorkloadRow {
  teacherName: string
  campusName: string
  records: number
  schedules: number
  lessons: number
  classes: number
  attendRate: number
}

export interface CourseConsumeRow {
  courseName: string
  students: number
  records: number
  lessons: number
}

export interface TeachingReport {
  records: number
  lessons: number
  students: number
  teacherCount: number
  present: number
  attendRate: number
  teachers: TeacherWorkloadRow[]
  courses: CourseConsumeRow[]
}

export interface DailyFinanceRow {
  date: string
  receivable: number
  received: number
  refund: number
  debt: number
}

export interface FinanceReport {
  receivable: number
  received: number
  refund: number
  debt: number
  orderCount: number
  paymentCount: number
  daily: DailyFinanceRow[]
}

export interface MaterialRow {
  name: string
  count: number
  receivable: number
  received: number
}

export interface MaterialReport {
  count: number
  receivable: number
  received: number
  rows: MaterialRow[]
}

export interface TopCourseRow {
  courseName: string
  students: number
  remainLessons: number
}

export interface ScreenReport {
  studentsTotal: number
  activeStudents: number
  classes: number
  todayClasses: number
  monthOrders: number
  monthConsults: number
  monthRevenue: number
  monthLessons: number
  debt: number
  remainLessons: number
  campuses: CampusCompareRow[]
  topCourses: TopCourseRow[]
}

export interface InsightRow {
  key: string
  tag: string
  title: string
  detail: string
  value: string
  trend: 'up' | 'down' | 'flat'
}

export function fetchBusinessReport(query: ReportQuery) {
  return request<BusinessReport>(`/reports/business${toQuery({ ...query })}`)
}

export function fetchTeachingReport(query: ReportQuery) {
  return request<TeachingReport>(`/reports/teaching${toQuery({ ...query })}`)
}

export function fetchFinanceReport(query: ReportQuery) {
  return request<FinanceReport>(`/reports/finance${toQuery({ ...query })}`)
}

export function fetchMaterialReport(query: ReportQuery) {
  return request<MaterialReport>(`/reports/material${toQuery({ ...query })}`)
}

export interface MaterialBuyRecordRow {
  id: number
  extOrderNo: string
  tradeType: string | null
  studentName: string | null
  itemName: string
  quantity: number
  paidText: string | null
  paidAmount: number | null
  paidPoints: number | null
  receiveStatus: string | null
  campusName: string | null
  handler: string | null
  handledDate: string | null
  remark: string | null
  source: string
}

export interface MaterialBuySummary {
  total: number
  orderCount: number
  amount: number
  unclaimed: number
}

export interface MaterialBuyQuery {
  orderNo?: string
  tradeType?: string
  receiveStatus?: string
  page?: number
  size?: number
}

export function fetchMaterialBuyRecords(query: MaterialBuyQuery) {
  return request<PageResult<MaterialBuyRecordRow>>(`/materials/buy-records${toQuery({ ...query })}`)
}

export function fetchMaterialBuySummary(query: MaterialBuyQuery) {
  return request<MaterialBuySummary>(`/materials/buy-records/summary${toQuery({ ...query })}`)
}

export interface MaterialStockRow {
  id: number
  name: string
  category: string | null
  status: string
  warehouse: number
  bayuquan: number
  xinmin: number
  total: number
}

export interface MaterialStockSummary {
  count: number
  warehouse: number
  bayuquan: number
  xinmin: number
  total: number
  lowStock: number
}

export interface MaterialStockQuery {
  keyword?: string
  category?: string
  status?: string
  lowStock?: boolean
  page?: number
  size?: number
}

export function fetchMaterialStock(query: MaterialStockQuery) {
  return request<PageResult<MaterialStockRow>>(`/materials/stock${toQuery({ ...query })}`)
}

export function fetchMaterialStockSummary(query: Omit<MaterialStockQuery, 'page' | 'size'>) {
  return request<MaterialStockSummary>(`/materials/stock/summary${toQuery({ ...query })}`)
}

export interface MaterialSettingRow {
  id: number
  name: string
  costPrice: number
  salePrice: number
  points: number | null
  category: string | null
  status: string
}

export interface MaterialSettingQuery {
  keyword?: string
  category?: string
  status?: string
  page?: number
  size?: number
}

export function fetchMaterialSettings(query: MaterialSettingQuery) {
  return request<PageResult<MaterialSettingRow>>(`/materials/settings${toQuery({ ...query })}`)
}

export interface IncidentalRow {
  id: number
  name: string
  price: number
  points: number | null
  category: string | null
  status: string
}

export interface IncidentalQuery {
  keyword?: string
  category?: string
  status?: string
  page?: number
  size?: number
}

export function fetchIncidentals(query: IncidentalQuery) {
  return request<PageResult<IncidentalRow>>(`/materials/incidentals${toQuery({ ...query })}`)
}

export function fetchMaterialCategories() {
  return request<string[]>('/materials/categories')
}

export interface MaterialChangeRow {
  id: number
  roomName: string
  materialName: string
  bizType: string
  quantity: number
  unitPrice: number
  changeDate: string | null
  handler: string | null
  studentName: string | null
  extOrderNo: string | null
  remark: string | null
}

export interface MaterialChangeSummary {
  count: number
  inSum: number
  outSum: number
  total: number
}

export interface MaterialChangeQuery {
  keyword?: string
  room?: string
  bizType?: string
  materialName?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export function fetchMaterialChanges(query: MaterialChangeQuery) {
  return request<PageResult<MaterialChangeRow>>(`/materials/stock/changes${toQuery({ ...query })}`)
}

export function fetchMaterialChangeSummary(query: Omit<MaterialChangeQuery, 'page' | 'size'>) {
  return request<MaterialChangeSummary>(`/materials/stock/changes/summary${toQuery({ ...query })}`)
}

export function fetchMaterialChangeOptions() {
  return request<{ rooms: string[]; materials: string[] }>('/materials/stock/changes/options')
}

export interface MaterialOptions {
  materials: Array<{ id: number; name: string; category: string | null; status: string; salePrice: number }>
  rooms: string[]
  categories: string[]
}

export function fetchMaterialOptions() {
  return request<MaterialOptions>('/materials/options')
}

export interface MaterialPurchaseItem {
  materialName: string
  quantity: number
  /** 不填按教材设置售价 */
  unitPrice?: number
}

export interface MaterialPurchasePayload {
  studentId: number
  roomName: string
  payMethod?: string
  items: MaterialPurchaseItem[]
  /** 使用账户剩余未用学费抵扣的金额 */
  balanceAmount?: number
  handler?: string
  remark?: string
}

export interface MaterialPurchaseResult {
  extOrderNo: string
  itemCount: number
  quantity: number
  totalAmount: number
  balanceAmount: number
  cashPay: number
  payMethod: string
}

export function createMaterialPurchase(payload: MaterialPurchasePayload) {
  return request<MaterialPurchaseResult>('/materials/purchases', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface PointsExchangePayload {
  studentId: number
  roomName: string
  materialName: string
  quantity: number
  handler?: string
  remark?: string
}

export interface PointsExchangeResult {
  id: number
  extOrderNo: string
  itemName: string
  quantity: number
  paidPoints: number | null
}

/** 学员积分兑换教材：扣积分、扣库存、生成购买记录与积分订单。 */
export function exchangeMaterialPoints(payload: PointsExchangePayload) {
  return request<PointsExchangeResult>('/materials/points-exchange', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface MaterialFormPayload {
  name?: string
  costPrice?: number
  salePrice?: number
  points?: number
  category?: string
  status?: string
}

export function createMaterial(payload: MaterialFormPayload) {
  return request<{ id: number; name: string }>('/materials/settings', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateMaterial(id: number, payload: MaterialFormPayload) {
  return request<{ id: number; name: string }>(`/materials/settings/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteMaterial(id: number) {
  return request<void>(`/materials/settings/${id}`, { method: 'DELETE' })
}

export interface StockChangePayload {
  roomName?: string
  materialName?: string
  bizType?: string
  quantity?: number
  unitPrice?: number
  changeDate?: string
  handler?: string
  studentName?: string
  extOrderNo?: string
  remark?: string
}

export function createStockChange(payload: StockChangePayload) {
  return request<{ id: number }>('/materials/stock/changes', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateStockChange(id: number, payload: StockChangePayload) {
  return request<{ id: number }>(`/materials/stock/changes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteStockChange(id: number) {
  return request<void>(`/materials/stock/changes/${id}`, { method: 'DELETE' })
}

export function receiveMaterialBuyRecord(id: number) {
  return request<void>(`/materials/buy-records/${id}/receive`, { method: 'POST' })
}

export function cancelReceiveMaterialBuyRecord(id: number) {
  return request<void>(`/materials/buy-records/${id}/cancel-receive`, { method: 'POST' })
}

/* ---------------- 资料费（A4 打印资料，无库存） ---------------- */
export interface DataFeeTemplateRow {
  id: number
  name: string
  price: number
  cycleMonths: number
  status: string
  remark?: string | null
}

export interface DataFeeTemplatePayload {
  name: string
  price: number
  cycleMonths: number
  status?: string
  remark?: string
}

export interface DataFeeEnrollmentOption {
  id: number
  courseId: number
  courseName: string
  lastPaidAt?: string | null
}

export interface DataFeeOptions {
  templates: DataFeeTemplateRow[]
  enrollments: DataFeeEnrollmentOption[]
  payMethods: string[]
}

export interface DataFeePaymentPayload {
  studentId: number
  enrollmentId: number
  templateId: number
  payMethod?: string
  handler?: string
  remark?: string
}

export interface DataFeePaymentResult {
  orderNo: string
  orderId: number
  courseName: string
  templateName: string
  price: number
  cycleMonths: number
  periodStart: string
  periodEnd: string
  paidAt: string
  lastPaidAt: string
}

export function fetchDataFeeTemplates() {
  return request<DataFeeTemplateRow[]>('/data-fees/templates')
}

export function createDataFeeTemplate(payload: DataFeeTemplatePayload) {
  return request<DataFeeTemplateRow>('/data-fees/templates', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDataFeeTemplate(id: number, payload: DataFeeTemplatePayload) {
  return request<DataFeeTemplateRow>(`/data-fees/templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteDataFeeTemplate(id: number) {
  return request<void>(`/data-fees/templates/${id}`, { method: 'DELETE' })
}

export function fetchDataFeeOptions(studentId: number) {
  return request<DataFeeOptions>(`/data-fees/options${toQuery({ studentId })}`)
}

export function payDataFee(payload: DataFeePaymentPayload) {
  return request<DataFeePaymentResult>('/data-fees/payments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchScreenReport(query: { campusId?: number }) {
  return request<ScreenReport>(`/reports/screen${toQuery({ ...query })}`)
}

export function fetchInsights(query: { campusId?: number }) {
  return request<InsightRow[]>(`/reports/insights${toQuery({ ...query })}`)
}

export interface ConsultRow {
  id: number
  campusId: number
  campusName: string | null
  name: string
  phone: string | null
  childName: string | null
  sex: string | null
  parentName: string | null
  motherTel: string | null
  fatherTel: string | null
  otherTel: string | null
  source: string | null
  channel: string | null
  scene: string | null
  method: string | null
  salesway: string | null
  salesmanName: string | null
  status: string
  followStateText: string | null
  enrolled: number | null
  interest: string | null
  intentionCourse: string | null
  schoolName: string | null
  grade: string | null
  pubSchool: string | null
  homeAddress: string | null
  birthday: string | null
  arrearage: number | null
  remainLessons: number | null
  remark: string | null
  content: string | null
  nextFollowAt: string | null
  lastFollowAt: string | null
  createdAt: string | null
}

export interface ConsultSourceRow {
  source: string
  count: number
  enrolled: number
}

export interface ConsultSummary {
  total: number
  enrolled: number
  following: number
  thisMonth: number
  phoneCount: number
  sources: ConsultSourceRow[]
}

export interface ConsultQuery {
  keyword?: string
  campusId?: number
  source?: string
  status?: string
  enrolled?: number
  start?: string
  end?: string
  page?: number
  size?: number
}

export function fetchConsultList(query: ConsultQuery) {
  return request<PageResult<ConsultRow>>(`/consult${toQuery({ ...query })}`)
}

export function fetchConsultSummary(query: Omit<ConsultQuery, 'page' | 'size'>) {
  return request<ConsultSummary>(`/consult/summary${toQuery({ ...query })}`)
}

/** ---------------- 招生中心 - 活动效果（只读统计，活动模板在缴费模板中维护） ---------------- */

export interface ActivityStatsRow {
  id: number
  name: string
  categoryName: string
  type: ActivityTemplateType
  startDate: string | null
  endDate: string | null
  status: '未开始' | '生效中' | '已结束' | '已停用'
  studentCount: number
  newStudentCount: number
  oldStudentCount: number
  orderCount: number
  lessons: number
  shouldAmount: number
  receivedAmount: number
  discountAmount: number
  remark: string | null
}

export interface ActivityStatsQuery {
  keyword?: string
  categoryId?: number | null
  type?: string | null
  startDate?: string | null
  endDate?: string | null
}

export function fetchActivityStats(query: ActivityStatsQuery) {
  return request<{ total: number; rows: ActivityStatsRow[] }>(`/marketing/activity-stats${toQuery({ ...query })}`)
}

export interface ActivityStatsStudent {
  studentId: number
  studentName: string
  phone: string
  campusName: string
  courseNames: string
  orderCount: number
  lessons: number
  shouldAmount: number
  receivedAmount: number
  discountAmount: number
  firstJoinTime: string | null
  isNew: boolean
}

export interface ActivityStatsStudentPage {
  total: number
  rows: ActivityStatsStudent[]
  summary: {
    studentCount: number
    orderCount: number
    lessons: number
    shouldAmount: number
    receivedAmount: number
    discountAmount: number
  }
}

export function fetchActivityStatsStudents(
  id: number,
  query: { keyword?: string; page?: number; size?: number },
) {
  return request<ActivityStatsStudentPage>(`/marketing/activity-stats/${id}/students${toQuery({ ...query })}`)
}

// -------------------------------------------------------------------
// 基础价格模板：价格与课程绑定，报读时按模板带价
// -------------------------------------------------------------------

export interface PriceTemplateTier {
  lessons: number
  unitPrice: number
  totalPrice: number
}

export interface PriceTemplate {
  id: number
  name: string
  campusScope: string
  campusIds: number[]
  campusNames: string[]
  classType: string | null
  materialFee: number
  validDays: number | null
  status: number
  remark: string | null
  courseIds: number[]
  courseNames: string[]
  prices: PriceTemplateTier[]
  usedCount: number
  updatedAt: string | null
}

export interface PriceTemplateOptions {
  campuses: { id: number; name: string }[]
  courses: { id: number; name: string; campusId: number | null; defaultPriceTemplateId: number | null }[]
  classTypes: string[]
}

export interface PriceTemplateFormPayload {
  name: string
  campusScope: string
  campusIds: number[]
  classType: string | null
  materialFee: number
  validDays: number | null
  courseIds: number[]
  prices: PriceTemplateTier[]
  remark: string | null
}

export function fetchPriceTemplates(query: {
  keyword?: string
  status?: number | null
  courseId?: number | null
}) {
  return request<{ total: number; rows: PriceTemplate[] }>(`/price-templates${toQuery({ ...query })}`)
}

export function fetchPriceTemplateOptions() {
  return request<PriceTemplateOptions>('/price-templates/options')
}

export function createPriceTemplate(payload: PriceTemplateFormPayload) {
  return request<number>('/price-templates', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePriceTemplate(id: number, payload: PriceTemplateFormPayload) {
  return request<void>(`/price-templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function setPriceTemplateStatus(id: number, status: number) {
  return request<void>(`/price-templates/${id}/status?status=${status}`, { method: 'POST' })
}

export function setPriceTemplateDefault(id: number) {
  return request<number>(`/price-templates/${id}/default`, { method: 'POST' })
}

export function deletePriceTemplate(id: number) {
  return request<void>(`/price-templates/${id}`, { method: 'DELETE' })
}

// -------------------------------------------------------------------
// 活动模板：节日活动 / 报课优惠，按分类层级组织
// -------------------------------------------------------------------

export type ActivityTemplateType = '节日活动' | '报课优惠'
export type ActivityDiscountMode = '折扣' | '立减' | '赠送课时'

export interface ActivityCategory {
  id: number
  name: string
  parentId: number | null
  sortNo: number
  templateCount: number
}

export interface ActivityRule {
  lessons: number
  discountMode: ActivityDiscountMode
  discountValue: number
}

export interface ActivityTemplate {
  id: number
  categoryId: number | null
  categoryName: string
  name: string
  type: ActivityTemplateType
  startDate: string | null
  endDate: string | null
  status: number
  remark: string | null
  rules: ActivityRule[]
  discountText: string
  updatedAt: string | null
}

export interface ActivityCategoryForm {
  name: string
  parentId: number | null
}

export interface ActivityTemplateFormPayload {
  categoryId: number | null
  name: string
  type: ActivityTemplateType
  startDate: string | null
  endDate: string | null
  rules: ActivityRule[]
  remark: string | null
}

export function fetchActivityCategories() {
  return request<ActivityCategory[]>('/activity-templates/categories')
}

export function createActivityCategory(payload: ActivityCategoryForm) {
  return request<number>('/activity-templates/categories', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateActivityCategory(id: number, payload: ActivityCategoryForm) {
  return request<void>(`/activity-templates/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteActivityCategory(id: number) {
  return request<void>(`/activity-templates/categories/${id}`, { method: 'DELETE' })
}

export function fetchActivityTemplates(query: {
  keyword?: string
  type?: string | null
  status?: number | null
  categoryId?: number | null
}) {
  return request<{ total: number; rows: ActivityTemplate[] }>(
    `/activity-templates/templates${toQuery({ ...query })}`,
  )
}

export function createActivityTemplate(payload: ActivityTemplateFormPayload) {
  return request<number>('/activity-templates/templates', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateActivityTemplate(id: number, payload: ActivityTemplateFormPayload) {
  return request<void>(`/activity-templates/templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function setActivityTemplateStatus(id: number, status: number) {
  return request<void>(`/activity-templates/templates/${id}/status?status=${status}`, {
    method: 'POST',
  })
}

export function deleteActivityTemplate(id: number) {
  return request<void>(`/activity-templates/templates/${id}`, { method: 'DELETE' })
}

/** 订单明细：查看每笔缴费使用的价格模板/活动模板。 */
export function fetchOrderItems(orderIds: number[]) {
  return request<
    {
      id: number
      orderId: number
      courseName: string | null
      priceTemplateName: string | null
      activityTemplateName: string | null
      lessons: number
      unitPrice: number
      baseAmount: number
      discountAmount: number
      materialAmount: number
      amount: number
    }[]
  >(`/local-orders/items?orderIds=${orderIds.join(',')}`)
}

/* ---------- 积分兑换（积分为独立口径，与金额无关） ---------- */

export interface PointsGoodsRow {
  id: number
  name: string
  pointsValue: number
  stock: number
  status: string
  remark: string | null
}

export interface PointsGoodsPayload {
  name: string
  pointsValue: number
  stock: number
  status: string
  remark?: string
}

export function fetchPointsGoods(params?: { keyword?: string; status?: string }) {
  return request<PointsGoodsRow[]>(`/points-goods/list${toQuery(params ?? {})}`)
}

export function createPointsGoods(payload: PointsGoodsPayload) {
  return request<PointsGoodsRow>('/points-goods', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePointsGoods(id: number, payload: PointsGoodsPayload) {
  return request<PointsGoodsRow>(`/points-goods/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deletePointsGoods(id: number) {
  return request<void>(`/points-goods/${id}`, { method: 'DELETE' })
}

export interface PointsGoodsExchangePayload {
  studentId: number
  goodsId: number
  quantity: number
  handler?: string
  remark?: string
}

export interface PointsGoodsExchangeResult {
  id: number
  orderNo: string
  studentName: string
  goodsName: string
  quantity: number
  pointsCost: number
}

/** 学员积分兑换兑换物：扣积分、扣库存，生成兑换记录与积分订单。 */
export function exchangePointsGoods(payload: PointsGoodsExchangePayload) {
  return request<PointsGoodsExchangeResult>('/points-goods/exchange', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/* ------------------------------ 成绩中心 ------------------------------ */

export type GradeExamStatus = '草稿' | '已发布' | '已归档'
export type GradeType = '周考' | '月考' | '学校考试'
export type GradeScoringMode = '分数' | '等级'

export interface GradePart {
  id?: number
  name: string
  weight: number
  fullScore: number
  sort?: number
}

export interface GradeExamRow {
  id: number
  campusId: number | null
  name: string
  type: GradeType
  courseId: number | null
  courseName: string | null
  examDate: string | null
  fullScore: number
  scoringMode: GradeScoringMode
  paperImages: string[]
  status: GradeExamStatus
  creator: string | null
  remark: string | null
  createdAt: string
  classIds: number[]
  classNames: string[]
  parts: GradePart[]
  studentCount: number
  enteredCount: number
}

export interface GradeExamPayload {
  name: string
  type: GradeType
  campusId?: number
  courseId?: number | null
  courseName?: string | null
  examDate?: string | null
  fullScore: number
  scoringMode: GradeScoringMode
  remark?: string
  classIds: number[]
  parts: GradePart[]
  paperImages?: string[]
}

export interface GradeClassOption {
  id: number
  name: string
  campusId: number | null
  courseId: number | null
  courseName: string | null
}

export interface GradeEntryStudent {
  studentId: number
  studentName: string
  studentNo: string | null
  enrollmentId: number | null
  scoreId: number | null
  totalScore: number | null
  level: string | null
  partScores: Record<string, number>
  status: string
  remark: string | null
  rankClass: number | null
}

export interface GradeEntryData {
  examId: number
  examName: string
  examType: GradeType
  examDate: string | null
  scoringMode: GradeScoringMode
  fullScore: number
  status: GradeExamStatus
  classId: number
  className: string
  parts: GradePart[]
  students: GradeEntryStudent[]
}

export interface GradeScoreRowPayload {
  studentId: number
  enrollmentId?: number | null
  totalScore?: number | null
  level?: string | null
  partScores?: Record<string, number>
  status?: string
  remark?: string
}

export interface GradeScoreSavePayload {
  examId: number
  classId: number
  rows: GradeScoreRowPayload[]
}

export interface GradeQueryRow {
  id: number
  examId: number
  examName: string
  examType: GradeType
  examDate: string | null
  fullScore: number
  scoringMode: GradeScoringMode
  classId: number
  className: string | null
  studentId: number
  studentName: string
  totalScore: number | null
  level: string | null
  status: string
  rankClass: number | null
  rankCampus: number | null
  remark: string | null
}

export interface GradeStudentItem {
  examId: number
  examName: string
  examType: GradeType
  examDate: string | null
  fullScore: number
  scoringMode: GradeScoringMode
  classId: number
  className: string | null
  totalScore: number | null
  level: string | null
  partScores: Record<string, number>
  status: string
  rankClass: number | null
  rankCampus: number | null
  classAvgScore: number | null
  remark: string | null
}

export interface GradeClassExam {
  examId: number
  examName: string
  examType: GradeType
  examDate: string | null
  fullScore: number
  scoringMode: GradeScoringMode
  status: GradeExamStatus
  enteredCount: number
  studentCount: number
  avgScore: number | null
  maxScore: number | null
  minScore: number | null
  passRate: number | null
  excellentRate: number | null
}

export interface GradeAnalysis {
  examId: number
  examName: string
  examType: GradeType
  examDate: string | null
  scoringMode: GradeScoringMode
  fullScore: number
  studentCount: number
  enteredCount: number
  absentCount: number
  exemptCount: number
  missingCount: number
  avgScore: number | null
  maxScore: number | null
  minScore: number | null
  passRate: number | null
  excellentRate: number | null
  distributions: { label: string; count: number }[]
  classes: {
    classId: number
    className: string
    count: number
    avgScore: number | null
    maxScore: number | null
    minScore: number | null
    passRate: number | null
    excellentRate: number | null
  }[]
  rankings: {
    rank: number
    studentId: number
    studentName: string | null
    className: string | null
    totalScore: number
    level: string | null
  }[]
  levels: { level: string; count: number }[]
}

export interface GradeRule {
  id: number
  name: string
  minScore: number
  maxScore: number
  sort: number
}

export interface GradeRulePayload {
  name: string
  minScore: number
  maxScore: number
  sort: number
}

export interface GradePartTemplate {
  id: number
  name: string
  courseCategory: string | null
  parts: GradePart[]
  createdAt: string
}

export interface GradePartTemplatePayload {
  name: string
  courseCategory?: string
  parts: GradePart[]
}

export function fetchGradeExams(params?: {
  keyword?: string
  type?: string
  status?: string
  courseId?: number
  classId?: number
  campusId?: number
}) {
  return request<GradeExamRow[]>(`/grades/exams${toQuery(params ?? {})}`)
}

export function fetchGradeClassOptions() {
  return request<GradeClassOption[]>('/grades/exams/class-options')
}

export function fetchGradeExam(id: number) {
  return request<GradeExamRow>(`/grades/exams/${id}`)
}

export function createGradeExam(payload: GradeExamPayload) {
  return request<GradeExamRow>('/grades/exams', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateGradeExam(id: number, payload: GradeExamPayload) {
  return request<GradeExamRow>(`/grades/exams/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateGradeExamFullScore(id: number, fullScore: number) {
  return request<GradeExamRow>(`/grades/exams/${id}/full-score${toQuery({ fullScore })}`, {
    method: 'PUT',
  })
}

export function deleteGradeExam(id: number) {
  return request<void>(`/grades/exams/${id}`, { method: 'DELETE' })
}

export function copyGradeExam(id: number) {
  return request<GradeExamRow>(`/grades/exams/${id}/copy`, { method: 'POST' })
}

export function publishGradeExam(id: number) {
  return request<GradeExamRow>(`/grades/exams/${id}/publish`, { method: 'POST' })
}

export function unlockGradeExam(id: number) {
  return request<GradeExamRow>(`/grades/exams/${id}/unlock`, { method: 'POST' })
}

export function archiveGradeExam(id: number) {
  return request<GradeExamRow>(`/grades/exams/${id}/archive`, { method: 'POST' })
}

/** 上传试卷图片（支持手机拍照），返回包含最新图片列表的考试数据。 */
export function uploadGradePaperImage(examId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<GradeExamRow>(`/grades/exams/${examId}/paper-images`, {
    method: 'POST',
    body: form,
  })
}

export function deleteGradePaperImage(examId: number, url: string) {
  return request<GradeExamRow>(`/grades/exams/${examId}/paper-images${toQuery({ url })}`, {
    method: 'DELETE',
  })
}

export function fetchGradeEntry(examId: number, classId: number) {
  return request<GradeEntryData>(`/grades/entry${toQuery({ examId, classId })}`)
}

export function saveGradeEntry(payload: GradeScoreSavePayload) {
  return request<GradeEntryData>('/grades/entry/save', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function publishGradeEntry(payload: GradeScoreSavePayload) {
  return request<GradeEntryData>('/grades/entry/publish', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchGradeQuery(params?: {
  examId?: number
  classId?: number
  studentId?: number
  type?: string
  scoreStatus?: string
  keyword?: string
}) {
  return request<GradeQueryRow[]>(`/grades/query${toQuery(params ?? {})}`)
}

export function fetchGradeStudentScores(studentId: number) {
  return request<GradeStudentItem[]>(`/grades/student/${studentId}`)
}

export function fetchGradeClassExams(classId: number) {
  return request<GradeClassExam[]>(`/grades/class/${classId}`)
}

export function fetchGradeAnalysis(examId: number) {
  return request<GradeAnalysis>(`/grades/analysis${toQuery({ examId })}`)
}

export function fetchGradeRules() {
  return request<GradeRule[]>('/grades/rules')
}

export function createGradeRule(payload: GradeRulePayload) {
  return request<GradeRule>('/grades/rules', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateGradeRule(id: number, payload: GradeRulePayload) {
  return request<GradeRule>(`/grades/rules/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteGradeRule(id: number) {
  return request<void>(`/grades/rules/${id}`, { method: 'DELETE' })
}

export function fetchGradePartTemplates() {
  return request<GradePartTemplate[]>('/grades/part-templates')
}

export function createGradePartTemplate(payload: GradePartTemplatePayload) {
  return request<GradePartTemplate>('/grades/part-templates', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateGradePartTemplate(id: number, payload: GradePartTemplatePayload) {
  return request<GradePartTemplate>(`/grades/part-templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteGradePartTemplate(id: number) {
  return request<void>(`/grades/part-templates/${id}`, { method: 'DELETE' })
}

/* ------------------------------ 家校服务 ------------------------------ */

export type MessageAudienceType = '全员' | '班级' | '学员'
export type MessageType = '系统通知' | '休课通知'

export interface MessageRow {
  id: number
  title: string
  content: string
  msgType: MessageType
  audienceType: MessageAudienceType
  classNameText: string | null
  targetCount: number
  receiverCount: number
  readCount: number
  sender: string | null
  sentAt: string
  createdAt: string
}

export interface MessageReceiverRow {
  id: number
  studentName: string | null
  parentName: string | null
  parentPhone: string | null
  readFlag: number
  readAt: string | null
  createdAt: string
}

export interface MessageQuery {
  keyword?: string
  msgType?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export interface MessageSendPayload {
  title: string
  content: string
  audienceType: MessageAudienceType
  classIds?: number[]
  studentIds?: number[]
}

export function fetchMessages(query: MessageQuery = {}) {
  return request<PageResult<MessageRow>>(`/messages${toQuery({ ...query })}`)
}

export function sendMessage(payload: MessageSendPayload) {
  return request<MessageRow>('/messages/send', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchMessageReceivers(
  id: number,
  query: { readFlag?: number; page?: number; size?: number } = {},
) {
  return request<PageResult<MessageReceiverRow>>(`/messages/${id}/receivers${toQuery({ ...query })}`)
}

export interface PosterRow {
  id: number
  title: string
  description: string | null
  audienceType: string
  gradeText: string | null
  classNameText: string | null
  targetCount: number
  receiverCount: number
  readCount: number
  messageId: number | null
  sender: string | null
  startDate: string | null
  endDate: string | null
  sentAt: string
  imageCount: number
  coverUrl: string | null
}

export interface PosterImageRow {
  id: number
  posterId: number | null
  url: string
  sortNo: number
}

export interface PosterQuery {
  keyword?: string
  start?: string
  end?: string
  page?: number
  size?: number
}

export interface PosterCreatePayload {
  title: string
  description?: string
  imageUrls: string[]
  grades: string[]
  classIds: number[]
  studentIds: number[]
  startDate?: string
  endDate?: string
}

export function fetchPosters(query: PosterQuery = {}) {
  return request<PageResult<PosterRow>>(`/posters${toQuery({ ...query })}`)
}

export function uploadPosterImage(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<PosterImageRow>('/posters/images', { method: 'POST', body: form })
}

export function createPoster(payload: PosterCreatePayload) {
  return request<PosterRow>('/posters', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deletePoster(id: number) {
  return request<void>(`/posters/${id}`, { method: 'DELETE' })
}

export function fetchPosterReceivers(
  id: number,
  query: { readFlag?: number; page?: number; size?: number } = {},
) {
  return request<PageResult<MessageReceiverRow>>(`/posters/${id}/receivers${toQuery({ ...query })}`)
}

export function fetchStudentGrades() {
  return request<string[]>('/students/grades')
}

export interface SuspendCandidateRow {
  id: number
  classId: number
  className: string | null
  courseName: string | null
  teacherName: string | null
  lessonDate: string
  startTime: string
  endTime: string
  room: string | null
  status: string
  suspendId: number | null
}

export interface SuspendRow {
  id: number
  scheduleId: number
  classId: number
  className: string | null
  courseName: string | null
  teacherName: string | null
  lessonDate: string
  startTime: string | null
  endTime: string | null
  reason: string
  status: string
  notifyFlag: number
  messageId: number | null
  operatorName: string | null
  suspendedAt: string
  restoredAt: string | null
  restoredBy: string | null
}

export interface SuspendBatchResult {
  suspended: number
  skipped: number
  messageId: number | null
  receiverCount: number
  notifyMessage: string | null
}

export function fetchSuspendCandidates(params: {
  start: string
  end: string
  classIds: number[]
  keyword?: string
}) {
  return request<SuspendCandidateRow[]>(
    `/class-suspends/candidates${toQuery({
      start: params.start,
      end: params.end,
      classIds: params.classIds.join(','),
      keyword: params.keyword,
    })}`,
  )
}

export function batchSuspend(payload: {
  scheduleIds: number[]
  reason: string
  notify: boolean
}) {
  return request<SuspendBatchResult>('/class-suspends/batch', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface SuspendNotifyResult {
  notified: number
  classCount: number
  messageId: number | null
  receiverCount: number
  notifyMessage: string | null
}

export function notifySuspends(payload: { ids: number[] }) {
  return request<SuspendNotifyResult>('/class-suspends/notify', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchSuspends(query: {
  start?: string
  end?: string
  classId?: number
  status?: string
  page?: number
  size?: number
} = {}) {
  return request<PageResult<SuspendRow>>(`/class-suspends${toQuery({ ...query })}`)
}

export function restoreSuspend(id: number) {
  return request<void>(`/class-suspends/${id}/restore`, { method: 'POST' })
}

