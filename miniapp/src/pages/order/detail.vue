<template>
  <view v-if="o">
    <view class="head">
      <view class="st">{{ o.statusLabel }}</view>
      <view class="hint">{{ hint }}</view>
    </view>

    <view class="card">
      <view class="row between">
        <text class="sname">{{ o.storeName }}</text>
        <button class="btn small ghost" @tap="call(o.storePhone)">打电话</button>
      </view>
      <view class="muted">{{ o.fulfillmentLabel }} · {{ o.payMethodLabel }}</view>
      <view v-if="o.addressText" class="sub addr">{{ o.contactName }} {{ o.contactPhone }}　{{ o.addressText }}</view>
      <view v-if="o.expectedDate" class="sub">期望送达：{{ o.expectedDate }}</view>
      <view v-if="o.expressNo" class="sub">快递：{{ o.expressCompany }} {{ o.expressNo }}</view>
      <view v-if="o.cancelReason" class="sub">取消原因：{{ o.cancelReason }}</view>
    </view>

    <view class="card">
      <view v-for="l in o.items" :key="l.productId" class="line row">
        <image class="img" :src="l.image" mode="aspectFill" />
        <view class="grow" style="margin-left: 16rpx">
          <view>{{ l.name }}</view>
          <view class="muted">¥{{ money(l.price) }} × {{ l.quantity }}{{ l.unit }}</view>
        </view>
        <text>¥{{ money(l.amount) }}</text>
      </view>
      <view class="row between sum"><text class="sub">运费</text><text>¥{{ money(o.deliveryFee) }}</text></view>
      <view class="row between sum"><text class="sub">合计</text><text class="price total">{{ money(o.totalAmount) }}</text></view>
      <view v-if="o.remark" class="sub">留言：{{ o.remark }}</view>
    </view>

    <view v-if="o.groupBuyId" class="card row" @tap="goGroup">
      <text class="tag wheat">凑单</text>
      <text class="grow">查看凑单进度</text>
      <text class="sub">›</text>
    </view>

    <view class="card">
      <view class="muted">订单号 {{ o.orderNo }}</view>
      <view v-for="(l, i) in o.logs" :key="i" class="log row">
        <view class="dot" :class="{ last: i === o.logs.length - 1 }" />
        <text class="grow">{{ l.note || l.statusLabel }}</text>
        <text class="muted">{{ dateTime(l.at) }}</text>
      </view>
    </view>

    <view class="footer-space" />
    <view v-if="o.buyerActions.length" class="footer-bar">
      <view class="grow" />
      <button v-if="o.buyerActions.includes('CANCEL')" class="btn warn" @tap="cancel">{{ o.status === 'GROUP_WAITING' ? '退出凑单' : '取消订单' }}</button>
      <button v-if="o.buyerActions.includes('REORDER')" class="btn ghost" style="margin-left: 16rpx" @tap="again">再来一单</button>
      <button v-if="o.buyerActions.includes('REVIEW')" class="btn wheat" style="margin-left: 16rpx" @tap="goReview">去评价</button>
      <button v-if="o.buyerActions.includes('PAY')" class="btn" style="margin-left: 16rpx" @tap="pay">去支付</button>
      <button v-if="o.buyerActions.includes('CONFIRM')" class="btn" style="margin-left: 16rpx" @tap="confirm">确认收货</button>
    </view>
  </view>
  <view v-else class="empty">加载中…</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { orderDetail, cancelOrder, confirmOrder, reorder } from '../../api'
import { setQty } from '../../store/cart'
import { payForOrder } from '../../utils/pay'
import { money, dateTime, call } from '../../utils/format'

const id = ref(0)
const o = ref(null)

const HINTS = {
  PENDING_PAY: '请在 15 分钟内付款，超时自动取消',
  PENDING_ACCEPT: '已通知老板，30 分钟内没接单会自动取消',
  ACCEPTED: '老板已接单，正在备货',
  DELIVERING: '老板正在送货的路上',
  DELIVERED: '货已送到，请检查一下',
  READY_PICKUP: '货已备好，可以到店里拿了',
  PENDING_SHIP: '等老板发快递',
  SHIPPED: '快递已发出，收到后点确认收货',
  COMPLETED: '订单已完成，谢谢惠顾',
  CANCELLED: '订单已取消，已付款的会原路退回',
  GROUP_WAITING: '已付款，等人凑够就发货；凑不成自动全额退款',
}
const hint = computed(() => (o.value ? HINTS[o.value.status] || '' : ''))

async function load() {
  o.value = await orderDetail(id.value)
}

onLoad((q) => {
  id.value = Number(q.id)
})
onShow(() => id.value && load())

async function pay() {
  if (await payForOrder(id.value).catch(() => false)) load()
}

function cancel() {
  const group = o.value.status === 'GROUP_WAITING'
  uni.showModal({
    title: group ? '确定退出凑单？' : '确定取消订单？',
    content: group ? '退出后付的钱会原路退回' : '',
    success: async (r) => {
      if (r.confirm) {
        o.value = await cancelOrder(id.value, group ? '买家退出凑单' : '买家取消')
      }
    },
  })
}

function goReview() {
  uni.navigateTo({ url: '/pages/review/edit?orderId=' + id.value + '&storeName=' + encodeURIComponent(o.value.storeName || '') })
}

function goGroup() {
  uni.navigateTo({ url: '/pages/group/detail?id=' + o.value.groupBuyId })
}

async function again() {
  uni.showLoading({ title: '加载中', mask: true })
  let r
  try {
    r = await reorder(id.value)
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.msg || '出错了', icon: 'none' })
    return
  }
  uni.hideLoading()
  const products = (r && r.products) || []
  if (!products.length) {
    uni.showToast({ title: '这些商品老板都不卖了，进店看看别的', icon: 'none' })
  } else {
    const qs = r.quantities || {}
    products.forEach((p) => setQty(r.storeId, r.storeName, p, Number(qs[p.id] || qs[String(p.id)] || 1)))
    uni.showToast({ title: '已放进购物车', icon: 'none' })
  }
  setTimeout(() => uni.navigateTo({ url: '/pages/store/store?id=' + (r.storeId || o.value.storeId) }), 400)
}

function confirm() {
  uni.showModal({
    title: '确认已收到货？',
    success: async (r) => {
      if (r.confirm) o.value = await confirmOrder(id.value)
    },
  })
}
</script>

<style lang="scss">
.head { background: $lm-green; color: #fff; padding: 40rpx 32rpx 48rpx; }
.st { font-size: 44rpx; font-weight: 800; }
.hint { margin-top: 8rpx; opacity: 0.9; }
.sname { font-size: 34rpx; font-weight: 700; }
.addr { margin-top: 8rpx; }
.line { padding: 12rpx 0; }
.line .img { width: 96rpx; height: 96rpx; border-radius: 10rpx; background: #EEE9DD; }
.sum { padding: 6rpx 0; }
.total { font-size: 40rpx; }
.log { padding: 14rpx 0; }
.dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: $lm-ink-3; margin-right: 20rpx; }
.dot.last { background: $lm-green; }
</style>
