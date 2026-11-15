<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  CheckCircleFilled,
  ClockCircleOutlined,
  DownOutlined,
  EditOutlined,
  FileTextOutlined,
  PauseCircleOutlined,
  RightOutlined,
  ScheduleOutlined,
  UserAddOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { fetchDashboard, fetchGradeExams, type DashboardSummary, type GradeExamRow } from '../api/client'

const router = useRouter()
const stepsOpen = ref(false)
const stats = ref<DashboardSummary | null>(null)
const gradeExams = ref<GradeExamRow[]>([])

const gradePending = computed(() => gradeExams.value.filter((exam) => exam.status === '草稿'))
const latestExam = computed(() => gradeExams.value.find((exam) => exam.status !== '草稿') ?? gradeExams.value[0] ?? null)

onMounted(async () => {
  try {
    stats.value = await fetchDashboard()
  } catch (e) {
    console.error(e)
  }
  try {
    gradeExams.value = await fetchGradeExams()
  } catch (e) {
    console.error(e)
  }
})

const todos = computed(() => [
  {
    icon: WarningOutlined,
    color: '#FA8C16',
    bg: '#FFF7E6',
    value: String(stats.value?.arrearsStudents ?? 0),
    unit: '人',
    label: '剩余欠费人数',
    onClick: () => router.push({ path: '/student', query: { debt: '1' } }),
  },
  {
    icon: CheckCircleFilled,
    color: '#007AFF',
    bg: '#F0F7FF',
    value: String(stats.value?.studyingStudents ?? 0),
    unit: '人',
    label: '在读人员',
  },
  {
    icon: ClockCircleOutlined,
    color: '#13C2C2',
    bg: '#E9FBFB',
    value: String(stats.value?.suspendedStudents ?? 0),
    unit: '人',
    label: '休课人员',
  },
  {
    icon: PauseCircleOutlined,
    color: '#722ED1',
    bg: '#F6F1FE',
    value: String(stats.value?.pausedStudents ?? 0),
    unit: '人',
    label: '停课人员',
  },
])

const steps = [
  { title: '员工', desc: '创建账号、分配权限' },
  { title: '课程', desc: '收费模式、课程价格' },
  { title: '班级', desc: '班级名称、满班人数' },
  { title: '学员', desc: '学员信息、批量导入' },
]

const quickActions = computed(() => [
  { icon: FileTextOutlined, color: '#F5222D', title: '咨询记录', desc: '今日咨询 0' },
  { icon: UserAddOutlined, color: '#FA8C16', title: '报名续费', desc: `今日报名订单 ${stats.value?.todayOrders ?? 0}` },
  { icon: ScheduleOutlined, color: '#007AFF', title: '排课', desc: `今日课次 ${stats.value?.todayClasses ?? 0}` },
  { icon: EditOutlined, color: '#13C2C2', title: '记上课', desc: `在读学员 ${stats.value?.activeStudents ?? 0}` },
])

const enrollTrend = computed(() => stats.value?.enrollTrend ?? [])
const trendTotal = computed(() => enrollTrend.value.reduce((sum, item) => sum + item.count, 0))
const trendMax = computed(() => Math.max(1, ...enrollTrend.value.map((item) => item.count)))
const trendPoints = computed(() =>
  enrollTrend.value
    .map((item, index) => {
      const x = 10 + (index / Math.max(1, enrollTrend.value.length - 1)) * 620
      const y = 170 - (item.count / trendMax.value) * 140
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' '),
)
const trendArea = computed(() => (trendPoints.value ? `10,170 ${trendPoints.value} 630,170` : ''))
const trendDots = computed(() =>
  enrollTrend.value
    .map((item, index) => ({
      x: 10 + (index / Math.max(1, enrollTrend.value.length - 1)) * 620,
      y: 170 - (item.count / trendMax.value) * 140,
      count: item.count,
    }))
    .filter((dot) => dot.count > 0),
)
const trendTicks = computed(() => {
  const list = enrollTrend.value
  if (!list.length) return []
  return [0, Math.floor((list.length - 1) / 2), list.length - 1].map((index) => ({
    x: 10 + (index / Math.max(1, list.length - 1)) * 620,
    text: list[index].date.slice(5),
  }))
})
</script>

<template>
  <div class="page dash">
    <div class="card todo">
      <div
        v-for="item in todos"
        :key="item.label"
        class="todo-item"
        :class="{ clickable: item.onClick }"
        @click="item.onClick?.()"
      >
        <span class="todo-badge" :style="{ background: item.bg }">
          <component :is="item.icon" class="todo-icon" :style="{ color: item.color }" />
        </span>
        <span class="todo-main">
          <span class="todo-value">{{ item.value }}<i v-if="item.unit" class="todo-unit">{{ item.unit }}</i></span>
          <span class="todo-label">{{ item.label }}</span>
        </span>
      </div>
    </div>

    <div class="grid">
      <div class="col-main">
        <div class="card">
          <div class="card-head" :style="{ marginBottom: stepsOpen ? '12px' : '0' }">
            <div class="card-title">
              四步快速完成
              <span class="setup-tag">初始设置</span>
              <span class="setup-done">已完成 4/4 步</span>
            </div>
            <span class="card-extra link-extra" @click="stepsOpen = !stepsOpen">
              {{ stepsOpen ? '收起' : '展开' }}
              <DownOutlined class="expand-arrow" :class="{ open: stepsOpen }" />
            </span>
          </div>
          <div v-if="stepsOpen" class="steps">
            <div v-for="step in steps" :key="step.title" class="step">
              <CheckCircleFilled class="step-icon" />
              <div>
                <div class="step-title">{{ step.title }}</div>
                <div class="step-desc">{{ step.desc }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div class="card-title">常用功能</div>
            <span class="card-extra">设置</span>
          </div>
          <div class="quick">
            <div v-for="action in quickActions" :key="action.title" class="quick-item">
              <component :is="action.icon" class="quick-icon" :style="{ color: action.color }" />
              <div>
                <div class="quick-title">{{ action.title }}</div>
                <div class="quick-desc">{{ action.desc }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div class="card-title">报名人数趋势</div>
            <span class="card-extra trend-total">近 30 天 {{ trendTotal }} 人</span>
          </div>
          <svg class="chart" viewBox="0 0 640 190" preserveAspectRatio="none">
            <defs>
              <linearGradient id="enrollFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#007AFF" stop-opacity="0.16" />
                <stop offset="100%" stop-color="#007AFF" stop-opacity="0" />
              </linearGradient>
            </defs>
            <line v-for="n in 5" :key="n" x1="0" :y1="n * 32" x2="640" :y2="n * 32" stroke="#EEF0F3" />
            <polygon v-if="trendArea" :points="trendArea" fill="url(#enrollFill)" />
            <polyline v-if="trendPoints" :points="trendPoints" fill="none" stroke="#007AFF" stroke-width="2" />
            <circle
              v-for="(dot, index) in trendDots"
              :key="index"
              :cx="dot.x"
              :cy="dot.y"
              r="2.5"
              fill="#007AFF"
            />
          </svg>
          <div class="axis">
            <span v-for="tick in trendTicks" :key="tick.text" :style="{ left: `${(tick.x / 640) * 100}%` }">
              {{ tick.text }}
            </span>
          </div>
        </div>
      </div>

      <div class="col-side">
        <div class="card">
          <div class="card-head">
            <div class="card-title">成绩中心</div>
            <router-link to="/gradeExam" class="card-extra link-extra">
              进入 <RightOutlined style="font-size: 10px" />
            </router-link>
          </div>
          <div class="grade-summary">
            <div class="grade-summary-item">
              <span class="grade-summary-label">待录入考试</span>
              <span class="grade-summary-value">{{ gradePending.length }}</span>
            </div>
            <div class="grade-summary-item">
              <span class="grade-summary-label">最近考试</span>
              <span class="grade-summary-value small">{{ latestExam ? latestExam.name : '暂无考试' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash {
  padding: 4px 20px 20px;
}

.todo {
  min-height: 158px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.todo-badge {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.todo-icon {
  font-size: 17px;
}

.todo-main {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.todo-value {
  font-size: 22px;
  font-weight: 500;
  line-height: 1;
  color: var(--brand);
}

.todo-unit {
  font-size: 12px;
  font-weight: 400;
  font-style: normal;
  margin-left: 3px;
}

.todo-label {
  font-size: 13px;
  color: var(--text-3);
}

.todo-inline {
  font-size: 13px;
  color: var(--text-3);
}

.grid {
  display: flex;
  gap: 16px;
  margin-top: 16px;
  align-items: flex-start;
}

.expand-arrow {
  font-size: 10px;
  margin-left: 4px;
  transition: transform 0.2s;
}

.expand-arrow.open {
  transform: rotate(180deg);
}

.link-extra {
  color: var(--brand);
}

.col-main {
  flex: 1;
  min-width: 0;
}

.col-side {
  width: 340px;
  flex: 0 0 340px;
}

.col-main > .card + .card {
  margin-top: 12px;
}

.setup-tag {
  font-size: 12px;
  font-weight: 400;
  color: var(--brand);
  margin-left: 6px;
}

.setup-done {
  font-size: 12px;
  font-weight: 400;
  color: #52c41a;
  margin-left: 10px;
}

.steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.step {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.step-icon {
  color: #52c41a;
  font-size: 18px;
  margin-top: 1px;
}

.step-title {
  font-size: 14px;
  font-weight: 500;
}

.step-desc {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 4px;
}

.quick {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
}

.quick-item:hover {
  background: #f7f9fc;
}

.quick-icon {
  font-size: 24px;
}

.quick-title {
  font-size: 14px;
  font-weight: 500;
}

.quick-desc {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
}

.legend {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: var(--text-2);
  margin-bottom: 8px;
}

.legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

.chart {
  width: 100%;
  height: 190px;
}

.trend-total {
  color: var(--text-3);
}

.axis {
  position: relative;
  height: 18px;
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-3);
}

.axis span {
  position: absolute;
  transform: translateX(-50%);
  white-space: nowrap;
}

.grade-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.grade-summary-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  background: #fafbfc;
  font-size: 13px;
}

.grade-summary-label {
  color: var(--text-2);
}

.grade-summary-value {
  color: var(--brand);
  font-size: 20px;
  font-weight: 600;
}

.grade-summary-value.small {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
}
</style>
