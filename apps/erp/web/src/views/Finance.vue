<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchFinanceMonthly,
  fetchFinanceSummary,
  fetchPayments,
  type FinanceSummary,
  type MonthlyRow,
  type PaymentRow,
} from '../api/client'

const keyword = ref('')
const campus = ref<'all' | number>('all')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const rows = ref<PaymentRow[]>([])
const summary = ref<FinanceSummary | null>(null)
const monthly = ref<MonthlyRow[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function money(value: number | null | undefined) {
  if (value == null) return '—'
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function shortMoney(value: number) {
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万`
  return String(Math.round(value))
}

const monthGrowth = computed(() => {
  const s = summary.value
  if (!s || !s.lastMonthReceived) return null
  return ((s.monthReceived - s.lastMonthReceived) / s.lastMonthReceived) * 100
})

const maxMonthly = computed(() => Math.max(...monthly.value.map((m) => m.amount), 1))

function barHeight(row: MonthlyRow) {
  return `${Math.max((row.amount / maxMonthly.value) * 100, 2)}%`
}

function baseQuery() {
  return {
    keyword: keyword.value.trim() || undefined,
    campusId: campus.value === 'all' ? undefined : Number(campus.value),
    start: startDate.value,
    end: endDate.value,
  }
}

async function load() {
  loading.value = true
  try {
    const q = baseQuery()
    const [list, sum, months] = await Promise.all([
      fetchPayments({ ...q, page: page.value, size: pageSize.value }),
      fetchFinanceSummary(q),
      fetchFinanceMonthly({
        campusId: q.campusId,
        keyword: q.keyword,
        months: 12,
      }),
    ])
    rows.value = list.records
    total.value = list.total
    summary.value = sum
    monthly.value = months
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  campus.value = 'all'
  startDate.value = undefined
  endDate.value = undefined
  page.value = 1
  load()
}

function onTableChange(pagination: { current?: number; pageSize?: number }) {
  page.value = pagination.current ?? 1
  pageSize.value = pagination.pageSize ?? 20
  load()
}

function dateTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

const columns = [
  { title: '收款日期', dataIndex: 'paidAt', width: 160 },
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '校区', dataIndex: 'campusName', width: 160 },
  { title: '收款金额', dataIndex: 'amount', width: 130 },
  { title: '支付方式', dataIndex: 'method', width: 110 },
  { title: '订单号', dataIndex: 'orderNo', width: 180 },
  { title: '凭证号', dataIndex: 'voucherNo', width: 140 },
  { title: '经手人', dataIndex: 'operatorName', width: 100 },
  { title: '备注', dataIndex: 'remark', ellipsis: true },
]

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>关键词</span>
          <a-input
            v-model:value="keyword"
            placeholder="学员 / 订单号 / 凭证号"
            style="width: 220px"
            size="small"
            allow-clear
            @press-enter="search"
          />
        </div>
        <div class="filter-item">
          <span>收款日期</span>
          <a-date-picker v-model:value="startDate" size="small" style="width: 140px" value-format="YYYY-MM-DD" placeholder="开始日期" />
          <span class="range-sep">至</span>
          <a-date-picker v-model:value="endDate" size="small" style="width: 140px" value-format="YYYY-MM-DD" placeholder="结束日期" />
        </div>
        <div class="filter-item">
          <span>校区</span>
          <a-select v-model:value="campus" size="small" style="width: 150px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </div>
        <a-button type="primary" size="small" :loading="loading" @click="search">
          <SearchOutlined />查询
        </a-button>
        <a-button size="small" @click="reset">重置</a-button>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">累计收款</div>
        <div class="stat-value">{{ money(summary?.totalReceived) }}</div>
        <div class="stat-sub">共 {{ summary?.paymentCount ?? 0 }} 笔收款</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本月收款</div>
        <div class="stat-value">{{ money(summary?.monthReceived) }}</div>
        <div class="stat-sub">
          上月 {{ money(summary?.lastMonthReceived) }}
          <span v-if="monthGrowth != null" :class="monthGrowth >= 0 ? 'up' : 'down'">
            {{ monthGrowth >= 0 ? '+' : '' }}{{ monthGrowth.toFixed(1) }}%
          </span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">缴费学员</div>
        <div class="stat-value">{{ summary?.studentCount ?? 0 }}</div>
        <div class="stat-sub">订单 {{ summary?.orderCount ?? 0 }} 单</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">待收欠费</div>
        <div class="stat-value">{{ money(summary?.debt) }}</div>
        <div class="stat-sub">订单应收未收合计</div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <span style="font-size: 13px; font-weight: 600">近 12 个月收款趋势</span>
        </div>
      </div>
      <div class="chart">
        <div v-for="item in monthly" :key="item.month" class="chart-col">
          <div class="chart-amount">{{ shortMoney(item.amount) }}</div>
          <div class="chart-bar-wrap">
            <div class="chart-bar" :style="{ height: barHeight(item) }" :title="`${item.month} ${money(item.amount)}（${item.count} 笔）`" />
          </div>
          <div class="chart-label">{{ item.month.slice(2) }}</div>
        </div>
        <div v-if="!monthly.length" class="chart-empty">暂无收款记录</div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <span class="muted" style="font-size: 12px">数据来源：校宝收款流水</span>
        </div>
      </div>

      <a-spin :spinning="loading">
        <a-table
          :columns="columns"
          :data-source="rows"
          :pagination="{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['20', '50', '100'],
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          row-key="id"
          size="middle"
          :scroll="{ x: 1300 }"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'paidAt'">{{ dateTime(record.paidAt) }}</span>
            <span v-else-if="column.dataIndex === 'amount'" class="amount">{{ money(record.amount) }}</span>
            <span v-else-if="column.dataIndex === 'method'">{{ record.method || '—' }}</span>
            <span v-else-if="column.dataIndex === 'orderNo'">{{ record.orderNo || '—' }}</span>
            <span v-else-if="column.dataIndex === 'voucherNo'">{{ record.voucherNo || '—' }}</span>
            <span v-else-if="column.dataIndex === 'operatorName'">{{ record.operatorName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'remark'">{{ record.remark || '—' }}</span>
            <span v-else>{{ record[column.dataIndex] }}</span>
          </template>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">暂无收款记录</div>
          </template>
        </a-table>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.stat-label {
  font-size: 12px;
  color: #8f959e;
}
.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
  color: #1d2129;
}
.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8f959e;
}
.up {
  color: #52c41a;
  margin-left: 4px;
}
.down {
  color: #f5222d;
  margin-left: 4px;
}
.range-sep {
  margin: 0 4px;
  font-size: 12px;
  color: #8f959e;
}
.chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 200px;
  padding: 8px 12px 0;
}
.chart-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.chart-amount {
  font-size: 11px;
  color: #8f959e;
  margin-bottom: 4px;
}
.chart-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
}
.chart-bar {
  width: 100%;
  background: linear-gradient(180deg, #40a9ff, #1677ff);
  border-radius: 4px 4px 0 0;
  transition: height 0.3s;
}
.chart-label {
  margin-top: 6px;
  font-size: 11px;
  color: #8f959e;
  white-space: nowrap;
}
.chart-empty {
  margin: auto;
  color: #8f959e;
  font-size: 12px;
}
.amount {
  font-weight: 600;
  color: #1677ff;
}
</style>
