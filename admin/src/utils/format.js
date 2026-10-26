/** 金额：保留 2 位小数，空值显示 - */
export function money(v) {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isFinite(n) ? n.toFixed(2) : '-'
}

/** 带 ¥ 的金额 */
export function yuan(v) {
  const m = money(v)
  return m === '-' ? m : `¥${m}`
}

/** 时间：显示到分钟，如 2026-10-24 12:49 */
export function time(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}

/** 空值兜底 */
export function text(v) {
  return v === null || v === undefined || v === '' ? '-' : v
}

/** 把 { records, total } 或数组统一成 { records, total } */
export function toPage(data) {
  if (Array.isArray(data)) return { records: data, total: data.length }
  return { records: data?.records || [], total: data?.total || 0 }
}

/** 列表兼容：数组或分页对象都取出数组 */
export function toList(data) {
  return Array.isArray(data) ? data : data?.records || []
}

/** 去掉空字符串 / null 的查询参数 */
export function cleanParams(obj) {
  const out = {}
  Object.entries(obj).forEach(([k, v]) => {
    if (v !== '' && v !== null && v !== undefined) out[k] = v
  })
  return out
}
