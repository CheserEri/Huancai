<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import {
  createStaff,
  fetchStaff,
  fetchStaffOptions,
  fetchStaffSummary,
  updateStaff,
  updateStaffStatus,
  type StaffFormPayload,
  type StaffOptions,
  type StaffRow,
  type StaffSummary,
} from '../api/client'
import { actionPermissionGroups, menuGroups } from '../config/menu'
import FilterBar from '../components/FilterBar.vue'

const keyword = ref('')
const campus = ref<'all' | number>('all')
const role = ref('all')
const status = ref<'all' | number>('all')
const rows = ref<StaffRow[]>([])
const summary = ref<StaffSummary | null>(null)
const loading = ref(false)
const options = ref<StaffOptions | null>(null)

const roleOptions = [
  { value: 'admin', label: '管理员' },
  { value: 'manager', label: '校长' },
  { value: 'finance', label: '财务' },
  { value: 'reception', label: '前台' },
  { value: 'consultant', label: '顾问' },
  { value: 'teacher', label: '教师' },
  { value: 'assistant', label: '助教' },
]

const roleText: Record<string, string> = {
  admin: '管理员',
  manager: '校长',
  finance: '财务',
  reception: '前台',
  consultant: '顾问',
  teacher: '教师',
  assistant: '助教',
}

const roleColor: Record<string, string> = {
  admin: 'red',
  manager: 'gold',
  finance: 'purple',
  reception: 'cyan',
  consultant: 'green',
  teacher: 'blue',
  assistant: 'geekblue',
}

function baseQuery() {
  return {
    keyword: keyword.value.trim() || undefined,
    campusId: campus.value === 'all' ? undefined : Number(campus.value),
    role: role.value === 'all' ? undefined : role.value,
    status: status.value === 'all' ? undefined : Number(status.value),
  }
}

async function load() {
  loading.value = true
  try {
    const q = baseQuery()
    const [list, sum] = await Promise.all([fetchStaff(q), fetchStaffSummary(q)])
    rows.value = list
    summary.value = sum
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function reset() {
  keyword.value = ''
  campus.value = 'all'
  role.value = 'all'
  status.value = 'all'
  load()
}

async function loadOptions() {
  try {
    options.value = await fetchStaffOptions()
  } catch (e) {
    console.error(e)
  }
}

/* ---------------- 写操作 ---------------- */
const formOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  phone: '',
  account: '',
  password: '',
  role: 'teacher',
  extraRoles: [] as string[],
  title: '',
  permissions: [] as string[],
  campusId: null as number | null,
  status: 1,
})

/** 兼任角色候选：排除管理员与当前主角色。 */
const extraRoleOptions = computed(() =>
  roleOptions.filter((item) => item.value !== 'admin' && item.value !== form.role),
)

function onPrimaryRoleChange() {
  form.extraRoles = form.extraRoles.filter((item) => item !== form.role)
}

/** 权限配置按菜单分组展示，值与路由路径一致。 */
const permissionGroups = [...menuGroups, ...actionPermissionGroups].map((group) => ({
  key: group.key,
  title: group.title,
  items: group.items
    .filter((item) => item.path !== '/desktop')
    .map((item) => ({ label: item.title, value: item.path })),
}))

function groupChecked(group: (typeof permissionGroups)[number]) {
  return group.items.length > 0 && group.items.every((item) => form.permissions.includes(item.value))
}

function groupIndeterminate(group: (typeof permissionGroups)[number]) {
  const count = group.items.filter((item) => form.permissions.includes(item.value)).length
  return count > 0 && count < group.items.length
}

function toggleGroup(group: (typeof permissionGroups)[number], event: { target: { checked: boolean } }) {
  const checked = event.target.checked
  const values = group.items.map((item) => item.value)
  const rest = form.permissions.filter((path) => !values.includes(path))
  form.permissions = checked ? [...rest, ...values] : rest
}

function permissionText(record: StaffRow) {
  if (record.role === 'admin' || (record.permissions ?? []).includes('*')) return '全部菜单'
  if (!record.account) return '未开通账号'
  const count = (record.permissions ?? []).length
  return count === 0 ? '仅工作台' : `已配置 ${count} 项`
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    phone: '',
    account: '',
    password: '',
    role: 'teacher',
    extraRoles: [],
    title: '',
    permissions: [],
    campusId: null,
    status: 1,
  })
  formOpen.value = true
}

function openEdit(record: StaffRow) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name,
    phone: record.phone ?? '',
    account: record.account ?? '',
    password: '',
    role: record.role,
    extraRoles: [...(record.extraRoles ?? [])],
    title: record.title ?? '',
    permissions: record.role === 'admin' ? [] : (record.permissions ?? []).filter((path) => path !== '*'),
    campusId: record.campusId,
    status: record.status,
  })
  formOpen.value = true
}

async function submitForm() {
  if (!form.name.trim()) {
    message.warning('请填写员工姓名')
    return
  }
  if (!form.role) {
    message.warning('请选择角色')
    return
  }
  if (form.account.trim() && !form.password && editingId.value === null) {
    message.warning('填写登录账号时必须设置登录密码')
    return
  }
  if (form.password && form.password.length < 6) {
    message.warning('登录密码至少 6 位')
    return
  }
  saving.value = true
  try {
    const payload: StaffFormPayload = {
      name: form.name.trim(),
      phone: form.phone.trim() || undefined,
      account: form.account.trim() || undefined,
      password: form.password || undefined,
      role: form.role,
      extraRoles: form.extraRoles.filter((item) => item !== form.role),
      title: form.title.trim() || undefined,
      permissions: form.role === 'admin' ? undefined : form.permissions,
      campusId: form.campusId,
      status: form.status,
    }
    if (editingId.value === null) {
      await createStaff(payload)
      message.success('员工已创建')
    } else {
      await updateStaff(editingId.value, payload)
      message.success('员工已更新')
    }
    formOpen.value = false
    await load()
  } catch (e) {
    message.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

function toggleStatus(record: StaffRow) {
  if (record.status !== 1) {
    doToggleStatus(record, 1)
    return
  }
  Modal.confirm({
    title: `确认将「${record.name}」设为离职？`,
    content: '离职后该员工将无法登录系统，已产生的排课、带班与点评记录会保留。',
    okText: '确认离职',
    cancelText: '取消',
    onOk: () => doToggleStatus(record, 0),
  })
}

async function doToggleStatus(record: StaffRow, next: number) {
  try {
    await updateStaffStatus(record.id, next)
    message.success(next === 1 ? `「${record.name}」已恢复在职` : `「${record.name}」已设为离职`)
    await load()
  } catch (e) {
    message.error((e as Error).message)
  }
}

const columns = [
  { title: '姓名', dataIndex: 'name', width: 110 },
  { title: '校区', dataIndex: 'campusName', width: 160 },
  { title: '角色', dataIndex: 'role', width: 110 },
  { title: '菜单权限', dataIndex: 'permissions', width: 120 },
  { title: '职位', dataIndex: 'title', width: 150 },
  { title: '手机号', dataIndex: 'phone', width: 140 },
  { title: '带班数', dataIndex: 'classCount', width: 90 },
  { title: '排课数', dataIndex: 'scheduleCount', width: 90 },
  { title: '点评数', dataIndex: 'commentCount', width: 90 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '操作', dataIndex: 'action', width: 130, fixed: 'right' as const },
]

onMounted(() => {
  load()
  loadOptions()
})
</script>

<template>
  <div class="page">
    <FilterBar @search="load" @reset="reset">
      <a-select v-model:value="campus" class="filter-select" style="width: 150px" @change="load">
        <a-select-option value="all">全部校区</a-select-option>
        <a-select-option v-for="item in options?.campuses ?? []" :key="item.id" :value="item.id">
          {{ item.name }}
        </a-select-option>
      </a-select>

      <a-select v-model:value="role" class="filter-select" style="width: 120px" @change="load">
        <a-select-option value="all">全部角色</a-select-option>
        <a-select-option v-for="item in roleOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </a-select-option>
      </a-select>

      <a-select v-model:value="status" class="filter-select" style="width: 110px" @change="load">
        <a-select-option value="all">全部状态</a-select-option>
        <a-select-option :value="1">在职</a-select-option>
        <a-select-option :value="0">离职</a-select-option>
      </a-select>

      <template #search>
        <a-input
          v-model:value="keyword"
          class="filter-search"
          placeholder="姓名 / 手机 / 职位"
          allow-clear
          @press-enter="load"
        >
          <template #prefix>
            <SearchOutlined class="search-icon" />
          </template>
        </a-input>
      </template>
    </FilterBar>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <span class="muted" style="font-size: 12px">数据来源：原系统员工名单与教学工作量</span>
        </div>
        <a-button type="primary" size="small" class="primary-btn" @click="openCreate">
          <PlusOutlined />新建员工
        </a-button>
      </div>

      <div class="summary-bar">
        <span>员工总数<b>{{ summary?.total ?? 0 }}</b></span>
        <span>在职<b>{{ summary?.active ?? 0 }}</b></span>
        <span>教师<b>{{ summary?.teachers ?? 0 }}</b></span>
        <span>顾问<b>{{ summary?.consultants ?? 0 }}</b></span>
      </div>

      <a-spin :spinning="loading">
        <a-table
          :columns="columns"
          :data-source="rows"
          :pagination="{ pageSize: 20, showSizeChanger: false, hideOnSinglePage: true }"
          row-key="id"
          size="middle"
          :scroll="{ x: 1280 }"
        >
          <template #bodyCell="{ column, record }">
            <span v-if="column.dataIndex === 'campusName'">{{ record.campusName || '—' }}</span>
            <span v-else-if="column.dataIndex === 'role'">
              <a-tag :color="roleColor[record.role] ?? 'default'" class="role-chip">
                {{ roleText[record.role] ?? record.role }}
              </a-tag>
              <a-tag
                v-for="extra in record.extraRoles ?? []"
                :key="extra"
                :color="roleColor[extra] ?? 'default'"
                class="role-chip"
              >
                兼{{ roleText[extra] ?? extra }}
              </a-tag>
            </span>
            <span v-else-if="column.dataIndex === 'title'">{{ record.title || '—' }}</span>
            <span v-else-if="column.dataIndex === 'permissions'">{{ permissionText(record) }}</span>
            <span v-else-if="column.dataIndex === 'phone'">{{ record.phone || '—' }}</span>
            <a-tag v-else-if="column.dataIndex === 'status'" :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '在职' : '离职' }}
            </a-tag>
            <span v-else-if="column.dataIndex === 'action'" class="row-actions">
              <a-button type="link" size="small" class="row-btn" @click="openEdit(record)">编辑</a-button>
              <a-button type="link" size="small" class="row-btn" @click="toggleStatus(record)">
                {{ record.status === 1 ? '离职' : '复职' }}
              </a-button>
            </span>
            <span v-else>{{ record[column.dataIndex] }}</span>
          </template>
          <template #emptyText>
            <div style="padding: 32px 0; color: #8f959e">暂无员工数据</div>
          </template>
        </a-table>
      </a-spin>
    </div>

    <a-modal
      v-model:open="formOpen"
      :title="editingId === null ? '新建员工' : '编辑员工'"
      :confirm-loading="saving"
      width="760px"
      ok-text="保存"
      cancel-text="取消"
      @ok="submitForm"
    >
      <a-form layout="vertical" class="staff-form">
        <div class="form-grid">
          <a-form-item label="姓名" required>
            <a-input v-model:value="form.name" :maxlength="64" placeholder="请输入员工姓名" />
          </a-form-item>
          <a-form-item label="手机号">
            <a-input v-model:value="form.phone" :maxlength="32" placeholder="可用于登录系统" />
          </a-form-item>
          <a-form-item label="角色" required>
            <a-select v-model:value="form.role" @change="onPrimaryRoleChange">
              <a-select-option v-for="item in roleOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="兼任角色">
            <a-select
              v-model:value="form.extraRoles"
              mode="multiple"
              allow-clear
              placeholder="可多选，如同时兼任教师"
            >
              <a-select-option v-for="item in extraRoleOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="职位">
            <a-input v-model:value="form.title" :maxlength="64" placeholder="如：教务、教师" />
          </a-form-item>
          <a-form-item label="校区">
            <a-select v-model:value="form.campusId" allow-clear placeholder="请选择所属校区">
              <a-select-option v-for="item in options?.campuses ?? []" :key="item.id" :value="item.id">
                {{ item.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="状态">
            <a-radio-group v-model:value="form.status">
              <a-radio :value="1">在职</a-radio>
              <a-radio :value="0">离职</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="登录账号">
            <a-input v-model:value="form.account" :maxlength="64" placeholder="选填，用于登录系统" />
          </a-form-item>
          <a-form-item label="登录密码">
            <a-input-password
              v-model:value="form.password"
              :placeholder="editingId === null ? '填账号时必填，至少 6 位' : '留空则不修改密码'"
            />
          </a-form-item>
        </div>

        <a-form-item label="菜单权限">
          <div v-if="form.role === 'admin'" class="perm-all">管理员固定拥有全部菜单权限，无需单独配置。</div>
          <div v-else class="perm-box">
            <div v-for="group in permissionGroups" :key="group.key" class="perm-group">
              <a-checkbox
                :checked="groupChecked(group)"
                :indeterminate="groupIndeterminate(group)"
                @change="toggleGroup(group, $event)"
              >
                {{ group.title }}
              </a-checkbox>
              <a-checkbox-group v-model:value="form.permissions" :options="group.items" class="perm-items" />
            </div>
            <div class="perm-tip">工作台为默认页面始终可见；未勾选的菜单登录后不显示，也无法通过网址直接访问。</div>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 20px;
}

.staff-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.perm-all {
  font-size: 12px;
  color: #8f959e;
}

.perm-box {
  max-height: 300px;
  overflow: auto;
  padding: 8px 12px;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}

.perm-group {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed #f2f2f2;
}

.perm-group > :first-child {
  flex: 0 0 92px;
}

.perm-items {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.perm-items :deep(.ant-checkbox-wrapper) {
  margin-inline-start: 0;
  font-size: 12px;
}

.perm-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #8f959e;
}

.row-actions {
  display: inline-flex;
  gap: 4px;
}

.role-chip {
  margin-inline-end: 4px;
  font-size: 11px;
  line-height: 18px;
}

.row-btn {
  padding: 0 4px;
  height: 22px;
  font-size: 12px;
}

.primary-btn {
  height: 24px;
  padding: 0 10px;
  font-size: 12px;
  border-radius: 4px;
}
</style>
