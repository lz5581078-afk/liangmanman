import { BASE_URL, MOCK_LOGIN } from '../config'

const TOKEN_KEY = 'lm_token'
const DEVICE_KEY = 'lm_device_id'

let loginPromise = null

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

function deviceId() {
  let id = uni.getStorageSync(DEVICE_KEY)
  if (!id) {
    id = 'dev' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
    uni.setStorageSync(DEVICE_KEY, id)
  }
  return id
}

function loginCode() {
  if (MOCK_LOGIN) return Promise.resolve(deviceId())
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (r) => resolve(r.code),
      fail: reject,
    })
  })
}

/** 静默登录，同一时间只发一次 */
export function ensureLogin(force = false) {
  if (!force && getToken()) return Promise.resolve(getToken())
  if (loginPromise) return loginPromise
  loginPromise = loginCode()
    .then((code) => rawRequest('POST', '/api/auth/wx-login', { code }))
    .then((data) => {
      uni.setStorageSync(TOKEN_KEY, data.token)
      return data.token
    })
    .finally(() => {
      loginPromise = null
    })
  return loginPromise
}

export function logout() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync('lm_uid')
}

function rawRequest(method, url, data) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      timeout: 15000,
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode >= 500 || typeof body.code !== 'number') {
          reject({ code: res.statusCode, msg: '系统繁忙，请稍后再试' })
        } else if (body.code === 0) {
          resolve(body.data)
        } else {
          reject({ code: body.code, msg: body.msg })
        }
      },
      fail: () => reject({ code: -1, msg: '网络不好，请检查网络后重试' }),
    })
  })
}

/**
 * 统一请求：自动带 token；401 时自动重新登录再试一次；失败时默认弹出提示。
 * options.silent = true 不弹提示
 */
export async function request(method, url, data, options = {}) {
  try {
    return await rawRequest(method, url, data)
  } catch (e) {
    if (e.code === 401 && !options.retried) {
      await ensureLogin(true)
      return request(method, url, data, { ...options, retried: true })
    }
    if (!options.silent) {
      uni.showToast({ title: e.msg || '出错了', icon: 'none', duration: 2500 })
    }
    throw e
  }
}

export const get = (url, data, options) => request('GET', url, data, options)
export const post = (url, data, options) => request('POST', url, data, options)
export const put = (url, data, options) => request('PUT', url, data, options)
export const del = (url, data, options) => request('DELETE', url, data, options)

/** 上传一张图片，返回 URL */
export async function uploadImage(filePath) {
  await ensureLogin()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/api/files',
      filePath,
      name: 'file',
      header: { Authorization: 'Bearer ' + getToken() },
      success: (res) => {
        let body = {}
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          body = {}
        }
        if (body.code === 0) {
          resolve(body.data.url)
        } else {
          uni.showToast({ title: body.msg || '图片上传失败', icon: 'none' })
          reject(body)
        }
      },
      fail: (e) => {
        uni.showToast({ title: '图片上传失败，请重试', icon: 'none' })
        reject(e)
      },
    })
  })
}

/**
 * 通用上传：把一个文件（字段名 file）传到指定接口，返回接口的 data。
 * 用于 OCR 识别（返回识别结果）、开户证件照（返回 imageId）等。
 * options.silent = true 不弹失败提示
 */
export async function uploadTo(url, filePath, options = {}) {
  await ensureLogin()
  const once = () =>
    new Promise((resolve, reject) => {
      uni.uploadFile({
        url: BASE_URL + url,
        filePath,
        name: 'file',
        header: { Authorization: 'Bearer ' + getToken() },
        timeout: 30000,
        success: (res) => {
          let body = {}
          try {
            body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data || {}
          } catch (e) {
            body = {}
          }
          if (body.code === 0) resolve(body.data)
          else reject({ code: typeof body.code === 'number' ? body.code : res.statusCode, msg: body.msg || '上传失败，请重试' })
        },
        fail: () => reject({ code: -1, msg: '上传失败，请检查网络后重试' }),
      })
    })
  try {
    return await once()
  } catch (e) {
    if (e.code === 401) {
      await ensureLogin(true)
      try {
        return await once()
      } catch (e2) {
        e = e2
      }
    }
    if (!options.silent) uni.showToast({ title: e.msg || '上传失败', icon: 'none' })
    throw e
  }
}
