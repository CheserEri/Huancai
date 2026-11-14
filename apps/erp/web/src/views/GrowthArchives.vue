<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchComments,
  fetchCommentSummary,
  type CommentRow,
  type CommentSummary,
} from '../api/client'

const keyword = ref('')
const campus = ref<'all' | number>('all')
const rating = ref('all')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const rows = ref<CommentRow[]>([])
const summary = ref<CommentSummary | null>(null)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function intText(value: number | null | undefined) {
  const n = Number(value ?? 0)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN', { maximumFractionDigits: 0 }) : '0'
}

function baseQuery() {
  return {
    keyword: keyword.value.trim() || undefined,
    campusId: campus.value === 'all' ? undefined : Number(campus.value),
    rating: rating.value === 'all' ? undefined : rating.value,
    start: startDate.value,
    end: endDate.value,
  }
}

async function load() {
  loading.value = true
  try {
    const base = baseQuery()
    const [list, sum] = await Promise.all([
      fetchComments({ ...base, page: page.value, size: pageSize.value }),
      fetchCommentSummary(base),
    ])
    rows.value = list.records ?? []
    total.value = list.total ?? 0
    summary.value = sum
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
  rating.value = 'all'
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
  { title: '记录时间', dataIndex: 'createdAt', width: 150 },
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '校区', dataIndex: 'campusName', width: 150 },
  { title: '班级', dataIndex: 'className', width: 220, ellipsis: true },
  { title: '教师', dataIndex: 'teacherName', width: 100 },
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '点评内容', dataIndex: 'content', width: 380, ellipsis: true },
  { title: '评分', dataIndex: 'rating', width: 90 },
  { title: '家长已读', dataIndex: 'readAt', width: 100 },
]

function ratingColor(value: string | null) {
  if (value === '优秀') return 'green'
  if (value === '良好') return 'blue'
  if (value === '需加强') return 'orange'
  return 'default'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="filter-card">
      <div class="intro muted">
        <span v-if="summary">
          成长档案数据来源：校宝课堂点评（共 {{ intText(summary.total) }} 条），按学员聚合展示
        </span>
        <span v-else>成长档案数据来源：校宝课堂点评，按学员聚合展示</span>
      </div>
      <div class="filter-row">
        <div class="filter-item">
          <span>关键词</span>
          <a-input
            v-model:value="keyword"
            placeholder="学员 / 班级 / 教师"
            style="width: 200px"
            size="small"
            allow-clear
            @press-enter="search"
          />
        </div>
        <div class="filter-item">
          <span>校区</span>
          <a-select v-model:value="campus" size="small" style="width: 150px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <span>评分</span>
          <a-select v-model:value="rating" size="small" style="width: 110px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option value="优秀">优秀</a-select-option>
            <a-select-option value="良好">良好</a-select-option>
            <a-select-option value="需加强">需加强</a-select-option>
          </a-select>
        </div>
        <div class="filter-item">
          <span>上课日期</span>
          <a-date-picker v-model:value="startDate" size="small" style="width: 140px" value-format="YYYY-MM-DD" placeholder="开始日期" />
          <span class="range-sep">至</span>
          <a-date-picker v-model:value="endDate" size="small" style="width: 140px" value-format="YYYY-MM-DD" placeholder="结束日期" />
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
          <span style="font-size: 13px; font-weight: 600">成长记录</span>
        </div>
        <span class="muted" style="font-size: 12px">按记录时间展示学员课堂点评</span>
      </div>

      <div class="summary-bar">
        <span>成长记录<b>{{ intText(summary?.total) }}</b></span>
        <span>覆盖学员<b>{{ intText(summary?.students) }}</b></span>
        <span>涉及班级<b>{{ intText(summary?.classes) }}</b></span>
        <span>未读<b>{{ intText(summary?.unread) }}</b></span>
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
          :scroll="{ x: 1400 }"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'createdAt'">{{ dateTime(record.createdAt) }}</span>
            <span v-else-if="column.dataIndex === 'studentName'">{{ record.studentName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'campusName'">{{ record.campusName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'lessonDate'">{{ record.lessonDate || '—' }}</span>
            <span v-else-if="column.dataIndex === 'content'" class="record-content">{{ record.content || '—' }}</span>
            <a-tag v-else-if="column.dataIndex === 'rating'" :color="ratingColor(record.rating)">
              {{ record.rating || '—' }}
            </a-tag>
            <span v-else-if="column.dataIndex === 'readAt'">
              <a-tag v-if="record.readAt" color="green">已读</a-tag>
              <a-tag v-else>未读</a-tag>
            </span>
            <span v-else>{{ record[column.dataIndex] }}</span>
          </template>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">暂无成长记录</div>
          </template>
        </a-table>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.intro {
  font-size: 12px;
  line-height: 1.7;
  margin-bottom: 12px;
}
.range-sep {
  margin: 0 4px;
  font-size: 12px;
  color: #8f959e;
}
.record-content {
  color: #4e5969;
}
</style>
