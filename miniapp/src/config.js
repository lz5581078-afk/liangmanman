// 后端地址。真机调试改成电脑的局域网 IP；上线改成已备案的 https 域名，
// 并在微信公众平台「开发管理 → 服务器域名」里加上 request / uploadFile 合法域名。
export const BASE_URL = 'http://127.0.0.1:8080'

// 开发阶段后端使用模拟登录（app.wx.mock=true）：用本机固定 ID 代替 wx.login 的 code，
// 这样清缓存后还是同一个账号。接入真实 AppID 后改为 false。
export const MOCK_LOGIN = true

// 定位失败时的默认位置（试点县城中心），上线前改成试点县的经纬度
export const DEFAULT_LOCATION = { lat: 34.7466, lng: 113.6253, name: '默认位置' }
