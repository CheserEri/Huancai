<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RightOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import {
  fetchClassStudents,
  fetchClasses,
  upgradeClasses,
  type ClassRow,
  type ClassStudentRow,
} from '../api/client'

const route = useRoute()
const router = useRouter()

const classes = ref<ClassRow[]>([])
const sourceId = ref<number | undefined>(undefined)
const students = ref<ClassStudentRow[]>([])
const classLoading = ref(false)
const studentLoading = ref(false)
const keyword = ref('')
const checkedIds = ref<number[]>([])
const targetIds = ref<number[]>([])
const submitting = ref(false)
const dragOverId = ref<number | null>(null)

/** 学员 -> 目标班级 */
const assignment = reactive<Record<number, number>>({})
const expanded = reactive<Record<number, boolean>>({})

const sourceClass = computed(() => classes.value.find((item) => item.id === sourceId.value) ?? null)

const sourceOptions = computed(() =>
  classes.value.map((item) => ({
    value: item.id,
    label: `${item.name}（${item.enrolledCount}/${item.capacity} 人）`,
  })),
)

const targetOptions = computed(() =>
  classes.value
    .filter((item) => item.id !== sourceId.value)
    .map((item) => ({
      value: item.id,
      label: `${item.name}${item.courseName ? ' · ' + item.courseName : ''}`,
    })),
)

const targets = computed(() =>
  targetIds.value
    .map((id) => classes.value.find((item) => item.id === id))
    .filter((item): item is ClassRow => Boolean(item)),
)

const poolStudents = computed(() => {
  const key = keyword.value.trim()
  return students.value.filter((item) => {
    if (assignment[item.studentId]) return false
    if (!key) return true
    return (
      item.studentName.includes(key) ||
      (item.studentNo ?? '').includes(key) ||
      (item.phone ?? '').includes(key)
    )
  })
})

const checkedInPool = computed(() =>
  checkedIds.value.filter((id) => !assignment[id] && students.value.some((item) => item.studentId === id)),
)

function isDebt(row: ClassStudentRow) {
  return Number(row.debt ?? 0) > 0 || Number(row.remainLessons ?? 0) > 0
}

function debtText(row: ClassStudentRow) {
  const parts: string[] = []
  if (Number(row.debt ?? 0) > 0) parts.push(`欠费 ￥${Number(row.debt).toFixed(2)}`)
  if (Number(row.remainLessons ?? 0) > 0) parts.push(`剩余 ${Number(row.remainLessons)} 课时`)
  return parts.join(' · ')
}

function assignedStudents(classId: number) {
  return students.value.filter((item) => assignment[item.studentId] === classId)
}

function poolTotal() {
  return students.value.filter((item) => !assignment[item.studentId]).length
}

function debtCountInTargets() {
  return students.value.filter((item) => {
    const target = assignment[item.studentId]
    return target !== undefined && isDebt(item)
  }).length
}

function assignedTotal() {
  return students.value.filter((item) => assignment[item.studentId] !== undefined).length
}

async function loadClasses() {
  classLoading.value = true
  try {
    const res = await fetchClasses({ status: '已开班', includeClosed: false, page: 1, size: 200 })
    classes.value = res.records
    const fromQuery = Number(route.query.sourceId)
    const initial = classes.value.find((item) => item.id === fromQuery) ?? classes.value[0]
    if (initial) await onSourceChange(initial.id)
  } catch (e) {
    message.error((e as Error).message || '班级数据加载失败')
  } finally {
    classLoading.value = false
  }
}

async function onSourceChange(value: unknown) {
  const id = Number(value)
  if (!id) return
  sourceId.value = id
  students.value = []
  checkedIds.value = []
  targetIds.value = []
  Object.keys(assignment).forEach((key) => delete assignment[Number(key)])
  Object.keys(expanded).forEach((key) => delete expanded[Number(key)])
  studentLoading.value = true
  try {
    students.value = await fetchClassStudents(id)
  } catch (e) {
    message.error((e as Error).message || '学员加载失败')
  } finally {
    studentLoading.value = false
  }
}

function onTargetsChange(values: Array<number | string>) {
  const ids = values.map((item) => Number(item))
  // 取消勾选的新班级，已分配学员放回左侧
  targetIds.value
    .filter((id) => !ids.includes(id))
    .forEach((id) => {
      students.value.forEach((item) => {
        if (assignment[item.studentId] === id) delete assignment[item.studentId]
      })
      delete expanded[id]
    })
  ids.forEach((id) => {
    if (expanded[id] === undefined) expanded[id] = true
  })
  targetIds.value = ids
  checkedIds.value = checkedIds.value.filter((id) => !assignment[id])
}

function toggleExpand(classId: number) {
  expanded[classId] = !expanded[classId]
}

function toggleCheck(studentId: number) {
  if (checkedIds.value.includes(studentId)) {
    checkedIds.value = checkedIds.value.filter((id) => id !== studentId)
  } else {
    checkedIds.value = [...checkedIds.value, studentId]
  }
}

function toggleCheckAll() {
  if (checkedInPool.value.length === poolStudents.value.length) {
    checkedIds.value = []
  } else {
    checkedIds.value = poolStudents.value.map((item) => item.studentId)
  }
}

function moveCheckedTo(classId: number) {
  const ids = checkedInPool.value
  if (!ids.length) {
    message.warning('请先在左侧勾选要升班的学员')
    return
  }
  ids.forEach((id) => (assignment[id] = classId))
  checkedIds.value = []
  expanded[classId] = true
}

function removeFromTarget(studentId: number) {
  delete assignment[studentId]
}

function clearTarget(classId: number) {
  students.value.forEach((item) => {
    if (assignment[item.studentId] === classId) delete assignment[item.studentId]
  })
}

function onDragStart(event: DragEvent, studentId: number) {
  const ids = checkedInPool.value.includes(studentId) ? [...checkedInPool.value] : [studentId]
  event.dataTransfer?.setData('text/plain', JSON.stringify(ids))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function onDrop(event: DragEvent, classId: number) {
  dragOverId.value = null
  event.preventDefault()
  let ids: number[] = []
  try {
    ids = JSON.parse(event.dataTransfer?.getData('text/plain') || '[]')
  } catch {
    ids = []
  }
  ids
    .filter((id) => students.value.some((item) => item.studentId === id))
    .forEach((id) => (assignment[id] = classId))
  checkedIds.value = []
  expanded[classId] = true
}

function submit() {
  if (!sourceId.value) {
    message.warning('请先选择原班级')
    return
  }
  const payloadTargets = targets.value
    .map((item) => ({
      classId: item.id,
      studentIds: assignedStudents(item.id).map((row) => row.studentId),
    }))
    .filter((item) => item.studentIds.length > 0)
  if (!payloadTargets.length) {
    message.warning('请至少把一名学员拖入新班级')
    return
  }

  const rows = payloadTargets
    .map((item) => {
      const cls = classes.value.find((row) => row.id === item.classId)
      return `「${cls?.name ?? ''}」${item.studentIds.length} 人`
    })
    .join('，')
  const keptCount = students.value.filter((item) => isDebt(item) && assignment[item.studentId]).length

  Modal.confirm({
    title: '确认执行升班？',
    content: `从「${sourceClass.value?.name ?? ''}」升出：${rows}。${
      keptCount ? `其中 ${keptCount} 名学员欠费或仍有剩余课时，原班记录会继续保留。` : ''
    }`,
    okText: '确认升班',
    cancelText: '再看看',
    onOk: async () => {
      submitting.value = true
      try {
        const res = await upgradeClasses({
          sourceClassId: sourceId.value as number,
          targets: payloadTargets,
        })
        Modal.success({
          title: '升班完成',
          content: `已升班 ${res.moved} 名学员；原班保留 ${res.kept} 条记录，移除 ${res.removed} 条记录，原班当前 ${res.sourceEnrolled} 人。`,
          okText: '返回班级列表',
          onOk: () => router.push('/class'),
        })
      } catch (e) {
        message.error((e as Error).message || '升班失败')
      } finally {
        submitting.value = false
      }
    },
  })
}

onMounted(loadClasses)
</script>

<template>
  <div class="upgrade-page">
    <div class="page-head">
      <span class="head-label">班级</span>
      <div class="head-tabs">
        <span class="head-tab">升班</span>
      </div>
      <a class="back-link" @click="router.push('/class')">返回班级列表</a>
    </div>

    <div class="page-title">
      <div>
        <div class="title-main">班级升班</div>
        <div class="title-sub">左侧选择原班级并勾选学员，右侧选择一个或多个新班级，把学员移入对应新班级</div>
      </div>
      <div class="title-status">
        原班剩余 <b>{{ poolTotal() }}</b> 人 · 已安排 <b>{{ assignedTotal() }}</b> 人
      </div>
    </div>

    <div class="board">
      <!-- 左侧：原班级 -->
      <aside class="left-pane">
        <div class="pane-title">
          <span class="step">1</span>
          <span>选择原班级</span>
        </div>

        <a-select
          :value="sourceId"
          :options="sourceOptions"
          :loading="classLoading"
          show-search
          option-filter-prop="label"
          placeholder="请选择原班级"
          class="source-select"
          @change="onSourceChange"
        />

        <div v-if="sourceClass" class="source-meta">
          <span>{{ sourceClass.courseName || '未绑定课程' }}</span>
          <span v-if="sourceClass.teacherName">{{ sourceClass.teacherName }}</span>
          <span v-if="sourceClass.scheduleText">{{ sourceClass.scheduleText }}</span>
        </div>

        <div class="pool-toolbar">
          <a-input
            v-model:value="keyword"
            placeholder="搜索姓名 / 学号 / 电话"
            allow-clear
            size="small"
            class="pool-search"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </a-input>
        </div>

        <div class="pool-head">
          <a-checkbox
            :checked="poolStudents.length > 0 && checkedInPool.length === poolStudents.length"
            :disabled="!poolStudents.length"
            @change="toggleCheckAll"
          >
            全选
          </a-checkbox>
          <span class="pool-count">待升班 {{ poolStudents.length }} 人 · 已勾选 {{ checkedInPool.length }} 人</span>
        </div>

        <a-spin :spinning="studentLoading" class="pool-spin">
          <div class="pool-list">
            <div
              v-for="row in poolStudents"
              :key="row.studentId"
              class="stu-card"
              :class="{ active: checkedInPool.includes(row.studentId) }"
              draggable="true"
              @click="toggleCheck(row.studentId)"
              @dragstart="onDragStart($event, row.studentId)"
            >
              <a-checkbox
                :checked="checkedInPool.includes(row.studentId)"
                @click.stop
                @change="toggleCheck(row.studentId)"
              />
              <div class="stu-body">
                <div class="stu-line">
                  <span class="stu-name">{{ row.studentName }}</span>
                  <span v-if="isDebt(row)" class="stu-tag">欠费/有剩余课时</span>
                </div>
                <div class="stu-meta">
                  {{ row.studentNo || '无学号' }} · 剩余 {{ Number(row.remainLessons ?? 0) }} 课时
                </div>
                <div v-if="isDebt(row)" class="stu-debt">{{ debtText(row) }}</div>
              </div>
            </div>

            <a-empty
              v-if="!studentLoading && !poolStudents.length"
              :description="students.length ? '学员已全部安排到新班级' : '该班级暂无学员'"
            />
          </div>
        </a-spin>
      </aside>

      <!-- 中间竖线分隔 -->
      <div class="divider" />

      <!-- 右侧：新班级 -->
      <section class="right-pane">
        <div class="pane-title">
          <span class="step">2</span>
          <span>选择新班级（可多选，勾选后按列表竖排）</span>
        </div>

        <a-select
          mode="multiple"
          :value="targetIds"
          :options="targetOptions"
          :loading="classLoading"
          show-search
          option-filter-prop="label"
          placeholder="选择新班级，可选择一个或多个"
          class="target-select"
          :max-tag-count="3"
          @change="onTargetsChange"
        />

        <div class="rule-tip">
          欠费或仍有剩余课时的学员，升班后原班记录会继续保留；其余学员将移出原班。
        </div>

        <div class="target-list">
          <div
            v-for="target in targets"
            :key="target.id"
            class="target-card"
            :class="{ over: dragOverId === target.id }"
            @dragover.prevent="dragOverId = target.id"
            @dragleave="dragOverId = null"
            @drop="onDrop($event, target.id)"
          >
            <div class="target-head" @click="toggleExpand(target.id)">
              <RightOutlined class="caret" :class="{ open: expanded[target.id] }" />
              <div class="target-name">
                {{ target.name }}
                <span class="target-tag">{{ target.courseName || '未绑定课程' }}</span>
              </div>
              <div class="target-meta">
                {{ target.teacherName || '未指派' }} · {{ target.scheduleText || '未排课' }} ·
                {{ target.enrolledCount }}/{{ target.capacity }} 人
              </div>
              <div class="target-count">{{ assignedStudents(target.id).length }} 人</div>
              <a-button
                size="small"
                type="link"
                class="move-link"
                @click.stop="moveCheckedTo(target.id)"
              >
                移入选中
              </a-button>
              <a-button
                v-if="assignedStudents(target.id).length"
                size="small"
                type="link"
                danger
                class="move-link"
                @click.stop="clearTarget(target.id)"
              >
                清空
              </a-button>
            </div>

            <div v-show="expanded[target.id]" class="target-body">
              <div v-if="assignedStudents(target.id).length" class="chip-list">
                <div
                  v-for="row in assignedStudents(target.id)"
                  :key="row.studentId"
                  class="chip"
                  :class="{ debt: isDebt(row) }"
                >
                  <span>{{ row.studentName }}</span>
                  <span v-if="isDebt(row)" class="chip-flag">欠费</span>
                  <span class="chip-close" @click="removeFromTarget(row.studentId)">×</span>
                </div>
              </div>
              <div v-else class="drop-hint">把左侧勾选的学员拖到这里，或点击「移入选中」</div>
            </div>
          </div>

          <a-empty v-if="!targets.length" description="请先在上方选择新班级（可多选）" />
        </div>

        <div class="foot-bar">
          <div class="foot-stats">
            <span>待升班 <b>{{ poolTotal() }}</b> 人</span>
            <span>已安排 <b>{{ assignedTotal() }}</b> 人</span>
            <span v-if="debtCountInTargets()" class="warn">欠费/有剩余课时 <b>{{ debtCountInTargets() }}</b> 人</span>
          </div>
          <a-button type="primary" class="primary-btn" :loading="submitting" @click="submit">
            执行升班
          </a-button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.upgrade-page {
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
  height: 100%;
}

.head-tab {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  font-size: 14px;
  color: var(--brand);
  font-weight: 500;
}

.head-tab::after {
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

.back-link {
  margin-left: auto;
  font-size: 13px;
}

.page-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0 12px;
}

.title-main {
  font-size: 16px;
  font-weight: 600;
}

.title-sub {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-3);
}

.title-status {
  font-size: 13px;
  color: var(--text-2);
}

.title-status b {
  color: var(--brand);
  padding: 0 2px;
}

.board {
  display: flex;
  align-items: stretch;
  gap: 0;
  height: calc(100vh - 220px);
  min-height: 520px;
}

.left-pane {
  flex: 0 0 360px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding-right: 20px;
}

.divider {
  flex: 0 0 1px;
  background: #e3e5e8;
  align-self: stretch;
}

.right-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding-left: 20px;
}

.pane-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.step {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  display: grid;
  place-items: center;
}

.source-select,
.target-select {
  width: 100%;
}

.source-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin: 8px 0 0;
  font-size: 12.5px;
  color: var(--text-3);
}

.pool-toolbar {
  margin: 12px 0 8px;
}

.pool-search {
  width: 100%;
}

.pool-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
  font-size: 12.5px;
  color: var(--text-2);
}

.pool-count {
  color: var(--text-3);
}

.pool-spin {
  flex: 1;
  min-height: 0;
}

.pool-spin :deep(.ant-spin-nested-loading),
.pool-spin :deep(.ant-spin-container) {
  height: 100%;
}

.pool-list {
  height: 100%;
  overflow-y: auto;
  padding: 10px 4px 10px 0;
}

.stu-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  cursor: grab;
  transition: all 0.15s;
}

.stu-card:hover {
  border-color: #91caff;
}

.stu-card.active {
  border-color: var(--brand);
  background: #f2f7ff;
}

.stu-body {
  flex: 1;
  min-width: 0;
}

.stu-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stu-name {
  font-size: 13.5px;
  font-weight: 500;
}

.stu-tag {
  font-size: 11px;
  color: #d46b08;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 9px;
  padding: 0 6px;
  line-height: 16px;
}

.stu-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-3);
}

.stu-debt {
  margin-top: 2px;
  font-size: 12px;
  color: #cf1322;
}

.rule-tip {
  margin: 8px 0 10px;
  padding: 6px 10px;
  font-size: 12px;
  color: #ad6800;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 6px;
}

.target-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.target-card {
  border: 1px solid var(--line);
  border-radius: 8px;
  margin-bottom: 10px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.target-card.over {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.12);
}

.target-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  user-select: none;
}

.target-head:hover {
  background: #fafbfc;
}

.caret {
  font-size: 12px;
  color: var(--text-3);
  transition: transform 0.15s;
}

.caret.open {
  transform: rotate(90deg);
}

.target-name {
  font-size: 13.5px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.target-tag {
  font-size: 11.5px;
  font-weight: 400;
  color: var(--text-3);
  background: #f5f6f7;
  border-radius: 3px;
  padding: 0 6px;
  line-height: 18px;
}

.target-meta {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.target-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--brand);
}

.move-link {
  padding: 0;
}

.target-body {
  padding: 0 14px 12px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  font-size: 12.5px;
  border: 1px solid #b7eb8f;
  background: #f6ffed;
  border-radius: 4px;
}

.chip.debt {
  border-color: #ffbb96;
  background: #fff2e8;
}

.chip-flag {
  font-size: 11px;
  color: #d4380d;
}

.chip-close {
  cursor: pointer;
  color: var(--text-3);
  font-size: 14px;
  line-height: 1;
}

.chip-close:hover {
  color: #cf1322;
}

.drop-hint {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  padding: 18px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-3);
}

.foot-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.foot-stats {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: var(--text-2);
}

.foot-stats b {
  color: var(--text);
  padding: 0 2px;
}

.foot-stats .warn b {
  color: #d46b08;
}

.primary-btn {
  background: var(--brand);
}
</style>
