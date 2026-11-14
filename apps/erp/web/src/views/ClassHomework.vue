<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import {
  createHomework,
  deleteHomework,
  fetchClasses,
  fetchHomeworks,
  updateHomework,
  type HomeworkRow,
} from '../api/client'

interface OptionItem {
  label: string
  value: number
}

const classOptions = ref<OptionItem[]>([])

const filter = reactive<{
  classId: number | undefined
  range: string[] | null
  keyword: string
}>({
  classId: undefined,
  range: null,
  keyword: '',
})

const rows = ref<HomeworkRow[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = 20

const columns = [
  { title: '发布日期', dataIndex: 'publishDate', width: 110 },
  { title: '班级', dataIndex: 'className', width: 160 },
  { title: '教师', dataIndex: 'teacherName', width: 90 },
  { title: '作业标题', dataIndex: 'title', width: 200, ellipsis: true },
  { title: '作业内容', dataIndex: 'content', ellipsis: true },
  { title: '截止日期', dataIndex: 'dueDate', width: 110 },
  { title: '发布人', dataIndex: 'publisher', width: 90 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', dataIndex: 'action', width: 110, fixed: 'right' },
]

const modalOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<{
  classId: number | undefined
  title: string
  content: string
  publishDate: string
  dueDate: string | undefined
}>({
  classId: undefined,
  title: '',
  content: '',
  publishDate: today(),
  dueDate: undefined,
})

function today() {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
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

async function loadRows() {
  loading.value = true
  try {
    const res = await fetchHomeworks({
      classId: filter.classId,
      keyword: filter.keyword.trim() || undefined,
      start: filter.range?.[0],
      end: filter.range?.[1],
      page: page.value,
      size: pageSize,
    })
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    rows.value = []
    total.value = 0
    message.error(e instanceof Error ? e.message : '作业列表加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadRows()
}

function reset() {
  filter.classId = undefined
  filter.range = null
  filter.keyword = ''
  page.value = 1
  loadRows()
}

function openCreate() {
  editingId.value = null
  form.classId = filter.classId
  form.title = ''
  form.content = ''
  form.publishDate = today()
  form.dueDate = undefined
  modalOpen.value = true
}

function openEdit(record: HomeworkRow) {
  editingId.value = record.id
  form.classId = record.classId
  form.title = record.title
  form.content = record.content
  form.publishDate = record.publishDate ? String(record.publishDate).slice(0, 10) : today()
  form.dueDate = record.dueDate ? String(record.dueDate).slice(0, 10) : undefined
  modalOpen.value = true
}

async function save() {
  if (!form.classId) {
    message.warning('请选择班级')
    return
  }
  if (!form.title.trim()) {
    message.warning('请填写作业标题')
    return
  }
  if (!form.content.trim()) {
    message.warning('请填写作业内容')
    return
  }
  saving.value = true
  try {
    const payload = {
      classId: form.classId,
      title: form.title.trim(),
      content: form.content.trim(),
      publishDate: form.publishDate,
      dueDate: form.dueDate,
    }
    if (editingId.value) {
      await updateHomework(editingId.value, payload)
      message.success('作业已更新')
    } else {
      await createHomework(payload)
      message.success('作业已发布')
    }
    modalOpen.value = false
    await loadRows()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(record: HomeworkRow) {
  try {
    await deleteHomework(record.id)
    message.success('作业已删除')
    if (rows.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await loadRows()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(() => {
  loadOptions()
  loadRows()
})
</script>

<template>
  <div class="homework-page">
    <div class="filter-line">
      <span class="filter-label">发布日期</span>
      <a-range-picker
        v-model:value="filter.range"
        value-format="YYYY-MM-DD"
        size="small"
        style="width: 220px"
        @change="search"
      />
      <a-select
        v-model:value="filter.classId"
        size="small"
        show-search
        allow-clear
        style="width: 200px"
        placeholder="全部班级"
        :options="classOptions"
        :filter-option="filterOption"
        @change="search"
      />
      <a-input
        v-model:value="filter.keyword"
        size="small"
        placeholder="作业标题/内容"
        allow-clear
        style="width: 170px"
        @press-enter="search"
      />
      <a-button size="small" type="primary" @click="search">查询</a-button>
      <a class="reset-link" @click="reset">重置</a>
      <span class="toolbar-spacer" />
      <a-button size="small" class="refresh-btn" @click="search">
        <ReloadOutlined />刷新
      </a-button>
      <a-button size="small" type="primary" @click="openCreate">布置作业</a-button>
    </div>

    <div class="summary-line">当前结果：共 {{ total }} 份作业</div>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{
        current: page,
        pageSize,
        total,
        showSizeChanger: false,
        showTotal: (value: number) => `共 ${value} 条`,
        onChange: (value: number) => {
          page = value
          loadRows()
        },
      }"
      row-key="id"
      size="small"
      :scroll="{ x: 1180 }"
    >
      <template #bodyCell="{ column, record }">
        <span v-if="column.dataIndex === 'publishDate'">{{ String(record.publishDate ?? '').slice(0, 10) || '—' }}</span>
        <span v-else-if="column.dataIndex === 'className'">{{ record.className || '—' }}</span>
        <span v-else-if="column.dataIndex === 'teacherName'">{{ record.teacherName || '—' }}</span>
        <span v-else-if="column.dataIndex === 'title'" :title="record.title">{{ record.title }}</span>
        <span v-else-if="column.dataIndex === 'content'" :title="record.content">{{ record.content }}</span>
        <span v-else-if="column.dataIndex === 'dueDate'">{{ record.dueDate ? String(record.dueDate).slice(0, 10) : '—' }}</span>
        <span v-else-if="column.dataIndex === 'publisher'">{{ record.publisher || '—' }}</span>
        <span v-else-if="column.dataIndex === 'status'">
          <a-tag color="green" style="margin: 0">{{ record.status }}</a-tag>
        </span>
        <span v-else-if="column.dataIndex === 'action'" class="row-actions">
          <a class="op-link" @click="openEdit(record)">编辑</a>
          <span class="op-sep">|</span>
          <a-popconfirm title="删除后不可恢复，确认删除这份作业？" ok-text="删除" cancel-text="取消" @confirm="remove(record)">
            <a class="op-link danger">删除</a>
          </a-popconfirm>
        </span>
      </template>
      <template #emptyText>
        <a-empty description="暂无作业，点击右上角「布置作业」发布" />
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editingId ? '编辑作业' : '布置作业'"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      width="640px"
      @ok="save"
    >
      <div class="form-line">
        <span class="form-label required">班级</span>
        <a-select
          v-model:value="form.classId"
          show-search
          placeholder="请选择班级"
          :options="classOptions"
          :filter-option="filterOption"
          style="flex: 1"
        />
      </div>
      <div class="form-line">
        <span class="form-label required">发布日期</span>
        <a-date-picker v-model:value="form.publishDate" value-format="YYYY-MM-DD" style="width: 160px" />
        <span class="form-label">截止日期</span>
        <a-date-picker v-model:value="form.dueDate" value-format="YYYY-MM-DD" style="width: 160px" />
      </div>
      <div class="form-line">
        <span class="form-label required">作业标题</span>
        <a-input v-model:value="form.title" :maxlength="128" show-count placeholder="例如：第 5 课《弟子规》诵读练习" />
      </div>
      <div class="form-block">
        <span class="form-label required">作业内容</span>
        <a-textarea
          v-model:value="form.content"
          :rows="6"
          :maxlength="1000"
          show-count
          placeholder="请填写作业要求、提交方式与注意事项"
        />
      </div>
      <div class="form-tip">作业发布后在班级详情「作业」页签可查看，家长端小程序可同步查看。</div>
    </a-modal>
  </div>
</template>

<style scoped>
.homework-page {
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

.filter-label,
.form-label {
  color: var(--text-2);
}

.reset-link {
  color: var(--brand);
  font-size: 13px;
}

.toolbar-spacer {
  flex: 1;
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.op-link {
  color: var(--brand);
}

.op-link.danger {
  color: #d4380d;
}

.op-sep {
  color: #d9d9d9;
}

.form-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.form-label {
  width: 72px;
  font-size: 13px;
  text-align: right;
}

.form-label.required::before {
  content: '*';
  color: #ff4d4f;
  margin-right: 3px;
}

.form-block {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.form-block :deep(.ant-input) {
  flex: 1;
}

.form-tip {
  font-size: 12px;
  color: var(--text-2);
}
</style>
