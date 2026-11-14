<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import {
  CloseOutlined,
  CopyOutlined,
  DownOutlined,
  ExclamationCircleFilled,
  FileTextFilled,
  FileTextOutlined,
  GiftFilled,
  LinkOutlined,
  PrinterOutlined,
  ReadFilled,
  RollbackOutlined,
  SearchOutlined,
  SolutionOutlined,
  StopOutlined,
  SwapOutlined,
  WalletFilled,
} from '@ant-design/icons-vue'
import OrderFilterPopover from '../components/OrderFilterPopover.vue'
import {
  downloadDailySettlement,
  fetchSpOrderDetail,
  fetchSpOrderOptions,
  fetchSpOrderPage,
  fetchStudents,
  printOrderReceipt,
  voidSpOrder,
  type SpOrderOptions,
  type SpOrderQuery,
  type SpOrderRow,
  type SpOrderSummary,
  type LocalOrderMode,
  type StudentRow,
} from '../api/client'

interface FilterOption {
  label: string
  value: string | number
}

interface OrderColumn {
  key: string
  title: string
  width: number
  fixed?: 'left' | 'right'
  align?: 'left' | 'center' | 'right'
  sorter?: boolean
}

type FilterType = 'multi' | 'single' | 'date'

interface FilterField {
  key: string
  label: string
  type: FilterType
  options: FilterOption[]
  value: Array<string | number>
  dateValue: [string, string] | null
  searchable?: boolean
  linked?: boolean
}

interface Chip {
  key: string
  label: string
}

interface ChipGroup {
  key: string
  label: string
  chips: Chip[]
}

const COLUMN_STORAGE_KEY = 'hc_order_hidden_columns_v4'

const router = useRouter()

const currentUser = (() => {
  try {
    const parsed = JSON.parse(localStorage.getItem('hc_user') || '{}') as {
      name?: string
      role?: string
      campusId?: number
    }
    return parsed
  } catch {
    return {}
  }
})()

const ALL_COLUMNS: OrderColumn[] = [
  { key: 'orderNo', title: '订单号', width: 118, fixed: 'left', sorter: true },
  { key: 'stuName', title: '学员姓名', width: 84, fixed: 'left' },
  { key: 'typeName', title: '订单类型', width: 100, fixed: 'left' },
  { key: 'content', title: '交易内容', width: 230 },
  { key: 'shouldPay', title: '应收/应退', width: 90 },
  { key: 'realPay', title: '实收/实退', width: 90 },
  { key: 'remain', title: '学员账户', width: 84, align: 'right' },
  { key: 'arrearage', title: '欠费', width: 70, align: 'right' },
  { key: 'credit', title: '积分', width: 66, align: 'right' },
  { key: 'tags', title: '付款方式', width: 92 },
  { key: 'hasConfirmedDetail', title: '订单状态', width: 92, align: 'center' },
  { key: 'operateTime', title: '操作时间', width: 132, align: 'center' },
  { key: 'sourceName', title: '订单来源', width: 96, align: 'center' },
  { key: 'salesSource', title: '销售来源', width: 96 },
  { key: 'campusName', title: '经办校区', width: 100 },
  { key: 'dealDate', title: '经办日期', width: 100, align: 'center', sorter: true },
  { key: 'creator', title: '经办人', width: 76 },
  { key: 'comment', title: '对内备注', width: 100 },
  { key: 'commentOuter', title: '对外备注', width: 100 },
  { key: 'action', title: '操作', width: 68, fixed: 'right' },
]

/** 与校宝默认视图一致：常用列全部展示，只有低频列默认收起。 */
const DEFAULT_HIDDEN: string[] = [
  'sourceName',
  'salesSource',
  'campusName',
  'dealDate',
  'creator',
  'comment',
  'commentOuter',
]

const TYPE_OPTIONS: FilterOption[] = [
  { label: '报名', value: 1 },
  { label: '账户余额变动', value: 2 },
  { label: '转课', value: 3 },
  { label: '停课', value: 4 },
  { label: '复课', value: 5 },
  { label: '结课', value: 6 },
  { label: '退费', value: 7 },
  { label: '教材杂费', value: 8 },
  { label: '积分', value: 9 },
  { label: '补费', value: 10 },
  { label: '资料费', value: 11 },
]

const STATUS_OPTIONS: FilterOption[] = [
  { label: '已完成', value: 2 },
  { label: '已作废', value: 3 },
]

const ARREARAGE_OPTIONS: FilterOption[] = [
  { label: '是', value: '1' },
  { label: '否', value: '0' },
]

const SEARCH_OPTIONS = [
  { label: '学员姓名', value: 'studentName' },
  { label: '订单号', value: 'orderNo' },
  { label: '课程名称', value: 'lessonName' },
]

interface QuickAction {
  label: string
  icon: Component
  bg: string
  color: string
  mode?: LocalOrderMode
  path?: string
}

const QUICK_ACTIONS: QuickAction[] = [
  { label: '报名/续费', icon: SolutionOutlined, bg: '#fff7e6', color: '#faad14', mode: 'enroll' },
  { label: '余额充值', icon: WalletFilled, bg: '#f9f0ff', color: '#722ed1', mode: 'recharge' },
  { label: '补费', icon: FileTextFilled, bg: '#fff1f0', color: '#ff4d4f', mode: 'subsidy' },
  { label: '转课', icon: SwapOutlined, bg: '#e6fffb', color: '#13c2c2', path: '/transfer' },
  { label: '退费', icon: RollbackOutlined, bg: '#f6ffed', color: '#52c41a', mode: 'refund' },
  { label: '教材杂费', icon: ReadFilled, bg: '#f6ffed', color: '#389e0d', mode: 'material' },
  { label: '积分', icon: GiftFilled, bg: '#fff1f0', color: '#f5222d', mode: 'points' },
]

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function dateToText(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function addDays(base: Date, days: number) {
  const next = new Date(base)
  next.setDate(next.getDate() + days)
  return next
}

const DEFAULT_RANGE: [string, string] = [dateToText(addDays(new Date(), -29)), dateToText(new Date())]

function readHiddenColumns() {
  try {
    const raw = JSON.parse(localStorage.getItem(COLUMN_STORAGE_KEY) || '[]')
    if (!Array.isArray(raw)) return [...DEFAULT_HIDDEN]
    const valid = raw.filter((key) => ALL_COLUMNS.some((column) => column.key === key))
    return valid.length ? valid : [...DEFAULT_HIDDEN]
  } catch {
    return [...DEFAULT_HIDDEN]
  }
}

const scope = ref<'all' | 'mine' | 'school'>('mine')
const dateRange = ref<[string, string] | null>([...DEFAULT_RANGE] as [string, string])
type DatePresetKey = 'today' | '7d' | '30d' | 'month' | 'all' | 'custom'
const DATE_PRESETS: Array<{ key: DatePresetKey; label: string }> = [
  { key: 'today', label: '今天' },
  { key: '7d', label: '近7天' },
  { key: '30d', label: '近30天' },
  { key: 'month', label: '本月' },
  { key: 'all', label: '全部' },
]
const datePreset = ref<DatePresetKey>('30d')
const dateStartDraft = ref(DEFAULT_RANGE[0])
const dateEndDraft = ref(DEFAULT_RANGE[1])
const statuses = ref<number[]>([2, 3])
const types = ref<number[]>([])
const campusIds = ref<number[]>([])
const creators = ref<string[]>([])
const courses = ref<string[]>([])
const items = ref<string[]>([])
const isArrearage = ref<string | null>(null)
const keyword = ref('')
const searchDraft = ref('')
const keywordType = ref('studentName')

const sortField = ref('orderNo')
const sortOrder = ref<'asc' | 'desc'>('desc')
const pageIndex = ref(1)
const pageSize = ref(50)

const rows = ref<SpOrderRow[]>([])
const total = ref(0)
const summary = ref<SpOrderSummary>({
  count: 0,
  shouldPay: 0,
  realPay: 0,
  remain: 0,
  arrearage: 0,
  credit: 0,
})
const loading = ref(false)
const exportingDaily = ref(false)
const options = ref<SpOrderOptions>({
  campuses: [],
  creators: [],
  salesmen: [],
  courses: [],
  items: [],
  tags: [],
  followUpTypes: [],
})

const hiddenKeys = ref<string[]>(readHiddenColumns())
const showColumns = ref(false)
const openFilter = ref<string | null>(null)
const openMenuId = ref<number | null>(null)

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<SpOrderRow | null>(null)

const studentPickOpen = ref(false)
const studentPickAction = ref<QuickAction | null>(null)
const studentPickKeyword = ref('')
const studentPickRows = ref<StudentRow[]>([])
const studentPickLoading = ref(false)
const studentPickId = ref<number | null>(null)

const voidOpen = ref(false)
const voidTarget = ref<SpOrderRow | null>(null)
const voidReason = ref('')
const voidSubmitting = ref(false)
const voidRemaining = computed(() => Math.max(0, 100 - voidReason.value.length))

let requestSeq = 0

const campusOptions = computed<FilterOption[]>(() =>
  options.value.campuses.map((campus) => ({ label: campus.name, value: campus.id })),
)
const creatorOptions = computed<FilterOption[]>(() =>
  options.value.creators.map((name) => ({ label: name, value: name })),
)
const courseOptions = computed<FilterOption[]>(() =>
  options.value.courses.map((name) => ({ label: name, value: name })),
)
const itemOptions = computed<FilterOption[]>(() =>
  options.value.items.map((name) => ({ label: name, value: name })),
)

const filterGroups = computed<FilterField[]>(() => [
  {
    key: 'isArrearage',
    label: '是否欠费',
    type: 'single',
    options: ARREARAGE_OPTIONS,
    value: isArrearage.value ? [isArrearage.value] : [],
    dateValue: null,
  },
  {
    key: 'courses',
    label: '课程',
    type: 'multi',
    options: courseOptions.value,
    value: courses.value,
    dateValue: null,
    searchable: true,
    linked: true,
  },
  {
    key: 'items',
    label: '教材杂费',
    type: 'multi',
    options: itemOptions.value,
    value: items.value,
    dateValue: null,
    searchable: true,
  },
  {
    key: 'types',
    label: '订单类型',
    type: 'multi',
    options: TYPE_OPTIONS,
    value: types.value,
    dateValue: null,
  },
  {
    key: 'creators',
    label: '经办人',
    type: 'multi',
    options: creatorOptions.value,
    value: creators.value,
    dateValue: null,
    searchable: true,
  },
  {
    key: 'campusIds',
    label: '经办校区',
    type: 'multi',
    options: campusOptions.value,
    value: campusIds.value,
    dateValue: null,
    searchable: true,
  },
  {
    key: 'statuses',
    label: '订单状态',
    type: 'multi',
    options: STATUS_OPTIONS,
    value: statuses.value,
    dateValue: null,
  },
])

const visibleColumns = computed(() =>
  ALL_COLUMNS.filter((column) => !hiddenKeys.value.includes(column.key)),
)

const columns = computed<TableColumnsType>(() => {
  const list = visibleColumns.value.map((column) => ({
    title: column.title,
    dataIndex: column.key,
    key: column.key,
    width: column.width,
    fixed: column.fixed,
    align: column.align,
    sorter: column.sorter ? true : undefined,
    sortOrder:
      column.sorter && sortField.value === column.key
        ? sortOrder.value === 'asc'
          ? 'ascend'
          : 'descend'
        : null,
  }))
  return list as TableColumnsType
})

const tableWidth = computed(
  () => visibleColumns.value.reduce((width, column) => width + column.width, 0),
)

const searchPlaceholder = computed(() => {
  if (keywordType.value === 'orderNo') return '请输入订单号'
  if (keywordType.value === 'lessonName') return '请输入课程名称'
  return '请输入姓名，支持拼音缩写'
})

const chipGroups = computed<ChipGroup[]>(() => {
  const groups: ChipGroup[] = []
  if (types.value.length) {
    groups.push({
      key: 'types',
      label: '订单类型',
      chips: types.value.map((value) => ({
        key: String(value),
        label: optionLabel(TYPE_OPTIONS, value),
      })),
    })
  }
  if (isArrearage.value !== null) {
    groups.push({
      key: 'isArrearage',
      label: '是否欠费',
      chips: [
        { key: isArrearage.value, label: isArrearage.value === '1' ? '是' : '否' },
      ],
    })
  }
  if (campusIds.value.length) {
    groups.push({
      key: 'campusIds',
      label: '经办校区',
      chips: campusIds.value.map((value) => ({
        key: String(value),
        label: optionLabel(campusOptions.value, value),
      })),
    })
  }
  if (creators.value.length) {
    groups.push({
      key: 'creators',
      label: '经办人',
      chips: creators.value.map((value) => ({ key: value, label: value })),
    })
  }
  if (courses.value.length) {
    groups.push({
      key: 'courses',
      label: '课程',
      chips: courses.value.map((value) => ({ key: value, label: value })),
    })
  }
  if (items.value.length) {
    groups.push({
      key: 'items',
      label: '教材杂费',
      chips: items.value.map((value) => ({ key: value, label: value })),
    })
  }
  if (keyword.value) {
    groups.push({
      key: 'keyword',
      label: optionLabel(SEARCH_OPTIONS, keywordType.value),
      chips: [{ key: 'keyword', label: keyword.value }],
    })
  }
  return groups
})

function optionLabel(list: FilterOption[], value: string | number) {
  return list.find((option) => String(option.value) === String(value))?.label ?? String(value)
}

function setFilterOpen(key: string, visible: boolean) {
  openFilter.value = visible ? key : openFilter.value === key ? null : openFilter.value
}

function resetScope() {
  scope.value = 'all'
}

function presetRange(key: DatePresetKey): [string, string] | null {
  const today = new Date()
  if (key === 'today') return [dateToText(today), dateToText(today)]
  if (key === '7d') return [dateToText(addDays(today, -6)), dateToText(today)]
  if (key === '30d') return [dateToText(addDays(today, -29)), dateToText(today)]
  if (key === 'month') {
    const first = new Date(today.getFullYear(), today.getMonth(), 1)
    return [dateToText(first), dateToText(today)]
  }
  return null
}

function applyDatePreset(key: DatePresetKey) {
  dateRange.value = presetRange(key)
  datePreset.value = key
  scope.value = 'all'
  pageIndex.value = 1
  load()
}

function applyCustomDate() {
  const start = dateStartDraft.value
  const end = dateEndDraft.value
  if (!start || !end) {
    message.warning('请选择完整的开始和结束日期')
    return
  }
  if (start > end) {
    message.warning('开始日期不能晚于结束日期')
    return
  }
  dateRange.value = [start, end]
  datePreset.value = 'custom'
  scope.value = 'all'
  pageIndex.value = 1
  load()
}

watch(dateRange, (value) => {
  dateStartDraft.value = value?.[0] ?? ''
  dateEndDraft.value = value?.[1] ?? ''
})

function onFilterApply(field: FilterField, values: Array<string | number>) {
  const key = field.key
  if (field.type === 'single') {
    const value = values.length ? values[0] : null
    if (key === 'isArrearage') isArrearage.value = value === null ? null : String(value)
  } else if (key === 'statuses') {
    statuses.value = values.map(Number)
  } else if (key === 'types') {
    types.value = values.map(Number)
  } else if (key === 'campusIds') {
    campusIds.value = values.map(Number)
  } else if (key === 'creators') {
    creators.value = values.map(String)
  } else if (key === 'courses') {
    courses.value = values.map(String)
  } else if (key === 'items') {
    items.value = values.map(String)
  }
  pageIndex.value = 1
  resetScope()
  load()
}

function onDateApply(value: [string, string] | null) {
  dateRange.value = value
  datePreset.value = value ? 'custom' : 'all'
  pageIndex.value = 1
  resetScope()
  load()
}

function removeChip(groupKey: string, chipKey: string) {
  if (groupKey === 'types') {
    types.value = types.value.filter((value) => String(value) !== chipKey)
  } else if (groupKey === 'campusIds') {
    campusIds.value = campusIds.value.filter((value) => String(value) !== chipKey)
  } else if (groupKey === 'creators') {
    creators.value = creators.value.filter((value) => value !== chipKey)
  } else if (groupKey === 'courses') {
    courses.value = courses.value.filter((value) => value !== chipKey)
  } else if (groupKey === 'items') {
    items.value = items.value.filter((value) => value !== chipKey)
  } else if (groupKey === 'isArrearage') {
    isArrearage.value = null
  } else if (groupKey === 'keyword') {
    keyword.value = ''
    searchDraft.value = ''
  }
  pageIndex.value = 1
  resetScope()
  load()
}

function clearFilters() {
  dateRange.value = [...DEFAULT_RANGE] as [string, string]
  datePreset.value = '30d'
  statuses.value = [2, 3]
  types.value = []
  campusIds.value = []
  creators.value = []
  courses.value = []
  items.value = []
  isArrearage.value = null
  keyword.value = ''
  searchDraft.value = ''
  keywordType.value = 'studentName'
  pageIndex.value = 1
  resetScope()
  load()
}

function applySearch() {
  keyword.value = searchDraft.value.trim()
  pageIndex.value = 1
  resetScope()
  load()
}

function onSearchTypeChange() {
  if (keyword.value) {
    applySearch()
  }
}

function setTodayScope(nextScope: 'mine' | 'school') {
  scope.value = nextScope
  const today = dateToText(new Date())
  dateRange.value = [today, today]
  datePreset.value = 'today'
  statuses.value = [2, 3]
  keyword.value = ''
  searchDraft.value = ''
  campusIds.value = []
  creators.value = []
  pageIndex.value = 1
  if (nextScope === 'mine') {
    const candidates = [currentUser.name?.trim(), currentUser.role === 'admin' ? '管理员' : ''].filter(
      (name): name is string => Boolean(name),
    )
    const matched = candidates.find((name) => options.value.creators.includes(name))
    if (matched) {
      creators.value = [matched]
    } else {
      message.info(`当前账号「${currentUser.name || '管理员'}」未匹配到校宝经办人，已展示今日全部经办人的订单`)
    }
  } else if (currentUser.campusId) {
    campusIds.value = [currentUser.campusId]
  } else {
    message.info('当前账号未绑定校区，已展示今日全部校区的订单')
  }
  load()
}

function onQuickAction(action: QuickAction) {
  if (action.path) {
    router.push(action.path)
    return
  }
  if (!action.mode) {
    message.info(`「${action.label}」暂未开放`)
    return
  }
  // 与学员详情页保持同一入口：先选学员，再进入该学员的对应功能。
  studentPickAction.value = action
  studentPickKeyword.value = ''
  studentPickRows.value = []
  studentPickId.value = null
  studentPickOpen.value = true
  searchPickStudents()
}

async function searchPickStudents() {
  studentPickLoading.value = true
  try {
    const page = await fetchStudents({
      keyword: studentPickKeyword.value.trim() || undefined,
      // 补费只针对欠费学员，其他快捷入口仍展示全部学员
      hasDebt: studentPickAction.value?.mode === 'subsidy' ? 1 : undefined,
      page: 1,
      size: 20,
    })
    studentPickRows.value = page.records
  } catch (e) {
    message.error((e as Error).message || '搜索学员失败')
  } finally {
    studentPickLoading.value = false
  }
}

function confirmStudentPick() {
  const action = studentPickAction.value
  if (!action || !studentPickId.value) {
    message.warning('请先选择学员')
    return
  }
  studentPickOpen.value = false
  if (action.mode === 'enroll') {
    router.push({ path: '/enroll', query: { studentId: String(studentPickId.value) } })
    return
  }
  router.push({
    path: `/studentDetail/${studentPickId.value}`,
    query: { action: action.mode },
  })
}

function buildQuery(): SpOrderQuery {
  return {
    pageIndex: pageIndex.value,
    pageSize: pageSize.value,
    dealStart: dateRange.value?.[0],
    dealEnd: dateRange.value?.[1],
    statuses: statuses.value.length ? statuses.value : undefined,
    types: types.value.length ? types.value : undefined,
    campusIds: campusIds.value.length ? campusIds.value : undefined,
    creators: creators.value.length ? creators.value : undefined,
    courses: courses.value.length ? courses.value : undefined,
    items: items.value.length ? items.value : undefined,
    isArrearage: isArrearage.value,
    keyword: keyword.value || undefined,
    keywordType: keyword.value ? keywordType.value : undefined,
    sortField: sortField.value,
    sortOrder: sortOrder.value,
  }
}

async function load() {
  const seq = ++requestSeq
  loading.value = true
  try {
    const page = await fetchSpOrderPage(buildQuery())
    if (seq !== requestSeq) return
    rows.value = page.list
    total.value = page.total
    summary.value = page.summary
  } catch (error) {
    if (seq === requestSeq) {
      message.error(error instanceof Error ? error.message : '订单加载失败')
    }
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

async function loadOptions() {
  try {
    options.value = await fetchSpOrderOptions()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '筛选项加载失败')
  }
}

function onPageChange(page: number, size: number) {
  const sizeChanged = size !== pageSize.value
  pageSize.value = size
  pageIndex.value = sizeChanged ? 1 : page
  load()
}

function onTableChange(
  _pagination: unknown,
  _filters: unknown,
  sorter: unknown,
) {
  const current = (Array.isArray(sorter) ? sorter[0] : sorter) as
    | { field?: string; columnKey?: string; order?: string | null }
    | undefined
  const field = String(current?.field ?? current?.columnKey ?? '')
  if (field === 'orderNo' || field === 'dealDate') {
    if (current?.order === 'ascend') {
      sortField.value = field
      sortOrder.value = 'asc'
    } else if (current?.order === 'descend') {
      sortField.value = field
      sortOrder.value = 'desc'
    } else if (sortField.value === field) {
      // 默认已是倒序，首次点击表头被 antd 视为“取消排序”，这里改为反向切换
      sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
    } else {
      sortField.value = field
      sortOrder.value = 'desc'
    }
  } else if (!current?.order) {
    sortField.value = 'orderNo'
    sortOrder.value = 'desc'
  }
  pageIndex.value = 1
  load()
}

function setColumnVisible(key: string, visible: boolean) {
  const next = new Set(hiddenKeys.value)
  if (visible) {
    next.delete(key)
  } else {
    next.add(key)
  }
  const visibleCount = ALL_COLUMNS.filter(
    (column) => column.key !== 'action' && !next.has(column.key),
  ).length
  if (visibleCount === 0) {
    message.warning('至少保留一列显示')
    return
  }
  hiddenKeys.value = [...next]
  localStorage.setItem(COLUMN_STORAGE_KEY, JSON.stringify(hiddenKeys.value))
}

function resetColumns() {
  hiddenKeys.value = [...DEFAULT_HIDDEN]
  localStorage.setItem(COLUMN_STORAGE_KEY, JSON.stringify(hiddenKeys.value))
}

function fixedMoney(value?: number | null) {
  return Number(value ?? 0).toFixed(2)
}

function absMoney(value?: number | null) {
  return Math.abs(Number(value ?? 0)).toFixed(2)
}

function signedMoney(value?: number | null) {
  const amount = Number(value ?? 0)
  const text = amount.toFixed(2)
  return amount < 0 ? text : `+${text}`
}

function dateText(value?: string | null) {
  return value ? value.slice(0, 10) : '-'
}

function dateTimeText(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function statusClass(code: number) {
  if (code === 1) return 'status-pending'
  if (code === 2) return 'status-done'
  if (code === 3) return 'status-void'
  return 'status-expired'
}

function splitTags(value?: string | null) {
  if (!value) return []
  const text = value.trim()
  if (text.startsWith('[')) {
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) return parsed.map((item) => String(item)).filter(Boolean)
    } catch {
      return [text]
    }
  }
  return text
    .split(/[,，、;；]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

async function openDetail(row: SpOrderRow) {
  openMenuId.value = null
  detail.value = row
  detailOpen.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchSpOrderDetail(row.id)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '订单详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function openStudent(row: SpOrderRow) {
  if (!row.studentId) {
    message.info('该订单学员未匹配到本地学员档案')
    return
  }
  router.push({ path: `/studentDetail/${row.studentId}`, query: { tab: 'order' } })
}

// 交易内容默认折叠，点击展开/收起
const expandedContentIds = ref<Set<number>>(new Set<number>())

function isContentExpandable(content?: string | null) {
  return (content ?? '').length > 24
}

function isContentExpanded(id: number) {
  return expandedContentIds.value.has(id)
}

function toggleContent(row: SpOrderRow) {
  if (!isContentExpandable(row.content)) return
  if (expandedContentIds.value.has(row.id)) {
    expandedContentIds.value.delete(row.id)
  } else {
    expandedContentIds.value.add(row.id)
  }
}

function showTotal(count: number) {
  return `共 ${count} 条`
}

function onRowMenuChange(id: number, visible: boolean) {
  openMenuId.value = visible ? id : null
}

function toggleRowMenu(id: number) {
  openMenuId.value = openMenuId.value === id ? null : id
}

async function copyOrderNo(orderNo: string) {
  try {
    await navigator.clipboard.writeText(orderNo)
    message.success('订单号已复制')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = orderNo
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('订单号已复制')
  }
  openMenuId.value = null
}

/** 打印收据：优先由后端直发 58mm 小票机；设备不可用时退回浏览器小票预览。 */
async function printReceipt(row: SpOrderRow) {
  openMenuId.value = null
  let order = row
  try {
    order = await fetchSpOrderDetail(row.id)
  } catch {
    // 详情接口失败时退回列表行数据，保证仍可打印
  }
  try {
    await printOrderReceipt(order.id)
    message.success('小票已发送到小票打印机')
    return
  } catch (error) {
    message.warning(`${(error as Error).message}，已切换为浏览器打印预览`)
  }
  const escape = (value: string | null | undefined) =>
    String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
  const money = (value: number) => `￥${absMoney(value)}`
  const amountLabel = order.isReturn ? '实退金额' : '实收金额'
  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<title>小票 ${escape(order.orderNo)}</title>
<style>
  @page { size: 58mm auto; margin: 0; }
  * { box-sizing: border-box; }
  body { width: 58mm; margin: 0 auto; padding: 4mm 2.5mm 6mm; font-family: "Courier New", Consolas, "Microsoft YaHei", monospace; font-size: 11px; line-height: 1.45; color: #000; }
  .center { text-align: center; }
  .shop { font-size: 15px; font-weight: 700; letter-spacing: 1px; }
  .doc { font-size: 12px; margin-top: 2px; }
  .meta { font-size: 10px; margin-top: 2px; }
  hr { border: 0; border-top: 1px dashed #000; margin: 5px 0; }
  .row { display: flex; justify-content: space-between; gap: 6px; }
  .row .v { text-align: right; word-break: break-all; }
  .block { margin: 3px 0; word-break: break-all; }
  .total { display: flex; justify-content: space-between; font-size: 14px; font-weight: 700; margin: 4px 0; }
  .foot { text-align: center; font-size: 10px; margin-top: 6px; }
  .thanks { text-align: center; font-size: 12px; margin-top: 4px; }
</style>
</head>
<body>
  <div class="center">
    <div class="shop">${escape(order.campusName) || '幻彩教育'}</div>
    <div class="doc">收 费 小 票</div>
    <div class="meta">单号 ${escape(order.orderNo)}</div>
  </div>
  <hr />
  <div class="row"><span>学员</span><span class="v">${escape(order.stuName)}</span></div>
  <div class="row"><span>电话</span><span class="v">${escape(order.stuTel) || '-'}</span></div>
  <div class="row"><span>类型</span><span class="v">${escape(order.typeName)}</span></div>
  <div class="row"><span>状态</span><span class="v">${escape(order.statusName)}</span></div>
  <hr />
  <div class="block">${escape(order.content) || '-'}</div>
  <hr />
  <div class="row"><span>应收金额</span><span class="v">${money(order.shouldPay)}</span></div>
  <div class="total"><span>${amountLabel}</span><span>${money(order.realPay)}</span></div>
  <hr />
  <div class="row"><span>账户变动</span><span class="v">${order.remain > 0 ? '+' : ''}${fixedMoney(order.remain)}</span></div>
  <div class="row"><span>欠费</span><span class="v">${fixedMoney(order.arrearage)}</span></div>
  <div class="row"><span>积分变动</span><span class="v">${order.credit > 0 ? '+' : ''}${fixedMoney(order.credit)}</span></div>
  <hr />
  <div class="row"><span>经办人</span><span class="v">${escape(order.creator) || '-'}</span></div>
  <div class="row"><span>支付方式</span><span class="v">${escape(order.payMethod) || '-'}</span></div>
  <div class="row"><span>经办日期</span><span class="v">${escape(dateText(order.dealDate))}</span></div>
  ${escape(order.commentOuter) ? `<hr /><div class="block">备注：${escape(order.commentOuter)}</div>` : ''}
  <hr />
  <div class="thanks">谢谢惠顾，欢迎再次光临</div>
  <div class="foot">打印时间 ${escape(new Date().toLocaleString('zh-CN'))}</div>
  <div class="foot">经办人签字：____________</div>
</body>
</html>`
  const win = window.open('', '_blank', 'width=420,height=760')
  if (!win) {
    message.error('浏览器拦截了打印窗口，请允许弹出窗口后重试')
    return
  }
  win.document.open()
  win.document.write(html)
  win.document.close()
  win.focus()
  setTimeout(() => win.print(), 300)
}

/** 作废订单：弹窗确认并填写原因，提交后只改本地状态，不写校宝。 */
function openVoidModal(row: SpOrderRow) {
  openMenuId.value = null
  voidTarget.value = row
  voidReason.value = ''
  voidOpen.value = true
}

async function submitVoid() {
  const target = voidTarget.value
  if (!target) return
  const reason = voidReason.value.trim()
  if (reason.length < 5) {
    message.warning('请填写 5～100 字的作废原因')
    return
  }
  voidSubmitting.value = true
  try {
    await voidSpOrder(target.id, reason)
    message.success('订单已作废')
    voidOpen.value = false
    voidTarget.value = null
    voidReason.value = ''
    load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '订单作废失败')
  } finally {
    voidSubmitting.value = false
  }
}

/** 导出当日订单：固定导出今天，版式沿用 26-11-8.xlsx 日结表。 */
async function doDailyExport() {
  const today = dateToText(new Date())
  const [year, month, day] = today.split('-').map(Number)
  exportingDaily.value = true
  try {
    const blob = await downloadDailySettlement(today)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${String(year).slice(2)}-${month}-${day}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    message.success('已导出当日订单')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '导出失败')
  } finally {
    exportingDaily.value = false
  }
}

onMounted(() => {
  loadOptions()
  load()
})
</script>

<template>
  <div class="order-page">
    <div class="top-section">
      <div class="quick-actions">
        <button
          v-for="action in QUICK_ACTIONS"
          :key="action.label"
          type="button"
          class="quick-item"
          @click="onQuickAction(action)"
        >
          <span class="quick-icon" :style="{ background: action.bg, color: action.color }">
            <component :is="action.icon" />
          </span>
          <span class="quick-text">{{ action.label }}</span>
        </button>
      </div>

      <div class="scope-row">
        <button
          type="button"
          class="scope-btn"
          :class="{ active: scope === 'mine' }"
          @click="setTodayScope('mine')"
        >
          我的今日
        </button>
        <button
          type="button"
          class="scope-btn"
          :class="{ active: scope === 'school' }"
          @click="setTodayScope('school')"
        >
          校区今日
        </button>
      </div>

      <div class="date-bar">
        <span class="date-bar-label">经办日期</span>
        <button
          v-for="preset in DATE_PRESETS"
          :key="preset.key"
          type="button"
          class="date-chip"
          :class="{ active: datePreset === preset.key }"
          @click="applyDatePreset(preset.key)"
        >
          {{ preset.label }}
        </button>
        <span class="date-bar-divider" />
        <div class="date-custom">
          <input v-model="dateStartDraft" type="date" class="date-input" />
          <span class="date-split">~</span>
          <input v-model="dateEndDraft" type="date" class="date-input" />
          <button type="button" class="date-apply" @click="applyCustomDate">应用</button>
        </div>
        <span class="date-current">{{ dateRange ? `${dateRange[0]} 至 ${dateRange[1]}` : '全部日期' }}</span>
      </div>

      <div class="filter-zone">
        <div class="filter-row">
          <div v-for="field in filterGroups" :key="field.key" class="filter-item">
            <OrderFilterPopover
              :label="field.label"
              :open="openFilter === field.key"
              :type="field.type"
              :options="field.options"
              :model-value="field.value"
              :date-value="field.dateValue"
              :searchable="field.searchable"
              @update:open="(visible) => setFilterOpen(field.key, visible)"
              @apply="(values) => onFilterApply(field, values)"
              @apply-date="onDateApply"
            />
            <span
              v-if="field.linked"
              class="filter-link"
              title="该筛选项关联学员在校课程 / 售前跟进记录"
            >
              <LinkOutlined />
            </span>
          </div>

          <div class="search-box">
            <a-select
              v-model:value="keywordType"
              :options="SEARCH_OPTIONS"
              size="small"
              :bordered="false"
              class="search-type"
              @change="onSearchTypeChange"
            />
            <span class="search-divider" />
            <input
              v-model="searchDraft"
              class="search-input"
              :placeholder="searchPlaceholder"
              @keyup.enter="applySearch"
            />
            <button type="button" class="search-btn" title="搜索" @click="applySearch">
              <SearchOutlined />
            </button>
          </div>
        </div>
      </div>

      <div v-if="chipGroups.length" class="chips-row">
        <div class="chips">
          <div v-for="group in chipGroups" :key="group.key" class="chip-group">
            <span class="chip-label">{{ group.label }}：</span>
            <span v-for="chip in group.chips" :key="chip.key" class="chip">
              {{ chip.label }}
              <CloseOutlined class="chip-close" @click="removeChip(group.key, chip.key)" />
            </span>
          </div>
        </div>
        <a class="clear-filter" @click="clearFilters">清空筛选</a>
      </div>
    </div>

    <div class="section-gap" />

    <div class="bottom-section">
      <div class="toolbar">
        <button
          type="button"
          class="export-btn"
          :disabled="exportingDaily"
          @click="doDailyExport"
        >
          {{ exportingDaily ? '导出中...' : '导出当日订单' }}
        </button>
        <a-popover
          v-model:open="showColumns"
          trigger="click"
          placement="bottomRight"
          :overlay-style="{ zIndex: 1100 }"
        >
          <template #content>
            <div class="column-panel">
              <div class="column-panel-head">
                <span>显示列</span>
                <a @click="resetColumns">恢复默认</a>
              </div>
              <div class="column-panel-list">
                <a-checkbox
                  v-for="column in ALL_COLUMNS.filter((item) => item.key !== 'action')"
                  :key="column.key"
                  class="column-check"
                  :checked="!hiddenKeys.includes(column.key)"
                  @change="setColumnVisible(column.key, $event.target.checked)"
                >
                  {{ column.title }}
                </a-checkbox>
              </div>
              <div class="column-panel-tip">勾选后立即生效，选择保存在本机</div>
            </div>
          </template>
          <button type="button" class="column-btn">自定义显示列</button>
        </a-popover>
      </div>

      <div class="summary">
        <span>当前结果：总计&nbsp;<b>{{ total }}</b>&nbsp;条记录，应收款项总计：</span>
        <b>{{ fixedMoney(summary.shouldPay) }}</b>
        <span class="summary-sep">|</span>
        <span>实收总计：</span>
        <b>{{ fixedMoney(summary.realPay) }}</b>
        <span class="summary-sep">|</span>
        <span>学员账户变动总计：</span>
        <b>{{ signedMoney(summary.remain) }}</b>
        <span class="summary-sep">|</span>
        <span>欠费总计：</span>
        <b>{{ fixedMoney(summary.arrearage) }}</b>
        <span class="summary-sep">|</span>
        <span>积分总计：</span>
        <b>{{ fixedMoney(summary.credit) }}</b>
        <span>&nbsp;分</span>
      </div>

      <div class="table-wrap">
        <a-table
          class="order-table"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="false"
          row-key="id"
          size="small"
          :scroll="{ x: tableWidth }"
          @change="onTableChange"
        >
          <template #headerCell="{ column }">
            <template v-if="typeof column.title === 'string' && column.title">
              <span>{{ column.title }}</span>
            </template>
          </template>

          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'orderNo'">
              <span class="order-no" @click="openDetail(record)">{{ record.orderNo }}</span>
            </template>
            <template v-else-if="column.key === 'stuName'">
              <a class="link-name" @click.prevent="openStudent(record)">{{ record.stuName }}</a>
            </template>
            <template v-else-if="column.key === 'typeName'">
              <span>{{ record.typeName }}</span>
            </template>
            <template v-else-if="column.key === 'content'">
              <div
                class="content-cell"
                :class="{ expanded: isContentExpanded(record.id), expandable: isContentExpandable(record.content) }"
                @click="toggleContent(record)"
              >
                <span class="content-text">{{ record.content || '-' }}</span>
                <span v-if="isContentExpandable(record.content)" class="content-toggle">
                  {{ isContentExpanded(record.id) ? '收起' : '展开' }}
                </span>
              </div>
            </template>
            <template v-else-if="column.key === 'shouldPay'">
              <span class="amount-pill" :class="record.isReturn ? 'ret' : 'rec'">
                {{ record.isReturn ? '退' : '收' }} {{ absMoney(record.shouldPay) }}
              </span>
            </template>
            <template v-else-if="column.key === 'realPay'">
              <span class="amount-pill" :class="record.isReturn ? 'ret' : 'rec'">
                {{ record.isReturn ? '退' : '收' }} {{ absMoney(record.realPay) }}
              </span>
            </template>
            <template v-else-if="column.key === 'remain'">
              <span :class="{ neg: Number(record.remain) < 0 }">{{ signedMoney(record.remain) }}</span>
            </template>
            <template v-else-if="column.key === 'arrearage'">
              <span :class="{ neg: Number(record.arrearage) > 0 }">
                {{ fixedMoney(record.arrearage) }}
              </span>
            </template>
            <template v-else-if="column.key === 'credit'">
              <span :class="{ neg: Number(record.credit) < 0 }">{{ signedMoney(record.credit) }}</span>
            </template>
            <template v-else-if="column.key === 'tags'">
              <span v-if="record.payMethod" class="order-tag">{{ record.payMethod }}</span>
              <span v-else class="muted-cell">-</span>
            </template>
            <template v-else-if="column.key === 'sourceName'">
              <span>{{ record.sourceName }}</span>
            </template>
            <template v-else-if="column.key === 'salesSource'">
              <span>{{ record.salesSource || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'campusName'">
              <span>{{ record.campusName || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'dealDate'">
              <span>{{ dateText(record.dealDate) }}</span>
            </template>
            <template v-else-if="column.key === 'creator'">
              <span>{{ record.creator || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'comment'">
              <span class="remark-cell" :title="record.comment || ''">{{ record.comment || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'commentOuter'">
              <span class="remark-cell" :title="record.commentOuter || ''">
                {{ record.commentOuter || '-' }}
              </span>
            </template>
            <template v-else-if="column.key === 'hasConfirmedDetail'">
              <span class="status-pill" :class="statusClass(record.statusCode)">
                {{ record.statusName }}
              </span>
            </template>
            <template v-else-if="column.key === 'operateTime'">
              <span>{{ dateTimeText(record.createdTime) }}</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-popover
                :open="openMenuId === record.id"
                trigger="click"
                placement="bottomRight"
                :overlay-style="{ zIndex: 1100 }"
                @open-change="(visible: boolean) => onRowMenuChange(record.id, visible)"
              >
                <span class="more-link" @click.stop="toggleRowMenu(record.id)">
                  操作<DownOutlined class="more-arrow" />
                </span>
                <template #content>
                  <div class="row-action-panel">
                    <div class="row-action" @click="openDetail(record)">
                      <FileTextOutlined /> 查看详情
                    </div>
                    <div class="row-action" @click="copyOrderNo(record.orderNo)">
                      <CopyOutlined /> 复制订单号
                    </div>
                    <div class="row-action" @click="printReceipt(record)">
                      <PrinterOutlined /> 打印收据
                    </div>
                    <div
                      v-if="record.statusCode !== 3"
                      class="row-action danger"
                      @click="openVoidModal(record)"
                    >
                      <StopOutlined /> 作废订单
                    </div>
                  </div>
                </template>
              </a-popover>
            </template>
          </template>

          <template #emptyText>
            <div class="table-empty">很抱歉，这里什么都没有</div>
          </template>
        </a-table>

        <div v-if="total > 0" class="pager">
          <a-pagination
            :current="pageIndex"
            :page-size="pageSize"
            :total="total"
            :show-size-changer="true"
            :page-size-options="['50', '100', '200']"
            :show-total="showTotal"
            size="small"
            @change="onPageChange"
          />
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="detailOpen"
      title="订单详情"
      :width="820"
      :footer="null"
      :mask-closable="true"
    >
      <a-spin :spinning="detailLoading">
        <div v-if="detail" class="order-detail">
          <div class="detail-head">
            <span class="detail-order">{{ detail.orderNo }}</span>
            <span class="status-pill" :class="statusClass(detail.statusCode)">
              {{ detail.statusName }}
            </span>
            <span class="detail-type">{{ detail.typeName }}</span>
          </div>

          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">学员姓名</span>
              <span>{{ detail.stuName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">联系方式</span>
              <span>{{ detail.stuTel || '-' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">经办校区</span>
              <span>{{ detail.campusName || '-' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">经办日期</span>
              <span>{{ dateText(detail.dealDate) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">经办人</span>
              <span>{{ detail.creator || '-' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">销售员</span>
              <span>{{ detail.salesman || '-' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">订单来源</span>
              <span>{{ detail.sourceName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">销售来源</span>
              <span>{{ detail.salesSource || '-' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">应收/应退</span>
              <span>
                <span class="amount-tag" :class="detail.isReturn ? 'ret' : 'rec'">
                  {{ detail.isReturn ? '退' : '收' }}
                </span>
                {{ absMoney(detail.shouldPay) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">实收/实退</span>
              <span>
                <span class="amount-tag" :class="detail.isReturn ? 'ret' : 'rec'">
                  {{ detail.isReturn ? '退' : '收' }}
                </span>
                {{ absMoney(detail.realPay) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">学员账户变动</span>
              <span :class="{ neg: Number(detail.remain) < 0 }">{{ signedMoney(detail.remain) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">欠费</span>
              <span :class="{ neg: Number(detail.arrearage) > 0 }">
                {{ fixedMoney(detail.arrearage) }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">积分</span>
              <span :class="{ neg: Number(detail.credit) < 0 }">{{ fixedMoney(detail.credit) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">到款状态</span>
              <span :class="detail.hasConfirmedDetail ? 'confirmed-cell' : 'muted-cell'">
                {{ detail.hasConfirmedDetail ? '已确认' : '未确认' }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">创建时间</span>
              <span>{{ dateTimeText(detail.createdTime) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">到期时间</span>
              <span>{{ dateTimeText(detail.expiryDate) }}</span>
            </div>
            <div class="detail-item detail-span">
              <span class="detail-label">订单标签</span>
              <span>
                <template v-if="splitTags(detail.tags).length">
                  <span v-for="tag in splitTags(detail.tags)" :key="tag" class="order-tag">
                    {{ tag }}
                  </span>
                </template>
                <span v-else>-</span>
              </span>
            </div>
          </div>

          <div v-if="detail.voidReason" class="detail-block">
            <span class="detail-label">作废原因</span>
            <div class="detail-content void-reason-text">{{ detail.voidReason }}</div>
          </div>
          <div v-if="detail.voidAt" class="detail-block">
            <span class="detail-label">作废时间</span>
            <div class="detail-content">{{ dateTimeText(detail.voidAt) }}</div>
          </div>

          <div class="detail-block">
            <span class="detail-label">交易内容</span>
            <div class="detail-content">{{ detail.content || '-' }}</div>
          </div>
          <div class="detail-block">
            <span class="detail-label">对内备注</span>
            <div class="detail-content">{{ detail.comment || '-' }}</div>
          </div>
          <div class="detail-block">
            <span class="detail-label">对外备注</span>
            <div class="detail-content">{{ detail.commentOuter || '-' }}</div>
          </div>

          <div class="detail-foot">
            <a-button type="primary" @click="detailOpen = false">关闭</a-button>
          </div>
        </div>
      </a-spin>
    </a-modal>

    <a-modal
      v-model:open="voidOpen"
      title="作废订单"
      :width="720"
      ok-text="确定作废"
      cancel-text="取消"
      :confirm-loading="voidSubmitting"
      :mask-closable="false"
      @ok="submitVoid"
    >
      <div class="void-alert">
        <ExclamationCircleFilled class="void-alert-icon" />
        <span>请确认是否作废订单，作废后将不可还原。</span>
      </div>
      <a-textarea
        v-model:value="voidReason"
        class="void-reason"
        :maxlength="100"
        :rows="4"
        placeholder="请输入订单作废原因（5～100个字）"
      />
      <div class="void-counter">您还可以输入{{ voidRemaining }}字</div>
    </a-modal>

    <a-modal
      v-model:open="studentPickOpen"
      title="选择学员"
      :width="560"
      ok-text="下一步"
      cancel-text="取消"
      :confirm-loading="studentPickLoading"
      :ok-button-props="{ disabled: !studentPickId }"
      @ok="confirmStudentPick"
    >
      <div class="student-pick-tip">
        办理「{{ studentPickAction?.label }}」请先选择学员，随后跳转到该学员的对应功能。
      </div>
      <a-input-search
        v-model:value="studentPickKeyword"
        placeholder="输入学员姓名或手机号搜索"
        allow-clear
        enter-button="搜索"
        @search="searchPickStudents"
      />
      <a-spin :spinning="studentPickLoading">
        <div class="student-pick-list">
          <button
            v-for="item in studentPickRows"
            :key="item.id"
            type="button"
            class="student-pick-item"
            :class="{ active: item.id === studentPickId }"
            @click="studentPickId = item.id"
          >
            <span class="student-pick-name">{{ item.name }}</span>
            <span class="student-pick-info">
              <span class="student-pick-meta">
                {{ item.phone || '—' }} · {{ item.campusText || item.campusName || '—' }} ·
                {{ item.status }}
              </span>
              <span v-if="studentPickAction?.mode === 'subsidy'" class="student-pick-debt">
                欠费 ￥{{ Number(item.debt ?? 0).toFixed(2) }} ·
                {{ Number(item.debtLessons ?? 0).toFixed(1) }} 课时
              </span>
            </span>
          </button>
          <a-empty v-if="!studentPickRows.length && !studentPickLoading" description="未找到学员" />
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.student-pick-tip {
  margin-bottom: 12px;
  color: #646a73;
  font-size: 13px;
}

.student-pick-list {
  max-height: 320px;
  margin-top: 12px;
  overflow-y: auto;
}

.student-pick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 6px;
  text-align: left;
  background: #f7f9fb;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
}

.student-pick-item:hover {
  border-color: #ffb27a;
}

.student-pick-item.active {
  background: #fff7e6;
  border-color: #ff8a00;
}

.student-pick-name {
  font-weight: 500;
  color: #1f2329;
}

.student-pick-meta {
  color: #8a9099;
  font-size: 12px;
}

.student-pick-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.student-pick-debt {
  color: #d4380d;
  font-size: 12px;
  font-weight: 500;
}
.order-page {
  min-height: 100%;
  background: #f4f6f9;
  color: #1f2329;
}

.top-section {
  background: #fff;
  padding: 6px 20px 0;
}

.quick-actions {
  display: flex;
  align-items: flex-start;
  gap: 46px;
  padding: 10px 0 14px;
  overflow-x: auto;
  scrollbar-width: none;
}

.quick-actions::-webkit-scrollbar {
  display: none;
}

.quick-item {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  height: 80px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.quick-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  transition: transform 0.15s;
}

.quick-item:hover .quick-icon {
  transform: translateY(-1px);
}

.quick-text {
  color: #30404f;
  font-size: 12px;
  line-height: 17px;
  white-space: nowrap;
}

.scope-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0 8px;
}

.scope-btn {
  height: 32px;
  padding: 0 15px;
  border: 1px solid #d6e4ff;
  border-radius: 4px;
  background: #fff;
  color: #007aff;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 2px 0 0 rgba(0, 0, 0, 0.043);
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.scope-btn:hover {
  border-color: #007aff;
  background: #f3f8ff;
}

.scope-btn.active {
  border-color: #007aff;
  background: #007aff;
  color: #fff;
}

.scope-btn.active:hover {
  background: #0062cc;
}

.date-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid #eef1f5;
  border-radius: 6px;
  background: #fafbfc;
}

.date-bar-label {
  margin-right: 2px;
  color: #30404f;
  font-size: 13px;
  font-weight: 600;
}

.date-chip {
  height: 28px;
  padding: 0 12px;
  border: 1px solid #e3e8ef;
  border-radius: 14px;
  background: #fff;
  color: #4e5969;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.date-chip:hover {
  border-color: #007aff;
  color: #007aff;
}

.date-chip.active {
  border-color: #007aff;
  background: #007aff;
  color: #fff;
}

.date-bar-divider {
  width: 1px;
  height: 18px;
  margin: 0 2px;
  background: #e5e6eb;
}

.date-custom {
  display: flex;
  align-items: center;
  gap: 5px;
}

.date-input {
  width: 126px;
  height: 28px;
  padding: 0 7px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #1f2329;
  font-size: 12px;
  outline: none;
}

.date-input:focus {
  border-color: #007aff;
}

.date-split {
  color: #8f959e;
  font-size: 12px;
}

.date-apply {
  height: 28px;
  padding: 0 12px;
  border: 1px solid #007aff;
  border-radius: 4px;
  background: #fff;
  color: #007aff;
  font-size: 12px;
  cursor: pointer;
}

.date-apply:hover {
  background: #007aff;
  color: #fff;
}

.date-current {
  margin-left: auto;
  color: #8f959e;
  font-size: 12px;
}

.filter-zone {
  position: relative;
  padding-bottom: 2px;
}

.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-height: 30px;
}

.filter-row:first-child {
  padding-right: 306px;
}

.filter-item {
  display: flex;
  align-items: center;
}

.filter-link {
  margin-left: 1px;
  color: #bfbfbf;
  font-size: 12px;
  cursor: help;
}

.search-box {
  position: absolute;
  right: 0;
  top: 0;
  display: flex;
  align-items: center;
  width: 290px;
  height: 32px;
  padding-left: 10px;
  border: 0.8px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  transition: border-color 0.15s;
}

.search-box:focus-within {
  border-color: #007aff;
}

.search-type {
  width: 88px;
  flex: 0 0 88px;
  font-size: 14px;
  color: #646a73;
}

.search-type :deep(.ant-select-selector) {
  padding: 0 !important;
  background: transparent !important;
}

.search-type :deep(.ant-select-selection-item) {
  color: #646a73;
  font-size: 14px;
}

.search-divider {
  width: 1px;
  height: 14px;
  background: #e5e6eb;
  margin-right: 8px;
}

.search-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  color: #1f2329;
  font-size: 12px;
  background: transparent;
}

.search-input::placeholder {
  color: #bfbfbf;
}

.search-btn {
  width: 30px;
  height: 28px;
  flex: 0 0 30px;
  border: 0;
  background: transparent;
  color: #8f959e;
  font-size: 14px;
  cursor: pointer;
}

.search-btn:hover {
  color: #007aff;
}

.chips-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-height: 38px;
  padding: 6px 0 10px;
}

.chips {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
}

.chip-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.chip-label {
  color: #67757c;
  font-size: 12px;
  white-space: nowrap;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 20px;
  padding: 0;
  color: #30404f;
  font-size: 12px;
  white-space: nowrap;
}

.chip-close {
  color: #8f959e;
  font-size: 9px;
  cursor: pointer;
}

.chip-close:hover {
  color: #67757c;
}

.clear-filter {
  margin-left: auto;
  color: #007aff;
  font-size: 12px;
  white-space: nowrap;
}

.section-gap {
  height: 8px;
  background: #f4f6f9;
}

.bottom-section {
  background: #fff;
}

.toolbar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 20px;
  border-bottom: 1px solid #f0f1f3;
}

.export-btn {
  height: 24px;
  margin-right: 8px;
  padding: 0 7px;
  border: 0.8px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 2px 0 0 rgba(0, 0, 0, 0.016);
  transition: border-color 0.15s, color 0.15s;
}

.export-btn:hover:not(:disabled) {
  border-color: #007aff;
  color: #007aff;
}

.export-btn:disabled {
  color: #bfbfbf;
  cursor: not-allowed;
}

.column-btn {
  height: 24px;
  padding: 0 7px;
  border: 0.8px solid #007aff;
  border-radius: 4px;
  background: #007aff;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  box-shadow: 0 2px 0 0 rgba(0, 0, 0, 0.043);
  transition: background 0.15s;
}

.column-btn:hover {
  background: #3395ff;
}

.summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  padding: 10px 20px;
  border-bottom: 1px solid #d4dfe5;
  color: #1f2329;
  font-size: 13px;
  line-height: 20px;
}

.summary b {
  color: #007aff;
  font-weight: 600;
}

.summary-sep {
  margin: 0 10px;
  color: #d9d9d9;
}

.table-wrap {
  padding: 0 12px;
}

.order-table :deep(.ant-table) {
  color: #1f2329;
  font-size: 13px;
}

.order-table :deep(.ant-table-thead > tr > th) {
  height: 42px;
  padding: 7px 6px;
  border-bottom: 1px solid #f0f1f3;
  background: #fff;
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  font-weight: 400;
  white-space: nowrap;
}

.order-table :deep(.ant-table-thead > tr > th.ant-table-cell-fix-left),
.order-table :deep(.ant-table-thead > tr > th.ant-table-cell-fix-right) {
  background: #fff;
}

.order-table :deep(.ant-table-tbody > tr > td) {
  padding: 9px 6px;
  border-bottom: 1px solid #d4dfe5;
  color: #67757c;
  vertical-align: middle;
}

.order-table :deep(.ant-table-tbody > tr:hover > td) {
  background: #f7faff;
}

.order-table :deep(.ant-table-tbody > tr > td.ant-table-cell-fix-left),
.order-table :deep(.ant-table-tbody > tr > td.ant-table-cell-fix-right) {
  background: #fff;
}

.order-table :deep(.ant-table-tbody > tr:hover > td.ant-table-cell-fix-left),
.order-table :deep(.ant-table-tbody > tr:hover > td.ant-table-cell-fix-right) {
  background: #f7faff;
}

.order-table :deep(.ant-table-column-sorter-up.active),
.order-table :deep(.ant-table-column-sorter-down.active) {
  color: #007aff;
}

.th-help {
  margin-left: 3px;
  color: #007aff;
  font-size: 12px;
  vertical-align: 1px;
  cursor: help;
}

.order-no {
  color: #67757c;
  cursor: pointer;
  white-space: nowrap;
}

.order-no:hover {
  color: #007aff;
}

.link-name {
  color: #67757c;
}

.link-name:hover {
  color: #007aff;
  text-decoration: underline;
}

.content-cell {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  line-height: 20px;
}

.content-cell.expandable {
  cursor: pointer;
}

.content-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  word-break: break-all;
}

.content-cell.expanded .content-text {
  white-space: normal;
  overflow: visible;
}

.content-toggle {
  flex-shrink: 0;
  font-size: 12px;
  color: #1677ff;
  user-select: none;
}

.amount-pill {
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 5px;
  border: 1px solid transparent;
  border-radius: 2px;
  font-size: 12px;
  line-height: 16px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.amount-pill.rec {
  border-color: #60bab2;
  background: #ddf8f6;
  color: #60bab2;
}

.amount-pill.ret {
  border-color: #f56c6c;
  background: #fef0f0;
  color: #f56c6c;
}

.neg {
  color: #f5222d;
}

.muted-cell {
  color: #bfbfbf;
}

.confirmed-cell {
  color: #52c41a;
}

.remark-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.status-pill {
  display: inline-block;
  height: 20px;
  padding: 0 6px;
  border-radius: 2px;
  font-size: 12px;
  line-height: 20px;
}

.status-pending {
  background: #fff7e6;
  color: #fa8c16;
}

.status-done {
  background: #f6ffed;
  color: #52c41a;
}

.status-void {
  background: #f5f5f5;
  color: #8c8c8c;
}

.status-expired {
  background: #fff1f0;
  color: #ff4d4f;
}

.order-tag {
  display: inline-block;
  margin: 1px 4px 1px 0;
  padding: 0 5px;
  border: 1px solid #d9e6ff;
  border-radius: 2px;
  background: #f2f7ff;
  color: #007aff;
  font-size: 11px;
  line-height: 18px;
}

.more-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: #007aff;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}

.more-arrow {
  font-size: 9px;
}

.row-action-panel {
  width: 148px;
  padding: 2px 0;
}

.row-action {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 8px;
  border-radius: 3px;
  color: #1f2329;
  font-size: 13px;
  cursor: pointer;
}

.row-action:hover {
  background: #f5f8ff;
  color: #007aff;
}

.row-action.disabled {
  color: #bfbfbf;
}

.row-action.danger:hover {
  background: #fff2f0;
  color: #ff4d4f;
}

.void-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 4px;
  background: #fffbe6;
  color: #1f2329;
  font-size: 13px;
}

.void-alert-icon {
  color: #faad14;
  font-size: 15px;
}

.void-reason {
  margin-top: 16px;
}

.void-counter {
  margin-top: 6px;
  color: #8f959e;
  font-size: 13px;
}

.table-empty {
  padding: 42px 0;
  color: #8f959e;
  font-size: 13px;
  text-align: center;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 18px;
}

.column-panel {
  width: 246px;
}

.column-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  border-bottom: 1px solid #f0f1f3;
  font-size: 13px;
  font-weight: 500;
}

.column-panel-list {
  display: flex;
  flex-direction: column;
  max-height: 330px;
  overflow-y: auto;
  padding: 6px 2px;
}

.column-check {
  height: 28px;
  margin-right: 0;
  font-size: 13px;
}

.column-panel-tip {
  padding: 6px 4px 0;
  color: #8f959e;
  font-size: 11px;
}

.order-detail {
  font-size: 13px;
  color: #1f2329;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.detail-order {
  font-size: 17px;
  font-weight: 600;
}

.detail-type {
  color: #646a73;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 28px;
  padding: 14px 16px;
  border-radius: 4px;
  background: #fafbfc;
}

.detail-item {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.detail-label {
  flex: 0 0 88px;
  color: #8f959e;
}

.detail-item > span:last-child {
  min-width: 0;
  color: #1f2329;
  word-break: break-all;
}

.detail-span {
  grid-column: 1 / -1;
}

.detail-block {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.detail-content {
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #f0f1f3;
  border-radius: 4px;
  background: #fff;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-all;
}

.void-reason-text {
  color: #cf1322;
}

.detail-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.order-item-panel {
  padding: 4px 8px 4px 48px;
}

.order-item-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.order-item-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.order-item-course {
  min-width: 120px;
  font-weight: 600;
}

.order-item-tag {
  padding: 1px 8px;
  border-radius: 10px;
  background: #eef4ff;
  color: #1d4ed8;
}

.order-item-tag.empty {
  background: #f4f4f5;
  color: #8f959e;
}

.order-item-meta {
  color: #646a73;
}

.order-item-empty {
  color: #8f959e;
  font-size: 13px;
}

@media (max-width: 1200px) {
  .quick-actions {
    gap: 24px;
  }

  .search-box {
    width: 260px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-span {
    grid-column: auto;
  }
}
</style>
