import { getAppConfig } from '../api'

/**
 * 微信订阅消息授权。微信规定必须在用户点击（tap）里调用，
 * 所以在「提交订单」「接单」等按钮里调一下；没配置模板、拒绝、非微信端都静默跳过。
 */
let config = null
let loading = null

/** 提前取配置（页面 onLoad 时调），这样点击时不用等网络 */
export function preloadSubscribeConfig() {
  if (config || loading) return loading
  loading = getAppConfig()
    .then((c) => { config = c || {} })
    .catch(() => { config = {} })
    .finally(() => { loading = null })
  return loading
}

function request(templateId) {
  // #ifdef MP-WEIXIN
  if (!templateId) return Promise.resolve(false)
  return new Promise((resolve) => {
    uni.requestSubscribeMessage({
      tmplIds: [templateId],
      success: (r) => resolve(r[templateId] === 'accept'),
      fail: () => resolve(false),
    })
  })
  // #endif
  // #ifndef MP-WEIXIN
  return Promise.resolve(false)
  // #endif
}

/** 买家：订单状态变化提醒（接单、送出、送达） */
export function subscribeOrderNotice() {
  return request(config && config.subscribeOrderTemplate)
}

/** 商家：新订单提醒（电话、短信之外的补充） */
export function subscribeMerchantNotice() {
  return request(config && config.subscribeMerchantTemplate)
}
