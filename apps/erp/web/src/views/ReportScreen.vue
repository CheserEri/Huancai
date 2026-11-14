<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  fetchScreenReport,
  type CampusCompareRow,
  type ScreenReport,
  type TopCourseRow,
} from '../api/client'

const campus = ref<'all' | number>('all')
const report = ref<ScreenReport | null>(null)
const loading = ref(false)
const autoRefresh = ref(true)
const lastUpdated = ref('')

let timer: ReturnType<typeof setInterval> | null = null

const campusOptions = [
  { value: 'all', label: '全部校区' },
  { value: 1, label: '鲅鱼圈幻彩国学堂' },
  { value: 2, label: '新民幻彩国学堂' },
]

function toNumber(value: number | null | undefined) {
  const n = Number(value ?? 0)
  return Number.isFinite(n) ? n : 0
}

function money(value: number | null | undefined) {
  return `¥${toNumber(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function intText(value: number | null | undefined) {
  return toNumber(value).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function nowText() {
  const d = new Date()
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function load() {
  loading.value = true
  try {
    report.value = await fetchScreenReport({
      campusId: campus.value === 'all' ? undefined : Number(campus.value),
    })
    lastUpdated.value = nowText()
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function onCampusChange() {
  load()
}

function syncTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (!autoRefresh.value) return
  timer = setInterval(() => {
    if (!loading.value) load()
  }, 60000)
}

watch(autoRefresh, syncTimer)

onMounted(() => {
  load()
  syncTimer()
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

const primaryMetrics = computed(() => {
  const r = report.value
  return [
    {
      label: '本月收款',
      value: money(r?.monthRevenue),
      unit: '',
      sub: '本月收款流水',
      emphasis: true,
      isMoney: true,
    },
    {
      label: '本月课耗',
      value: intText(r?.monthLessons),
      unit: '课时',
      sub: '本月点名课耗',
      emphasis: true,
      isMoney: false,
    },
    {
      label: '在读学员',
      value: intText(r?.activeStudents),
      unit: '人',
      sub: '当前在读学员',
      emphasis: false,
      isMoney: false,
    },
    {
      label: '本月报名订单',
      value: intText(r?.monthOrders),
      unit: '单',
      sub: '本月新增报名',
      emphasis: false,
      isMoney: false,
    },
    {
      label: '本月咨询跟进',
      value: intText(r?.monthConsults),
      unit: '条',
      sub: '本月咨询记录',
      emphasis: false,
      isMoney: false,
    },
    {
      label: '今日课次',
      value: intText(r?.todayClasses),
      unit: '节',
      sub: '今日排课课次',
      emphasis: false,
      isMoney: false,
    },
  ]
})

const secondaryMetrics = computed(() => {
  const r = report.value
  return [
    { label: '学员总数', value: intText(r?.studentsTotal), unit: '人' },
    { label: '班级数', value: intText(r?.classes), unit: '个' },
    { label: '欠费', value: money(r?.debt), unit: '' },
    { label: '剩余课时', value: intText(r?.remainLessons), unit: '课时' },
  ]
})

const campuses = computed<CampusCompareRow[]>(() => report.value?.campuses ?? [])
const topCourses = computed<TopCourseRow[]>(() => report.value?.topCourses ?? [])

type CompareKey = 'payments' | 'lessons' | 'activeStudents'

interface CompareMetric {
  label: string
  key: CompareKey
  money: boolean
}

const compareMetrics: CompareMetric[] = [
  { label: '本月收款', key: 'payments', money: true },
  { label: '本月课耗', key: 'lessons', money: false },
  { label: '在读学员', key: 'activeStudents', money: false },
]

const campusColors = ['#38bdf8', '#22d3ee', '#818cf8']

function campusColor(index: number) {
  return campusColors[index % campusColors.length]
}

function barWidth(row: CampusCompareRow, key: CompareKey) {
  const max = Math.max(...campuses.value.map((item) => toNumber(item[key])), 0)
  if (!max) return '0%'
  const ratio = toNumber(row[key]) / max
  return ratio > 0 ? `${Math.max(ratio * 100, 2)}%` : '0%'
}

function formatCompare(row: CampusCompareRow, metric: CompareMetric) {
  return metric.money ? money(row[metric.key]) : intText(row[metric.key])
}

const maxCourseStudents = computed(() =>
  Math.max(...topCourses.value.map((course) => toNumber(course.students)), 0),
)

function courseWidth(course: TopCourseRow) {
  const max = maxCourseStudents.value
  if (!max) return '0%'
  const ratio = toNumber(course.students) / max
  return ratio > 0 ? `${Math.max(ratio * 100, 4)}%` : '0%'
}
</script>

<template>
  <div class="screen-page">
    <div v-if="loading" class="loading-line" />

    <div class="screen-top">
      <div class="brand-block">
        <span class="brand-mark">幻彩</span>
        <div>
          <div class="brand-title">幻彩教育 · 数据大屏</div>
          <div class="brand-sub">经营概览 · 本月实时数据</div>
        </div>
      </div>
      <div class="top-controls">
        <a-select
          v-model:value="campus"
          class="dark-select"
          size="small"
          style="width: 170px"
          @change="onCampusChange"
        >
          <a-select-option v-for="item in campusOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </a-select-option>
        </a-select>
        <button
          type="button"
          class="tool-btn auto-btn"
          :class="{ on: autoRefresh }"
          @click="autoRefresh = !autoRefresh"
        >
          自动刷新 {{ autoRefresh ? '开' : '关' }}
        </button>
        <button type="button" class="tool-btn" :disabled="loading" @click="load">
          <ReloadOutlined :spin="loading" />
          {{ loading ? '刷新中' : '刷新' }}
        </button>
        <span class="update-time">最后更新 {{ lastUpdated || '—' }}</span>
      </div>
    </div>

    <div class="primary-grid">
      <div
        v-for="metric in primaryMetrics"
        :key="metric.label"
        class="metric-card"
        :class="{ emphasis: metric.emphasis }"
      >
        <div class="metric-label">{{ metric.label }}</div>
        <div class="metric-value" :class="{ money: metric.isMoney }">{{ metric.value }}</div>
        <div class="metric-foot">
          <span v-if="metric.unit" class="metric-unit">{{ metric.unit }}</span>
          <span class="metric-sub">{{ metric.sub }}</span>
        </div>
      </div>
    </div>

    <div class="secondary-bar">
      <div v-for="item in secondaryMetrics" :key="item.label" class="secondary-item">
        <span class="sec-label">{{ item.label }}</span>
        <span class="sec-value">{{ item.value }}</span>
        <span v-if="item.unit" class="sec-unit">{{ item.unit }}</span>
      </div>
    </div>

    <div class="panels">
      <div class="panel">
        <div class="panel-head">
          <div class="panel-title">校区对比</div>
          <span class="panel-extra">本月收款 / 本月课耗 / 在读学员</span>
        </div>

        <div v-if="campuses.length">
          <div class="campus-legend">
            <span v-for="(item, index) in campuses" :key="item.campusName">
              <i class="dot" :style="{ background: campusColor(index) }" />
              {{ item.campusName || '未命名校区' }}
            </span>
          </div>
          <div class="compare-list">
            <div v-for="metric in compareMetrics" :key="metric.key" class="compare-row">
              <div class="compare-label">{{ metric.label }}</div>
              <div class="compare-bars">
                <div v-for="(item, index) in campuses" :key="item.campusName" class="compare-line">
                  <div class="compare-track">
                    <div
                      class="compare-bar"
                      :style="{ width: barWidth(item, metric.key), background: campusColor(index) }"
                    />
                  </div>
                  <span class="compare-value">{{ formatCompare(item, metric) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-line">暂无校区数据</div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div class="panel-title">热门课程</div>
          <span class="panel-extra">按在读人数</span>
        </div>

        <div v-if="topCourses.length" class="course-list">
          <div v-for="(course, index) in topCourses" :key="`${course.courseName}-${index}`" class="course-row">
            <div class="course-top">
              <span class="course-rank">{{ index + 1 }}</span>
              <span class="course-name">{{ course.courseName || '未命名课程' }}</span>
              <span class="course-students">{{ intText(course.students) }} 人</span>
              <span class="course-remain">剩余 {{ intText(course.remainLessons) }} 课时</span>
            </div>
            <div class="course-track">
              <div class="course-bar" :style="{ width: courseWidth(course) }" />
            </div>
          </div>
        </div>
        <div v-else class="empty-line">暂无课程数据</div>
      </div>
    </div>

    <div class="screen-foot">数据来源：本系统订单、收款、点名与咨询记录 · 每 60 秒自动刷新</div>
  </div>
</template>

<style scoped>
.screen-page {
  position: relative;
  min-height: 100%;
  padding: 18px 20px 24px;
  color: #e6f2ff;
  background:
    radial-gradient(circle at 85% 0%, rgba(56, 189, 248, 0.18), transparent 55%),
    linear-gradient(160deg, #0b1a33 0%, #0f2b4d 100%);
}

.loading-line {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, #38bdf8, transparent);
  animation: loading-slide 1.2s linear infinite;
  z-index: 10;
}

@keyframes loading-slide {
  0% {
    opacity: 0.2;
    transform: translateX(-20%);
  }
  50% {
    opacity: 1;
    transform: translateX(0);
  }
  100% {
    opacity: 0.2;
    transform: translateX(20%);
  }
}

.screen-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(90, 170, 255, 0.18);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #e0f2fe;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.5), rgba(34, 211, 238, 0.2));
  border: 1px solid rgba(56, 189, 248, 0.55);
  box-shadow: 0 0 18px rgba(56, 189, 248, 0.25);
}

.brand-title {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #f0f9ff;
}

.brand-sub {
  margin-top: 3px;
  font-size: 12px;
  color: rgba(147, 197, 253, 0.65);
}

.top-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.tool-btn {
  height: 28px;
  padding: 0 12px;
  border-radius: 6px;
  border: 1px solid rgba(90, 170, 255, 0.35);
  background: rgba(56, 189, 248, 0.12);
  color: #7dd3fc;
  font-size: 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.tool-btn:hover {
  background: rgba(56, 189, 248, 0.24);
  color: #e0f2fe;
}

.tool-btn:disabled {
  opacity: 0.6;
  cursor: default;
}

.auto-btn::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #64748b;
}

.auto-btn.on {
  border-color: rgba(34, 211, 238, 0.6);
  color: #67e8f9;
}

.auto-btn.on::before {
  background: #22d3ee;
  box-shadow: 0 0 6px #22d3ee;
}

.update-time {
  font-size: 12px;
  color: rgba(147, 197, 253, 0.75);
  font-variant-numeric: tabular-nums;
}

.primary-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  padding: 14px 16px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(90, 170, 255, 0.25);
}

.metric-card.emphasis {
  background: linear-gradient(150deg, rgba(56, 189, 248, 0.2), rgba(255, 255, 255, 0.05));
  border-color: rgba(56, 189, 248, 0.45);
}

.metric-label {
  font-size: 12px;
  color: rgba(186, 220, 255, 0.75);
}

.metric-value {
  margin-top: 10px;
  font-size: 34px;
  line-height: 1.05;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #f0f9ff;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-card.emphasis .metric-value {
  color: #38bdf8;
}

.metric-value.money {
  font-size: 32px;
  letter-spacing: 0;
}

.metric-foot {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}

.metric-unit {
  font-size: 13px;
  color: #7dd3fc;
}

.metric-sub {
  color: rgba(147, 197, 253, 0.55);
}

.secondary-bar {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(90, 170, 255, 0.18);
}

.secondary-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 16px;
  border-left: 1px solid rgba(90, 170, 255, 0.12);
}

.secondary-item:first-child {
  border-left: none;
}

.sec-label {
  font-size: 12px;
  color: rgba(186, 220, 255, 0.65);
}

.sec-value {
  margin-left: auto;
  font-size: 18px;
  font-weight: 600;
  color: #dbeafe;
  font-variant-numeric: tabular-nums;
}

.sec-unit {
  font-size: 11px;
  color: rgba(147, 197, 253, 0.55);
}

.panels {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 12px;
  align-items: start;
}

.panel {
  padding: 14px 16px 16px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(90, 170, 255, 0.25);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #dbeafe;
}

.panel-extra {
  font-size: 12px;
  color: rgba(147, 197, 253, 0.55);
}

.campus-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 10px;
  font-size: 12px;
  color: rgba(186, 220, 255, 0.75);
}

.campus-legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.compare-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}

.compare-label {
  flex: 0 0 68px;
  font-size: 12px;
  color: rgba(186, 220, 255, 0.7);
}

.compare-bars {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.compare-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.compare-track {
  flex: 1;
  height: 10px;
  border-radius: 5px;
  background: rgba(90, 170, 255, 0.12);
  overflow: hidden;
}

.compare-bar {
  height: 100%;
  border-radius: 5px;
  transition: width 0.4s ease;
}

.compare-value {
  flex: 0 0 96px;
  text-align: right;
  font-size: 12px;
  color: #dbeafe;
  font-variant-numeric: tabular-nums;
}

.course-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.course-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: rgba(186, 220, 255, 0.75);
}

.course-rank {
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #7dd3fc;
  background: rgba(56, 189, 248, 0.16);
}

.course-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: #e0f2fe;
}

.course-students {
  flex: 0 0 auto;
  color: #38bdf8;
}

.course-remain {
  flex: 0 0 auto;
  color: rgba(147, 197, 253, 0.6);
}

.course-track {
  margin-top: 6px;
  height: 6px;
  border-radius: 3px;
  background: rgba(90, 170, 255, 0.14);
  overflow: hidden;
}

.course-bar {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #38bdf8, #22d3ee);
  transition: width 0.4s ease;
}

.empty-line {
  padding: 24px 0;
  text-align: center;
  font-size: 12px;
  color: rgba(147, 197, 253, 0.55);
}

.screen-foot {
  margin-top: 14px;
  font-size: 11px;
  color: rgba(147, 197, 253, 0.4);
}

.dark-select :deep(.ant-select-selector) {
  background: rgba(255, 255, 255, 0.08) !important;
  border-color: rgba(90, 170, 255, 0.35) !important;
}

.dark-select :deep(.ant-select-selection-item) {
  color: #e6f2ff;
}

.dark-select :deep(.ant-select-arrow) {
  color: #93c5fd;
}

@media (max-width: 1400px) {
  .primary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .panels {
    grid-template-columns: 1fr;
  }
  .loading-line {
    left: 0;
  }
}

@media (max-width: 900px) {
  .primary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .secondary-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .secondary-item:nth-child(3) {
    border-left: none;
  }
  .secondary-item:nth-child(n + 3) {
    border-top: 1px solid rgba(90, 170, 255, 0.12);
  }
}
</style>
