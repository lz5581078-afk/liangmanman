<template>
  <view class="page">
    <view class="head">
      <view class="h-label">可提现（元）</view>
      <view class="h-num">{{ money(bal.available) }}</view>
      <view class="h-pending">待解冻 ¥{{ money(bal.pending) }}　<text class="h-note">订单完成后自动到可提现</text></view>
      <button class="btn big wd-btn" :disabled="!(bal.available > 0)" @tap="doWithdraw">提现到银行卡</button>
    </view>

    <view v-if="acc && acc.status !== 'APPROVED'" class="card guide" @tap="goOnboarding">
      <view class="g-title">还没开通收款账户</view>
      <view class="sub">开通后，农户微信付的钱才能提现到你的银行卡。</view>
      <view class="g-go">去开通 ›</view>
    </view>

    <view class="tabs row">
      <view class="tab" :class="{ on: tab === 'in' }" @tap="switchTab('in')">收入明细</view>
      <view class="tab" :class="{ on: tab === 'out' }" @tap="switchTab('out')">提现记录</view>
    </view>

    <view v-if="tab === 'in'">
      <view v-if="inc.loaded.value && !inc.list.value.length" class="empty">还没有收入</view>
      <view v-if="inc.list.value.length" class="card list">
        <view v-for="r in inc.list.value" :key="r.id" class="line row">
          <view class="grow">
            <view>订单 {{ r.orderNo || r.orderId }}</view>
            <view class="muted">{{ dateTime(r.createdAt) }}</view>
          </view>
          <view class="right">
            <view class="amt in">+{{ money(r.amount) }}</view>
            <view class="st" :class="r.status">{{ r.statusLabel || r.status }}</view>
          </view>
        </view>
      </view>
      <view v-if="inc.list.value.length" class="muted end">{{ inc.loading.value ? '加载中…' : inc.finished.value ? '没有更多了' : '上拉加载更多' }}</view>
    </view>

    <view v-else>
      <view v-if="out.loaded.value && !out.list.value.length" class="empty">还没提现过</view>
      <view v-if="out.list.value.length" class="card list">
        <view v-for="r in out.list.value" :key="r.id" class="line row">
          <view class="grow">
            <view>提现到银行卡</view>
            <view class="muted">{{ dateTime(r.createdAt) }}</view>
            <view v-if="r.reason" class="reason">{{ r.reason }}</view>
          </view>
          <view class="right">
            <view class="amt">-{{ money(r.amount) }}</view>
            <view class="st" :class="r.status">{{ r.statusLabel || r.status }}</view>
          </view>
        </view>
      </view>
      <view v-if="out.list.value.length" class="muted end">{{ out.loading.value ? '加载中…' : out.finished.value ? '没有更多了' : '上拉加载更多' }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { merchantBalance, withdraw, withdrawals, settlements, merchantAccount } from '../../api'
import { usePaged } from '../../utils/paged'
import { money, dateTime, askText } from '../../utils/format'

const bal = ref({ available: 0, pending: 0 })
const acc = ref(null)
const tab = ref('in')
const inc = usePaged((p) => settlements(p))
const out = usePaged((p) => withdrawals(p))

async function loadHead() {
  const [b, a] = await Promise.all([merchantBalance().catch(() => null), merchantAccount().catch(() => null)])
  if (b) bal.value = b
  acc.value = a
}

function cur() {
  return tab.value === 'in' ? inc : out
}

onShow(() => {
  loadHead()
  cur().reload().catch(() => {})
})
onReachBottom(() => cur().more().catch(() => {}))
onPullDownRefresh(async () => {
  await Promise.all([loadHead(), cur().reload().catch(() => {})])
  uni.stopPullDownRefresh()
})

function switchTab(t) {
  tab.value = t
  cur().reload().catch(() => {})
}

function goOnboarding() {
  uni.navigateTo({ url: '/pages/merchant/onboarding' })
}

async function doWithdraw() {
  if (acc.value && acc.value.status !== 'APPROVED' && !acc.value.onlinePayEnabled) {
    return uni.showModal({
      title: '先开通收款账户',
      content: '开通后才能提现到银行卡',
      confirmText: '去开通',
      success: (r) => r.confirm && goOnboarding(),
    })
  }
  const text = await askText('提现多少钱', '最多 ' + money(bal.value.available), money(bal.value.available))
  if (text === null) return
  const amount = Math.round(Number(text) * 100) / 100
  if (!(amount > 0)) return uni.showToast({ title: '金额填得不对', icon: 'none' })
  if (amount > Number(bal.value.available)) return uni.showToast({ title: '不能超过可提现的 ¥' + money(bal.value.available), icon: 'none' })
  await withdraw(amount)
  uni.showToast({ title: '已申请提现，一般 1–2 天到账', icon: 'none', duration: 3000 })
  tab.value = 'out'
  loadHead()
  out.reload().catch(() => {})
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.head { background: $lm-green; color: #fff; padding: 40rpx 32rpx; }
.h-label { opacity: 0.9; }
.h-num { font-size: 80rpx; font-weight: 800; line-height: 1.2; }
.h-pending { font-size: 30rpx; margin-top: 8rpx; }
.h-note { font-size: 24rpx; opacity: 0.8; }
.wd-btn { background: #fff; color: $lm-green; margin-top: 28rpx; }
.guide { background: $lm-wheat-light; }
.g-title { font-size: 34rpx; font-weight: 800; color: #8A5A00; }
.g-go { color: $lm-wheat; font-weight: 800; margin-top: 8rpx; }
.tabs { background: #fff; margin-top: 20rpx; }
.tab { flex: 1; text-align: center; padding: 26rpx 0; font-size: 32rpx; color: $lm-ink-2; }
.tab.on { color: $lm-green; font-weight: 800; border-bottom: 6rpx solid $lm-green; }
.list { padding-top: 4rpx; padding-bottom: 4rpx; }
.line { padding: 22rpx 0; border-bottom: 2rpx solid $lm-line; }
.line:last-child { border-bottom: none; }
.right { text-align: right; margin-left: 16rpx; }
.amt { font-size: 36rpx; font-weight: 800; }
.amt.in { color: $lm-green; }
.st { font-size: 24rpx; color: $lm-ink-3; }
.st.PENDING_RELEASE, .st.PROCESSING { color: $lm-wheat; }
.st.FAILED { color: $lm-red; }
.reason { color: $lm-red; font-size: 26rpx; }
.end { text-align: center; padding: 20rpx 0 40rpx; }
</style>
