<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AccountBookOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  BellOutlined,
  CloseOutlined,
  CustomerServiceOutlined,
  DesktopOutlined,
  DownOutlined,
  DownloadOutlined,
  GiftOutlined,
  HomeOutlined,
  MenuOutlined,
  NotificationOutlined,
  QuestionCircleOutlined,
  ReadOutlined,
  RocketOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { menuIndex } from '../config/menu'
import { fetchProfile, logout as logoutRequest } from '../api/client'
import { authState, clearLogin, hasMenuPermission, HOME_PATH, updateProfile, visibleMenuGroups } from '../stores/auth'
import { closeTab, openTab, workTabs } from '../stores/workspace'

const route = useRoute()
const router = useRouter()

const groupIcons: Record<string, Component> = {
  AppstoreOutlined,
  RocketOutlined,
  AccountBookOutlined,
  TeamOutlined,
  ReadOutlined,
  BarChartOutlined,
  NotificationOutlined,
  HomeOutlined,
  SettingOutlined,
  GiftOutlined,
}

const expanded = ref('home')
const campus = ref('buoyant')
const collapsed = ref(false)

const campusOptions = [
  { value: 'all', label: '全部校区' },
  { value: 'buoyant', label: '鲅鱼圈校区' },
  { value: 'xinmin', label: '新民校区' },
]

watch(
  () => route.path,
  (path) => {
    const entry = menuEntryFor(path)
    if (entry) {
      openTab(path, path.startsWith('/classDetail') ? '班级详情' : entry.title)
      expanded.value = entry.group
    }
  },
  { immediate: true },
)

function menuEntryFor(path: string) {
  const direct = menuIndex.get(path)
  if (direct) return direct
  // 班级详情属于「班级」的二级页面，复用班级菜单的归属与高亮
  if (path.startsWith('/classDetail')) return menuIndex.get('/class')
  return undefined
}

const singleItem = (groupKey: string) => {
  const group = groups.value.find((item) => item.key === groupKey)
  return group && group.items.length === 1 ? group.items[0] : null
}

const activeGroup = computed(() => menuEntryFor(route.path)?.group ?? 'home')

function onGroupClick(key: string, path: string) {
  if (path) {
    router.push(path)
    return
  }
  expanded.value = expanded.value === key ? '' : key
}

function isGroupActive(key: string) {
  return activeGroup.value === key
}

function onClose(path: string) {
  const fallback = closeTab(path)
  if (fallback && route.path === path) router.push(fallback)
}

function closeAll() {
  const home = workTabs[0]
  workTabs.splice(1)
  if (home) router.push(home.path)
}

async function logout() {
  try {
    await logoutRequest()
  } catch {
    // 令牌已失效时退出仍应清理本地登录态
  }
  clearLogin()
  router.replace('/login')
}

/** 当前账号可见的菜单：由登录账号的菜单权限决定。 */
const groups = computed(() => visibleMenuGroups(authState.permissions))
const currentUser = computed(() => authState.user ?? { name: '', role: '' })

onMounted(async () => {
  try {
    const profile = await fetchProfile()
    updateProfile(profile)
    // 权限被调整后，当前页面若已不可见则退回工作台
    if (menuIndex.has(route.path) && !hasMenuPermission(route.path)) {
      router.replace(HOME_PATH)
    }
  } catch {
    // 令牌失效由请求层统一清除登录态并跳回登录页
  }
})
</script>

<template>
  <div class="shell">
    <div class="app">
      <aside class="side" :class="{ collapsed }">
        <div class="brand">
          <div class="brand-logo"><img src="/logo.png" alt="幻彩学堂" /></div>
          <span class="brand-logo-mini">幻</span>
        </div>

        <nav class="nav">
          <template v-for="group in groups" :key="group.key">
            <div
              class="nav-row"
              :class="{ active: isGroupActive(group.key) }"
              @click="onGroupClick(group.key, singleItem(group.key) ? singleItem(group.key)!.path : '')"
            >
              <component :is="groupIcons[group.icon]" class="nav-icon" />
              <span class="nav-title">{{ group.title }}</span>
              <DownOutlined
                v-if="!singleItem(group.key)"
                class="nav-arrow"
                :class="{ open: expanded === group.key }"
              />
            </div>
            <div v-if="!singleItem(group.key) && expanded === group.key && !collapsed" class="nav-sub">
              <div
                v-for="item in group.items"
                :key="item.path"
                class="nav-item"
                :class="{
                  active:
                    route.path === item.path ||
                    (item.path === '/class' && route.path.startsWith('/classDetail')),
                }"
                @click="router.push(item.path)"
              >
                <span class="nav-dot" />
                {{ item.title }}
              </div>
            </div>
          </template>
        </nav>

        <div class="side-foot">
          <div class="app-entry">
            <DesktopOutlined />
            <span>试用手机 App</span>
            <i class="red-dot" />
          </div>
          <div class="user">
            <div class="avatar">幻</div>
            <div class="user-meta">
              <div class="user-name">{{ currentUser.name || '幻彩教育' }}</div>
              <div class="user-ver">{{ currentUser.role === 'admin' ? '管理员' : '版本 v13.0' }}</div>
            </div>
            <span class="logout" @click="logout">退出</span>
          </div>
        </div>
      </aside>

      <section class="main">
        <header class="topbar">
          <span class="collapse-btn" @click="collapsed = !collapsed"><MenuOutlined /></span>

          <div class="tabs">
            <div
              v-for="tab in workTabs"
              :key="tab.path"
              class="tab"
              :class="{ active: route.path === tab.path }"
              @click="router.push(tab.path)"
            >
              <i class="tab-dot" />
              <span class="tab-title">{{ tab.title }}</span>
              <CloseOutlined v-if="tab.closable" class="tab-close" @click.stop="onClose(tab.path)" />
            </div>
            <span v-if="workTabs.length > 1" class="close-all" @click="closeAll">
              <CloseOutlined />
            </span>
          </div>

          <div class="top-actions">
            <a-select
              v-model:value="campus"
              :options="campusOptions"
              size="small"
              :bordered="false"
              style="width: 124px"
            />
            <DesktopOutlined class="action-icon" title="数据大屏" />
            <QuestionCircleOutlined class="action-icon" title="帮助" />
            <DownloadOutlined class="action-icon" title="下载客户端" />
            <BellOutlined class="action-icon" title="通知" />
            <CustomerServiceOutlined class="action-icon" title="在线客服" />
            <div class="top-avatar"><UserOutlined /></div>
          </div>
        </header>

        <div class="content">
          <router-view />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

/* ---------- 左侧导航 ---------- */
.side {
  width: 176px;
  flex: 0 0 176px;
  background: var(--nav-bg);
  color: #a7b4c6;
  display: flex;
  flex-direction: column;
  transition: width 0.16s;
}

.side.collapsed {
  width: 64px;
  flex: 0 0 64px;
}

.side.collapsed .nav-title,
.side.collapsed .nav-arrow,
.side.collapsed .app-entry span,
.side.collapsed .user-meta {
  display: none;
}

.side.collapsed .nav-row {
  padding: 0;
  justify-content: center;
}

.side.collapsed .app-entry {
  justify-content: center;
}

.side.collapsed .user {
  justify-content: center;
}

.brand {
  height: 84px;
  flex: 0 0 84px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-logo img {
  display: block;
  height: 32px;
  width: auto;
}

.brand-logo-mini {
  display: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #e8322a, #c1150e);
  color: #ffe9e7;
  font-size: 15px;
  font-weight: 700;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.08);
}

.side.collapsed .brand-logo {
  display: none;
}

.side.collapsed .brand-logo-mini {
  display: flex;
}

.nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-bottom: 8px;
}

.nav::-webkit-scrollbar {
  width: 0;
}

.nav-row {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 30px;
  padding: 0 22px;
  font-size: 15px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.nav-row:hover {
  background: #17243a;
}

.nav-row.active {
  background: var(--nav-active);
  color: #fff;
}

.nav-icon {
  font-size: 19px;
  flex: 0 0 19px;
}

.nav-title {
  flex: 1;
  white-space: nowrap;
}

.nav-arrow {
  font-size: 11px;
  opacity: 0.55;
  transition: transform 0.2s;
}

.nav-arrow.open {
  transform: rotate(180deg);
}

.nav-sub {
  padding: 2px 0 4px;
  background: #0b1421;
}

.nav-item {
  height: 40px;
  padding: 0 12px 0 54px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.nav-item:hover {
  color: #fff;
  background: #17243a;
}

.nav-item.active {
  color: #fff;
  background: var(--nav-active);
}

.nav-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
  flex: 0 0 4px;
}

.side-foot {
  flex: 0 0 auto;
  border-top: 1px solid #1b2940;
  padding: 10px 14px 14px;
}

.app-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  height: 34px;
  cursor: pointer;
  position: relative;
}

.red-dot {
  position: absolute;
  right: 2px;
  top: 5px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f5222d;
}

.user {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 50%;
  background: #2b3b55;
  color: #dfe7f3;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 13px;
  color: #dfe7f3;
}

.user-ver {
  font-size: 11px;
  color: #64748b;
  margin-top: 1px;
}

.logout {
  margin-left: auto;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
}

.logout:hover {
  color: #fff;
}

/* ---------- 右侧主区 ---------- */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--page-bg);
}

.topbar {
  height: 56px;
  flex: 0 0 56px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
}

.collapse-btn {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 6px;
  background: #e8f2ff;
  color: var(--brand);
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.tabs {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tab {
  flex: 0 0 auto;
  height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
  white-space: nowrap;
  border-radius: 4px;
  position: relative;
}

.tab + .tab::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10px;
  bottom: 10px;
  width: 1px;
  background: #e9ecf1;
}

.tab:hover {
  color: var(--text);
}

.tab.active {
  color: var(--brand);
  font-weight: 500;
  background: #f3f7ff;
}

.tab-dot {
  width: 5px;
  height: 5px;
  flex: 0 0 5px;
  border-radius: 50%;
  background: #c9d6e4;
}

.tab.active .tab-dot {
  background: var(--brand);
}

.tab-close {
  font-size: 10px;
  opacity: 0.4;
  margin-left: 2px;
}

.tab-close:hover {
  opacity: 1;
  color: #f5222d;
}

.close-all {
  width: 20px;
  height: 20px;
  margin-left: 8px;
  flex: 0 0 20px;
  border-radius: 50%;
  border: 1px solid #e0e4ea;
  color: #9aa3ad;
  font-size: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.close-all:hover {
  color: var(--brand);
  border-color: var(--brand);
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-2);
}

.action-icon {
  font-size: 16px;
  cursor: pointer;
}

.action-icon:hover {
  color: var(--brand);
}

.top-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #eef1f6;
  color: #646a73;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  cursor: pointer;
}

.content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
