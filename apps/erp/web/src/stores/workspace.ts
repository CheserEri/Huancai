import { reactive } from 'vue'

export interface WorkTab {
  path: string
  title: string
  closable: boolean
}

const HOME: WorkTab = { path: '/desktop', title: '工作台', closable: false }

export const workTabs = reactive<WorkTab[]>([{ ...HOME }])

export function openTab(path: string, title: string) {
  if (!workTabs.some((tab) => tab.path === path)) {
    workTabs.push({ path, title, closable: path !== HOME.path })
  }
}

export function closeTab(path: string): string | null {
  const index = workTabs.findIndex((tab) => tab.path === path)
  if (index < 0 || !workTabs[index].closable) return null
  workTabs.splice(index, 1)
  const fallback = workTabs[Math.max(0, index - 1)]
  return fallback ? fallback.path : HOME.path
}
