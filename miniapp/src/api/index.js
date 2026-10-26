// 所有后端接口集中在这里，页面只调这些函数
import { get, post, put, del, uploadTo } from './request'
import { BASE_URL } from '../config'

// ---------- 账号 ----------
export const getMe = () => get('/api/me')
export const updateMe = (data) => put('/api/me', data)

// ---------- 逛店 / 搜索（不登录也能用） ----------
export const nearbyStores = (lat, lng, categoryId) =>
  get('/api/stores/nearby', categoryId ? { lat, lng, categoryId } : { lat, lng })
export const storeDetail = (id, loc) => get('/api/stores/' + id, loc ? { lat: loc.lat, lng: loc.lng } : {})
export const productDetail = (id, loc) => get('/api/products/' + id, loc ? { lat: loc.lat, lng: loc.lng } : {})
/** 搜索（平铺、可翻页）。params: { sort, delivery, categoryId, page, size }，空的不传 */
export const search = (keyword, loc, params = {}) => {
  const q = { keyword }
  if (loc && loc.lat != null) Object.assign(q, { lat: loc.lat, lng: loc.lng })
  for (const k of ['sort', 'delivery', 'categoryId', 'page', 'size']) {
    if (params[k] != null && params[k] !== '') q[k] = params[k]
  }
  return get('/api/search', q)
}
/** 同款比价：这一款在各家店的价格。params: { sort } */
export const skuOffers = (id, loc, params = {}) => {
  const q = {}
  if (loc && loc.lat != null) Object.assign(q, { lat: loc.lat, lng: loc.lng })
  if (params.sort) q.sort = params.sort
  return get('/api/skus/' + id + '/offers', q)
}
/** 按条码 / 登记证号+规格找一款；没有时返回 null。params: { barcode } 或 { regNo, spec } */
export const skuLookup = (params) => get('/api/skus/lookup', params, { silent: true })
export const categories = () => get('/api/categories')
export const standardProducts = (keyword, categoryId) => {
  const q = {}
  if (keyword) q.keyword = keyword
  if (categoryId) q.categoryId = categoryId
  return get('/api/standard-products', q)
}

// ---------- 收货地址 ----------
export const listAddresses = () => get('/api/addresses')
export const addAddress = (data) => post('/api/addresses', data)
export const updateAddress = (id, data) => put('/api/addresses/' + id, data)
export const deleteAddress = (id) => del('/api/addresses/' + id)

// ---------- 买家订单 ----------
export const previewOrder = (data) => post('/api/orders/preview', data)
export const createOrder = (data) => post('/api/orders', data)
export const myOrders = (status) => get('/api/orders', status ? { status } : {})
export const orderDetail = (id) => get('/api/orders/' + id)
export const payOrder = (id) => post('/api/orders/' + id + '/pay')
export const mockPaySuccess = (outTradeNo) => post('/api/pay/mock/' + outTradeNo + '/success')
export const cancelOrder = (id, reason) => post('/api/orders/' + id + '/cancel', { reason })
export const confirmOrder = (id) => post('/api/orders/' + id + '/confirm')

// ---------- 商家 ----------
export const myStore = () => get('/api/merchant/store')
export const openStore = (data) => post('/api/merchant/store', data)
export const updateStore = (data) => put('/api/merchant/store', data)
export const myProducts = (onShelf) => get('/api/merchant/products', onShelf === undefined ? {} : { onShelf })
export const createProduct = (data) => post('/api/merchant/products', data)
export const updateProduct = (id, data) => put('/api/merchant/products/' + id, data)
export const setOnShelf = (id, on) => post('/api/merchant/products/' + id + (on ? '/on-shelf' : '/off-shelf'))
export const merchantOrders = (tab) => get('/api/merchant/orders', { tab })
export const merchantOrder = (id) => get('/api/merchant/orders/' + id)
export const merchantAct = (id, action, body) => post('/api/merchant/orders/' + id + '/' + action, body || {})
export const deliverySheet = (date) => get('/api/merchant/delivery-sheet', date ? { date } : {})

// ======================= 第二批 =======================
const locQ = (loc) => (loc && loc.lat != null ? { lat: loc.lat, lng: loc.lng } : {})
const pageQ = (page, size) => (size ? { page: page || 1, size } : { page: page || 1 })

// ---------- 订单：再来一单、评价 ----------
export const reorder = (id) => get('/api/orders/' + id + '/reorder', {}, { silent: true })
export const reviewOrder = (id, data) => post('/api/orders/' + id + '/review', data)

// ---------- 凑单（买家） ----------
export const groupBuyDetail = (id, loc) => get('/api/group-buys/' + id, locQ(loc))
export const nearbyGroupBuys = (loc) => get('/api/group-buys/nearby', locQ(loc))

// ---------- 凑单（商家） ----------
export const merchantGroupBuys = () => get('/api/merchant/group-buys')
export const createGroupBuy = (data) => post('/api/merchant/group-buys', data)
export const decideGroupBuy = (id, action) => post('/api/merchant/group-buys/' + id + '/decide', { action })
export const closeGroupBuy = (id) => post('/api/merchant/group-buys/' + id + '/close')

// ---------- 评价 ----------
export const storeReviews = (storeId, page) => get('/api/stores/' + storeId + '/reviews', pageQ(page))
export const merchantReviews = (page) => get('/api/merchant/reviews', pageQ(page))
export const replyReview = (id, reply) => post('/api/merchant/reviews/' + id + '/reply', { reply })

// ---------- 求购 ----------
export const createWanted = (data) => post('/api/wanted', data)
export const myWanted = () => get('/api/wanted/mine')
export const closeWanted = (id) => post('/api/wanted/' + id + '/close')
export const merchantWanted = () => get('/api/merchant/wanted')
export const replyWanted = (id, data) => post('/api/merchant/wanted/' + id + '/reply', data)

// ---------- 商家：经营数据、证件 ----------
export const merchantStats = () => get('/api/merchant/stats')
export const myLicenses = () => get('/api/merchant/licenses')
export const addLicense = (data) => post('/api/merchant/licenses', data)

// ---------- 收款开户、余额、提现 ----------
export const merchantAccount = () => get('/api/merchant/account')
export const submitOnboarding = (data) => post('/api/merchant/onboarding', data)
export const merchantBalance = () => get('/api/merchant/balance')
export const withdraw = (amount) => post('/api/merchant/withdraw', { amount })
export const withdrawals = (page) => get('/api/merchant/withdrawals', pageQ(page))
export const settlements = (page) => get('/api/merchant/settlements', pageQ(page))

// ---------- 赊账本 ----------
export const creditCustomers = (keyword) => get('/api/merchant/credit/customers', keyword ? { keyword } : {})
export const addCreditCustomer = (data) => post('/api/merchant/credit/customers', data)
export const updateCreditCustomer = (id, data) => put('/api/merchant/credit/customers/' + id, data)
export const creditRecords = (id, page) => get('/api/merchant/credit/customers/' + id + '/records', pageQ(page))
export const addCreditRecord = (id, data) => post('/api/merchant/credit/customers/' + id + '/records', data)

// ---------- 轮播、分享 ----------
export const banners = (loc) => get('/api/banners', locQ(loc), { silent: true })
export const shareVisit = (data) => post('/api/share/visit', data, { silent: true })

// ---------- 上传类（multipart） ----------
export const ocrProduct = (filePath, options) => uploadTo('/api/ocr/product', filePath, options)
export const uploadOnboardingImage = (filePath) => uploadTo('/api/merchant/onboarding/image', filePath)

// ---------- 小程序码（PNG 图片，不是 JSON） ----------
export const qrcodeUrl = (type, id) => BASE_URL + '/api/qrcode?type=' + type + '&id=' + id

// ---------- 小程序配置（订阅消息模板） ----------
export const getAppConfig = () => get('/api/app-config')
