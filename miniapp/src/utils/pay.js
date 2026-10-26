import { payOrder, mockPaySuccess } from '../api'

/**
 * 发起支付。开发环境后端返回 mock=true，直接模拟成功；正式环境调起微信支付。
 * 成功 resolve(true)，用户取消 resolve(false)。
 */
export async function payForOrder(orderId) {
  const p = await payOrder(orderId)
  if (p.mock) {
    const ok = await new Promise((resolve) => {
      uni.showModal({
        title: '模拟支付',
        content: '开发环境不会真扣钱，点“确定”模拟支付成功',
        success: (r) => resolve(r.confirm),
      })
    })
    if (!ok) return false
    await mockPaySuccess(p.outTradeNo)
    return true
  }
  return new Promise((resolve) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: p.timeStamp,
      nonceStr: p.nonceStr,
      package: p.packageValue,
      signType: p.signType,
      paySign: p.paySign,
      success: () => resolve(true),
      fail: () => resolve(false),
    })
  })
}
