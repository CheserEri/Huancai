<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AccountBookOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  CloseOutlined,
  DesktopOutlined,
  DownOutlined,
  GiftOutlined,
  HomeOutlined,
  IdcardOutlined,
  LogoutOutlined,
  MenuOutlined,
  NotificationOutlined,
  PrinterOutlined,
  ReadOutlined,
  RightOutlined,
  RocketOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { menuIndex } from '../config/menu'
import { message } from 'ant-design-vue'
import {
  downloadAppPackage,
  fetchAppDownloadInfo,
  fetchProfile,
  logout as logoutRequest,
  type AppDownloadInfo,
} from '../api/client'
import {
  authState,
  clearLogin,
  hasMenuPermission,
  HOME_PATH,
  roleText,
  updateProfile,
  visibleMenuGroups,
  type AuthUser,
} from '../stores/auth'
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
const collapsed = ref(false)

watch(
  () => route.path,
  (path) => {
    if (path === '/profile') {
      openTab('/profile', '个人中心')
      return
    }
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

function goProfile() {
  router.push('/profile')
}

/* ---------- 试用手机 App：仅安卓，每个账号每日限下载 5 次 ---------- */
const appDialogOpen = ref(false)
const appInfo = ref<AppDownloadInfo | null>(null)
const appInfoLoading = ref(false)
const appDownloading = ref(false)

const apkSizeText = computed(() => {
  const size = appInfo.value?.sizeBytes ?? 0
  if (!size) return '-'
  return `${(size / 1024 / 1024).toFixed(1)} MB`
})

async function loadAppInfo() {
  appInfoLoading.value = true
  try {
    appInfo.value = await fetchAppDownloadInfo()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    appInfoLoading.value = false
  }
}

async function openAppDialog() {
  appDialogOpen.value = true
  await loadAppInfo()
}

async function downloadApp() {
  if (appDownloading.value) return
  appDownloading.value = true
  try {
    const { blob, fileName } = await downloadAppPackage()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 10000)
    message.success('安装包已开始下载，请在手机「文件管理」中打开安装（仅支持安卓）')
    appDialogOpen.value = false
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    appDownloading.value = false
    // 无论成功失败都刷新剩余次数，避免页面显示与实际额度不一致
    await loadAppInfo()
  }
}

/** 当前账号可见的菜单：由登录账号的菜单权限决定。 */
const groups = computed(() => visibleMenuGroups(authState.permissions))
const currentUser = computed<AuthUser>(
  () => authState.user ?? { staffId: 0, name: '', role: '', permissions: [] },
)

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
          <div class="app-entry" @click="openAppDialog">
            <DesktopOutlined />
            <span>试用手机 App</span>
          </div>
          <a
            class="app-entry"
            href="/downloads/HcReceiptPrinter.exe"
            download
            title="下载后在收银电脑上双击运行，点打印即可直接出纸（无需再弹浏览器打印框）"
          >
            <PrinterOutlined />
            <span>打印小助手下载</span>
          </a>
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
            <a-popover
              placement="bottomRight"
              trigger="hover"
              :arrow="false"
              overlay-class-name="account-popover"
            >
              <template #content>
                <div class="account-card">
                  <div class="account-head">
                    <div class="account-avatar"><UserOutlined /></div>
                    <div class="account-head-text">
                      <div class="account-name">{{ currentUser.name || '未登录' }}</div>
                      <div class="account-phone">{{ currentUser.phone || '未绑定手机号' }}</div>
                    </div>
                  </div>
                  <div class="account-entry" @click="goProfile">
                    <IdcardOutlined class="account-entry-icon" />
                    <div class="account-entry-text">
                      <div class="account-entry-title">个人中心</div>
                      <div class="account-entry-sub">
                        {{ currentUser.account || currentUser.name
                        }}{{ currentUser.role ? `（${roleText(currentUser.role)}）` : '' }}
                      </div>
                    </div>
                    <RightOutlined class="account-entry-arrow" />
                  </div>
                  <div class="account-logout" @click="logout">
                    <LogoutOutlined />
                    <span>退出登录</span>
                  </div>
                </div>
              </template>
              <div class="top-avatar" title="账号信息"><UserOutlined /></div>
            </a-popover>
          </div>
        </header>

        <div class="content">
          <router-view />
        </div>
      </section>
    </div>

    <a-modal
      v-model:open="appDialogOpen"
      :footer="null"
      :width="460"
      centered
      title="试用手机 App"
      class="app-download-modal"
    >
      <div class="app-download">
        <div class="app-download-tip">
          <span class="app-download-badge">仅安卓</span>
          <span>本安装包仅支持安卓手机（Android 7.0 及以上），苹果 iOS 设备无法安装。</span>
        </div>
        <ul class="app-download-list">
          <li>版本：v{{ appInfo?.version ?? '-' }}</li>
          <li>大小：{{ apkSizeText }}</li>
          <li>更新时间：{{ appInfo?.updatedAt ?? '-' }}</li>
          <li>
            今日剩余下载次数：
            <b :class="{ danger: (appInfo?.remaining ?? 0) <= 0 }">{{ appInfo?.remaining ?? '-' }}</b>
            / {{ appInfo?.limit ?? 5 }} 次（每个账号每日限下载 {{ appInfo?.limit ?? 5 }} 次）
          </li>
        </ul>
        <div class="app-download-actions">
          <a-button @click="appDialogOpen = false">取消</a-button>
          <a-button
            type="primary"
            :loading="appDownloading"
            :disabled="appInfoLoading || (appInfo?.remaining ?? 0) <= 0"
            @click="downloadApp"
          >
            {{ (appInfo?.remaining ?? 0) <= 0 ? '今日次数已用完' : '立即下载安装包' }}
          </a-button>
        </div>
      </div>
    </a-modal>
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
.side.collapsed .app-entry span {
  display: none;
}

.side.collapsed .nav-row {
  padding: 0;
  justify-content: center;
}

.side.collapsed .app-entry {
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

a.app-entry {
  color: inherit;
  text-decoration: none;
}

a.app-entry:hover,
.app-entry:hover {
  color: #40a9ff;
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

.top-avatar:hover {
  background: #e3e8f0;
}

.content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

/* ---------- 试用手机 App 下载弹窗 ---------- */
.app-download-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  background: #fffbe6;
  font-size: 13px;
  line-height: 1.6;
  color: #874d00;
}

.app-download-badge {
  flex: 0 0 auto;
  padding: 0 6px;
  border-radius: 4px;
  background: #fa8c16;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  margin-top: 2px;
}

.app-download-list {
  margin: 14px 0 18px;
  padding: 0;
  list-style: none;
  font-size: 13px;
  line-height: 2;
  color: var(--text-2);
}

.app-download-list b {
  color: var(--brand);
}

.app-download-list b.danger {
  color: #f5222d;
}

.app-download-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

<style>
/* 头像悬浮卡片由 Popover 挂载到 body，需使用非 scoped 样式 */
.account-popover .ant-popover-inner {
  padding: 0;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(15, 23, 42, 0.12);
}

.account-card {
  width: 288px;
  padding: 18px 18px 0;
}

.account-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 16px;
}

.account-avatar {
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  border-radius: 50%;
  background: #eceff3;
  color: #a8b0ba;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}

.account-head-text {
  min-width: 0;
}

.account-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--text);
}

.account-phone {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-3);
}

.account-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0 14px;
  cursor: pointer;
}

.account-entry:hover .account-entry-title {
  color: var(--brand);
}

.account-entry-icon {
  font-size: 18px;
  color: var(--text-2);
}

.account-entry-text {
  min-width: 0;
}

.account-entry-title {
  font-size: 14px;
  color: var(--text);
}

.account-entry-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.account-entry-arrow {
  margin-left: auto;
  font-size: 11px;
  color: #c3c8cf;
}

.account-logout {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 -18px;
  padding: 13px 18px;
  border-top: 1px solid var(--line);
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
}

.account-logout:hover {
  color: #f5222d;
}
</style>
