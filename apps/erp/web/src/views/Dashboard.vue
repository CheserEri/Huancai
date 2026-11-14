<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  BellOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  DownOutlined,
  EditOutlined,
  FileTextOutlined,
  RightOutlined,
  ScheduleOutlined,
  UserAddOutlined,
  WarningOutlined,
} from '@ant-design/icons-vue'
import { fetchDashboard, fetchGradeExams, type DashboardSummary, type GradeExamRow } from '../api/client'

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
  { icon: ClockCircleOutlined, color: '#F5222D', bg: '#FFF1F2', value: String(stats.value?.remainLessons ?? 0), unit: '课时', label: '剩余课时总量', compact: true },
  { icon: WarningOutlined, color: '#FA8C16', bg: '#FFF7E6', value: `¥${Number(stats.value?.debt ?? 0).toLocaleString('zh-CN')}`, unit: '', label: '待处理欠费', compact: false },
  { icon: CheckCircleFilled, color: '#007AFF', bg: '#F0F7FF', value: String(stats.value?.activeStudents ?? 0), unit: '', label: '在读学员', compact: false },
  { icon: BellOutlined, color: '#13C2C2', bg: '#E9FBFB', value: String(stats.value?.todayOrders ?? 0), unit: '', label: '今日报名订单', compact: false },
  { icon: UserAddOutlined, color: '#722ED1', bg: '#F6F1FE', value: String(stats.value?.students ?? 0), unit: '', label: '学员总数', compact: false },
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

const news = [
  { title: '功能更新 | 电子合同功能升级', date: '09/18' },
  { title: '功能更新 | 订单标签必选开关、支持批...', date: '08/08' },
  { title: '功能更新 | 收款对账升级', date: '07/18' },
  { title: '功能更新 | 财务账目按校区锁定等', date: '07/04' },
]

const chartBars = [12, 18, 9, 22, 16, 25, 14, 20, 11, 26, 18, 23]
</script>

<template>
  <div class="page dash">
    <div class="card todo">
      <div v-for="item in todos" :key="item.label" class="todo-item">
        <span class="todo-badge" :style="{ background: item.bg }">
          <component :is="item.icon" class="todo-icon" :style="{ color: item.color }" />
        </span>
        <span v-if="item.compact" class="todo-inline">
          {{ item.label }} <b>{{ item.value }}</b> <span class="unit">{{ item.unit }}</span>
        </span>
        <span v-else class="todo-main">
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
            <div class="card-title">今日总览</div>
          </div>
          <div class="legend">
            <span><i class="dot" style="background: #007aff" />新生报名</span>
            <span><i class="dot" style="background: #13c2c2" />老生报名</span>
            <span><i class="dot" style="background: #f6bd16" />新增咨询</span>
          </div>
          <svg class="chart" viewBox="0 0 640 190" preserveAspectRatio="none">
            <line v-for="n in 5" :key="n" x1="0" :y1="n * 32" x2="640" :y2="n * 32" stroke="#EEF0F3" />
            <rect
              v-for="(bar, index) in chartBars"
              :key="index"
              :x="index * 52 + 12"
              :y="160 - bar * 5"
              width="18"
              :height="bar * 5"
              rx="2"
              fill="#007AFF"
              opacity="0.85"
            />
            <polyline
              :points="chartBars.map((bar, index) => `${index * 52 + 21},${150 - bar * 3.2}`).join(' ')"
              fill="none"
              stroke="#13C2C2"
              stroke-width="2"
            />
            <polyline
              :points="chartBars.map((bar, index) => `${index * 52 + 21},${165 - bar * 2.4}`).join(' ')"
              fill="none"
              stroke="#F6BD16"
              stroke-width="2"
            />
          </svg>
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

        <div class="card">
          <div class="card-head">
            <div class="card-title">产品动态</div>
            <span class="card-extra link-extra">更多 <RightOutlined style="font-size: 10px" /></span>
          </div>
          <div v-for="item in news" :key="item.title" class="news-item">
            <span class="news-title">{{ item.title }}</span>
            <span class="news-date">{{ item.date }}</span>
          </div>
        </div>

        <div class="banner">
          <div class="banner-tag">双十一招生</div>
          <div class="banner-title">7 套双十一招生方案<br />助你提前抢占生源</div>
          <div class="banner-btn">点击查看详情</div>
        </div>

        <div class="card">
          <div class="card-head">
            <div class="card-title">运营指导</div>
          </div>
          <div class="guide-item"><span class="guide-icon">优</span>优质案例：县域机构续费提升 12%</div>
          <div class="guide-item"><span class="guide-icon">学</span>学习最佳实践：课时账户对账</div>
          <div class="guide-item"><span class="guide-icon">交</span>学习交流：校长经营社群</div>
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

.todo-inline b {
  font-size: 16px;
  color: var(--brand);
  font-weight: 500;
  margin: 0 2px;
}

.todo-inline .unit {
  margin-left: 1px;
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

.news-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 13px;
}

.news-item:last-child {
  border-bottom: none;
}

.news-title {
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 250px;
}

.news-date {
  color: var(--text-3);
  font-size: 12px;
}

.banner {
  margin-top: 12px;
  border-radius: 8px;
  padding: 20px 18px;
  background: linear-gradient(120deg, #ff5f6d, #ff9966 60%, #ffd36e);
  color: #fff;
  position: relative;
  overflow: hidden;
}

.banner-tag {
  display: inline-block;
  background: rgba(255, 255, 255, 0.24);
  border-radius: 10px;
  font-size: 11px;
  padding: 2px 10px;
}

.banner-title {
  font-size: 19px;
  font-weight: 700;
  line-height: 1.5;
  margin: 10px 0 14px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.12);
}

.banner-btn {
  display: inline-block;
  background: #fff;
  color: #e8452c;
  font-size: 12px;
  font-weight: 600;
  padding: 5px 14px;
  border-radius: 14px;
  cursor: pointer;
}

.guide-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  font-size: 13px;
  color: var(--text-2);
  border-bottom: 1px dashed var(--line);
}

.guide-item:last-child {
  border-bottom: none;
}

.guide-icon {
  width: 22px;
  height: 22px;
  border-radius: 4px;
  background: #e8f2ff;
  color: var(--brand);
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
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
