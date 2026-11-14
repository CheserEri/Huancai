<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { fetchClassCategories, fetchClassCourses } from '../api/client'

type TabKey = 'class' | 'schedule' | 'record' | 'faq'

const tabs: { key: TabKey; label: string }[] = [
  { key: 'class', label: '约课班级列表' },
  { key: 'schedule', label: '约课日程列表' },
  { key: 'record', label: '预约记录' },
  { key: 'faq', label: '约课常见问题' },
]
const tab = ref<TabKey>('class')

const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const periods = ['上午', '下午', '晚上']

const categories = ref<string[]>([])
const courses = ref<string[]>([])

function fmt(d: Date) {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}
const now = new Date()
const monthLater = new Date(now)
monthLater.setMonth(monthLater.getMonth() + 1)
const defaultRange = [fmt(now), fmt(monthLater)]

const classFilter = reactive<{
  name: string
  campus?: number
  category?: string
  course?: string
  weekday?: string
  period?: string
  status: string
}>({
  name: '',
  campus: undefined,
  category: undefined,
  course: undefined,
  weekday: undefined,
  period: undefined,
  status: '上架',
})

const scheduleFilter = reactive<{
  name: string
  category?: string
  course?: string
  weekday?: string
  period?: string
  range: string[]
  status: string
}>({
  name: '',
  category: undefined,
  course: undefined,
  weekday: undefined,
  period: undefined,
  range: [...defaultRange],
  status: '上架',
})

const recordFilter = reactive<{
  studentName: string
  campus?: number
  range?: string[]
  status?: string
}>({
  studentName: '',
  campus: undefined,
  range: undefined,
  status: undefined,
})

function resetClass() {
  classFilter.name = ''
  classFilter.campus = undefined
  classFilter.category = undefined
  classFilter.course = undefined
  classFilter.weekday = undefined
  classFilter.period = undefined
  classFilter.status = '上架'
}

function resetSchedule() {
  scheduleFilter.name = ''
  scheduleFilter.category = undefined
  scheduleFilter.course = undefined
  scheduleFilter.weekday = undefined
  scheduleFilter.period = undefined
  scheduleFilter.range = [...defaultRange]
  scheduleFilter.status = '上架'
}

function resetRecord() {
  recordFilter.studentName = ''
  recordFilter.campus = undefined
  recordFilter.range = undefined
  recordFilter.status = undefined
}

function needSchoolpal(what: string) {
  message.info(`「${what}」需在校宝端操作，本系统仅同步展示`)
}

const faqs = [
  '约课功能操作指南？',
  '如何下架约课班级？',
  '为什么学员在家长端看不到可以预约的班级日程？',
  '为什么有班级没法添加成约课班级？',
]

const classColumns = [
  { title: '班级名称', dataIndex: 'className', width: 200 },
  { title: '所属课程', dataIndex: 'courseName', width: 200 },
  { title: '开班校区', dataIndex: 'campusName', width: 160 },
  { title: '开班日期', dataIndex: 'startDate', width: 120 },
  { title: '上课时间', dataIndex: 'scheduleText', width: 180 },
  { title: '约课状态', dataIndex: 'status', width: 100 },
]

const scheduleColumns = [
  { title: '班级名称', dataIndex: 'className', width: 180 },
  { title: '所属课程', dataIndex: 'courseName', width: 160 },
  { title: '教师', dataIndex: 'teacherName', width: 100 },
  { title: '教室', dataIndex: 'room', width: 100 },
  { title: '满班人数', dataIndex: 'capacity', width: 100 },
  { title: '上课学员', dataIndex: 'studentCount', width: 100 },
  { title: '日程状态', dataIndex: 'status', width: 100 },
  { title: '上课日期', dataIndex: 'lessonDate', width: 120 },
  { title: '时间段', dataIndex: 'timeText', width: 140 },
]

const recordColumns = [
  { title: '学员姓名', dataIndex: 'studentName', width: 120 },
  { title: '上课时间', dataIndex: 'lessonTime', width: 180 },
  { title: '课程名称', dataIndex: 'courseName', width: 160 },
  { title: '班级名称', dataIndex: 'className', width: 160 },
  { title: '开班校区', dataIndex: 'campusName', width: 160 },
  { title: '预约状态', dataIndex: 'status', width: 120 },
  { title: '创建时间', dataIndex: 'createdAt', width: 160 },
]

onMounted(async () => {
  try {
    const [cates, courseList] = await Promise.all([fetchClassCategories(), fetchClassCourses()])
    categories.value = cates
    courses.value = courseList
  } catch (e) {
    console.error(e)
  }
})
</script>

<template>
  <div class="booking-page">
    <div class="page-head">
      <span class="head-label">约课</span>
      <div class="head-tabs">
        <div
          v-for="item in tabs"
          :key="item.key"
          class="head-tab"
          :class="{ active: tab === item.key }"
          @click="tab = item.key"
        >
          {{ item.label }}
        </div>
      </div>
    </div>

    <div class="sync-note">
      当前暂无可预约班级、约课日程与预约记录（核对日期 {{ defaultRange[0] }}）。
    </div>

    <!-- 约课班级列表 -->
    <template v-if="tab === 'class'">
      <div class="action-row">
        <a-button type="primary" class="primary-btn" @click="needSchoolpal('添加约课班级')">添加约课班级</a-button>
      </div>

      <div class="filter-line">
        <span class="filter-label">班级名称</span>
        <a-input
          v-model:value="classFilter.name"
          class="name-input-inline"
          placeholder="请输入"
          allow-clear
          :bordered="false"
          size="small"
        >
          <template #suffix>
            <SearchOutlined class="search-icon" @click="needSchoolpal('搜索')" />
          </template>
        </a-input>

        <span class="filter-divider" />

        <span class="filter-label">校区</span>
        <a-select v-model:value="classFilter.campus" class="plain-select" placeholder="" allow-clear :bordered="false" size="small" style="width: 130px">
          <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
          <a-select-option :value="2">新民幻彩国学堂</a-select-option>
        </a-select>

        <a-select v-model:value="classFilter.category" class="plain-select" placeholder="课程类别" allow-clear :bordered="false" size="small" style="width: 110px">
          <a-select-option v-for="item in categories" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <span class="filter-divider" />

        <a-select v-model:value="classFilter.course" class="plain-select" placeholder="课程" allow-clear :bordered="false" size="small" style="width: 100px">
          <a-select-option v-for="item in courses" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <span class="filter-divider" />

        <a-select v-model:value="classFilter.weekday" class="plain-select" placeholder="上课星期" allow-clear :bordered="false" size="small" style="width: 106px">
          <a-select-option v-for="item in weekdays" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <a-select v-model:value="classFilter.period" class="plain-select" placeholder="上课时间段" allow-clear :bordered="false" size="small" style="width: 116px">
          <a-select-option v-for="item in periods" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <span class="filter-divider" />

        <a-select v-model:value="classFilter.status" class="plain-select" :bordered="false" size="small" style="width: 100px">
          <a-select-option value="上架">上架</a-select-option>
          <a-select-option value="下架">下架</a-select-option>
        </a-select>

        <span class="filter-spacer" />

        <a class="reset-link" @click="resetClass">清空筛选</a>
      </div>

      <div class="toolbar-row">
        <span class="filter-chip">约课状态：{{ classFilter.status }}</span>
        <a-button size="small" class="bulk-btn" @click="needSchoolpal('批量上架')">批量上架</a-button>
        <a-button size="small" class="bulk-btn" @click="needSchoolpal('批量下架')">批量下架</a-button>
      </div>

      <div class="result-line">当前结果：共计 <b>0</b> 个班</div>

      <a-table
        class="booking-table"
        :columns="classColumns"
        :data-source="[]"
        :pagination="false"
        row-key="id"
        size="middle"
      >
        <template #emptyText>
          <div class="empty-wrap">
            <a-empty description="暂无可预约班级" />
          </div>
        </template>
      </a-table>
    </template>

    <!-- 约课日程列表 -->
    <template v-else-if="tab === 'schedule'">
      <div class="filter-line schedule-filter">
        <span class="filter-label">班级名称</span>
        <a-input
          v-model:value="scheduleFilter.name"
          class="name-input-inline"
          placeholder="请输入"
          allow-clear
          :bordered="false"
          size="small"
        >
          <template #suffix>
            <SearchOutlined class="search-icon" @click="needSchoolpal('搜索')" />
          </template>
        </a-input>

        <a-select v-model:value="scheduleFilter.category" class="plain-select" placeholder="课程类别" allow-clear :bordered="false" size="small" style="width: 110px">
          <a-select-option v-for="item in categories" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <span class="filter-divider" />

        <a-select v-model:value="scheduleFilter.course" class="plain-select" placeholder="课程" allow-clear :bordered="false" size="small" style="width: 100px">
          <a-select-option v-for="item in courses" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <span class="filter-divider" />

        <a-select v-model:value="scheduleFilter.weekday" class="plain-select" placeholder="上课星期" allow-clear :bordered="false" size="small" style="width: 106px">
          <a-select-option v-for="item in weekdays" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <a-select v-model:value="scheduleFilter.period" class="plain-select" placeholder="上课时间段" allow-clear :bordered="false" size="small" style="width: 116px">
          <a-select-option v-for="item in periods" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <span class="filter-divider" />

        <span class="filter-label">上课日期</span>
        <a-range-picker
          v-model:value="scheduleFilter.range"
          value-format="YYYY-MM-DD"
          size="small"
          :bordered="false"
          class="date-range"
          style="width: 230px"
        />

        <span class="filter-divider" />

        <a-select v-model:value="scheduleFilter.status" class="plain-select" :bordered="false" size="small" style="width: 120px">
          <a-select-option value="上架">上架</a-select-option>
          <a-select-option value="下架">下架</a-select-option>
        </a-select>

        <span class="filter-spacer" />

        <a class="reset-link" @click="resetSchedule">清空筛选</a>
      </div>

      <div class="toolbar-row">
        <span class="filter-chip">上课日期：{{ scheduleFilter.range[0] }} ~ {{ scheduleFilter.range[1] }}</span>
        <span class="filter-chip">约课日程状态：{{ scheduleFilter.status }}</span>
        <a-button size="small" class="bulk-btn" @click="needSchoolpal('批量上架')">批量上架</a-button>
        <a-button size="small" class="bulk-btn" @click="needSchoolpal('批量下架')">批量下架</a-button>
      </div>

      <div class="result-line">当前结果：共计 <b>0</b> 个日程</div>

      <a-table
        class="booking-table"
        :columns="scheduleColumns"
        :data-source="[]"
        :pagination="false"
        row-key="id"
        size="middle"
      >
        <template #emptyText>
          <div class="empty-wrap">
            <a-empty description="暂无约课日程" />
          </div>
        </template>
      </a-table>
    </template>

    <!-- 预约记录 -->
    <template v-else-if="tab === 'record'">
      <div class="filter-line">
        <span class="filter-label">学员姓名</span>
        <a-input
          v-model:value="recordFilter.studentName"
          class="name-input-inline"
          placeholder="请输入"
          allow-clear
          :bordered="false"
          size="small"
        >
          <template #suffix>
            <SearchOutlined class="search-icon" @click="needSchoolpal('搜索')" />
          </template>
        </a-input>

        <span class="filter-divider" />

        <span class="filter-label">校区</span>
        <a-select v-model:value="recordFilter.campus" class="plain-select" placeholder="" allow-clear :bordered="false" size="small" style="width: 130px">
          <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
          <a-select-option :value="2">新民幻彩国学堂</a-select-option>
        </a-select>

        <span class="filter-divider" />

        <span class="filter-label">上课日期</span>
        <a-range-picker
          v-model:value="recordFilter.range"
          value-format="YYYY-MM-DD"
          size="small"
          :bordered="false"
          class="date-range"
          style="width: 220px"
        />

        <span class="filter-divider" />

        <a-select v-model:value="recordFilter.status" class="plain-select" placeholder="约课状态" allow-clear :bordered="false" size="small" style="width: 110px">
          <a-select-option value="已预约">已预约</a-select-option>
          <a-select-option value="已取消">已取消</a-select-option>
          <a-select-option value="已失效">已失效</a-select-option>
        </a-select>

        <span class="filter-spacer" />

        <a class="reset-link" @click="resetRecord">清空筛选</a>
      </div>

      <div class="result-line">当前结果：共<b>0</b>条记录</div>

      <a-table
        class="booking-table"
        :columns="recordColumns"
        :data-source="[]"
        :pagination="false"
        row-key="id"
        size="middle"
      >
        <template #emptyText>
          <div class="empty-wrap">
            <a-empty description="暂无预约记录" />
          </div>
        </template>
      </a-table>
    </template>

    <!-- 约课常见问题 -->
    <template v-else>
      <div class="faq-panel">
        <div class="faq-title">常见问题</div>
        <a-collapse ghost expand-icon-position="right" class="faq-collapse">
          <a-collapse-panel v-for="item in faqs" :key="item" :header="item">
            <div class="faq-answer">
              暂无该问题的解答
            </div>
          </a-collapse-panel>
        </a-collapse>
      </div>
    </template>
  </div>
</template>

<style scoped>
.booking-page {
  min-height: 100%;
  background: #fff;
  padding: 0 24px 20px;
}

.page-head {
  height: 54px;
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 0 -24px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line);
}

.head-label {
  font-size: 13px;
  color: var(--text-3);
  padding-right: 16px;
  border-right: 1px solid var(--line);
  line-height: 20px;
}

.head-tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 100%;
}

.head-tab {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
}

.head-tab.active {
  color: var(--brand);
  font-weight: 500;
}

.head-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 56px;
  height: 2px;
  transform: translateX(-50%);
  background: var(--brand);
  border-radius: 1px;
}

.sync-note {
  margin-top: 12px;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-2);
  background: #f7f8fa;
  border: 1px solid #f0f1f2;
  border-radius: 4px;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0 12px;
}

.primary-btn {
  background: var(--brand);
}

.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  padding: 2px 0 10px;
}

.filter-label {
  font-size: 13px;
  color: var(--text-2);
  white-space: nowrap;
}

.filter-divider {
  width: 1px;
  height: 14px;
  background: var(--line);
}

.name-input-inline {
  width: 120px;
  border-bottom: 1px solid #e5e6eb;
  padding-left: 0;
}

.name-input-inline :deep(input) {
  font-size: 13px;
}

.search-icon {
  color: var(--text-3);
  cursor: pointer;
}

.plain-select {
  font-size: 13px;
  color: var(--text-2);
}

.plain-select :deep(.ant-select-selector) {
  padding-left: 0;
  padding-right: 18px;
}

.plain-select :deep(.ant-select-selection-placeholder) {
  color: var(--text-2);
  padding-left: 0;
}

.date-range {
  font-size: 13px;
}

.date-range :deep(.ant-picker-input > input) {
  font-size: 13px;
}

.filter-spacer {
  flex: 1;
  min-width: 12px;
}

.reset-link {
  padding: 0;
  font-size: 13px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0 12px;
}

.filter-chip {
  display: inline-block;
  padding: 0 10px;
  height: 24px;
  line-height: 24px;
  font-size: 12px;
  color: var(--text-2);
  background: #f2f3f5;
  border-radius: 2px;
  white-space: nowrap;
}

.bulk-btn {
  color: var(--brand);
  border-color: #e5e6eb;
}

.result-line {
  padding: 2px 0 10px;
  font-size: 13px;
  color: var(--text-2);
}

.result-line b {
  color: var(--text);
  font-weight: 600;
  padding: 0 2px;
}

.booking-table :deep(.ant-table-thead > tr > th) {
  background: #fafbfc;
  color: var(--text);
  font-size: 13px;
  font-weight: 500;
  padding: 10px 8px;
  border-right: 1px solid #f0f1f2;
}

.booking-table :deep(.ant-table-tbody > tr > td) {
  padding: 10px 8px;
  font-size: 13px;
}

.empty-wrap {
  padding: 48px 0 56px;
}

.empty-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
}

.faq-panel {
  padding: 20px 0;
}

.faq-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 8px;
}

.faq-collapse :deep(.ant-collapse-header) {
  font-size: 13px;
  color: var(--brand);
  padding-left: 0;
}

.faq-answer {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.8;
  padding-left: 12px;
}
</style>
