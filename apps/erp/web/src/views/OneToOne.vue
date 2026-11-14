<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  fetchScheduleSummary,
  fetchSchedules,
  type ScheduleRow,
  type ScheduleSummary,
} from '../api/client'

const campus = ref<'all' | number>('all')
const loading = ref(false)
const rows = ref<ScheduleRow[]>([])
const summary = ref<ScheduleSummary | null>(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function campusId() {
  return campus.value === 'all' ? undefined : Number(campus.value)
}

async function load() {
  loading.value = true
  try {
    const [list, sum] = await Promise.all([
      fetchSchedules({ campusId: campusId(), lessonMode: 2, page: page.value, size: pageSize.value }),
      fetchScheduleSummary({ campusId: campusId(), lessonMode: 2 }),
    ])
    rows.value = list.records
    total.value = list.total
    summary.value = sum
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function refresh() {
  page.value = 1
  load()
}

function onTableChange(pagination: { current?: number; pageSize?: number }) {
  page.value = pagination.current ?? 1
  pageSize.value = pagination.pageSize ?? 20
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <a-alert
      v-if="total === 0"
      class="notice"
      type="info"
      show-icon
      message="暂无一对一排课记录。当前排课均为班级课，后续产生一对一排课时会自动显示。"
    />

    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>校区</span>
          <a-select v-model:value="campus" size="small" style="width: 170px" @change="refresh">
            <a-select-option value="all">全部校区</a-select-option>
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </div>
        <a-button size="small" :loading="loading" @click="refresh"><ReloadOutlined />刷新</a-button>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">一对一排课</div>
        <div class="stat-value">{{ summary?.total ?? 0 }}</div>
        <div class="stat-sub">按 lessonMode = 2 过滤统计</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">一对一班课</div>
        <div class="stat-value">0</div>
        <div class="stat-sub">按 lessonMode = 2 口径统计，暂无记录</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">一对一学员</div>
        <div class="stat-value">0</div>
        <div class="stat-sub">暂无一对一学员记录</div>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <span class="muted" style="font-size: 12px">数据来源：校宝一对一排课（lesson_mode = 2）</span>
        </div>
      </div>

      <a-spin :spinning="loading">
        <a-table
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
          :scroll="{ x: 1000 }"
          @change="onTableChange"
        >
          <a-table-column title="上课日期" data-index="lessonDate" width="110" />
          <a-table-column title="开始" width="80">
            <template #default="{ record }">{{ record.startTime.slice(0, 5) }}</template>
          </a-table-column>
          <a-table-column title="结束" width="80">
            <template #default="{ record }">{{ record.endTime.slice(0, 5) }}</template>
          </a-table-column>
          <a-table-column title="学员或班级" width="140" ellipsis>
            <template #default="{ record }">{{ record.className || '—' }}</template>
          </a-table-column>
          <a-table-column title="课程" data-index="courseName" ellipsis />
          <a-table-column title="教师" width="90">
            <template #default="{ record }">{{ record.teacherName || '—' }}</template>
          </a-table-column>
          <a-table-column title="教室" width="100">
            <template #default="{ record }">{{ record.room || '—' }}</template>
          </a-table-column>
          <a-table-column title="状态" width="90">
            <template #default="{ record }">
              <a-tag :color="record.status === '已上课' ? 'green' : 'default'">{{ record.status }}</a-tag>
            </template>
          </a-table-column>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">
              暂无一对一排课记录
            </div>
          </template>
        </a-table>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.notice {
  margin-bottom: 12px;
}
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
}
.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8f959e;
}
</style>
