<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  batchSuspend,
  fetchClasses,
  fetchSuspends,
  fetchSuspendCandidates,
  restoreSuspend,
  type SuspendCandidateRow,
  type SuspendRow,
} from '../api/client'

interface OptionItem {
  label: string
  value: number
}

const activeTab = ref<'handle' | 'records'>('handle')

const classOptions = ref<OptionItem[]>([])

const handleFilter = reactive<{
  range: string[]
  classIds: number[]
  keyword: string
}>({
  range: [today(), dateAfter(7)],
  classIds: [],
  keyword: '',
})

const candidates = ref<SuspendCandidateRow[]>([])
const candidateLoading = ref(false)
const selectedIds = ref<number[]>([])
const reason = ref('')
const notify = ref(true)
const submitting = ref(false)

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

const candidateColumns = [
  { title: '上课日期', dataIndex: 'lessonDate', width: 110 },
  { title: '上课时间', dataIndex: 'timeText', width: 120 },
  { title: '班级', dataIndex: 'className', width: 180 },
  { title: '课程', dataIndex: 'courseName', width: 140 },
  { title: '教师', dataIndex: 'teacherName', width: 100 },
  { title: '教室', dataIndex: 'room', width: 100 },
]

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

const selectedCount = computed(() => selectedIds.value.length)

function today() {
  return formatDate(new Date())
}

function dateAfter(days: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return formatDate(date)
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

async function loadOptions() {
  try {
    const res = await fetchClasses({ page: 1, size: 500 })
    classOptions.value = res.records.map((item) => ({ label: item.name, value: item.id }))
  } catch {
    // 班级列表加载失败时保留空选项，由查询提示
  }
}

async function loadCandidates() {
  if (!handleFilter.classIds.length) {
    message.warning('请先选择要休课的班级')
    return
  }
  candidateLoading.value = true
  try {
    candidates.value = await fetchSuspendCandidates({
      start: handleFilter.range[0],
      end: handleFilter.range[1],
      classIds: handleFilter.classIds,
      keyword: handleFilter.keyword.trim() || undefined,
    })
    selectedIds.value = []
    if (!candidates.value.length) {
      message.info('所选时间段与班级下没有可休课的待上课课次')
    }
  } catch (e) {
    candidates.value = []
    message.error(e instanceof Error ? e.message : '查询待休课课次失败')
  } finally {
    candidateLoading.value = false
  }
}

async function submitSuspend() {
  if (!selectedIds.value.length) {
    message.warning('请先勾选要休课的课次')
    return
  }
  if (!reason.value.trim()) {
    message.warning('请填写休课原因')
    return
  }
  submitting.value = true
  try {
    const result = await batchSuspend({
      scheduleIds: selectedIds.value,
      reason: reason.value.trim(),
      notify: notify.value,
    })
    const skippedText = result.skipped > 0 ? `，跳过 ${result.skipped} 节（已上课/已取消/已休课）` : ''
    const notifyText = result.messageId
      ? `，已推送休课通知，触达家长 ${result.receiverCount} 人`
      : '，未推送通知'
    message.success(`成功休课 ${result.suspended} 节${skippedText}${notifyText}`)
    if (result.notifyMessage) {
      message.warning(result.notifyMessage)
    }
    reason.value = ''
    selectedIds.value = []
    await loadCandidates()
    if (activeTab.value === 'records') {
      await loadRecords()
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '休课失败')
  } finally {
    submitting.value = false
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
    if (activeTab.value === 'handle' && handleFilter.classIds.length) {
      await loadCandidates()
    }
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
  loadRecords()
})
</script>

<template>
  <div class="suspend-page">
    <a-tabs v-model:activeKey="activeTab" class="suspend-tabs" @change="onTabChange">
      <a-tab-pane key="handle" tab="休课办理">
        <div class="filter-line">
          <span class="filter-label">上课日期</span>
          <a-range-picker
            v-model:value="handleFilter.range"
            value-format="YYYY-MM-DD"
            size="small"
            style="width: 220px"
          />
          <span class="filter-label">班级</span>
          <a-select
            v-model:value="handleFilter.classIds"
            mode="multiple"
            size="small"
            show-search
            allow-clear
            style="min-width: 260px"
            placeholder="请选择班级，可多选"
            :options="classOptions"
            :filter-option="filterOption"
            :max-tag-count="3"
          />
          <a-input
            v-model:value="handleFilter.keyword"
            size="small"
            placeholder="班级/课程/教师"
            allow-clear
            style="width: 160px"
            @press-enter="loadCandidates"
          />
          <a-button size="small" type="primary" @click="loadCandidates">查询待休课课次</a-button>
        </div>

        <div class="summary-line">
          当前结果：共 {{ candidates.length }} 节待上课课次；休课只标记课次状态，不扣减学员课时。
        </div>

        <a-table
          class="suspend-table"
          :columns="candidateColumns"
          :data-source="candidates"
          :loading="candidateLoading"
          :pagination="false"
          :row-selection="{
            selectedRowKeys: selectedIds,
            onChange: (keys: (string | number)[]) => (selectedIds = keys as number[]),
          }"
          row-key="id"
          size="small"
          :scroll="{ y: 380 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'lessonDate'">{{ record.lessonDate }}</span>
            <span v-else-if="column.dataIndex === 'timeText'">{{ timeRange(record) }}</span>
            <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
            <span v-else-if="column.dataIndex === 'courseName'">{{ record.courseName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'room'">{{ record.room || '—' }}</span>
          </template>
          <template #emptyText>
            <a-empty description="请选择班级并查询待休课课次" />
          </template>
        </a-table>

        <div class="suspend-panel">
          <div class="panel-head">
            <span class="panel-title">休课原因与通知</span>
            <span class="panel-count">已选 {{ selectedCount }} 节</span>
          </div>
          <a-textarea
            v-model:value="reason"
            :rows="4"
            :maxlength="255"
            show-count
            placeholder="请填写休课原因，例如：因场地检修，本周课程暂停一次；推送通知时会同步告知家长"
          />
          <div class="panel-actions">
            <span class="notify-label">同时推送休课通知到家长端小程序</span>
            <a-switch v-model:checked="notify" size="small" />
            <span class="panel-tip">通知内容自动包含班级、上课时间与填写的休课原因</span>
            <a-button
              type="primary"
              size="small"
              class="submit-btn"
              :loading="submitting"
              @click="submitSuspend"
            >
              批量休课
            </a-button>
          </div>
        </div>
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

.suspend-panel {
  margin-top: 14px;
  padding: 14px 16px 16px;
  background: #fafbfc;
  border: 1px solid #eef0f3;
  border-radius: 6px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
}

.panel-count {
  color: var(--text-2);
  font-size: 12px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 13px;
}

.notify-label {
  color: var(--text);
}

.panel-tip {
  color: var(--text-2);
  font-size: 12px;
}

.submit-btn {
  margin-left: auto;
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
