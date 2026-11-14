<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DownloadOutlined } from '@ant-design/icons-vue'
import {
  fetchGradeClassOptions,
  fetchGradeExams,
  fetchGradeQuery,
  type GradeClassOption,
  type GradeExamRow,
  type GradeQueryRow,
} from '../api/client'

const rows = ref<GradeQueryRow[]>([])
const exams = ref<GradeExamRow[]>([])
const classOptions = ref<GradeClassOption[]>([])
const loading = ref(false)

const examId = ref<number | undefined>(undefined)
const classId = ref<number | undefined>(undefined)
const typeFilter = ref<string | undefined>(undefined)
const statusFilter = ref<string | undefined>(undefined)
const keyword = ref('')

const examSelectOptions = computed(() =>
  exams.value.map((exam) => ({ label: `${exam.name}（${exam.examDate ?? '未定日期'}）`, value: exam.id })),
)
const classSelectOptions = computed(() =>
  classOptions.value.map((item) => ({
    label: item.courseName ? `${item.name}（${item.courseName}）` : item.name,
    value: item.id,
  })),
)

const columns = [
  { title: '考试名称', dataIndex: 'examName', width: 200 },
  { title: '类型', dataIndex: 'examType', width: 90 },
  { title: '考试日期', dataIndex: 'examDate', width: 110 },
  { title: '班级', dataIndex: 'className', width: 160 },
  { title: '学员', dataIndex: 'studentName', width: 110 },
  { title: '成绩', dataIndex: 'score', width: 100, align: 'right' as const },
  { title: '班级排名', dataIndex: 'rankClass', width: 90, align: 'center' as const },
  { title: '校区排名', dataIndex: 'rankCampus', width: 90, align: 'center' as const },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '备注', dataIndex: 'remark' },
]

async function load() {
  loading.value = true
  try {
    rows.value = await fetchGradeQuery({
      examId: examId.value,
      classId: classId.value,
      type: typeFilter.value,
      scoreStatus: statusFilter.value,
      keyword: keyword.value.trim() || undefined,
    })
  } catch (e) {
    message.error(e instanceof Error ? e.message : '查询成绩失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    exams.value = await fetchGradeExams({ status: '已发布' })
  } catch {
    exams.value = []
  }
  try {
    classOptions.value = await fetchGradeClassOptions()
  } catch {
    classOptions.value = []
  }
}

function scoreText(record: GradeQueryRow) {
  if (record.status === '缺考') return '缺考'
  if (record.status === '免考') return '免考'
  if (record.scoringMode === '等级') return record.level ?? '—'
  return record.totalScore === null ? '—' : `${record.totalScore} / ${record.fullScore}`
}

function exportCsv() {
  if (!rows.value.length) {
    message.warning('当前没有可导出的成绩')
    return
  }
  const header = ['考试名称', '类型', '考试日期', '班级', '学员', '成绩', '班级排名', '校区排名', '状态', '备注']
  const lines = rows.value.map((row) => [
    row.examName,
    row.examType,
    row.examDate ?? '',
    row.className ?? '',
    row.studentName,
    scoreText(row),
    row.rankClass ?? '',
    row.rankCampus ?? '',
    row.status,
    row.remark ?? '',
  ])
  const csv = [header, ...lines]
    .map((cells) => cells.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `成绩查询_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
  message.success(`已导出 ${rows.value.length} 条成绩`)
}

onMounted(async () => {
  await Promise.all([load(), loadOptions()])
})
</script>

<template>
  <div class="grade-page">
    <div class="filter-line">
      <a-select
        v-model:value="examId"
        size="small"
        style="width: 240px"
        placeholder="全部考试"
        allow-clear
        show-search
        :options="examSelectOptions"
        @change="load"
      />
      <a-select
        v-model:value="classId"
        size="small"
        style="width: 170px"
        placeholder="全部班级"
        allow-clear
        show-search
        :options="classSelectOptions"
        @change="load"
      />
      <a-select
        v-model:value="typeFilter"
        size="small"
        style="width: 120px"
        placeholder="全部类型"
        allow-clear
        :options="[
          { label: '周考（机构）', value: '周考' },
          { label: '月考（机构）', value: '月考' },
          { label: '学校考试', value: '学校考试' },
        ]"
        @change="load"
      />
      <a-select
        v-model:value="statusFilter"
        size="small"
        style="width: 110px"
        placeholder="全部状态"
        allow-clear
        :options="['正常', '缺考', '免考'].map((item) => ({ label: item, value: item }))"
        @change="load"
      />
      <a-input-search
        v-model:value="keyword"
        size="small"
        placeholder="搜索学员或考试名称"
        allow-clear
        style="width: 200px"
        @search="load"
        @change="!keyword && load()"
      />
      <a-button size="small" class="with-icon" @click="exportCsv">
        <DownloadOutlined />导出
      </a-button>
      <span class="filter-label">仅展示已发布的考试成绩</span>
    </div>

    <div class="summary-line">当前结果：共 {{ rows.length }} 条成绩</div>

    <a-table
      class="grade-table"
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{ pageSize: 50, showSizeChanger: false }"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <span v-if="column.dataIndex === 'examName'" class="ellipsis">{{ record.examName }}</span>
        <span v-else-if="column.dataIndex === 'examType'">
          <a-tag color="blue">{{ record.examType }}</a-tag>
        </span>
        <span v-else-if="column.dataIndex === 'examDate'">{{ record.examDate ?? '—' }}</span>
        <span v-else-if="column.dataIndex === 'className'" class="ellipsis">{{ record.className ?? '—' }}</span>
        <span v-else-if="column.dataIndex === 'score'" class="score-text">{{ scoreText(record) }}</span>
        <span v-else-if="column.dataIndex === 'rankClass'">{{ record.rankClass ?? '—' }}</span>
        <span v-else-if="column.dataIndex === 'rankCampus'">{{ record.rankCampus ?? '—' }}</span>
        <span v-else-if="column.dataIndex === 'status'">
          <span :class="record.status === '正常' ? 'status-on' : 'status-off'">{{ record.status }}</span>
        </span>
        <span v-else-if="column.dataIndex === 'remark'">{{ record.remark ?? '—' }}</span>
      </template>
      <template #emptyText>
        <a-empty description="暂无已发布成绩，请先在「成绩录入」中录入并发布" />
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.grade-page {
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

.filter-label {
  color: var(--text-2);
}

.summary-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.grade-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.grade-table :deep(.ant-table-tbody > tr > td) {
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

.score-text {
  font-weight: 600;
  color: var(--brand);
}

.status-on {
  color: var(--brand);
}

.status-off {
  color: #8f959e;
}
</style>
