<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  LeftOutlined,
  NotificationOutlined,
  ReloadOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import {
  batchSuspend,
  fetchClasses,
  fetchSchedules,
  fetchScheduleRooms,
  fetchScheduleTeachers,
  fetchSuspends,
  notifySuspends,
  restoreSuspend,
  type ScheduleRow,
  type SuspendRow,
} from '../api/client'

interface OptionItem {
  label: string
  value: number
}

const WEEK_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const activeTab = ref<'handle' | 'records'>('handle')

const classOptions = ref<OptionItem[]>([])
const teachers = ref<string[]>([])
const rooms = ref<string[]>([])

const handleFilter = reactive<{
  classId: number | undefined
  teacher: string | undefined
  room: string | undefined
}>({
  classId: undefined,
  teacher: undefined,
  room: undefined,
})

const weekStart = ref(startOfWeek(new Date()))
const lessons = ref<ScheduleRow[]>([])
const lessonLoading = ref(false)

const suspendOpen = ref(false)
const suspendTarget = ref<ScheduleRow | null>(null)
const suspendReason = ref('')
const suspendNotify = ref(true)
const submitting = ref(false)

const notifyOpen = ref(false)
const notifyLoading = ref(false)
const notifyRecords = ref<SuspendRow[]>([])
const notifySelected = ref<number[]>([])
const notifying = ref(false)

const recordFilter = reactive<{
  range: string[] | null
  classId: number | undefined
  status: string | undefined
}>({
  range: [dateBefore(30), today()],
  classId: undefined,
  status: undefined,
})
const records = ref<SuspendRow[]>([])
const recordLoading = ref(false)
const recordTotal = ref(0)
const recordPage = ref(1)

const recordColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '上课时间', dataIndex: 'timeText', width: 120 },
  { title: '班级', dataIndex: 'className', width: 160 },
  { title: '课程', dataIndex: 'courseName', width: 130 },
  { title: '休课原因', dataIndex: 'reason', width: 220 },
  { title: '通知家长', dataIndex: 'notifyFlag', width: 90 },
  { title: '操作人', dataIndex: 'operatorName', width: 90 },
  { title: '休课时间', dataIndex: 'suspendedAt', width: 150 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', dataIndex: 'action', width: 90 },
]

const notifyColumns = [
  { title: '班级', dataIndex: 'className', width: 190 },
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '上课时间', dataIndex: 'timeText', width: 120 },
  { title: '休课原因', dataIndex: 'reason' },
  { title: '通知状态', dataIndex: 'notifyFlag', width: 100 },
]

const weekDays = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const date = addDays(weekStart.value, index)
    return {
      date: formatDate(date),
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      week: WEEK_LABELS[index],
      isToday: formatDate(date) === today(),
    }
  }),
)

const rangeLabel = computed(() => `${weekDays.value[0].date} ~ ${weekDays.value[6].date}`)

const slots = computed(() => {
  const values = Array.from(
    new Set(lessons.value.map((item) => String(item.startTime).slice(0, 5))),
  )
  values.sort()
  return values
})

const cellMap = computed(() => {
  const map = new Map<string, ScheduleRow[]>()
  for (const lesson of lessons.value) {
    const key = `${lesson.lessonDate}|${String(lesson.startTime).slice(0, 5)}`
    const list = map.get(key) ?? []
    list.push(lesson)
    map.set(key, list)
  }
  return map
})

const pendingCount = computed(() => lessons.value.filter((item) => item.status === '待上课').length)
const suspendedCount = computed(
  () => lessons.value.filter((item) => item.status === '休课' || item.status === '已取消').length,
)

const notifyClassCount = computed(
  () => new Set(notifyRecords.value.filter((item) => notifySelected.value.includes(item.id)).map((item) => item.classId)).size,
)

function today() {
  return formatDate(new Date())
}

function dateBefore(days: number) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return formatDate(date)
}

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function startOfWeek(date: Date) {
  const result = new Date(date)
  const day = result.getDay()
  result.setDate(result.getDate() + (day === 0 ? -6 : 1 - day))
  return result
}

function addDays(date: Date, days: number) {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

function formatTime(value: string | null) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function timeRange(record: { startTime: string | null; endTime: string | null }) {
  const start = record.startTime ? String(record.startTime).slice(0, 5) : ''
  const end = record.endTime ? String(record.endTime).slice(0, 5) : ''
  return start || end ? `${start}-${end}` : '—'
}

function filterOption(input: string, option: { label?: string }) {
  return String(option.label ?? '').toLowerCase().includes(input.toLowerCase())
}

function cellLessons(date: string, slot: string) {
  return cellMap.value.get(`${date}|${slot}`) ?? []
}

function statusLabel(status: string) {
  if (status === '已取消') return '已停课'
  if (status === '休课') return '已休课'
  return status
}

function statusColor(status: string) {
  if (status === '已上课') return 'green'
  if (status === '休课') return 'orange'
  if (status === '已取消') return 'red'
  return 'blue'
}

function lessonClass(status: string) {
  if (status === '已上课') return 'is-done'
  if (status === '休课' || status === '已取消') return 'is-suspended'
  return 'is-pending'
}

async function loadOptions() {
  try {
    const [classRes, teacherRes, roomRes] = await Promise.all([
      fetchClasses({ page: 1, size: 500 }),
      fetchScheduleTeachers(),
      fetchScheduleRooms(),
    ])
    classOptions.value = classRes.records.map((item) => ({ label: item.name, value: item.id }))
    teachers.value = teacherRes
    rooms.value = roomRes
  } catch {
    // 选项加载失败时保留空列表，不影响课表查询
  }
}

async function loadWeek() {
  lessonLoading.value = true
  try {
    const res = await fetchSchedules({
      start: weekDays.value[0].date,
      end: weekDays.value[6].date,
      classId: handleFilter.classId,
      teacher: handleFilter.teacher,
      room: handleFilter.room,
      size: 1000,
    })
    lessons.value = res.records
  } catch (e) {
    lessons.value = []
    message.error(e instanceof Error ? e.message : '加载课表失败')
  } finally {
    lessonLoading.value = false
  }
}

function prevWeek() {
  weekStart.value = addDays(weekStart.value, -7)
  loadWeek()
}

function nextWeek() {
  weekStart.value = addDays(weekStart.value, 7)
  loadWeek()
}

function thisWeek() {
  weekStart.value = startOfWeek(new Date())
  loadWeek()
}

function onLessonClick(lesson: ScheduleRow) {
  if (lesson.status === '待上课') {
    suspendTarget.value = lesson
    suspendReason.value = ''
    suspendNotify.value = true
    suspendOpen.value = true
    return
  }
  if (lesson.status === '休课' || lesson.status === '已取消') {
    message.info('该课次已休课，可在「休课记录」中查看或恢复')
    return
  }
  message.info('该课次已上课，无法休课')
}

async function submitSuspend() {
  if (!suspendTarget.value) return
  if (!suspendReason.value.trim()) {
    message.warning('请填写休课原因')
    return
  }
  submitting.value = true
  try {
    const result = await batchSuspend({
      scheduleIds: [suspendTarget.value.id],
      reason: suspendReason.value.trim(),
      notify: suspendNotify.value,
    })
    const notifyText = result.messageId
      ? `，已推送休课通知，触达家长 ${result.receiverCount} 人`
      : '，未推送通知'
    message.success(`休课成功${notifyText}`)
    if (result.notifyMessage) {
      message.warning(result.notifyMessage)
    }
    suspendOpen.value = false
    suspendTarget.value = null
    await loadWeek()
    await loadRecords()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '休课失败')
  } finally {
    submitting.value = false
  }
}

async function openNotify() {
  notifyOpen.value = true
  notifySelected.value = []
  await loadNotifyRecords()
}

async function loadNotifyRecords() {
  notifyLoading.value = true
  try {
    const res = await fetchSuspends({
      start: weekDays.value[0].date,
      end: weekDays.value[6].date,
      status: '已休课',
      page: 1,
      size: 200,
    })
    notifyRecords.value = res.records.filter((item) => item.status === '已休课')
  } catch (e) {
    notifyRecords.value = []
    message.error(e instanceof Error ? e.message : '加载已休课班级失败')
  } finally {
    notifyLoading.value = false
  }
}

async function submitNotify() {
  if (!notifySelected.value.length) {
    message.warning('请先勾选需要通知的已休课课次')
    return
  }
  notifying.value = true
  try {
    const result = await notifySuspends({ ids: notifySelected.value })
    message.success(
      `休课通知已发送，涉及 ${result.classCount} 个班级，触达家长 ${result.receiverCount} 人`,
    )
    if (result.notifyMessage) {
      message.warning(result.notifyMessage)
    }
    notifySelected.value = []
    notifyOpen.value = false
    await loadRecords()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '发送通知失败')
  } finally {
    notifying.value = false
  }
}

async function loadRecords() {
  recordLoading.value = true
  try {
    const res = await fetchSuspends({
      start: recordFilter.range?.[0],
      end: recordFilter.range?.[1],
      classId: recordFilter.classId,
      status: recordFilter.status,
      page: recordPage.value,
      size: 20,
    })
    records.value = res.records
    recordTotal.value = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载休课记录失败')
  } finally {
    recordLoading.value = false
  }
}

async function handleRestore(record: SuspendRow) {
  try {
    await restoreSuspend(record.id)
    message.success('已恢复为待上课，课时未受影响')
    await loadRecords()
    await loadWeek()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '恢复失败')
  }
}

function searchRecords() {
  recordPage.value = 1
  loadRecords()
}

function resetRecords() {
  recordFilter.range = [dateBefore(30), today()]
  recordFilter.classId = undefined
  recordFilter.status = undefined
  recordPage.value = 1
  loadRecords()
}

function onTabChange(key: string | number) {
  if (key === 'records') {
    loadRecords()
  }
}

onMounted(() => {
  loadOptions()
  loadWeek()
  loadRecords()
})
</script>

<template>
  <div class="suspend-page">
    <a-tabs v-model:activeKey="activeTab" class="suspend-tabs" @change="onTabChange">
      <a-tab-pane key="handle" tab="休课办理">
        <div class="filter-line">
          <div class="week-nav">
            <a-button size="small" type="text" title="上一周" @click="prevWeek">
              <LeftOutlined />
            </a-button>
            <span class="week-range">{{ rangeLabel }}</span>
            <a-button size="small" type="text" title="下一周" @click="nextWeek">
              <RightOutlined />
            </a-button>
            <a-button size="small" @click="thisWeek">本周</a-button>
          </div>
          <a-select
            v-model:value="handleFilter.classId"
            size="small"
            show-search
            allow-clear
            style="width: 200px"
            placeholder="全部班级"
            :options="classOptions"
            :filter-option="filterOption"
            @change="loadWeek"
          />
          <a-select
            v-model:value="handleFilter.teacher"
            size="small"
            allow-clear
            style="width: 140px"
            placeholder="全部教师"
            :options="teachers.map((item) => ({ label: item, value: item }))"
            @change="loadWeek"
          />
          <a-select
            v-model:value="handleFilter.room"
            size="small"
            allow-clear
            style="width: 140px"
            placeholder="全部教室"
            :options="rooms.map((item) => ({ label: item, value: item }))"
            @change="loadWeek"
          />
          <a-button size="small" @click="loadWeek">
            <ReloadOutlined />刷新
          </a-button>
          <a-button type="primary" size="small" class="notify-btn" @click="openNotify">
            <NotificationOutlined />通知
          </a-button>
        </div>

        <div class="summary-line">
          本周共 {{ lessons.length }} 节课次，待上课 {{ pendingCount }} 节，已休课
          {{ suspendedCount }} 节；点击课表中的待上课课次即可办理休课，休课不扣减学员课时。
        </div>

        <a-spin :spinning="lessonLoading">
          <div v-if="slots.length" class="week-grid">
            <div class="grid-row grid-head">
              <div class="slot-cell slot-head">时间</div>
              <div
                v-for="day in weekDays"
                :key="day.date"
                class="day-head"
                :class="{ 'is-today': day.isToday }"
              >
                <span class="day-week">{{ day.week }}</span>
                <span class="day-date">{{ day.label }}</span>
              </div>
            </div>
            <div v-for="slot in slots" :key="slot" class="grid-row">
              <div class="slot-cell">{{ slot }}</div>
              <div v-for="day in weekDays" :key="`${day.date}-${slot}`" class="lesson-cell">
                <div
                  v-for="lesson in cellLessons(day.date, slot)"
                  :key="lesson.id"
                  class="lesson-card"
                  :class="lessonClass(lesson.status)"
                  @click="onLessonClick(lesson)"
                >
                  <div class="lesson-title">{{ lesson.className || lesson.courseName || '班级课程' }}</div>
                  <div class="lesson-sub">
                    {{ lesson.teacherName || '未指定教师' }} · {{ lesson.room || '待定教室' }}
                  </div>
                  <a-tag :color="statusColor(lesson.status)" class="lesson-tag">
                    {{ statusLabel(lesson.status) }}
                  </a-tag>
                </div>
                <div v-if="!cellLessons(day.date, slot).length" class="cell-empty">—</div>
              </div>
            </div>
          </div>
          <a-empty v-else-if="!lessonLoading" description="本周暂无课次" />
        </a-spin>
      </a-tab-pane>

      <a-tab-pane key="records" tab="休课记录">
        <div class="filter-line">
          <span class="filter-label">上课日期</span>
          <a-range-picker
            v-model:value="recordFilter.range"
            value-format="YYYY-MM-DD"
            size="small"
            style="width: 220px"
            @change="searchRecords"
          />
          <a-select
            v-model:value="recordFilter.classId"
            size="small"
            show-search
            allow-clear
            style="width: 200px"
            placeholder="全部班级"
            :options="classOptions"
            :filter-option="filterOption"
            @change="searchRecords"
          />
          <a-select
            v-model:value="recordFilter.status"
            size="small"
            allow-clear
            style="width: 120px"
            placeholder="全部状态"
            :options="[
              { label: '已休课', value: '已休课' },
              { label: '已恢复', value: '已恢复' },
            ]"
            @change="searchRecords"
          />
          <a class="reset-link" @click="resetRecords">清空筛选</a>
          <a-button size="small" class="refresh-btn" @click="searchRecords">
            <ReloadOutlined />刷新
          </a-button>
        </div>

        <div class="summary-line">当前结果：共 {{ recordTotal }} 条休课记录</div>

        <a-table
          class="suspend-table"
          :columns="recordColumns"
          :data-source="records"
          :loading="recordLoading"
          :pagination="{
            current: recordPage,
            pageSize: 20,
            total: recordTotal,
            showSizeChanger: false,
            showTotal: (value: number) => `共 ${value} 条`,
            onChange: (value: number) => {
              recordPage = value
              loadRecords()
            },
          }"
          row-key="id"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'lessonDate'">{{ record.lessonDate }}</span>
            <span v-else-if="column.dataIndex === 'timeText'">{{ timeRange(record) }}</span>
            <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
            <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'reason'" class="ellipsis" :title="record.reason">
              {{ record.reason }}
            </span>
            <span v-else-if="column.dataIndex === 'notifyFlag'">
              <a-tag :color="record.notifyFlag ? 'blue' : 'default'" style="margin: 0">
                {{ record.notifyFlag ? '已推送' : '未推送' }}
              </a-tag>
            </span>
            <span v-else-if="column.dataIndex === 'operatorName'">{{ record.operatorName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'suspendedAt'">{{ formatTime(record.suspendedAt) }}</span>
            <span v-else-if="column.dataIndex === 'status'">
              <a-tag :color="record.status === '已休课' ? 'orange' : 'green'" style="margin: 0">
                {{ record.status }}
              </a-tag>
            </span>
            <span v-else-if="column.dataIndex === 'action'">
              <a-popconfirm
                v-if="record.status === '已休课'"
                title="恢复后该课次回到待上课状态，课时不做任何变动，确定恢复？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleRestore(record)"
              >
                <a-button type="link" size="small" class="row-btn">恢复</a-button>
              </a-popconfirm>
              <span v-else class="muted">—</span>
            </span>
          </template>
          <template #emptyText>
            <a-empty description="所选条件下暂无休课记录" />
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="suspendOpen"
      title="课次休课"
      width="520px"
      :confirm-loading="submitting"
      ok-text="确认休课"
      cancel-text="取消"
      @ok="submitSuspend"
    >
      <div v-if="suspendTarget" class="suspend-detail">
        <div class="detail-row">
          <span class="detail-label">班级</span>
          <span>{{ suspendTarget.className || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">课程</span>
          <span>{{ suspendTarget.courseName || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">教师</span>
          <span>{{ suspendTarget.teacherName || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">上课时间</span>
          <span>{{ suspendTarget.lessonDate }} {{ timeRange(suspendTarget) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">教室</span>
          <span>{{ suspendTarget.room || '—' }}</span>
        </div>
      </div>
      <a-textarea
        v-model:value="suspendReason"
        class="reason-input"
        :rows="3"
        :maxlength="255"
        show-count
        placeholder="请填写休课原因，例如：因场地检修，本周课程暂停一次"
      />
      <div class="notify-switch">
        <span>同时推送休课通知到家长端小程序</span>
        <a-switch v-model:checked="suspendNotify" size="small" />
      </div>
    </a-modal>

    <a-modal
      v-model:open="notifyOpen"
      title="发送休课通知"
      width="760px"
      :confirm-loading="notifying"
    >
      <div class="notify-tip">
        选择本周已休课的课次，系统将按班级向家长端小程序推送休课通知，可选择多个班级一起发送。
      </div>
      <a-table
        class="suspend-table"
        :columns="notifyColumns"
        :data-source="notifyRecords"
        :loading="notifyLoading"
        :pagination="false"
        :row-selection="{
          selectedRowKeys: notifySelected,
          onChange: (keys: (string | number)[]) => (notifySelected = keys as number[]),
        }"
        row-key="id"
        size="small"
        :scroll="{ y: 320 }"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
          <span v-else-if="column.dataIndex === 'lessonDate'">{{ record.lessonDate }}</span>
          <span v-else-if="column.dataIndex === 'timeText'">{{ timeRange(record) }}</span>
          <span v-else-if="column.dataIndex === 'reason'" class="ellipsis" :title="record.reason">
            {{ record.reason }}
          </span>
          <span v-else-if="column.dataIndex === 'notifyFlag'">
            <a-tag :color="record.notifyFlag ? 'blue' : 'default'" style="margin: 0">
              {{ record.notifyFlag ? '已推送' : '未推送' }}
            </a-tag>
          </span>
        </template>
        <template #emptyText>
          <a-empty description="本周暂无已休课课次" />
        </template>
      </a-table>
      <template #footer>
        <div class="notify-footer">
          <span class="notify-count">
            已选 {{ notifySelected.length }} 节 / 涉及 {{ notifyClassCount }} 个班级
          </span>
          <a-button @click="notifyOpen = false">取消</a-button>
          <a-button
            type="primary"
            :loading="notifying"
            :disabled="!notifySelected.length"
            @click="submitNotify"
          >
            发送通知
          </a-button>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.suspend-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.suspend-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 4px;
}

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 0;
  font-size: 13px;
}

.filter-label {
  color: var(--text-2);
}

.week-nav {
  display: flex;
  align-items: center;
  gap: 2px;
}

.week-range {
  min-width: 178px;
  text-align: center;
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
}

.notify-btn {
  margin-left: auto;
}

.reset-link {
  color: var(--brand);
  font-size: 13px;
}

.refresh-btn {
  margin-left: auto;
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.week-grid {
  border: 1px solid #eef0f3;
  border-radius: 6px;
  overflow: hidden;
}

.grid-row {
  display: grid;
  grid-template-columns: 72px repeat(7, minmax(0, 1fr));
  border-top: 1px solid #f0f1f2;
}

.grid-head {
  border-top: 0;
  background: #fafbfc;
}

.slot-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 4px;
  font-size: 12px;
  color: var(--text-2);
  border-right: 1px solid #f0f1f2;
}

.slot-head {
  font-weight: 500;
  color: var(--text);
}

.day-head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 4px;
  font-size: 12px;
  border-left: 1px solid #f0f1f2;
}

.day-head:first-of-type {
  border-left: 0;
}

.day-head.is-today {
  background: #eef5ff;
}

.day-week {
  font-weight: 500;
  color: var(--text);
}

.day-date {
  color: var(--text-2);
}

.lesson-cell {
  min-height: 92px;
  padding: 4px;
  border-left: 1px solid #f5f6f7;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.lesson-card {
  padding: 5px 7px;
  border-radius: 5px;
  border-left: 3px solid #d9dde3;
  background: #f7f9fb;
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}

.lesson-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.lesson-card.is-pending {
  border-left-color: #1677ff;
  background: #f2f7ff;
}

.lesson-card.is-done {
  border-left-color: #52c41a;
  background: #f5fbf2;
  cursor: default;
}

.lesson-card.is-suspended {
  border-left-color: #fa8c16;
  background: #fff8ef;
  cursor: default;
}

.lesson-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lesson-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lesson-tag {
  margin: 3px 0 0;
  font-size: 11px;
  line-height: 16px;
  padding: 0 5px;
}

.cell-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #d3d7dd;
}

.suspend-detail {
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: 6px;
  font-size: 13px;
}

.detail-row {
  display: flex;
  gap: 10px;
  line-height: 24px;
}

.detail-label {
  width: 64px;
  color: var(--text-2);
  flex: none;
}

.reason-input {
  margin-top: 12px;
}

.notify-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 13px;
}

.notify-tip {
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--text-2);
}

.notify-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.notify-count {
  margin-right: auto;
  font-size: 13px;
  color: var(--text-2);
}

.suspend-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.suspend-table :deep(.ant-table-tbody > tr > td) {
  padding: 9px 8px;
  font-size: 13px;
  border-right: 1px solid #f5f6f7;
}

.ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-btn {
  padding: 0 4px;
}

.muted {
  color: #8f959e;
}
</style>
