import { reactive } from 'vue'
import { menuGroups, type MenuGroup } from '../config/menu'

export const AUTH_KEY = 'hc_auth'
export const TOKEN_KEY = 'hc_token'
export const USER_KEY = 'hc_user'
export const PERMISSIONS_KEY = 'hc_permissions'

/** 全部菜单权限标记，管理员固定拥有。 */
export const ALL_PERMISSION = '*'
/** 工作台是登录后的默认页面，始终可见。 */
export const HOME_PATH = '/desktop'

export interface AuthUser {
  staffId: number
  name: string
  role: string
  campusId?: number
  permissions: string[]
}

function readUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<AuthUser>
    if (!parsed || typeof parsed.name !== 'string') return null
    const permissions = readPermissions()
    return {
      staffId: Number(parsed.staffId ?? 0),
      name: parsed.name,
      role: parsed.role ?? '',
      campusId: parsed.campusId,
      permissions,
    }
  } catch {
    return null
  }
}

function readPermissions(): string[] {
  try {
    const raw = localStorage.getItem(PERMISSIONS_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

export const authState = reactive<{ user: AuthUser | null; permissions: string[] }>({
  user: readUser(),
  permissions: readPermissions(),
})

export function isAuthed(): boolean {
  return localStorage.getItem(AUTH_KEY) === '1' && !!localStorage.getItem(TOKEN_KEY)
}

/** 登录成功后写入登录态；权限随账号一起保存。 */
export function saveLogin(user: AuthUser & { token: string }) {
  const profile: AuthUser = {
    staffId: user.staffId,
    name: user.name,
    role: user.role,
    campusId: user.campusId,
    permissions: user.permissions ?? [],
  }
  localStorage.setItem(TOKEN_KEY, user.token)
  localStorage.setItem(USER_KEY, JSON.stringify(profile))
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(profile.permissions))
  localStorage.setItem(AUTH_KEY, '1')
  authState.user = profile
  authState.permissions = profile.permissions
}

/** 进入系统时用 /auth/me 的结果刷新账号信息与最新权限。 */
export function updateProfile(user: Omit<AuthUser, 'permissions'> & { permissions: string[] }) {
  const profile: AuthUser = {
    staffId: user.staffId,
    name: user.name,
    role: user.role,
    campusId: user.campusId,
    permissions: user.permissions ?? [],
  }
  localStorage.setItem(USER_KEY, JSON.stringify(profile))
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(profile.permissions))
  authState.user = profile
  authState.permissions = profile.permissions
}

export function clearLogin() {
  localStorage.removeItem(AUTH_KEY)
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(PERMISSIONS_KEY)
  authState.user = null
  authState.permissions = []
}

export function hasAllPermissions(permissions: string[] = authState.permissions): boolean {
  return permissions.includes(ALL_PERMISSION)
}

/** 当前账号是否可访问某个菜单路径。 */
export function hasMenuPermission(path: string, permissions: string[] = authState.permissions): boolean {
  if (path === HOME_PATH) return true
  if (hasAllPermissions(permissions)) return true
  return permissions.includes(path)
}

/** 按当前权限过滤菜单：没有可见子项的菜单组整体隐藏。 */
export function visibleMenuGroups(permissions: string[] = authState.permissions): MenuGroup[] {
  const all = hasAllPermissions(permissions)
  return menuGroups
    .map((group) => ({
      ...group,
      items: all ? group.items : group.items.filter((item) => item.path === HOME_PATH || permissions.includes(item.path)),
    }))
    .filter((group) => group.items.length > 0)
}
