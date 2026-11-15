<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchMaterialReport,
  type MaterialReport,
  type MaterialRow,
} from '../api/client'

const period = ref('month')
const campus = ref<'all' | number>('all')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const report = ref<MaterialReport | null>(null)
const loading = ref(false)

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

const rows = computed<MaterialRow[]>(() => report.value?.rows ?? [])

async function load() {
  loading.value = true
  try {
    const range = periodRange()
    report.value = await fetchMaterialReport({
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
  load()
}

function reset() {
  period.value = 'month'
  campus.value = 'all'
  startDate.value = undefined
  endDate.value = undefined
  load()
}

const columns = [
  { title: '项目名称', dataIndex: 'name', ellipsis: true },
  { title: '笔数', dataIndex: 'count', width: 120, align: 'right' as const },
  { title: '应收', dataIndex: 'receivable', width: 180, align: 'right' as const },
  { title: '实收', dataIndex: 'received', width: 180, align: 'right' as const },
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
    </div>

    <a-spin :spinning="loading">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-label">杂费笔数</div>
          <div class="stat-value">{{ intText(report?.count) }}</div>
          <div class="stat-sub">期间含教材杂费项目订单数</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">应收金额</div>
          <div class="stat-value">{{ money(report?.receivable) }}</div>
          <div class="stat-sub">期间杂费应收合计</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">实收金额</div>
          <div class="stat-value">{{ money(report?.received) }}</div>
          <div class="stat-sub">期间杂费实收合计</div>
        </div>
      </div>

      <div class="table-card">
        <div class="table-toolbar">
          <div class="table-toolbar-left">
            <span style="font-size: 13px; font-weight: 600">杂费明细</span>
          </div>
        </div>
        <div class="material-note muted">
          当前数据来自历史订单中含「资料/教材/杂费/材料」字样的项目，历史导入的资料费金额为 0，教材库存尚未同步。
        </div>

        <a-table
          :columns="columns"
          :data-source="rows"
          :pagination="false"
          row-key="name"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'name'">{{ record.name || '—' }}</span>
            <span v-else-if="column.dataIndex === 'count'" class="count">{{ intText(record.count) }}</span>
            <span v-else-if="column.dataIndex === 'receivable'" class="amount">{{ money(record.receivable) }}</span>
            <span v-else-if="column.dataIndex === 'received'" class="amount">{{ money(record.received) }}</span>
          </template>
          <template #emptyText>
            <div class="table-empty">该期间暂无教材杂费记录</div>
          </template>
        </a-table>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
.material-note {
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.7;
}
.amount {
  font-weight: 600;
  color: #1677ff;
}
.count {
  color: #1d2129;
}
.table-empty {
  padding: 32px 0;
  color: #8f959e;
}
</style>
