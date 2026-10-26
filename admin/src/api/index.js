import request from '@/utils/request'

const get = (url, params) => request.get(url, { params })
const post = (url, data) => request.post(url, data)
const put = (url, data) => request.put(url, data)
const del = (url) => request.delete(url)

// 登录与当前管理员
export const login = (data) => post('/login', data)
export const getMe = () => get('/me')
export const changePassword = (data) => post('/me/password', data)

// 看板
export const getDashboard = () => get('/dashboard')

// 店铺
export const listStores = (params) => get('/stores', params)
export const getStore = (id) => get(`/stores/${id}`)
export const approveStore = (id) => post(`/stores/${id}/approve`)
export const rejectStore = (id, reason) => post(`/stores/${id}/reject`, { reason })
export const banStore = (id, reason) => post(`/stores/${id}/ban`, { reason })
export const unbanStore = (id) => post(`/stores/${id}/unban`)

// 证件
export const listLicenses = (params) => get('/licenses', params)
export const approveLicense = (id) => post(`/licenses/${id}/approve`)
export const rejectLicense = (id, reason) => post(`/licenses/${id}/reject`, { reason })

// 商品
export const listProducts = (params) => get('/products', params)
export const offShelfProduct = (id, reason) => post(`/products/${id}/off-shelf`, { reason })
export const linkProduct = (id, standardProductId) => post(`/products/${id}/link`, { standardProductId })
export const unlinkProduct = (id) => post(`/products/${id}/unlink`)

// 商品库（具体一款）、搜索同义词
export const listStandardProducts = (params) => get('/standard-products', params)
export const createStandardProduct = (data) => post('/standard-products', data)
export const updateStandardProduct = (id, data) => put(`/standard-products/${id}`, data)
export const deleteStandardProduct = (id) => del(`/standard-products/${id}`)
export const verifyStandardProduct = (id) => post(`/standard-products/${id}/verify`)
export const mergeStandardProduct = (id, intoId) => post(`/standard-products/${id}/merge`, { intoId })
export const listSearchWords = (params) => get('/search-words', params)
export const createSearchWord = (data) => post('/search-words', data)
export const updateSearchWord = (id, data) => put(`/search-words/${id}`, data)
export const deleteSearchWord = (id) => del(`/search-words/${id}`)

// 分类
export const listCategories = () => get('/categories')
export const createCategory = (data) => post('/categories', data)
export const updateCategory = (id, data) => put(`/categories/${id}`, data)

// 订单
export const listOrders = (params) => get('/orders', params)
export const getOrder = (id) => get(`/orders/${id}`)
export const forceCancelOrder = (id, reason) => post(`/orders/${id}/force-cancel`, { reason })

// 凑单
export const listGroupBuys = (params) => get('/group-buys', params)

// 资金
export const listSettlements = (params) => get('/settlements', params)
export const retrySettlement = (id) => post(`/settlements/${id}/retry`)
export const listWithdrawals = (params) => get('/withdrawals', params)
export const listRefunds = (params) => get('/refunds', params)

// 评价
export const listReviews = (params) => get('/reviews', params)
export const deleteReview = (id) => del(`/reviews/${id}`)

// 求购
export const listWanted = (params) => get('/wanted', params)

// 用户
export const listUsers = (params) => get('/users', params)

// 开通区域
export const listRegions = () => get('/regions')
export const createRegion = (data) => post('/regions', data)
export const updateRegion = (id, data) => put(`/regions/${id}`, data)
export const deleteRegion = (id) => del(`/regions/${id}`)

// 轮播图
export const listBanners = () => get('/banners')
export const createBanner = (data) => post('/banners', data)
export const updateBanner = (id, data) => put(`/banners/${id}`, data)
export const deleteBanner = (id) => del(`/banners/${id}`)

// 管理员
export const listAdmins = () => get('/admins')
export const createAdmin = (data) => post('/admins', data)
export const resetAdminPassword = (id, password) => post(`/admins/${id}/reset-password`, { password })
export const toggleAdmin = (id) => post(`/admins/${id}/toggle`)

// 日志
export const listLogs = (params) => get('/logs', params)
export const listNotifyLogs = (params) => get('/notify-logs', params)

// 上传
export const uploadFile = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/files', form)
}
