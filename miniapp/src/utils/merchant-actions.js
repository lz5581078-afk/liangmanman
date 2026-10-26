import { merchantAct } from '../api'
import { subscribeMerchantNotice } from './subscribe'

function ask(title, placeholder) {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      editable: true,
      placeholderText: placeholder,
      success: (r) => resolve(r.confirm ? (r.content || '').trim() : null),
    })
  })
}

function confirm(title, content) {
  return new Promise((resolve) => {
    uni.showModal({ title, content: content || '', success: (r) => resolve(r.confirm) })
  })
}

/**
 * 执行商家订单操作，需要填写内容或二次确认的在这里弹窗。
 * 返回更新后的订单；用户取消返回 null。
 */
export async function runMerchantAction(order, action) {
  // 接单时顺便授权下一次的新订单提醒（一次性订阅，每次接单补一次）
  if (action === 'ACCEPT') await subscribeMerchantNotice()
  let body = {}
  switch (action) {
    case 'REJECT': {
      const reason = await ask('拒单原因', '如：缺货、送不到')
      if (reason === null) return null
      body = { reason: reason || '商家拒单' }
      break
    }
    case 'CANCEL': {
      const reason = await ask('取消原因（会告诉买家）', '如：缺货')
      if (reason === null) return null
      if (!reason) {
        uni.showToast({ title: '请填写取消原因', icon: 'none' })
        return null
      }
      body = { reason }
      break
    }
    case 'SHIP': {
      const company = await ask('快递公司', '如：中通、邮政')
      if (!company) return null
      const no = await ask('快递单号', '')
      if (!no) return null
      body = { expressCompany: company, expressNo: no }
      break
    }
    case 'COD_RECEIVED':
      if (!(await confirm('确认已收到钱？', `应收 ¥${order.totalAmount}`))) return null
      break
    case 'PICKED_UP':
      if (!(await confirm('买家已经把货拿走了？', order.payMethod === 'COD' ? `记得收款 ¥${order.totalAmount}` : ''))) return null
      break
    default:
      break
  }
  const updated = await merchantAct(order.id, action.toLowerCase(), body)
  uni.showToast({ title: '已' + (updated.statusLabel || '处理'), icon: 'none' })
  return updated
}
