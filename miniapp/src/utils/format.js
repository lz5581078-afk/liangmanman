export function money(v) {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

export function km(v) {
  if (v === null || v === undefined) return ''
  return v < 1 ? Math.round(v * 1000) + ' 米' : v + ' 公里'
}

export function dateTime(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(5, 16)
}

export function today(offsetDays = 0) {
  const d = new Date(Date.now() + offsetDays * 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 打电话 */
export function call(phone) {
  if (phone) uni.makePhoneCall({ phoneNumber: String(phone) })
}

/** 用微信内置地图导航 */
export function navigate(lat, lng, name, address) {
  if (lat == null || lng == null) {
    uni.showToast({ title: '没有位置信息', icon: 'none' })
    return
  }
  uni.openLocation({ latitude: Number(lat), longitude: Number(lng), name: name || '', address: address || '' })
}

/** 选图并压缩：商家拍照上架、开店拍门头 */
export function pickImages(count = 1) {
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success: (r) => resolve(r.tempFilePaths),
      fail: reject,
    })
  })
}

export const MERCHANT_ACTION_LABELS = {
  ACCEPT: '接单',
  REJECT: '拒单',
  DISPATCH: '出发送货',
  DELIVERED: '已送达',
  COD_RECEIVED: '已收款',
  PICKED_UP: '已提货',
  SHIP: '发快递',
  CANCEL: '取消订单',
}

/** 距离截止还有多久：“2 天 5 小时” / “3 小时 20 分”；已过返回空 */
export function remain(endAt) {
  if (!endAt) return ''
  const t = new Date(String(endAt).replace(/-/g, '/').replace('T', ' ')).getTime() - Date.now()
  if (!(t > 0)) return ''
  const d = Math.floor(t / 86400000)
  const h = Math.floor((t % 86400000) / 3600000)
  const m = Math.floor((t % 3600000) / 60000)
  if (d > 0) return `${d} 天 ${h} 小时`
  if (h > 0) return `${h} 小时 ${m} 分`
  return `${Math.max(m, 1)} 分钟`
}

/** 弹窗输入一段文字，取消返回 null */
export function askText(title, placeholder, content) {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      editable: true,
      placeholderText: placeholder || '',
      content: content || '',
      success: (r) => resolve(r.confirm ? (r.content || '').trim() : null),
      fail: () => resolve(null),
    })
  })
}

/** 二次确认 */
export function confirmBox(title, content, confirmText) {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content: content || '',
      confirmText: confirmText || '确定',
      success: (r) => resolve(r.confirm),
      fail: () => resolve(false),
    })
  })
}
