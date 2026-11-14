<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { authState } from '../stores/auth'
import {
  enrollLocalOrder,
  fetchIncidentals,
  fetchLocalOrderOptions,
  fetchStudentDetail,
  fetchStudents,
  type IncidentalRow,
  type LocalEnrollMaterial,
  type LocalOrderActivityTemplate,
  type LocalOrderClass,
  type LocalOrderCourse,
  type LocalOrderEnrollment,
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
const incidentals = ref<IncidentalRow[]>([])
const activityTemplates = ref<LocalOrderActivityTemplate[]>([])

const student = computed(() => detail.value?.student ?? null)
const courses = computed(() => options.value?.courses ?? [])
const classes = computed(() => options.value?.classes ?? [])

// ---------------- 学员选择 ----------------
const studentKeyword = ref('')
const studentDialog = ref(false)
const studentRows = ref<StudentRow[]>([])
const studentSearching = ref(false)

// ---------------- 课程 / 班级选择 ----------------
const course = ref<LocalOrderCourse | null>(null)
const classId = ref<number | null>(null)
const courseDialog = ref(false)
const classDialog = ref(false)
const courseKeyword = ref('')

const availableClasses = computed(() => {
  if (!course.value) return classes.value
  return classes.value.filter((item) => item.courseId === course.value?.id)
})

// ---------------- 价格 / 有效期 ----------------
const priceTemplateId = ref<number | null>(null)
const lessons = ref<number>(1)
const unitPrice = ref<number>(0)
const expireEnabled = ref(false)
const expireDate = ref<string | null>(null)

// ---------------- 优惠 ----------------
const localActivityTemplateIds = ref<number[]>([])
const discount = ref<number>(0)
const giftLessons = ref<number>(0)

// ---------------- 教材杂费 ----------------
const materials = ref<LocalEnrollMaterial[]>([])
const materialDialog = ref(false)
const materialKeyword = ref('')

// ---------------- 经办 ----------------
const dealDate = ref<string>(today())
const payMethod = ref<string>('现金')
const receivedInput = ref<number | null>(null)
const creator = ref<string>(authState.user?.name ?? '')
const remark = ref<string>('')
const tags = ref<string>('')

const courseAmount = computed(() => round2((lessons.value || 0) * (unitPrice.value || 0)))
const materialAmount = computed(() =>
  round2(materials.value.reduce((sum, item) => sum + (item.price || 0) * (item.quantity || 0), 0)),
)
/** 应收 = 优惠前原价；应付 = 应收 - 优惠；实收必须等于应付，不支持赊账。 */
const subtotal = computed(() => round2(courseAmount.value + materialAmount.value))
const payable = computed(() => Math.max(0, round2(subtotal.value - (discount.value || 0))))
const received = computed(() => (receivedInput.value === null ? payable.value : round2(receivedInput.value)))
const totalLessons = computed(() => round2((lessons.value || 0) + (giftLessons.value || 0)))

const filteredCourses = computed(() => {
  const kw = courseKeyword.value.trim()
  if (!kw) return courses.value
  return courses.value.filter((item) => item.name.includes(kw))
})
/** 当前课程可用、且校区匹配的基础价格模板。 */
const availableTemplates = computed<LocalOrderPriceTemplate[]>(() => {
  if (!course.value) return []
  return (options.value?.priceTemplates ?? [])
    .filter((template) => template.courseIds.includes(course.value!.id))
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
const selectedPriceTemplate = computed(() =>
  availableTemplates.value.find((template) => template.id === priceTemplateId.value),
)
const eligibleActivityTemplates = computed(() => {
  const current = today()
  return activityTemplates.value.filter(
    (template) =>
      (!template.startDate || template.startDate <= current) &&
      (!template.endDate || template.endDate >= current),
  )
})
const activityTemplateOptions = computed(() =>
  eligibleActivityTemplates.value.map((template) => ({
    label: `${template.name}（${template.type}）`,
    value: template.id,
  })),
)
const filteredMaterials = computed(() => {
  const kw = materialKeyword.value.trim()
  return incidentals.value.filter((item) => (kw ? item.name.includes(kw) : true))
})

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

function money(value: number | null | undefined) {
  return `￥${Number(value ?? 0).toFixed(2)}`
}

/** 选择课程弹窗展示价：优先取价格模板的课时单价，其次课程原价，均未设置时明确提示。 */
function coursePickPrice(item: LocalOrderCourse) {
  const templates = (options.value?.priceTemplates ?? []).filter((template) =>
    template.courseIds.includes(item.id),
  )
  const template =
    templates.find((candidate) => candidate.id === item.defaultPriceTemplateId) ?? templates[0]
  const unitPrice = Number(template?.prices?.[0]?.unitPrice ?? 0)
  if (unitPrice > 0) return `${money(unitPrice)}/课时`
  if (item.listPrice && item.listPrice > 0) return money(item.listPrice)
  return '未设置价格'
}

async function loadAll() {
  if (!studentId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [detailData, optionData, materialData] = await Promise.all([
      fetchStudentDetail(studentId.value),
      fetchLocalOrderOptions(studentId.value),
      fetchIncidentals({ status: '正常', page: 1, size: 300 }),
    ])
    detail.value = detailData
    options.value = optionData
    incidentals.value = materialData.records
    activityTemplates.value = optionData.activityTemplates ?? []
    prefillFromQuery(optionData)
  } catch (e) {
    message.error((e as Error).message || '加载办理数据失败')
  } finally {
    loading.value = false
  }
}

/** 从学员详情「续费」进入时，按报读记录预填课程、班级、单价。 */
function prefillFromQuery(optionData: LocalOrderOptions) {
  const enrollmentId = numOrNull(route.query.enrollmentId)
  const courseId = numOrNull(route.query.courseId)
  let enrollment: LocalOrderEnrollment | undefined
  if (enrollmentId) {
    enrollment = optionData.enrollments.find((item) => item.id === enrollmentId)
  }
  const targetCourseId = enrollment?.courseId ?? courseId
  if (!targetCourseId) return
  const target = optionData.courses.find((item) => item.id === targetCourseId)
  if (!target) return
  selectCourse(target)
  const presetClassId = numOrNull(route.query.classId) ?? enrollment?.classId ?? null
  classId.value = presetClassId
  if (enrollment?.totalLessons) {
    lessons.value = Number(enrollment.totalLessons)
    applyPriceTemplate()
    if (enrollment.unitPrice && enrollment.unitPrice > 0) {
      unitPrice.value = enrollment.unitPrice
    }
  } else if (enrollment?.unitPrice && enrollment.unitPrice > 0) {
    unitPrice.value = enrollment.unitPrice
  }
}

function selectCourse(item: LocalOrderCourse) {
  course.value = item
  classId.value = null
  giftLessons.value = 0
  discount.value = 0
  localActivityTemplateIds.value = []
  const templates = (options.value?.priceTemplates ?? []).filter((template) =>
    template.courseIds.includes(item.id),
  )
  const preferred = item.defaultPriceTemplateId
  priceTemplateId.value =
    preferred && templates.some((template) => template.id === preferred) ? preferred : templates[0]?.id ?? null
  applyPriceTemplate()
  applyActivityTemplate()
}

/** 使用价格模板的基础课时单价；无模板时回退课程原价。 */
function applyPriceTemplate() {
  const tier = selectedPriceTemplate.value?.prices[0]
  unitPrice.value = tier ? Number(tier.unitPrice) : Number(course.value?.listPrice ?? 0)
}

function openCoursePicker() {
  if (!student.value) {
    message.warning('请先选择学员')
    return
  }
  courseKeyword.value = ''
  courseDialog.value = true
}

function chooseCourse(item: LocalOrderCourse) {
  selectCourse(item)
  courseDialog.value = false
  if (!availableClasses.value.length) {
    message.info('该课程暂无已开班班级，可先保存报名后再分班')
  }
}

function openClassPicker() {
  if (!course.value) {
    message.warning('请先选择课程')
    return
  }
  classDialog.value = true
}

function chooseClass(item: LocalOrderClass) {
  classId.value = item.id
  classDialog.value = false
}

function removeCourse() {
  course.value = null
  classId.value = null
  priceTemplateId.value = null
  unitPrice.value = 0
  lessons.value = 1
  discount.value = 0
  giftLessons.value = 0
  localActivityTemplateIds.value = []
}

// ---------------- 优惠模板算价 ----------------
function applyActivityTemplate() {
  discount.value = 0
  giftLessons.value = 0
  const selected = eligibleActivityTemplates.value.filter((item) =>
    localActivityTemplateIds.value.includes(item.id),
  )
  let discountTotal = 0
  let giftTotal = 0
  for (const template of selected) {
    if (!template.rules.length) continue
    let rule = template.rules[0]
    if (template.type === '报课优惠') {
      const matched = template.rules
        .filter((item) => lessons.value >= Number(item.lessons))
        .sort((a, b) => Number(b.lessons) - Number(a.lessons))
      rule = matched[0]
    }
    if (!rule) continue
    const value = Number(rule.discountValue) || 0
    if (rule.discountMode === '折扣') {
      discountTotal += courseAmount.value * (1 - value / 10)
    } else if (rule.discountMode === '立减') {
      discountTotal += value
    } else {
      giftTotal += value
    }
  }
  discount.value = Math.min(round2(discountTotal), courseAmount.value)
  giftLessons.value = giftTotal
}

watch([localActivityTemplateIds, courseAmount, lessons], () => applyActivityTemplate())

// ---------------- 教材杂费 ----------------
function openMaterialPicker() {
  materialKeyword.value = ''
  materialDialog.value = true
}

function addMaterial(item: IncidentalRow) {
  const exists = materials.value.find((row) => row.materialId === item.id)
  if (exists) {
    exists.quantity += 1
  } else {
    materials.value.push({ materialId: item.id, name: item.name, price: Number(item.price ?? 0), quantity: 1 })
  }
}

function addCustomMaterial() {
  materials.value.push({ materialId: null, name: '自定义教材杂费', price: 0, quantity: 1 })
  materialDialog.value = false
}

function removeMaterial(index: number) {
  materials.value.splice(index, 1)
}

// ---------------- 学员搜索 ----------------
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
  loadAll()
}
function goStudentDetail() {
  if (studentId.value) router.push(`/studentDetail/${studentId.value}`)
}

function createStudentTip() {
  message.info('新增学员请到「学员中心」办理')
}

// ---------------- 保存 ----------------
async function save() {
  if (!studentId.value || !student.value) {
    message.warning('请先选择学员')
    return
  }
  if (!course.value) {
    message.warning('请选择报名课程')
    return
  }
  if (availableClasses.value.length > 0 && !classId.value) {
    message.warning('请选择班级')
    return
  }
  if (!(lessons.value > 0)) {
    message.warning('报读课时必须大于 0')
    return
  }
  if (Math.abs(received.value - payable.value) > 0.009) {
    message.warning(`实收金额需等于应付金额 ${money(payable.value)}，不支持赊账`)
    return
  }
  saving.value = true
  try {
    const result = await enrollLocalOrder({
      studentId: studentId.value,
      campusId: student.value.campusId,
      courseId: course.value.id,
      classId: classId.value,
      lessons: lessons.value,
      giftLessons: giftLessons.value,
      unitPrice: unitPrice.value,
      discount: discount.value,
      shouldPay: subtotal.value,
      received: received.value,
      materials: materials.value,
      expireDate: expireEnabled.value ? expireDate.value : null,
      payMethod: payMethod.value,
      creator: creator.value,
      remark: remark.value,
      tags: tags.value || undefined,
      priceTemplateId: priceTemplateId.value ?? undefined,
      localActivityTemplateIds: localActivityTemplateIds.value,
    })
    message.success(`${result.typeName}成功，订单号 ${result.orderNo}`)
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
    <div class="enroll-page">
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
            <a-button type="primary" size="small" class="sm-btn" @click="createStudentTip">创建学员</a-button>
          </div>

          <div v-if="student" class="student-card">
            <div class="student-main">
              <span class="student-name">{{ student.name }}</span>
              <span class="student-sub">在读课程: {{ student.courseCount }}</span>
              <span class="student-sub">报读校区：{{ student.campusText || '—' }}</span>
              <span class="student-status">{{ student.status }}</span>
            </div>
            <a-button type="link" size="small" @click="goStudentDetail">编辑</a-button>
          </div>
        </div>
      </section>

      <!-- 报名信息 -->
      <section class="page-model">
        <div class="model-title">
          <span>报名信息</span>
          <a-button type="primary" size="small" class="sm-btn" @click="openCoursePicker">选择课程</a-button>
          <a-button type="primary" size="small" class="sm-btn" @click="openClassPicker">选择班级</a-button>
        </div>
        <div class="model-content">
          <div v-if="!course" class="empty-select">
            <div class="empty-dot">!</div>
            <span>请点击左上方按钮选择报名信息</span>
          </div>

          <div v-else class="lesson-box">
            <div class="lesson-head">
              <span class="lesson-tag">学员</span>
              <span class="lesson-name">{{ course.name }}</span>
              <a-select
                v-model:value="classId"
                class="class-select"
                placeholder="请选择"
                :options="availableClasses.map((item) => ({ label: item.name, value: item.id }))"
              />
              <span class="lesson-sum">
                共: <b>{{ totalLessons.toFixed(2) }}</b> 课时
                <span class="sum-gap">小计: <b>{{ subtotal.toFixed(2) }}</b> 元</span>
              </span>
              <a-button type="text" class="lesson-close" @click="removeCourse">×</a-button>
            </div>

            <div class="sub-block">
              <div class="sub-title">
                <span>价格</span>
                <b class="price-text">{{ money(courseAmount) }}</b>
              </div>
              <div class="sub-row price-template-row">
                <span class="hint">价格模板</span>
                <span class="template-name">{{ selectedPriceTemplate?.name || '未配置' }}</span>
                <span class="hint">优惠模板</span>
                <a-select
                  v-model:value="localActivityTemplateIds"
                  class="activity-template-select"
                  mode="multiple"
                  placeholder="可多选优惠，不使用请留空"
                  allow-clear
                  :max-tag-count="2"
                  :options="activityTemplateOptions"
                />
              </div>
              <div v-if="!selectedPriceTemplate" class="template-hint">
                该课程尚未配置价格模板，请到「办理中心 - 缴费模板」维护；暂按课程原价
              </div>
              <div class="sub-row">
                <span class="hint">单价</span>
                <a-input-number
                  v-model:value="unitPrice"
                  :min="0"
                  :precision="2"
                  class="unit-input"
                  :disabled="!!selectedPriceTemplate"
                />
                <span class="multiply">×</span>
                <a-input-number v-model:value="lessons" :min="0.5" :step="1" :precision="2" class="num-input" />
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
              <div class="sub-title">
                <span>优惠</span>
                <b class="price-text">{{ money(discount) }}</b>
                <span class="hint">可多选优惠模板，按活动规则自动叠加计算，也可手动调整</span>
              </div>
              <div class="sub-row">
                <span class="hint">优惠金额</span>
                <a-input-number v-model:value="discount" :min="0" :precision="2" class="num-input" />
                <span v-if="giftLessons > 0" class="hint">活动赠送 {{ giftLessons }} 课时</span>
              </div>
            </div>

            <div class="sub-block">
              <div class="sub-title">
                <span>教材杂费</span>
                <b class="price-text">{{ money(materialAmount) }}</b>
                <a-button type="link" size="small" class="add-material" @click="openMaterialPicker">
                  <PlusOutlined /> 新增教材
                </a-button>
              </div>
              <table class="material-table">
                <thead>
                  <tr>
                    <th style="width: 80px">领用状态</th>
                    <th>教材杂费</th>
                    <th style="width: 120px">价格</th>
                    <th style="width: 120px">购买数量</th>
                    <th style="width: 120px">总价</th>
                    <th style="width: 90px">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="!materials.length">
                    <td colspan="6" class="material-empty">暂无教材杂费</td>
                  </tr>
                  <tr v-for="(item, index) in materials" :key="`${item.materialId}-${index}`">
                    <td><a-checkbox :checked="true" /></td>
                    <td>
                      <a-input v-if="item.materialId === null" v-model:value="item.name" size="small" />
                      <span v-else>{{ item.name }}</span>
                    </td>
                    <td>
                      <a-input-number
                        v-if="item.materialId === null"
                        v-model:value="item.price"
                        :min="0"
                        :precision="2"
                        size="small"
                      />
                      <span v-else>{{ money(item.price) }}</span>
                    </td>
                    <td>
                      <a-input-number v-model:value="item.quantity" :min="1" :precision="0" size="small" />
                    </td>
                    <td>{{ money(item.price * item.quantity) }}</td>
                    <td><a-button type="link" size="small" @click="removeMaterial(index)">删除</a-button></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
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
            <div class="info-item">
              <label>实收金额</label>
              <a-input-number
                v-model:value="receivedInput"
                :min="0"
                :precision="2"
                class="info-input"
                :placeholder="payable.toFixed(2)"
              />
            </div>
            <div class="info-item">
              <label>应收合计（优惠前）</label>
              <span class="info-text">{{ money(subtotal) }}</span>
            </div>
            <div class="info-item">
              <label>应付合计</label>
              <span class="info-text">{{ money(payable) }}</span>
            </div>
            <div class="info-item">
              <label>订单标签</label>
              <a-input v-model:value="tags" class="info-input" placeholder="多个标签用逗号分隔" />
            </div>
            <div class="info-item info-item-wide">
              <label>备注</label>
              <a-input v-model:value="remark" class="info-input" placeholder="选填" />
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

    <!-- 选择课程 -->
    <a-modal v-model:open="courseDialog" title="选择课程" :footer="null" width="720px">
      <div class="picker-line">
        <a-input v-model:value="courseKeyword" placeholder="输入课程名称搜索" />
      </div>
      <div class="picker-list">
        <div v-for="item in filteredCourses" :key="item.id" class="picker-row" @click="chooseCourse(item)">
          <span class="picker-name">{{ item.name }}</span>
          <span class="picker-sub">{{ courses.length ? '' : '' }}</span>
          <span class="picker-price">{{ coursePickPrice(item) }}</span>
        </div>
        <a-empty v-if="!filteredCourses.length" description="暂无可用课程" />
      </div>
    </a-modal>

    <!-- 选择班级 -->
    <a-modal v-model:open="classDialog" title="选择班级" :footer="null" width="680px">
      <div class="picker-list">
        <div v-for="item in availableClasses" :key="item.id" class="picker-row" @click="chooseClass(item)">
          <span class="picker-name">{{ item.name }}</span>
        </div>
        <a-empty v-if="!availableClasses.length" description="该课程暂无已开班班级" />
      </div>
    </a-modal>

    <!-- 新增教材 -->
    <a-modal v-model:open="materialDialog" title="新增教材杂费" :footer="null" width="680px">
      <div class="picker-line">
        <a-input v-model:value="materialKeyword" placeholder="输入教材名称搜索" />
        <a-button type="link" @click="addCustomMaterial">自定义</a-button>
      </div>
      <div class="picker-list">
        <div v-for="item in filteredMaterials" :key="item.id" class="picker-row" @click="addMaterial(item)">
          <span class="picker-name">{{ item.name }}</span>
          <span class="picker-sub">{{ item.category || '教材杂费' }}</span>
          <span class="picker-price">{{ money(item.price) }}</span>
        </div>
        <a-empty v-if="!filteredMaterials.length" description="暂无教材杂费数据" />
      </div>
    </a-modal>
  </a-spin>
</template>

<style scoped>
.enroll-page {
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

.student-status {
  font-size: 12px;
  color: #129ca9;
  border: 1px solid #129ca9;
  border-radius: 10px;
  padding: 0 6px;
  line-height: 18px;
}

.empty-select {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #adb4bc;
  font-size: 14px;
}

.empty-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid #d9d9d9;
  color: #adb4bc;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
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

.lesson-tag {
  font-size: 12px;
  color: #007aff;
  background: #eaf5fa;
  border: 1px solid #a8ceff;
  border-radius: 4px;
  padding: 0 4px;
  line-height: 20px;
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

.sum-gap {
  margin-left: 25px;
}

.lesson-close {
  font-size: 18px;
  color: #adb4bc;
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

.add-material {
  margin-left: auto;
  padding: 0;
}

.sub-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.price-template-row {
  flex-wrap: wrap;
  row-gap: 8px;
  margin-bottom: 8px;
}

.template-name {
  width: 180px;
  font-size: 13px;
  color: #1f2329;
}

.activity-template-select {
  width: 320px;
}

.unit-input {
  width: 110px;
}

.template-hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: #fa8c16;
}

.multiply {
  color: #787f8a;
}

.num-input {
  width: 110px;
}

.expire-check {
  margin-left: 30px;
}

.date-input {
  width: 180px;
}

.hint {
  font-size: 13px;
  color: #adb4bc;
}

.material-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
  color: #30404f;
}

.material-table th,
.material-table td {
  border: 1px solid #e8e8e8;
  padding: 8px 10px;
  text-align: left;
}

.material-table th {
  background: rgba(82, 149, 231, 0.05);
  font-weight: 400;
  color: #787f8a;
}

.material-empty {
  text-align: center;
  color: #adb4bc;
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

.info-text {
  color: #30404f;
}

.info-item-wide {
  grid-column: span 2;
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

.picker-list {
  max-height: 420px;
  overflow: auto;
}

.picker-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  font-size: 14px;
  color: #30404f;
}

.picker-row:hover {
  background: #f5f9ff;
}

.picker-name {
  flex: 1;
}

.picker-sub {
  font-size: 13px;
  color: #adb4bc;
}

.picker-price {
  color: #007aff;
  font-size: 13px;
}
</style>
