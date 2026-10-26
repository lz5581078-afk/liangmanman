/**
 * 扫商品条码 / 农药追溯码，得到搜索关键词。
 * 条码直接用数字；二维码是网址时，取里面最像编号的一段（登记证号如 PD20xxxxxx）。
 * 拍包装识别文字（OCR）在下一批接入。
 */
export function scanForKeyword() {
  return new Promise((resolve, reject) => {
    uni.scanCode({
      scanType: ['barCode', 'qrCode'],
      success: (r) => resolve(extractKeyword(r.result || '')),
      fail: reject,
    })
  })
}

export function extractKeyword(text) {
  const t = String(text).trim()
  const reg = t.match(/(PD|LS|WP|WL|PDN)\d{6,}/i) // 农药登记证号
  if (reg) return reg[0].toUpperCase()
  if (/^https?:\/\//i.test(t)) {
    const parts = t.split(/[/?=&#]/).filter((s) => /\d{6,}/.test(s))
    return parts.length ? parts[parts.length - 1] : t
  }
  return t.slice(0, 32)
}
