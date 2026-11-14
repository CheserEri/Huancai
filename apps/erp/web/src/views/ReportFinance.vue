<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchFinanceReport,
  type DailyFinanceRow,
  type FinanceReport,
} from '../api/client'

const period = ref('month')
const campus = ref<'all' | number>('all')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const report = ref<FinanceReport | null>(null)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function fmt(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function periodRange() {
  const now = new Date()
  if (period.value === 'all') return { start: undefined, end: undefined }
  if (period.value === 'custom') return { start: startDate.value, end: endDate.value }
  if (period.value === 'month') {
    return { start: fmt(new Date(now.getFullYear(), now.getMonth(), 1)), end: fmt(now) }
  }
  if (period.value === 'lastMonth') {
    return {
      start: fmt(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
      end: fmt(new Date(now.getFullYear(), now.getMonth(), 0)),
    }
  }
  return { start: fmt(new Date(now.getFullYear(), 0, 1)), end: fmt(now) }
}

function money(value: number | null | undefined) {
  if (value == null) return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return `¥${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function intText(value: number | null | undefined) {
  if (value == null) return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

const daily = computed<DailyFinanceRow[]>(() => report.value?.daily ?? [])

async function load() {
  loading.value = true
  try {
    const range = periodRange()
    report.value = await fetchFinanceReport({
      campusId: campus.value === 'all' ? undefined : Number(campus.value),
      start: range.start,
      end: range.end,
    })
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
  period.value = 'month'
  campus.value = 'all'
  startDate.value = undefined
  endDate.value = undefined
  page.value = 1
  load()
}

function onTableChange(pagination: { current?: number; pageSize?: number }) {
  page.value = pagination.current ?? 1
  pageSize.value = pagination.pageSize ?? 20
}

const columns = [
  { title: '日期', dataIndex: 'date', width: 160 },
  { title: '应收', dataIndex: 'receivable', width: 180, align: 'right' as const },
  { title: '实收', dataIndex: 'received', width: 180, align: 'right' as const },
  { title: '退款', dataIndex: 'refund', width: 180, align: 'right' as const },
  { title: '欠费', dataIndex: 'debt', width: 180, align: 'right' as const },
]

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>统计周期</span>
          <a-select v-model:value="period" size="small" style="width: 130px">
            <a-select-option value="month">本月</a-select-option>
            <a-select-option value="lastMonth">上月</a-select-option>
            <a-select-option value="year">今年</a-select-option>
            <a-select-option value="all">全部</a-select-option>
            <a-select-option value="custom">自定义</a-select-option>
          </a-select>
        </div>
        <template v-if="period === 'custom'">
          <div class="filter-item">
            <a-date-picker v-model:value="startDate" size="small" style="width: 140px" value-format="YYYY-MM-DD" placeholder="开始日期" />
            <span class="range-sep">至</span>
            <a-date-picker v-model:value="endDate" size="small" style="width: 140px" value-format="YYYY-MM-DD" placeholder="结束日期" />
          </div>
        </template>
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
      <div class="source-note muted">数据来源：校宝订单与收款流水（按日期汇总）</div>
    </div>

    <a-spin :spinning="loading">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-label">应收费</div>
          <div class="stat-value">{{ money(report?.receivable) }}</div>
          <div class="stat-sub">期间订单应收合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">实收</div>
          <div class="stat-value">{{ money(report?.received) }}</div>
          <div class="stat-sub">期间收款流水合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">退款</div>
          <div class="stat-value">{{ money(report?.refund) }}</div>
          <div class="stat-sub">期间退费合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">欠费</div>
          <div class="stat-value">{{ money(report?.debt) }}</div>
          <div class="stat-sub">订单应收未收合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">订单数</div>
          <div class="stat-value">{{ intText(report?.orderCount) }}</div>
          <div class="stat-sub">期间订单笔数</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">收款笔数</div>
          <div class="stat-value">{{ intText(report?.paymentCount) }}</div>
          <div class="stat-sub">期间收款流水笔数</div>
        </div>
      </div>

      <div class="table-card">
        <div class="table-toolbar">
          <div class="table-toolbar-left">
            <span style="font-size: 13px; font-weight: 600">按日收支明细</span>
          </div>
        </div>

        <a-table
          :columns="columns"
          :data-source="daily"
          :pagination="{
            current: page,
            pageSize,
            total: daily.length,
            showSizeChanger: true,
            pageSizeOptions: ['20', '50', '100'],
            showTotal: (t: number) => `共 ${t} 天`,
          }"
          row-key="date"
          size="middle"
          :scroll="{ x: 900 }"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'date'">{{ record.date || '—' }}</span>
            <span v-else-if="column.dataIndex === 'receivable'" class="amount">{{ money(record.receivable) }}</span>
            <span v-else-if="column.dataIndex === 'received'" class="amount">{{ money(record.received) }}</span>
            <span v-else-if="column.dataIndex === 'refund'" class="amount">{{ money(record.refund) }}</span>
            <span v-else-if="column.dataIndex === 'debt'" class="amount">{{ money(record.debt) }}</span>
          </template>
          <template #emptyText>
            <div class="table-empty">该期间暂无收支记录</div>
          </template>
        </a-table>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.source-note {
  margin-bottom: 12px;
  font-size: 12px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
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
  white-space: nowrap;
}
.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8f959e;
}
.range-sep {
  margin: 0 4px;
  font-size: 12px;
  color: #8f959e;
}
.amount {
  font-weight: 600;
  color: #1677ff;
}
.table-empty {
  padding: 32px 0;
  color: #8f959e;
}
</style>
