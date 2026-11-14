<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchActivityCategories,
  fetchActivityStats,
  fetchActivityStatsStudents,
  type ActivityCategory,
  type ActivityStatsRow,
  type ActivityStatsStudent,
} from '../api/client'

const STATUS_COLOR: Record<string, string> = {
  生效中: 'green',
  未开始: 'blue',
  已结束: 'default',
  已停用: 'red',
}

const TYPE_COLOR: Record<string, string> = {
  节日活动: 'orange',
  报课优惠: 'blue',
}

const keyword = ref('')
const categoryId = ref<number | null>(null)
const type = ref<string | null>(null)
const startDate = ref<string | null>(null)
const endDate = ref<string | null>(null)

const rows = ref<ActivityStatsRow[]>([])
const loading = ref(false)
const categories = ref<ActivityCategory[]>([])

const drawerOpen = ref(false)
const currentActivity = ref<ActivityStatsRow | null>(null)
const studentKeyword = ref('')
const studentRows = ref<ActivityStatsStudent[]>([])
const studentTotal = ref(0)
const studentPage = ref(1)
const studentPageSize = ref(20)
const studentLoading = ref(false)
const studentSummary = ref({
  studentCount: 0,
  orderCount: 0,
  lessons: 0,
  shouldAmount: 0,
  receivedAmount: 0,
  discountAmount: 0,
})

const categoryOptions = computed(() => {
  const names = new Map(categories.value.map((item) => [item.id, item.name]))
  return categories.value.map((item) => ({
    label: item.parentId && names.get(item.parentId) ? `${names.get(item.parentId)} / ${item.name}` : item.name,
    value: item.id,
  }))
})

const summary = computed(() =>
  rows.value.reduce(
    (acc, row) => ({
      students: acc.students + Number(row.studentCount || 0),
      orders: acc.orders + Number(row.orderCount || 0),
      received: acc.received + Number(row.receivedAmount || 0),
    }),
    { students: 0, orders: 0, received: 0 },
  ),
)

const columns = [
  { title: '活动名称', dataIndex: 'name', width: 220, ellipsis: true },
  { title: '所属分类', dataIndex: 'categoryName', width: 150 },
  { title: '活动类型', dataIndex: 'type', width: 110 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '有效期', dataIndex: 'period', width: 200 },
  { title: '参与学员', dataIndex: 'studentCount', width: 150, align: 'right' as const },
  { title: '订单数', dataIndex: 'orderCount', width: 90, align: 'right' as const },
  { title: '报读课时', dataIndex: 'lessons', width: 100, align: 'right' as const },
  { title: '应收（元）', dataIndex: 'shouldAmount', width: 120, align: 'right' as const },
  { title: '实收（元）', dataIndex: 'receivedAmount', width: 120, align: 'right' as const },
  { title: '优惠金额（元）', dataIndex: 'discountAmount', width: 130, align: 'right' as const },
  { title: '操作', dataIndex: 'action', width: 120, fixed: 'right' as const },
]

const studentColumns = [
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '手机号', dataIndex: 'phone', width: 120 },
  { title: '校区', dataIndex: 'campusName', width: 110 },
  { title: '报读课程', dataIndex: 'courseNames', ellipsis: true },
  { title: '订单数', dataIndex: 'orderCount', width: 80, align: 'right' as const },
  { title: '报读课时', dataIndex: 'lessons', width: 90, align: 'right' as const },
  { title: '应收（元）', dataIndex: 'shouldAmount', width: 110, align: 'right' as const },
  { title: '实收（元）', dataIndex: 'receivedAmount', width: 110, align: 'right' as const },
  { title: '首次参与', dataIndex: 'firstJoinTime', width: 140 },
  { title: '新老生', dataIndex: 'isNew', width: 80 },
]

function money(value: number | null | undefined) {
  return Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function plain(value: number | null | undefined) {
  const num = Number(value ?? 0)
  return Number.isInteger(num) ? String(num) : num.toFixed(2)
}

function periodText(row: ActivityStatsRow) {
  if (!row.startDate && !row.endDate) return '长期有效'
  return `${row.startDate || '—'} 至 ${row.endDate || '长期'}`
}

async function load() {
  loading.value = true
  try {
    const data = await fetchActivityStats({
      keyword: keyword.value.trim() || undefined,
      categoryId: categoryId.value,
      type: type.value,
      startDate: startDate.value,
      endDate: endDate.value,
    })
    rows.value = data.rows
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function search() {
  load()
}

function reset() {
  keyword.value = ''
  categoryId.value = null
  type.value = null
  startDate.value = null
  endDate.value = null
  load()
}

async function openStudents(row: ActivityStatsRow) {
  currentActivity.value = row
  studentKeyword.value = ''
  studentPage.value = 1
  drawerOpen.value = true
  await loadStudents()
}

async function loadStudents() {
  if (!currentActivity.value) return
  studentLoading.value = true
  try {
    const data = await fetchActivityStatsStudents(currentActivity.value.id, {
      keyword: studentKeyword.value.trim() || undefined,
      page: studentPage.value,
      size: studentPageSize.value,
    })
    studentRows.value = data.rows
    studentTotal.value = data.total
    studentSummary.value = data.summary
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    studentLoading.value = false
  }
}

function onStudentTableChange(pagination: { current?: number; pageSize?: number }) {
  studentPage.value = pagination.current ?? 1
  studentPageSize.value = pagination.pageSize ?? 20
  loadStudents()
}

function searchStudents() {
  studentPage.value = 1
  loadStudents()
}

async function loadCategories() {
  try {
    categories.value = await fetchActivityCategories()
  } catch (error) {
    message.error((error as Error).message)
  }
}

onMounted(async () => {
  await loadCategories()
  await load()
})
</script>

<template>
  <div class="page">
    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>活动名称</span>
          <a-input
            v-model:value="keyword"
            placeholder="请输入活动名称"
            style="width: 200px"
            size="small"
            allow-clear
            @press-enter="search"
          />
        </div>
        <div class="filter-item">
          <span>所属分类</span>
          <a-select
            v-model:value="categoryId"
            size="small"
            style="width: 190px"
            placeholder="全部分类"
            allow-clear
            show-search
            option-filter-prop="label"
            :options="categoryOptions"
          />
        </div>
        <div class="filter-item">
          <span>活动类型</span>
          <a-select v-model:value="type" size="small" style="width: 130px" placeholder="全部类型" allow-clear>
            <a-select-option value="节日活动">节日活动</a-select-option>
            <a-select-option value="报课优惠">报课优惠</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <span>有效期</span>
          <a-date-picker
            v-model:value="startDate"
            size="small"
            style="width: 130px"
            value-format="YYYY-MM-DD"
            placeholder="开始日期"
          />
          <span class="range-sep">至</span>
          <a-date-picker
            v-model:value="endDate"
            size="small"
            style="width: 130px"
            value-format="YYYY-MM-DD"
            placeholder="结束日期"
          />
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
          <span class="muted" style="font-size: 12px">
            活动 {{ rows.length }} 个｜参与学员 {{ summary.students }} 人次｜订单 {{ summary.orders }} 笔｜实收 ￥{{
              money(summary.received)
            }}
          </span>
        </div>
        <router-link to="/priceTemplate" class="muted" style="font-size: 12px">
          去「缴费模板 → 活动模板」维护活动
        </router-link>
      </div>

      <a-spin :spinning="loading">
        <a-table
          :columns="columns"
          :data-source="rows"
          :pagination="false"
          row-key="id"
          size="middle"
          :scroll="{ x: 1600 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'name'">{{ record.name }}</span>
            <span v-else-if="column.dataIndex === 'categoryName'">{{ record.categoryName }}</span>
            <a-tag v-else-if="column.dataIndex === 'type'" :color="TYPE_COLOR[record.type] || 'default'">
              {{ record.type }}
            </a-tag>
            <a-tag v-else-if="column.dataIndex === 'status'" :color="STATUS_COLOR[record.status] || 'default'">
              {{ record.status }}
            </a-tag>
            <span v-else-if="column.dataIndex === 'period'">{{ periodText(record) }}</span>
            <span v-else-if="column.dataIndex === 'studentCount'">
              {{ record.studentCount }}
              <span class="muted">（新 {{ record.newStudentCount }} / 老 {{ record.oldStudentCount }}）</span>
            </span>
            <span v-else-if="column.dataIndex === 'lessons'">{{ plain(record.lessons) }}</span>
            <span v-else-if="column.dataIndex === 'shouldAmount'">{{ money(record.shouldAmount) }}</span>
            <span v-else-if="column.dataIndex === 'receivedAmount'">{{ money(record.receivedAmount) }}</span>
            <span v-else-if="column.dataIndex === 'discountAmount'">{{ money(record.discountAmount) }}</span>
            <div v-else-if="column.dataIndex === 'action'" class="row-actions">
              <a v-if="record.studentCount > 0" @click="openStudents(record)">参与学员</a>
              <span v-else class="muted">暂无参与</span>
            </div>
          </template>
          <template #emptyText>
            <div class="empty">
              <div>暂无活动数据</div>
              <div class="muted" style="margin-top: 8px">
                活动统一在「办理中心 - 缴费模板 - 活动模板」维护，报名时选择活动后即可在此统计效果
              </div>
            </div>
          </template>
        </a-table>
      </a-spin>
    </div>

    <a-drawer
      v-model:open="drawerOpen"
      :title="currentActivity ? `参与学员 - ${currentActivity.name}` : '参与学员'"
      :width="980"
    >
      <div class="drawer-summary">
        <span>参与学员 <b>{{ studentSummary.studentCount }}</b> 人</span>
        <span>订单 <b>{{ studentSummary.orderCount }}</b> 笔</span>
        <span>报读课时 <b>{{ plain(studentSummary.lessons) }}</b></span>
        <span>应收 <b>￥{{ money(studentSummary.shouldAmount) }}</b></span>
        <span>实收 <b>￥{{ money(studentSummary.receivedAmount) }}</b></span>
        <span>优惠 <b>￥{{ money(studentSummary.discountAmount) }}</b></span>
      </div>
      <div class="drawer-toolbar">
        <a-input
          v-model:value="studentKeyword"
          placeholder="搜索学员姓名 / 手机号 / 校区"
          size="small"
          style="width: 240px"
          allow-clear
          @press-enter="searchStudents"
        />
        <a-button type="primary" size="small" :loading="studentLoading" @click="searchStudents">查询</a-button>
        <span class="muted" style="font-size: 12px">共 {{ studentTotal }} 位学员</span>
      </div>
      <a-table
        :columns="studentColumns"
        :data-source="studentRows"
        :loading="studentLoading"
        :pagination="{
          current: studentPage,
          pageSize: studentPageSize,
          total: studentTotal,
          showSizeChanger: true,
          pageSizeOptions: ['20', '50', '100'],
          showTotal: (t: number) => `共 ${t} 条`,
        }"
        row-key="studentId"
        size="small"
        :scroll="{ x: 1180 }"
        @change="onStudentTableChange"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'phone'">{{ record.phone || '—' }}</span>
          <span v-else-if="column.dataIndex === 'campusName'">{{ record.campusName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'courseNames'">{{ record.courseNames || '—' }}</span>
          <span v-else-if="column.dataIndex === 'lessons'">{{ plain(record.lessons) }}</span>
          <span v-else-if="column.dataIndex === 'shouldAmount'">{{ money(record.shouldAmount) }}</span>
          <span v-else-if="column.dataIndex === 'receivedAmount'">{{ money(record.receivedAmount) }}</span>
          <span v-else-if="column.dataIndex === 'firstJoinTime'">{{ record.firstJoinTime || '—' }}</span>
          <a-tag v-else-if="column.dataIndex === 'isNew'" :color="record.isNew ? 'green' : 'default'">
            {{ record.isNew ? '新生' : '老生' }}
          </a-tag>
        </template>
        <template #emptyText>
          <div class="empty">暂无参与学员</div>
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<style scoped>
.range-sep {
  margin: 0 4px;
  font-size: 12px;
  color: #8f959e;
}

.row-actions {
  display: flex;
  gap: 10px;
}

.row-actions a {
  font-size: 13px;
}

.empty {
  padding: 32px 0;
  color: #8f959e;
  text-align: center;
}

.drawer-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #f7f8fa;
  border-radius: 6px;
  font-size: 13px;
  color: #646a73;
}

.drawer-summary b {
  color: #1f2329;
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
