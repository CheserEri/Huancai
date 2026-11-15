<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { DownOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import FilterBar from '../components/FilterBar.vue'
import {
  batchCourses,
  createCourse,
  createCourseCategory,
  createCourseSubject,
  createCourseUpgrade,
  createPriceTemplate,
  deleteCourse,
  deleteCourseCategory,
  deleteCourseSubject,
  deleteCourseUpgrade,
  fetchCourseCategories,
  fetchCourseClasses,
  fetchCourseDetail,
  fetchCourseOptions,
  fetchCoursePage,
  fetchCourseSubjects,
  fetchCourseSummary,
  fetchCourseUpgrades,
  fetchPriceTemplateOptions,
  renameCourseCategory,
  updateCourse,
  type CourseCategoryRow,
  type CourseClassRow,
  type CourseFormPayload,
  type CourseOptions,
  type CourseRow,
  type CourseSubjectRow,
  type CourseSummary,
  type CourseUpgradeRow,
  type PriceTemplateFormPayload,
  type PriceTemplateOptions,
} from '../api/client'

type TabKey = 'list' | 'upgrade' | 'category' | 'subject'

const tab = ref<TabKey>('list')

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'list', label: '课程列表' },
  { key: 'upgrade', label: '升期关系列表' },
  { key: 'category', label: '课程类别' },
  { key: 'subject', label: '科目设置' },
]

// ---------------- 通用下拉选项 ----------------------------
const courseOptions = ref<CourseOptions>({ campuses: [], categories: [], subjects: [] })
const allCourses = ref<Array<{ id: number; name: string }>>([])

async function loadOptions() {
  courseOptions.value = await fetchCourseOptions().catch(() => courseOptions.value)
}

async function loadAllCourses() {
  const page = await fetchCoursePage({ page: 1, size: 200 }).catch(() => null)
  allCourses.value = page ? page.records.map((row) => ({ id: row.id, name: row.name })) : []
}

// ---------------- 课程列表 ----------------------------
const keyword = ref('')
const campusId = ref<number | undefined>(undefined)
const category = ref<string | undefined>(undefined)
const subject = ref<string | undefined>(undefined)
const lessonMode = ref<string | undefined>(undefined)
const chargeMode = ref<string | undefined>(undefined)
const status = ref<number | undefined>(undefined)

const sortField = ref<string | undefined>(undefined)
const sortOrder = ref<string | undefined>(undefined)

const rows = ref<CourseRow[]>([])
const summary = ref<CourseSummary | null>(null)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedKeys = ref<number[]>([])

const expandedKeys = ref<number[]>([])
const classMap = ref<Record<number, CourseClassRow[]>>({})
const classLoading = ref<Record<number, boolean>>({})

const visibleOptional = ref<string[]>([])

const optionalColumns = [
  { key: 'subject', title: '科目' },
  { key: 'remark', title: '备注' },
]

function listQuery() {
  return {
    keyword: keyword.value.trim() || undefined,
    campusIds: campusId.value === undefined ? undefined : String(campusId.value),
    categories: category.value || undefined,
    subjects: subject.value || undefined,
    lessonMode: lessonMode.value || undefined,
    chargeMode: chargeMode.value || undefined,
    status: status.value,
  }
}

async function loadList() {
  loading.value = true
  try {
    const base = listQuery()
    const [list, sum] = await Promise.all([
      fetchCoursePage({
        ...base,
        sortField: sortField.value,
        sortOrder: sortOrder.value,
        page: page.value,
        size: pageSize.value,
      }),
      fetchCourseSummary(base),
    ])
    rows.value = list.records
    total.value = list.total
    summary.value = sum
  } catch (e) {
    console.error(e)
    message.error('课程数据加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  selectedKeys.value = []
  expandedKeys.value = []
  loadList()
}

function resetFilters() {
  keyword.value = ''
  campusId.value = undefined
  category.value = undefined
  subject.value = undefined
  lessonMode.value = undefined
  chargeMode.value = undefined
  status.value = undefined
  sortField.value = undefined
  sortOrder.value = undefined
  search()
}

const activeChips = computed(() => {
  const chips: Array<{ key: string; label: string }> = []
  if (campusId.value !== undefined) {
    const campus = courseOptions.value.campuses.find((item) => item.id === campusId.value)
    chips.push({ key: 'campus', label: `开课校区：${campus?.name ?? campusId.value}` })
  }
  if (category.value) chips.push({ key: 'category', label: `课程类别：${category.value}` })
  if (lessonMode.value) chips.push({ key: 'lessonMode', label: `授课模式：${lessonMode.value}` })
  if (chargeMode.value) chips.push({ key: 'chargeMode', label: `收费模式：${chargeMode.value}` })
  if (status.value !== undefined) {
    chips.push({ key: 'status', label: `课程状态：${status.value === 1 ? '开售' : '停售'}` })
  }
  if (subject.value) chips.push({ key: 'subject', label: `科目：${subject.value}` })
  if (keyword.value.trim()) chips.push({ key: 'keyword', label: `课程名称：${keyword.value.trim()}` })
  return chips
})

function removeChip(key: string) {
  if (key === 'campus') campusId.value = undefined
  if (key === 'category') category.value = undefined
  if (key === 'lessonMode') lessonMode.value = undefined
  if (key === 'chargeMode') chargeMode.value = undefined
  if (key === 'status') status.value = undefined
  if (key === 'subject') subject.value = undefined
  if (key === 'keyword') keyword.value = ''
  search()
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
  selectedKeys.value = []
  expandedKeys.value = []
  loadList()
}

function onSelectChange(keys: number[]) {
  selectedKeys.value = keys
}

async function onExpandRow(expanded: boolean, record: CourseRow) {
  if (!expanded) {
    expandedKeys.value = expandedKeys.value.filter((id) => id !== record.id)
    return
  }
  expandedKeys.value = [...expandedKeys.value, record.id]
  if (classMap.value[record.id]) return
  classLoading.value = { ...classLoading.value, [record.id]: true }
  try {
    classMap.value = { ...classMap.value, [record.id]: await fetchCourseClasses(record.id) }
  } catch (e) {
    console.error(e)
  } finally {
    classLoading.value = { ...classLoading.value, [record.id]: false }
  }
}

function actionsOf(record: CourseRow) {
  const items: Array<{ key: string; label: string; danger?: boolean }> = [
    { key: 'edit', label: '编辑' },
    { key: 'copy', label: '复制' },
  ]
  if (record.status === 1) {
    items.push({ key: 'off', label: '停售' })
  } else {
    items.push({ key: 'on', label: '起售' })
  }
  items.push({ key: 'delete', label: '删除', danger: true })
  return items
}

function onRowAction(event: { key?: string | number }, record: CourseRow) {
  const key = String(event.key ?? '')
  if (key === 'edit') openEdit(record)
  if (key === 'copy') openCopy(record)
  if (key === 'on') toggleSale(record, 1)
  if (key === 'off') toggleSale(record, 0)
  if (key === 'delete') confirmDelete(record)
}

async function toggleSale(record: CourseRow, next: number) {
  try {
    await batchCourses([record.id], next === 1 ? 'on' : 'off')
    message.success(next === 1 ? '课程已起售' : '课程已停售')
    await loadList()
  } catch (e) {
    message.error((e as Error).message || '操作失败')
  }
}

function confirmDelete(record: CourseRow) {
  Modal.confirm({
    title: '确认删除课程？',
    content: `删除后「${record.name}」及其定价标准将不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteCourse(record.id)
        message.success('课程已删除')
        await loadList()
      } catch (e) {
        message.error((e as Error).message || '删除失败')
      }
    },
  })
}

function bulkDelete() {
  if (!selectedKeys.value.length) {
    message.info('请先选择课程')
    return
  }
  Modal.confirm({
    title: '确认批量删除？',
    content: `已选择 ${selectedKeys.value.length} 个课程，删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await batchCourses(selectedKeys.value, 'delete')
        message.success('已批量删除')
        selectedKeys.value = []
        await loadList()
      } catch (e) {
        message.error((e as Error).message || '批量删除失败')
      }
    },
  })
}

async function bulkSale(next: number) {
  if (!selectedKeys.value.length) {
    message.info('请先选择课程')
    return
  }
  try {
    await batchCourses(selectedKeys.value, next === 1 ? 'on' : 'off')
    message.success(next === 1 ? '已批量起售' : '已批量停售')
    await loadList()
  } catch (e) {
    message.error((e as Error).message || '操作失败')
  }
}

function exportCourses() {
  const source = selectedKeys.value.length
    ? rows.value.filter((row) => selectedKeys.value.includes(row.id))
    : rows.value
  if (!source.length) {
    message.info('当前没有可导出的课程')
    return
  }
  const header = ['课程名称', '课程类别', '授课模式', '开班数', '收费模式', '开课校区', '科目', '创建日期', '课程状态', '备注']
  const lines = source.map((row) => [
    row.name,
    row.category || '',
    row.lessonMode || '班课',
    String(row.classCount ?? 0),
    row.chargeMode || '按课时',
    String(row.campusCount ?? 0),
    row.subject || '',
    row.createdAt ? row.createdAt.slice(0, 10) : '',
    row.status === 1 ? '开售' : '停售',
    row.remark || '',
  ])
  const csv = [header, ...lines]
    .map((cols) => cols.map((value) => `"${String(value).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `课程列表_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

const columns = computed<any[]>(() => {
  const list: any[] = [
    { title: '课程名称', dataIndex: 'name', width: 320, sorter: true, fixed: 'left' },
    { title: '课程类别', dataIndex: 'category', width: 110 },
    { title: '授课模式', dataIndex: 'lessonMode', width: 96 },
    { title: '开班数', dataIndex: 'classCount', width: 88, align: 'right', sorter: true },
    { title: '收费模式', dataIndex: 'chargeMode', width: 128 },
    { title: '开课校区', dataIndex: 'campusCount', width: 92, align: 'right' },
  ]
  if (visibleOptional.value.includes('subject')) {
    list.push({ title: '科目', dataIndex: 'subject', width: 100 })
  }
  list.push({ title: '创建日期', dataIndex: 'createdAt', width: 118, sorter: true })
  if (visibleOptional.value.includes('remark')) {
    list.push({ title: '备注', dataIndex: 'remark', width: 200, ellipsis: true })
  }
  list.push({ title: '课程状态', dataIndex: 'status', width: 96 })
  list.push({ title: '操作', dataIndex: 'action', width: 84, fixed: 'right' })
  return list
})

const scrollX = computed(() => {
  let width = 0
  for (const col of columns.value) width += Number(col.width ?? 0)
  return width + 48
})

function cellValue(record: Record<string, unknown>, key: string | number | undefined) {
  return show(key === undefined ? undefined : record[String(key)])
}

function onCategoryAction(event: { key?: string | number }, record: CourseCategoryRow) {
  const key = String(event.key ?? '')
  if (key === 'edit') openRenameCategory(record)
  if (key === 'delete') confirmDeleteCategory(record)
}

function onUpgradeAction(event: { key?: string | number }, record: CourseUpgradeRow) {
  if (String(event.key ?? '') === 'delete') confirmDeleteUpgrade(record)
}

function dateOf(value: string | null | undefined) {
  return value ? value.slice(0, 10) : '—'
}

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : value
}

// ---------------- 新建 / 编辑课程 ----------------------------
interface PriceRowState {
  uid: number
  campusId?: number
  lessons?: number
  totalPrice?: number
}

interface CourseFormState {
  name: string
  category?: string
  subject?: string
  lessonMode: string
  campusScope: string
  campusId?: number
  lessonCharge: boolean
  timeCharge: boolean
  periodCharge: boolean
  prices: PriceRowState[]
  materialFee?: number
  remark: string
}

let priceUid = 0

function newPriceRow(lessons = 1, totalPrice?: number, campusId?: number): PriceRowState {
  priceUid += 1
  return { uid: priceUid, lessons, totalPrice, campusId }
}

function emptyForm(): CourseFormState {
  return {
    name: '',
    category: undefined,
    subject: undefined,
    lessonMode: '班课',
    campusScope: '全部',
    campusId: undefined,
    lessonCharge: true,
    timeCharge: false,
    periodCharge: false,
    prices: [],
    materialFee: undefined,
    remark: '',
  }
}

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formSaving = ref(false)
const formLoading = ref(false)
const form = ref<CourseFormState>(emptyForm())

function openCreate() {
  formMode.value = 'create'
  editingId.value = null
  form.value = emptyForm()
  formOpen.value = true
}

async function fillFormFromDetail(id: number) {
  const detail = await fetchCourseDetail(id)
  form.value = {
    name: detail.name,
    category: detail.category ?? undefined,
    subject: detail.subject ?? undefined,
    lessonMode: detail.lessonMode || '班课',
    campusScope: detail.campusScope || '全部',
    campusId: detail.campusId ?? undefined,
    lessonCharge: detail.lessonCharge !== 0,
    timeCharge: detail.timeCharge === 1,
    periodCharge: detail.periodCharge === 1,
    prices: detail.prices?.length
      ? detail.prices.map((row) => newPriceRow(Number(row.lessons), row.totalPrice ?? undefined, row.campusId ?? undefined))
      : [],
    materialFee: detail.materialFee === null || detail.materialFee === undefined ? undefined : Number(detail.materialFee),
    remark: detail.remark ?? '',
  }
}

async function openEdit(record: CourseRow) {
  formMode.value = 'edit'
  editingId.value = record.id
  form.value = emptyForm()
  formOpen.value = true
  formLoading.value = true
  try {
    await fillFormFromDetail(record.id)
  } catch (e) {
    message.error((e as Error).message || '课程详情加载失败')
    formOpen.value = false
  } finally {
    formLoading.value = false
  }
}

async function openCopy(record: CourseRow) {
  formMode.value = 'create'
  editingId.value = null
  form.value = emptyForm()
  formOpen.value = true
  formLoading.value = true
  try {
    await fillFormFromDetail(record.id)
    form.value.name = `${record.name}（副本）`
    // 价格归缴费模板管理，复制课程不复制旧定价
    form.value.prices = []
  } catch (e) {
    message.error((e as Error).message || '课程详情加载失败')
    formOpen.value = false
  } finally {
    formLoading.value = false
  }
}

async function saveCourse() {
  if (!form.value.name.trim()) {
    message.warning('请填写课程名称')
    return
  }
  if (!form.value.category) {
    message.warning('请选择课程类别')
    return
  }
  if (form.value.campusScope === '指定' && form.value.campusId === undefined) {
    message.warning('请选择指定校区')
    return
  }
  const validPrices = form.value.prices.filter(
    (row) => row.lessons !== undefined && Number(row.lessons) > 0,
  )

  const payload: CourseFormPayload = {
    name: form.value.name.trim(),
    category: form.value.category,
    subject: form.value.subject || null,
    lessonMode: form.value.lessonMode,
    campusScope: form.value.campusScope,
    campusId: form.value.campusScope === '指定' ? form.value.campusId : null,
    lessonCharge: form.value.lessonCharge,
    timeCharge: form.value.timeCharge,
    periodCharge: form.value.periodCharge,
    prices: form.value.lessonCharge
      ? validPrices.map((row) => ({
          campusId: row.campusId ?? null,
          lessons: Number(row.lessons),
          totalPrice: row.totalPrice === undefined ? null : Number(row.totalPrice),
        }))
      : [],
    materialFee: form.value.materialFee === undefined ? 0 : Number(form.value.materialFee),
    remark: form.value.remark || null,
  }

  formSaving.value = true
  try {
    if (formMode.value === 'create') {
      await createCourse(payload)
      message.success('课程已创建')
    } else if (editingId.value !== null) {
      await updateCourse(editingId.value, payload)
      message.success('课程已保存')
    }
    formOpen.value = false
    await Promise.all([loadList(), loadOptions()])
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    formSaving.value = false
  }
}

const priceColumns = [
  { title: '开课校区', dataIndex: 'campusId', width: 220 },
  { title: '数量（课时）', dataIndex: 'lessons', width: 180 },
  { title: '总价（元）', dataIndex: 'totalPrice', width: 180 },
]

// ---------------- 新建价格模板（弹窗，不离开当前课程表单） ----------------
const templateOpen = ref(false)
const templateSaving = ref(false)
const templateOptions = ref<PriceTemplateOptions>({ campuses: [], courses: [], classTypes: [] })
const templateForm = reactive({
  name: '',
  campusScope: '全部',
  campusIds: [] as number[],
  classType: null as string | null,
  materialFee: 0 as number | null,
  validDays: null as number | null,
  courseIds: [] as number[],
  unitPrice: null as number | null,
  remark: '',
})

const templateCourseOptions = computed(() => {
  const courses = templateOptions.value.courses
  if (templateForm.campusScope !== '指定' || !templateForm.campusIds.length) return courses
  return courses.filter((item) => item.campusId && templateForm.campusIds.includes(item.campusId))
})

async function openTemplateModal() {
  templateForm.name = form.value.name.trim() ? `${form.value.name.trim()} 标准价` : ''
  templateForm.campusScope = '全部'
  templateForm.campusIds = []
  templateForm.classType = null
  templateForm.materialFee = 0
  templateForm.validDays = null
  templateForm.courseIds = formMode.value === 'edit' && editingId.value ? [editingId.value] : []
  templateForm.unitPrice = null
  templateForm.remark = ''
  templateOpen.value = true
  try {
    templateOptions.value = await fetchPriceTemplateOptions()
  } catch (e) {
    message.error((e as Error).message || '价格模板选项加载失败')
  }
}

function templateUnitPriceText(unitPrice: number | null | undefined) {
  return unitPrice == null ? '—' : `￥${Number(unitPrice).toFixed(2)}/课时`
}

async function saveTemplate() {
  if (!templateForm.name.trim()) {
    message.warning('请填写模板名称')
    return
  }
  if (!templateForm.courseIds.length) {
    message.warning('请至少绑定一门课程')
    return
  }
  if (!Number(templateForm.unitPrice) || Number(templateForm.unitPrice) <= 0) {
    message.warning('课时单价必须大于 0')
    return
  }
  const payload: PriceTemplateFormPayload = {
    name: templateForm.name.trim(),
    campusScope: templateForm.campusScope,
    campusIds: templateForm.campusScope === '指定' ? templateForm.campusIds : [],
    classType: templateForm.classType?.trim() || null,
    materialFee: Number(templateForm.materialFee ?? 0),
    validDays: templateForm.validDays ? Number(templateForm.validDays) : null,
    courseIds: templateForm.courseIds,
    prices: [
      {
        lessons: 1,
        unitPrice: Number(templateForm.unitPrice),
        totalPrice: Number(templateForm.unitPrice),
      },
    ],
    remark: templateForm.remark.trim() || null,
  }
  templateSaving.value = true
  try {
    await createPriceTemplate(payload)
    message.success('价格模板已创建')
    templateOpen.value = false
  } catch (e) {
    message.error((e as Error).message || '价格模板创建失败')
  } finally {
    templateSaving.value = false
  }
}

// ---------------- 课程类别 ----------------------------
const categoryKeyword = ref('')
const categoryRows = ref<CourseCategoryRow[]>([])
const categoryLoading = ref(false)

async function loadCategories() {
  categoryLoading.value = true
  try {
    categoryRows.value = await fetchCourseCategories(categoryKeyword.value.trim() || undefined)
  } catch (e) {
    message.error((e as Error).message || '课程类别加载失败')
  } finally {
    categoryLoading.value = false
  }
}

// ---------------- 科目 ----------------------------
const subjectRows = ref<CourseSubjectRow[]>([])
const subjectLoading = ref(false)

async function loadSubjects() {
  subjectLoading.value = true
  try {
    subjectRows.value = await fetchCourseSubjects()
  } catch (e) {
    message.error((e as Error).message || '科目加载失败')
  } finally {
    subjectLoading.value = false
  }
}

// ---------------- 升期关系 ----------------------------
const upgradeKeyword = ref('')
const upgradeRows = ref<CourseUpgradeRow[]>([])
const upgradeLoading = ref(false)

async function loadUpgrades() {
  upgradeLoading.value = true
  try {
    upgradeRows.value = await fetchCourseUpgrades(upgradeKeyword.value.trim() || undefined)
  } catch (e) {
    message.error((e as Error).message || '升期关系加载失败')
  } finally {
    upgradeLoading.value = false
  }
}

const upgradeOpen = ref(false)
const upgradeSaving = ref(false)
const upgradeForm = ref<{ name: string; fromCourseId?: number; toCourseId?: number; periodCount: number }>({
  name: '',
  fromCourseId: undefined,
  toCourseId: undefined,
  periodCount: 1,
})

function openUpgradeCreate() {
  upgradeForm.value = { name: '', fromCourseId: undefined, toCourseId: undefined, periodCount: 1 }
  upgradeOpen.value = true
}

async function saveUpgrade() {
  if (!upgradeForm.value.name.trim()) {
    message.warning('请填写升期关系名称')
    return
  }
  if (!upgradeForm.value.fromCourseId) {
    message.warning('请选择原课程')
    return
  }
  upgradeSaving.value = true
  try {
    await createCourseUpgrade({
      name: upgradeForm.value.name.trim(),
      fromCourseId: upgradeForm.value.fromCourseId,
      toCourseId: upgradeForm.value.toCourseId ?? null,
      periodCount: upgradeForm.value.periodCount,
    })
    message.success('升期关系已创建')
    upgradeOpen.value = false
    await loadUpgrades()
  } catch (e) {
    message.error((e as Error).message || '创建失败')
  } finally {
    upgradeSaving.value = false
  }
}

function confirmDeleteUpgrade(record: CourseUpgradeRow) {
  Modal.confirm({
    title: '确认删除升期关系？',
    content: `删除后「${record.name}」将不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteCourseUpgrade(record.id)
        message.success('已删除')
        await loadUpgrades()
      } catch (e) {
        message.error((e as Error).message || '删除失败')
      }
    },
  })
}

// ---------------- 类别 / 科目 新建与重命名 ----------------------------
type NameModalTarget = 'category' | 'subject' | 'renameCategory'

const nameModalOpen = ref(false)
const nameModalTarget = ref<NameModalTarget>('category')
const nameModalValue = ref('')
const nameModalSaving = ref(false)
const renameCategoryId = ref<number | null>(null)

const nameModalTitle = computed(() => {
  if (nameModalTarget.value === 'subject') return '新建科目'
  if (nameModalTarget.value === 'renameCategory') return '编辑课程类别'
  return '新建课程类别'
})

function openCategoryModal() {
  nameModalTarget.value = 'category'
  renameCategoryId.value = null
  nameModalValue.value = ''
  nameModalOpen.value = true
}

function openRenameCategory(record: CourseCategoryRow) {
  nameModalTarget.value = 'renameCategory'
  renameCategoryId.value = record.id
  nameModalValue.value = record.name
  nameModalOpen.value = true
}

function openSubjectModal() {
  nameModalTarget.value = 'subject'
  nameModalValue.value = ''
  nameModalOpen.value = true
}

async function submitNameModal() {
  const name = nameModalValue.value.trim()
  if (!name) {
    message.warning('请填写名称')
    return
  }
  nameModalSaving.value = true
  try {
    if (nameModalTarget.value === 'category') {
      await createCourseCategory(name)
      message.success('课程类别已创建')
      if (formOpen.value) form.value.category = name
    } else if (nameModalTarget.value === 'renameCategory' && renameCategoryId.value !== null) {
      await renameCourseCategory(renameCategoryId.value, name)
      message.success('课程类别已更新')
    } else {
      await createCourseSubject(name)
      message.success('科目已创建')
      if (formOpen.value) form.value.subject = name
    }
    nameModalOpen.value = false
    await Promise.all([
      loadOptions(),
      tab.value === 'category' ? loadCategories() : Promise.resolve(),
      tab.value === 'subject' ? loadSubjects() : Promise.resolve(),
    ])
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    nameModalSaving.value = false
  }
}

function confirmDeleteCategory(record: CourseCategoryRow) {
  Modal.confirm({
    title: '确认删除课程类别？',
    content: `「${record.name}」下若仍有课程将无法删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteCourseCategory(record.id)
        message.success('已删除')
        await Promise.all([loadOptions(), loadCategories()])
      } catch (e) {
        message.error((e as Error).message || '删除失败')
      }
    },
  })
}

function confirmDeleteSubject(record: CourseSubjectRow) {
  Modal.confirm({
    title: '确认删除科目？',
    content: `「${record.name}」已被课程使用时将无法删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteCourseSubject(record.id)
        message.success('已删除')
        await Promise.all([loadOptions(), loadSubjects()])
      } catch (e) {
        message.error((e as Error).message || '删除失败')
      }
    },
  })
}

function switchTab(key: TabKey) {
  tab.value = key
  if (key === 'list') loadList()
  if (key === 'upgrade') loadUpgrades()
  if (key === 'category') loadCategories()
  if (key === 'subject') loadSubjects()
}

function openFaq() {
  message.info('原系统「课程常见问题」帮助内容未迁移到本系统')
}

onMounted(async () => {
  loadList()
  await Promise.all([loadOptions(), loadAllCourses()])
})
</script>

<template>
  <div class="course-page">
    <div class="page-head">
      <span class="head-label">课程</span>
      <div class="head-tabs">
        <span
          v-for="item in tabs"
          :key="item.key"
          class="head-tab"
          :class="{ active: tab === item.key }"
          @click="switchTab(item.key)"
        >
          {{ item.label }}
        </span>
      </div>
      <a class="help-link" @click="openFaq">课程常见问题</a>
    </div>

    <!-- 课程列表 -->
    <template v-if="tab === 'list'">
      <div class="action-row">
        <a-button type="primary" class="primary-btn" @click="openCreate">新建课程</a-button>
        <a-button @click="message.info('批量导入模板功能暂未开放，可在课程列表逐个新建')">批量导入</a-button>
      </div>

      <FilterBar @search="search" @reset="resetFilters">
        <a-select
          v-model:value="campusId"
          class="filter-select"
          placeholder="开课校区"
          allow-clear
          style="width: 130px"
          @change="search"
        >
          <a-select-option v-for="item in courseOptions.campuses" :key="item.id" :value="item.id">
            {{ item.name }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="category"
          class="filter-select"
          placeholder="课程类别"
          allow-clear
          style="width: 130px"
          @change="search"
        >
          <a-select-option v-for="item in courseOptions.categories" :key="item.id" :value="item.name">
            {{ item.name }}
          </a-select-option>
        </a-select>
        <a-select
          v-model:value="lessonMode"
          class="filter-select"
          placeholder="授课模式"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option value="班课">班课</a-select-option>
          <a-select-option value="一对一">一对一</a-select-option>
        </a-select>
        <a-select
          v-model:value="chargeMode"
          class="filter-select"
          placeholder="收费模式"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option value="按课时">按课时</a-select-option>
          <a-select-option value="按时间">按时间</a-select-option>
          <a-select-option value="按期">按期</a-select-option>
        </a-select>
        <a-select
          v-model:value="status"
          class="filter-select"
          placeholder="课程状态"
          allow-clear
          style="width: 116px"
          @change="search"
        >
          <a-select-option :value="1">开售</a-select-option>
          <a-select-option :value="0">停售</a-select-option>
        </a-select>
        <a-select
          v-model:value="subject"
          class="filter-select"
          placeholder="科目"
          allow-clear
          style="width: 100px"
          @change="search"
        >
          <a-select-option v-for="item in courseOptions.subjects" :key="item.id" :value="item.name">
            {{ item.name }}
          </a-select-option>
        </a-select>

        <template #search>
          <a-input
            v-model:value="keyword"
            class="filter-search"
            placeholder="搜索课程名称"
            allow-clear
            @press-enter="search"
          >
            <template #prefix>
              <SearchOutlined class="search-icon" />
            </template>
          </a-input>
        </template>
      </FilterBar>

      <div v-if="activeChips.length" class="chip-line">
        <span class="chip-text">已选筛选：</span>
        <span v-for="chip in activeChips" :key="chip.key" class="filter-chip">
          {{ chip.label }}
          <span class="chip-x" @click="removeChip(chip.key)">×</span>
        </span>
        <a class="reset-link" @click="resetFilters">清空筛选</a>
      </div>

      <div class="bulk-row">
        <a-button size="small" class="bulk-btn" @click="exportCourses">批量导出</a-button>
        <a-button size="small" class="bulk-btn" @click="bulkDelete">批量删除</a-button>
        <a-button size="small" class="bulk-btn" @click="bulkSale(1)">批量起售</a-button>
        <a-button size="small" class="bulk-btn" @click="bulkSale(0)">批量停售</a-button>

        <span class="bulk-spacer" />

        <a-popover trigger="click" placement="bottomRight">
          <template #content>
            <div class="column-picker">
              <a-checkbox-group
                v-model:value="visibleOptional"
                :options="optionalColumns.map((c) => ({ label: c.title, value: c.key }))"
              />
            </div>
          </template>
          <a-button type="primary" size="small" class="custom-btn">自定义显示列</a-button>
        </a-popover>
      </div>

      <div class="result-line">
        当前结果：总计 <b>{{ summary?.total ?? 0 }}</b> 个课程 |
        <b>{{ summary?.opening ?? 0 }}</b> 个课程正在开班/课
      </div>

      <a-table
        class="course-table"
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
        :scroll="{ x: scrollX }"
        :row-selection="{ selectedRowKeys: selectedKeys, onChange: onSelectChange }"
        :expanded-row-keys="expandedKeys"
        @expand="onExpandRow"
        @change="onTableChange"
      >
        <template #bodyCell="{ column, record }">
          <a v-if="column.dataIndex === 'name'" class="course-name" @click.stop="openEdit(record)">
            {{ record.name }}
          </a>
          <span v-else-if="column.dataIndex === 'category'">{{ show(record.category) }}</span>
          <span v-else-if="column.dataIndex === 'lessonMode'" class="mode-text">
            <span class="mode-badge" :class="{ 'mode-one': record.lessonMode === '一对一' }">
              {{ record.lessonMode === '一对一' ? '1' : '班' }}
            </span>
            {{ record.lessonMode || '班课' }}
          </span>
          <span v-else-if="column.dataIndex === 'classCount'">
            {{ record.classCount > 0 ? record.classCount : '-' }}
          </span>
          <span v-else-if="column.dataIndex === 'chargeMode'">{{ record.chargeMode || '按课时' }}</span>
          <span v-else-if="column.dataIndex === 'campusCount'">{{ record.campusCount ?? 1 }}</span>
          <span v-else-if="column.dataIndex === 'subject'">{{ show(record.subject) }}</span>
          <span v-else-if="column.dataIndex === 'createdAt'">{{ dateOf(record.createdAt) }}</span>
          <a-tooltip v-else-if="column.dataIndex === 'remark'" :title="record.remark || '—'">
            <span class="ellipsis">{{ show(record.remark) }}</span>
          </a-tooltip>
          <span v-else-if="column.dataIndex === 'status'">
            <span class="pill-status" :class="{ 'pill-off': record.status !== 1 }">
              {{ record.status === 1 ? '开售' : '停售' }}
            </span>
          </span>
          <a-dropdown v-else-if="column.dataIndex === 'action'" :trigger="['click']">
            <a class="op-link" @click.stop>操作 <DownOutlined /></a>
            <template #overlay>
              <a-menu @click="onRowAction($event, record)">
                <a-menu-item
                  v-for="item in actionsOf(record)"
                  :key="item.key"
                  :danger="item.danger"
                >
                  {{ item.label }}
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>

        <template #expandedRowRender="{ record }">
          <div class="expand-panel">
            <div class="expand-title">开班班级（{{ (classMap[record.id] || []).length }}）</div>
            <a-spin :spinning="!!classLoading[record.id]">
              <a-table
                v-if="(classMap[record.id] || []).length"
                :data-source="classMap[record.id]"
                :pagination="false"
                row-key="id"
                size="small"
                :columns="[
                  { title: '班级名称', dataIndex: 'name', width: 280 },
                  { title: '教师', dataIndex: 'teacherName', width: 110 },
                  { title: '状态', dataIndex: 'status', width: 100 },
                  { title: '在读人数', dataIndex: 'enrolledCount', width: 100, align: 'right' },
                  { title: '上课时间', dataIndex: 'scheduleText', width: 240 },
                ]"
              >
                <template #bodyCell="{ column: childColumn, record: child }">
                  <span v-if="childColumn.dataIndex === 'teacherName'">{{ show(child.teacherName) }}</span>
                  <span v-else-if="childColumn.dataIndex === 'enrolledCount'">
                    {{ child.enrolledCount }} / {{ child.capacity }}
                  </span>
                  <span v-else-if="childColumn.dataIndex === 'scheduleText'">{{ show(child.scheduleText) }}</span>
                  <span v-else>{{ cellValue(child, childColumn.dataIndex) }}</span>
                </template>
              </a-table>
              <a-empty v-else description="该课程还没有开班班级" />
            </a-spin>
          </div>
        </template>

        <template #emptyText>
          <a-empty description="当前筛选条件下暂无课程" />
        </template>
      </a-table>
    </template>

    <!-- 升期关系列表 -->
    <template v-else-if="tab === 'upgrade'">
      <div class="action-row">
        <a-button type="primary" class="primary-btn" @click="openUpgradeCreate">新建升期</a-button>
      </div>

      <div class="filter-line">
        <div class="name-search">
          <span class="name-label">升期名称</span>
          <a-input
            v-model:value="upgradeKeyword"
            class="name-input"
            placeholder="请输入升期名称"
            allow-clear
            :bordered="false"
            @press-enter="loadUpgrades"
          >
            <template #suffix>
              <SearchOutlined class="search-icon" @click="loadUpgrades" />
            </template>
          </a-input>
        </div>
      </div>

      <a-table
        class="course-table"
        :data-source="upgradeRows"
        :loading="upgradeLoading"
        :pagination="false"
        row-key="id"
        size="small"
        :columns="[
          { title: '升期关系名称', dataIndex: 'name', width: 260, sorter: (a: CourseUpgradeRow, b: CourseUpgradeRow) => a.name.localeCompare(b.name) },
          { title: '原课程', dataIndex: 'fromCourseName', width: 240 },
          { title: '升期课程', dataIndex: 'toCourseName', width: 240 },
          { title: '期数', dataIndex: 'periodCount', width: 100, align: 'right', sorter: (a: CourseUpgradeRow, b: CourseUpgradeRow) => a.periodCount - b.periodCount },
          { title: '课程数', dataIndex: 'courseCount', width: 100, align: 'right', sorter: (a: CourseUpgradeRow, b: CourseUpgradeRow) => a.courseCount - b.courseCount },
          { title: '创建日期', dataIndex: 'createdAt', width: 130, sorter: (a: CourseUpgradeRow, b: CourseUpgradeRow) => String(a.createdAt || '').localeCompare(String(b.createdAt || '')) },
          { title: '操作', dataIndex: 'action', width: 90 },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'fromCourseName'">{{ show(record.fromCourseName) }}</span>
          <span v-else-if="column.dataIndex === 'toCourseName'">{{ show(record.toCourseName) }}</span>
          <span v-else-if="column.dataIndex === 'createdAt'">{{ dateOf(record.createdAt) }}</span>
          <a-dropdown v-else-if="column.dataIndex === 'action'" :trigger="['click']">
            <a class="op-link" @click.stop>操作 <DownOutlined /></a>
            <template #overlay>
              <a-menu @click="onUpgradeAction($event, record)">
                <a-menu-item key="delete" danger>删除</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <span v-else>{{ cellValue(record, column.dataIndex) }}</span>
        </template>
        <template #emptyText>
          <a-empty description="暂无数据" />
        </template>
      </a-table>
    </template>

    <!-- 课程类别 -->
    <template v-else-if="tab === 'category'">
      <div class="action-row">
        <a-button type="primary" class="primary-btn" @click="openCategoryModal">新建课程类别</a-button>
        <span class="bulk-spacer" />
        <div class="name-search">
          <a-input
            v-model:value="categoryKeyword"
            class="name-input"
            placeholder="请输入课程大类名称"
            allow-clear
            :bordered="false"
            @press-enter="loadCategories"
          >
            <template #suffix>
              <SearchOutlined class="search-icon" @click="loadCategories" />
            </template>
          </a-input>
        </div>
      </div>

      <a-table
        class="course-table"
        :data-source="categoryRows"
        :loading="categoryLoading"
        :pagination="false"
        row-key="id"
        size="small"
        :columns="[
          { title: '序号', dataIndex: 'index', width: 80 },
          { title: '课程类别', dataIndex: 'name' },
          { title: '课程数', dataIndex: 'courseCount', width: 100, align: 'right' },
          { title: '操作', dataIndex: 'action', width: 90 },
        ]"
      >
        <template #bodyCell="{ column, record, index }">
          <span v-if="column.dataIndex === 'index'">{{ index + 1 }}</span>
          <span v-else-if="column.dataIndex === 'name'">{{ show(record.name) }}</span>
          <span v-else-if="column.dataIndex === 'courseCount'">{{ record.courseCount ?? 0 }}</span>
          <a-dropdown v-else-if="column.dataIndex === 'action'" :trigger="['click']">
            <a class="op-link" @click.stop>操作 <DownOutlined /></a>
            <template #overlay>
              <a-menu @click="onCategoryAction($event, record)">
                <a-menu-item key="edit">编辑</a-menu-item>
                <a-menu-item key="delete" danger>删除</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template #emptyText>
          <a-empty description="暂无数据" />
        </template>
      </a-table>
    </template>

    <!-- 科目设置 -->
    <template v-else>
      <div class="action-row">
        <a-button type="primary" class="primary-btn" @click="openSubjectModal">新建科目</a-button>
      </div>

      <a-table
        class="course-table"
        :data-source="subjectRows"
        :loading="subjectLoading"
        :pagination="false"
        row-key="id"
        size="small"
        :columns="[
          { title: '科目名称', dataIndex: 'name', width: 300 },
          { title: '状态', dataIndex: 'status', width: 120 },
          { title: '操作', dataIndex: 'action', width: 90 },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'name'">{{ show(record.name) }}</span>
          <span v-else-if="column.dataIndex === 'status'">
            <span class="pill-status">{{ record.status === 1 ? '正常' : '停用' }}</span>
          </span>
          <a-dropdown v-else-if="column.dataIndex === 'action'" :trigger="['click']">
            <a class="op-link" @click.stop>操作 <DownOutlined /></a>
            <template #overlay>
              <a-menu @click="confirmDeleteSubject(record)">
                <a-menu-item key="delete" danger>删除</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
        <template #emptyText>
          <a-empty description="暂无数据" />
        </template>
      </a-table>
    </template>

    <!-- 新建 / 编辑课程（宽抽屉） -->
    <a-drawer
      v-model:open="formOpen"
      :width="1040"
      :title="formMode === 'create' ? '新建课程' : '编辑课程'"
      :body-style="{ padding: '0' }"
      destroy-on-close
    >
      <a-spin :spinning="formLoading">
        <div class="course-form-page">
          <div class="form-body">
            <div class="section-title">课程基础信息</div>

            <div class="form-row">
              <label class="field-label required">课程名称</label>
              <a-input
                v-model:value="form.name"
                placeholder="请输入课程名称"
                style="max-width: 440px"
                :maxlength="64"
              />
            </div>

            <div class="form-row">
              <label class="field-label required">课程类别</label>
              <a-select
                v-model:value="form.category"
                placeholder="请选择课程类别"
                style="width: 240px"
                allow-clear
              >
                <a-select-option v-for="item in courseOptions.categories" :key="item.id" :value="item.name">
                  {{ item.name }}
                </a-select-option>
              </a-select>
              <a-button type="link" class="inline-link" @click="openCategoryModal">新建类别</a-button>
            </div>

            <div class="form-row">
              <label class="field-label">科目</label>
              <a-select
                v-model:value="form.subject"
                placeholder="请选择科目"
                style="width: 240px"
                allow-clear
              >
                <a-select-option v-for="item in courseOptions.subjects" :key="item.id" :value="item.name">
                  {{ item.name }}
                </a-select-option>
              </a-select>
              <a-button type="link" class="inline-link" @click="openSubjectModal">新建科目</a-button>
            </div>

            <div class="form-row">
              <label class="field-label">授课模式</label>
              <a-radio-group v-model:value="form.lessonMode">
                <a-radio value="班课">班课</a-radio>
                <a-radio value="一对一">一对一</a-radio>
              </a-radio-group>
            </div>

            <div class="form-row">
              <label class="field-label">开课校区</label>
              <a-radio-group v-model:value="form.campusScope">
                <a-radio value="全部">全部校区</a-radio>
                <a-radio value="指定">指定校区</a-radio>
              </a-radio-group>
              <a-select
                v-if="form.campusScope === '指定'"
                v-model:value="form.campusId"
                placeholder="请选择校区"
                style="width: 220px; margin-left: 12px"
                allow-clear
              >
                <a-select-option v-for="item in courseOptions.campuses" :key="item.id" :value="item.id">
                  {{ item.name }}
                </a-select-option>
              </a-select>
            </div>

            <div class="section-title">收费模式</div>

            <div class="charge-row">
              <span class="charge-label">按课时收费</span>
              <a-switch v-model:checked="form.lessonCharge" size="small" />
            </div>
            <div v-if="form.lessonCharge" class="price-table-wrap">
              <a-table
                :data-source="form.prices"
                :pagination="false"
                row-key="uid"
                size="small"
                bordered
                :columns="priceColumns"
              >
                <template #bodyCell="{ column, record }">
                  <span v-if="column.dataIndex === 'campusId'">
                    {{ courseOptions.campuses.find((item) => item.id === record.campusId)?.name || '全部校区' }}
                  </span>
                  <span v-else-if="column.dataIndex === 'lessons'">{{ record.lessons ?? '—' }}</span>
                  <span v-else-if="column.dataIndex === 'totalPrice'">
                    {{ record.totalPrice == null ? '—' : Number(record.totalPrice).toFixed(2) }}
                  </span>
                </template>
              </a-table>
              <div class="price-readonly-tip">
                课程价格由「办理中心 - 缴费模板」统一维护，课程页仅作展示。
                <a @click="openTemplateModal">新建价格模板</a>
              </div>
            </div>
          </div>

          <div class="form-footer">
            <a-button class="save-btn" type="primary" :loading="formSaving" @click="saveCourse">保存</a-button>
          </div>
        </div>
      </a-spin>
    </a-drawer>

    <!-- 新建价格模板：在当前页面弹窗完成，不跳转、不丢失课程表单数据 -->
    <a-modal
      v-model:open="templateOpen"
      title="新建价格模板"
      :width="700"
      :confirm-loading="templateSaving"
      ok-text="保存模板"
      cancel-text="取消"
      destroy-on-close
      @ok="saveTemplate"
    >
      <div class="tmpl-form">
        <div class="tmpl-line">
          <label class="required">模板名称</label>
          <a-input v-model:value="templateForm.name" placeholder="如：三年级语文标准价" :maxlength="120" />
        </div>

        <div class="tmpl-line">
          <label class="required">适用课程</label>
          <a-select
            v-model:value="templateForm.courseIds"
            mode="multiple"
            style="width: 100%"
            placeholder="请选择课程（可多选）"
            show-search
            option-filter-prop="label"
            :options="templateCourseOptions.map((item) => ({ label: item.name, value: item.id }))"
          />
        </div>

        <div class="tmpl-line">
          <label>适用校区</label>
          <div class="tmpl-scope">
            <a-radio-group v-model:value="templateForm.campusScope" size="small">
              <a-radio value="全部">全部校区</a-radio>
              <a-radio value="指定">指定校区</a-radio>
            </a-radio-group>
            <a-select
              v-if="templateForm.campusScope === '指定'"
              v-model:value="templateForm.campusIds"
              mode="multiple"
              style="width: 100%; margin-top: 8px"
              placeholder="请选择校区"
              option-filter-prop="label"
              :options="templateOptions.campuses.map((item) => ({ label: item.name, value: item.id }))"
            />
          </div>
        </div>

        <div class="tmpl-line">
          <label>班型</label>
          <a-auto-complete
            v-model:value="templateForm.classType"
            style="width: 100%"
            placeholder="选填，如：小班/一对一；不填表示不限"
            :options="templateOptions.classTypes.map((item) => ({ value: item }))"
            allow-clear
          />
        </div>

        <div class="tmpl-line">
          <label class="required">课时单价</label>
          <a-input-number v-model:value="templateForm.unitPrice" :min="0" :precision="2" style="width: 160px" />
          <span class="tmpl-unit">元/课时</span>
          <div class="tmpl-hint">
            价格模板只记录基础单价，报多少课时都按此单价计算；优惠请在「活动模板」中配置。
            <span v-if="templateForm.unitPrice">当前：{{ templateUnitPriceText(templateForm.unitPrice) }}</span>
          </div>
        </div>

        <div class="tmpl-line">
          <label>教材费</label>
          <a-input-number v-model:value="templateForm.materialFee" :min="0" :precision="2" style="width: 160px" />
          <span class="tmpl-unit">元</span>
        </div>

        <div class="tmpl-line">
          <label>报读有效期</label>
          <a-input-number v-model:value="templateForm.validDays" :min="0" :precision="0" style="width: 160px" placeholder="不填表示不限" />
          <span class="tmpl-unit">天</span>
        </div>

        <div class="tmpl-line">
          <label>备注</label>
          <a-textarea v-model:value="templateForm.remark" :rows="2" placeholder="选填，仅内部可见" :maxlength="200" />
        </div>
      </div>
    </a-modal>

    <!-- 新建升期 -->
    <a-modal
      v-model:open="upgradeOpen"
      title="新建升期"
      :confirm-loading="upgradeSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveUpgrade"
    >
      <div class="modal-form">
        <div class="modal-row">
          <label class="modal-label required">升期关系名称</label>
          <a-input v-model:value="upgradeForm.name" placeholder="请输入升期关系名称" />
        </div>
        <div class="modal-row">
          <label class="modal-label required">原课程</label>
          <a-select
            v-model:value="upgradeForm.fromCourseId"
            placeholder="请选择原课程"
            show-search
            option-filter-prop="label"
            style="width: 100%"
          >
            <a-select-option
              v-for="item in allCourses"
              :key="item.id"
              :value="item.id"
              :label="item.name"
            >
              {{ item.name }}
            </a-select-option>
          </a-select>
        </div>
        <div class="modal-row">
          <label class="modal-label">升期课程</label>
          <a-select
            v-model:value="upgradeForm.toCourseId"
            placeholder="请选择升期课程"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 100%"
          >
            <a-select-option
              v-for="item in allCourses"
              :key="item.id"
              :value="item.id"
              :label="item.name"
            >
              {{ item.name }}
            </a-select-option>
          </a-select>
        </div>
        <div class="modal-row">
          <label class="modal-label">期数</label>
          <a-input-number v-model:value="upgradeForm.periodCount" :min="0" style="width: 140px" />
        </div>
      </div>
    </a-modal>

    <!-- 新建 / 重命名 类别与科目 -->
    <a-modal
      v-model:open="nameModalOpen"
      :title="nameModalTitle"
      :confirm-loading="nameModalSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitNameModal"
    >
      <a-input
        v-model:value="nameModalValue"
        :placeholder="nameModalTarget === 'subject' ? '请输入科目名称' : '请输入课程类别名称'"
        :maxlength="64"
        @press-enter="submitNameModal"
      />
    </a-modal>
  </div>
</template>

<style scoped>
.course-page {
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

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 12px;
  padding: 2px 0 10px;
}

.name-search {
  display: flex;
  align-items: center;
  height: 32px;
  border: 1px solid #d9dce0;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}

.name-label {
  flex: 0 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 10px;
  font-size: 13px;
  color: var(--text-2);
  background: #f7f8fa;
  border-right: 1px solid #e5e6eb;
}

.name-input {
  width: 210px;
  padding: 0 8px;
}

.search-icon {
  color: var(--brand);
  cursor: pointer;
}

.chip-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.chip-text {
  color: var(--text-3);
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border: 1px solid #dbe6f5;
  border-radius: 3px;
  background: #f4f8ff;
  color: var(--brand);
}

.chip-x {
  cursor: pointer;
  color: #9aa3ad;
}

.chip-x:hover {
  color: #f53f3f;
}

.reset-link {
  font-size: 13px;
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

.custom-btn {
  background: var(--brand);
  margin-left: 12px;
}

.column-picker {
  min-width: 160px;
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

.course-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.course-table :deep(.ant-table-tbody > tr > td) {
  padding: 10px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
}

.course-name {
  color: var(--brand);
  cursor: pointer;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.mode-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.mode-badge {
  width: 18px;
  height: 18px;
  border-radius: 3px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 18px;
}

.mode-one {
  background: #7b61ff;
}

.pill-status {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 10px;
  border: 1px solid var(--brand);
  color: var(--brand);
  font-size: 12px;
  line-height: 18px;
  background: #fff;
}

.pill-off {
  border-color: #c9ccd3;
  color: #8f959e;
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.op-link {
  font-size: 13px;
  white-space: nowrap;
}

.expand-panel {
  background: #fafbfc;
  border-radius: 4px;
  padding: 12px 16px;
}

.expand-title {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 8px;
}

/* ---------- 新建课程整页表单 ---------- */
.course-form-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.form-body {
  flex: 1;
  padding: 8px 24px 24px;
  max-width: 1100px;
  margin: 0 auto;
  width: 100%;
}

.section-title {
  position: relative;
  margin: 18px 0 16px;
  padding-left: 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  line-height: 22px;
}

.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  width: 3px;
  height: 14px;
  border-radius: 1px;
  background: var(--brand);
}

.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  min-height: 32px;
}

.field-label {
  flex: 0 0 96px;
  text-align: right;
  font-size: 13px;
  color: var(--text-2);
}

.field-label.required::before {
  content: '*';
  color: #f53f3f;
  margin-right: 4px;
}

.inline-link {
  padding: 0;
  font-size: 13px;
}

.charge-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-left: 108px;
  min-height: 24px;
}

.charge-label {
  font-size: 13px;
  color: var(--text);
}

.price-table-wrap {
  padding: 0 0 12px 108px;
  max-width: 760px;
}

.price-readonly-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-2);
}

.price-readonly-tip a {
  margin-left: 4px;
  color: var(--brand);
  cursor: pointer;
}

.row-link {
  color: var(--brand);
  cursor: pointer;
  font-size: 13px;
}

.add-row-link {
  display: inline-block;
  margin-top: 8px;
  font-size: 13px;
}

.unit-text {
  font-size: 13px;
  color: var(--text-2);
}

.form-footer {
  position: sticky;
  bottom: 0;
  background: #fff;
  border-top: 1px solid var(--line);
  padding: 12px 24px;
  display: flex;
  justify-content: center;
}

.save-btn {
  min-width: 64px;
  height: 32px;
}

.modal-form {
  padding: 8px 0;
}

.modal-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.modal-label {
  flex: 0 0 108px;
  text-align: right;
  font-size: 13px;
  color: var(--text-2);
}

.modal-label.required::before {
  content: '*';
  color: #f53f3f;
  margin-right: 4px;
}

/* ---------- 新建价格模板弹窗 ---------- */
.tmpl-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 4px;
}

.tmpl-line {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.tmpl-line > label {
  flex: 0 0 88px;
  padding-top: 5px;
  color: var(--text-2);
  font-size: 13px;
  text-align: right;
}

.tmpl-line > label.required::before {
  content: '*';
  margin-right: 2px;
  color: #f53f3f;
}

.tmpl-line > *:not(label) {
  flex: 1;
  min-width: 0;
}

.tmpl-scope {
  display: flex;
  flex-direction: column;
}

.tmpl-tier-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tmpl-tier-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tmpl-unit {
  color: #8f959e;
  font-size: 12px;
  white-space: nowrap;
}

.tmpl-hint {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  color: #8f959e;
  font-size: 12px;
  line-height: 1.6;
}

.tmpl-danger {
  color: #ff4d4f;
  white-space: nowrap;
}
</style>
