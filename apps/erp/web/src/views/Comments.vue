<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import {
  fetchCommentLessons,
  fetchCommentLessonSummary,
  fetchComments,
  fetchCommentSummary,
  fetchCommentTeachers,
  fetchCommentTeacherStats,
  fetchScheduleClasses,
  fetchScheduleCourses,
  fetchScheduleTeachers,
  type CommentLessonRow,
  type CommentLessonSummary,
  type CommentRow,
  type CommentSummary,
  type CommentTeacherStat,
  type ScheduleOption,
} from '../api/client'

const tab = ref<'lessons' | 'comments' | 'stats'>('lessons')
const keyword = ref('')
const startDate = ref<string | undefined>(undefined)
const endDate = ref<string | undefined>(undefined)
const campus = ref<'all' | number>('all')
const classId = ref<'all' | number>('all')
const courseId = ref<'all' | number>('all')
const lessonTeacher = ref<string | 'all'>('all')
const commentTeacher = ref<string | 'all'>('all')
const rating = ref('all')
const readStatus = ref('all')
const commented = ref('all')

const loading = ref(false)
const lessonRows = ref<CommentLessonRow[]>([])
const lessonSummary = ref<CommentLessonSummary | null>(null)
const commentRows = ref<CommentRow[]>([])
const commentSummary = ref<CommentSummary | null>(null)
const stats = ref<CommentTeacherStat[]>([])

const lessonPage = ref(1)
const lessonPageSize = ref(20)
const lessonTotal = ref(0)
const commentPage = ref(1)
const commentPageSize = ref(20)
const commentTotal = ref(0)

const classes = ref<ScheduleOption[]>([])
const courses = ref<ScheduleOption[]>([])
/** 扁平选项，按 label 搜索，避免 a-select-option 在 v4 下搜不到中文 */
const classFilterOptions = computed(() => [
  { value: 'all', label: '全部班级' },
  ...classes.value.map((item) => ({ value: item.id, label: item.name })),
])
const courseFilterOptions = computed(() => [
  { value: 'all', label: '全部课程' },
  ...courses.value.map((item) => ({ value: item.id, label: item.name })),
])
const scheduleTeachers = ref<string[]>([])
const commentTeachers = ref<string[]>([])

const current = ref<CommentRow | null>(null)
const drawerOpen = ref(false)

function baseQuery() {
  return {
    keyword: keyword.value.trim() || undefined,
    start: startDate.value,
    end: endDate.value,
    campusId: campus.value === 'all' ? undefined : Number(campus.value),
    classId: classId.value === 'all' ? undefined : Number(classId.value),
    courseId: courseId.value === 'all' ? undefined : Number(courseId.value),
  }
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'lessons') {
      const base = { ...baseQuery(), teacher: lessonTeacher.value === 'all' ? undefined : lessonTeacher.value, commented: commented.value === 'all' ? undefined : commented.value }
      const [list, sum] = await Promise.all([
        fetchCommentLessons({ ...base, page: lessonPage.value, size: lessonPageSize.value }),
        fetchCommentLessonSummary(base),
      ])
      lessonRows.value = list.records
      lessonTotal.value = list.total
      lessonSummary.value = sum
    } else if (tab.value === 'comments') {
      const base = {
        ...baseQuery(),
        teacher: commentTeacher.value === 'all' ? undefined : commentTeacher.value,
        rating: rating.value === 'all' ? undefined : rating.value,
        readStatus: readStatus.value === 'all' ? undefined : readStatus.value,
      }
      const [list, sum] = await Promise.all([
        fetchComments({ ...base, page: commentPage.value, size: commentPageSize.value }),
        fetchCommentSummary(base),
      ])
      commentRows.value = list.records
      commentTotal.value = list.total
      commentSummary.value = sum
    } else {
      stats.value = await fetchCommentTeacherStats(baseQuery())
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function switchTab(next: 'lessons' | 'comments' | 'stats') {
  tab.value = next
  load()
}

function search() {
  lessonPage.value = 1
  commentPage.value = 1
  load()
}

function reset() {
  keyword.value = ''
  startDate.value = undefined
  endDate.value = undefined
  campus.value = 'all'
  classId.value = 'all'
  courseId.value = 'all'
  lessonTeacher.value = 'all'
  commentTeacher.value = 'all'
  rating.value = 'all'
  readStatus.value = 'all'
  commented.value = 'all'
  search()
}

function onLessonTableChange(p: { current?: number; pageSize?: number }) {
  lessonPage.value = p.current ?? 1
  lessonPageSize.value = p.pageSize ?? 20
  load()
}

function onCommentTableChange(p: { current?: number; pageSize?: number }) {
  commentPage.value = p.current ?? 1
  commentPageSize.value = p.pageSize ?? 20
  load()
}

function timeOf(value: string | null) {
  return value ? value.slice(0, 5) : '—'
}

function rate(part: number | null | undefined, whole: number | null | undefined) {
  if (!whole) return '—'
  return `${(((part ?? 0) / whole) * 100).toFixed(1)}%`
}

const lessonRate = computed(() => {
  const sum = lessonSummary.value
  if (!sum || !sum.actualStudents) return '—'
  return `${((sum.comments / sum.actualStudents) * 100).toFixed(1)}%`
})

const lessonReadRate = computed(() => {
  const sum = lessonSummary.value
  if (!sum || !sum.comments) return '—'
  return `${((sum.readComments / sum.comments) * 100).toFixed(1)}%`
})

const commentReadRate = computed(() => {
  const sum = commentSummary.value
  if (!sum || !sum.total) return '—'
  return `${((sum.readCount / sum.total) * 100).toFixed(1)}%`
})

const statsTotals = computed(() => {
  return stats.value.reduce(
    (acc, item) => ({
      comments: acc.comments + Number(item.comments || 0),
      readComments: acc.readComments + Number(item.readComments || 0),
      students: acc.students + Number(item.students || 0),
    }),
    { comments: 0, readComments: 0, students: 0 },
  )
})

const statsReadRate = computed(() => {
  if (!statsTotals.value.comments) return '—'
  return `${((statsTotals.value.readComments / statsTotals.value.comments) * 100).toFixed(1)}%`
})

const lessonColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '时间', dataIndex: 'startTime', width: 110 },
  { title: '班级', dataIndex: 'className', width: 240, ellipsis: true },
  { title: '课程', dataIndex: 'courseName', width: 170, ellipsis: true },
  { title: '授课教师', dataIndex: 'teacherName', width: 100 },
  { title: '到课 / 应到', dataIndex: 'attendance', width: 110, align: 'center' as const },
  { title: '点评数', dataIndex: 'commentCount', width: 90, align: 'right' as const },
  { title: '阅读率', dataIndex: 'readRate', width: 90, align: 'right' as const },
  { title: '操作', dataIndex: 'action', width: 100 },
]

const commentColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '学员', dataIndex: 'studentName', width: 100 },
  { title: '班级', dataIndex: 'className', width: 220, ellipsis: true },
  { title: '课程', dataIndex: 'courseName', width: 150, ellipsis: true },
  { title: '点评教师', dataIndex: 'teacherName', width: 100 },
  { title: '点评内容', dataIndex: 'content', ellipsis: true },
  { title: '评价', dataIndex: 'rating', width: 90 },
  { title: '家长已读', dataIndex: 'readAt', width: 110 },
  { title: '操作', dataIndex: 'action', width: 80 },
]

const statColumns = [
  { title: '点评教师', dataIndex: 'teacherName', width: 140 },
  { title: '点评数', dataIndex: 'comments', width: 100, align: 'right' as const },
  { title: '涉及学员', dataIndex: 'students', width: 110, align: 'right' as const },
  { title: '涉及课次', dataIndex: 'lessons', width: 110, align: 'right' as const },
  { title: '家长已读', dataIndex: 'readComments', width: 110, align: 'right' as const },
  { title: '阅读率', dataIndex: 'readRate', width: 100, align: 'right' as const },
]

function ratingColor(value: string | null) {
  if (value === '优秀') return 'green'
  if (value === '良好') return 'blue'
  if (value === '需加强') return 'orange'
  return 'default'
}

function openLesson(record: CommentLessonRow) {
  classId.value = record.classId ?? 'all'
  keyword.value = ''
  startDate.value = record.lessonDate ?? undefined
  endDate.value = record.lessonDate ?? undefined
  commentTeacher.value = 'all'
  rating.value = 'all'
  readStatus.value = 'all'
  commentPage.value = 1
  tab.value = 'comments'
  load()
}

function openDetail(record: CommentRow) {
  current.value = record
  drawerOpen.value = true
}

onMounted(async () => {
  load()
  const [cls, crs, st, ct] = await Promise.all([
    fetchScheduleClasses().catch(() => [] as ScheduleOption[]),
    fetchScheduleCourses().catch(() => [] as ScheduleOption[]),
    fetchScheduleTeachers().catch(() => [] as string[]),
    fetchCommentTeachers().catch(() => [] as string[]),
  ])
  classes.value = cls
  courses.value = crs
  scheduleTeachers.value = st
  commentTeachers.value = ct
})
</script>

<template>
  <div class="page">
    <a-tabs v-model:active-key="tab" size="small" @change="switchTab">
      <a-tab-pane key="lessons" tab="班级点评" />
      <a-tab-pane key="comments" tab="课堂评价" />
      <a-tab-pane key="stats" tab="点评统计" />
    </a-tabs>

    <div class="filter-card">
      <div class="filter-row">
        <div class="filter-item">
          <span>关键词</span>
          <a-input
            v-model:value="keyword"
            :placeholder="tab === 'lessons' ? '班级 / 课程 / 教师 / 主题' : '学员 / 班级 / 点评内容'"
            style="width: 190px"
            size="small"
            allow-clear
            @press-enter="search"
          />
        </div>
        <div class="filter-item">
          <span>上课日期</span>
          <a-date-picker v-model:value="startDate" size="small" style="width: 135px" value-format="YYYY-MM-DD" placeholder="开始日期" />
          <span class="range-sep">至</span>
          <a-date-picker v-model:value="endDate" size="small" style="width: 135px" value-format="YYYY-MM-DD" placeholder="结束日期" />
        </div>
        <div class="filter-item">
          <span>校区</span>
          <a-select v-model:value="campus" size="small" style="width: 145px">
            <a-select-option value="all">全部校区</a-select-option>
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </div>
        <div v-if="tab !== 'stats'" class="filter-item">
          <span>班级</span>
          <a-select v-model:value="classId" size="small" style="width: 200px" show-search :options="classFilterOptions" option-filter-prop="label" allow-clear placeholder="全部" />
        </div>
        <div v-if="tab !== 'stats'" class="filter-item">
          <span>课程</span>
          <a-select v-model:value="courseId" size="small" style="width: 160px" show-search :options="courseFilterOptions" option-filter-prop="label" allow-clear placeholder="全部" />
        </div>
        <div v-if="tab === 'lessons'" class="filter-item">
          <span>授课教师</span>
          <a-select v-model:value="lessonTeacher" size="small" style="width: 120px" show-search allow-clear placeholder="全部">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option v-for="name in scheduleTeachers" :key="name" :value="name">{{ name }}</a-select-option>
          </a-select>
        </div>
        <div v-if="tab === 'lessons'" class="filter-item">
          <span>点评状态</span>
          <a-select v-model:value="commented" size="small" style="width: 105px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option value="yes">已点评</a-select-option>
            <a-select-option value="no">未点评</a-select-option>
          </a-select>
        </div>
        <div v-if="tab === 'comments'" class="filter-item">
          <span>点评教师</span>
          <a-select v-model:value="commentTeacher" size="small" style="width: 120px" allow-clear placeholder="全部">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option v-for="name in commentTeachers" :key="name" :value="name">{{ name }}</a-select-option>
          </a-select>
        </div>
        <div v-if="tab === 'comments'" class="filter-item">
          <span>评价</span>
          <a-select v-model:value="rating" size="small" style="width: 100px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option value="优秀">优秀</a-select-option>
            <a-select-option value="良好">良好</a-select-option>
            <a-select-option value="需加强">需加强</a-select-option>
          </a-select>
        </div>
        <div v-if="tab === 'comments'" class="filter-item">
          <span>家长已读</span>
          <a-select v-model:value="readStatus" size="small" style="width: 100px">
            <a-select-option value="all">全部</a-select-option>
            <a-select-option value="read">已读</a-select-option>
            <a-select-option value="unread">未读</a-select-option>
          </a-select>
        </div>
        <a-button type="primary" size="small" :loading="loading" @click="search"><SearchOutlined />查询</a-button>
        <a-button size="small" @click="reset">重置</a-button>
      </div>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <span class="muted" style="font-size: 12px">
            数据来源：校宝课堂点评（2025-03 ~ 2026-10，共 15,331 条，覆盖 921 节已上课）｜小红花、点评图片等字段校宝未同步，未展示
          </span>
        </div>
      </div>

      <template v-if="tab === 'lessons'">
        <div class="summary-bar">
          <span>已上课次<b>{{ lessonSummary?.lessons ?? 0 }}</b></span>
          <span>已点评课次<b>{{ lessonSummary?.commentedLessons ?? 0 }}</b></span>
          <span>点评数<b>{{ lessonSummary?.comments ?? 0 }}</b></span>
          <span>到课 / 应到<b>{{ lessonSummary?.actualStudents ?? 0 }} / {{ lessonSummary?.shouldStudents ?? 0 }}</b></span>
          <span>点评率<b>{{ lessonRate }}</b></span>
          <span>阅读率<b>{{ lessonReadRate }}</b></span>
        </div>

        <a-spin :spinning="loading">
          <a-table
            :columns="lessonColumns"
            :data-source="lessonRows"
            :pagination="{
              current: lessonPage,
              pageSize: lessonPageSize,
              total: lessonTotal,
              showSizeChanger: true,
              pageSizeOptions: ['20', '50', '100'],
              showTotal: (t: number) => `共 ${t} 节课`,
            }"
            row-key="scheduleId"
            size="middle"
            :scroll="{ x: 1200 }"
            @change="onLessonTableChange"
          >
            <template #bodyCell="{ column, record }">
              <span v-if="column.dataIndex === 'startTime'">{{ timeOf(record.startTime) }} - {{ timeOf(record.endTime) }}</span>
              <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
              <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
              <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
              <span v-else-if="column.dataIndex === 'attendance'">{{ record.actualCount ?? 0 }} / {{ record.shouldCount ?? 0 }}</span>
              <span v-else-if="column.dataIndex === 'commentCount'">
                <a-tag v-if="record.commentCount > 0" color="green">{{ record.commentCount }}</a-tag>
                <a-tag v-else>未点评</a-tag>
              </span>
              <span v-else-if="column.dataIndex === 'readRate'">{{ rate(record.readCount, record.commentCount) }}</span>
              <a v-else-if="column.dataIndex === 'action'" @click="openLesson(record)">查看点评</a>
              <span v-else>{{ record[column.dataIndex] }}</span>
            </template>
            <template #emptyText>
              <div style="padding: 32px 0; color: #8f959e">所选条件下没有已上课的课次</div>
            </template>
          </a-table>
        </a-spin>
      </template>

      <template v-else-if="tab === 'comments'">
        <div class="summary-bar">
          <span>点评总数<b>{{ commentSummary?.total ?? 0 }}</b></span>
          <span>涉及学员<b>{{ commentSummary?.students ?? 0 }}</b></span>
          <span>涉及班级<b>{{ commentSummary?.classes ?? 0 }}</b></span>
          <span>家长未读<b>{{ commentSummary?.unread ?? 0 }}</b></span>
          <span>阅读率<b>{{ commentReadRate }}</b></span>
        </div>

        <a-spin :spinning="loading">
          <a-table
            :columns="commentColumns"
            :data-source="commentRows"
            :pagination="{
              current: commentPage,
              pageSize: commentPageSize,
              total: commentTotal,
              showSizeChanger: true,
              pageSizeOptions: ['20', '50', '100'],
              showTotal: (t: number) => `共 ${t} 条`,
            }"
            row-key="id"
            size="middle"
            :scroll="{ x: 1400 }"
            @change="onCommentTableChange"
          >
            <template #bodyCell="{ column, record }">
              <span v-if="column.dataIndex === 'lessonDate'">{{ record.lessonDate || '—' }}</span>
              <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
              <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
              <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
              <span v-else-if="column.dataIndex === 'content'" class="comment-content">{{ record.content }}</span>
              <a-tag v-else-if="column.dataIndex === 'rating'" :color="ratingColor(record.rating)">
                {{ record.rating || '—' }}
              </a-tag>
              <span v-else-if="column.dataIndex === 'readAt'">
                <a-tag v-if="record.readAt" color="green">已读</a-tag>
                <a-tag v-else>未读</a-tag>
              </span>
              <a v-else-if="column.dataIndex === 'action'" @click="openDetail(record)">查看</a>
              <span v-else>{{ record[column.dataIndex] }}</span>
            </template>
            <template #emptyText>
              <div style="padding: 32px 0; color: #8f959e">所选条件下暂无点评记录</div>
            </template>
          </a-table>
        </a-spin>
      </template>

      <template v-else>
        <div class="summary-bar">
          <span>点评教师<b>{{ stats.length }}</b></span>
          <span>点评总数<b>{{ statsTotals.comments }}</b></span>
          <span>学员人次<b>{{ statsTotals.students }}</b></span>
          <span>家长已读<b>{{ statsTotals.readComments }}</b></span>
          <span>阅读率<b>{{ statsReadRate }}</b></span>
        </div>

        <a-spin :spinning="loading">
          <a-table
            :columns="statColumns"
            :data-source="stats"
            :pagination="{ pageSize: 20, showSizeChanger: true, pageSizeOptions: ['20', '50', '100'], showTotal: (t: number) => `共 ${t} 位教师` }"
            row-key="teacherName"
            size="middle"
          >
            <template #bodyCell="{ column, record }">
              <span v-if="column.dataIndex === 'readRate'">{{ rate(record.readComments, record.comments) }}</span>
              <span v-else>{{ record[column.dataIndex] }}</span>
            </template>
            <template #emptyText>
              <div style="padding: 32px 0; color: #8f959e">所选条件下暂无点评统计数据</div>
            </template>
          </a-table>
        </a-spin>
      </template>
    </div>

    <a-drawer v-model:open="drawerOpen" title="点评详情" :width="560" placement="right">
      <a-descriptions v-if="current" :column="2" size="small" bordered>
        <a-descriptions-item label="学员">{{ current.studentName }}</a-descriptions-item>
        <a-descriptions-item label="点评教师">{{ current.teacherName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="班级">{{ current.className || '—' }}</a-descriptions-item>
        <a-descriptions-item label="课程">{{ current.courseName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="上课日期">{{ current.lessonDate || '—' }}</a-descriptions-item>
        <a-descriptions-item label="上课时间">{{ timeOf(current.startTime) }}</a-descriptions-item>
        <a-descriptions-item label="校区">{{ current.campusName }}</a-descriptions-item>
        <a-descriptions-item label="评价">
          <a-tag :color="ratingColor(current.rating)">{{ current.rating || '—' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="家长已读">
          <a-tag v-if="current.readAt" color="green">已读 {{ current.readAt }}</a-tag>
          <a-tag v-else>未读</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="点评时间">{{ current.createdAt || '—' }}</a-descriptions-item>
        <a-descriptions-item label="点评内容" :span="2">
          <div class="detail-content">{{ current.content || '—' }}</div>
        </a-descriptions-item>
      </a-descriptions>
    </a-drawer>
  </div>
</template>

<style scoped>
.comment-content {
  color: #4e5969;
  white-space: normal;
  line-height: 1.6;
}
.detail-content {
  color: #1f2329;
  white-space: pre-wrap;
  line-height: 1.75;
  max-height: 420px;
  overflow-y: auto;
}
</style>
