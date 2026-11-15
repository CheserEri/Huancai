const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(n) {
  return n < 10 ? '0' + n : '' + n
}

/** 把 2026-11-13 转为 11月13日 周五 */
function shortDate(value) {
  if (!value) return ''
  const parts = String(value).slice(0, 10).split('-')
  if (parts.length !== 3) return String(value)
  const date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
  return Number(parts[1]) + '月' + Number(parts[2]) + '日 ' + WEEK[date.getDay()]
}

/** 把 2026-11-13T16:09:16 转为 11-13 16:09 */
function dateTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  const date = text.slice(5, 10)
  const time = text.slice(11, 16)
  return time ? date + ' ' + time : date
}

function timeRange(start, end) {
  const s = start ? String(start).slice(0, 5) : ''
  const e = end ? String(end).slice(0, 5) : ''
  return e ? s + '-' + e : s
}

/** 课时数去掉多余的 .0 */
function lesson(value) {
  if (value === null || value === undefined || value === '') return '0'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return Number.isInteger(num) ? String(num) : num.toFixed(1)
}

function score(value) {
  if (value === null || value === undefined || value === '') return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return Number.isInteger(num) ? String(num) : num.toFixed(1)
}

/** 成绩较上次变化文案 */
function deltaText(delta) {
  if (delta === null || delta === undefined) return ''
  const num = Number(delta)
  if (Number.isNaN(num) || num === 0) return '持平'
  return (num > 0 ? '+' : '') + (Number.isInteger(num) ? num : num.toFixed(1))
}

function deltaClass(delta) {
  if (delta === null || delta === undefined || Number(delta) === 0) return 'flat'
  return Number(delta) > 0 ? 'up' : 'down'
}

function rank(value) {
  return value ? '第' + value + '名' : ''
}

/** 活动优惠规则文案 */
function ruleText(rule) {
  if (!rule) return ''
  const prefix = rule.lessons && Number(rule.lessons) > 0 ? '报满 ' + lesson(rule.lessons) + ' 课时 ' : ''
  const value = Number(rule.discountValue)
  if (rule.discountMode === '折扣') {
    const rate = value > 10 ? value / 10 : value
    return prefix + (Number.isInteger(rate) ? rate : rate.toFixed(1)) + ' 折'
  }
  if (rule.discountMode === '立减') {
    return prefix + '立减 ' + lesson(value) + ' 元'
  }
  if (rule.discountMode === '赠送课时') {
    return prefix + '赠 ' + lesson(value) + ' 课时'
  }
  return prefix + rule.discountMode + ' ' + value
}

module.exports = {
  shortDate,
  dateTime,
  timeRange,
  lesson,
  score,
  deltaText,
  deltaClass,
  rank,
  ruleText,
  pad
}
