<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchBusinessReport,
  type BusinessReport,
  type CampusCompareRow,
} from '../api/client'

const period = ref('month')
const campus = ref<'all' | number>('all')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const report = ref<BusinessReport | null>(null)
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

function lessonText(value: number | null | undefined) {
  if (value == null) return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

async function load() {
  loading.value = true
  try {
    const range = periodRange()
    report.value = await fetchBusinessReport({
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

const groups = computed(() => {
  const r = report.value
  return [
    {
      title: '招生概况',
      metrics: [
        { label: '期间跟进咨询', value: intText(r?.consults), sub: '期间咨询跟进条数' },
        { label: '报名订单', value: intText(r?.deals), sub: '期间报名订单数' },
        { label: '应收成交额', value: money(r?.receivable), sub: '期间订单应收合计' },
        { label: '实收成交额', value: money(r?.received), sub: '期间订单实收合计' },
        { label: '优惠金额', value: money(r?.discount), sub: '期间订单优惠合计' },
        { label: '退费金额', value: money(r?.refund), sub: '期间退费合计' },
        { label: '学员账户余额', value: money(r?.balance), sub: '学员账户当前余额' },
      ],
    },
    {
      title: '教务概况',
      metrics: [
        { label: '在读学员', value: intText(r?.activeStudents), sub: '当前在读学员数' },
        { label: '在读报读', value: intText(r?.enrollmentCount), sub: '当前在读报读人次' },
        { label: '剩余课时', value: lessonText(r?.remainLessons), sub: '学员剩余课时合计' },
        { label: '期间课耗', value: lessonText(r?.periodLessons), sub: '期间点名课耗课时' },
      ],
    },
    {
      title: '财务概况',
      metrics: [
        { label: '期间实收', value: money(r?.periodPayments), sub: '期间收款流水合计' },
        { label: '欠费总额', value: money(r?.debt), sub: '订单应收未收合计' },
        { label: '教材杂费收入', value: money(r?.materialIncome), sub: '期间教材杂费实收' },
      ],
    },
  ]
})

const campuses = computed<CampusCompareRow[]>(() => report.value?.campuses ?? [])

type CompareKey = 'receivable' | 'received' | 'payments' | 'lessons' | 'activeStudents'

interface CompareMetric {
  label: string
  key: CompareKey
  money: boolean
}

const compareMetrics: CompareMetric[] = [
  { label: '应收成交额', key: 'receivable', money: true },
  { label: '实收成交额', key: 'received', money: true },
  { label: '期间收款', key: 'payments', money: true },
  { label: '课耗课时', key: 'lessons', money: false },
  { label: '在读学员', key: 'activeStudents', money: false },
]

const compareColors = ['#007AFF', '#13C2C2']

function compareColor(index: number) {
  return compareColors[index % compareColors.length]
}

function formatCompare(row: CampusCompareRow, metric: CompareMetric) {
  const value = row[metric.key]
  if (metric.money) return money(value)
  return metric.key === 'lessons' ? lessonText(value) : intText(value)
}

function barWidth(value: number | null | undefined, key: CompareKey) {
  const max = Math.max(...campuses.value.map((row) => Number(row[key] ?? 0)), 0)
  if (!max) return '0%'
  return `${(Number(value ?? 0) / max) * 100}%`
}

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
      <div class="source-note muted">数据来源：校宝历史订单、收款、点名与咨询记录。</div>
    </div>

    <a-spin :spinning="loading">
      <div v-for="group in groups" :key="group.title" class="card report-group">
        <div class="card-head">
          <div class="card-title">{{ group.title }}</div>
        </div>
        <div class="metric-grid">
          <div v-for="metric in group.metrics" :key="metric.label" class="metric">
            <div class="metric-label">{{ metric.label }}</div>
            <div class="metric-value">{{ metric.value }}</div>
            <div class="metric-sub">{{ metric.sub }}</div>
          </div>
        </div>
      </div>

      <div class="card report-group">
        <div class="card-head">
          <div class="card-title">校区对比</div>
          <span class="muted" style="font-size: 12px">按校区对比</span>
        </div>
        <div v-if="campuses.length" class="compare-legend">
          <span v-for="(item, index) in campuses" :key="item.campusName">
            <i class="dot" :style="{ background: compareColor(index) }" />{{ item.campusName || '未命名校区' }}
          </span>
        </div>
        <div class="compare">
          <div v-for="metric in compareMetrics" :key="metric.key" class="compare-row">
            <div class="compare-label">{{ metric.label }}</div>
            <div class="bars">
              <div v-for="(item, index) in campuses" :key="item.campusName" class="bar-line">
                <div class="bar" :style="{ width: barWidth(item[metric.key], metric.key), background: compareColor(index) }" />
                <span class="bar-value">{{ formatCompare(item, metric) }}</span>
              </div>
            </div>
          </div>
          <div v-if="!campuses.length" class="muted" style="font-size: 12px; padding: 8px 0">暂无校区数据</div>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.source-note {
  margin-bottom: 12px;
  font-size: 12px;
}

.report-group {
  margin-top: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.metric {
  background: #fafbfc;
  border-radius: 6px;
  padding: 14px 16px;
}

.metric-label {
  font-size: 13px;
  color: var(--text-3);
}

.metric-value {
  font-size: 18px;
  font-weight: 600;
  margin: 8px 0 4px;
  white-space: nowrap;
}

.metric-sub {
  font-size: 12px;
  color: var(--text-3);
}

.range-sep {
  margin: 0 4px;
  font-size: 12px;
  color: #8f959e;
}

.compare-legend {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: var(--text-2);
  margin-bottom: 12px;
}

.compare-legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.compare-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
}

.compare-label {
  width: 110px;
  font-size: 13px;
  color: var(--text-2);
  flex: 0 0 110px;
}

.bars {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bar-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar {
  height: 14px;
  border-radius: 2px;
  min-width: 2px;
}

.bar-value {
  font-size: 12px;
  color: var(--text-2);
}
</style>
