import { reactive } from 'vue'

const TOKEN_KEY = 'lm_admin_token'
const ADMIN_KEY = 'lm_admin_info'

function readAdmin() {
  try {
    return JSON.parse(localStorage.getItem(ADMIN_KEY) || 'null')
  } catch {
    return null
  }
}

/** 当前登录的管理员（全局共享） */
export const session = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  admin: readAdmin()
})

export function setSession(token, admin) {
  session.token = token
  localStorage.setItem(TOKEN_KEY, token)
  setAdmin(admin)
}

export function setAdmin(admin) {
  session.admin = admin || null
  if (admin) localStorage.setItem(ADMIN_KEY, JSON.stringify(admin))
  else localStorage.removeItem(ADMIN_KEY)
}

export function clearSession() {
  session.token = ''
  session.admin = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ADMIN_KEY)
}

export const isSuperAdmin = () => session.admin?.role === 'ADMIN'
