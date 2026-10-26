import { getMe, shareVisit } from '../api'
import { getToken, ensureLogin } from '../api/request'

const UID_KEY = 'lm_uid'

/** 当前用户 id（同步取缓存，用于分享路径里的 from）；没有则返回空字符串 */
export function cachedUserId() {
  return uni.getStorageSync(UID_KEY) || ''
}

/** 拉一次 /api/me 并缓存用户 id；失败静默 */
export async function loadUserId() {
  if (cachedUserId()) return cachedUserId()
  try {
    if (!getToken()) await ensureLogin()
    const me = await getMe()
    if (me && me.id) uni.setStorageSync(UID_KEY, me.id)
    return (me && me.id) || ''
  } catch (e) {
    return ''
  }
}

/** 拼分享路径：自动加上 from=当前用户id */
export function sharePath(path) {
  const uid = cachedUserId()
  if (!uid) return path
  return path + (path.includes('?') ? '&' : '?') + 'from=' + uid
}

/**
 * 解析扫小程序码进来的 scene，如 "g=1" / "s=1" / "p=1"，返回对象 { g: '1' }
 */
export function parseScene(q) {
  if (!q || !q.scene) return {}
  const out = {}
  decodeURIComponent(q.scene)
    .split('&')
    .forEach((kv) => {
      const [k, v] = kv.split('=')
      if (k) out[k] = v
    })
  return out
}

/** 页面 onLoad 时调用：带 from 且已登录，就记录一次分享带来的访问（失败静默） */
export function recordShareVisit(q, targetType, targetId) {
  const from = q && Number(q.from)
  if (!from || !targetId || !getToken()) return
  if (String(from) === String(cachedUserId())) return
  shareVisit({ sharerId: from, targetType, targetId: Number(targetId) }).catch(() => {})
}
