<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import { authState } from '../stores/auth'
import {
  cancelReceiveMaterialBuyRecord,
  createMaterial,
  createStockChange,
  deleteMaterial,
  deleteStockChange,
  fetchIncidentals,
  fetchMaterialBuyRecords,
  fetchMaterialBuySummary,
  fetchMaterialCategories,
  fetchMaterialChangeOptions,
  fetchMaterialChanges,
  fetchMaterialChangeSummary,
  fetchMaterialOptions,
  fetchMaterialSettings,
  fetchMaterialStock,
  fetchMaterialStockSummary,
  receiveMaterialBuyRecord,
  updateMaterial,
  updateStockChange,
  type IncidentalRow,
  type MaterialBuyRecordRow,
  type MaterialBuySummary,
  type MaterialChangeRow,
  type MaterialChangeSummary,
  type MaterialOptions,
  type MaterialSettingRow,
  type MaterialStockRow,
  type MaterialStockSummary,
} from '../api/client'

const tabs = ['购买记录', '库存', '教材设置', '杂费设置']
const tab = ref('购买记录')

const categories = ref<string[]>([])

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '-' : value
}

function money(value: number | null | undefined) {
  return value === null || value === undefined ? '-' : value.toFixed(2)
}

function pointsText(value: number | null | undefined) {
  return value === null || value === undefined ? '-' : value.toFixed(2)
}

function needSchoolpal(what: string) {
  message.info(`「${what}」需在校宝端操作，本系统仅同步展示`)
}

/* ---------------- 购买记录 ---------------- */
const buyOrderNo = ref('')
const buyRows = ref<MaterialBuyRecordRow[]>([])
const buySummary = ref<MaterialBuySummary | null>(null)
const buyLoading = ref(false)
const buyPage = ref(1)
const buyPageSize = ref(20)
const buyTotal = ref(0)

const buyColumns = [
  { title: '订单号', dataIndex: 'extOrderNo', width: 96 },
  { title: '交易类型', dataIndex: 'tradeType', width: 96 },
  { title: '学生姓名', dataIndex: 'studentName', width: 108 },
  { title: '购买商品', dataIndex: 'itemName', width: 260, ellipsis: true },
  { title: '购买数量', dataIndex: 'quantity', width: 88, align: 'right' as const },
  { title: '实付', dataIndex: 'paidText', width: 108, align: 'right' as const },
  { title: '领用状态', dataIndex: 'receiveStatus', width: 96 },
  { title: '经办校区', dataIndex: 'campusName', width: 180, ellipsis: true },
  { title: '经办人', dataIndex: 'handler', width: 88 },
  { title: '经办日期', dataIndex: 'handledDate', width: 112 },
  { title: '对内备注', dataIndex: 'remark', width: 120, ellipsis: true },
  { title: '操作', dataIndex: 'action', width: 100, fixed: 'right' as const },
]

async function loadBuy() {
  buyLoading.value = true
  try {
    const query = { orderNo: buyOrderNo.value.trim() || undefined }
    const [list, sum] = await Promise.all([
      fetchMaterialBuyRecords({ ...query, page: buyPage.value, size: buyPageSize.value }),
      fetchMaterialBuySummary(query),
    ])
    buyRows.value = list.records
    buyTotal.value = list.total
    buySummary.value = sum
  } catch (e) {
    console.error(e)
    message.error('教材购买记录加载失败')
  } finally {
    buyLoading.value = false
  }
}

function searchBuy() {
  buyPage.value = 1
  loadBuy()
}

function resetBuy() {
  buyOrderNo.value = ''
  buyPage.value = 1
  loadBuy()
}

/* ---------------- 库存 ---------------- */
const stockTabs = ['库存查询', '库存变动记录']
const stockTab = ref('库存查询')

const stockFilter = reactive({
  keyword: '',
  category: undefined as string | undefined,
  lowStock: undefined as boolean | undefined,
  status: undefined as string | undefined,
})
const stockRows = ref<MaterialStockRow[]>([])
const stockSummary = ref<MaterialStockSummary | null>(null)
const stockLoading = ref(false)
const stockPage = ref(1)
const stockPageSize = ref(50)
const stockTotal = ref(0)

const stockColumns = [
  { title: '教材名称', dataIndex: 'name', width: 300, ellipsis: true },
  { title: '仓库', dataIndex: 'warehouse', width: 90, align: 'right' as const },
  { title: '鲅鱼圈幻彩国学堂', dataIndex: 'bayuquan', width: 150, align: 'right' as const },
  { title: '新民幻彩国学堂', dataIndex: 'xinmin', width: 150, align: 'right' as const },
  { title: '总计', dataIndex: 'total', width: 100, align: 'right' as const },
]

async function loadStock() {
  stockLoading.value = true
  try {
    const query = {
      keyword: stockFilter.keyword.trim() || undefined,
      category: stockFilter.category,
      status: stockFilter.status,
      lowStock: stockFilter.lowStock,
    }
    const [list, sum] = await Promise.all([
      fetchMaterialStock({ ...query, page: stockPage.value, size: stockPageSize.value }),
      fetchMaterialStockSummary(query),
    ])
    stockRows.value = list.records
    stockTotal.value = list.total
    stockSummary.value = sum
  } catch (e) {
    console.error(e)
    message.error('库存数据加载失败')
  } finally {
    stockLoading.value = false
  }
}

function searchStock() {
  stockPage.value = 1
  loadStock()
}

function resetStock() {
  stockFilter.keyword = ''
  stockFilter.category = undefined
  stockFilter.lowStock = undefined
  stockFilter.status = undefined
  stockPage.value = 1
  loadStock()
}

/* ---------------- 库存变动记录 ---------------- */
const changeFilter = reactive({
  keyword: '',
  room: undefined as string | undefined,
  bizType: undefined as string | undefined,
  materialName: undefined as string | undefined,
  range: [undefined, undefined] as [string | undefined, string | undefined],
})
const changeRows = ref<MaterialChangeRow[]>([])
const changeSummary = ref<MaterialChangeSummary | null>(null)
const changeLoading = ref(false)
const changePage = ref(1)
const changePageSize = ref(20)
const changeTotal = ref(0)
const changeRooms = ref<string[]>([])
const changeMaterials = ref<string[]>([])

const changeColumns = [
  { title: '所属库房', dataIndex: 'roomName', width: 170, ellipsis: true },
  { title: '教材名称', dataIndex: 'materialName', width: 240, ellipsis: true },
  { title: '业务类型', dataIndex: 'bizType', width: 90 },
  { title: '数量', dataIndex: 'quantity', width: 80, align: 'right' as const },
  { title: '单价', dataIndex: 'unitPrice', width: 90, align: 'right' as const },
  { title: '出入库日期', dataIndex: 'changeDate', width: 112 },
  { title: '经办人', dataIndex: 'handler', width: 88 },
  { title: '学员', dataIndex: 'studentName', width: 120, ellipsis: true },
  { title: '订单号', dataIndex: 'extOrderNo', width: 92 },
  { title: '备注', dataIndex: 'remark', width: 200, ellipsis: true },
  { title: '操作', dataIndex: 'action', width: 110, fixed: 'right' as const },
]

async function loadChange() {
  changeLoading.value = true
  try {
    const query = {
      keyword: changeFilter.keyword.trim() || undefined,
      room: changeFilter.room,
      bizType: changeFilter.bizType,
      materialName: changeFilter.materialName,
      start: changeFilter.range[0] || undefined,
      end: changeFilter.range[1] || undefined,
    }
    const [list, sum] = await Promise.all([
      fetchMaterialChanges({ ...query, page: changePage.value, size: changePageSize.value }),
      fetchMaterialChangeSummary(query),
    ])
    changeRows.value = list.records
    changeTotal.value = list.total
    changeSummary.value = sum
  } catch (e) {
    console.error(e)
    message.error('库存变动记录加载失败')
  } finally {
    changeLoading.value = false
  }
}

async function loadChangeOptions() {
  try {
    const [data, opts] = await Promise.all([fetchMaterialChangeOptions(), fetchMaterialOptions()])
    changeMaterials.value = data.materials
    materialOptions.value = opts
    changeRooms.value = opts.rooms.length ? opts.rooms : data.rooms
  } catch (e) {
    console.error(e)
  }
}

function searchChange() {
  changePage.value = 1
  loadChange()
}

function resetChange() {
  changeFilter.keyword = ''
  changeFilter.room = undefined
  changeFilter.bizType = undefined
  changeFilter.materialName = undefined
  changeFilter.range = [undefined, undefined]
  changePage.value = 1
  loadChange()
}

/* ---------------- 教材设置 ---------------- */
const settingFilter = reactive({
  keyword: '',
  category: undefined as string | undefined,
  status: undefined as string | undefined,
})
const settingRows = ref<MaterialSettingRow[]>([])
const settingLoading = ref(false)
const settingPage = ref(1)
const settingPageSize = ref(20)
const settingTotal = ref(0)

const settingColumns = [
  { title: '教材名称', dataIndex: 'name', width: 360, ellipsis: true },
  { title: '进价', dataIndex: 'costPrice', width: 110, align: 'right' as const },
  { title: '售价', dataIndex: 'salePrice', width: 110, align: 'right' as const },
  { title: '积分兑换', dataIndex: 'points', width: 110, align: 'right' as const },
  { title: '课程类别', dataIndex: 'category', width: 160 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', dataIndex: 'action', width: 120, fixed: 'right' as const },
]

async function loadSetting() {
  settingLoading.value = true
  try {
    const list = await fetchMaterialSettings({
      keyword: settingFilter.keyword.trim() || undefined,
      category: settingFilter.category,
      status: settingFilter.status,
      page: settingPage.value,
      size: settingPageSize.value,
    })
    settingRows.value = list.records
    settingTotal.value = list.total
  } catch (e) {
    console.error(e)
    message.error('教材设置加载失败')
  } finally {
    settingLoading.value = false
  }
}

function searchSetting() {
  settingPage.value = 1
  loadSetting()
}

function resetSetting() {
  settingFilter.keyword = ''
  settingFilter.category = undefined
  settingFilter.status = undefined
  settingPage.value = 1
  loadSetting()
}

/* ---------------- 杂费设置 ---------------- */
const feeKeyword = ref('')
const feeRows = ref<IncidentalRow[]>([])
const feeLoading = ref(false)
const feePage = ref(1)
const feePageSize = ref(20)
const feeTotal = ref(0)

const feeColumns = [
  { title: '杂费名称', dataIndex: 'name', width: 360, ellipsis: true },
  { title: '售价', dataIndex: 'price', width: 110, align: 'right' as const },
  { title: '积分兑换', dataIndex: 'points', width: 110, align: 'right' as const },
  { title: '课程类别', dataIndex: 'category', width: 160 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', dataIndex: 'action', width: 120, fixed: 'right' as const },
]

async function loadFee() {
  feeLoading.value = true
  try {
    const list = await fetchIncidentals({
      keyword: feeKeyword.value.trim() || undefined,
      page: feePage.value,
      size: feePageSize.value,
    })
    feeRows.value = list.records
    feeTotal.value = list.total
  } catch (e) {
    console.error(e)
    message.error('杂费设置加载失败')
  } finally {
    feeLoading.value = false
  }
}

function searchFee() {
  feePage.value = 1
  loadFee()
}

function resetFee() {
  feeKeyword.value = ''
  feePage.value = 1
  loadFee()
}

/* ---------------- 写操作：教材设置 / 出入库 / 领用确认 ---------------- */
const materialOptions = ref<MaterialOptions | null>(null)
const settingOpen = ref(false)
const settingSaving = ref(false)
const settingEditingId = ref<number | null>(null)
const settingForm = reactive({
  name: '',
  costPrice: 0,
  salePrice: 0,
  points: 0,
  category: undefined as string | undefined,
  status: '正常',
})

async function loadMaterialOptions() {
  try {
    materialOptions.value = await fetchMaterialOptions()
  } catch (e) {
    console.error(e)
  }
}

function openSetting(row?: MaterialSettingRow) {
  settingEditingId.value = row ? row.id : null
  settingForm.name = row?.name ?? ''
  settingForm.costPrice = Number(row?.costPrice ?? 0)
  settingForm.salePrice = Number(row?.salePrice ?? 0)
  settingForm.points = Number(row?.points ?? 0)
  settingForm.category = row?.category ?? undefined
  settingForm.status = row?.status ?? '正常'
  settingOpen.value = true
  loadMaterialOptions()
}

async function submitSetting() {
  if (!settingForm.name.trim()) {
    message.warning('请输入教材名称')
    return
  }
  settingSaving.value = true
  try {
    const payload = {
      name: settingForm.name.trim(),
      costPrice: Number(settingForm.costPrice ?? 0),
      salePrice: Number(settingForm.salePrice ?? 0),
      points: Number(settingForm.points ?? 0),
      category: settingForm.category,
      status: settingForm.status,
    }
    if (settingEditingId.value) {
      await updateMaterial(settingEditingId.value, payload)
      message.success('教材已更新')
    } else {
      await createMaterial(payload)
      message.success('教材已新建')
    }
    settingOpen.value = false
    categories.value = await fetchMaterialCategories()
    await Promise.all([loadSetting(), loadStock()])
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存教材失败')
  } finally {
    settingSaving.value = false
  }
}

function removeSetting(row: MaterialSettingRow) {
  Modal.confirm({
    title: '删除教材',
    content: `确认删除教材「${row.name}」？有库存变动记录或库存不为 0 时无法删除。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteMaterial(row.id)
        message.success('教材已删除')
        await Promise.all([loadSetting(), loadStock()])
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除教材失败')
      }
    },
  })
}

const changeOpen = ref(false)
const changeSaving = ref(false)
const changeEditingId = ref<number | null>(null)
const changeForm = reactive({
  roomName: '总库' as string | undefined,
  materialName: undefined as string | undefined,
  bizType: '入库',
  quantity: 1,
  unitPrice: undefined as number | undefined,
  changeDate: new Date().toISOString().slice(0, 10),
  handler: '',
  studentName: '',
  extOrderNo: '',
  remark: '',
})

function openChange(row?: MaterialChangeRow) {
  changeEditingId.value = row ? row.id : null
  changeForm.roomName = row?.roomName ?? '总库'
  changeForm.materialName = row?.materialName
  changeForm.bizType = row?.bizType ?? '入库'
  changeForm.quantity = row ? Math.abs(Number(row.quantity ?? 0)) : 1
  changeForm.unitPrice = row?.unitPrice ?? undefined
  changeForm.changeDate = row?.changeDate || new Date().toISOString().slice(0, 10)
  changeForm.handler = row?.handler ?? authState.user?.name ?? ''
  changeForm.studentName = row?.studentName ?? ''
  changeForm.extOrderNo = row?.extOrderNo ?? ''
  changeForm.remark = row?.remark ?? ''
  changeOpen.value = true
  loadMaterialOptions()
}

async function submitChange() {
  if (!changeForm.materialName) {
    message.warning('请选择教材')
    return
  }
  if (!(Number(changeForm.quantity) > 0)) {
    message.warning('数量必须大于 0')
    return
  }
  changeSaving.value = true
  try {
    const payload = {
      roomName: changeForm.roomName,
      materialName: changeForm.materialName,
      bizType: changeForm.bizType,
      quantity: Number(changeForm.quantity),
      unitPrice: changeForm.unitPrice === undefined ? undefined : Number(changeForm.unitPrice),
      changeDate: changeForm.changeDate,
      handler: changeForm.handler.trim() || undefined,
      studentName: changeForm.studentName.trim() || undefined,
      extOrderNo: changeForm.extOrderNo.trim() || undefined,
      remark: changeForm.remark.trim() || undefined,
    }
    if (changeEditingId.value) {
      await updateStockChange(changeEditingId.value, payload)
      message.success('库存变动已更新')
    } else {
      await createStockChange(payload)
      message.success('库存变动已记录')
    }
    changeOpen.value = false
    await Promise.all([loadChange(), loadStock(), loadChangeOptions()])
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存库存变动失败')
  } finally {
    changeSaving.value = false
  }
}

function removeChange(row: MaterialChangeRow) {
  Modal.confirm({
    title: '删除库存变动',
    content: `确认删除「${row.materialName}」${row.bizType} ${Math.abs(row.quantity)} 的记录？删除后会同步回滚库存。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteStockChange(row.id)
        message.success('库存变动已删除，库存已回滚')
        await Promise.all([loadChange(), loadStock()])
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除库存变动失败')
      }
    },
  })
}

async function toggleReceive(row: MaterialBuyRecordRow) {
  if (row.receiveStatus === '未领用') {
    try {
      await receiveMaterialBuyRecord(row.id)
      message.success(`「${row.studentName ?? row.extOrderNo}」已确认领用`)
      await loadBuy()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '确认领用失败')
    }
    return
  }
  Modal.confirm({
    title: '取消领用',
    content: `确认将订单 ${row.extOrderNo} 的「${row.itemName}」改回未领用？`,
    okText: '确认取消',
    okType: 'danger',
    cancelText: '返回',
    onOk: async () => {
      try {
        await cancelReceiveMaterialBuyRecord(row.id)
        message.success('已取消领用')
        await loadBuy()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '取消领用失败')
      }
    },
  })
}

/* ---------------- 页签切换 ---------------- */
const loaded = ref<Record<string, boolean>>({ '购买记录': true, '库存变动记录': false })

function switchStockTab(value: string) {
  stockTab.value = value
  if (value === '库存变动记录' && !loaded.value['库存变动记录']) {
    loaded.value['库存变动记录'] = true
    loadChangeOptions()
    loadChange()
  }
}

function switchTab(value: string) {
  tab.value = value
  if (!loaded.value[value]) {
    loaded.value[value] = true
    if (value === '库存') loadStock()
    if (value === '教材设置') loadSetting()
    if (value === '杂费设置') loadFee()
  }
}

const stockFooter = computed(() => {
  const s = stockSummary.value
  return s
    ? {
        name: `总计（${s.count} 条）`,
        warehouse: s.warehouse,
        bayuquan: s.bayuquan,
        xinmin: s.xinmin,
        total: s.total,
      }
    : null
})

onMounted(async () => {
  loadBuy()
  try {
    categories.value = await fetchMaterialCategories()
  } catch (e) {
    console.error(e)
  }
})
</script>

<template>
  <div class="material-page">
    <div class="tab-line">
      <span
        v-for="item in tabs"
        :key="item"
        class="tab"
        :class="{ active: tab === item }"
        @click="switchTab(item)"
      >
        {{ item }}
      </span>
    </div>

    <!-- 购买记录 -->
    <template v-if="tab === '购买记录'">
      <div class="filter-line">
        <span class="filter-label">订单号</span>
        <a-input
          v-model:value="buyOrderNo"
          class="order-input"
          placeholder="请输入订单号"
          allow-clear
          @press-enter="searchBuy"
        >
          <template #suffix>
            <SearchOutlined class="filter-search-icon" @click="searchBuy" />
          </template>
        </a-input>
        <a class="reset-link" @click="resetBuy">清空筛选</a>
      </div>

      <div class="summary-line">共 {{ buySummary?.total ?? 0 }} 个购买记录</div>

      <a-table
        class="buy-table"
        :columns="buyColumns"
        :data-source="buyRows"
        :loading="buyLoading"
        :pagination="{
          current: buyPage,
          pageSize: buyPageSize,
          total: buyTotal,
          showSizeChanger: true,
          pageSizeOptions: ['20', '50', '100'],
          showTotal: (t: number) => `共 ${t} 条`,
          size: 'small',
        }"
        row-key="id"
        size="small"
        :scroll="{ x: 1560 }"
        @change="(p: { current?: number; pageSize?: number }) => { buyPage = p.current ?? 1; buyPageSize = p.pageSize ?? 20; loadBuy() }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'extOrderNo'" class="order-no">{{ record.extOrderNo }}</span>
          <span v-else-if="column.dataIndex === 'tradeType'">{{ show(record.tradeType) }}</span>
          <span v-else-if="column.dataIndex === 'studentName'">{{ show(record.studentName) }}</span>
          <span v-else-if="column.dataIndex === 'itemName'" class="ellipsis">{{ record.itemName }}</span>
          <span v-else-if="column.dataIndex === 'quantity'">{{ record.quantity }}</span>
          <span v-else-if="column.dataIndex === 'paidText'" class="paid">{{ show(record.paidText) }}</span>
          <span v-else-if="column.dataIndex === 'receiveStatus'">
            <span :class="record.receiveStatus === '未领用' ? 'status-unclaimed' : 'status-claimed'">
              {{ show(record.receiveStatus) }}
            </span>
          </span>
          <span v-else-if="column.dataIndex === 'campusName'" class="ellipsis">{{ show(record.campusName) }}</span>
          <span v-else-if="column.dataIndex === 'handler'">{{ show(record.handler) }}</span>
          <span v-else-if="column.dataIndex === 'handledDate'">{{ show(record.handledDate) }}</span>
          <span v-else-if="column.dataIndex === 'remark'">{{ show(record.remark) }}</span>
          <span v-else-if="column.dataIndex === 'action'">
            <a-button type="link" size="small" class="receive-btn" @click="toggleReceive(record)">
              {{ record.receiveStatus === '未领用' ? '确认领用' : '取消领用' }}
            </a-button>
          </span>
        </template>
        <template #emptyText>
          <a-empty description="当前筛选条件下暂无购买记录" />
        </template>
      </a-table>
    </template>

    <!-- 库存 -->
    <template v-else-if="tab === '库存'">
      <div class="sub-tab-line">
        <span
          v-for="item in stockTabs"
          :key="item"
          class="sub-tab"
          :class="{ active: stockTab === item }"
          @click="switchStockTab(item)"
        >
          {{ item }}
        </span>
        <div class="sub-tab-actions">
          <a-button size="small" type="primary" class="with-icon" @click="openChange()">
            <PlusOutlined />出入库
          </a-button>
          <a-button size="small" @click="needSchoolpal('导出')">导出</a-button>
        </div>
      </div>

      <template v-if="stockTab === '库存查询'">
        <div class="filter-line">
          <a-input
            v-model:value="stockFilter.keyword"
            class="stock-search"
            placeholder="请输入教材名称"
            allow-clear
            @press-enter="searchStock"
          >
            <template #suffix>
              <SearchOutlined class="filter-search-icon" @click="searchStock" />
            </template>
          </a-input>
          <span class="filter-label">课程类别</span>
          <a-select
            v-model:value="stockFilter.category"
            class="filter-select"
            placeholder="全部"
            allow-clear
            :options="categories.map((c) => ({ value: c, label: c }))"
          />
          <span class="filter-label">库存不足</span>
          <a-select
            v-model:value="stockFilter.lowStock"
            class="filter-select narrow"
            placeholder="全部"
            allow-clear
            :options="[
              { value: true, label: '是' },
              { value: false, label: '否' },
            ]"
          />
          <span class="filter-label">状态</span>
          <a-select
            v-model:value="stockFilter.status"
            class="filter-select narrow"
            placeholder="全部"
            allow-clear
            :options="[
              { value: '正常', label: '正常' },
              { value: '下架', label: '下架' },
            ]"
          />
          <a-button type="primary" size="small" @click="searchStock">查询</a-button>
          <a class="reset-link" @click="resetStock">清空筛选</a>
        </div>

        <a-table
          class="buy-table"
          :columns="stockColumns"
          :data-source="stockRows"
          :loading="stockLoading"
          :pagination="{
            current: stockPage,
            pageSize: stockPageSize,
            total: stockTotal,
            showSizeChanger: true,
            pageSizeOptions: ['50', '100', '200'],
            showTotal: (t: number) => `共 ${t} 条`,
            size: 'small',
          }"
          row-key="id"
          size="small"
          table-layout="fixed"
          @change="(p: { current?: number; pageSize?: number }) => { stockPage = p.current ?? 1; stockPageSize = p.pageSize ?? 50; loadStock() }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'name'" class="ellipsis">{{ record.name }}</span>
            <span v-else-if="column.dataIndex === 'warehouse'" :class="{ 'qty-negative': record.warehouse < 0 }">
              {{ record.warehouse }}
            </span>
            <span v-else-if="column.dataIndex === 'bayuquan'" :class="{ 'qty-negative': record.bayuquan < 0 }">
              {{ record.bayuquan }}
            </span>
            <span v-else-if="column.dataIndex === 'xinmin'" :class="{ 'qty-negative': record.xinmin < 0 }">
              {{ record.xinmin }}
            </span>
            <span v-else-if="column.dataIndex === 'total'" :class="{ 'qty-negative': record.total < 0 }">
              {{ record.total }}
            </span>
          </template>
          <template #summary>
            <a-table-summary-row v-if="stockFooter" class="stock-footer">
              <a-table-summary-cell index="0">{{ stockFooter.name }}</a-table-summary-cell>
              <a-table-summary-cell index="1" align="right">{{ stockFooter.warehouse }}</a-table-summary-cell>
              <a-table-summary-cell index="2" align="right">{{ stockFooter.bayuquan }}</a-table-summary-cell>
              <a-table-summary-cell index="3" align="right">{{ stockFooter.xinmin }}</a-table-summary-cell>
              <a-table-summary-cell index="4" align="right">{{ stockFooter.total }}</a-table-summary-cell>
            </a-table-summary-row>
          </template>
          <template #emptyText>
            <a-empty description="当前筛选条件下暂无库存记录" />
          </template>
        </a-table>
      </template>

      <template v-else>
        <div class="filter-line">
          <a-input
            v-model:value="changeFilter.keyword"
            class="stock-search"
            placeholder="搜索教材/学员/订单号/经办人"
            allow-clear
            @press-enter="searchChange"
          >
            <template #suffix>
              <SearchOutlined class="filter-search-icon" @click="searchChange" />
            </template>
          </a-input>
          <span class="filter-label">出入库日期</span>
          <a-range-picker
            v-model:value="changeFilter.range"
            class="date-range"
            value-format="YYYY-MM-DD"
            :placeholder="['开始日期', '结束日期']"
          />
          <span class="filter-label">所属库房</span>
          <a-select
            v-model:value="changeFilter.room"
            class="filter-select"
            placeholder="全部"
            allow-clear
            :options="changeRooms.map((c) => ({ value: c, label: c }))"
          />
          <span class="filter-label">业务类型</span>
          <a-select
            v-model:value="changeFilter.bizType"
            class="filter-select narrow"
            placeholder="全部"
            allow-clear
            :options="[
              { value: '入库', label: '入库' },
              { value: '出库', label: '出库' },
            ]"
          />
          <span class="filter-label">教材名称</span>
          <a-select
            v-model:value="changeFilter.materialName"
            class="filter-select wide"
            placeholder="全部"
            show-search
            allow-clear
            :options="changeMaterials.map((c) => ({ value: c, label: c }))"
          />
          <a-button type="primary" size="small" @click="searchChange">查询</a-button>
          <a class="reset-link" @click="resetChange">清空筛选</a>
        </div>

        <div class="summary-line">
          当前结果：共 {{ changeSummary?.count ?? 0 }} 条记录，入库总计：{{ changeSummary?.inSum ?? 0 }}
          出库总计：{{ changeSummary?.outSum ?? 0 }}
          变动总计：{{ changeSummary?.total ?? 0 }}
        </div>

        <a-table
          class="buy-table"
          :columns="changeColumns"
          :data-source="changeRows"
          :loading="changeLoading"
          :pagination="{
            current: changePage,
            pageSize: changePageSize,
            total: changeTotal,
            showSizeChanger: true,
            pageSizeOptions: ['20', '50', '100'],
            showTotal: (t: number) => `共 ${t} 条`,
            size: 'small',
          }"
          row-key="id"
          size="small"
          :scroll="{ x: 1580 }"
          @change="(p: { current?: number; pageSize?: number }) => { changePage = p.current ?? 1; changePageSize = p.pageSize ?? 20; loadChange() }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'roomName'" class="ellipsis">{{ record.roomName }}</span>
            <span v-else-if="column.dataIndex === 'materialName'" class="ellipsis">{{ record.materialName }}</span>
            <span v-else-if="column.dataIndex === 'bizType'">
              <span :class="record.bizType === '入库' ? 'biz-in' : 'biz-out'">{{ record.bizType }}</span>
            </span>
            <span v-else-if="column.dataIndex === 'quantity'" :class="record.quantity < 0 ? 'qty-negative' : ''">
              {{ record.quantity }}
            </span>
            <span v-else-if="column.dataIndex === 'unitPrice'">{{ money(record.unitPrice) }}</span>
            <span v-else-if="column.dataIndex === 'changeDate'">{{ show(record.changeDate) }}</span>
            <span v-else-if="column.dataIndex === 'handler'">{{ show(record.handler) }}</span>
            <span v-else-if="column.dataIndex === 'studentName'" class="ellipsis">{{ show(record.studentName) }}</span>
            <span v-else-if="column.dataIndex === 'extOrderNo'">{{ show(record.extOrderNo) }}</span>
            <span v-else-if="column.dataIndex === 'remark'" class="ellipsis">{{ show(record.remark) }}</span>
            <span v-else-if="column.dataIndex === 'action'" class="row-actions">
              <a-button type="link" size="small" class="row-btn" @click="openChange(record)">编辑</a-button>
              <a-button type="link" size="small" danger class="row-btn" @click="removeChange(record)">删除</a-button>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="当前筛选条件下暂无库存变动记录" />
          </template>
        </a-table>
      </template>
    </template>

    <!-- 教材设置 -->
    <template v-else-if="tab === '教材设置'">
      <div class="filter-line">
        <a-button size="small" type="primary" class="with-icon" @click="openSetting()">
          <PlusOutlined />新建教材
        </a-button>
        <a-button size="small" @click="needSchoolpal('批量导入')">批量导入</a-button>
        <a-input
          v-model:value="settingFilter.keyword"
          class="stock-search"
          placeholder="请输入教材名称"
          allow-clear
          @press-enter="searchSetting"
        >
          <template #suffix>
            <SearchOutlined class="filter-search-icon" @click="searchSetting" />
          </template>
        </a-input>
        <span class="filter-label">适用课程类别</span>
        <a-select
          v-model:value="settingFilter.category"
          class="filter-select"
          placeholder="全部"
          allow-clear
          :options="categories.map((c) => ({ value: c, label: c }))"
        />
        <span class="filter-label">状态</span>
        <a-select
          v-model:value="settingFilter.status"
          class="filter-select narrow"
          placeholder="全部"
          allow-clear
          :options="[
            { value: '正常', label: '正常' },
            { value: '下架', label: '下架' },
          ]"
        />
        <a-button type="primary" size="small" @click="searchSetting">查询</a-button>
        <a class="reset-link" @click="resetSetting">清空筛选</a>
      </div>

      <div class="summary-line">当前结果：共 {{ settingTotal }} 条记录</div>

      <a-table
        class="buy-table"
        :columns="settingColumns"
        :data-source="settingRows"
        :loading="settingLoading"
        :pagination="{
          current: settingPage,
          pageSize: settingPageSize,
          total: settingTotal,
          showSizeChanger: true,
          pageSizeOptions: ['20', '50', '100'],
          showTotal: (t: number) => `共 ${t} 条`,
          size: 'small',
        }"
        row-key="id"
        size="small"
        :scroll="{ x: 1060 }"
        @change="(p: { current?: number; pageSize?: number }) => { settingPage = p.current ?? 1; settingPageSize = p.pageSize ?? 20; loadSetting() }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'name'" class="ellipsis">{{ record.name }}</span>
          <span v-else-if="column.dataIndex === 'costPrice'">{{ money(record.costPrice) }}</span>
          <span v-else-if="column.dataIndex === 'salePrice'">{{ money(record.salePrice) }}</span>
          <span v-else-if="column.dataIndex === 'points'">{{ pointsText(record.points) }}</span>
          <span v-else-if="column.dataIndex === 'category'">{{ show(record.category) }}</span>
          <span v-else-if="column.dataIndex === 'status'">
            <span :class="record.status === '正常' ? 'status-on' : 'status-off'">{{ record.status }}</span>
          </span>
          <span v-else-if="column.dataIndex === 'action'" class="row-actions">
            <a-button type="link" size="small" class="row-btn" @click="openSetting(record)">编辑</a-button>
            <a-button type="link" size="small" danger class="row-btn" @click="removeSetting(record)">删除</a-button>
          </span>
        </template>
        <template #emptyText>
          <a-empty description="当前筛选条件下暂无教材" />
        </template>
      </a-table>
    </template>

    <!-- 杂费设置 -->
    <template v-else>
      <div class="filter-line">
        <a-button size="small" @click="needSchoolpal('新建杂费')">新建杂费</a-button>
        <a-input
          v-model:value="feeKeyword"
          class="stock-search"
          placeholder="请输入杂费名称"
          allow-clear
          @press-enter="searchFee"
        >
          <template #suffix>
            <SearchOutlined class="filter-search-icon" @click="searchFee" />
          </template>
        </a-input>
        <a-button type="primary" size="small" @click="searchFee">查询</a-button>
        <a class="reset-link" @click="resetFee">清空筛选</a>
      </div>

      <div class="summary-line">当前结果：共 {{ feeTotal }} 条记录</div>

      <a-table
        class="buy-table"
        :columns="feeColumns"
        :data-source="feeRows"
        :loading="feeLoading"
        :pagination="{
          current: feePage,
          pageSize: feePageSize,
          total: feeTotal,
          showSizeChanger: true,
          pageSizeOptions: ['20', '50', '100'],
          showTotal: (t: number) => `共 ${t} 条`,
          size: 'small',
        }"
        row-key="id"
        size="small"
        :scroll="{ x: 1060 }"
        @change="(p: { current?: number; pageSize?: number }) => { feePage = p.current ?? 1; feePageSize = p.pageSize ?? 20; loadFee() }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'name'" class="ellipsis">{{ record.name }}</span>
          <span v-else-if="column.dataIndex === 'price'">{{ money(record.price) }}</span>
          <span v-else-if="column.dataIndex === 'points'">{{ pointsText(record.points) }}</span>
          <span v-else-if="column.dataIndex === 'category'">{{ show(record.category) }}</span>
          <span v-else-if="column.dataIndex === 'status'">
            <span :class="record.status === '正常' ? 'status-on' : 'status-off'">{{ record.status }}</span>
          </span>
          <span v-else-if="column.dataIndex === 'action'" class="row-actions">
            <a @click="needSchoolpal('编辑杂费')">编辑</a>
            <a class="danger" @click="needSchoolpal('删除杂费')">删除</a>
          </span>
        </template>
        <template #emptyText>
          <a-empty description="当前筛选条件下暂无杂费" />
        </template>
      </a-table>
    </template>

    <a-modal
      v-model:open="settingOpen"
      :title="settingEditingId ? '编辑教材' : '新建教材'"
      width="560"
      :confirm-loading="settingSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitSetting"
    >
      <a-form layout="vertical" class="material-form">
        <a-form-item label="教材名称" required>
          <a-input v-model:value="settingForm.name" :maxlength="64" placeholder="请输入教材名称" />
        </a-form-item>
        <div class="form-grid">
          <a-form-item label="进价">
            <a-input-number v-model:value="settingForm.costPrice" :min="0" :precision="2" style="width: 100%" />
          </a-form-item>
          <a-form-item label="售价">
            <a-input-number v-model:value="settingForm.salePrice" :min="0" :precision="2" style="width: 100%" />
          </a-form-item>
          <a-form-item label="积分兑换">
            <a-input-number v-model:value="settingForm.points" :min="0" :precision="2" style="width: 100%" />
          </a-form-item>
          <a-form-item label="课程类别">
            <a-auto-complete
              v-model:value="settingForm.category"
              :options="(materialOptions?.categories ?? categories).map((c) => ({ value: c }))"
              placeholder="选择或输入类别"
              allow-clear
            />
          </a-form-item>
        </div>
        <a-form-item label="状态">
          <a-radio-group v-model:value="settingForm.status">
            <a-radio value="正常">正常</a-radio>
            <a-radio value="下架">下架</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="changeOpen"
      :title="changeEditingId ? '编辑库存变动' : '教材出入库'"
      width="620"
      :confirm-loading="changeSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitChange"
    >
      <a-form layout="vertical" class="material-form">
        <div class="form-grid">
          <a-form-item label="业务类型" required>
            <a-radio-group v-model:value="changeForm.bizType">
              <a-radio value="入库">入库</a-radio>
              <a-radio value="出库">出库</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="所属库房" required>
            <a-select v-model:value="changeForm.roomName">
              <a-select-option v-for="item in materialOptions?.rooms ?? []" :key="item" :value="item">
                {{ item }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="教材名称" class="span-2" required>
            <a-select
              v-model:value="changeForm.materialName"
              placeholder="请选择教材"
              show-search
              allow-clear
              :options="(materialOptions?.materials ?? []).map((m) => ({ value: m.name, label: m.name }))"
            />
          </a-form-item>
          <a-form-item label="数量" required>
            <a-input-number v-model:value="changeForm.quantity" :min="1" :precision="0" style="width: 100%" />
          </a-form-item>
          <a-form-item label="单价">
            <a-input-number v-model:value="changeForm.unitPrice" :min="0" :precision="2" style="width: 100%" placeholder="默认取进价" />
          </a-form-item>
          <a-form-item label="出入库日期" required>
            <a-date-picker v-model:value="changeForm.changeDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </a-form-item>
          <a-form-item label="经办人">
            <a-input v-model:value="changeForm.handler" :maxlength="32" />
          </a-form-item>
          <a-form-item label="学员">
            <a-input v-model:value="changeForm.studentName" :maxlength="64" />
          </a-form-item>
          <a-form-item label="订单号">
            <a-input v-model:value="changeForm.extOrderNo" :maxlength="64" />
          </a-form-item>
          <a-form-item label="备注" class="span-2">
            <a-input v-model:value="changeForm.remark" :maxlength="255" />
          </a-form-item>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.material-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.tab-line {
  display: flex;
  align-items: center;
  gap: 28px;
  margin: 0 -24px;
  padding: 0 24px;
  height: 50px;
  border-bottom: 1px solid var(--line);
}

.tab {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
}

.tab.active {
  color: var(--brand);
  font-weight: 500;
}

.tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 40px;
  height: 2px;
  transform: translateX(-50%);
  background: var(--brand);
  border-radius: 1px;
}

.sub-tab-line {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 0 4px;
}

.sub-tab {
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  padding-bottom: 6px;
  border-bottom: 2px solid transparent;
}

.sub-tab.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
  font-weight: 500;
}

.sub-tab-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 0 12px;
  font-size: 13px;
}

.filter-label {
  color: var(--text-2);
}

.order-input {
  width: 240px;
}

.stock-search {
  width: 220px;
}

.filter-select {
  width: 160px;
}

.filter-select.narrow {
  width: 100px;
}

.filter-select.wide {
  width: 240px;
}

.date-range {
  width: 240px;
}

.biz-in {
  color: var(--brand);
}

.biz-out {
  color: #ff7d00;
}

.filter-search-icon {
  color: #8f959e;
  cursor: pointer;
}

.reset-link {
  color: var(--brand);
  margin-left: 4px;
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.buy-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.buy-table :deep(.ant-table-tbody > tr > td) {
  padding: 10px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
}

.buy-table :deep(.ant-table-summary > tr > td) {
  background: #fafbfc;
  padding: 10px 8px;
  font-size: 13px;
  font-weight: 500;
  border-top: 1px solid #f0f1f2;
}

.order-no {
  color: var(--brand);
}

.paid {
  color: #f53f3f;
}

.status-claimed {
  color: var(--text-2);
}

.status-unclaimed {
  color: #ff7d00;
}

.status-on {
  color: var(--brand);
}

.status-off {
  color: #8f959e;
}

.qty-negative {
  color: #f53f3f;
}

.row-actions {
  display: flex;
  gap: 12px;
}

.row-actions .danger {
  color: #f53f3f;
}

.receive-btn {
  height: 22px;
  padding: 0 2px;
  font-size: 13px;
}

.row-btn {
  height: 22px;
  padding: 0 2px;
  font-size: 13px;
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.pending-panel {
  padding: 90px 0;
}

.with-icon :deep(.anticon) {
  margin-right: 2px;
}

.material-form :deep(.ant-form-item) {
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

.pending-text {
  font-size: 13px;
  line-height: 1.9;
  color: #8f959e;
}
</style>
