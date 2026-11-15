<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  createSchedule,
  fetchScheduleOptions,
  fetchTeachingLogRoster,
  fetchTeachingLogs,
  saveTeachingLog,
  type ScheduleOptions,
  type TeachingLogRow,
  type TeachingLogStudent,
} from '../api/client'
import Attendance from './Attendance.vue'
import FilterBar from '../components/FilterBar.vue'

const tab = ref<'log' | 'records'>('log')
const subTab = ref<'today' | 'makeup'>('today')

function todayText() {
  const pad = (n: number) => String(n).padStart(2, '0')
  const now = new Date()
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

const selectedDate = ref(todayText())
const campusId = ref<number | undefined>(undefined)
const room = ref<string | undefined>(undefined)
const lessonMode = ref<number | undefined>(undefined)
const courseId = ref<number | undefined>(undefined)
const teacher = ref<string | undefined>(undefined)
const recordStatus = ref<'未记录' | '已记录' | undefined>(undefined)
const keyword = ref('')

const rows = ref<TeachingLogRow[]>([])
const loading = ref(false)
const options = ref<ScheduleOptions>({ campuses: [], classes: [], courses: [], teachers: [], rooms: [] })
/** 扁平选项，按 label 搜索，避免 a-select-option 在 v4 下搜不到中文 */
const classSelectOptions = computed(() =>
  options.value.classes.map((item) => ({ value: item.id, label: item.name }))
)

const weekdayText = computed(() => {
  const date = new Date(`${selectedDate.value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return ''
  return ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][date.getDay()]
})
const dayNumber = computed(() => selectedDate.value.slice(8, 10))
const yearText = computed(() => selectedDate.value.replace(/-/g, '/'))

function timeText(row: TeachingLogRow) {
  const start = row.startTime ? String(row.startTime).slice(0, 5) : ''
  const end = row.endTime ? String(row.endTime).slice(0, 5) : ''
  return start && end ? `${start}-${end}` : start || '—'
}

function statusColor(row: TeachingLogRow) {
  return row.recordCount > 0 ? 'green' : 'red'
}

function statusText(row: TeachingLogRow) {
  return row.recordCount > 0 ? '已记录' : '未记录'
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchTeachingLogs({
      date: subTab.value === 'today' ? selectedDate.value : undefined,
      supplement: subTab.value === 'makeup',
      campusId: campusId.value,
      room: room.value,
      lessonMode: lessonMode.value,
      courseId: courseId.value,
      teacher: teacher.value,
      status: recordStatus.value,
      keyword: keyword.value.trim() || undefined,
    })
  } catch (e) {
    message.error((e as Error).message || '记上课列表加载失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  campusId.value = undefined
  room.value = undefined
  lessonMode.value = undefined
  courseId.value = undefined
  teacher.value = undefined
  recordStatus.value = undefined
  keyword.value = ''
  load()
}

function switchSubTab(key: 'today' | 'makeup') {
  subTab.value = key
  load()
}

function openOtherTab(key: 'records' | 'makeup' | 'summary' | 'trial') {
  if (key === 'records') {
    tab.value = 'records'
    return
  }
  message.info('该功能暂未迁移到本系统')
}

// ---------------------------------------------------------------------------
// 记上课
// ---------------------------------------------------------------------------
interface RosterEdit extends TeachingLogStudent {
  status: string
  lessons: number
  remark: string
}

const recordOpen = ref(false)
const recordRow = ref<TeachingLogRow | null>(null)
const rosterLoading = ref(false)
const saving = ref(false)
const roster = ref<RosterEdit[]>([])

const recordReadonly = computed(() => (recordRow.value?.recordCount ?? 0) > 0)
const presentCount = computed(() =>
  roster.value.filter((row) => row.status === '出勤' || row.status === '迟到').length,
)

async function openRecord(row: TeachingLogRow) {
  recordRow.value = row
  recordOpen.value = true
  rosterLoading.value = true
  roster.value = []
  try {
    const list = await fetchTeachingLogRoster(row.id)
    roster.value = list.map((item) => ({
      ...item,
      status: item.recordStatus || '出勤',
      lessons: Number(item.recordLessons ?? 1),
      remark: item.recordRemark ?? '',
    }))
  } catch (e) {
    message.error((e as Error).message || '学员名单加载失败')
  } finally {
    rosterLoading.value = false
  }
}

function onStatusChange(row: RosterEdit) {
  if (row.status === '请假' || row.status === '缺席') {
    row.lessons = 0
  } else if (!row.lessons) {
    row.lessons = 1
  }
}

function markAllPresent() {
  roster.value.forEach((row) => {
    row.status = '出勤'
    if (!row.lessons) row.lessons = 1
  })
}

async function submitRecord() {
  const row = recordRow.value
  if (!row) return
  if (!roster.value.length) {
    message.warning('当前班级没有在读学员')
    return
  }
  saving.value = true
  try {
    const res = await saveTeachingLog(
      row.id,
      roster.value.map((item) => ({
        studentId: item.studentId,
        enrollmentId: item.enrollmentId,
        status: item.status,
        lessons: item.status === '请假' || item.status === '缺席' ? 0 : item.lessons,
        remark: item.remark || null,
      })),
    )
    message.success(`记上课完成：${res.recorded} 名学员，实到 ${res.present} 人`)
    recordOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// A4 上课签到名单打印（单个 / 批量）
// ---------------------------------------------------------------------------
const ROSTER_PRINT_STYLE = `
  @page { size: A4 landscape; margin: 7mm; }
  * { box-sizing: border-box; }
  body { margin: 0; font-family: "Microsoft YaHei", "PingFang SC", SimSun, sans-serif; font-size: 10pt; color: #000; }
  .head { text-align: center; }
  .org { font-size: 11pt; letter-spacing: 2px; color: #333; }
  h1 { margin: 1mm 0 0; font-size: 19pt; letter-spacing: 7px; font-weight: 700; }
  .meta { display: flex; flex-wrap: wrap; gap: 2mm 7mm; margin: 2.5mm 0 1.5mm; font-size: 9pt; }
  .meta .item { white-space: nowrap; }
  .note { margin: 0 0 1.5mm; font-size: 8.5pt; color: #444; }
  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  thead { display: table-header-group; }
  tr { page-break-inside: avoid; }
  col.name-col { width: 23mm; }
  col.sign-col { width: 33.2mm; }
  th, td { border: 0.6pt solid #222; }
  th { height: 6mm; font-size: 8.5pt; font-weight: 600; text-align: center; }
  th.name-head { background: #eef2f6; }
  th.sign-head { background: #f7f8f9; }
  td { height: 6.2mm; padding: 0 1.5mm; vertical-align: middle; white-space: nowrap; overflow: hidden; }
  td.nm { font-size: 9.5pt; font-weight: 600; }
  td.nm .no { display: inline-block; width: 6mm; color: #777; font-size: 8pt; font-weight: 400; }
  td.sign { background: #fff; }
  tr.section td { height: 5mm; padding: 0 1.5mm; font-size: 8.5pt; font-weight: 600; background: #f4f6f8; }
  .confirm { margin-top: 4mm; font-size: 10pt; text-align: right; }
  .foot { margin-top: 2mm; font-size: 7.5pt; color: #666; text-align: right; }
  .sheet-break { break-before: page; page-break-before: always; }
`

const escapeHtml = (value: string | null | undefined) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

/** 生成单个班级的签到名单内容（不含 html/head，便于批量拼接多页）。 */
function buildRosterSheet(row: TeachingLogRow, students: TeachingLogStudent[]) {
  const names = students.map((item) => escapeHtml(item.studentName))
  // 固定 5 人 × 20 行 = 100 个学员位，每行 5 组「姓名 + 签名」，不足也保留空位
  const totalSlots = Math.max(100, Math.ceil(names.length / 5) * 5)
  const studentRows: string[] = []
  for (let i = 0; i < totalSlots; i += 5) {
    const tds = Array.from({ length: 5 }, (_, offset) => {
      const index = i + offset
      return `<td class="nm"><span class="no">${index + 1}</span>${names[index] ?? ''}</td><td class="sign"></td>`
    }).join('')
    studentRows.push(`<tr>${tds}</tr>`)
  }
  // 试听人员固定 10 名，同样保留姓名与签名位
  const trialRows =
    `<tr class="section"><td colspan="10">试听人员（现场填写姓名并签名）</td></tr>` +
    [0, 5]
      .map(
        (start) =>
          `<tr>${Array.from(
            { length: 5 },
            (_, offset) =>
              `<td class="nm"><span class="no">试听${start + offset + 1}</span></td><td class="sign"></td>`,
          ).join('')}</tr>`,
      )
      .join('')
  return `
  <div class="head">
    <div class="org">${escapeHtml(row.campusName) || '幻彩教育'}</div>
    <h1>上课签到名单</h1>
  </div>
  <div class="meta">
    <div class="item">课程：${escapeHtml(row.courseName) || '—'}</div>
    <div class="item">班级：${escapeHtml(row.className) || '—'}</div>
    <div class="item">教师：${escapeHtml(row.teacherName) || '—'}</div>
    <div class="item">时间：${escapeHtml(row.lessonDate)} ${escapeHtml(timeText(row))}</div>
    <div class="item">教室：${escapeHtml(row.room) || '—'}</div>
    <div class="item">应到：${students.length} 人　实到：______ 人</div>
  </div>
  <div class="note">说明：姓名栏由系统打印，签名栏留空，请学员现场手写签名。</div>
  <table>
    <colgroup>
      <col class="name-col" /><col class="sign-col" />
      <col class="name-col" /><col class="sign-col" />
      <col class="name-col" /><col class="sign-col" />
      <col class="name-col" /><col class="sign-col" />
      <col class="name-col" /><col class="sign-col" />
    </colgroup>
    <thead>
      <tr>
        <th class="name-head">姓名</th><th class="sign-head">签名</th>
        <th class="name-head">姓名</th><th class="sign-head">签名</th>
        <th class="name-head">姓名</th><th class="sign-head">签名</th>
        <th class="name-head">姓名</th><th class="sign-head">签名</th>
        <th class="name-head">姓名</th><th class="sign-head">签名</th>
      </tr>
    </thead>
    <tbody>
      ${studentRows.join('')}
      ${trialRows}
    </tbody>
  </table>
  <div class="confirm">助教确认签名：____________________　　日期：______ 年 ____ 月 ____ 日</div>
  <div class="foot">打印时间：${escapeHtml(new Date().toLocaleString('zh-CN'))}</div>`
}

function createPrintWindow(): Window | null {
  const win = window.open('', '_blank', 'width=1100,height=800')
  if (!win) {
    message.error('浏览器拦截了打印窗口，请允许弹出窗口后重试')
    return null
  }
  win.document.write('<p style="font-family:sans-serif;padding:20px">正在生成签到名单…</p>')
  return win
}

function renderPrintWindow(win: Window, title: string, sheets: string[]) {
  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<title>${escapeHtml(title)}</title>
<style>${ROSTER_PRINT_STYLE}</style>
</head>
<body>
${sheets.join('<div class="sheet-break"></div>')}
</body>
</html>`
  win.document.open()
  win.document.write(html)
  win.document.close()
  win.focus()
  setTimeout(() => win.print(), 300)
}

/** 打印当前班级的 A4 签到名单。 */
function printRoster() {
  const row = recordRow.value
  if (!row) return
  if (!roster.value.length) {
    message.warning('当前班级没有可打印的学员名单')
    return
  }
  const win = createPrintWindow()
  if (!win) return
  renderPrintWindow(win, `上课签到名单 ${row.className}`, [buildRosterSheet(row, roster.value)])
}

// ---------------------------------------------------------------------------
// 批量打印签到名单
// ---------------------------------------------------------------------------
const batchOpen = ref(false)
const batchSelectedKeys = ref<(string | number)[]>([])
const batchPrinting = ref(false)
const batchColumns = [
  { title: '上课时段', dataIndex: 'time', width: 120 },
  { title: '班级/一对一', dataIndex: 'className', width: 260, ellipsis: true },
  { title: '所属课程', dataIndex: 'courseName', width: 160, ellipsis: true },
  { title: '上课教师', dataIndex: 'teacherName', width: 100 },
  { title: '状态', dataIndex: 'status', width: 90 },
]

function openBatch() {
  if (!rows.value.length) {
    message.warning('当前没有可打印的课次')
    return
  }
  batchSelectedKeys.value = []
  batchOpen.value = true
}

function onBatchSelect(keys: (string | number)[]) {
  batchSelectedKeys.value = keys
}

async function printBatch() {
  const selectedIds = batchSelectedKeys.value.map(Number)
  const selected = rows.value.filter((row) => selectedIds.includes(row.id))
  if (!selected.length) {
    message.warning('请先勾选需要打印的班级')
    return
  }
  // 先同步打开窗口，避免异步获取名单后被浏览器拦截
  const win = createPrintWindow()
  if (!win) return
  batchPrinting.value = true
  try {
    const lists = await Promise.all(selected.map((row) => fetchTeachingLogRoster(row.id)))
    const sheets: string[] = []
    let skipped = 0
    lists.forEach((list, index) => {
      if (!list.length) {
        skipped += 1
        return
      }
      sheets.push(buildRosterSheet(selected[index], list))
    })
    if (!sheets.length) {
      win.close()
      message.warning('所选班级均没有在读学员')
      return
    }
    batchOpen.value = false
    renderPrintWindow(win, '上课签到名单', sheets)
    if (skipped) message.info(`已跳过 ${skipped} 个没有学员的班级`)
  } catch (e) {
    win.close()
    message.error((e as Error).message || '批量打印失败')
  } finally {
    batchPrinting.value = false
  }
}

// ---------------------------------------------------------------------------
// 未排课记上课
// ---------------------------------------------------------------------------
const quickOpen = ref(false)
const quickSaving = ref(false)
const timeSlots = Array.from({ length: 27 }, (_, i) => {
  const minutes = 8 * 60 + i * 30
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
})
const quickForm = ref({
  classId: undefined as number | undefined,
  lessonDate: todayText(),
  startTime: '08:00',
  duration: 2,
})

function endTimeOf(start: string, duration: number) {
  const [h, m] = start.split(':').map(Number)
  const total = h * 60 + m + duration * 60
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

async function openQuick() {
  // 班级可能在页面打开后才新建，打开弹窗时重新拉取选项
  options.value = await fetchScheduleOptions().catch(() => options.value)
  quickOpen.value = true
}

async function submitQuick() {
  const form = quickForm.value
  if (!form.classId) {
    message.warning('请选择班级')
    return
  }
  if (!form.lessonDate || !form.startTime) {
    message.warning('请选择上课日期与时间')
    return
  }
  const target = options.value.classes.find((item) => item.id === form.classId)
  quickSaving.value = true
  try {
    const res = await createSchedule({
      campusId: target?.campusId ?? undefined,
      classId: form.classId,
      courseId: target?.courseId ?? null,
      teacherId: target?.teacherId ?? null,
      lessonDate: form.lessonDate,
      startTime: form.startTime,
      endTime: endTimeOf(form.startTime, form.duration),
      room: target?.room ?? undefined,
      lessonMode: 1,
    })
    quickOpen.value = false
    await load()
    const created = rows.value.find((item) => item.id === res.id)
    if (created) {
      await openRecord(created)
    } else {
      message.success('课次已创建，请在列表中点「记上课」')
    }
  } catch (e) {
    message.error((e as Error).message || '创建课次失败')
  } finally {
    quickSaving.value = false
  }
}

const logColumns = [
  { title: '上课时段', dataIndex: 'time', width: 130 },
  { title: '班级/一对一', dataIndex: 'className', width: 300, ellipsis: true },
  { title: '所属课程', dataIndex: 'courseName', width: 180, ellipsis: true },
  { title: '上课教师', dataIndex: 'teacherName', width: 100 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '上课校区', dataIndex: 'campusName', width: 180, ellipsis: true },
  { title: '教室', dataIndex: 'room', width: 110 },
  { title: '操作', dataIndex: 'action', width: 84, fixed: 'right' },
]

const rosterColumns = [
  { title: '学员姓名', dataIndex: 'studentName', width: 120 },
  { title: '报读课程', dataIndex: 'courseName', width: 150, ellipsis: true },
  { title: '剩余课时', dataIndex: 'remainLessons', width: 96, align: 'right' },
  { title: '出勤状态', dataIndex: 'status', width: 110 },
  { title: '扣减课时', dataIndex: 'lessons', width: 110 },
  { title: '备注', dataIndex: 'remark' },
]

onMounted(async () => {
  options.value = await fetchScheduleOptions().catch(() => options.value)
  await load()
})
</script>

<template>
  <div class="page">
    <div class="module-tabs">
      <span class="module-tab" :class="{ active: tab === 'log' }" @click="tab = 'log'">记上课</span>
      <span class="module-tab" :class="{ active: tab === 'records' }" @click="openOtherTab('records')">上课记录</span>
      <span class="module-tab" @click="openOtherTab('makeup')">补课管理</span>
      <span class="module-tab" @click="openOtherTab('summary')">课时汇总</span>
      <span class="module-tab" @click="openOtherTab('trial')">教师试听</span>
    </div>

    <Attendance v-if="tab === 'records'" />

    <template v-else>
      <div class="day-bar">
        <div class="day-date">
          <div class="day-week">{{ weekdayText }}</div>
          <div class="day-main">
            <span class="day-number">{{ dayNumber }}</span>
            <span class="day-year">{{ yearText }}</span>
          </div>
        </div>
        <a-date-picker
          v-model:value="selectedDate"
          size="small"
          value-format="YYYY-MM-DD"
          :allow-clear="false"
          class="day-picker"
          @change="subTab === 'today' && load()"
        />
      </div>

      <div class="sub-tabs">
        <span class="sub-tab" :class="{ active: subTab === 'today' }" @click="switchSubTab('today')">今日上课</span>
        <span class="sub-tab" :class="{ active: subTab === 'makeup' }" @click="switchSubTab('makeup')">补记上课</span>
      </div>

      <FilterBar @search="load" @reset="reset">
        <a-select
          v-model:value="campusId"
          class="filter-select"
          placeholder="全部校区"
          allow-clear
          style="width: 150px"
          @change="load"
        >
          <a-select-option v-for="item in options.campuses" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="room"
          class="filter-select"
          placeholder="全部教室"
          allow-clear
          style="width: 130px"
          @change="load"
        >
          <a-select-option v-for="item in options.rooms" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="lessonMode"
          class="filter-select"
          placeholder="全部授课模式"
          allow-clear
          style="width: 130px"
          @change="load"
        >
          <a-select-option :value="1">班课</a-select-option>
          <a-select-option :value="2">一对一</a-select-option>
        </a-select>
        <a-select
          v-model:value="courseId"
          class="filter-select"
          placeholder="全部科目"
          allow-clear
          style="width: 140px"
          @change="load"
        >
          <a-select-option v-for="item in options.courses" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="teacher"
          class="filter-select"
          placeholder="全部教师"
          allow-clear
          style="width: 130px"
          @change="load"
        >
          <a-select-option v-for="item in options.teachers" :key="item.id" :value="item.name">{{ item.name }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="recordStatus"
          class="filter-select"
          placeholder="全部记上课状态"
          allow-clear
          style="width: 150px"
          @change="load"
        >
          <a-select-option value="未记录">未记录</a-select-option>
          <a-select-option value="已记录">已记录</a-select-option>
        </a-select>
        <template #search>
          <a-input
            v-model:value="keyword"
            class="filter-search"
            placeholder="请输入班级/一对一名称"
            allow-clear
            @press-enter="load"
          >
            <template #prefix><SearchOutlined class="search-icon" /></template>
          </a-input>
        </template>
      </FilterBar>

      <div class="action-bar">
        <a-button type="primary" @click="openQuick">未排课记上课</a-button>
        <a-button @click="openBatch">批量打印签到表</a-button>
        <span class="filter-spacer" />
        <a-button class="ghost-btn">自定义列表</a-button>
      </div>

      <div class="result-line">共 {{ rows.length }} 条记录</div>

      <a-table
        :columns="logColumns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
        size="small"
        :scroll="{ x: 1180 }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'time'">{{ timeText(record) }}</span>
          <span v-else-if="column.dataIndex === 'className'" class="class-cell">
            <a-tag :color="record.lessonMode === 2 ? 'purple' : 'blue'">{{ record.lessonMode === 2 ? '一对一' : '班' }}</a-tag>
            <span class="class-name">{{ record.className || '—' }}</span>
          </span>
          <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'status'">
            <a-tag :color="statusColor(record)">{{ statusText(record) }}</a-tag>
          </span>
          <span v-else-if="column.dataIndex === 'campusName'">{{ record.campusName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'room'">{{ record.room || '—' }}</span>
          <a
            v-else-if="column.dataIndex === 'action'"
            class="op-link"
            @click="openRecord(record)"
          >{{ record.recordCount > 0 ? '查看' : '记上课' }}</a>
        </template>
        <template #emptyText>
          <a-empty :description="subTab === 'today' ? '当天暂无课次' : '暂无需要补记的课次'" />
        </template>
      </a-table>
      <div class="table-foot">没有更多信息</div>
    </template>

    <a-modal
      v-model:open="recordOpen"
      :title="`记上课 · ${recordRow?.className || ''}`"
      width="900px"
      :confirm-loading="saving"
      :ok-text="recordReadonly ? '关闭' : '保存'"
      :cancel-button-props="{ style: recordReadonly ? 'display:none' : '' }"
      @ok="recordReadonly ? (recordOpen = false) : submitRecord()"
    >
      <div class="record-summary">
        <span>{{ recordRow?.lessonDate }} {{ recordRow ? timeText(recordRow) : '' }}</span>
        <span class="summary-sep">|</span>
        <span>应到 {{ roster.length }} 人</span>
        <span class="summary-sep">|</span>
        <span>实到 {{ presentCount }} 人</span>
        <span class="filter-spacer" />
        <a-button size="small" @click="printRoster">打印签到表</a-button>
        <a-button v-if="!recordReadonly" size="small" @click="markAllPresent">全部到课</a-button>
      </div>
      <a-table
        :columns="rosterColumns"
        :data-source="roster"
        :loading="rosterLoading"
        :pagination="false"
        row-key="studentId"
        size="small"
        :scroll="{ y: 420 }"
      >
        <template #bodyCell="{ column, record }">
          <router-link
            v-if="column.dataIndex === 'studentName'"
            class="student-name"
            :to="`/studentDetail/${record.studentId}`"
          >{{ record.studentName }}</router-link>
          <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'remainLessons'">{{ record.remainLessons == null ? '—' : Number(record.remainLessons) }}</span>
          <span v-else-if="column.dataIndex === 'status'">
            <a-select
              v-if="!recordReadonly"
              v-model:value="record.status"
              size="small"
              style="width: 92px"
              @change="onStatusChange(record)"
            >
              <a-select-option value="出勤">到课</a-select-option>
              <a-select-option value="请假">请假</a-select-option>
              <a-select-option value="缺席">旷课</a-select-option>
              <a-select-option value="迟到">迟到</a-select-option>
            </a-select>
            <a-tag v-else>{{ record.recordStatus || '—' }}</a-tag>
          </span>
          <span v-else-if="column.dataIndex === 'lessons'">
            <a-input-number
              v-if="!recordReadonly"
              v-model:value="record.lessons"
              size="small"
              :min="0"
              :step="0.5"
              :disabled="record.status === '请假' || record.status === '缺席'"
              style="width: 88px"
            />
            <span v-else>{{ record.recordLessons == null ? '—' : Number(record.recordLessons) }}</span>
          </span>
          <span v-else-if="column.dataIndex === 'remark'">
            <a-input v-if="!recordReadonly" v-model:value="record.remark" size="small" placeholder="备注" />
            <span v-else>{{ record.recordRemark || '—' }}</span>
          </span>
        </template>
        <template #emptyText>
          <a-empty description="班级暂无在读学员" />
        </template>
      </a-table>
    </a-modal>

    <a-modal
      v-model:open="batchOpen"
      title="批量打印签到表"
      width="760px"
      :confirm-loading="batchPrinting"
      ok-text="打印所选签到表"
      :ok-button-props="{ disabled: !batchSelectedKeys.length }"
      @ok="printBatch"
    >
      <div class="batch-tip">勾选需要打印的班级，每个班级生成一页 A4 横向签到表，所选班级合并为一次打印。</div>
      <a-table
        :columns="batchColumns"
        :data-source="rows"
        :pagination="false"
        row-key="id"
        size="small"
        :row-selection="{ selectedRowKeys: batchSelectedKeys, onChange: onBatchSelect }"
        :scroll="{ y: 360 }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'time'">{{ timeText(record) }}</span>
          <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
          <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'status'">
            <a-tag :color="statusColor(record)">{{ statusText(record) }}</a-tag>
          </span>
        </template>
        <template #emptyText>
          <a-empty description="当天暂无课次" />
        </template>
      </a-table>
    </a-modal>

    <a-modal
      v-model:open="quickOpen"
      title="未排课记上课"
      width="520px"
      :confirm-loading="quickSaving"
      ok-text="创建并记上课"
      @ok="submitQuick"
    >
      <a-form layout="vertical" class="quick-form">
        <a-form-item label="班级/一对一">
          <a-select
            v-model:value="quickForm.classId"
            placeholder="请选择班级"
            show-search
            :options="classSelectOptions"
            option-filter-prop="label"
          />
        </a-form-item>
        <a-form-item label="上课日期">
          <a-date-picker v-model:value="quickForm.lessonDate" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
        <a-form-item label="上课时间">
          <div class="quick-time">
            <a-select v-model:value="quickForm.startTime" style="width: 110px">
              <a-select-option v-for="slot in timeSlots" :key="slot" :value="slot">{{ slot }}</a-select-option>
            </a-select>
            <span class="range-sep">至</span>
            <span class="quick-end">{{ endTimeOf(quickForm.startTime, quickForm.duration) }}</span>
            <a-select v-model:value="quickForm.duration" style="width: 100px">
              <a-select-option :value="1">1 小时</a-select-option>
              <a-select-option :value="1.5">1.5 小时</a-select-option>
              <a-select-option :value="2">2 小时</a-select-option>
            </a-select>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page {
  padding: 0 16px 24px;
}

.module-tabs {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 4px 4px 0;
  border-bottom: 1px solid #f0f0f0;
}

.module-tab {
  position: relative;
  padding: 10px 0 12px;
  font-size: 14px;
  color: #595959;
  cursor: pointer;
}

.module-tab.active {
  color: #1677ff;
  font-weight: 500;
}

.module-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: #1677ff;
}

.day-bar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  padding: 18px 4px 10px;
}

.day-week {
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 2px;
}

.day-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.day-number {
  font-size: 30px;
  font-weight: 600;
  line-height: 1;
  color: #262626;
}

.day-year {
  font-size: 13px;
  color: #8c8c8c;
}

.day-picker {
  margin-bottom: 4px;
}

.sub-tabs {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 140px;
  padding: 6px 0 4px;
}

.sub-tab {
  position: relative;
  padding: 8px 6px 12px;
  font-size: 14px;
  color: #595959;
  cursor: pointer;
}

.sub-tab.active {
  color: #1677ff;
  font-weight: 500;
}

.sub-tab.active::after {
  content: '';
  position: absolute;
  left: 12%;
  right: 12%;
  bottom: 2px;
  height: 2px;
  background: #1677ff;
}

.filter-spacer {
  flex: 1;
}

.search-icon {
  color: #8c8c8c;
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0 10px;
}

.ghost-btn {
  color: #595959;
}

.result-line {
  padding: 2px 0 8px;
  font-size: 13px;
  color: #595959;
}

.batch-tip {
  margin-bottom: 10px;
  font-size: 12px;
  color: #8c8c8c;
  line-height: 1.6;
}

.class-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.class-name {
  color: #262626;
}

.op-link {
  color: #1677ff;
}

.student-name {
  color: #1677ff;
  text-decoration: none;
  cursor: pointer;
}

.student-name:hover {
  text-decoration: underline;
}

.table-foot {
  padding: 10px 0;
  text-align: center;
  font-size: 12px;
  color: #bfbfbf;
}

.record-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0 10px;
  font-size: 13px;
  color: #595959;
}

.summary-sep {
  color: #d9d9d9;
}

.range-sep {
  color: #8c8c8c;
}

.quick-time {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quick-end {
  font-size: 13px;
  color: #595959;
}

.quick-form {
  padding-top: 8px;
}
</style>
