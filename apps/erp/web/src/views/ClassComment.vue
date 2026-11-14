<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  fetchClasses,
  fetchClassSchedules,
  fetchCommentRoster,
  saveClassComments,
  type ClassScheduleRow,
} from '../api/client'

interface OptionItem {
  label: string
  value: number
}

interface EditRow {
  studentId: number
  studentName: string
  commentId: number | null
  rating: string | undefined
  content: string
}

const RATING_OPTIONS = ['优秀', '良好', '需加强'].map((value) => ({ label: value, value }))

const classOptions = ref<OptionItem[]>([])
const filter = reactive<{
  classId: number | undefined
  range: string[] | null
}>({
  classId: undefined,
  range: [dateBefore(30), today()],
})

const lessons = ref<ClassScheduleRow[]>([])
const loading = ref(false)

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const saving = ref(false)
const currentLesson = ref<ClassScheduleRow | null>(null)
const editRows = ref<EditRow[]>([])

const columns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '上课时间', dataIndex: 'timeText', width: 120 },
  { title: '课程', dataIndex: 'courseName', width: 150, ellipsis: true },
  { title: '课程主题', dataIndex: 'topic', width: 180, ellipsis: true },
  { title: '教师', dataIndex: 'teacherName', width: 90 },
  { title: '实到/应到', dataIndex: 'counts', width: 100, align: 'center' as const },
  { title: '已点评', dataIndex: 'commentCount', width: 90, align: 'right' as const },
  { title: '操作', dataIndex: 'action', width: 100, fixed: 'right' as const },
]

const rosterColumns = [
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '评价', dataIndex: 'rating', width: 120 },
  { title: '点评内容', dataIndex: 'content' },
]

const filteredLessons = computed(() => {
  const [start, end] = filter.range ?? []
  return lessons.value.filter((row) => {
    const date = String(row.lessonDate ?? '').slice(0, 10)
    if (!date) return false
    if (start && date < start) return false
    if (end && date > end) return false
    return true
  })
})

const commentedCount = computed(() => filteredLessons.value.filter((row) => row.commentCount > 0).length)

function today() {
  return formatDate(new Date())
}

function dateBefore(days: number) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return formatDate(date)
}

function formatDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function timeRange(record: { startTime: string | null; endTime: string | null }) {
  const start = record.startTime ? String(record.startTime).slice(0, 5) : ''
  const end = record.endTime ? String(record.endTime).slice(0, 5) : ''
  return start || end ? `${start}-${end}` : '—'
}

function filterOption(input: string, option: { label?: string }) {
  return String(option.label ?? '').toLowerCase().includes(input.toLowerCase())
}

async function loadOptions() {
  try {
    const res = await fetchClasses({ page: 1, size: 500 })
    classOptions.value = res.records.map((item) => ({ label: item.name, value: item.id }))
  } catch {
    // 班级列表加载失败时保留空选项，由查询提示
  }
}

async function loadLessons() {
  if (!filter.classId) {
    message.warning('请先选择班级')
    return
  }
  loading.value = true
  try {
    lessons.value = await fetchClassSchedules(filter.classId, true)
    if (!lessons.value.length) {
      message.info('该班级暂无已上课的课次')
    }
  } catch (e) {
    lessons.value = []
    message.error(e instanceof Error ? e.message : '课次加载失败')
  } finally {
    loading.value = false
  }
}

async function openRoster(record: ClassScheduleRow) {
  if (!filter.classId) return
  currentLesson.value = record
  drawerOpen.value = true
  drawerLoading.value = true
  editRows.value = []
  try {
    const roster = await fetchCommentRoster(filter.classId, record.id)
    editRows.value = roster.map((row) => ({
      studentId: row.studentId,
      studentName: row.studentName,
      commentId: row.commentId,
      rating: row.rating ?? undefined,
      content: row.content ?? '',
    }))
  } catch (e) {
    message.error(e instanceof Error ? e.message : '学员名单加载失败')
  } finally {
    drawerLoading.value = false
  }
}

async function save() {
  if (!filter.classId || !currentLesson.value) return
  const items = editRows.value
    .filter((row) => row.commentId || row.rating || row.content.trim())
    .map((row) => ({
      studentId: row.studentId,
      rating: row.rating,
      content: row.content.trim(),
    }))
  if (!items.length) {
    message.warning('请至少填写一位学员的点评')
    return
  }
  saving.value = true
  try {
    const count = await saveClassComments({
      classId: filter.classId,
      scheduleId: currentLesson.value.id,
      items,
    })
    message.success(`已保存 ${count} 位学员的点评`)
    drawerOpen.value = false
    await loadLessons()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '点评保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadOptions()
})
</script>

<template>
  <div class="comment-page">
    <div class="filter-line">
      <span class="filter-label">班级</span>
      <a-select
        v-model:value="filter.classId"
        size="small"
        show-search
        allow-clear
        style="width: 220px"
        placeholder="请选择班级"
        :options="classOptions"
        :filter-option="filterOption"
        @change="loadLessons"
      />
      <span class="filter-label">上课日期</span>
      <a-range-picker v-model:value="filter.range" value-format="YYYY-MM-DD" size="small" style="width: 220px" />
      <a-button size="small" type="primary" @click="loadLessons">查询课次</a-button>
      <a-button size="small" class="refresh-btn" @click="loadLessons">
        <ReloadOutlined />刷新
      </a-button>
    </div>

    <div class="summary-line">
      当前结果：共 {{ filteredLessons.length }} 节课，其中 {{ commentedCount }} 节已点评；点评保存后家长端小程序可查看。
    </div>

    <a-table
      :columns="columns"
      :data-source="filteredLessons"
      :loading="loading"
      :pagination="{ pageSize: 10, size: 'small', showSizeChanger: false }"
      row-key="id"
      size="small"
      :scroll="{ x: 1040 }"
    >
      <template #bodyCell="{ column, record }">
        <span v-if="column.dataIndex === 'lessonDate'">{{ String(record.lessonDate ?? '').slice(0, 10) }}</span>
        <span v-else-if="column.dataIndex === 'timeText'">{{ timeRange(record) }}</span>
        <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
        <span v-else-if="column.dataIndex === 'topic'" :title="record.topic ?? ''">{{ record.topic || '—' }}</span>
        <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
        <span v-else-if="column.dataIndex === 'counts'">{{ record.actualCount ?? 0 }}/{{ record.shouldCount ?? 0 }}</span>
        <span v-else-if="column.dataIndex === 'commentCount'">
          <a-tag :color="record.commentCount > 0 ? 'green' : 'default'" style="margin: 0">
            {{ record.commentCount }} 人
          </a-tag>
        </span>
        <a v-else-if="column.dataIndex === 'action'" class="op-link" @click="openRoster(record)">写点评</a>
      </template>
      <template #emptyText>
        <a-empty description="请选择班级后查询已上课的课次" />
      </template>
    </a-table>

    <a-drawer v-model:open="drawerOpen" title="课堂点评" placement="right" width="720">
      <div v-if="currentLesson" class="drawer-head">
        <span class="drawer-title">
          {{ String(currentLesson.lessonDate ?? '').slice(0, 10) }}
          {{ timeRange(currentLesson) }}
          {{ currentLesson.courseName || '' }}
        </span>
        <span class="drawer-sub">
          学员 {{ editRows.length }} 人 · 已点评 {{ editRows.filter((row) => row.commentId).length }} 人
        </span>
      </div>
      <a-spin :spinning="drawerLoading">
        <a-table
          :columns="rosterColumns"
          :data-source="editRows"
          :pagination="false"
          row-key="studentId"
          size="small"
          :scroll="{ y: 460 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'studentName'">{{ record.studentName }}</span>
            <a-select
              v-else-if="column.dataIndex === 'rating'"
              v-model:value="record.rating"
              size="small"
              allow-clear
              placeholder="选择评价"
              :options="RATING_OPTIONS"
              style="width: 100%"
            />
            <a-textarea
              v-else-if="column.dataIndex === 'content'"
              v-model:value="record.content"
              :rows="2"
              :maxlength="1000"
              placeholder="填写本节课表现与建议"
            />
          </template>
        </a-table>
      </a-spin>
      <div class="drawer-foot">
        <span class="drawer-tip">清空评价与内容并保存，会删除该学员本次课已有点评</span>
        <a-button size="small" @click="drawerOpen = false">取消</a-button>
        <a-button type="primary" size="small" :loading="saving" @click="save">保存点评</a-button>
      </div>
    </a-drawer>
  </div>
</template>

<style scoped>
.comment-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 14px 0 12px;
  font-size: 13px;
}

.filter-label {
  color: var(--text-2);
}

.refresh-btn {
  margin-left: auto;
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.op-link {
  color: var(--brand);
}

.drawer-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
}

.drawer-title {
  font-size: 14px;
  font-weight: 600;
}

.drawer-sub {
  font-size: 12px;
  color: var(--text-2);
}

.drawer-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
}

.drawer-tip {
  flex: 1;
  font-size: 12px;
  color: var(--text-2);
}
</style>
