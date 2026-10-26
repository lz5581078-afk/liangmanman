<template>
  <view>
    <view class="tabs row">
      <view v-for="t in tabs" :key="t.key" class="tab" :class="{ on: tab === t.key }" @tap="tab = t.key">{{ t.label }}</view>
    </view>

    <view v-if="loaded && !shown.length" class="empty">还没有订单</view>

    <view v-for="o in shown" :key="o.id" class="card" @tap="goDetail(o.id)">
      <view class="row between">
        <text class="sname ellipsis grow">{{ o.storeName }}</text>
        <text class="status" :class="{ done: o.status === 'COMPLETED' || o.status === 'CANCELLED' }">{{ o.statusLabel }}</text>
      </view>
      <view class="muted">{{ o.fulfillmentLabel }} · {{ o.payMethodLabel }} · {{ dateTime(o.createdAt) }}</view>
      <view class="items">
        <text v-for="l in o.items" :key="l.productId" class="sub item">{{ l.name }} ×{{ l.quantity }}</text>
      </view>
      <view class="row between">
        <text>合计 <text class="price">{{ money(o.totalAmount) }}</text></text>
        <view class="row">
          <button v-if="o.buyerActions.includes('REVIEW')" class="btn small ghost" style="margin-right: 12rpx" @tap.stop="goReview(o)">去评价</button>
          <button v-if="o.buyerActions.includes('PAY')" class="btn small" @tap.stop="pay(o)">去支付</button>
          <button v-if="o.buyerActions.includes('CONFIRM')" class="btn small" @tap.stop="confirm(o)">确认收货</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { myOrders, confirmOrder } from '../../api'
import { payForOrder } from '../../utils/pay'
import { money, dateTime } from '../../utils/format'

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pay', label: '待付款' },
  { key: 'doing', label: '进行中' },
  { key: 'done', label: '已完成' },
]
const tab = ref('all')
const orders = ref([])
const loaded = ref(false)

const shown = computed(() => {
  const list = orders.value
  if (tab.value === 'pay') return list.filter((o) => o.status === 'PENDING_PAY')
  // 进行中：包含待成团（GROUP_WAITING）在内的所有未结束订单
  if (tab.value === 'doing') return list.filter((o) => o.status === 'GROUP_WAITING' || !['PENDING_PAY', 'COMPLETED', 'CANCELLED'].includes(o.status))
  if (tab.value === 'done') return list.filter((o) => o.status === 'COMPLETED')
  return list
})

async function load() {
  try {
    orders.value = await myOrders()
  } finally {
    loaded.value = true
  }
}

onShow(load)
onPullDownRefresh(async () => {
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

function goReview(o) {
  uni.navigateTo({ url: '/pages/review/edit?orderId=' + o.id + '&storeName=' + encodeURIComponent(o.storeName || '') })
}

function goDetail(id) {
  uni.navigateTo({ url: '/pages/order/detail?id=' + id })
}

async function pay(o) {
  if (await payForOrder(o.id).catch(() => false)) load()
}

function confirm(o) {
  uni.showModal({
    title: '确认已收到货？',
    success: async (r) => {
      if (r.confirm) {
        await confirmOrder(o.id)
        load()
      }
    },
  })
}
</script>

<style lang="scss">
.tabs { background: #fff; padding: 0 12rpx; }
.tab { flex: 1; text-align: center; padding: 24rpx 0; font-size: 30rpx; color: $lm-ink-2; }
.tab.on { color: $lm-green; font-weight: 700; border-bottom: 6rpx solid $lm-green; }
.sname { font-size: 32rpx; font-weight: 700; }
.status { color: $lm-wheat; font-weight: 600; margin-left: 12rpx; }
.status.done { color: $lm-ink-3; }
.items { margin: 12rpx 0; }
.item { margin-right: 20rpx; }
</style>
