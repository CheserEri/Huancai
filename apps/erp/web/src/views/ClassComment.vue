<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CalendarOutlined, LeftOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  fetchClassCommentRows,
  fetchClasses,
  fetchCommentRoster,
  fetchLessonOverall,
  fetchSchedules,
  saveClassComments,
  saveLessonOverall,
  type ScheduleRow,
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
const WEEK_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const weekStart = ref(startOfWeek(new Date()))
const classId = ref<'all' | number>('all')
const classOptions = ref<OptionItem[]>([])
const lessons = ref<ScheduleRow[]>([])
const commentCounts = ref<Map<number, number>>(new Map())
const loading = ref(false)

const modalOpen = ref(false)
const rosterLoading = ref(false)
const saving = ref(false)
const currentLesson = ref<ScheduleRow | null>(null)
const editRows = ref<EditRow[]>([])
/** 整节课整体点评，与学员逐人点评分开保存。 */
const overallRating = ref<string | undefined>(undefined)
const overallContent = ref('')
const overallLoading = ref(false)

const rosterColumns = [
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '评价', dataIndex: 'rating', width: 130 },
  { title: '点评内容', dataIndex: 'content' },
]

const classFilterOptions = computed(() => [
  { value: 'all', label: '全部班级' },
  ...classOptions.value.map((item) => ({ value: item.value, label: item.label })),
])

function p2(n: number) {
  return String(n).padStart(2, '0')
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${p2(date.getMonth() + 1)}-${p2(date.getDate())}`
}

function startOfWeek(date: Date) {
  const d = new Date(date)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  d.setHours(0, 0, 0, 0)
  return d
}

function timeOf(value: string | null) {
  return value ? String(value).slice(0, 5) : ''
}

function timeRange(row: { startTime: string | null; endTime: string | null }) {
  const start = timeOf(row.startTime)
  const end = timeOf(row.endTime)
  return start || end ? `${start}-${end}` : '—'
}

const days = computed(() =>
  WEEK_LABELS.map((label, i) => {
    const d = new Date(weekStart.value)
    d.setDate(d.getDate() + i)
    return { label, date: formatDate(d), short: `${d.getMonth() + 1}-${p2(d.getDate())}` }
  })
)

const rangeLabel = computed(() => `${days.value[0].date} ~ ${days.value[6].date}`)

const slots = computed(() => {
  const set = new Set<string>()
  lessons.value.forEach((row) => {
    const start = timeOf(row.startTime)
    if (start) set.add(start)
  })
  return Array.from(set).sort()
})

const grid = computed(() => {
  const map = new Map<string, ScheduleRow[]>()
  lessons.value.forEach((row) => {
    const key = `${String(row.lessonDate).slice(0, 10)}-${timeOf(row.startTime)}`
    const list = map.get(key) ?? []
    list.push(row)
    map.set(key, list)
  })
  return map
})

function cell(date: string, slot: string) {
  return grid.value.get(`${date}-${slot}`) ?? []
}

function commentCountOf(row: ScheduleRow) {
  return commentCounts.value.get(row.id) ?? 0
}

function isCommented(row: ScheduleRow) {
  return commentCountOf(row) > 0
}

function commentText(row: ScheduleRow) {
  const count = commentCountOf(row)
  const actual = row.actualCount ?? 0
  return actual > 0 ? `已评 ${count}/${actual} 人` : `已评 ${count} 人`
}

const commentedLessons = computed(
  () => lessons.value.filter((row) => row.status === '已上课' && isCommented(row)).length
)

const canComment = computed(
  () => currentLesson.value?.status === '已上课' && !!currentLesson.value?.classId
)

const commentedRows = computed(() => editRows.value.filter((row) => row.commentId).length)

const palette = ['#007AFF', '#13C2C2', '#722ED1', '#FA8C16', '#52C41A', '#EB2F96']

function colorOf(row: ScheduleRow) {
  const key = row.courseName ?? row.className ?? ''
  let h = 0
  for (let i = 0; i < key.length; i += 1) h = (h * 31 + key.charCodeAt(i)) % 997
  return palette[h % palette.length]
}

function statusColor(value: string) {
  if (value === '已上课') return 'green'
  if (value === '休课') return 'orange'
  return 'default'
}

async function loadCommentCounts(classIds: number[], start: string, end: string) {
  const map = new Map<number, number>()
  if (!classIds.length) return map
  let failed = false
  const results = await Promise.all(
    classIds.map(async (id) => {
      try {
        return await fetchClassCommentRows({ classId: id, start, end })
      } catch {
        failed = true
        return []
      }
    })
  )
  results.forEach((rows) => {
    rows.forEach((row) => map.set(row.scheduleId, (map.get(row.scheduleId) ?? 0) + 1))
  })
  if (failed) message.warning('部分班级的点评状态加载失败，已按未点评显示')
  return map
}

async function load() {
  const start = days.value[0].date
  const end = days.value[6].date
  loading.value = true
  try {
    const list = await fetchSchedules({
      start,
      end,
      classId: classId.value === 'all' ? undefined : Number(classId.value),
      size: 1000,
    })
    lessons.value = list.records.filter((row) => !!row.classId)
    const ids = Array.from(new Set(lessons.value.map((row) => row.classId).filter((id): id is number => !!id)))
    commentCounts.value = await loadCommentCounts(ids, start, end)
  } catch (e) {
    lessons.value = []
    commentCounts.value = new Map()
    message.error(e instanceof Error ? e.message : '课次加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const res = await fetchClasses({ page: 1, size: 500 })
    classOptions.value = res.records.map((item) => ({ label: item.name, value: item.id }))
  } catch {
    // 班级列表加载失败时保留空选项
  }
}

function shiftWeek(n: number) {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + n * 7)
  weekStart.value = d
  load()
}

function thisWeek() {
  weekStart.value = startOfWeek(new Date())
  load()
}

async function openLesson(record: ScheduleRow) {
  currentLesson.value = record
  editRows.value = []
  overallRating.value = undefined
  overallContent.value = ''
  modalOpen.value = true
  if (record.status !== '已上课' || !record.classId) return
  rosterLoading.value = true
  overallLoading.value = true
  try {
    const [roster, overall] = await Promise.all([
      fetchCommentRoster(record.classId, record.id),
      fetchLessonOverall(record.classId, record.id),
    ])
    editRows.value = roster.map((row) => ({
      studentId: row.studentId,
      studentName: row.studentName,
      commentId: row.commentId,
      rating: row.rating ?? undefined,
      content: row.content ?? '',
    }))
    overallRating.value = overall.rating ?? undefined
    overallContent.value = overall.content ?? ''
  } catch (e) {
    message.error(e instanceof Error ? e.message : '点评数据加载失败')
  } finally {
    rosterLoading.value = false
    overallLoading.value = false
  }
}

async function save() {
  if (!currentLesson.value || !currentLesson.value.classId) return
  const items = editRows.value
    .filter((row) => row.commentId || row.rating || row.content.trim())
    .map((row) => ({
      studentId: row.studentId,
      rating: row.rating,
      content: row.content.trim(),
    }))
  const overallText = overallContent.value.trim()
  if (!items.length && !overallText && !overallRating.value) {
    message.warning('请至少填写整体点评或一位学员的点评')
    return
  }
  saving.value = true
  try {
    await saveLessonOverall({
      classId: currentLesson.value.classId,
      scheduleId: currentLesson.value.id,
      rating: overallRating.value,
      content: overallText,
    })
    const count = items.length
      ? await saveClassComments({
          classId: currentLesson.value.classId,
          scheduleId: currentLesson.value.id,
          items,
        })
      : 0
    message.success(count ? `已保存整体点评与 ${count} 位学员点评` : '整体点评已保存')
    modalOpen.value = false
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '点评保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadOptions()
  load()
})
</script>

<template>
  <div class="comment-page">
    <div class="filter-line">
      <span class="filter-label">班级</span>
      <a-select
        v-model:value="classId"
        size="small"
        show-search
        style="width: 200px"
        :options="classFilterOptions"
        option-filter-prop="label"
        @change="load"
      />
      <a-button size="small" @click="shiftWeek(-1)"><LeftOutlined /></a-button>
      <span class="week-label">{{ rangeLabel }}</span>
      <a-button size="small" @click="shiftWeek(1)"><RightOutlined /></a-button>
      <a-button size="small" @click="thisWeek"><CalendarOutlined />本周</a-button>
      <a-button size="small" class="refresh-btn" :loading="loading" @click="load">
        <ReloadOutlined />刷新
      </a-button>
    </div>

    <div class="summary-line">
      本周共 {{ lessons.length }} 节课，其中 {{ commentedLessons }} 节已点评；点评保存后家长端小程序可查看。仅显示班课课次。
    </div>

    <a-spin :spinning="loading">
      <div class="schedule">
        <div class="head-cell">时间</div>
        <div v-for="day in days" :key="day.date" class="head-cell day-head">
          <span>{{ day.label }}</span>
          <span class="date">{{ day.short }}</span>
        </div>

        <template v-for="slot in slots" :key="slot">
          <div class="time-cell">{{ slot }}</div>
          <div v-for="day in days" :key="`${day.date}-${slot}`" class="grid-cell">
            <div
              v-for="lesson in cell(day.date, slot)"
              :key="lesson.id"
              class="lesson"
              :class="{ done: lesson.status === '已上课' && isCommented(lesson) }"
              :style="{ borderLeftColor: colorOf(lesson) }"
              @click="openLesson(lesson)"
            >
              <div class="lesson-name">{{ lesson.className || lesson.courseName || '未命名课次' }}</div>
              <div v-if="lesson.courseName" class="lesson-topic">
                {{ lesson.courseName }}<template v-if="lesson.topic"> · {{ lesson.topic }}</template>
              </div>
              <div class="lesson-meta">
                <span>{{ timeOf(lesson.startTime) }}-{{ timeOf(lesson.endTime) }}</span>
                <span>{{ lesson.teacherName || '未指定教师' }}</span>
              </div>
              <div class="lesson-meta">
                <a-tag
                  v-if="lesson.status !== '已上课'"
                  :color="statusColor(lesson.status)"
                  style="margin: 0"
                >
                  {{ lesson.status }}
                </a-tag>
                <a-tag v-else :color="isCommented(lesson) ? 'green' : 'default'" style="margin: 0">
                  {{ isCommented(lesson) ? '已点评' : '未点评' }}
                </a-tag>
                <span v-if="lesson.status === '已上课'" class="count">{{ commentText(lesson) }}</span>
              </div>
            </div>
          </div>
        </template>

        <div v-if="!slots.length" class="empty">本周暂无课次</div>
      </div>
    </a-spin>

    <a-modal
      v-model:open="modalOpen"
      title="课堂点评"
      width="760px"
      centered
      :mask-closable="false"
    >
      <template v-if="currentLesson">
        <div class="lesson-head">
          <div class="lesson-title">
            {{ String(currentLesson.lessonDate ?? '').slice(0, 10) }}
            {{ timeRange(currentLesson) }} ·
            {{ currentLesson.className || currentLesson.courseName || '未命名课次' }}
          </div>
          <div class="lesson-sub">
            <span>课程：{{ currentLesson.courseName || '—' }}</span>
            <span>主题：{{ currentLesson.topic || '—' }}</span>
            <span>教师：{{ currentLesson.teacherName || '—' }}</span>
            <span>实到/应到：{{ currentLesson.actualCount ?? 0 }}/{{ currentLesson.shouldCount ?? 0 }}</span>
          </div>
        </div>

        <a-alert
          v-if="!canComment"
          type="warning"
          show-icon
          :message="`该课次当前状态为「${currentLesson.status}」，暂不能录入点评`"
        />

        <template v-else>
          <div class="section">
            <div class="section-title">整体点评<span class="section-sub">对全班统一生效</span></div>
            <a-spin :spinning="overallLoading">
              <div class="overall-row">
                <a-select
                  v-model:value="overallRating"
                  size="small"
                  allow-clear
                  placeholder="选择整体评价"
                  :options="RATING_OPTIONS"
                  style="width: 140px"
                />
                <a-textarea
                  v-model:value="overallContent"
                  :rows="2"
                  :maxlength="1000"
                  placeholder="填写本节课整体表现、课堂重点与作业安排，家长端可见"
                  style="flex: 1"
                />
              </div>
            </a-spin>
          </div>

          <div class="section">
            <div class="section-title">
              学员点评
              <span class="section-sub">
                共 {{ editRows.length }} 人 · 已点评 {{ commentedRows }} 人
              </span>
            </div>
            <a-spin :spinning="rosterLoading">
              <div v-if="!rosterLoading && !editRows.length" class="empty-text">暂无在读学员</div>
              <a-table
                v-else
                :columns="rosterColumns"
                :data-source="editRows"
                :pagination="false"
                row-key="studentId"
                size="small"
                :scroll="{ y: 300 }"
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
          </div>
        </template>
      </template>

      <template #footer>
        <div class="modal-foot">
          <span class="foot-tip">清空评价与内容并保存，会删除该学员本次课已有点评</span>
          <a-button @click="modalOpen = false">取消</a-button>
          <a-button v-if="canComment" type="primary" :loading="saving" @click="save">保存点评</a-button>
        </div>
      </template>
    </a-modal>
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

.week-label {
  font-size: 13px;
  font-weight: 500;
  min-width: 180px;
  text-align: center;
}

.refresh-btn {
  margin-left: auto;
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.schedule {
  display: grid;
  grid-template-columns: 70px repeat(7, 1fr);
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}

.head-cell {
  background: #fafafa;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-2);
  border-bottom: 1px solid var(--line);
  border-right: 1px solid var(--line);
}

.day-head {
  flex-direction: column;
  gap: 2px;
  font-weight: 500;
  color: var(--text);
}

.day-head .date {
  font-size: 11px;
  color: var(--text-3);
  font-weight: 400;
}

.time-cell {
  min-height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-3);
  border-bottom: 1px solid var(--line);
  border-right: 1px solid var(--line);
  background: #fcfcfd;
}

.grid-cell {
  min-height: 96px;
  padding: 4px;
  border-bottom: 1px solid var(--line);
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.lesson {
  background: #f7faff;
  border-left: 3px solid #007aff;
  border-radius: 3px;
  padding: 5px 6px;
  cursor: pointer;
}

.lesson:hover {
  background: #eef5ff;
}

.lesson.done {
  background: #f6ffed;
}

.lesson.done:hover {
  background: #edfbe0;
}

.lesson-name {
  font-size: 12px;
  color: var(--text);
  line-height: 1.4;
}

.lesson-topic {
  font-size: 11px;
  color: #007aff;
  margin-top: 2px;
  line-height: 1.4;
}

.lesson-meta {
  font-size: 11px;
  color: var(--text-3);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.count {
  font-size: 11px;
  color: var(--text-3);
}

.empty {
  grid-column: 1 / -1;
  padding: 40px;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

.lesson-head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.lesson-title {
  font-size: 14px;
  font-weight: 600;
}

.lesson-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--text-2);
}

.section {
  margin-top: 14px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.section-sub {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-2);
}

.overall-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.empty-text {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-3);
}

.modal-foot {
  display: flex;
  align-items: center;
  gap: 8px;
}

.foot-tip {
  flex: 1;
  font-size: 12px;
  color: var(--text-2);
  text-align: left;
}
</style>
