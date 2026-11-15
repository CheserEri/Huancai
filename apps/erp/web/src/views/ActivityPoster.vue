<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  DeleteOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons-vue'
import {
  createPoster,
  deletePoster,
  fetchClasses,
  fetchPosterReceivers,
  fetchPosters,
  fetchStudentGrades,
  fetchStudents,
  uploadPosterImage,
  type MessageReceiverRow,
  type PosterRow,
} from '../api/client'

interface OptionItem {
  label: string
  value: number
}

interface GradeOption {
  label: string
  value: string
}

const rows = ref<PosterRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const range = ref<string[] | null>(null)

const classOptions = ref<OptionItem[]>([])
const gradeOptions = ref<GradeOption[]>([])
const studentOptions = ref<OptionItem[]>([])
const studentSearching = ref(false)
const selectedStudentOptions = ref<OptionItem[]>([])
let studentSearchTimer: number | undefined
let studentSearchSeq = 0

const composeOpen = ref(false)
const sending = ref(false)
const uploading = ref(false)
const images = ref<string[]>([])
const form = reactive({
  title: '',
  description: '',
  grades: [] as string[],
  classIds: [] as number[],
  studentIds: [] as number[],
  validRange: null as string[] | null,
})

const receiverOpen = ref(false)
const receiverLoading = ref(false)
const receiverRows = ref<MessageReceiverRow[]>([])
const receiverTotal = ref(0)
const receiverPage = ref(1)
const receiverReadFilter = ref<number | undefined>(undefined)
const currentPoster = ref<PosterRow | null>(null)

const columns = [
  { title: '海报', dataIndex: 'coverUrl', width: 80 },
  { title: '活动名称', dataIndex: 'title', width: 200 },
  { title: '推送对象', dataIndex: 'audienceType', width: 220 },
  { title: '有效期', dataIndex: 'validity', width: 180 },
  { title: '海报数', dataIndex: 'imageCount', width: 80, align: 'right' as const },
  { title: '目标学员', dataIndex: 'targetCount', width: 90, align: 'right' as const },
  { title: '触达家长', dataIndex: 'receiverCount', width: 90, align: 'right' as const },
  { title: '已读', dataIndex: 'readCount', width: 80, align: 'right' as const },
  { title: '发送人', dataIndex: 'sender', width: 90 },
  { title: '发送时间', dataIndex: 'sentAt', width: 150 },
  { title: '操作', dataIndex: 'action', width: 130 },
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

function validityText(record: PosterRow) {
  if (!record.startDate && !record.endDate) return '长期有效'
  return `${record.startDate || '不限'} 至 ${record.endDate || '不限'}`
}

function audienceText(record: PosterRow) {
  const parts: string[] = []
  if (record.gradeText) parts.push(record.gradeText)
  if (record.classNameText) parts.push(record.classNameText)
  return parts.length ? `${record.audienceType}：${parts.join('；')}` : record.audienceType
}

async function load() {
  loading.value = true
  try {
    const res = await fetchPosters({
      keyword: keyword.value.trim() || undefined,
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

async function loadOptions() {
  try {
    const [classes, grades] = await Promise.all([
      fetchClasses({ page: 1, size: 200 }),
      fetchStudentGrades(),
    ])
    classOptions.value = classes.records.map((item) => ({ label: item.name, value: item.id }))
    gradeOptions.value = grades.map((grade) => ({ label: grade, value: grade }))
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
  form.description = ''
  form.grades = []
  form.classIds = []
  form.studentIds = []
  form.validRange = null
  images.value = []
  selectedStudentOptions.value = []
  studentOptions.value = []
  composeOpen.value = true
  searchStudents('')
}

async function handleUpload(options: any) {
  const file = options?.file as File | undefined
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    options?.onError?.(new Error('格式不支持'))
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('单张海报不能超过 10MB')
    options?.onError?.(new Error('文件过大'))
    return
  }
  if (images.value.length >= 9) {
    message.warning('一次推送最多 9 张海报')
    options?.onError?.(new Error('数量超限'))
    return
  }
  uploading.value = true
  try {
    const result = await uploadPosterImage(file)
    images.value.push(result.url)
    options?.onSuccess?.(result)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '海报上传失败')
    options?.onError?.(e as Error)
  } finally {
    uploading.value = false
  }
}

function removeImage(index: number) {
  images.value.splice(index, 1)
}

function moveImage(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= images.value.length) return
  const list = [...images.value]
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
  images.value = list
}

async function submitCompose() {
  if (!form.title.trim()) {
    message.warning('请填写活动名称')
    return
  }
  if (!images.value.length) {
    message.warning('请至少上传一张活动海报')
    return
  }
  if (!form.grades.length && !form.classIds.length && !form.studentIds.length) {
    message.warning('请至少选择年级、班级或学员中的一项')
    return
  }
  sending.value = true
  try {
    const result = await createPoster({
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      imageUrls: images.value,
      grades: form.grades,
      classIds: form.classIds,
      studentIds: form.studentIds,
      startDate: form.validRange?.[0],
      endDate: form.validRange?.[1],
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

async function removePoster(record: PosterRow) {
  try {
    await deletePoster(record.id)
    message.success('已删除该海报推送')
    if (rows.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function openReceivers(record: PosterRow) {
  currentPoster.value = record
  receiverOpen.value = true
  receiverReadFilter.value = undefined
  receiverPage.value = 1
  await loadReceivers()
}

async function loadReceivers() {
  if (!currentPoster.value) return
  receiverLoading.value = true
  try {
    const res = await fetchPosterReceivers(currentPoster.value.id, {
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
  range.value = null
  page.value = 1
  load()
}

onMounted(() => {
  load()
  loadOptions()
})
</script>

<template>
  <div class="poster-page">
    <div class="filter-line">
      <a-button size="small" type="primary" class="with-icon" @click="openCompose">
        <PlusOutlined />新建推送
      </a-button>
      <a-input-search
        v-model:value="keyword"
        size="small"
        placeholder="搜索活动名称或说明"
        allow-clear
        style="width: 200px"
        @search="search"
        @change="!keyword && search()"
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
      当前结果：共 {{ total }} 条推送记录；家长在「消息通知」收到提醒，并在小程序活动页查看海报。
    </div>

    <a-table
      class="poster-table"
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
        <span v-if="column.dataIndex === 'coverUrl'">
          <a-image v-if="record.coverUrl" :src="record.coverUrl" :width="44" class="cover" />
          <span v-else class="muted">—</span>
        </span>
        <span v-else-if="column.dataIndex === 'title'" class="ellipsis" :title="record.description || record.title">
          {{ record.title }}
        </span>
        <span v-else-if="column.dataIndex === 'audienceType'" class="ellipsis" :title="audienceText(record)">
          {{ audienceText(record) }}
        </span>
        <span v-else-if="column.dataIndex === 'validity'">{{ validityText(record) }}</span>
        <span v-else-if="column.dataIndex === 'imageCount'">{{ record.imageCount }}</span>
        <span v-else-if="column.dataIndex === 'targetCount'">{{ record.targetCount }}</span>
        <span v-else-if="column.dataIndex === 'receiverCount'">{{ record.receiverCount }}</span>
        <span v-else-if="column.dataIndex === 'readCount'">{{ record.readCount }}</span>
        <span v-else-if="column.dataIndex === 'sender'">{{ record.sender || '—' }}</span>
        <span v-else-if="column.dataIndex === 'sentAt'">{{ formatTime(record.sentAt) }}</span>
        <span v-else-if="column.dataIndex === 'action'">
          <a-button type="link" size="small" class="row-btn" @click="openReceivers(record)">接收明细</a-button>
          <a-popconfirm
            title="删除后家长端将不再展示该海报，确认删除？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="removePoster(record)"
          >
            <a-button type="link" size="small" danger class="row-btn">删除</a-button>
          </a-popconfirm>
        </span>
      </template>
      <template #emptyText>
        <a-empty description="暂无活动海报推送，点击左上角「新建推送」上传海报" />
      </template>
    </a-table>

    <a-modal
      v-model:open="composeOpen"
      width="780"
      centered
      class="compose-modal"
      :confirm-loading="sending"
      ok-text="立即推送"
      cancel-text="取消"
      @ok="submitCompose"
    >
      <template #title>
        <div class="compose-title">
          <span class="compose-title-text">新建活动海报推送</span>
          <span class="compose-title-sub">上传海报并选择推送范围，家长端同步收到消息提醒</span>
        </div>
      </template>

      <div class="compose">
        <div class="field">
          <div class="field-head">
            <label class="field-label">活动名称<i>*</i></label>
          </div>
          <a-input
            v-model:value="form.title"
            :maxlength="128"
            size="large"
            placeholder="例如：寒假托管营报名开始"
          />
        </div>

        <div class="field">
          <div class="field-head">
            <label class="field-label">活动海报<i>*</i></label>
            <span class="field-hint">已上传 {{ images.length }}/9 张，家长端按顺序左右滑动查看</span>
          </div>
          <div class="poster-grid">
            <div v-for="(url, index) in images" :key="url" class="poster-card">
              <a-image :src="url" :width="108" :height="144" class="poster-img" />
              <span class="poster-order">{{ index + 1 }}</span>
              <div class="poster-tools">
                <button
                  type="button"
                  class="tool"
                  title="前移一位"
                  :disabled="index === 0"
                  @click="moveImage(index, -1)"
                >
                  <ArrowLeftOutlined />
                </button>
                <button
                  type="button"
                  class="tool"
                  title="后移一位"
                  :disabled="index === images.length - 1"
                  @click="moveImage(index, 1)"
                >
                  <ArrowRightOutlined />
                </button>
                <button type="button" class="tool danger" title="移除这张海报" @click="removeImage(index)">
                  <DeleteOutlined />
                </button>
              </div>
            </div>
            <a-upload
              v-if="images.length < 9"
              :show-upload-list="false"
              :custom-request="handleUpload"
              accept="image/*"
              multiple
            >
              <div class="upload-tile" :class="{ busy: uploading }">
                <PlusOutlined class="upload-plus" />
                <span class="upload-text">{{ uploading ? '上传中…' : '上传海报' }}</span>
                <span class="upload-tip">JPG / PNG / WebP</span>
                <span class="upload-tip">单张不超过 10MB</span>
              </div>
            </a-upload>
          </div>
        </div>

        <div class="field">
          <div class="field-head">
            <label class="field-label">推送说明</label>
            <span class="field-hint">选填，作为家长端消息通知的正文</span>
          </div>
          <a-textarea
            v-model:value="form.description"
            :rows="2"
            :maxlength="500"
            show-count
            placeholder="例如：限时优惠，点击查看活动海报"
          />
        </div>

        <div class="field">
          <div class="field-head">
            <label class="field-label">推送对象<i>*</i></label>
            <span class="field-hint">三个维度可自由组合，取并集去重，同一学员只推送一次</span>
          </div>
          <div class="audience-grid">
            <div class="audience-item">
              <span class="audience-label">年级</span>
              <a-select
                v-model:value="form.grades"
                mode="multiple"
                allow-clear
                placeholder="全部年级"
                :options="gradeOptions"
                :max-tag-count="1"
              />
            </div>
            <div class="audience-item">
              <span class="audience-label">班级</span>
              <a-select
                v-model:value="form.classIds"
                mode="multiple"
                show-search
                allow-clear
                placeholder="全部班级"
                :options="classOptions"
                :filter-option="filterOption"
                :max-tag-count="1"
              />
            </div>
            <div class="audience-item wide">
              <span class="audience-label">学员</span>
              <a-select
                v-model:value="form.studentIds"
                mode="multiple"
                show-search
                allow-clear
                placeholder="输入姓名或手机号搜索学员"
                :options="studentOptions"
                :filter-option="false"
                :loading="studentSearching"
                :not-found-content="studentSearching ? '搜索中…' : '未找到匹配学员'"
                :max-tag-count="2"
                @search="onStudentSearch"
                @change="onStudentChange"
              />
            </div>
          </div>
        </div>

        <div class="field">
          <div class="field-head">
            <label class="field-label">有效日期</label>
            <span class="field-hint">留空表示长期有效，过期后家长端自动隐藏</span>
          </div>
          <a-range-picker
            v-model:value="form.validRange"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :placeholder="['开始日期', '结束日期']"
          />
        </div>

        <div class="compose-tip">
          <InfoCircleOutlined class="tip-icon" />
          <span>
            推送后家长会在小程序「消息通知」收到一条提醒，并在「活动」页查看海报；触达人数仅统计发送时已绑定小程序的家长。
          </span>
        </div>
      </div>
    </a-modal>

    <a-drawer v-model:open="receiverOpen" title="接收明细" width="720" :body-style="{ padding: '0 16px 16px' }">
      <div class="receiver-head">
        <div class="receiver-title">{{ currentPoster?.title }}</div>
        <div class="receiver-meta">
          <span>目标学员 {{ currentPoster?.targetCount ?? 0 }} 人</span>
          <span>触达家长 {{ currentPoster?.receiverCount ?? 0 }} 人</span>
          <span>已读 {{ currentPoster?.readCount ?? 0 }} 人</span>
        </div>
        <div class="receiver-content">
          {{ currentPoster?.description || '未填写推送说明' }}
        </div>
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
.poster-page {
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

.poster-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.poster-table :deep(.ant-table-tbody > tr > td) {
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

.muted {
  color: var(--text-2);
}

.cover {
  border-radius: 4px;
  object-fit: cover;
}

.row-btn {
  padding: 0 4px;
}

.compose-modal :deep(.ant-modal-content) {
  padding: 22px 24px 16px;
  border-radius: 14px;
}

.compose-modal :deep(.ant-modal-header) {
  margin-bottom: 16px;
  border-bottom: none;
}

.compose-modal :deep(.ant-modal-footer) {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #f0f2f5;
}

.compose-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.compose-title-text {
  font-size: 17px;
  font-weight: 600;
  color: var(--text);
}

.compose-title-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-2);
}

.compose {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 6px;
}

.field-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text);
}

.field-label i {
  margin-left: 2px;
  font-style: normal;
  color: #ff4d4f;
}

.field-hint {
  font-size: 12px;
  color: var(--text-2);
}

.poster-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.poster-card {
  position: relative;
  width: 108px;
  height: 144px;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f7fa;
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.06), 0 2px 6px rgba(15, 23, 42, 0.05);
}

.poster-img {
  display: block;
}

.poster-img :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.poster-order {
  position: absolute;
  top: 6px;
  left: 6px;
  z-index: 2;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.62);
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.poster-tools {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2;
  display: flex;
  justify-content: center;
  gap: 4px;
  padding: 14px 6px 6px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.6));
  opacity: 0;
  transition: opacity 0.2s;
}

.poster-card:hover .poster-tools {
  opacity: 1;
}

.tool {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.94);
  color: #334155;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.tool:hover:not(:disabled) {
  background: #fff;
  color: var(--brand);
}

.tool:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.tool.danger:hover {
  color: #ff4d4f;
}

.upload-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 108px;
  height: 144px;
  border: 1px dashed #cfd8e3;
  border-radius: 10px;
  background: #fafbfd;
  color: var(--text-2);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.upload-tile:hover {
  border-color: var(--brand);
  background: #f5f9ff;
  color: var(--brand);
}

.upload-tile.busy {
  opacity: 0.6;
  pointer-events: none;
}

.upload-plus {
  font-size: 18px;
}

.upload-text {
  font-size: 12px;
}

.upload-tip {
  font-size: 11px;
  opacity: 0.85;
}

.audience-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.35fr);
  gap: 12px;
}

.audience-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 8px 10px 10px;
  border: 1px solid #f0f3f7;
  border-radius: 8px;
  background: #fafbfd;
}

.audience-item.wide {
  grid-column: 1 / -1;
}

.audience-label {
  font-size: 12px;
  color: var(--text-2);
}

.audience-item :deep(.ant-select) {
  width: 100%;
}

.compose-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #e8eef5;
  border-radius: 8px;
  background: #f6f8fb;
  color: var(--text-2);
  font-size: 12px;
  line-height: 1.6;
}

.tip-icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--brand);
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
