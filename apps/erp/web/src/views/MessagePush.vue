<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  fetchClasses,
  fetchMessageReceivers,
  fetchMessages,
  fetchStudents,
  sendMessage,
  type MessageAudienceType,
  type MessageReceiverRow,
  type MessageRow,
} from '../api/client'

interface OptionItem {
  label: string
  value: number
}

const rows = ref<MessageRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const msgType = ref<string | undefined>(undefined)
const range = ref<string[] | null>(null)

const classOptions = ref<OptionItem[]>([])
const studentOptions = ref<OptionItem[]>([])
const studentSearching = ref(false)
const selectedStudentOptions = ref<OptionItem[]>([])
let studentSearchTimer: number | undefined
let studentSearchSeq = 0

const composeOpen = ref(false)
const sending = ref(false)
const form = reactive<{
  title: string
  content: string
  audienceType: MessageAudienceType
  classIds: number[]
  studentIds: number[]
}>({
  title: '',
  content: '',
  audienceType: '全员',
  classIds: [],
  studentIds: [],
})

const receiverOpen = ref(false)
const receiverLoading = ref(false)
const receiverRows = ref<MessageReceiverRow[]>([])
const receiverTotal = ref(0)
const receiverPage = ref(1)
const receiverReadFilter = ref<number | undefined>(undefined)
const currentMessage = ref<MessageRow | null>(null)

const columns = [
  { title: '标题', dataIndex: 'title', width: 220 },
  { title: '类型', dataIndex: 'msgType', width: 100 },
  { title: '推送范围', dataIndex: 'audienceType', width: 100 },
  { title: '推送班级', dataIndex: 'classNameText', width: 200 },
  { title: '目标学员', dataIndex: 'targetCount', width: 90, align: 'right' as const },
  { title: '触达家长', dataIndex: 'receiverCount', width: 90, align: 'right' as const },
  { title: '已读', dataIndex: 'readCount', width: 90, align: 'right' as const },
  { title: '发送人', dataIndex: 'sender', width: 90 },
  { title: '发送时间', dataIndex: 'sentAt', width: 160 },
  { title: '操作', dataIndex: 'action', width: 110 },
]

const receiverColumns = [
  { title: '学员', dataIndex: 'studentName', width: 120 },
  { title: '家长', dataIndex: 'parentName', width: 160 },
  { title: '手机号', dataIndex: 'parentPhone', width: 130 },
  { title: '阅读状态', dataIndex: 'readFlag', width: 100 },
  { title: '阅读时间', dataIndex: 'readAt', width: 160 },
]

function filterOption(input: string, option: { label?: string }) {
  return String(option.label ?? '').toLowerCase().includes(input.toLowerCase())
}

function formatTime(value: string | null) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function audienceText(record: MessageRow) {
  return record.audienceType
}

async function load() {
  loading.value = true
  try {
    const res = await fetchMessages({
      keyword: keyword.value.trim() || undefined,
      msgType: msgType.value,
      start: range.value?.[0],
      end: range.value?.[1],
      page: page.value,
      size: size.value,
    })
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载推送记录失败')
  } finally {
    loading.value = false
  }
}

async function loadClassOptions() {
  try {
    const classes = await fetchClasses({ page: 1, size: 200 })
    classOptions.value = classes.records.map((item) => ({ label: item.name, value: item.id }))
  } catch {
    // 下拉数据加载失败不阻断列表展示
  }
}

async function searchStudents(keywordText: string) {
  const seq = ++studentSearchSeq
  studentSearching.value = true
  try {
    const res = await fetchStudents({ keyword: keywordText.trim() || undefined, page: 1, size: 50 })
    if (seq !== studentSearchSeq) return
    const found = res.records.map((item) => ({
      label: item.phone ? `${item.name}（${item.phone}）` : item.name,
      value: item.id,
    }))
    // 已选学员保留在下拉里，避免搜索后标签回退成 id
    const keep = selectedStudentOptions.value.filter((item) => !found.some((f) => f.value === item.value))
    studentOptions.value = [...keep, ...found]
  } catch {
    // 搜索失败不阻断弹窗填写
  } finally {
    if (seq === studentSearchSeq) studentSearching.value = false
  }
}

function onStudentSearch(value: string) {
  window.clearTimeout(studentSearchTimer)
  studentSearchTimer = window.setTimeout(() => searchStudents(value), 300)
}

function onStudentChange(values: number[]) {
  selectedStudentOptions.value = studentOptions.value.filter((item) => values.includes(item.value))
}

function openCompose() {
  form.title = ''
  form.content = ''
  form.audienceType = '全员'
  form.classIds = []
  form.studentIds = []
  selectedStudentOptions.value = []
  studentOptions.value = []
  composeOpen.value = true
  searchStudents('')
}

async function submitCompose() {
  if (!form.title.trim()) {
    message.warning('请填写消息标题')
    return
  }
  if (!form.content.trim()) {
    message.warning('请填写消息内容')
    return
  }
  if (form.audienceType === '班级' && !form.classIds.length) {
    message.warning('请选择要推送的班级')
    return
  }
  if (form.audienceType === '学员' && !form.studentIds.length) {
    message.warning('请选择要推送的学员')
    return
  }
  sending.value = true
  try {
    const result = await sendMessage({
      title: form.title.trim(),
      content: form.content.trim(),
      audienceType: form.audienceType,
      classIds: form.audienceType === '班级' ? form.classIds : undefined,
      studentIds: form.audienceType === '学员' ? form.studentIds : undefined,
    })
    message.success(
      result.receiverCount
        ? `推送完成：目标学员 ${result.targetCount} 人，当前已绑定家长 ${result.receiverCount} 人`
        : `推送完成：目标学员 ${result.targetCount} 人，暂无已绑定家长，家长绑定后可在小程序查看`,
    )
    composeOpen.value = false
    page.value = 1
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '推送失败')
  } finally {
    sending.value = false
  }
}

async function openReceivers(record: MessageRow) {
  currentMessage.value = record
  receiverOpen.value = true
  receiverReadFilter.value = undefined
  receiverPage.value = 1
  await loadReceivers()
}

async function loadReceivers() {
  if (!currentMessage.value) return
  receiverLoading.value = true
  try {
    const res = await fetchMessageReceivers(currentMessage.value.id, {
      readFlag: receiverReadFilter.value,
      page: receiverPage.value,
      size: 10,
    })
    receiverRows.value = res.records
    receiverTotal.value = res.total
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载接收明细失败')
  } finally {
    receiverLoading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  msgType.value = undefined
  range.value = null
  page.value = 1
  load()
}

onMounted(() => {
  load()
  loadClassOptions()
})
</script>

<template>
  <div class="message-page">
    <div class="filter-line">
      <a-button size="small" type="primary" class="with-icon" @click="openCompose">
        <PlusOutlined />新建推送
      </a-button>
      <a-input-search
        v-model:value="keyword"
        size="small"
        placeholder="搜索标题或内容"
        allow-clear
        style="width: 200px"
        @search="search"
        @change="!keyword && search()"
      />
      <a-select
        v-model:value="msgType"
        size="small"
        style="width: 120px"
        placeholder="全部类型"
        allow-clear
        :options="[
          { label: '系统通知', value: '系统通知' },
          { label: '休课通知', value: '休课通知' },
        ]"
        @change="search"
      />
      <a-range-picker
        v-model:value="range"
        value-format="YYYY-MM-DD"
        size="small"
        style="width: 220px"
        :placeholder="['发送开始日期', '发送结束日期']"
        @change="search"
      />
      <a class="reset-link" @click="reset">清空筛选</a>
    </div>

    <div class="summary-line">
      当前结果：共 {{ total }} 条推送记录；消息按学员留存，家长绑定后 30 天内的通知可在小程序查看。
    </div>

    <a-table
      class="message-table"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{
        current: page,
        pageSize: size,
        total,
        showSizeChanger: false,
        showTotal: (value: number) => `共 ${value} 条`,
        onChange: (value: number) => {
          page = value
          load()
        },
      }"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <span v-if="column.dataIndex === 'title'" class="ellipsis" :title="record.content">{{ record.title }}</span>
        <span v-else-if="column.dataIndex === 'msgType'">
          <a-tag :color="record.msgType === '休课通知' ? 'orange' : 'blue'" style="margin: 0">
            {{ record.msgType }}
          </a-tag>
        </span>
        <span v-else-if="column.dataIndex === 'audienceType'">{{ audienceText(record) }}</span>
        <span v-else-if="column.dataIndex === 'classNameText'" class="ellipsis">
          {{ record.classNameText || (record.audienceType === '全员' ? '全体家长' : '—') }}
        </span>
        <span v-else-if="column.dataIndex === 'targetCount'">{{ record.targetCount }}</span>
        <span v-else-if="column.dataIndex === 'receiverCount'">{{ record.receiverCount }}</span>
        <span v-else-if="column.dataIndex === 'readCount'">{{ record.readCount }}</span>
        <span v-else-if="column.dataIndex === 'sender'">{{ record.sender || '—' }}</span>
        <span v-else-if="column.dataIndex === 'sentAt'">{{ formatTime(record.sentAt) }}</span>
        <span v-else-if="column.dataIndex === 'action'">
          <a-button type="link" size="small" class="row-btn" @click="openReceivers(record)">接收明细</a-button>
        </span>
      </template>
      <template #emptyText>
        <a-empty description="暂无推送记录，点击左上角「新建推送」向家长端小程序发送消息" />
      </template>
    </a-table>

    <a-modal
      v-model:open="composeOpen"
      title="新建消息推送"
      width="560"
      :confirm-loading="sending"
      ok-text="立即推送"
      cancel-text="取消"
      @ok="submitCompose"
    >
      <a-form layout="vertical" class="compose-form">
        <a-form-item label="消息标题" required>
          <a-input v-model:value="form.title" :maxlength="60" placeholder="例如：本周六课程调整通知" />
        </a-form-item>
        <a-form-item label="消息内容" required>
          <a-textarea
            v-model:value="form.content"
            :rows="5"
            :maxlength="1000"
            show-count
            placeholder="请输入推送给家长的完整内容"
          />
        </a-form-item>
        <a-form-item label="推送范围" required>
          <a-radio-group v-model:value="form.audienceType">
            <a-radio value="全员">全员家长</a-radio>
            <a-radio value="班级">按班级</a-radio>
            <a-radio value="学员">按学员</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="form.audienceType === '班级'" label="选择班级" required>
          <a-select
            v-model:value="form.classIds"
            mode="multiple"
            show-search
            allow-clear
            placeholder="请选择推送班级，可多选"
            :options="classOptions"
            :filter-option="filterOption"
            :max-tag-count="4"
          />
        </a-form-item>
        <a-form-item v-if="form.audienceType === '学员'" label="选择学员" required>
          <a-select
            v-model:value="form.studentIds"
            mode="multiple"
            show-search
            allow-clear
            placeholder="输入学员姓名或手机号搜索，可多选"
            :options="studentOptions"
            :filter-option="false"
            :loading="studentSearching"
            :not-found-content="studentSearching ? '搜索中…' : '未找到匹配学员'"
            :max-tag-count="4"
            @search="onStudentSearch"
            @change="onStudentChange"
          />
        </a-form-item>
        <div class="compose-tip">
          消息会保存给所选学员的家长，进入家长端微信小程序的「消息通知」；当前未绑定的家长在绑定孩子后 30 天内可看到这条通知。触达人数仅统计发送时已绑定的家长。
        </div>
      </a-form>
    </a-modal>

    <a-drawer
      v-model:open="receiverOpen"
      title="接收明细"
      width="720"
      :body-style="{ padding: '0 16px 16px' }"
    >
      <div class="receiver-head">
        <div class="receiver-title">{{ currentMessage?.title }}</div>
        <div class="receiver-meta">
          <span>目标学员 {{ currentMessage?.targetCount ?? 0 }} 人</span>
          <span>触达家长 {{ currentMessage?.receiverCount ?? 0 }} 人</span>
          <span>已读 {{ currentMessage?.readCount ?? 0 }} 人</span>
        </div>
        <div class="receiver-content">{{ currentMessage?.content }}</div>
      </div>
      <div class="receiver-filter">
        <a-select
          v-model:value="receiverReadFilter"
          size="small"
          style="width: 120px"
          placeholder="全部状态"
          allow-clear
          :options="[
            { label: '未读', value: 0 },
            { label: '已读', value: 1 },
          ]"
          @change="
            () => {
              receiverPage = 1
              loadReceivers()
            }
          "
        />
      </div>
      <a-table
        :columns="receiverColumns"
        :data-source="receiverRows"
        :loading="receiverLoading"
        :pagination="{
          current: receiverPage,
          pageSize: 10,
          total: receiverTotal,
          showSizeChanger: false,
          onChange: (value: number) => {
            receiverPage = value
            loadReceivers()
          },
        }"
        row-key="id"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <span v-if="column.dataIndex === 'studentName'">{{ record.studentName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'parentName'">{{ record.parentName || '—' }}</span>
          <span v-else-if="column.dataIndex === 'parentPhone'">{{ record.parentPhone || '—' }}</span>
          <span v-else-if="column.dataIndex === 'readFlag'">
            <a-tag :color="record.readFlag ? 'green' : 'default'" style="margin: 0">
              {{ record.readFlag ? '已读' : '未读' }}
            </a-tag>
          </span>
          <span v-else-if="column.dataIndex === 'readAt'">{{ formatTime(record.readAt) }}</span>
        </template>
        <template #emptyText>
          <a-empty description="暂无接收明细" />
        </template>
      </a-table>
    </a-drawer>
  </div>
</template>

<style scoped>
.message-page {
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

.reset-link {
  color: var(--brand);
  font-size: 13px;
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.message-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.message-table :deep(.ant-table-tbody > tr > td) {
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

.compose-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.compose-tip {
  padding: 8px 10px;
  background: #f6f8fb;
  border: 1px solid #e8eef5;
  border-radius: 4px;
  color: var(--text-2);
  font-size: 12px;
  line-height: 1.6;
}

.receiver-head {
  padding: 12px 0;
  border-bottom: 1px solid #f0f1f2;
}

.receiver-title {
  font-size: 15px;
  font-weight: 600;
}

.receiver-meta {
  display: flex;
  gap: 16px;
  margin-top: 6px;
  color: var(--text-2);
  font-size: 12px;
}

.receiver-content {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fafbfc;
  border-radius: 4px;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.7;
}

.receiver-filter {
  padding: 10px 0;
}
</style>
