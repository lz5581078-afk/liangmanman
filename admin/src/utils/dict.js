/** 各种状态的中文名与标签颜色。后端给了 statusLabel 时优先用后端的。 */

const make = (pairs) => ({
  options: pairs.map(([value, label]) => ({ value, label })),
  label: Object.fromEntries(pairs.map(([v, l]) => [v, l])),
  type: Object.fromEntries(pairs.map(([v, , t]) => [v, t || 'info']))
})

export const STORE_STATUS = make([
  ['PENDING', '待审核', 'warning'],
  ['APPROVED', '已上线', 'success'],
  ['REJECTED', '已拒绝', 'danger'],
  ['BANNED', '已停用', 'info']
])

export const ORDER_STATUS = make([
  ['PENDING_PAY', '待支付', 'warning'],
  ['GROUP_WAITING', '待成团', 'warning'],
  ['PENDING_ACCEPT', '待接单', 'danger'],
  ['ACCEPTED', '已接单', 'primary'],
  ['DELIVERING', '送货中', 'primary'],
  ['DELIVERED', '已送达', 'success'],
  ['READY_PICKUP', '待自提', 'primary'],
  ['PENDING_SHIP', '待发货', 'warning'],
  ['SHIPPED', '已发货', 'primary'],
  ['COMPLETED', '已完成', 'success'],
  ['CANCELLED', '已取消', 'info']
])

export const LICENSE_STATUS = make([
  ['PENDING', '待审核', 'warning'],
  ['APPROVED', '已通过', 'success'],
  ['REJECTED', '已拒绝', 'danger']
])

export const LICENSE_TYPE = make([
  ['BUSINESS', '营业执照'],
  ['PESTICIDE', '农药经营许可证'],
  ['SEED', '种子经营许可证'],
  ['VET', '兽药经营许可证']
])

export const GROUP_STATUS = make([
  ['ONGOING', '进行中', 'primary'],
  ['SUCCESS', '已成团', 'success'],
  ['WAIT_DECISION', '未凑够·等老板决定', 'warning'],
  ['FAILED', '未成团已退款', 'info']
])

export const SETTLEMENT_STATUS = make([
  ['PENDING_RELEASE', '待解冻', 'warning'],
  ['RELEASED', '已解冻', 'success'],
  ['FAILED', '失败', 'danger']
])

export const WITHDRAW_STATUS = make([
  ['PROCESSING', '处理中', 'warning'],
  ['SUCCESS', '成功', 'success'],
  ['FAILED', '失败', 'danger']
])

export const REFUND_STATUS = make([
  ['PROCESSING', '退款中', 'warning'],
  ['SUCCESS', '已退款', 'success'],
  ['FAILED', '失败', 'danger']
])

export const WANTED_STATUS = make([
  ['OPEN', '求购中', 'success'],
  ['CLOSED', '已关闭', 'info'],
  ['EXPIRED', '已过期', 'info']
])

export const ACCOUNT_STATUS = make([
  ['NONE', '未开户', 'info'],
  ['SUBMITTED', '已提交', 'warning'],
  ['AUDITING', '审核中', 'warning'],
  ['NEED_SIGN', '待签约', 'warning'],
  ['APPROVED', '已开通', 'success'],
  ['REJECTED', '已驳回', 'danger']
])

export const ADMIN_ROLE = make([
  ['ADMIN', '超级管理员', 'danger'],
  ['OPERATOR', '运营', 'primary']
])

export const NORMALIZE_STATUS = make([
  ['PENDING', '待归一', 'warning'],
  ['LINKED', '已归一', 'success']
])
