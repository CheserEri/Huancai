<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { authState } from '../stores/auth'
import {
  fetchLocalOrderOptions,
  fetchStudentDetail,
  fetchStudents,
  transferLocalOrder,
  type LocalOrderCourse,
  type LocalOrderOptions,
  type LocalOrderPriceTemplate,
  type StudentDetail,
  type StudentRow,
} from '../api/client'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const studentId = ref<number | null>(numOrNull(route.query.studentId))
const detail = ref<StudentDetail | null>(null)
const options = ref<LocalOrderOptions | null>(null)

const student = computed(() => detail.value?.student ?? null)
const courses = computed(() => options.value?.courses ?? [])
const classes = computed(() => options.value?.classes ?? [])
const activeEnrollments = computed(
  () => (detail.value?.enrollments ?? []).filter((item) => item.status === '生效'),
)

// ---------------- 学员选择 ----------------
const studentKeyword = ref('')
const studentDialog = ref(false)
const studentRows = ref<StudentRow[]>([])
const studentSearching = ref(false)

// ---------------- 转出课程 ----------------
const fromEnrollmentId = ref<number | null>(numOrNull(route.query.enrollmentId))
const fromEnrollment = computed(
  () => activeEnrollments.value.find((item) => item.id === fromEnrollmentId.value) ?? null,
)
const fromRemain = computed(() => Math.max(0, Number(fromEnrollment.value?.remainLessons ?? 0)))
const fromUnitPrice = computed(() => {
  const item = fromEnrollment.value
  if (!item) return 0
  if (Number(item.unitPrice ?? 0) > 0) return Number(item.unitPrice)
  const total = Number(item.totalLessons ?? 0)
  return total > 0 ? round2(Number(item.paidAmount ?? 0) / total) : 0
})
const originalAmount = computed(() =>
  round2(Number(fromEnrollment.value?.totalLessons ?? 0) * fromUnitPrice.value),
)
const originalDiscount = computed(() =>
  Math.max(0, round2(originalAmount.value - Number(fromEnrollment.value?.paidAmount ?? 0))),
)
const usedAmount = computed(() => Number(fromEnrollment.value?.consumedAmount ?? 0))

const transferType = ref<'全部' | '部分'>('全部')
const outLessonsInput = ref<number>(0)
const outLessons = computed(() =>
  transferType.value === '全部' ? fromRemain.value : Number(outLessonsInput.value || 0),
)
const outAmount = computed(() => round2(outLessons.value * fromUnitPrice.value))

// ---------------- 转入课程 ----------------
const toCourse = ref<LocalOrderCourse | null>(null)
const toClassId = ref<number | null>(null)
const courseDialog = ref(false)
const courseKeyword = ref('')
const campusFilter = ref<number | null>(null)

const toClasses = computed(() =>
  classes.value.filter((item) => (toCourse.value ? item.courseId === toCourse.value.id : false)),
)
const toClassOptions = computed(() => toClasses.value.map((item) => ({ label: item.name, value: item.id })))

const priceTemplateId = ref<number | null>(null)
const tierIndex = ref(0)
const inLessons = ref<number>(1)
const inUnitPrice = ref<number>(0)
const discount = ref<number>(0)
const expireEnabled = ref(false)
const expireDate = ref<string | null>(null)
const innerRemark = ref<string>('')
const outerRemark = ref<string>('')

const availableTemplates = computed<LocalOrderPriceTemplate[]>(() => {
  if (!toCourse.value) return []
  return (options.value?.priceTemplates ?? [])
    .filter((template) => template.courseIds.includes(toCourse.value!.id))
    .filter((template) => {
      if (template.campusScope !== '指定') return true
      const campusId = student.value?.campusId
      if (!campusId) return true
      const raw = template.campusIds
      const ids = Array.isArray(raw)
        ? raw
        : (raw || '').split(',').map((item) => Number(item.trim())).filter((id) => Number.isFinite(id) && id > 0)
      return ids.includes(campusId)
    })
})
const priceTemplateOptions = computed(() =>
  availableTemplates.value.map((template) => ({
    label: `${template.name}（${template.prices.length} 个档位）`,
    value: template.id,
  })),
)
const selectedTemplate = computed(() =>
  availableTemplates.value.find((template) => template.id === priceTemplateId.value),
)
const tierOptions = computed(() =>
  (selectedTemplate.value?.prices || []).map((tier, index) => ({
    label: `${Number(tier.lessons)} 课时 × ￥${Number(tier.unitPrice).toFixed(2)} = ￥${Number(
      tier.totalPrice ?? Number(tier.lessons) * Number(tier.unitPrice),
    ).toFixed(2)}`,
    value: index,
  })),
)

const filteredCourses = computed(() => {
  const kw = courseKeyword.value.trim()
  return courses.value.filter((item) => {
    if (campusFilter.value && item.campusId !== campusFilter.value) return false
    if (item.id === fromEnrollment.value?.courseId) return false
    return kw ? item.name.includes(kw) : true
  })
})

// ---------------- 金额核算 ----------------
const inAmount = computed(() =>
  Math.max(0, round2(Number(inLessons.value || 0) * Number(inUnitPrice.value || 0) - Number(discount.value || 0))),
)
const diff = computed(() => round2(outAmount.value - inAmount.value))
const surplus = computed(() => Math.max(0, diff.value))
const shortfall = computed(() => Math.max(0, -diff.value))

const refundCash = ref<number>(0)
const receivedInput = ref<number>(0)

watch(surplus, () => {
  // 应退差额默认不实退，自动计入账户剩余未用学费
  refundCash.value = 0
})
watch(shortfall, (value) => {
  receivedInput.value = value
})
watch(fromEnrollmentId, () => {
  outLessonsInput.value = fromRemain.value
})

const actualReceived = computed(() => (shortfall.value > 0 ? Number(receivedInput.value || 0) : 0))
const actualRefund = computed(() => (surplus.value > 0 ? Number(refundCash.value || 0) : 0))
const balanceAdd = computed(() => round2(Math.max(0, surplus.value - actualRefund.value)))

function onRefundChange(value: number | null) {
  const next = Math.min(Math.max(Number(value || 0), 0), surplus.value)
  refundCash.value = next
}

// ---------------- 经办 ----------------
const campusId = ref<number | null>(null)
const payMethod = ref<string>('现金')
const dealDate = ref<string>(today())
const creator = ref<string>(authState.user?.name ?? '')
const tags = ref<string>('')

// ---------------- 工具 ----------------
function numOrNull(value: unknown): number | null {
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? n : null
}

function round2(value: number) {
  return Math.round((Number(value) || 0) * 100) / 100
}

function today() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function dayText(value: string | null | undefined) {
  return value ? value.slice(0, 10) : '—'
}

function money(value: number | null | undefined) {
  return `￥${Number(value ?? 0).toFixed(2)}`
}

function num(value: number | null | undefined) {
  return Number(value ?? 0).toFixed(1)
}

function campusName(id: number | null | undefined) {
  if (!id) return '—'
  return options.value?.campuses.find((item) => item.id === id)?.name ?? '—'
}

function courseStandard(item: LocalOrderCourse) {
  const templates = (options.value?.priceTemplates ?? []).filter((template) =>
    template.courseIds.includes(item.id),
  )
  const price = Number(templates[0]?.prices?.[0]?.unitPrice ?? item.listPrice ?? 0)
  return `${money(price)}/课时`
}

function classCount(item: LocalOrderCourse) {
  return classes.value.filter((clazz) => clazz.courseId === item.id).length
}

// ---------------- 加载 ----------------
async function loadAll() {
  if (!studentId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [detailData, optionData] = await Promise.all([
      fetchStudentDetail(studentId.value),
      fetchLocalOrderOptions(studentId.value),
    ])
    detail.value = detailData
    options.value = optionData
    campusId.value = detailData.student.campusId
    payMethod.value = optionData.payMethods[0] ?? '现金'
    prefill()
  } catch (e) {
    message.error((e as Error).message || '加载转课数据失败')
  } finally {
    loading.value = false
  }
}

function prefill() {
  if (!fromEnrollmentId.value) {
    fromEnrollmentId.value = activeEnrollments.value[0]?.id ?? null
  }
  outLessonsInput.value = fromRemain.value
  const courseId = numOrNull(route.query.courseId)
  if (!toCourse.value && courseId) {
    const target = courses.value.find((item) => item.id === courseId)
    if (target) selectCourse(target)
  }
}

async function searchStudents() {
  const keyword = studentKeyword.value.trim()
  if (!keyword) {
    message.warning('请输入学员姓名或手机号')
    return
  }
  studentSearching.value = true
  try {
    const data = await fetchStudents({ keyword, page: 1, size: 20 })
    studentRows.value = data.records
    if (!data.records.length) {
      message.info('没有查到匹配的学员')
    }
    studentDialog.value = true
  } catch (e) {
    message.error((e as Error).message || '搜索学员失败')
  } finally {
    studentSearching.value = false
  }
}

function chooseStudent(row: StudentRow) {
  studentId.value = row.id
  studentDialog.value = false
  fromEnrollmentId.value = null
  removeToCourse()
  loadAll()
}

// ---------------- 转入课程选择 ----------------
function openCoursePicker(sameCampus = false) {
  if (!student.value) {
    message.warning('请先选择学员')
    return
  }
  courseKeyword.value = ''
  campusFilter.value = sameCampus ? student.value.campusId : null
  courseDialog.value = true
}

function selectCourse(item: LocalOrderCourse) {
  toCourse.value = item
  toClassId.value = null
  discount.value = 0
  expireEnabled.value = false
  expireDate.value = null
  const templates = (options.value?.priceTemplates ?? []).filter((template) =>
    template.courseIds.includes(item.id),
  )
  const preferred = item.defaultPriceTemplateId
  priceTemplateId.value =
    preferred && templates.some((template) => template.id === preferred) ? preferred : templates[0]?.id ?? null
  tierIndex.value = 0
  applyTemplate()
}

function chooseCourse(item: LocalOrderCourse) {
  selectCourse(item)
  courseDialog.value = false
}

function removeToCourse() {
  toCourse.value = null
  toClassId.value = null
  priceTemplateId.value = null
  tierIndex.value = 0
  inLessons.value = 1
  inUnitPrice.value = 0
  discount.value = 0
  expireEnabled.value = false
  expireDate.value = null
  innerRemark.value = ''
  outerRemark.value = ''
}

/** 价格模板档位带出转入课时与单价。 */
function applyTemplate() {
  const tier = selectedTemplate.value?.prices?.[tierIndex.value]
  if (tier) {
    inLessons.value = Number(tier.lessons)
    inUnitPrice.value = Number(tier.unitPrice)
  } else {
    inUnitPrice.value = Number(toCourse.value?.listPrice ?? 0)
  }
}

function onTemplateChange() {
  tierIndex.value = 0
  applyTemplate()
}

function onTierChange() {
  applyTemplate()
}

// ---------------- 保存 ----------------
async function save() {
  if (!studentId.value || !student.value) {
    message.warning('请先选择学员')
    return
  }
  if (!fromEnrollment.value) {
    message.warning('请选择转出课程')
    return
  }
  if (!toCourse.value) {
    message.warning('请选择转入课程')
    return
  }
  if (!(outLessons.value > 0)) {
    message.warning('本次转出课时必须大于 0')
    return
  }
  if (!(Number(inLessons.value) > 0)) {
    message.warning('转入课时必须大于 0')
    return
  }
  if (!(Number(inUnitPrice.value) > 0)) {
    message.warning('转入单价必须大于 0')
    return
  }
  if (surplus.value > 0 && round2(refundCash.value + balanceAdd.value) > surplus.value) {
    message.warning('实退金额与增加余额合计不能大于应退总计')
    return
  }
  saving.value = true
  try {
    const result = await transferLocalOrder({
      studentId: studentId.value,
      campusId: campusId.value ?? undefined,
      fromEnrollmentId: fromEnrollment.value.id,
      toCourseId: toCourse.value.id,
      toClassId: toClassId.value,
      transferType: transferType.value,
      lessons: outLessons.value,
      inLessons: Number(inLessons.value),
      inUnitPrice: Number(inUnitPrice.value),
      discount: Number(discount.value || 0),
      extraPay: actualReceived.value,
      refundCash: actualRefund.value,
      balanceAdd: surplus.value > 0 ? Number(balanceAdd.value || 0) : 0,
      expireDate: expireEnabled.value ? expireDate.value : null,
      payMethod: payMethod.value,
      creator: creator.value,
      remark: innerRemark.value || undefined,
      outerRemark: outerRemark.value || undefined,
      tags: tags.value || undefined,
    })
    message.success(`转课成功，订单号 ${result.orderNo}`)
    router.push(`/studentDetail/${studentId.value}`)
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <a-spin :spinning="loading">
    <div class="transfer-page">
      <!-- 学员信息 -->
      <section class="page-model">
        <div class="model-title">学员信息</div>
        <div class="model-content">
          <div class="search-line">
            <a-input
              v-model:value="studentKeyword"
              class="search-input"
              placeholder="输入学员姓名/手机号码进行搜索"
              @press-enter="searchStudents"
            >
              <template #suffix><SearchOutlined @click="searchStudents" /></template>
            </a-input>
            <a-button type="primary" size="small" class="sm-btn" @click="searchStudents">搜索</a-button>
          </div>

          <div v-if="student" class="student-card">
            <div class="student-main">
              <span class="student-name">{{ student.name }}</span>
              <span class="student-sub">
                {{ detail?.guardians?.[0]?.relation || '学员' }} {{ detail?.guardians?.[0]?.phone || student.phone }}
              </span>
              <span class="student-tag">在读课程: {{ student.courseCount }}</span>
              <span class="student-tag">报读校区: {{ student.campusText || '—' }}</span>
              <span class="student-status">{{ student.status }}</span>
            </div>
          </div>
          <a-empty v-else description="请搜索并选择学员后办理转课" />
        </div>
      </section>

      <!-- 转课信息 -->
      <section class="page-model">
        <div class="model-title">转课信息</div>
        <div class="model-content">
          <div class="transfer-line">
            <a-select
              v-model:value="fromEnrollmentId"
              class="from-select"
              placeholder="请选择转出课程"
              :options="activeEnrollments.map((item) => ({
                label: `${item.courseName}（剩余 ${Number(item.remainLessons)} 课时）`,
                value: item.id,
              }))"
            />
            <span class="arrow">→</span>
            <div v-if="toCourse" class="course-chip">
              <span>{{ toCourse.name }}</span>
              <span v-if="toCourse.campusId" class="chip-campus">（{{ campusName(toCourse.campusId) }}）</span>
              <a class="chip-remove" @click="removeToCourse">×</a>
            </div>
            <template v-else>
              <a-button type="primary" size="small" class="sm-btn" @click="openCoursePicker()">选择课程</a-button>
              <a-button size="small" class="sm-btn" @click="openCoursePicker(true)">同校选择</a-button>
            </template>
          </div>
        </div>
      </section>

      <!-- 转出课程 -->
      <section v-if="fromEnrollment" class="page-model">
        <div class="model-title">
          <span>转出课程</span>
          <a-radio-group v-model:value="transferType" size="small" class="type-radio">
            <a-radio value="全部">全部转出</a-radio>
            <a-radio value="部分">部分转出</a-radio>
          </a-radio-group>
        </div>
        <div class="model-content">
          <div class="table-wrap">
            <table class="transfer-table">
              <thead>
                <tr>
                  <th>订单号</th>
                  <th>经办日期</th>
                  <th>购买课时</th>
                  <th>有效期</th>
                  <th>原价</th>
                  <th>优惠</th>
                  <th>实收</th>
                  <th>剩余课时</th>
                  <th>已用学费</th>
                  <th>累计退出金额</th>
                  <th>本次转出课时</th>
                  <th>本次转出金额</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>BO{{ fromEnrollment.id }}</td>
                  <td>{{ dayText(fromEnrollment.enrollTime) }}</td>
                  <td>{{ num(fromEnrollment.totalLessons) }}</td>
                  <td>{{ dayText(fromEnrollment.expireDate) }}</td>
                  <td>{{ money(originalAmount) }}</td>
                  <td>{{ money(originalDiscount) }}</td>
                  <td>{{ money(fromEnrollment.paidAmount) }}</td>
                  <td>{{ num(fromEnrollment.remainLessons) }}</td>
                  <td>{{ money(usedAmount) }}</td>
                  <td>{{ money(0) }}</td>
                  <td>
                    <a-input-number
                      v-model:value="outLessonsInput"
                      class="cell-input"
                      size="small"
                      :min="0"
                      :max="fromRemain"
                      :step="1"
                      :precision="1"
                      :disabled="transferType === '全部'"
                    />
                  </td>
                  <td class="amount-cell">{{ money(outAmount) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="transfer-total">
            转出学费：<b>{{ money(outAmount) }}</b> 元
          </div>
        </div>
      </section>

      <!-- 转入课程 -->
      <section v-if="toCourse" class="page-model">
        <div class="model-title">转入课程</div>
        <div class="model-content">
          <div class="lesson-box">
            <div class="lesson-head">
              <span class="lesson-name">{{ toCourse.name }}</span>
              <a-select
                v-model:value="toClassId"
                class="class-select"
                placeholder="请选择"
                allow-clear
                :options="toClassOptions"
              />
              <span class="lesson-sum">
                共：<b>{{ Number(inLessons).toFixed(2) }}</b> 课时
                <span class="sum-gap">
                  应收学费：<b class="blue">{{ money(inAmount) }}</b> 元
                </span>
              </span>
            </div>

            <div class="sub-block">
              <div class="sub-title">
                <span>价格</span>
                <b class="price-text">{{ money(inAmount) }}</b>
              </div>
              <div class="sub-row price-template-row">
                <span class="hint">价格模板</span>
                <a-select
                  v-model:value="priceTemplateId"
                  class="template-select"
                  show-search
                  option-filter-prop="label"
                  placeholder="请选择与课程绑定的价格模板"
                  :options="priceTemplateOptions"
                  @change="onTemplateChange"
                />
                <a-select
                  v-if="tierOptions.length"
                  v-model:value="tierIndex"
                  class="tier-select"
                  :options="tierOptions"
                  @change="onTierChange"
                />
              </div>
              <div v-if="!priceTemplateOptions.length" class="template-hint">
                该课程尚未配置价格模板，请到「办理中心 - 缴费模板」维护；暂按课程原价
              </div>
              <div class="sub-row">
                <a-select
                  v-model:value="inUnitPrice"
                  class="mode-select"
                  :options="[{ label: `1课时=${Number(inUnitPrice || 0).toFixed(2)}元`, value: inUnitPrice }]"
                />
                <span class="multiply">×</span>
                <a-input-number v-model:value="inLessons" :min="0.5" :step="1" :precision="2" class="num-input" />
                <span class="hint">优惠</span>
                <a-input-number v-model:value="discount" :min="0" :precision="2" class="num-input" />
                <a-checkbox v-model:checked="expireEnabled" class="expire-check">有效期</a-checkbox>
                <a-date-picker
                  v-model:value="expireDate"
                  value-format="YYYY-MM-DD"
                  :disabled="!expireEnabled"
                  placeholder="请选择"
                  class="date-input"
                />
              </div>
            </div>

            <div class="sub-block">
              <div class="sub-row remark-row">
                <span class="hint remark-label">对内备注</span>
                <a-input v-model:value="innerRemark" placeholder="仅内部可见" class="remark-input" />
                <span class="hint remark-label">对外备注</span>
                <a-input v-model:value="outerRemark" placeholder="会打印且家长端可见" class="remark-input" />
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 支付信息 -->
      <section v-if="toCourse" class="page-model">
        <div class="model-title">支付信息</div>
        <div class="model-content pay-block">
          <div class="pay-left">
            <div class="info-item">
              <label>经办校区</label>
              <a-select
                v-model:value="campusId"
                class="info-input"
                :options="(options?.campuses ?? []).map((item) => ({ label: item.name, value: item.id }))"
              />
            </div>
          </div>
          <div class="pay-right">
            <div v-if="surplus > 0" class="pay-big is-refund">应退总计：{{ money(surplus) }}元</div>
            <div v-else-if="shortfall > 0" class="pay-big is-receive">应收总计：{{ money(shortfall) }}元</div>
            <div v-else class="pay-big">应收/应退差额：{{ money(0) }}元</div>

            <template v-if="surplus > 0">
              <div class="pay-row">
                <span class="pay-label">实退现金：</span>
                <a-input-number
                  :value="refundCash"
                  :min="0"
                  :max="surplus"
                  :precision="2"
                  class="num-input"
                  @change="onRefundChange"
                />
                <span>元</span>
              </div>
              <div class="pay-row">
                <span class="pay-label">计入账户剩余未用学费：</span>
                <b>{{ money(balanceAdd) }}元</b>
                <span class="pay-hint">（应退未实退部分，可在学员详情退费）</span>
              </div>
            </template>
            <div v-else-if="shortfall > 0" class="pay-row">
              <span class="pay-label">实收：</span>
              <a-input-number v-model:value="receivedInput" :min="0" :precision="2" class="num-input" />
              <span>元</span>
            </div>

            <div class="pay-footer">
              <span>实收总计：{{ money(actualReceived) }}元</span>
              <span>实退总计：{{ money(actualRefund) }}元</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 订单标签 -->
      <section class="page-model">
        <div class="model-title">订单标签</div>
        <div class="model-content">
          <a-input v-model:value="tags" class="tags-input" placeholder="多个标签用逗号分隔" />
        </div>
      </section>

      <!-- 经办信息 -->
      <section class="page-model">
        <div class="model-title">经办信息</div>
        <div class="model-content">
          <div class="info-grid">
            <div class="info-item">
              <label>收费方式</label>
              <a-select
                v-model:value="payMethod"
                class="info-input"
                :options="(options?.payMethods ?? []).map((item) => ({ label: item, value: item }))"
              />
            </div>
            <div class="info-item">
              <label>经办日期</label>
              <a-date-picker v-model:value="dealDate" value-format="YYYY-MM-DD" class="info-input" disabled />
            </div>
            <div class="info-item">
              <label>经办人</label>
              <a-input v-model:value="creator" class="info-input" placeholder="请输入经办人" />
            </div>
          </div>
        </div>
      </section>

      <div class="footer-bar">
        <a-button type="primary" class="save-btn" :loading="saving" @click="save">保存</a-button>
      </div>
    </div>

    <!-- 选择学员 -->
    <a-modal v-model:open="studentDialog" title="选择学员" :footer="null" width="620px">
      <div class="picker-line">
        <a-input v-model:value="studentKeyword" placeholder="输入学员姓名/手机号码" @press-enter="searchStudents" />
        <a-button type="primary" :loading="studentSearching" @click="searchStudents">搜索</a-button>
      </div>
      <a-table
        :data-source="studentRows"
        :pagination="false"
        size="small"
        row-key="id"
        :columns="[
          { title: '学员姓名', dataIndex: 'name' },
          { title: '手机号', dataIndex: 'phone' },
          { title: '校区', dataIndex: 'campusText' },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'name'">
            <a-button type="link" size="small" @click="chooseStudent(record)">{{ record.name }}</a-button>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 选择转入课程 -->
    <a-modal v-model:open="courseDialog" title="选择课程" :footer="null" width="860px">
      <div class="picker-line">
        <a-select
          v-model:value="campusFilter"
          class="campus-filter"
          allow-clear
          placeholder="全部校区"
          :options="(options?.campuses ?? []).map((item) => ({ label: item.name, value: item.id }))"
        />
        <a-input v-model:value="courseKeyword" placeholder="输入课程名称搜索" />
      </div>
      <a-table
        :data-source="filteredCourses"
        :pagination="false"
        size="small"
        row-key="id"
        :columns="[
          { title: '课程名称', dataIndex: 'name' },
          { title: '收费模式', dataIndex: 'mode' },
          { title: '学费标准', dataIndex: 'standard' },
          { title: '开班数', dataIndex: 'classCount' },
          { title: '操作', dataIndex: 'action' },
        ]"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'mode'">按课时</template>
          <template v-else-if="column.dataIndex === 'standard'">{{ courseStandard(record) }}</template>
          <template v-else-if="column.dataIndex === 'classCount'">{{ classCount(record) }}</template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-button type="link" size="small" @click="chooseCourse(record)">选择</a-button>
          </template>
        </template>
      </a-table>
    </a-modal>
  </a-spin>
</template>

<style scoped>
.transfer-page {
  min-height: 100%;
  background: #f4f6f9;
  padding-bottom: 70px;
}

.page-model {
  background: #fff;
  margin-bottom: 10px;
}

.model-title {
  display: flex;
  align-items: center;
  gap: 15px;
  height: 58px;
  padding-left: 32px;
  position: relative;
  font-size: 14px;
  color: #30404f;
}

.model-title::before {
  content: '';
  position: absolute;
  left: 20px;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 15px;
  background: #007aff;
}

.sm-btn {
  height: 24px;
  padding: 0 7px;
  font-size: 14px;
  border-radius: 4px;
}

.model-content {
  padding: 0 20px 15px;
}

.search-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 15px;
}

.search-input {
  width: 230px;
}

.student-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 12px 16px;
}

.student-main {
  display: flex;
  align-items: center;
  gap: 16px;
}

.student-name {
  font-size: 16px;
  font-weight: 600;
  color: #30404f;
}

.student-sub {
  font-size: 13px;
  color: #787f8a;
}

.student-tag {
  font-size: 12px;
  color: #007aff;
  background: #eaf5fa;
  border: 1px solid #a8ceff;
  border-radius: 4px;
  padding: 0 6px;
  line-height: 20px;
}

.student-status {
  font-size: 12px;
  color: #129ca9;
  border: 1px solid #129ca9;
  border-radius: 10px;
  padding: 0 6px;
  line-height: 18px;
}

.transfer-line {
  display: flex;
  align-items: center;
  gap: 15px;
  min-height: 40px;
}

.from-select {
  width: 300px;
}

.arrow {
  color: #adb4bc;
  font-size: 18px;
}

.course-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid #a8ceff;
  background: #eaf5fa;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 14px;
  color: #30404f;
}

.chip-campus {
  color: #787f8a;
  font-size: 12px;
}

.chip-remove {
  color: #adb4bc;
  margin-left: 4px;
}

.type-radio {
  margin-left: 10px;
}

.table-wrap {
  overflow-x: auto;
}

.transfer-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  color: #30404f;
  white-space: nowrap;
}

.transfer-table th,
.transfer-table td {
  border: 1px solid #e8e8e8;
  padding: 8px 10px;
  text-align: left;
}

.transfer-table th {
  background: rgba(82, 149, 231, 0.05);
  font-weight: 400;
  color: #787f8a;
}

.cell-input {
  width: 100px;
}

.amount-cell {
  color: #007aff;
}

.transfer-total {
  margin-top: 10px;
  text-align: right;
  font-size: 14px;
  color: #30404f;
}

.transfer-total b {
  color: #007aff;
}

.lesson-box {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
}

.lesson-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #fafbfc;
  border-bottom: 1px solid #e8e8e8;
}

.lesson-name {
  font-size: 14px;
  font-weight: 600;
  color: #30404f;
}

.class-select {
  width: 220px;
}

.lesson-sum {
  margin-left: auto;
  font-size: 14px;
  color: #30404f;
}

.lesson-sum b {
  font-weight: 600;
}

.blue {
  color: #007aff;
}

.sum-gap {
  margin-left: 25px;
}

.sub-block {
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
}

.sub-block:last-child {
  border-bottom: none;
}

.sub-title {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 10px;
  font-size: 14px;
  color: #30404f;
}

.price-text {
  color: #007aff;
  font-size: 18px;
  font-weight: 600;
}

.sub-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.price-template-row {
  margin-bottom: 8px;
}

.template-select {
  width: 280px;
}

.tier-select {
  width: 300px;
}

.template-hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: #fa8c16;
}

.mode-select {
  width: 220px;
}

.multiply {
  color: #787f8a;
}

.num-input {
  width: 110px;
}

.expire-check {
  margin-left: 20px;
}

.date-input {
  width: 180px;
}

.hint {
  font-size: 13px;
  color: #adb4bc;
}

.remark-row {
  gap: 10px;
}

.remark-label {
  flex: 0 0 auto;
}

.remark-input {
  flex: 1;
}

.pay-block {
  display: flex;
  justify-content: space-between;
  gap: 40px;
}

.pay-left {
  padding-top: 6px;
}

.pay-right {
  min-width: 360px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pay-big {
  font-size: 18px;
  font-weight: 600;
  color: #30404f;
  text-align: right;
}

.pay-big.is-refund {
  color: #fa8c16;
}

.pay-big.is-receive {
  color: #f5222d;
}

.pay-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  font-size: 14px;
  color: #30404f;
}

.pay-label {
  color: #787f8a;
}

.pay-hint {
  color: #a0a6b0;
  font-size: 12px;
}

.pay-footer {
  display: flex;
  justify-content: flex-end;
  gap: 30px;
  padding-top: 8px;
  border-top: 1px dashed #e8e8e8;
  font-size: 14px;
  color: #30404f;
}

.tags-input {
  max-width: 420px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 270px);
  gap: 12px 40px;
  padding-bottom: 5px;
}

.info-item {
  display: flex;
  align-items: center;
}

.info-item label {
  width: 60px;
  flex: 0 0 60px;
  font-size: 14px;
  color: #adb4bc;
}

.info-input {
  width: 100%;
}

.footer-bar {
  position: fixed;
  right: 20px;
  bottom: 0;
  padding: 12px 0;
  background: #fff;
  border-top: 1px solid #e8e8e8;
}

.save-btn {
  width: 64px;
  height: 32px;
  border-radius: 4px;
}

.picker-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.campus-filter {
  width: 180px;
}
</style>
