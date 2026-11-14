<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  fetchStaff,
  fetchStaffOptions,
  updateStaffPermissions,
  type StaffOptions,
  type StaffRow,
} from '../api/client'
import { menuGroups } from '../config/menu'
import { authState } from '../stores/auth'

interface OrgNode {
  key: string
  title: string
  children?: OrgNode[]
  isLeaf?: boolean
}

interface PermGroup {
  key: string
  title: string
  items: Array<{ label: string; value: string }>
}

const roleText: Record<string, string> = {
  admin: '管理员',
  manager: '校区主管',
  teacher: '教师',
  consultant: '顾问',
  finance: '财务',
}

const roleColor: Record<string, string> = {
  admin: 'red',
  manager: 'gold',
  teacher: 'blue',
  consultant: 'green',
  finance: 'purple',
}

const roleOrder = ['admin', 'manager', 'teacher', 'consultant', 'finance']

const loading = ref(false)
const saving = ref(false)
const rows = ref<StaffRow[]>([])
const options = ref<StaffOptions | null>(null)
const keyword = ref('')
const selectedKey = ref('all')
const expandedKeys = ref<string[]>([])
const drawerOpen = ref(false)
const current = ref<StaffRow | null>(null)
const draft = ref<string[]>([])

/** 只有管理员可以分配权限；其余账号在此页只读。 */
const isAdmin = computed(() => authState.user?.role === 'admin')

/** 权限配置按菜单分组展示，值与路由路径一致。 */
const permissionGroups: PermGroup[] = menuGroups
  .map((group) => ({
    key: group.key,
    title: group.title,
    items: group.items
      .filter((item) => item.path !== '/desktop')
      .map((item) => ({ label: item.title, value: item.path })),
  }))
  .filter((group) => group.items.length > 0)

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((row) =>
    [row.name, row.title, row.phone, row.account, row.campusName, roleText[row.role]]
      .some((value) => (value ?? '').toLowerCase().includes(kw)),
  )
})

function roleRank(role: string) {
  const index = roleOrder.indexOf(role)
  return index === -1 ? roleOrder.length : index
}

const treeData = computed<OrgNode[]>(() => {
  const list = filteredRows.value
  const campusMap = new Map<string, { name: string; roles: Map<string, StaffRow[]> }>()
  for (const row of list) {
    const campusKey = row.campusId == null ? 'none' : String(row.campusId)
    if (!campusMap.has(campusKey)) {
      campusMap.set(campusKey, { name: row.campusName ?? '未分配校区', roles: new Map() })
    }
    const roles = campusMap.get(campusKey)!.roles
    if (!roles.has(row.role)) roles.set(row.role, [])
    roles.get(row.role)!.push(row)
  }
  const campusNodes = [...campusMap.entries()].map(([campusKey, campus]) => {
    const roleNodes = [...campus.roles.entries()]
      .sort((a, b) => roleRank(a[0]) - roleRank(b[0]))
      .map(([role, staff]) => ({
        key: `campus:${campusKey}|role:${role}`,
        title: `${roleText[role] ?? role} · ${staff.length}`,
        isLeaf: true,
      }))
    const total = [...campus.roles.values()].reduce((sum, staff) => sum + staff.length, 0)
    return {
      key: `campus:${campusKey}`,
      title: `${campus.name} · ${total}`,
      children: roleNodes,
    }
  })
  return [
    {
      key: 'all',
      title: `全部员工 · ${list.length}`,
      children: campusNodes,
    },
  ]
})

const scopeRows = computed(() => {
  const key = selectedKey.value
  if (key === 'all') return filteredRows.value
  const [campusPart, rolePart] = key.split('|')
  const campusKey = campusPart.replace('campus:', '')
  const role = rolePart ? rolePart.replace('role:', '') : ''
  return filteredRows.value.filter((row) => {
    const rowCampusKey = row.campusId == null ? 'none' : String(row.campusId)
    if (rowCampusKey !== campusKey) return false
    return !role || row.role === role
  })
})

const scopeTitle = computed(() => {
  if (selectedKey.value === 'all') return '全部员工'
  const [campusPart, rolePart] = selectedKey.value.split('|')
  const campusKey = campusPart.replace('campus:', '')
  const campusName = campusKey === 'none'
    ? '未分配校区'
    : options.value?.campuses.find((item) => String(item.id) === campusKey)?.name ?? '校区'
  const role = rolePart ? ` · ${roleText[rolePart.replace('role:', '')] ?? ''}` : ''
  return `${campusName}${role}`
})

const scopeSummary = computed(() => {
  const list = scopeRows.value
  return {
    total: list.length,
    active: list.filter((row) => row.status === 1).length,
    teachers: list.filter((row) => row.role === 'teacher').length,
    consultants: list.filter((row) => row.role === 'consultant').length,
  }
})

const canEdit = computed(() => isAdmin.value && !!current.value && current.value.role !== 'admin')

async function load() {
  loading.value = true
  try {
    const [list, opts] = await Promise.all([fetchStaff({}), fetchStaffOptions()])
    rows.value = list
    options.value = opts
    expandedKeys.value = ['all', ...opts.campuses.map((campus) => `campus:${campus.id}`)]
  } catch (e) {
    message.error(e instanceof Error ? e.message : '加载组织架构失败')
  } finally {
    loading.value = false
  }
}

function onSelect(keys: Array<string | number>) {
  selectedKey.value = keys.length > 0 ? String(keys[0]) : 'all'
}

function permissionSummary(row: StaffRow) {
  if (row.role === 'admin' || (row.permissions ?? []).includes('*')) return '全部菜单'
  const count = (row.permissions ?? []).filter((path) => path !== '*').length
  return count === 0 ? '仅工作台' : `已授权 ${count} 个菜单`
}

function openPermission(row: StaffRow) {
  current.value = row
  draft.value = row.role === 'admin' ? [] : (row.permissions ?? []).filter((path) => path !== '*')
  drawerOpen.value = true
}

function groupChecked(group: PermGroup) {
  return group.items.length > 0 && group.items.every((item) => draft.value.includes(item.value))
}

function groupIndeterminate(group: PermGroup) {
  const count = group.items.filter((item) => draft.value.includes(item.value)).length
  return count > 0 && count < group.items.length
}

function toggleGroup(group: PermGroup, event: { target: { checked: boolean } }) {
  const values = group.items.map((item) => item.value)
  const rest = draft.value.filter((path) => !values.includes(path))
  draft.value = event.target.checked ? [...rest, ...values] : rest
}

function clearAll() {
  draft.value = []
  message.info('已清空，仅保留工作台。保存后生效')
}

async function savePermissions() {
  if (!current.value || !canEdit.value) return
  saving.value = true
  try {
    const targetId = current.value.id
    const permissions = await updateStaffPermissions(targetId, draft.value)
    const target = rows.value.find((row) => row.id === targetId)
    if (target) target.permissions = permissions
    current.value = { ...current.value, permissions }
    message.success(`「${current.value.name}」的菜单权限已更新`)
    drawerOpen.value = false
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存权限失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="org-tip">
      管理员点击员工卡片即可授予或回收菜单权限；未授权的功能在员工登录后不会显示，直接输入网址会被拦回工作台，对应接口也会拒绝访问。
      <span v-if="!isAdmin" class="readonly">当前账号不是管理员，仅可查看，无法调整权限。</span>
    </div>

    <div class="org-layout">
      <div class="org-side">
        <div class="side-title">组织架构</div>
        <a-input
          v-model:value="keyword"
          placeholder="搜索姓名 / 职位 / 手机号"
          size="small"
          allow-clear
          style="margin-bottom: 10px"
        />
        <a-spin :spinning="loading">
          <a-tree
            v-model:expanded-keys="expandedKeys"
            :tree-data="treeData"
            :selected-keys="[selectedKey]"
            block-node
            @select="onSelect"
          />
          <div v-if="!loading && filteredRows.length === 0" class="side-empty">没有匹配的员工</div>
        </a-spin>
      </div>

      <div class="org-main">
        <div class="main-head">
          <div>
            <div class="main-title">{{ scopeTitle }}</div>
            <div class="main-sub">
              <span>共 <b>{{ scopeSummary.total }}</b> 人</span>
              <span>在职 <b>{{ scopeSummary.active }}</b></span>
              <span>教师 <b>{{ scopeSummary.teachers }}</b></span>
              <span>顾问 <b>{{ scopeSummary.consultants }}</b></span>
            </div>
          </div>
          <span class="muted" style="font-size: 12px">点击员工卡片配置权限</span>
        </div>

        <a-spin :spinning="loading">
          <div v-if="scopeRows.length > 0" class="staff-grid">
            <div
              v-for="row in scopeRows"
              :key="row.id"
              class="staff-card"
              :class="{ inactive: row.status !== 1 }"
              @click="openPermission(row)"
            >
              <div class="staff-avatar">{{ row.name.slice(0, 1) }}</div>
              <div class="staff-body">
                <div class="staff-line">
                  <span class="staff-name">{{ row.name }}</span>
                  <a-tag :color="roleColor[row.role] ?? 'default'" class="role-tag">
                    {{ roleText[row.role] ?? row.role }}
                  </a-tag>
                  <a-tag v-if="row.status !== 1" class="role-tag">离职</a-tag>
                </div>
                <div class="staff-meta">
                  {{ row.campusName || '未分配校区' }}<template v-if="row.title"> · {{ row.title }}</template>
                </div>
                <div class="staff-perm">
                  <span class="perm-value">{{ permissionSummary(row) }}</span>
                  <span v-if="!row.account" class="perm-warn">未开通登录账号</span>
                </div>
              </div>
              <span class="card-action">{{ isAdmin ? '配置权限' : '查看权限' }}</span>
            </div>
          </div>
          <a-empty v-else description="当前范围暂无员工" style="padding: 40px 0" />
        </a-spin>
      </div>
    </div>

    <a-drawer
      v-model:open="drawerOpen"
      :width="560"
      :title="`配置菜单权限 · ${current?.name ?? ''}`"
      destroy-on-close
    >
      <template v-if="current">
        <div class="drawer-head">
          <div class="staff-avatar">{{ current.name.slice(0, 1) }}</div>
          <div>
            <div class="staff-line">
              <span class="staff-name">{{ current.name }}</span>
              <a-tag :color="roleColor[current.role] ?? 'default'">{{ roleText[current.role] ?? current.role }}</a-tag>
              <a-tag v-if="current.status !== 1">离职</a-tag>
            </div>
            <div class="staff-meta">
              {{ current.campusName || '未分配校区' }}<template v-if="current.title"> · {{ current.title }}</template>
            </div>
            <div class="staff-meta">
              登录账号：{{ current.account || '未开通' }}<template v-if="current.phone"> · {{ current.phone }}</template>
            </div>
          </div>
        </div>

        <a-alert
          v-if="current.role === 'admin'"
          type="info"
          show-icon
          message="管理员固定拥有全部菜单权限，无需单独配置。"
          style="margin: 12px 0"
        />
        <a-alert
          v-else-if="!isAdmin"
          type="warning"
          show-icon
          message="只有管理员可以分配菜单权限，当前为只读查看。"
          style="margin: 12px 0"
        />
        <a-alert
          v-else-if="!current.account"
          type="warning"
          show-icon
          message="该员工尚未开通登录账号，配置的权限将在开通账号后生效。"
          style="margin: 12px 0"
        />

        <div v-if="current.role !== 'admin'" class="perm-box">
          <div class="perm-toolbar">
            <span>可见菜单</span>
            <a-button v-if="canEdit" type="link" size="small" @click="clearAll">清空</a-button>
          </div>
          <div v-for="group in permissionGroups" :key="group.key" class="perm-group">
            <a-checkbox
              :checked="groupChecked(group)"
              :indeterminate="groupIndeterminate(group)"
              :disabled="!canEdit"
              @change="toggleGroup(group, $event)"
            >
              {{ group.title }}
            </a-checkbox>
            <a-checkbox-group v-model:value="draft" :options="group.items" class="perm-items" :disabled="!canEdit" />
          </div>
          <div class="perm-tip">
            工作台为默认页面始终可见；未勾选的菜单登录后不显示，也无法通过网址直接访问。
          </div>
        </div>
      </template>

      <template #footer>
        <div class="drawer-footer">
          <a-button @click="drawerOpen = false">关闭</a-button>
          <a-button v-if="canEdit" type="primary" :loading="saving" @click="savePermissions">保存权限</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.org-tip {
  background: #f2f7ff;
  border: 1px solid #d6e6ff;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 12px;
  color: #4a5a75;
  margin-bottom: 12px;
  line-height: 1.7;
}

.readonly {
  color: #d46b08;
  margin-left: 6px;
}

.org-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
}

.org-side,
.org-main {
  background: var(--card);
  border-radius: 8px;
  padding: 14px 16px 16px;
}

.org-side :deep(.ant-tree) {
  font-size: 13px;
}

.side-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 10px;
}

.side-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
}

.main-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.main-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.main-sub {
  display: flex;
  gap: 18px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-2);
}

.main-sub b {
  color: var(--text);
  font-weight: 600;
  margin-left: 2px;
}

.staff-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.staff-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.staff-card:hover {
  border-color: #adc6ff;
  box-shadow: 0 2px 10px rgba(22, 93, 255, 0.08);
}

.staff-card.inactive {
  opacity: 0.65;
}

.staff-avatar {
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #eef3ff;
  color: var(--brand);
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.staff-body {
  flex: 1;
  min-width: 0;
}

.staff-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.staff-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}

.role-tag {
  margin-inline-end: 0;
  font-size: 11px;
  line-height: 18px;
}

.staff-meta {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-3);
}

.staff-perm {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
}

.perm-value {
  color: var(--brand);
}

.perm-warn {
  color: #d46b08;
}

.card-action {
  flex: 0 0 auto;
  font-size: 12px;
  color: var(--brand);
}

.drawer-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.perm-box {
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 6px 12px 10px;
}

.perm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 2px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.perm-group {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
  border-top: 1px dashed #f2f2f2;
}

.perm-group > :first-child {
  flex: 0 0 92px;
  margin-top: 1px;
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
  color: var(--text-3);
  line-height: 1.6;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
