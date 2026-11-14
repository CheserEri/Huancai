<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchTeachingReport,
  type CourseConsumeRow,
  type TeacherWorkloadRow,
  type TeachingReport,
} from '../api/client'

const period = ref('month')
const campus = ref<'all' | number>('all')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const report = ref<TeachingReport | null>(null)
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

function rateText(value: number | null | undefined) {
  if (value == null) return '—'
  const n = Number(value)
  if (!Number.isFinite(n)) return '—'
  return `${n.toFixed(1)}%`
}

async function load() {
  loading.value = true
  try {
    const range = periodRange()
    report.value = await fetchTeachingReport({
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

const teacherColumns = [
  { title: '教师', dataIndex: 'teacherName', width: 120 },
  { title: '校区', dataIndex: 'campusName', width: 160 },
  { title: '点名记录', dataIndex: 'records', width: 100, align: 'right' as const },
  { title: '课次', dataIndex: 'schedules', width: 90, align: 'right' as const },
  { title: '课时数', dataIndex: 'lessons', width: 100, align: 'right' as const },
  { title: '班级数', dataIndex: 'classes', width: 90, align: 'right' as const },
  { title: '到课率', dataIndex: 'attendRate', width: 100, align: 'right' as const },
]

const courseColumns = [
  { title: '课程', dataIndex: 'courseName' },
  { title: '学员数', dataIndex: 'students', width: 110, align: 'right' as const },
  { title: '点名记录', dataIndex: 'records', width: 110, align: 'right' as const },
  { title: '课时数', dataIndex: 'lessons', width: 110, align: 'right' as const },
]

const teacherRowKey = (row: TeacherWorkloadRow) => `${row.campusName ?? ''}-${row.teacherName ?? ''}`
const courseRowKey = (row: CourseConsumeRow) => `${row.courseName ?? ''}`

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
      <div class="source-note muted">数据来源：校宝历史记上课/点名记录</div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">上课记录</div>
        <div class="stat-value">{{ intText(report?.records) }}</div>
        <div class="stat-sub">期间点名记录条数</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">课时消耗</div>
        <div class="stat-value">{{ lessonText(report?.lessons) }}</div>
        <div class="stat-sub">期间课耗课时合计</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">出勤记录</div>
        <div class="stat-value">{{ intText(report?.present) }}</div>
        <div class="stat-sub">期间出勤记录条数</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">到课率</div>
        <div class="stat-value">{{ rateText(report?.attendRate) }}</div>
        <div class="stat-sub">出勤记录 / 上课记录</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">覆盖学员</div>
        <div class="stat-value">{{ intText(report?.students) }}</div>
        <div class="stat-sub">期间点名学员数</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">授课教师</div>
        <div class="stat-value">{{ intText(report?.teacherCount) }}</div>
        <div class="stat-sub">期间授课教师数</div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <span style="font-size: 13px; font-weight: 600">教师工作量</span>
        </div>
      </div>
      <a-spin :spinning="loading">
        <a-table
          :columns="teacherColumns"
          :data-source="report?.teachers ?? []"
          :pagination="false"
          :row-key="teacherRowKey"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'campusName'">{{ record.campusName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'records'">{{ intText(record.records) }}</span>
            <span v-else-if="column.dataIndex === 'schedules'">{{ intText(record.schedules) }}</span>
            <span v-else-if="column.dataIndex === 'lessons'">{{ lessonText(record.lessons) }}</span>
            <span v-else-if="column.dataIndex === 'classes'">{{ intText(record.classes) }}</span>
            <span v-else-if="column.dataIndex === 'attendRate'">{{ rateText(record.attendRate) }}</span>
          </template>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">暂无数据</div>
          </template>
        </a-table>
      </a-spin>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <span style="font-size: 13px; font-weight: 600">课程课时消耗</span>
        </div>
      </div>
      <a-spin :spinning="loading">
        <a-table
          :columns="courseColumns"
          :data-source="report?.courses ?? []"
          :pagination="false"
          :row-key="courseRowKey"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'students'">{{ intText(record.students) }}</span>
            <span v-else-if="column.dataIndex === 'records'">{{ intText(record.records) }}</span>
            <span v-else-if="column.dataIndex === 'lessons'">{{ lessonText(record.lessons) }}</span>
          </template>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">暂无数据</div>
          </template>
        </a-table>
      </a-spin>
    </div>
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
</style>
