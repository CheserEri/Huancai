<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  fetchClassStudents,
  fetchGradeAnalysis,
  fetchGradeClassExams,
  fetchGradeClassOptions,
  fetchGradeExams,
  fetchGradeStudentScores,
  type GradeAnalysis,
  type GradeClassExam,
  type GradeClassOption,
  type GradeExamRow,
  type GradeStudentItem,
} from '../api/client'

const activeTab = ref('exam')

/* ---------------- 单场考试分析 ---------------- */
const exams = ref<GradeExamRow[]>([])
const examId = ref<number | undefined>(undefined)
const analysis = ref<GradeAnalysis | null>(null)
const loading = ref(false)

const examSelectOptions = computed(() =>
  exams.value.map((exam) => ({
    label: `${exam.name}（${exam.examDate ?? '未定日期'} · ${exam.status}）`,
    value: exam.id,
  })),
)

const hasNumeric = computed(() => (analysis.value?.distributions ?? []).some((item) => item.count > 0))

const distributionMax = computed(() =>
  Math.max(1, ...(analysis.value?.distributions ?? []).map((item) => item.count)),
)

const chartWidth = 620
const chartHeight = 240
const chartPadding = { top: 20, right: 16, bottom: 34, left: 40 }

const barLayout = computed(() => {
  const items = analysis.value?.distributions ?? []
  const plotWidth = chartWidth - chartPadding.left - chartPadding.right
  const plotHeight = chartHeight - chartPadding.top - chartPadding.bottom
  const step = items.length ? plotWidth / items.length : plotWidth
  const barWidth = Math.min(56, step * 0.55)
  return items.map((item, index) => {
    const height = (item.count / distributionMax.value) * plotHeight
    return {
      label: item.label,
      count: item.count,
      x: chartPadding.left + step * index + (step - barWidth) / 2,
      y: chartPadding.top + plotHeight - height,
      width: barWidth,
      height: Math.max(height, item.count > 0 ? 2 : 0),
      centerX: chartPadding.left + step * index + step / 2,
      baseline: chartPadding.top + plotHeight,
    }
  })
})

const classBarLayout = computed(() => {
  const items = analysis.value?.classes ?? []
  const fullScore = Number(analysis.value?.fullScore ?? 100)
  const width = 620
  const rowHeight = 34
  const height = Math.max(items.length * rowHeight + 24, 80)
  const labelWidth = 120
  const maxWidth = width - labelWidth - 70
  return {
    height,
    rows: items.map((item, index) => {
      const avg = Number(item.avgScore ?? 0)
      return {
        className: item.className,
        avg: item.avgScore,
        passRate: item.passRate,
        width: Math.max((avg / fullScore) * maxWidth, avg > 0 ? 2 : 0),
        y: index * rowHeight + 16,
        labelWidth,
        maxWidth,
      }
    }),
  }
})

/* ---------------- 班级考试趋势 ---------------- */
const classOptions = ref<GradeClassOption[]>([])
const classTrendId = ref<number | undefined>(undefined)
const classTrendExams = ref<GradeClassExam[]>([])
const classTrendLoading = ref(false)

const classSelectOptions = computed(() =>
  classOptions.value.map((item) => ({
    label: item.courseName ? `${item.name}（${item.courseName}）` : item.name,
    value: item.id,
  })),
)

function round1(value: number) {
  return Math.round(value * 10) / 10
}

function isNumber(value: number | null | undefined): value is number {
  return value !== null && value !== undefined && Number.isFinite(value)
}

function rateOf(score: number | null | undefined, fullScore: number | null | undefined) {
  if (score === null || score === undefined || !fullScore) return null
  const value = (Number(score) * 100) / Number(fullScore)
  return Number.isFinite(value) ? round1(value) : null
}

function sortByExamDate<T extends { examDate: string | null; examId: number }>(list: T[]) {
  return [...list].sort((a, b) => {
    const left = a.examDate ?? ''
    const right = b.examDate ?? ''
    if (left === right) return a.examId - b.examId
    return left < right ? -1 : 1
  })
}

const classTrendOrdered = computed(() => sortByExamDate(classTrendExams.value))

const classTrendChartItems = computed(() =>
  classTrendOrdered.value.filter((item) => rateOf(item.avgScore, item.fullScore) !== null),
)

const classSummary = computed(() => {
  const items = classTrendChartItems.value
  const rates = items.map((item) => rateOf(item.avgScore, item.fullScore)).filter(isNumber)
  const passRates = items.map((item) => item.passRate).filter(isNumber)
  const latest = items.length ? items[items.length - 1] : null
  return {
    count: items.length,
    avgRate: rates.length ? round1(rates.reduce((sum, value) => sum + value, 0) / rates.length) : null,
    avgPassRate: passRates.length
      ? round1(passRates.reduce((sum, value) => sum + value, 0) / passRates.length)
      : null,
    latestRate: latest ? rateOf(latest.avgScore, latest.fullScore) : null,
    latestDate: latest?.examDate ?? null,
  }
})

const classTrendRows = computed(() => {
  const ordered = classTrendOrdered.value.map((item) => ({
    ...item,
    rate: rateOf(item.avgScore, item.fullScore),
  }))
  const decorated = ordered.map((row, index) => {
    let delta: number | null = null
    if (row.rate !== null) {
      for (let i = index - 1; i >= 0; i--) {
        const previous = ordered[i].rate
        if (previous !== null) {
          delta = round1(row.rate - previous)
          break
        }
      }
    }
    return { ...row, delta }
  })
  return decorated.reverse()
})

/* ---------------- 学员成绩波动 ---------------- */
const studentClassId = ref<number | undefined>(undefined)
const studentOptions = ref<{ label: string; value: number }[]>([])
const studentId = ref<number | undefined>(undefined)
const studentScores = ref<GradeStudentItem[]>([])
const studentLoading = ref(false)

const studentOrdered = computed(() => sortByExamDate(studentScores.value))

const studentChartItems = computed(() =>
  studentOrdered.value.filter((item) => rateOf(item.totalScore, item.fullScore) !== null),
)

const studentSummary = computed(() => {
  const items = studentChartItems.value
  const rates = items.map((item) => rateOf(item.totalScore, item.fullScore)).filter(isNumber)
  const latest = items.length ? items[items.length - 1] : null
  const first = items.length ? items[0] : null
  const latestRate = latest ? rateOf(latest.totalScore, latest.fullScore) : null
  const firstRate = first ? rateOf(first.totalScore, first.fullScore) : null
  return {
    count: studentChartItems.value.length,
    avgRate: rates.length ? round1(rates.reduce((sum, value) => sum + value, 0) / rates.length) : null,
    latest,
    latestRate,
    change: latestRate !== null && firstRate !== null ? round1(latestRate - firstRate) : null,
  }
})

const studentRows = computed(() =>
  [...studentOrdered.value].reverse().map((item) => ({
    ...item,
    rate: rateOf(item.totalScore, item.fullScore),
    classAvgRate: rateOf(item.classAvgScore, item.fullScore),
  })),
)

/* ---------------- 折线图布局 ---------------- */
const chartBox = { width: 760, height: 260, left: 46, right: 24, top: 24, bottom: 46 }

interface ChartSeriesItem {
  label: string
  value: number | null
  tip: string
}

function buildChart(items: ChartSeriesItem[]) {
  const plotWidth = chartBox.width - chartBox.left - chartBox.right
  const plotHeight = chartBox.height - chartBox.top - chartBox.bottom
  const step = items.length > 1 ? plotWidth / (items.length - 1) : 0
  const xOf = (index: number) =>
    chartBox.left + (items.length > 1 ? step * index : plotWidth / 2)
  const points: { x: number; y: number; value: number; tip: string }[] = []
  items.forEach((item, index) => {
    if (item.value === null) return
    const y = chartBox.top + plotHeight * (1 - Math.min(Math.max(item.value, 0), 100) / 100)
    points.push({ x: xOf(index), y, value: item.value, tip: item.tip })
  })
  const path = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)},${point.y.toFixed(1)}`)
    .join(' ')
  const grids = [0, 20, 40, 60, 80, 100].map((value) => ({
    value,
    y: chartBox.top + plotHeight * (1 - value / 100),
  }))
  const labels = items.map((item, index) => ({ x: xOf(index), text: item.label }))
  return { points, path, grids, labels }
}

function thinLabels(labels: { x: number; text: string }[]) {
  if (labels.length <= 7) return labels
  return labels.filter((_, index) => index % 2 === 0 || index === labels.length - 1)
}

function shortDate(value: string | null | undefined) {
  if (!value) return '未定'
  return value.length >= 10 ? value.slice(5, 10) : value
}

const classChart = computed(() => {
  const items = classTrendChartItems.value
  const labels = items.map((item) => shortDate(item.examDate))
  const avg = buildChart(
    items.map((item, index) => {
      const rate = rateOf(item.avgScore, item.fullScore)
      return {
        label: labels[index],
        value: rate,
        tip: `${item.examName}\n平均分 ${scoreText(item.avgScore)} / 满分 ${item.fullScore}\n得分率 ${rateText(rate)}`,
      }
    }),
  )
  const pass = buildChart(
    items.map((item, index) => ({
      label: labels[index],
      value: item.passRate,
      tip: `${item.examName}\n及格率 ${rateText(item.passRate)}\n优秀率 ${rateText(item.excellentRate)}`,
    })),
  )
  return { avg, pass }
})

const classChartLabels = computed(() => thinLabels(classChart.value.avg.labels))

const studentChart = computed(() => {
  const items = studentChartItems.value
  const labels = items.map((item) => shortDate(item.examDate))
  const scoreSeries = buildChart(
    items.map((item, index) => {
      const rate = rateOf(item.totalScore, item.fullScore)
      return {
        label: labels[index],
        value: rate,
        tip: `${item.examName}\n成绩 ${scoreText(item.totalScore)} / 满分 ${item.fullScore}\n得分率 ${rateText(rate)}`,
      }
    }),
  )
  const classSeries = buildChart(
    items.map((item, index) => ({
      label: labels[index],
      value: rateOf(item.classAvgScore, item.fullScore),
      tip: `${item.examName}\n班级平均 ${scoreText(item.classAvgScore)} / 满分 ${item.fullScore}`,
    })),
  )
  return { score: scoreSeries, classAvg: classSeries }
})

const studentChartLabels = computed(() => thinLabels(studentChart.value.score.labels))

/* ---------------- 数据加载 ---------------- */
async function loadExams() {
  try {
    exams.value = await fetchGradeExams()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载考试列表失败')
  }
}

async function load() {
  if (!examId.value) {
    analysis.value = null
    return
  }
  loading.value = true
  try {
    analysis.value = await fetchGradeAnalysis(examId.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载成绩分析失败')
  } finally {
    loading.value = false
  }
}

async function loadClassOptions() {
  try {
    classOptions.value = await fetchGradeClassOptions()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载班级列表失败')
  }
}

async function loadClassTrend() {
  if (!classTrendId.value) {
    classTrendExams.value = []
    return
  }
  classTrendLoading.value = true
  try {
    classTrendExams.value = await fetchGradeClassExams(classTrendId.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载班级考试趋势失败')
  } finally {
    classTrendLoading.value = false
  }
}

async function loadStudentOptions() {
  studentOptions.value = []
  studentId.value = undefined
  studentScores.value = []
  if (!studentClassId.value) return
  try {
    const list = await fetchClassStudents(studentClassId.value)
    studentOptions.value = list
      .filter((item) => item.status === '在读')
      .map((item) => ({ label: item.studentName, value: item.studentId }))
    const first = studentOptions.value[0]
    if (first) {
      studentId.value = first.value
      await loadStudentScores()
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载班级学员失败')
  }
}

async function loadStudentScores() {
  if (!studentId.value) {
    studentScores.value = []
    return
  }
  studentLoading.value = true
  try {
    studentScores.value = await fetchGradeStudentScores(studentId.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载学员成绩失败')
  } finally {
    studentLoading.value = false
  }
}

/* ---------------- 展示格式化 ---------------- */
function percentText(value: number | null | undefined) {
  return value === null || value === undefined ? '—' : `${value}%`
}

function scoreText(value: number | null | undefined) {
  return value === null || value === undefined ? '—' : value
}

function rateText(value: number | null | undefined) {
  return value === null || value === undefined ? '—' : `${value}%`
}

function deltaText(value: number | null | undefined) {
  if (value === null || value === undefined) return '—'
  return `${value > 0 ? '+' : ''}${value}`
}

function deltaClass(value: number | null | undefined) {
  if (value === null || value === undefined || value === 0) return ''
  return value > 0 ? 'delta-up' : 'delta-down'
}

const rankColumns = [
  { title: '排名', dataIndex: 'rank', width: 70, align: 'center' as const },
  { title: '学员', dataIndex: 'studentName', width: 120 },
  { title: '班级', dataIndex: 'className', width: 160 },
  { title: '成绩', dataIndex: 'totalScore', width: 100, align: 'right' as const },
  { title: '等级', dataIndex: 'level', width: 100 },
]

const classColumns = [
  { title: '班级', dataIndex: 'className', width: 180 },
  { title: '录入人数', dataIndex: 'count', width: 100, align: 'center' as const },
  { title: '平均分', dataIndex: 'avgScore', width: 100, align: 'right' as const },
  { title: '最高分', dataIndex: 'maxScore', width: 100, align: 'right' as const },
  { title: '最低分', dataIndex: 'minScore', width: 100, align: 'right' as const },
  { title: '及格率', dataIndex: 'passRate', width: 100, align: 'right' as const },
  { title: '优秀率', dataIndex: 'excellentRate', width: 100, align: 'right' as const },
]

const classTrendColumns = [
  { title: '考试', dataIndex: 'examName', width: 220, ellipsis: true },
  { title: '日期', dataIndex: 'examDate', width: 104 },
  { title: '类型', dataIndex: 'examType', width: 88 },
  { title: '状态', dataIndex: 'status', width: 76 },
  { title: '录入进度', dataIndex: 'enteredCount', width: 92, align: 'center' as const },
  { title: '平均分', dataIndex: 'avgScore', width: 82, align: 'right' as const },
  { title: '得分率', dataIndex: 'rate', width: 82, align: 'right' as const },
  { title: '最高 / 最低', dataIndex: 'range', width: 112, align: 'right' as const },
  { title: '及格率', dataIndex: 'passRate', width: 82, align: 'right' as const },
  { title: '优秀率', dataIndex: 'excellentRate', width: 82, align: 'right' as const },
  { title: '较上次', dataIndex: 'delta', width: 78, align: 'right' as const },
]

const studentColumns = [
  { title: '考试', dataIndex: 'examName', width: 220, ellipsis: true },
  { title: '日期', dataIndex: 'examDate', width: 104 },
  { title: '类型', dataIndex: 'examType', width: 88 },
  { title: '成绩', dataIndex: 'totalScore', width: 84, align: 'right' as const },
  { title: '得分率', dataIndex: 'rate', width: 82, align: 'right' as const },
  { title: '班级平均', dataIndex: 'classAvgScore', width: 92, align: 'right' as const },
  { title: '班级排名', dataIndex: 'rankClass', width: 84, align: 'center' as const },
  { title: '校区排名', dataIndex: 'rankCampus', width: 84, align: 'center' as const },
  { title: '等级', dataIndex: 'level', width: 78 },
]

onMounted(async () => {
  await Promise.all([loadExams(), loadClassOptions()])
  if (exams.value.length) {
    examId.value = exams.value[0].id
    await load()
  }
  const preferred = analysis.value?.classes?.[0]?.classId ?? classOptions.value[0]?.id
  classTrendId.value = preferred
  studentClassId.value = preferred
  await Promise.all([loadClassTrend(), loadStudentOptions()])
})
</script>

<template>
  <div class="grade-page">
    <a-tabs v-model:activeKey="activeTab" class="analysis-tabs">
      <a-tab-pane key="exam" tab="单场考试分析">
        <div class="filter-line">
          <a-select
            v-model:value="examId"
            size="small"
            style="width: 300px"
            placeholder="选择要分析的考试"
            show-search
            :options="examSelectOptions"
            @change="load"
          />
          <span v-if="analysis" class="filter-label">
            {{ analysis.examType }} · 满分 {{ analysis.fullScore }} · 及格线 {{ analysis.passLine }} 分 · 自动评级
          </span>
        </div>

        <a-spin :spinning="loading">
          <div v-if="!analysis" class="empty-panel">
            <a-empty description="请选择一场考试查看成绩分析" />
          </div>

          <template v-else>
            <div class="stat-grid">
              <div class="stat-card">
                <div class="stat-label">应考人数</div>
                <div class="stat-value">{{ analysis.studentCount }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">已录入</div>
                <div class="stat-value">{{ analysis.enteredCount }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">未录入 / 缺考 / 免考</div>
                <div class="stat-value small">
                  {{ analysis.missingCount }} / {{ analysis.absentCount }} / {{ analysis.exemptCount }}
                </div>
              </div>
              <div class="stat-card">
                <div class="stat-label">平均分</div>
                <div class="stat-value">{{ scoreText(analysis.avgScore) }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">最高 / 最低</div>
                <div class="stat-value small">
                  {{ scoreText(analysis.maxScore) }} / {{ scoreText(analysis.minScore) }}
                </div>
              </div>
              <div class="stat-card">
                <div class="stat-label">及格率</div>
                <div class="stat-value">{{ percentText(analysis.passRate) }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">优秀率</div>
                <div class="stat-value">{{ percentText(analysis.excellentRate) }}</div>
              </div>
            </div>

            <div class="panel-grid">
              <div class="panel">
                <div class="panel-title">分数段分布</div>
                <div v-if="!hasNumeric" class="panel-empty">
                  暂无有效分数（等级制考试或尚未录入正常成绩）
                </div>
                <svg v-else :viewBox="`0 0 ${chartWidth} ${chartHeight}`" class="chart">
                  <line
                    :x1="chartPadding.left"
                    :y1="chartHeight - chartPadding.bottom"
                    :x2="chartWidth - chartPadding.right"
                    :y2="chartHeight - chartPadding.bottom"
                    stroke="#e5e6eb"
                  />
                  <g v-for="bar in barLayout" :key="bar.label">
                    <rect
                      :x="bar.x"
                      :y="bar.y"
                      :width="bar.width"
                      :height="bar.height"
                      rx="4"
                      fill="#3b6ef6"
                    />
                    <text :x="bar.centerX" :y="bar.y - 6" text-anchor="middle" class="chart-value">
                      {{ bar.count }}
                    </text>
                    <text :x="bar.centerX" :y="bar.baseline + 18" text-anchor="middle" class="chart-label">
                      {{ bar.label }}
                    </text>
                  </g>
                </svg>
              </div>

              <div class="panel">
                <div class="panel-title">班级平均分对比</div>
                <div v-if="!classBarLayout.rows.length" class="panel-empty">该考试暂未关联班级</div>
                <svg v-else :viewBox="`0 0 620 ${classBarLayout.height}`" class="chart">
                  <g v-for="row in classBarLayout.rows" :key="row.className">
                    <text x="0" :y="row.y + 13" class="chart-label class-label">{{ row.className }}</text>
                    <rect
                      :x="row.labelWidth"
                      :y="row.y"
                      :width="row.maxWidth"
                      height="18"
                      rx="4"
                      fill="#f2f4f7"
                    />
                    <rect
                      :x="row.labelWidth"
                      :y="row.y"
                      :width="row.width"
                      height="18"
                      rx="4"
                      fill="#36b37e"
                    />
                    <text :x="row.labelWidth + row.width + 8" :y="row.y + 13" class="chart-value">
                      {{ row.avg === null || row.avg === undefined ? '—' : row.avg }}
                    </text>
                  </g>
                </svg>
              </div>
            </div>

            <div class="panel">
              <div class="panel-title">等级分布（自动评估）</div>
              <div class="level-tip">
                按班级内参与考试的学生排名、得分率（距满分）和超出及格线幅度综合评定；低于及格线为「待提高」
              </div>
              <div class="level-list">
                <div v-for="item in analysis.levels" :key="item.level" class="level-item">
                  <span class="level-name">{{ item.level }}</span>
                  <span class="level-count">{{ item.count }} 人</span>
                </div>
              </div>
            </div>

            <div class="panel">
              <div class="panel-title">班级对比明细</div>
              <a-table
                class="grade-table"
                :columns="classColumns"
                :data-source="analysis.classes"
                :pagination="false"
                row-key="classId"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <span v-if="column.dataIndex === 'avgScore'">{{ scoreText(record.avgScore) }}</span>
                  <span v-else-if="column.dataIndex === 'maxScore'">{{ scoreText(record.maxScore) }}</span>
                  <span v-else-if="column.dataIndex === 'minScore'">{{ scoreText(record.minScore) }}</span>
                  <span v-else-if="column.dataIndex === 'passRate'">{{ percentText(record.passRate) }}</span>
                  <span v-else-if="column.dataIndex === 'excellentRate'">
                    {{ percentText(record.excellentRate) }}
                  </span>
                </template>
                <template #emptyText>
                  <a-empty description="暂无班级数据" />
                </template>
              </a-table>
            </div>

            <div class="panel">
              <div class="panel-title">学员排名（前 50 名）</div>
              <a-table
                class="grade-table"
                :columns="rankColumns"
                :data-source="analysis.rankings"
                :pagination="false"
                row-key="studentId"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <span v-if="column.dataIndex === 'rank'" class="rank-no">{{ record.rank }}</span>
                  <span v-else-if="column.dataIndex === 'studentName'">{{ record.studentName ?? '—' }}</span>
                  <span v-else-if="column.dataIndex === 'className'">{{ record.className ?? '—' }}</span>
                  <span v-else-if="column.dataIndex === 'totalScore'" class="score-text">
                    {{ record.totalScore }}
                  </span>
                  <span v-else-if="column.dataIndex === 'level'">{{ record.level ?? '—' }}</span>
                </template>
                <template #emptyText>
                  <a-empty description="暂无有效成绩排名" />
                </template>
              </a-table>
            </div>
          </template>
        </a-spin>
      </a-tab-pane>

      <a-tab-pane key="class" tab="班级考试趋势">
        <div class="filter-line">
          <a-select
            v-model:value="classTrendId"
            size="small"
            style="width: 280px"
            placeholder="选择班级"
            show-search
            :options="classSelectOptions"
            @change="loadClassTrend"
          />
          <span v-if="classTrendExams.length" class="filter-label">
            共 {{ classTrendExams.length }} 场考试 · 已出分 {{ classSummary.count }} 场
          </span>
        </div>

        <a-spin :spinning="classTrendLoading">
          <div v-if="!classTrendId" class="empty-panel">
            <a-empty description="请选择班级查看历次考试对比" />
          </div>
          <div v-else-if="!classTrendExams.length" class="empty-panel">
            <a-empty description="该班级暂无考试记录" />
          </div>
          <template v-else>
            <div class="stat-grid">
              <div class="stat-card">
                <div class="stat-label">已出分场次</div>
                <div class="stat-value">
                  {{ classSummary.count }}<span class="stat-unit">/ {{ classTrendExams.length }} 场</span>
                </div>
              </div>
              <div class="stat-card">
                <div class="stat-label">平均得分率</div>
                <div class="stat-value">{{ rateText(classSummary.avgRate) }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">最近一次得分率</div>
                <div class="stat-value">{{ rateText(classSummary.latestRate) }}</div>
                <div class="stat-sub">{{ classSummary.latestDate ?? '—' }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">平均及格率</div>
                <div class="stat-value">{{ rateText(classSummary.avgPassRate) }}</div>
              </div>
            </div>

            <div class="panel">
              <div class="panel-title">历次考试得分率趋势</div>
              <div class="chart-legend">
                <span class="legend-item"><i class="legend-dot avg" />平均得分率</span>
                <span class="legend-item"><i class="legend-dot pass" />及格率</span>
              </div>
              <div v-if="!classSummary.count" class="panel-empty">该班级暂无已录入成绩的考试</div>
              <svg v-else :viewBox="`0 0 ${chartBox.width} ${chartBox.height}`" class="chart">
                <g v-for="grid in classChart.avg.grids" :key="grid.value">
                  <line
                    :x1="chartBox.left"
                    :x2="chartBox.width - chartBox.right"
                    :y1="grid.y"
                    :y2="grid.y"
                    stroke="#f0f1f2"
                  />
                  <text :x="chartBox.left - 8" :y="grid.y + 4" text-anchor="end" class="chart-label">
                    {{ grid.value }}
                  </text>
                </g>
                <path :d="classChart.avg.path" fill="none" stroke="#3b6ef6" stroke-width="2" />
                <path
                  :d="classChart.pass.path"
                  fill="none"
                  stroke="#36b37e"
                  stroke-width="2"
                  stroke-dasharray="6 4"
                />
                <g v-for="point in classChart.avg.points" :key="`avg-${point.x}`">
                  <circle :cx="point.x" :cy="point.y" r="4" fill="#fff" stroke="#3b6ef6" stroke-width="2">
                    <title>{{ point.tip }}</title>
                  </circle>
                </g>
                <g v-for="point in classChart.pass.points" :key="`pass-${point.x}`">
                  <circle :cx="point.x" :cy="point.y" r="3.5" fill="#fff" stroke="#36b37e" stroke-width="2">
                    <title>{{ point.tip }}</title>
                  </circle>
                </g>
                <text
                  v-for="label in classChartLabels"
                  :key="`label-${label.x}`"
                  :x="label.x"
                  :y="chartBox.height - chartBox.bottom + 20"
                  text-anchor="middle"
                  class="chart-label"
                >
                  {{ label.text }}
                </text>
              </svg>
            </div>

            <div class="panel">
              <div class="panel-title">历次考试明细</div>
              <a-table
                class="grade-table"
                :columns="classTrendColumns"
                :data-source="classTrendRows"
                :pagination="false"
                :scroll="{ x: 1094 }"
                row-key="examId"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <span v-if="column.dataIndex === 'enteredCount'">
                    {{ record.enteredCount }} / {{ record.studentCount }}
                  </span>
                  <span v-else-if="column.dataIndex === 'avgScore'">{{ scoreText(record.avgScore) }}</span>
                  <span v-else-if="column.dataIndex === 'rate'">{{ rateText(record.rate) }}</span>
                  <span v-else-if="column.dataIndex === 'range'">
                    {{ scoreText(record.maxScore) }} / {{ scoreText(record.minScore) }}
                  </span>
                  <span v-else-if="column.dataIndex === 'passRate'">{{ rateText(record.passRate) }}</span>
                  <span v-else-if="column.dataIndex === 'excellentRate'">
                    {{ rateText(record.excellentRate) }}
                  </span>
                  <span v-else-if="column.dataIndex === 'delta'" :class="deltaClass(record.delta)">
                    {{ deltaText(record.delta) }}
                  </span>
                </template>
                <template #emptyText>
                  <a-empty description="暂无考试数据" />
                </template>
              </a-table>
            </div>
          </template>
        </a-spin>
      </a-tab-pane>

      <a-tab-pane key="student" tab="学员成绩波动">
        <div class="filter-line">
          <a-select
            v-model:value="studentClassId"
            size="small"
            style="width: 240px"
            placeholder="选择班级"
            show-search
            :options="classSelectOptions"
            @change="loadStudentOptions"
          />
          <a-select
            v-model:value="studentId"
            size="small"
            style="width: 200px"
            placeholder="选择学员"
            show-search
            :options="studentOptions"
            @change="loadStudentScores"
          />
          <span v-if="studentSummary.count" class="filter-label">
            共 {{ studentSummary.count }} 场已发布考试
          </span>
        </div>

        <a-spin :spinning="studentLoading">
          <div v-if="!studentClassId" class="empty-panel">
            <a-empty description="请选择班级与学员查看成绩波动" />
          </div>
          <div v-else-if="!studentOptions.length" class="empty-panel">
            <a-empty description="该班级暂无在读学员" />
          </div>
          <div v-else-if="!studentScores.length" class="empty-panel">
            <a-empty description="该学员暂无已发布成绩" />
          </div>
          <template v-else>
            <div class="stat-grid">
              <div class="stat-card">
                <div class="stat-label">参考场次</div>
                <div class="stat-value">{{ studentSummary.count }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">平均得分率</div>
                <div class="stat-value">{{ rateText(studentSummary.avgRate) }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">最近成绩</div>
                <div class="stat-value small">
                  {{
                    studentSummary.latest
                      ? `${scoreText(studentSummary.latest.totalScore)} / ${studentSummary.latest.fullScore}`
                      : '—'
                  }}
                </div>
                <div class="stat-sub">{{ studentSummary.latest?.examDate ?? '—' }}</div>
              </div>
              <div class="stat-card">
                <div class="stat-label">较首场变化</div>
                <div class="stat-value" :class="deltaClass(studentSummary.change)">
                  {{ deltaText(studentSummary.change) }}
                </div>
                <div class="stat-sub">得分率百分点</div>
              </div>
            </div>

            <div class="panel">
              <div class="panel-title">成绩波动趋势</div>
              <div class="chart-legend">
                <span class="legend-item"><i class="legend-dot avg" />本人得分率</span>
                <span class="legend-item"><i class="legend-dot class" />班级平均得分率</span>
              </div>
              <svg :viewBox="`0 0 ${chartBox.width} ${chartBox.height}`" class="chart">
                <g v-for="grid in studentChart.score.grids" :key="grid.value">
                  <line
                    :x1="chartBox.left"
                    :x2="chartBox.width - chartBox.right"
                    :y1="grid.y"
                    :y2="grid.y"
                    stroke="#f0f1f2"
                  />
                  <text :x="chartBox.left - 8" :y="grid.y + 4" text-anchor="end" class="chart-label">
                    {{ grid.value }}
                  </text>
                </g>
                <path
                  :d="studentChart.classAvg.path"
                  fill="none"
                  stroke="#8f959e"
                  stroke-width="2"
                  stroke-dasharray="6 4"
                />
                <path :d="studentChart.score.path" fill="none" stroke="#3b6ef6" stroke-width="2" />
                <g v-for="point in studentChart.classAvg.points" :key="`class-${point.x}`">
                  <circle :cx="point.x" :cy="point.y" r="3.5" fill="#fff" stroke="#8f959e" stroke-width="2">
                    <title>{{ point.tip }}</title>
                  </circle>
                </g>
                <g v-for="point in studentChart.score.points" :key="`score-${point.x}`">
                  <circle :cx="point.x" :cy="point.y" r="4" fill="#fff" stroke="#3b6ef6" stroke-width="2">
                    <title>{{ point.tip }}</title>
                  </circle>
                </g>
                <text
                  v-for="label in studentChartLabels"
                  :key="`label-${label.x}`"
                  :x="label.x"
                  :y="chartBox.height - chartBox.bottom + 20"
                  text-anchor="middle"
                  class="chart-label"
                >
                  {{ label.text }}
                </text>
              </svg>
            </div>

            <div class="panel">
              <div class="panel-title">历次成绩明细</div>
              <a-table
                class="grade-table"
                :columns="studentColumns"
                :data-source="studentRows"
                :pagination="false"
                :scroll="{ x: 912 }"
                row-key="examId"
                size="small"
              >
                <template #bodyCell="{ column, record }">
                  <span v-if="column.dataIndex === 'totalScore'" class="score-text">
                    {{ scoreText(record.totalScore) }}
                  </span>
                  <span v-else-if="column.dataIndex === 'rate'">{{ rateText(record.rate) }}</span>
                  <span v-else-if="column.dataIndex === 'classAvgScore'">
                    {{ scoreText(record.classAvgScore) }}
                  </span>
                  <span v-else-if="column.dataIndex === 'rankClass'">{{ record.rankClass ?? '—' }}</span>
                  <span v-else-if="column.dataIndex === 'rankCampus'">{{ record.rankCampus ?? '—' }}</span>
                  <span v-else-if="column.dataIndex === 'level'">{{ record.level ?? '—' }}</span>
                </template>
                <template #emptyText>
                  <a-empty description="暂无成绩数据" />
                </template>
              </a-table>
            </div>
          </template>
        </a-spin>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<style scoped>
.grade-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 24px;
}

.analysis-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
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

.empty-panel {
  padding: 80px 0;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  padding: 14px 16px;
  background: #fafbfc;
  border: 1px solid #f0f1f2;
  border-radius: 6px;
}

.stat-label {
  color: var(--text-2);
  font-size: 12px;
}

.stat-value {
  margin-top: 6px;
  color: var(--text);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-value.small {
  font-size: 18px;
}

.stat-unit {
  margin-left: 4px;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 400;
}

.stat-sub {
  margin-top: 4px;
  color: var(--text-3);
  font-size: 12px;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.panel {
  padding: 14px 16px 16px;
  border: 1px solid #f0f1f2;
  border-radius: 6px;
  margin-bottom: 16px;
}

.panel-grid .panel {
  margin-bottom: 0;
}

.panel-title {
  margin-bottom: 10px;
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.panel-empty {
  padding: 30px 0;
  color: var(--text-2);
  font-size: 13px;
  text-align: center;
}

.chart {
  width: 100%;
  height: auto;
}

.chart-value {
  fill: #4e5969;
  font-size: 12px;
}

.chart-label {
  fill: #8f959e;
  font-size: 12px;
}

.class-label {
  font-size: 12px;
}

.chart-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 6px;
  color: var(--text-2);
  font-size: 12px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-dot.avg {
  background: #3b6ef6;
}

.legend-dot.pass {
  background: #36b37e;
}

.legend-dot.class {
  background: #8f959e;
}

.delta-up {
  color: #0e9f6e;
}

.delta-down {
  color: #d4380d;
}

.level-tip {
  margin-bottom: 10px;
  color: var(--text-3);
  font-size: 12px;
  line-height: 1.6;
}

.level-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.level-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: #fafbfc;
  border: 1px solid #f0f1f2;
  border-radius: 4px;
  font-size: 13px;
}

.level-name {
  color: var(--text);
  font-weight: 500;
}

.level-count {
  color: var(--brand);
}

.rank-no {
  display: inline-block;
  min-width: 22px;
  padding: 0 6px;
  background: #f2f4f7;
  border-radius: 10px;
  color: #4e5969;
  font-size: 12px;
}

.score-text {
  font-weight: 600;
  color: var(--brand);
}

.grade-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 9px 8px;
  white-space: nowrap;
}

.grade-table :deep(.ant-table-tbody > tr > td) {
  padding: 8px;
  font-size: 13px;
  white-space: nowrap;
}
</style>
