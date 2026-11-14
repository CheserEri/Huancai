<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  createMaterialPurchase,
  enrollLocalOrder,
  exchangePointsGoods,
  fetchLocalOrderOptions,
  fetchMaterialOptions,
  fetchMaterialSettings,
  fetchPointsGoods,
  fetchStudents,
  rechargeLocalOrder,
  refundLocalOrder,
  subsidyLocalOrder,
  transferLocalOrder,
  type LocalOrderMode,
  type LocalOrderOptions,
  type LocalOrderPriceTemplate,
  type MaterialSettingRow,
  type PointsGoodsRow,
  type StudentRow,
} from '../api/client'

const props = defineProps<{
  open: boolean
  mode: LocalOrderMode
  creator?: string
  /** 从学员详情进入时预设学员，无需再次搜索。 */
  presetStudent?: {
    id: number
    name: string
    phone?: string | null
    points?: number | null
    balance?: number | null
    debt?: number | null
    campusId?: number | null
  }
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'saved', orderNo: string): void
}>()

const MODE_TITLE: Record<LocalOrderMode, string> = {
  enroll: '报名 / 续费',
  recharge: '余额充值',
  subsidy: '补费',
  refund: '退费',
  transfer: '转课',
  material: '教材杂费',
  points: '积分兑换',
}

const form = reactive({
  studentId: undefined as number | undefined,
  campusId: undefined as number | undefined,
  courseId: undefined as number | undefined,
  classId: undefined as number | undefined,
  lessons: undefined as number | undefined,
  giftLessons: undefined as number | undefined,
  shouldPay: undefined as number | undefined,
  received: undefined as number | undefined,
  priceTemplateId: undefined as number | undefined,
  localActivityTemplateIds: [] as number[],
  tierIndex: 0,
  amount: undefined as number | undefined,
  enrollmentId: undefined as number | undefined,
  fromEnrollmentId: undefined as number | undefined,
  toCourseId: undefined as number | undefined,
  cutLessons: 0 as number | undefined,
  extraPay: undefined as number | undefined,
  payMethod: '现金' as string | undefined,
  remark: '',
  reason: '',
  tags: '',
  roomName: undefined as string | undefined,
  materialName: undefined as string | undefined,
  goodsId: undefined as number | undefined,
  quantity: 1 as number | undefined,
  unitPrice: null as number | null,
  useBalance: false,
  balanceAmount: null as number | null,
})

const options = ref<LocalOrderOptions>({
  campuses: [],
  courses: [],
  classes: [],
  payMethods: [],
  enrollments: [],
  activityTemplates: [],
  priceTemplates: [],
})
const students = ref<StudentRow[]>([])
const studentLoading = ref(false)
const submitting = ref(false)
const receivedTouched = ref(false)
const cutLessonsTouched = ref(false)

let searchTimer: ReturnType<typeof setTimeout> | undefined
let optionsSeq = 0

const title = computed(() => MODE_TITLE[props.mode])

const campusOptions = computed(() =>
  options.value.campuses.map((campus) => ({ label: campus.name, value: campus.id })),
)

const courseOptions = computed(() =>
  options.value.courses
    .filter((course) => props.mode !== 'transfer' || course.id !== fromCourseId.value)
    .map((course) => ({ label: course.name, value: course.id })),
)

const classOptions = computed(() =>
  options.value.classes
    .filter((clazz) => !form.courseId || clazz.courseId === form.courseId)
    .map((clazz) => ({ label: clazz.name, value: clazz.id })),
)

const enrollmentOptions = computed(() =>
  options.value.enrollments.map((enrollment) => ({
    label:
      `${enrollment.courseName || '未知课程'}｜剩余 ${Number(enrollment.remainLessons)} 课时` +
      (Number(enrollment.arrears || 0) > 0 ? `｜待补 ￥${Number(enrollment.arrears).toFixed(2)}` : '') +
      (enrollment.status === '生效' ? '' : `（${enrollment.status}）`),
    value: enrollment.id,
    disabled: enrollment.status !== '生效',
  })),
)

const studentOptions = computed(() =>
  students.value.map((student) => ({
    label: `${student.name}${student.phone ? `（${student.phone}）` : ''}`,
    value: student.id,
  })),
)

const selectedStudent = computed(() => students.value.find((item) => item.id === form.studentId))

/** 教材杂费 / 积分兑换：教材清单与出库校区。 */
const materials = ref<MaterialSettingRow[]>([])
const materialRooms = ref<string[]>([])
const materialLoading = ref(false)

/** 积分兑换物（积分口径，与金额无关）。 */
const pointsGoods = ref<PointsGoodsRow[]>([])

const isMaterialMode = computed(() => props.mode === 'material' || props.mode === 'points')

const materialOptionsList = computed(() =>
  materials.value
    .filter((item) => item.status === '正常')
    .map((item) => ({
      label: `${item.name}（￥${Number(item.salePrice ?? 0).toFixed(2)}）`,
      value: item.name,
    })),
)

const pointsGoodsOptions = computed(() =>
  pointsGoods.value
    .filter((item) => item.status === '正常')
    .map((item) => ({
      label: `${item.name}（${Number(item.pointsValue ?? 0).toLocaleString('zh-CN')} 积分｜库存 ${item.stock}）`,
      value: item.id,
    })),
)

const selectedMaterial = computed(() => materials.value.find((item) => item.name === form.materialName) ?? null)

const selectedPointsGoods = computed(
  () => pointsGoods.value.find((item) => item.id === form.goodsId) ?? null,
)

const materialRoomOptions = computed(() => materialRooms.value.map((item) => ({ label: item, value: item })))

const materialBalance = computed(() => Math.max(0, Number(selectedStudent.value?.balance ?? 0)))

const materialTotal = computed(() => {
  const unit = Number(form.unitPrice ?? 0)
  const quantity = Number(form.quantity ?? 0)
  return Number((unit * quantity).toFixed(2))
})

const pointsNeeded = computed(() => {
  const unit = Number(selectedPointsGoods.value?.pointsValue ?? 0)
  const quantity = Number(form.quantity ?? 0)
  return Math.max(0, Math.round(unit * quantity))
})

const fromEnrollment = computed(() =>
  options.value.enrollments.find((item) => item.id === form.fromEnrollmentId),
)

const subsidyEnrollment = computed(() =>
  options.value.enrollments.find((item) => item.id === form.enrollmentId),
)

const subsidyArrears = computed(() => Number(subsidyEnrollment.value?.arrears || 0))

const fromCourseId = computed(() => fromEnrollment.value?.courseId ?? undefined)

const selectedCourse = computed(() =>
  options.value.courses.find((item) => item.id === form.courseId),
)

/** 当前课程可用、且校区匹配的价格模板。 */
const availableTemplates = computed<LocalOrderPriceTemplate[]>(() =>
  options.value.priceTemplates
    .filter((template) => !form.courseId || template.courseIds.includes(form.courseId))
    .filter((template) => {
      if (template.campusScope !== '指定' || !form.campusId) return true
      const raw = template.campusIds
      const ids = Array.isArray(raw)
        ? raw
        : (raw || '').split(',').map((item) => Number(item.trim())).filter((id) => Number.isFinite(id) && id > 0)
      return ids.includes(form.campusId)
    }),
)

const priceTemplateOptions = computed(() =>
  availableTemplates.value.map((template) => ({
    label: `${template.name}（￥${Number(template.prices[0]?.unitPrice ?? 0).toFixed(2)}/课时）`,
    value: template.id,
  })),
)

const selectedPriceTemplate = computed(() =>
  availableTemplates.value.find((template) => template.id === form.priceTemplateId),
)

const selectedTier = computed(() => selectedPriceTemplate.value?.prices[form.tierIndex ?? 0])

/** 活动优惠：与新建报名页保持同一套算价规则，支持多选叠加。 */
const selectedActivities = computed(() =>
  options.value.activityTemplates.filter((item) => form.localActivityTemplateIds.includes(item.id)),
)

const courseAmount = computed(() =>
  round2(Number(form.lessons || 0) * Number(selectedTier.value?.unitPrice ?? 0)),
)

const activityDiscount = ref(0)
const activityGiftLessons = ref(0)

function round2(value: number) {
  return Math.round((Number(value) || 0) * 100) / 100
}

function applyActivityPricing() {
  activityDiscount.value = 0
  activityGiftLessons.value = 0
  let discountTotal = 0
  let giftTotal = 0
  for (const template of selectedActivities.value) {
    if (!template.rules?.length) continue
    let rule = template.rules[0]
    if (template.type === '报课优惠') {
      const matched = template.rules
        .filter((item) => Number(form.lessons || 0) >= Number(item.lessons))
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
  activityDiscount.value = Math.min(round2(discountTotal), courseAmount.value)
  activityGiftLessons.value = giftTotal
  const materialFee = Number(selectedPriceTemplate.value?.materialFee ?? 0)
  // 应收为优惠前原价，实收默认等于应付（应收 - 优惠）。
  form.shouldPay = Math.max(round2(courseAmount.value + materialFee), 0)
  if (!receivedTouched.value) {
    form.received = Math.max(round2(form.shouldPay - activityDiscount.value), 0)
  }
  form.giftLessons = activityGiftLessons.value > 0 ? activityGiftLessons.value : undefined
}

watch([courseAmount, () => form.localActivityTemplateIds], applyActivityPricing, { deep: true })

const tierOptions = computed(() =>
  (selectedPriceTemplate.value?.prices || []).map((tier, index) => ({
    label: `${Number(tier.lessons)} 课时 × ￥${Number(tier.unitPrice).toFixed(2)} = ￥${Number(
      tier.totalPrice ?? Number(tier.lessons) * Number(tier.unitPrice),
    ).toFixed(2)}`,
    value: index,
  })),
)

const activityOptions = computed(() =>
  options.value.activityTemplates.map((template) => ({
    label: `${template.name}（${template.type}）`,
    value: template.id,
  })),
)

function resetForm() {
  form.studentId = undefined
  form.campusId = undefined
  form.courseId = undefined
  form.classId = undefined
  form.lessons = undefined
  form.giftLessons = undefined
  form.shouldPay = undefined
  form.received = undefined
  form.priceTemplateId = undefined
  form.localActivityTemplateIds = []
  form.tierIndex = 0
  form.amount = undefined
  form.enrollmentId = undefined
  form.fromEnrollmentId = undefined
  form.toCourseId = undefined
  form.cutLessons = 0
  form.extraPay = undefined
  form.payMethod = '现金'
  form.remark = ''
  form.reason = ''
  form.tags = ''
  form.roomName = undefined
  form.materialName = undefined
  form.goodsId = undefined
  form.quantity = 1
  form.unitPrice = null
  form.useBalance = false
  form.balanceAmount = null
  receivedTouched.value = false
  cutLessonsTouched.value = false
  students.value = []
  options.value = {
    campuses: [],
    courses: [],
    classes: [],
    payMethods: [],
    enrollments: [],
    activityTemplates: [],
    priceTemplates: [],
  }
}

async function loadOptions(studentId?: number) {
  const seq = ++optionsSeq
  try {
    const data = await fetchLocalOrderOptions(studentId)
    if (seq !== optionsSeq) return
    options.value = data
    if (!form.payMethod) {
      form.payMethod = data.payMethods[0]
    }
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载办理选项失败')
  }
}

async function loadMaterials() {
  materialLoading.value = true
  try {
    if (props.mode === 'points') {
      pointsGoods.value = await fetchPointsGoods({ status: '正常' })
      return
    }
    const [settings, materialOptions] = await Promise.all([
      fetchMaterialSettings({ status: '正常', size: 200 }),
      fetchMaterialOptions(),
    ])
    materials.value = settings.records
    materialRooms.value = materialOptions.rooms
    if (!form.roomName) form.roomName = materialOptions.rooms[0]
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载教材选项失败')
  } finally {
    materialLoading.value = false
  }
}

function onMaterialChange() {
  form.unitPrice = props.mode === 'material' ? Number(selectedMaterial.value?.salePrice ?? 0) : null
}

function onStudentSearch(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  const text = keyword.trim()
  if (!text) {
    students.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    studentLoading.value = true
    try {
      const page = await fetchStudents({ keyword: text, size: 20 })
      students.value = page.records
    } catch (error) {
      message.error(error instanceof Error ? error.message : '搜索学员失败')
    } finally {
      studentLoading.value = false
    }
  }, 250)
}

async function onStudentChange(studentId: number) {
  const student = students.value.find((item) => item.id === studentId)
  form.campusId = student?.campusId ?? form.campusId
  form.enrollmentId = undefined
  form.fromEnrollmentId = undefined
  if (!isMaterialMode.value) await loadOptions(studentId)
}

function onCourseChange() {
  form.classId = undefined
  form.localActivityTemplateIds = []
  form.tierIndex = 0
  form.lessons = undefined
  form.giftLessons = undefined
  receivedTouched.value = false
  const preferred = selectedCourse.value?.defaultPriceTemplateId
  const templates = availableTemplates.value
  form.priceTemplateId =
    preferred && templates.some((template) => template.id === preferred) ? preferred : templates[0]?.id
  applyTier()
}

function onPriceTemplateChange() {
  form.tierIndex = 0
  receivedTouched.value = false
  applyTier()
}

function onTierChange() {
  receivedTouched.value = false
  applyTier()
}

/** 选中课时档位后自动带出课时数与应收金额。 */
function applyTier() {
  const tier = selectedTier.value
  if (tier) {
    form.lessons = Number(tier.lessons)
  }
  prefillAmount()
}

function onSubsidyEnrollmentChange() {
  if (subsidyArrears.value > 0) {
    form.amount = subsidyArrears.value
  }
}

function prefillAmount() {
  if (receivedTouched.value) return
  const lessons = Number(form.lessons ?? 0)
  const tier = selectedTier.value
  let total = 0
  if (tier) {
    const unitPrice = Number(tier.unitPrice ?? 0)
    total =
      lessons > 0 && lessons !== Number(tier.lessons)
        ? lessons * unitPrice
        : Number(tier.totalPrice || Number(tier.lessons) * unitPrice)
    total += Number(selectedPriceTemplate.value?.materialFee ?? 0)
  } else {
    const price = selectedCourse.value?.listPrice
    if (price == null || !lessons) return
    total = Number(price) * lessons
  }
  const rounded = Math.round(total * 100) / 100
  form.shouldPay = rounded
  if (!receivedTouched.value) {
    form.received = Math.max(Math.round((rounded - activityDiscount.value) * 100) / 100, 0)
  }
}

function onLessonsChange() {
  prefillAmount()
}

function onEnrollmentChange() {
  if (!cutLessonsTouched.value) {
    form.cutLessons = 0
  }
}

function onFromEnrollmentChange() {
  const remain = Number(fromEnrollment.value?.remainLessons ?? 0)
  if (form.lessons == null || Number(form.lessons) > remain) {
    form.lessons = remain > 0 ? remain : undefined
  }
  if (form.toCourseId === fromCourseId.value) {
    form.toCourseId = undefined
  }
}

function close() {
  emit('update:open', false)
}

function fail(text: string) {
  message.warning(text)
}

/** 教材杂费（现金/余额抵扣）与积分兑换的提交。 */
async function submitMaterial() {
  if (props.mode === 'points') {
    await submitPointsExchange()
    return
  }
  if (!form.materialName) return fail('请选择教材')
  if (!form.roomName) return fail('请选择出库校区')
  const quantity = Number(form.quantity ?? 0)
  if (!(quantity > 0)) return fail('数量必须大于 0')
  submitting.value = true
  try {
    const unitPrice = form.unitPrice === null ? undefined : Number(form.unitPrice)
    const total = Number(((unitPrice ?? 0) * quantity).toFixed(2))
    let balanceAmount = 0
    if (form.useBalance) {
      balanceAmount = Number(form.balanceAmount ?? 0)
      if (balanceAmount < 0) return fail('抵扣金额不能为负数')
      if (balanceAmount > materialBalance.value) {
        return fail(`账户剩余未用学费不足，当前可用 ￥${materialBalance.value.toFixed(2)}`)
      }
      if (balanceAmount > total) return fail(`抵扣金额不能大于本次教材金额 ￥${total.toFixed(2)}`)
    }
    const res = await createMaterialPurchase({
      studentId: form.studentId!,
      roomName: form.roomName,
      items: [{ materialName: form.materialName, quantity, unitPrice }],
      balanceAmount: balanceAmount > 0 ? Number(balanceAmount.toFixed(2)) : undefined,
      handler: props.creator,
      remark: form.remark || undefined,
    })
    message.success(`「${form.materialName}」购买成功，金额 ￥${Number(res.totalAmount ?? 0).toFixed(2)}`)
    emit('saved', res.extOrderNo)
    close()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '办理失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

/** 积分兑换：只扣学员积分，与现金、账户余额无关。 */
async function submitPointsExchange() {
  if (!form.goodsId) return fail('请选择要兑换的物品')
  const quantity = Number(form.quantity ?? 0)
  if (!(quantity > 0)) return fail('数量必须大于 0')
  if (pointsNeeded.value <= 0) return fail('该兑换物未设置所需积分，无法兑换')
  const available = Number(selectedStudent.value?.points ?? 0)
  if (available < pointsNeeded.value) {
    return fail(`学员积分不足，当前 ${available} 积分，本次需 ${pointsNeeded.value} 积分`)
  }
  const stock = Number(selectedPointsGoods.value?.stock ?? 0)
  if (stock < quantity) return fail(`库存不足，当前剩余 ${stock} 件`)
  submitting.value = true
  try {
    const res = await exchangePointsGoods({
      studentId: form.studentId!,
      goodsId: form.goodsId,
      quantity,
      handler: props.creator,
      remark: form.remark || undefined,
    })
    message.success(
      `「${res.goodsName}」兑换成功，消耗 ${Number(res.pointsCost ?? 0).toLocaleString('zh-CN')} 积分`,
    )
    emit('saved', res.orderNo)
    close()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '积分兑换失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function submit() {
  if (!form.studentId) return fail('请先选择学员')
  if (isMaterialMode.value) {
    await submitMaterial()
    return
  }
  if (!form.campusId) return fail('请选择经办校区')
  submitting.value = true
  try {
    let result
    if (props.mode === 'enroll') {
      if (!form.courseId) return fail('请选择报读课程')
      if (!form.classId) return fail('请选择班级')
      if (!form.lessons || Number(form.lessons) <= 0) return fail('报读课时必须大于 0')
      const payable = Math.max(round2(Number(form.shouldPay || 0) - activityDiscount.value), 0)
      if (Math.abs(Number(form.received || 0) - payable) > 0.009) {
        return fail(`实收金额需等于应付金额￥${payable.toFixed(2)}，不支持赊账`)
      }
      const materialFee = Number(selectedPriceTemplate.value?.materialFee ?? 0)
      result = await enrollLocalOrder({
        studentId: form.studentId,
        campusId: form.campusId,
        courseId: form.courseId,
        classId: form.classId,
        lessons: Number(form.lessons),
        giftLessons: Number(form.giftLessons || 0),
        unitPrice: selectedTier.value ? Number(selectedTier.value.unitPrice) : undefined,
        discount: activityDiscount.value,
        shouldPay: Number(form.shouldPay || 0),
        received: Number(form.received || 0),
        materials:
          materialFee > 0
            ? [{ materialId: null, name: '教材费', price: materialFee, quantity: 1 }]
            : undefined,
        payMethod: form.payMethod,
        creator: props.creator,
        remark: form.remark || undefined,
        tags: form.tags || undefined,
        priceTemplateId: form.priceTemplateId,
        localActivityTemplateIds: form.localActivityTemplateIds,
      })
    } else if (props.mode === 'recharge') {
      if (!form.amount || Number(form.amount) <= 0) return fail('充值金额必须大于 0')
      result = await rechargeLocalOrder({
        studentId: form.studentId,
        campusId: form.campusId,
        amount: Number(form.amount),
        payMethod: form.payMethod,
        creator: props.creator,
        remark: form.remark || undefined,
        tags: form.tags || undefined,
      })
    } else if (props.mode === 'subsidy') {
      if (!form.enrollmentId) return fail('请选择要补费的报读科目')
      if (!form.amount || Number(form.amount) <= 0) return fail('补费金额必须大于 0')
      result = await subsidyLocalOrder({
        studentId: form.studentId,
        campusId: form.campusId,
        enrollmentId: form.enrollmentId,
        amount: Number(form.amount),
        payMethod: form.payMethod,
        creator: props.creator,
        remark: form.remark || undefined,
        tags: form.tags || undefined,
      })
    } else if (props.mode === 'refund') {
      if (!form.amount || Number(form.amount) <= 0) return fail('退费金额必须大于 0')
      result = await refundLocalOrder({
        studentId: form.studentId,
        campusId: form.campusId,
        enrollmentId: form.enrollmentId,
        amount: Number(form.amount),
        lessons: Number(form.cutLessons || 0),
        payMethod: form.payMethod,
        creator: props.creator,
        reason: form.reason || undefined,
      })
    } else {
      if (!form.fromEnrollmentId) return fail('请选择转出报读')
      if (!form.toCourseId) return fail('请选择转入课程')
      if (!form.lessons || Number(form.lessons) <= 0) return fail('转移课时必须大于 0')
      result = await transferLocalOrder({
        studentId: form.studentId,
        campusId: form.campusId,
        fromEnrollmentId: form.fromEnrollmentId,
        toCourseId: form.toCourseId,
        lessons: Number(form.lessons),
        extraPay: Number(form.extraPay || 0),
        payMethod: form.payMethod,
        creator: props.creator,
        remark: form.remark || undefined,
      })
    }
    message.success(`${result.typeName}成功，订单号 ${result.orderNo}`)
    emit('saved', result.orderNo)
    close()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '办理失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

watch(
  [() => props.open, () => props.mode],
  ([open]) => {
    if (!open) return
    resetForm()
    if (props.presetStudent) {
      const preset = props.presetStudent
      students.value = [preset as unknown as StudentRow]
      form.studentId = preset.id
      form.campusId = preset.campusId ?? undefined
    }
    if (isMaterialMode.value) {
      loadMaterials()
    } else {
      loadOptions()
    }
  },
  { immediate: true },
)
</script>

<template>
  <a-modal
    :open="open"
    :title="title"
    :width="580"
    :confirm-loading="submitting"
    ok-text="确认办理"
    cancel-text="取消"
    destroy-on-close
    @ok="submit"
    @cancel="close"
  >
    <a-form layout="horizontal" :label-col="{ span: 5 }" :wrapper-col="{ span: 19 }">
      <a-form-item label="学员" required>
        <a-select
          v-model:value="form.studentId"
          show-search
          :filter-option="false"
          :options="studentOptions"
          :loading="studentLoading"
          placeholder="输入姓名或手机号搜索学员"
          @search="onStudentSearch"
          @change="onStudentChange"
        />
        <div v-if="selectedStudent" class="student-meta">
          账户余额 ￥{{ Number(selectedStudent.balance || 0).toFixed(2) }}
          <span class="meta-sep">｜</span>
          欠费 ￥{{ Number(selectedStudent.debt || 0).toFixed(2) }}
          <template v-if="mode === 'points'">
            <span class="meta-sep">｜</span>
            积分 {{ Number(selectedStudent.points || 0) }}
          </template>
        </div>
      </a-form-item>

      <a-form-item v-if="!isMaterialMode" label="经办校区" required>
        <a-select v-model:value="form.campusId" :options="campusOptions" placeholder="请选择校区" />
      </a-form-item>

      <template v-if="mode === 'enroll'">
        <a-form-item label="报读课程" required>
          <a-select
            v-model:value="form.courseId"
            show-search
            option-filter-prop="label"
            :options="courseOptions"
            placeholder="请选择课程"
            @change="onCourseChange"
          />
        </a-form-item>
        <a-form-item label="价格模板" required>
          <a-select
            v-model:value="form.priceTemplateId"
            show-search
            option-filter-prop="label"
            :options="priceTemplateOptions"
            placeholder="请选择与课程绑定的价格模板"
            @change="onPriceTemplateChange"
          />
          <div v-if="form.courseId && !priceTemplateOptions.length" class="template-hint">
            该课程尚未配置价格模板，请到「办理中心 - 缴费模板」维护
          </div>
        </a-form-item>
        <a-form-item v-if="tierOptions.length" label="课时档位">
          <a-select v-model:value="form.tierIndex" :options="tierOptions" @change="onTierChange" />
        </a-form-item>
        <a-form-item label="优惠模板">
          <a-select
            v-model:value="form.localActivityTemplateIds"
            mode="multiple"
            show-search
            option-filter-prop="label"
            :options="activityOptions"
            placeholder="可多选优惠，不使用请留空"
            allow-clear
            :max-tag-count="2"
          />
        </a-form-item>
        <a-form-item v-if="activityDiscount > 0 || activityGiftLessons > 0" label="活动优惠">
          <span class="field-tip">
            {{ activityDiscount > 0 ? `-￥${activityDiscount.toFixed(2)}` : `赠 ${activityGiftLessons} 课时` }}
            <template v-if="selectedActivities.length">
              （{{ selectedActivities.map((item) => item.name).join('、') }}）
            </template>
          </span>
        </a-form-item>
        <a-form-item label="报读班级" required>
          <a-select
            v-model:value="form.classId"
            show-search
            option-filter-prop="label"
            :options="classOptions"
            :disabled="!form.courseId"
            placeholder="请先选择课程，再选择班级"
          />
        </a-form-item>
        <a-form-item label="报读课时" required>
          <a-input-number
            v-model:value="form.lessons"
            :min="0.5"
            :step="0.5"
            :precision="1"
            style="width: 100%"
            @change="onLessonsChange"
          />
        </a-form-item>
        <a-form-item label="赠送课时">
          <a-input-number
            v-model:value="form.giftLessons"
            :min="0"
            :step="0.5"
            :precision="1"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="应收金额（优惠前）">
          <a-input-number
            v-model:value="form.shouldPay"
            :min="0"
            :precision="2"
            style="width: 100%"
            addon-before="￥"
            disabled
          />
        </a-form-item>
        <a-form-item label="实收金额">
          <a-input-number
            v-model:value="form.received"
            :min="0"
            :precision="2"
            style="width: 100%"
            addon-before="￥"
            @change="receivedTouched = true"
          />
        </a-form-item>
      </template>

      <template v-else-if="mode === 'recharge'">
        <a-form-item label="充值金额" required>
          <a-input-number
            v-model:value="form.amount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
            addon-before="￥"
          />
          <div v-if="Number(selectedStudent?.debt || 0) > 0" class="field-tip">
            当前欠费 ￥{{ Number(selectedStudent?.debt || 0).toFixed(2) }}，充值金额将优先冲抵欠费，剩余部分计入余额
          </div>
        </a-form-item>
      </template>

      <template v-else-if="mode === 'subsidy'">
        <a-form-item label="报读科目" required>
          <a-select
            v-model:value="form.enrollmentId"
            :options="enrollmentOptions"
            placeholder="请选择要补费的报读科目"
            @change="onSubsidyEnrollmentChange"
          />
        </a-form-item>
        <a-form-item label="待补金额">
          <span class="field-tip">
            {{ subsidyEnrollment ? `￥${subsidyArrears.toFixed(2)}` : '选择报读科目后显示' }}
          </span>
        </a-form-item>
        <a-form-item label="补费金额" required>
          <a-input-number
            v-model:value="form.amount"
            :min="0.01"
            :max="subsidyArrears > 0 ? subsidyArrears : undefined"
            :precision="2"
            style="width: 100%"
            addon-before="￥"
          />
          <div class="field-tip">补费直接冲减该报读的应收未收，不进入账户余额</div>
        </a-form-item>
      </template>

      <template v-else-if="mode === 'refund'">
        <a-form-item label="关联报读">
          <a-select
            v-model:value="form.enrollmentId"
            allow-clear
            :options="enrollmentOptions"
            placeholder="可选：指定要退课的报读记录"
            @change="onEnrollmentChange"
          />
        </a-form-item>
        <a-form-item label="退费金额" required>
          <a-input-number
            v-model:value="form.amount"
            :min="0.01"
            :precision="2"
            style="width: 100%"
            addon-before="￥"
          />
        </a-form-item>
        <a-form-item label="扣减课时">
          <a-input-number
            v-model:value="form.cutLessons"
            :min="0"
            :step="0.5"
            :precision="1"
            style="width: 100%"
            @change="cutLessonsTouched = true"
          />
          <div class="field-tip">选填，按退费课时从该报读中扣减</div>
        </a-form-item>
        <a-form-item label="退费原因">
          <a-input v-model:value="form.reason" placeholder="选填" />
        </a-form-item>
      </template>

      <template v-else-if="mode === 'material'">
        <a-form-item label="出库校区" required>
          <a-select v-model:value="form.roomName" :options="materialRoomOptions" placeholder="请选择出库校区" />
        </a-form-item>
        <a-form-item label="教材" required>
          <a-select
            v-model:value="form.materialName"
            show-search
            option-filter-prop="label"
            :loading="materialLoading"
            :options="materialOptionsList"
            placeholder="请选择教材"
            @change="onMaterialChange"
          />
        </a-form-item>
        <a-form-item label="数量" required>
          <a-input-number v-model:value="form.quantity" :min="1" :precision="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="单价">
          <a-input-number
            v-model:value="form.unitPrice"
            :min="0"
            :precision="2"
            style="width: 100%"
            addon-before="￥"
          />
          <div class="field-tip">默认取教材售价，可手动调整</div>
        </a-form-item>
        <a-form-item label="教材金额">
          <span class="field-tip">￥{{ materialTotal.toFixed(2) }}</span>
        </a-form-item>
        <a-form-item label="余额抵扣">
          <a-checkbox v-model:checked="form.useBalance" :disabled="materialBalance <= 0">
            使用账户剩余未用学费抵扣（可用 ￥{{ materialBalance.toFixed(2) }}）
          </a-checkbox>
          <a-input-number
            v-if="form.useBalance"
            v-model:value="form.balanceAmount"
            :min="0"
            :max="Math.min(materialBalance, materialTotal)"
            :precision="2"
            style="width: 100%; margin-top: 8px"
            addon-before="￥"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" placeholder="选填" />
        </a-form-item>
      </template>

      <template v-else-if="mode === 'points'">
        <div class="points-notice">积分兑换只扣学员积分，不涉及现金与账户余额</div>
        <a-form-item label="兑换物" required>
          <a-select
            v-model:value="form.goodsId"
            show-search
            option-filter-prop="label"
            :loading="materialLoading"
            :options="pointsGoodsOptions"
            placeholder="请选择要兑换的物品"
          />
        </a-form-item>
        <a-form-item label="数量" required>
          <a-input-number v-model:value="form.quantity" :min="1" :precision="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="所需积分">
          <span class="points-cost">{{ pointsNeeded.toLocaleString('zh-CN') }} 积分</span>
          <div class="field-tip">
            学员当前拥有 {{ Number(selectedStudent?.points ?? 0).toLocaleString('zh-CN') }} 积分
          </div>
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" placeholder="选填" />
        </a-form-item>
      </template>

      <template v-else>
        <a-form-item label="转出报读" required>
          <a-select
            v-model:value="form.fromEnrollmentId"
            :options="enrollmentOptions"
            placeholder="请选择要转出的报读记录"
            @change="onFromEnrollmentChange"
          />
        </a-form-item>
        <a-form-item label="转入课程" required>
          <a-select
            v-model:value="form.toCourseId"
            show-search
            option-filter-prop="label"
            :options="courseOptions"
            placeholder="请选择转入课程"
          />
        </a-form-item>
        <a-form-item label="转移课时" required>
          <a-input-number
            v-model:value="form.lessons"
            :min="0.5"
            :max="fromEnrollment ? Number(fromEnrollment.remainLessons) : undefined"
            :step="0.5"
            :precision="1"
            style="width: 100%"
          />
          <div v-if="fromEnrollment" class="field-tip">
            原报读剩余 {{ Number(fromEnrollment.remainLessons) }} 课时
          </div>
        </a-form-item>
        <a-form-item label="补差价">
          <a-input-number
            v-model:value="form.extraPay"
            :min="0"
            :precision="2"
            style="width: 100%"
            addon-before="￥"
          />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" placeholder="选填" />
        </a-form-item>
      </template>

      <a-form-item
        v-if="mode === 'enroll' || mode === 'recharge' || mode === 'subsidy'"
        label="订单标签"
      >
        <a-input v-model:value="form.tags" placeholder="选填，多个标签用逗号分隔" />
      </a-form-item>

      <a-form-item v-if="mode === 'enroll' || mode === 'recharge' || mode === 'subsidy'" label="备注">
        <a-input v-model:value="form.remark" placeholder="选填" />
      </a-form-item>

      <a-form-item v-if="!isMaterialMode" label="支付方式">
        <a-select
          v-model:value="form.payMethod"
          :options="options.payMethods.map((item) => ({ label: item, value: item }))"
          placeholder="请选择支付方式"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
.student-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
}

.meta-sep {
  margin: 0 4px;
  color: #d9d9d9;
}

.field-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
}

.points-notice {
  margin-bottom: 14px;
  padding: 6px 10px;
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  border-radius: 4px;
  color: #ad6800;
  font-size: 12px;
}

.points-cost {
  color: #fa8c16;
  font-size: 16px;
  font-weight: 600;
}

.template-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #fa8c16;
}
</style>
