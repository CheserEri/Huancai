<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CreditCardOutlined,
  DownOutlined,
  ExportOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import FilterBar from '../components/FilterBar.vue'
import {
  createStudent,
  fetchStudentSummary,
  fetchStudents,
  type StudentFormPayload,
  type StudentRow,
  type StudentSummary,
} from '../api/client'

const router = useRouter()
const route = useRoute()

const tabKeys = ['students', 'roster', 'enrollment', 'classAssign', 'score']
const tabLabels = ['学员', '花名册', '报读列表', '分班列表', '学员成绩']
const tab = ref('roster')

const keyword = ref('')
const status = ref('在读')
const campus = ref<'all' | number>('all')
const gender = ref('all')
const cardBound = ref<'all' | number>('all')
const debt = ref<'all' | number>('all')
const isStar = ref<'all' | number>('all')
const studentType = ref('all')
const grade = ref('all')
const sort = ref<string | undefined>(undefined)

const gradeOptions = ['中班', '一年级', '二年级', '三年级', '四年级', '五年级', '六年级', '初一', '初二', '初三']

const rows = ref<StudentRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const summary = ref<StudentSummary>({ count: 0, balance: 0, debt: 0, points: 0 })
const selectedKeys = ref<number[]>([])
const extraKeys = ref<string[]>([])

const isRoster = computed(() => tab.value === 'roster')

function queryParams(includeSort = true) {
  return {
    keyword: keyword.value.trim() || undefined,
    status: status.value === 'all' ? undefined : status.value,
    campusId: campus.value === 'all' ? undefined : Number(campus.value),
    gender: gender.value === 'all' ? undefined : gender.value,
    cardBound: cardBound.value === 'all' ? undefined : Number(cardBound.value),
    hasDebt: debt.value === 'all' ? undefined : Number(debt.value),
    isStar: isStar.value === 'all' ? undefined : Number(isStar.value),
    studentType: studentType.value === 'all' ? undefined : studentType.value,
    grade: grade.value === 'all' ? undefined : grade.value,
    sort: includeSort ? sort.value : undefined,
  }
}

async function load() {
  loading.value = true
  const query = queryParams()
  try {
    const [pageData, summaryData] = await Promise.all([
      fetchStudents({ ...query, page: page.value, size: size.value }),
      fetchStudentSummary(query),
    ])
    rows.value = pageData.records
    total.value = pageData.total
    summary.value = summaryData
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  selectedKeys.value = []
  load()
}

function clearFilters() {
  keyword.value = ''
  status.value = 'all'
  campus.value = 'all'
  gender.value = 'all'
  cardBound.value = 'all'
  debt.value = 'all'
  isStar.value = 'all'
  studentType.value = 'all'
  grade.value = 'all'
  sort.value = undefined
  search()
}

const extraColumns = [
  { title: '是否星标', dataIndex: 'isStar', width: 90 },
  { title: '余额', dataIndex: 'balance', width: 110, align: 'right' as const },
  { title: '在读课程数', dataIndex: 'courseCount', width: 110, align: 'right' as const },
  { title: '积分', dataIndex: 'points', width: 80, align: 'right' as const },
  { title: '年龄', dataIndex: 'ageText', width: 70 },
  { title: '年级', dataIndex: 'grade', width: 80 },
  { title: '微信号', dataIndex: 'wechat', width: 120 },
  { title: '生日', dataIndex: 'birthday', width: 110 },
  { title: '公立学校', dataIndex: 'school', width: 140 },
  { title: '公立校班级', dataIndex: 'publicSchoolClass', width: 110 },
  { title: '居住区域', dataIndex: 'address', width: 140 },
  { title: '渠道名称', dataIndex: 'channelName', width: 110 },
  { title: '其他信息', dataIndex: 'extraInfo', width: 160 },
  { title: '建档日期', dataIndex: 'enrollDate', width: 110 },
]

const rosterColumns = computed(() => [
  { title: '学员姓名', dataIndex: 'name', width: 100, fixed: 'left' as const, sorter: true },
  { title: '性别', dataIndex: 'gender', width: 60 },
  { title: '所属关系(主要)', dataIndex: 'primaryRelation', width: 110 },
  { title: '联系电话', dataIndex: 'phone', width: 125 },
  { title: '磁卡绑定', dataIndex: 'cardNo', width: 95, align: 'center' as const },
  { title: '欠费', dataIndex: 'debt', width: 90, align: 'right' as const },
  { title: '学员状态', dataIndex: 'status', width: 85 },
  { title: '报读校区', dataIndex: 'campusText', width: 165 },
  ...extraColumns.filter((c) => extraKeys.value.includes(String(c.dataIndex))),
])

const studentColumns = computed(() => [
  { title: '学员姓名', dataIndex: 'name', width: 110, fixed: 'left' as const, sorter: true },
  { title: '性别', dataIndex: 'gender', width: 70 },
  { title: '联系电话', dataIndex: 'phone', width: 130 },
  { title: '报读校区', dataIndex: 'campusText', width: 165 },
  { title: '学员状态', dataIndex: 'status', width: 90 },
  { title: '在读课程数', dataIndex: 'courseCount', width: 110, align: 'right' as const },
  { title: '余额', dataIndex: 'balance', width: 110, align: 'right' as const },
  { title: '欠费', dataIndex: 'debt', width: 100, align: 'right' as const },
  { title: '建档日期', dataIndex: 'enrollDate', width: 115 },
])

const tableColumns = computed(() => (isRoster.value ? rosterColumns.value : studentColumns.value))

const rowSelection = computed(() => ({
  selectedRowKeys: selectedKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedKeys.value = keys.map((k) => Number(k))
  },
}))

function onTableChange(
  pagination: { current?: number; pageSize?: number },
  _filters: unknown,
  sorter: { order?: 'ascend' | 'descend' | null; columnKey?: string },
) {
  page.value = pagination.current ?? 1
  size.value = pagination.pageSize ?? 20
  if (sorter && sorter.columnKey === 'name') {
    sort.value = sorter.order === 'ascend' ? 'nameAsc' : sorter.order === 'descend' ? 'nameDesc' : undefined
  }
  load()
}

function formatMoney(value: number | null | undefined) {
  return `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

/** 负数显示为 -¥100.00，便于课时费扣成负数时阅读。 */
function signedMoney(value: number | null | undefined) {
  const n = Number(value ?? 0)
  const text = Math.abs(n).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
  return n < 0 ? `-¥${text}` : `¥${text}`
}

function show(value: unknown) {
  return value === null || value === undefined || value === '' ? '—' : value
}

const emptyText = computed(() => {
  if (loading.value || total.value !== 0) return '暂无数据'
  if (keyword.value.trim()) return `没有匹配「${keyword.value.trim()}」的学员`
  if (status.value === '在读') return '当前筛选条件下暂无在读学员'
  return '当前筛选条件下暂无学员'
})

async function exportCsv() {
  loading.value = true
  try {
    const all: StudentRow[] = []
    let current = 1
    for (let guard = 0; guard < 10; guard++) {
      const res = await fetchStudents({ ...queryParams(), page: current, size: 1000 })
      all.push(...res.records)
      if (all.length >= res.total || res.records.length === 0) break
      current += 1
    }
    const cols = tableColumns.value.filter((c) => c.dataIndex !== 'action')
    const header = cols.map((c) => c.title).join(',')
    const lines = all.map((row) =>
      cols
        .map((c) => {
          const key = String(c.dataIndex)
          let value = ''
          if (key === 'campusText') value = row.campusText || row.campusName || ''
          else if (key === 'cardNo') value = row.cardNo ? '已绑定' : '未绑定'
          else if (key === 'isStar') value = row.isStar ? '已星标' : '无标记'
          else value = String((row as unknown as Record<string, unknown>)[key] ?? '')
          return `"${value.replace(/"/g, '""')}"`
        })
        .join(','),
    )
    const csv = `\ufeff${[header, ...lines].join('\r\n')}`
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }))
    const a = document.createElement('a')
    a.href = url
    a.download = `学员花名册_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function goOrder() {
  router.push('/order')
}

function goClass() {
  router.push('/class')
}

type StudentFormState = {
  name: string
  gender: string
  birthday?: string
  guardianName: string
  primaryRelation?: string
  phone: string
  campusId?: number
  status: string
  grade?: string
  school?: string
  address?: string
  wechat?: string
  remark?: string
}

function emptyForm(): StudentFormState {
  return {
    name: '',
    gender: '未知',
    birthday: undefined,
    guardianName: '',
    primaryRelation: undefined,
    phone: '',
    campusId: 1,
    status: '在读',
    grade: undefined,
    school: undefined,
    address: undefined,
    wechat: undefined,
    remark: undefined,
  }
}

const formOpen = ref(false)
const saving = ref(false)
const form = reactive<StudentFormState>(emptyForm())

const relationOptions = ['父亲', '母亲', '祖父母', '其他']

function openCreate() {
  Object.assign(form, emptyForm())
  formOpen.value = true
}

async function saveForm() {
  if (!form.name.trim()) {
    message.warning('请填写学员姓名')
    return
  }
  if (!form.campusId) {
    message.warning('请选择报读校区')
    return
  }
  const payload: StudentFormPayload = {
    name: form.name.trim(),
    gender: form.gender,
    birthday: form.birthday || undefined,
    guardianName: form.guardianName.trim() || undefined,
    primaryRelation: form.primaryRelation || undefined,
    phone: form.phone.trim() || undefined,
    campusId: form.campusId,
    status: form.status,
    grade: form.grade || undefined,
    school: form.school || undefined,
    address: form.address || undefined,
    wechat: form.wechat || undefined,
    remark: form.remark || undefined,
  }
  saving.value = true
  try {
    await createStudent(payload)
    message.success('学员已创建')
    formOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  // 工作台「剩余欠费人数」跳转过来时，切到学员列表并只展示有欠费的学员
  if (route.query.debt === '1') {
    tab.value = 'students'
    debt.value = 1
  }
  load()
})
</script>

<template>
  <div class="page">
    <div class="tabs-row">
      <span
        v-for="(label, i) in tabLabels"
        :key="label"
        class="tabs-item"
        :class="{ active: tab === tabKeys[i] }"
        @click="tab = tabKeys[i]"
      >{{ label }}</span>
    </div>

    <template v-if="tab === 'enrollment' || tab === 'classAssign' || tab === 'score'">
      <div class="table-card tab-panel">
        <template v-if="tab === 'enrollment'">
          <div class="panel-title">报读列表</div>
          <p class="panel-desc">本系统的报名、续费、补费、转课、退费、充值流水已按原系统口径落在「报名订单」页面，数据来自历史报读明细（1,645 条）。</p>
          <a-button type="primary" @click="goOrder">前往报名订单</a-button>
        </template>
        <template v-else-if="tab === 'classAssign'">
          <div class="panel-title">分班列表</div>
          <p class="panel-desc">历史班级学员名单已全量导入（10,684 条 / 394 个班级），可在「班级」页逐个班级查看名单与排课。</p>
          <a-button type="primary" @click="goClass">前往班级管理</a-button>
        </template>
        <template v-else>
          <div class="panel-title">学员成绩</div>
          <p class="panel-desc">暂无可展示的成绩记录。</p>
          <a-empty description="暂无成绩记录" />
        </template>
      </div>
    </template>

    <template v-else>
      <FilterBar @search="search" @reset="clearFilters">
        <a-button disabled title="原系统批量导入模板未随本次同步提供">批量导入</a-button>

        <a-select v-model:value="status" class="filter-select" style="width: 116px" @change="search">
          <a-select-option value="all">学员状态</a-select-option>
          <a-select-option value="在读">在读</a-select-option>
          <a-select-option value="停课">停课</a-select-option>
          <a-select-option value="结课">结课</a-select-option>
        </a-select>

        <a-select v-model:value="campus" class="filter-select" style="width: 140px" @change="search">
          <a-select-option value="all">报读校区</a-select-option>
          <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
          <a-select-option :value="2">新民幻彩国学堂</a-select-option>
        </a-select>

        <a-select v-model:value="gender" class="filter-select" style="width: 96px" @change="search">
          <a-select-option value="all">性别</a-select-option>
          <a-select-option value="男">男</a-select-option>
          <a-select-option value="女">女</a-select-option>
        </a-select>

        <a-select v-model:value="cardBound" class="filter-select" style="width: 130px" @change="search">
          <a-select-option value="all">是否绑定磁卡</a-select-option>
          <a-select-option :value="1">已绑定</a-select-option>
          <a-select-option :value="0">未绑定</a-select-option>
        </a-select>

        <a-select v-model:value="debt" class="filter-select" style="width: 110px" @change="search">
          <a-select-option value="all">是否欠费</a-select-option>
          <a-select-option :value="1">有欠费</a-select-option>
          <a-select-option :value="0">无欠费</a-select-option>
        </a-select>

        <a-select v-model:value="isStar" class="filter-select" style="width: 110px" @change="search">
          <a-select-option value="all">是否星标</a-select-option>
          <a-select-option :value="1">已星标</a-select-option>
          <a-select-option :value="0">无标记</a-select-option>
        </a-select>

        <a-select v-model:value="studentType" class="filter-select" style="width: 110px" @change="search">
          <a-select-option value="all">新老生</a-select-option>
          <a-select-option value="new">新生</a-select-option>
          <a-select-option value="old">老生</a-select-option>
        </a-select>

        <a-select v-model:value="grade" class="filter-select" style="width: 110px" @change="search">
          <a-select-option value="all">全部年级</a-select-option>
          <a-select-option v-for="item in gradeOptions" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>

        <template #search>
          <a-input
            v-model:value="keyword"
            class="filter-search"
            placeholder="请输入学员姓名"
            allow-clear
            @press-enter="search"
          >
            <template #prefix>
              <SearchOutlined class="search-icon" />
            </template>
          </a-input>
        </template>
      </FilterBar>

      <div class="table-card">
        <div class="action-row">
          <a-button type="primary" size="small" @click="openCreate">新建学员</a-button>
          <a-button size="small" @click="exportCsv"><ExportOutlined />导出当前结果</a-button>
          <a-button size="small" disabled title="原系统通知公告接口未同步">发送通知公告</a-button>
          <a-button size="small" disabled title="原系统批量调整接口未同步">批量调整</a-button>
          <a-button size="small" disabled title="原系统二维码打印接口未同步">批量打印二维码</a-button>
          <span class="filter-spacer" />
          <a-popover trigger="click" placement="bottomRight">
            <template #content>
              <div class="column-picker">
                <a-checkbox-group v-model:value="extraKeys">
                  <div v-for="col in extraColumns" :key="String(col.dataIndex)" class="picker-item">
                    <a-checkbox :value="String(col.dataIndex)">{{ col.title }}</a-checkbox>
                  </div>
                </a-checkbox-group>
              </div>
            </template>
            <a-button size="small">自定义列表 <DownOutlined /></a-button>
          </a-popover>
        </div>

        <div class="result-row">
          <span>当前结果：学员共计 <b>{{ summary.count }}</b> 名，欠费共计 <b>{{ signedMoney(-summary.debt) }}</b></span>
          <span v-if="selectedKeys.length" class="muted">已选择 {{ selectedKeys.length }} 名</span>
        </div>

        <a-table
          :columns="tableColumns"
          :data-source="rows"
          :loading="loading"
          :pagination="{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            pageSizeOptions: ['20', '50', '100'],
            showQuickJumper: true,
            showTotal: (t: number) => `共 ${t} 条`,
          }"
          :row-selection="rowSelection"
          :scroll="{ x: isRoster ? 920 : 1080 }"
          row-key="id"
          size="small"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <router-link v-if="column.dataIndex === 'name'" class="name-link" :to="`/studentDetail/${record.id}`">
              {{ record.name }}
            </router-link>
            <span v-else-if="column.dataIndex === 'cardNo'">
              <CreditCardOutlined
                :style="{ fontSize: '16px', color: record.cardNo ? '#1677FF' : '#D9D9D9' }"
                :title="record.cardNo || '未绑定'"
              />
            </span>
            <span v-else-if="column.dataIndex === 'debt'" :style="{ color: Number(record.debt) > 0 ? '#F5222D' : '' }">
              {{ Number(record.debt) > 0 ? signedMoney(-Number(record.debt)) : '0' }}
            </span>
            <span v-else-if="column.dataIndex === 'status'">
              <span :class="['status-text', `status-${record.status}`]">{{ record.status }}</span>
            </span>
            <span v-else-if="column.dataIndex === 'campusText'">{{ record.campusText || record.campusName }}</span>
            <span v-else-if="column.dataIndex === 'balance'">{{ formatMoney(record.balance) }}</span>
            <span v-else-if="column.dataIndex === 'courseCount'">{{ record.courseCount ?? 0 }} 门</span>
            <span v-else-if="column.dataIndex === 'isStar'" :style="{ color: record.isStar ? '#FAAD14' : '#BFBFBF' }">
              {{ record.isStar ? '★' : '—' }}
            </span>
            <span v-else-if="column.dataIndex === 'enrollDate'">{{ record.enrollDate || '—' }}</span>
            <span v-else-if="column.dataIndex === 'birthday'">{{ record.birthday || '—' }}</span>
            <span v-else>{{ show(record[column.dataIndex]) }}</span>
          </template>
          <template #emptyText>
            <a-empty :description="emptyText" />
          </template>
        </a-table>

        <div class="source-note">
          数据来源：历史学员花名册（3,400 条，2026-10 同步）。姓名、电话、所属关系、校区、状态、余额、欠费等均为原系统源数据原样。
        </div>
      </div>
    </template>

    <a-modal
      v-model:open="formOpen"
      title="新建学员"
      width="680px"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
      @ok="saveForm"
    >
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }" size="small">
        <a-form-item label="学员姓名" required>
          <a-input v-model:value="form.name" placeholder="请输入学员姓名" allow-clear />
        </a-form-item>
        <a-form-item label="性别">
          <a-radio-group v-model:value="form.gender" :options="['男', '女', '未知']" />
        </a-form-item>
        <a-form-item label="生日">
          <a-date-picker
            v-model:value="form.birthday"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="请选择生日"
          />
        </a-form-item>
        <a-form-item label="家长姓名">
          <a-input v-model:value="form.guardianName" placeholder="主要联系人姓名" allow-clear />
        </a-form-item>
        <a-form-item label="所属关系">
          <a-select v-model:value="form.primaryRelation" placeholder="请选择" allow-clear>
            <a-select-option v-for="item in relationOptions" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="form.phone" placeholder="主要联系电话" allow-clear />
        </a-form-item>
        <a-form-item label="报读校区" required>
          <a-select v-model:value="form.campusId" placeholder="请选择校区">
            <a-select-option :value="1">鲅鱼圈幻彩国学堂</a-select-option>
            <a-select-option :value="2">新民幻彩国学堂</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="学员状态">
          <a-select v-model:value="form.status">
            <a-select-option value="在读">在读</a-select-option>
            <a-select-option value="停课">停课</a-select-option>
            <a-select-option value="结课">结课</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="年级">
          <a-select v-model:value="form.grade" placeholder="请选择" allow-clear>
            <a-select-option v-for="item in gradeOptions" :key="item" :value="item">{{ item }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="公立学校">
          <a-input v-model:value="form.school" placeholder="公立学校名称" allow-clear />
        </a-form-item>
        <a-form-item label="居住区域">
          <a-input v-model:value="form.address" placeholder="居住区域" allow-clear />
        </a-form-item>
        <a-form-item label="微信号">
          <a-input v-model:value="form.wechat" placeholder="微信号" allow-clear />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="form.remark" :rows="3" placeholder="备注信息" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.name-link {
  color: var(--brand);
}
.name-link:hover {
  text-decoration: underline;
}
.tabs-row {
  display: flex;
  gap: 28px;
  padding: 0 4px 0;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--line);
}
.tabs-item {
  position: relative;
  padding: 8px 0 10px;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
}
.tabs-item.active {
  color: var(--brand);
  font-weight: 500;
}
.tabs-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--brand);
  border-radius: 1px;
}

.filter-spacer {
  flex: 1;
}
.search-icon {
  color: #8f959e;
}

.action-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-2);
  margin-bottom: 8px;
}
.result-row b {
  color: var(--text);
}

.status-text {
  font-size: 13px;
}
.status-在读 {
  color: #1f9d55;
}
.status-停课 {
  color: #d46b08;
}
.status-结课 {
  color: #8f959e;
}

.column-picker {
  width: 260px;
  max-height: 320px;
  overflow-y: auto;
}
.picker-item {
  margin-bottom: 6px;
}

.tab-panel {
  padding: 24px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}
.panel-desc {
  color: var(--text-2);
  font-size: 13px;
  margin-bottom: 16px;
}

.source-note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-2);
}

</style>
