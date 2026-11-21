/**
 * 本地小票打印小助手（HcReceiptPrinter.exe）通道。
 *
 * 浏览器无法直接写 USB 小票机，因此在打印电脑上常驻一个本地服务
 * （127.0.0.1:18666）：网页把订单数据发过去，由它直发 ESC/POS 指令，
 * 不经 Windows 打印队列，不会出现乱码或卡在队列里。
 */
const BASE = 'http://127.0.0.1:18666'
const PROBE_TIMEOUT = 900
const PRINT_TIMEOUT = 6000
const CACHE_TTL = 15000

export interface LocalPrintResult {
  /** 小票是否已成功写入打印机 */
  ok: boolean
  /** 本地小助手是否在运行；在运行但打印失败时应把 message 直接提示给用户 */
  reachable: boolean
  bytes?: number
  message?: string
}

let cachedAt = 0
let cachedReachable = false

async function request(
  path: string,
  body: unknown,
  timeout: number,
): Promise<{ reachable: boolean; data: Record<string, unknown> | null }> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  try {
    const response = await fetch(BASE + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
    const text = await response.text()
    let data: Record<string, unknown> | null = null
    try {
      data = text ? (JSON.parse(text) as Record<string, unknown>) : null
    } catch {
      data = null
    }
    return { reachable: true, data }
  } catch {
    return { reachable: false, data: null }
  } finally {
    clearTimeout(timer)
  }
}

/** 本地小助手是否在运行（结果短时缓存，避免每次打印都探测）。 */
export async function localPrinterOnline(force = false): Promise<boolean> {
  if (!force && cachedAt && Date.now() - cachedAt < CACHE_TTL) return cachedReachable
  const result = await request('/health', undefined, PROBE_TIMEOUT)
  cachedAt = Date.now()
  cachedReachable = result.reachable
  return result.reachable
}

async function post(path: string, payload: unknown): Promise<LocalPrintResult> {
  const result = await request(path, payload, PRINT_TIMEOUT)
  cachedAt = Date.now()
  cachedReachable = result.reachable

  if (!result.reachable || !result.data) {
    return { ok: false, reachable: false }
  }
  if (result.data.ok === true) {
    return { ok: true, reachable: true, bytes: Number(result.data.bytes) || 0 }
  }
  return {
    ok: false,
    reachable: true,
    message:
      typeof result.data.message === 'string' && result.data.message
        ? result.data.message
        : '小票打印失败，请检查打印机是否开机、缺纸',
  }
}

/** 打印订单收费小票。 */
export function printReceiptLocally(order: Record<string, unknown>) {
  return post('/print/receipt', order)
}

/** 打印听课证。 */
export function printTicketLocally(ticket: Record<string, unknown>) {
  return post('/print/ticket', ticket)
}
