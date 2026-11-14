<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  createActivityCategory,
  createActivityTemplate,
  createPriceTemplate,
  deleteActivityCategory,
  deleteActivityTemplate,
  deletePriceTemplate,
  fetchActivityCategories,
  fetchActivityTemplates,
  fetchPriceTemplateOptions,
  fetchPriceTemplates,
  setActivityTemplateStatus,
  setPriceTemplateDefault,
  setPriceTemplateStatus,
  updateActivityCategory,
  updateActivityTemplate,
  updatePriceTemplate,
  type ActivityCategory,
  type ActivityDiscountMode,
  type ActivityTemplate,
  type ActivityTemplateFormPayload,
  type ActivityTemplateType,
  type PriceTemplate,
  type PriceTemplateFormPayload,
  type PriceTemplateOptions,
  type PriceTemplateTier,
} from '../api/client'

const activeTab = ref('price')

// ------------------------------------------------------------------
// 基础价格模板
// ------------------------------------------------------------------
const keyword = ref('')
const statusFilter = ref<number | null>(null)
const loading = ref(false)
const rows = ref<PriceTemplate[]>([])
const total = ref(0)
const options = ref<PriceTemplateOptions>({ campuses: [], courses: [], classTypes: [] })

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const saving = ref(false)
const form = reactive({
  id: 0,
  name: '',
  campusScope: '全部',
  campusIds: [] as number[],
  classType: '' as string | null,
  materialFee: 0 as number | null,
  validDays: null as number | null,
  courseIds: [] as number[],
  unitPrice: 0 as number | null,
  remark: '',
})

const filteredCourses = computed(() => {
  if (form.campusScope !== '指定' || form.campusIds.length === 0) return options.value.courses
  return options.value.courses.filter((item) => item.campusId && form.campusIds.includes(item.campusId))
})

const columns = [
  { title: '模板名称', dataIndex: 'name', width: 200, ellipsis: true },
  { title: '适用课程', dataIndex: 'courseNames', width: 260, ellipsis: true },
  { title: '适用校区', dataIndex: 'campusNames', width: 160, ellipsis: true },
  { title: '班型', dataIndex: 'classType', width: 100 },
  { title: '课时单价', dataIndex: 'prices', width: 180 },
  { title: '教材费', dataIndex: 'materialFee', width: 100, align: 'right' as const },
  { title: '有效期', dataIndex: 'validDays', width: 100 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '已用订单', dataIndex: 'usedCount', width: 90, align: 'right' as const },
  { title: '操作', dataIndex: 'action', width: 220, fixed: 'right' as const },
]

function money(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function tierText(tiers: PriceTemplateTier[]) {
  const unitPrice = tiers[0]?.unitPrice
  if (unitPrice == null) return '—'
  return `￥${money(unitPrice)}/课时`
}

function listText(items: string[] | null | undefined, fallback = '全部') {
  return items && items.length ? items.join('、') : fallback
}

async function load() {
  loading.value = true
  try {
    const data = await fetchPriceTemplates({
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value,
    })
    rows.value = data.rows
    total.value = data.total
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    options.value = await fetchPriceTemplateOptions()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function search() {
  load()
}

function reset() {
  keyword.value = ''
  statusFilter.value = null
  load()
}

function openCreate() {
  formMode.value = 'create'
  form.id = 0
  form.name = ''
  form.campusScope = '全部'
  form.campusIds = []
  form.classType = ''
  form.materialFee = 0
  form.validDays = null
  form.courseIds = []
  form.unitPrice = 0
  form.remark = ''
  formOpen.value = true
}

function openEdit(row: PriceTemplate) {
  formMode.value = 'edit'
  form.id = row.id
  form.name = row.name
  form.campusScope = row.campusScope || '全部'
  form.campusIds = [...(row.campusIds || [])]
  form.classType = row.classType || ''
  form.materialFee = Number(row.materialFee ?? 0)
  form.validDays = row.validDays ?? null
  form.courseIds = [...(row.courseIds || [])]
  form.unitPrice = Number(row.prices[0]?.unitPrice ?? 0)
  form.remark = row.remark || ''
  formOpen.value = true
}

async function submit() {
  if (!form.name.trim()) {
    message.warning('请填写模板名称')
    return
  }
  if (!form.courseIds.length) {
    message.warning('请至少绑定一门课程')
    return
  }
  if (!Number(form.unitPrice) || Number(form.unitPrice) <= 0) {
    message.warning('课时单价必须大于 0')
    return
  }
  const payload: PriceTemplateFormPayload = {
    name: form.name.trim(),
    campusScope: form.campusScope,
    campusIds: form.campusScope === '指定' ? form.campusIds : [],
    classType: form.classType?.trim() || null,
    materialFee: Number(form.materialFee ?? 0),
    validDays: form.validDays ? Number(form.validDays) : null,
    courseIds: form.courseIds,
    prices: [
      {
        lessons: 1,
        unitPrice: Number(form.unitPrice),
        totalPrice: Number(form.unitPrice),
      },
    ],
    remark: form.remark.trim() || null,
  }
  saving.value = true
  try {
    if (formMode.value === 'create') {
      await createPriceTemplate(payload)
      message.success('价格模板已创建')
    } else {
      await updatePriceTemplate(form.id, payload)
      message.success('价格模板已保存')
    }
    formOpen.value = false
    await load()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: PriceTemplate) {
  try {
    await setPriceTemplateStatus(row.id, row.status === 1 ? 0 : 1)
    message.success(row.status === 1 ? '已停用' : '已启用')
    load()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function makeDefault(row: PriceTemplate) {
  Modal.confirm({
    title: '设为课程默认价格',
    content: `确认把「${row.name}」设为所绑定课程的默认价格模板？报名时会自动带价。`,
    okText: '设为默认',
    cancelText: '取消',
    onOk: async () => {
      try {
        const count = await setPriceTemplateDefault(row.id)
        message.success(`已设为 ${count} 门课程的默认价格模板`)
        await loadOptions()
      } catch (error) {
        message.error((error as Error).message)
      }
    },
  })
}

function remove(row: PriceTemplate) {
  Modal.confirm({
    title: '删除价格模板',
    content: `确认删除「${row.name}」？已被订单使用的模板只能停用，不能删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deletePriceTemplate(row.id)
        message.success('已删除')
        load()
      } catch (error) {
        message.error((error as Error).message)
      }
    },
  })
}

// ------------------------------------------------------------------
// 活动模板：节日活动 / 报课优惠，按层级分类组织
// ------------------------------------------------------------------
interface CategoryTreeNode {
  key: number
  title: string
  templateCount: number
  children?: CategoryTreeNode[]
}

const categories = ref<ActivityCategory[]>([])
const selectedCategoryId = ref<number | null>(null)
const selectedKeys = ref<number[]>([])

const categoryTree = computed<CategoryTreeNode[]>(() => {
  const map = new Map<number, CategoryTreeNode>()
  for (const item of categories.value) {
    map.set(item.id, {
      key: item.id,
      title: item.templateCount > 0 ? `${item.name}（${item.templateCount}）` : item.name,
      templateCount: item.templateCount,
    })
  }
  const roots: CategoryTreeNode[] = []
  for (const item of categories.value) {
    const node = map.get(item.id)!
    const parent = item.parentId != null ? map.get(item.parentId) : undefined
    if (parent) {
      parent.children = parent.children || []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
})

interface TreeSelectNode {
  value: number
  title: string
  children?: TreeSelectNode[]
}

function toTreeSelect(node: CategoryTreeNode, depth = 0): TreeSelectNode {
  return {
    value: node.key,
    title: `${'　'.repeat(depth)}${node.title}`,
    children: node.children?.map((child) => toTreeSelect(child, depth + 1)),
  }
}

const categoryTreeSelect = computed(() => categoryTree.value.map((node) => toTreeSelect(node)))

const selectedCategory = computed(
  () => categories.value.find((item) => item.id === selectedCategoryId.value) || null,
)

const activityKeyword = ref('')
const activityType = ref<string | null>(null)
const activityStatus = ref<number | null>(null)
const activityLoading = ref(false)
const activities = ref<ActivityTemplate[]>([])
const activityTotal = ref(0)

const activityColumns = [
  { title: '活动名称', dataIndex: 'name', width: 220, ellipsis: true },
  { title: '类型', dataIndex: 'type', width: 100 },
  { title: '所属分类', dataIndex: 'categoryName', width: 130 },
  { title: '优惠内容', dataIndex: 'discountText', width: 260, ellipsis: true },
  { title: '有效期', dataIndex: 'period', width: 200 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', dataIndex: 'action', width: 170, fixed: 'right' as const },
]

const activityTypeOptions = [
  { label: '节日活动', value: '节日活动' },
  { label: '报课优惠', value: '报课优惠' },
]

const discountModeOptions = [
  { label: '折扣', value: '折扣' },
  { label: '立减', value: '立减' },
  { label: '赠送课时', value: '赠送课时' },
]

function discountUnit(mode: ActivityDiscountMode) {
  if (mode === '折扣') return '折'
  if (mode === '立减') return '元'
  return '课时'
}

function periodText(row: ActivityTemplate) {
  if (!row.startDate && !row.endDate) return '长期有效'
  return `${row.startDate || '不限'} 至 ${row.endDate || '不限'}`
}

async function loadCategories() {
  try {
    categories.value = await fetchActivityCategories()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function loadActivities() {
  activityLoading.value = true
  try {
    const data = await fetchActivityTemplates({
      keyword: activityKeyword.value.trim() || undefined,
      type: activityType.value,
      status: activityStatus.value,
      categoryId: selectedCategoryId.value,
    })
    activities.value = data.rows
    activityTotal.value = data.total
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    activityLoading.value = false
  }
}

function onCategorySelect(keys: (string | number)[]) {
  selectedKeys.value = keys.length ? [Number(keys[0])] : []
  selectedCategoryId.value = selectedKeys.value[0] ?? null
  loadActivities()
}

function clearCategory() {
  selectedKeys.value = []
  selectedCategoryId.value = null
  loadActivities()
}

function searchActivities() {
  loadActivities()
}

function resetActivityFilters() {
  activityKeyword.value = ''
  activityType.value = null
  activityStatus.value = null
  selectedKeys.value = []
  selectedCategoryId.value = null
  loadActivities()
}

// 分类维护
const categoryModalOpen = ref(false)
const categoryEditingId = ref<number | null>(null)
const categorySaving = ref(false)
const categoryForm = reactive({ name: '', parentId: null as number | null })

const categoryParentOptions = computed(() => {
  const rows: { label: string; value: number }[] = []
  const walk = (nodes: CategoryTreeNode[], depth: number) => {
    for (const node of nodes) {
      if (node.key !== categoryEditingId.value) {
        rows.push({ label: `${'　'.repeat(depth)}${node.title}`, value: node.key })
      }
      if (node.children) walk(node.children, depth + 1)
    }
  }
  walk(categoryTree.value, 0)
  return rows
})

function openCategoryCreate() {
  categoryEditingId.value = null
  categoryForm.name = ''
  categoryForm.parentId = selectedCategoryId.value
  categoryModalOpen.value = true
}

function openCategoryEdit() {
  const current = selectedCategory.value
  if (!current) return
  categoryEditingId.value = current.id
  categoryForm.name = current.name
  categoryForm.parentId = current.parentId
  categoryModalOpen.value = true
}

async function submitCategory() {
  if (!categoryForm.name.trim()) {
    message.warning('请填写分类名称')
    return
  }
  categorySaving.value = true
  try {
    const payload = { name: categoryForm.name.trim(), parentId: categoryForm.parentId }
    if (categoryEditingId.value) {
      await updateActivityCategory(categoryEditingId.value, payload)
      message.success('分类已保存')
    } else {
      await createActivityCategory(payload)
      message.success('分类已创建')
    }
    categoryModalOpen.value = false
    await loadCategories()
    await loadActivities()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    categorySaving.value = false
  }
}

function removeCategory() {
  const current = selectedCategory.value
  if (!current) return
  Modal.confirm({
    title: '删除分类',
    content: `确认删除分类「${current.name}」？存在子分类或活动模板时无法删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteActivityCategory(current.id)
        selectedKeys.value = []
        selectedCategoryId.value = null
        message.success('已删除')
        await loadCategories()
        await loadActivities()
      } catch (error) {
        message.error((error as Error).message)
      }
    },
  })
}

// 活动模板维护
const templateModalOpen = ref(false)
const templateEditingId = ref<number | null>(null)
const templateSaving = ref(false)
const templateForm = reactive({
  name: '',
  type: '节日活动' as ActivityTemplateType,
  categoryId: null as number | null,
  startDate: null as string | null,
  endDate: null as string | null,
  remark: '',
  rules: [] as {
    lessons: number | null
    discountMode: ActivityDiscountMode
    discountValue: number | null
  }[],
})

function resetRules(type: ActivityTemplateType) {
  templateForm.rules = [
    {
      lessons: type === '报课优惠' ? 10 : 0,
      discountMode: '折扣',
      discountValue: null,
    },
  ]
}

function onTemplateTypeChange() {
  resetRules(templateForm.type)
}

function openTemplateCreate() {
  templateEditingId.value = null
  templateForm.name = ''
  templateForm.type = '节日活动'
  templateForm.categoryId = selectedCategoryId.value
  templateForm.startDate = null
  templateForm.endDate = null
  templateForm.remark = ''
  resetRules(templateForm.type)
  templateModalOpen.value = true
}

function openTemplateEdit(row: ActivityTemplate) {
  templateEditingId.value = row.id
  templateForm.name = row.name
  templateForm.type = row.type
  templateForm.categoryId = row.categoryId
  templateForm.startDate = row.startDate
  templateForm.endDate = row.endDate
  templateForm.remark = row.remark || ''
  templateForm.rules = row.rules.length
    ? row.rules.map((rule) => ({
        lessons: Number(rule.lessons ?? 0),
        discountMode: rule.discountMode,
        discountValue: Number(rule.discountValue ?? 0),
      }))
    : [{ lessons: row.type === '报课优惠' ? 10 : 0, discountMode: '折扣', discountValue: null }]
  templateModalOpen.value = true
}

function addActivityRule() {
  templateForm.rules.push({
    lessons: 10,
    discountMode: '折扣',
    discountValue: null,
  })
}

function removeActivityRule(index: number) {
  if (templateForm.rules.length === 1) return
  templateForm.rules.splice(index, 1)
}

async function submitTemplate() {
  if (!templateForm.name.trim()) {
    message.warning('请填写活动模板名称')
    return
  }
  for (const rule of templateForm.rules) {
    if (templateForm.type === '报课优惠' && (!rule.lessons || rule.lessons <= 0)) {
      message.warning('报课优惠需填写大于 0 的课时门槛')
      return
    }
    if (!rule.discountValue || rule.discountValue <= 0) {
      message.warning('优惠值必须大于 0')
      return
    }
    if (rule.discountMode === '折扣' && rule.discountValue >= 10) {
      message.warning('折扣力度需小于 10 折')
      return
    }
  }
  const payload: ActivityTemplateFormPayload = {
    categoryId: templateForm.categoryId,
    name: templateForm.name.trim(),
    type: templateForm.type,
    startDate: templateForm.startDate || null,
    endDate: templateForm.endDate || null,
    rules: templateForm.rules.map((rule) => ({
      lessons: templateForm.type === '报课优惠' ? Number(rule.lessons ?? 0) : 0,
      discountMode: rule.discountMode,
      discountValue: Number(rule.discountValue ?? 0),
    })),
    remark: templateForm.remark.trim() || null,
  }
  templateSaving.value = true
  try {
    if (templateEditingId.value) {
      await updateActivityTemplate(templateEditingId.value, payload)
      message.success('活动模板已保存')
    } else {
      await createActivityTemplate(payload)
      message.success('活动模板已创建')
    }
    templateModalOpen.value = false
    await loadCategories()
    await loadActivities()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    templateSaving.value = false
  }
}

async function toggleTemplateStatus(row: ActivityTemplate) {
  try {
    await setActivityTemplateStatus(row.id, row.status === 1 ? 0 : 1)
    message.success(row.status === 1 ? '已停用' : '已启用')
    loadActivities()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function removeTemplate(row: ActivityTemplate) {
  Modal.confirm({
    title: '删除活动模板',
    content: `确认删除「${row.name}」？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteActivityTemplate(row.id)
        message.success('已删除')
        await loadCategories()
        await loadActivities()
      } catch (error) {
        message.error((error as Error).message)
      }
    },
  })
}

onMounted(async () => {
  await loadOptions()
  await load()
  await loadCategories()
  await loadActivities()
})
</script>

<template>
  <div class="page">
    <a-tabs v-model:active-key="activeTab" class="template-tabs">
      <a-tab-pane key="price" tab="基础价格模板">
        <div class="filter-card">
          <div class="filter-row">
            <div class="filter-item">
              <span>模板名称</span>
              <a-input
                v-model:value="keyword"
                placeholder="请输入模板名称"
                style="width: 200px"
                size="small"
                allow-clear
                @press-enter="search"
              />
            </div>
            <div class="filter-item">
              <span>状态</span>
              <a-select v-model:value="statusFilter" size="small" style="width: 120px" placeholder="全部状态" allow-clear>
                <a-select-option :value="1">启用</a-select-option>
                <a-select-option :value="0">停用</a-select-option>
              </a-select>
            </div>
            <a-button type="primary" size="small" :loading="loading" @click="search">
              <SearchOutlined />查询
            </a-button>
            <a-button size="small" @click="reset">重置</a-button>
          </div>
        </div>

        <div class="table-card">
          <div class="table-toolbar">
            <div class="table-toolbar-left">
              <a-button type="primary" size="small" @click="openCreate">
                <PlusOutlined />新建价格模板
              </a-button>
            </div>
            <span class="muted" style="font-size: 12px">当前结果：{{ total }} 个模板</span>
          </div>

          <a-spin :spinning="loading">
            <a-table
              :columns="columns"
              :data-source="rows"
              row-key="id"
              size="middle"
              :pagination="false"
              :scroll="{ x: 1560 }"
            >
              <template #bodyCell="{ column, record }">
                <span v-if="column.dataIndex === 'name'">{{ record.name }}</span>
                <span v-else-if="column.dataIndex === 'courseNames'">{{ listText(record.courseNames) }}</span>
                <span v-else-if="column.dataIndex === 'campusNames'">
                  {{ record.campusScope === '指定' ? listText(record.campusNames) : '全部校区' }}
                </span>
                <span v-else-if="column.dataIndex === 'classType'">{{ record.classType || '不限' }}</span>
                <span v-else-if="column.dataIndex === 'prices'">{{ tierText(record.prices) }}</span>
                <span v-else-if="column.dataIndex === 'materialFee'">￥{{ money(record.materialFee) }}</span>
                <span v-else-if="column.dataIndex === 'validDays'" :class="{ 'no-expire': !record.validDays }">
                  {{ record.validDays ? `${record.validDays} 天` : '不限' }}
                </span>
                <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 1 ? 'green' : 'default'">
                  {{ record.status === 1 ? '启用' : '停用' }}
                </a-tag>
                <span v-else-if="column.dataIndex === 'usedCount'">{{ record.usedCount ?? 0 }}</span>
                <div v-else-if="column.dataIndex === 'action'" class="row-actions">
                  <a @click="openEdit(record)">编辑</a>
                  <a v-if="record.status === 1" @click="makeDefault(record)">设为默认</a>
                  <a @click="toggleStatus(record)">{{ record.status === 1 ? '停用' : '启用' }}</a>
                  <a class="danger" @click="remove(record)">删除</a>
                </div>
              </template>
              <template #emptyText>
                <div style="padding: 32px 0; color: #8f959e">暂无价格模板</div>
              </template>
            </a-table>
          </a-spin>
        </div>
      </a-tab-pane>

      <a-tab-pane key="activity" tab="活动模板">
        <div class="activity-layout">
          <div class="category-card">
            <div class="category-head">
              <span class="category-title">活动分类</span>
              <a-button type="link" size="small" @click="openCategoryCreate">新增分类</a-button>
            </div>
            <div class="category-all" :class="{ active: !selectedCategoryId }" @click="clearCategory">
              全部分类（{{ categories.length }}）
            </div>
            <a-tree
              v-if="categoryTree.length"
              :tree-data="categoryTree"
              :selected-keys="selectedKeys"
              block-node
              @select="onCategorySelect"
            />
            <div v-else class="hint" style="padding: 8px 0">暂无分类，点击「新增分类」创建</div>
            <div class="category-actions">
              <a-button size="small" :disabled="!selectedCategory" @click="openCategoryEdit">编辑</a-button>
              <a-button size="small" danger :disabled="!selectedCategory" @click="removeCategory">删除</a-button>
            </div>
          </div>

          <div class="activity-main">
            <div class="filter-card">
              <div class="filter-row">
                <div class="filter-item">
                  <span>活动名称</span>
                  <a-input
                    v-model:value="activityKeyword"
                    size="small"
                    placeholder="活动名称"
                    allow-clear
                    style="width: 180px"
                    @press-enter="searchActivities"
                  />
                </div>
                <div class="filter-item">
                  <span>类型</span>
                  <a-select
                    v-model:value="activityType"
                    size="small"
                    allow-clear
                    placeholder="全部"
                    style="width: 120px"
                    :options="activityTypeOptions"
                  />
                </div>
                <div class="filter-item">
                  <span>状态</span>
                  <a-select
                    v-model:value="activityStatus"
                    size="small"
                    allow-clear
                    placeholder="全部"
                    style="width: 100px"
                    :options="[
                      { label: '启用', value: 1 },
                      { label: '停用', value: 0 },
                    ]"
                  />
                </div>
                <a-button type="primary" size="small" @click="searchActivities">
                  <SearchOutlined />
                  查询
                </a-button>
                <a-button size="small" @click="resetActivityFilters">重置</a-button>
              </div>
            </div>

            <div class="table-card">
              <div class="table-toolbar">
                <div class="table-toolbar-left">
                  <a-button type="primary" size="small" @click="openTemplateCreate">
                    <PlusOutlined />
                    新建活动模板
                  </a-button>
                </div>
                <span class="muted" style="font-size: 12px">当前结果：{{ activityTotal }} 个活动</span>
              </div>
              <a-spin :spinning="activityLoading">
                <a-table
                  :columns="activityColumns"
                  :data-source="activities"
                  row-key="id"
                  size="middle"
                  :pagination="false"
                  :scroll="{ x: 1160 }"
                >
                  <template #bodyCell="{ column, record }">
                    <span v-if="column.dataIndex === 'name'">{{ record.name }}</span>
                    <a-tag
                      v-else-if="column.dataIndex === 'type'"
                      :color="record.type === '节日活动' ? 'orange' : 'blue'"
                    >
                      {{ record.type }}
                    </a-tag>
                    <span v-else-if="column.dataIndex === 'categoryName'">{{ record.categoryName || '未分类' }}</span>
                    <span v-else-if="column.dataIndex === 'discountText'">{{ record.discountText || '—' }}</span>
                    <span v-else-if="column.dataIndex === 'period'">{{ periodText(record) }}</span>
                    <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 1 ? 'green' : 'default'">
                      {{ record.status === 1 ? '启用' : '停用' }}
                    </a-tag>
                    <div v-else-if="column.dataIndex === 'action'" class="row-actions">
                      <a @click="openTemplateEdit(record)">编辑</a>
                      <a @click="toggleTemplateStatus(record)">{{ record.status === 1 ? '停用' : '启用' }}</a>
                      <a class="danger" @click="removeTemplate(record)">删除</a>
                    </div>
                  </template>
                  <template #emptyText>
                    <div style="padding: 32px 0; color: #8f959e">暂无活动模板</div>
                  </template>
                </a-table>
              </a-spin>
            </div>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="formOpen"
      :title="formMode === 'create' ? '新建价格模板' : '编辑价格模板'"
      :width="760"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submit"
    >
      <div class="form-body">
        <div class="form-line">
          <label class="required">模板名称</label>
          <a-input v-model:value="form.name" placeholder="如：三年级语文标准价" :maxlength="120" />
        </div>

        <div class="form-line">
          <label class="required">适用课程</label>
          <a-select
            v-model:value="form.courseIds"
            mode="multiple"
            size="small"
            style="width: 100%"
            placeholder="请选择课程（可多选）"
            show-search
            option-filter-prop="label"
            :options="filteredCourses.map((item) => ({ label: item.name, value: item.id }))"
          />
        </div>

        <div class="form-line">
          <label>适用校区</label>
          <div class="scope-block">
            <a-radio-group v-model:value="form.campusScope" size="small">
              <a-radio value="全部">全部校区</a-radio>
              <a-radio value="指定">指定校区</a-radio>
            </a-radio-group>
            <a-select
              v-if="form.campusScope === '指定'"
              v-model:value="form.campusIds"
              mode="multiple"
              size="small"
              style="width: 100%; margin-top: 8px"
              placeholder="请选择校区"
              option-filter-prop="label"
              :options="options.campuses.map((item) => ({ label: item.name, value: item.id }))"
            />
          </div>
        </div>

        <div class="form-line">
          <label>班型</label>
          <a-auto-complete
            v-model:value="form.classType"
            style="width: 100%"
            placeholder="选填，如：小班/一对一；不填表示不限"
            :options="options.classTypes.map((item) => ({ value: item }))"
            allow-clear
          />
        </div>

        <div class="form-line">
          <label class="required">课时单价</label>
          <a-input-number v-model:value="form.unitPrice" :min="0" :precision="2" style="width: 160px" />
          <span class="tier-unit">元/课时</span>
          <div class="hint">价格模板只记录基础单价，报多少课时都按此单价计算；优惠请在「活动模板」中配置。</div>
        </div>

        <div class="form-line">
          <label>教材费</label>
          <a-input-number v-model:value="form.materialFee" :min="0" :precision="2" style="width: 160px" />
          <span class="tier-unit">元</span>
        </div>

        <div class="form-line">
          <label>报读有效期</label>
          <a-input-number v-model:value="form.validDays" :min="0" :precision="0" style="width: 160px" placeholder="不填表示不限" />
          <span class="tier-unit">天</span>
        </div>

        <div class="form-line">
          <label>备注</label>
          <a-textarea v-model:value="form.remark" :rows="2" placeholder="选填，仅内部可见" :maxlength="200" />
        </div>
      </div>
    </a-modal>

    <a-modal
      v-model:open="categoryModalOpen"
      :title="categoryEditingId ? '编辑分类' : '新增分类'"
      :confirm-loading="categorySaving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submitCategory"
    >
      <div class="form-body">
        <div class="form-line">
          <label class="required">分类名称</label>
          <a-input v-model:value="categoryForm.name" placeholder="如：1-3年级" :maxlength="64" />
        </div>
        <div class="form-line">
          <label>上级分类</label>
          <a-select
            v-model:value="categoryForm.parentId"
            allow-clear
            placeholder="不选则为一级分类"
            :options="categoryParentOptions"
          />
        </div>
        <div class="hint">分类可多层嵌套，活动模板挂在对应分类下，如「1-3年级」下挂「暑期报课优惠」。</div>
      </div>
    </a-modal>

    <a-modal
      v-model:open="templateModalOpen"
      :title="templateEditingId ? '编辑活动模板' : '新建活动模板'"
      :width="720"
      :confirm-loading="templateSaving"
      ok-text="保存"
      cancel-text="取消"
      destroy-on-close
      @ok="submitTemplate"
    >
      <div class="form-body">
        <div class="form-line">
          <label class="required">活动名称</label>
          <a-input v-model:value="templateForm.name" placeholder="如：618 年中大促 / 报20节享8折" :maxlength="120" />
        </div>

        <div class="form-line">
          <label class="required">活动类型</label>
          <a-radio-group v-model:value="templateForm.type" @change="onTemplateTypeChange">
            <a-radio-button value="节日活动">节日活动</a-radio-button>
            <a-radio-button value="报课优惠">报课优惠</a-radio-button>
          </a-radio-group>
        </div>

        <div class="form-line">
          <label>所属分类</label>
          <a-tree-select
            v-model:value="templateForm.categoryId"
            :tree-data="categoryTreeSelect"
            allow-clear
            tree-default-expand-all
            placeholder="选填，如 1-3年级"
          />
        </div>

        <div class="form-line">
          <label>有效期</label>
          <div class="date-range">
            <a-date-picker
              v-model:value="templateForm.startDate"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
            />
            <span class="tier-unit">至</span>
            <a-date-picker
              v-model:value="templateForm.endDate"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
            />
          </div>
        </div>

        <div class="form-line">
          <label class="required">优惠规则</label>
          <div class="tier-list">
            <div v-for="(rule, index) in templateForm.rules" :key="index" class="tier-row">
              <template v-if="templateForm.type === '报课优惠'">
                <span class="tier-unit">报满</span>
                <a-input-number v-model:value="rule.lessons" :min="1" :precision="0" style="width: 80px" />
                <span class="tier-unit">节</span>
              </template>
              <span v-else class="tier-unit">全场</span>
              <a-select
                v-model:value="rule.discountMode"
                style="width: 110px"
                :options="discountModeOptions"
              />
              <a-input-number v-model:value="rule.discountValue" :min="0.1" :precision="1" style="width: 100px" />
              <span class="tier-unit">{{ discountUnit(rule.discountMode) }}</span>
              <a
                v-if="templateForm.type === '报课优惠' && templateForm.rules.length > 1"
                class="danger"
                @click="removeActivityRule(index)"
              >
                删除
              </a>
            </div>
            <a
              v-if="templateForm.type === '报课优惠' && templateForm.rules.length < 6"
              @click="addActivityRule"
            >
              + 添加优惠档位
            </a>
            <div v-if="templateForm.type === '节日活动'" class="hint">
              节日活动只设置一条优惠，如全场 8.5 折、立减 200 元或赠 2 课时。
            </div>
            <div v-else class="hint">报课优惠按课时门槛分档，如报 10 节享 9 折、报 20 节享 8 折。</div>
          </div>
        </div>

        <div class="form-line">
          <label>备注</label>
          <a-textarea
            v-model:value="templateForm.remark"
            :rows="2"
            placeholder="选填，仅内部可见"
            :maxlength="200"
          />
        </div>

        <a-alert type="info" show-icon message="活动模板仅在本系统内维护，后续报名时可选叠加优惠" />
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.template-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 12px;
}

.row-actions {
  display: flex;
  gap: 10px;
}

.row-actions a {
  font-size: 13px;
}

.danger {
  color: #ff4d4f;
}

.no-expire {
  color: #8f959e;
}

.form-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 4px;
}

.form-line {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.form-line > label {
  flex: 0 0 88px;
  padding-top: 5px;
  color: #646a73;
  font-size: 13px;
  text-align: right;
}

.form-line > label.required::before {
  content: '*';
  margin-right: 2px;
  color: #ff4d4f;
}

.form-line > *:not(label) {
  flex: 1;
  min-width: 0;
}

.scope-block {
  display: flex;
  flex-direction: column;
}

.tier-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tier-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tier-unit {
  color: #8f959e;
  font-size: 12px;
}

.hint {
  color: #8f959e;
  font-size: 12px;
}

.activity-layout {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.category-card {
  flex: 0 0 240px;
  padding: 12px;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 8px;
}

.category-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.category-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
}

.category-all {
  padding: 4px 8px;
  margin-bottom: 4px;
  font-size: 13px;
  color: #1f2329;
  border-radius: 4px;
  cursor: pointer;
}

.category-all:hover,
.category-all.active {
  background: #f0f6ff;
  color: #1677ff;
}

.category-actions {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  margin-top: 8px;
  border-top: 1px solid #f0f0f0;
}

.activity-main {
  flex: 1;
  min-width: 0;
}

.date-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.date-range :deep(.ant-picker) {
  flex: 1;
}
</style>
