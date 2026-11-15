<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchAttendance,
  fetchAttendanceSummary,
  fetchAttendanceTeachers,
  type AttendanceRow,
  type AttendanceSummary,
} from '../api/client'

const keyword = ref('')
const campus = ref<'all' | number>('all')
const teacher = ref<string | undefined>(undefined)
const status = ref('all')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const rows = ref<AttendanceRow[]>([])
const summary = ref<AttendanceSummary | null>(null)
const loading = ref(false)
const teachers = ref<string[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function baseQuery() {
  return {
    keyword: keyword.value.trim() || undefined,
    campusId: campus.value === 'all' ? undefined : Number(campus.value),
    teacher: teacher.value || undefined,
    status: status.value === 'all' ? undefined : status.value,
    start: startDate.value,
    end: endDate.value,
  }
}

async function load() {
  loading.value = true
  try {
    const base = baseQuery()
    const [list, sum] = await Promise.all([
      fetchAttendance({ ...base, page: page.value, size: pageSize.value }),
      fetchAttendanceSummary(base),
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

function search() {
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  campus.value = 'all'
  teacher.value = undefined
  status.value = 'all'
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

function statusColor(value: string) {
  if (value === '出勤') return 'green'
  if (value === '请假') return 'orange'
  if (value === '缺席') return 'red'
  if (value === '迟到') return 'gold'
  return 'default'
}

const columns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '班级', dataIndex: 'className', width: 240, ellipsis: true },
  { title: '课程', dataIndex: 'courseName', width: 180, ellipsis: true },
  { title: '教师', dataIndex: 'teacherName', width: 90 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '扣课时', dataIndex: 'lessons', width: 90 },
  { title: '点名时间', dataIndex: 'checkedAt', width: 170 },
  { title: '备注', dataIndex: 'remark', width: 160, ellipsis: true },
  { title: '校区', dataIndex: 'campusName', width: 160 },
]

onMounted(async () => {
  load()
  try {
    teachers.value = await fetchAttendanceTeachers()
  } catch {
    teachers.value = []
  }
})
</script>

<template>
  <div class="page">
    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>关键词</span>
          <a-input
            v-model:value="keyword"
            placeholder="学员 / 班级 / 课程 / 教师"
            style="width: 200px"
            size="small"
            allow-clear
            @press-enter="search"
          />
        </div>
        <div class="filter-item">
          <span>上课日期</span>
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
        <div class="filter-item">
          <span>教师</span>
          <a-select
            v-model:value="teacher"
            size="small"
            style="width: 140px"
            allow-clear
            placeholder="全部"
            :options="teachers.map((t) => ({ label: t, value: t }))"
          />
        </div>
        <div class="filter-item">
          <span>状态</span>
          <a-select v-model:value="status" size="small" style="width: 110px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option value="出勤">出勤</a-select-option>
            <a-select-option value="请假">请假</a-select-option>
            <a-select-option value="缺席">缺席</a-select-option>
            <a-select-option value="迟到">迟到</a-select-option>
            <a-select-option value="未记录">未记录</a-select-option>
          </a-select>
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
          <span class="muted" style="font-size: 12px">数据来源：历史上课记录与点名</span>
        </div>
        <a-button size="small"><DownloadOutlined />导出当前结果</a-button>
      </div>

      <div class="summary-bar">
        <span>上课记录<b>{{ summary?.total ?? 0 }}</b></span>
        <span>出勤<b>{{ summary?.present ?? 0 }}</b></span>
        <span>请假<b>{{ summary?.leaveCount ?? 0 }}</b></span>
        <span>缺席<b>{{ summary?.absent ?? 0 }}</b></span>
        <span>课时消耗<b>{{ summary?.lessons ?? 0 }}</b></span>
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
            <span v-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
            <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
            <a-tag v-else-if="column.dataIndex === 'status'" :color="statusColor(record.status)">
              {{ record.status }}
            </a-tag>
            <span v-else-if="column.dataIndex === 'checkedAt'">{{ record.checkedAt ? record.checkedAt.replace('T', ' ') : '—' }}</span>
            <span v-else-if="column.dataIndex === 'remark'">{{ record.remark || '—' }}</span>
            <span v-else>{{ record[column.dataIndex] }}</span>
          </template>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">暂无上课记录</div>
          </template>
        </a-table>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.range-sep {
  margin: 0 4px;
  font-size: 12px;
  color: #8f959e;
}
</style>
