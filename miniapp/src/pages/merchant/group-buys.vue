<template>
  <view class="page">
    <view class="intro">
      <view class="i-title">凑单：人凑够了按低价卖</view>
      <view class="sub">比如复合肥原价 150，凑够 50 袋每袋 128。农户先付钱，凑够了你再发货；凑不够你可以照样发，也可以全部退款。</view>
    </view>

    <view v-if="loaded && !list.length" class="empty">
      <view>还没开过凑单</view>
      <view class="muted" style="margin-top: 12rpx">点下面按钮开一个，分享到村里微信群</view>
    </view>

    <view v-for="g in list" :key="g.id" class="card gb">
      <view class="row">
        <image class="img" :src="g.productImage" mode="aspectFill" />
        <view class="grow" style="margin-left: 20rpx">
          <view class="pname">{{ g.productName }}</view>
          <view>
            <text class="price gp">{{ money(g.groupPrice) }}</text>
            <text class="muted">/{{ g.unit }}　原价 ¥{{ money(g.originalPrice) }}</text>
          </view>
        </view>
        <text class="status" :class="g.status">{{ g.statusLabel }}</text>
      </view>

      <view class="prog-text">已凑 <text class="strong">{{ g.currentQty }}</text> / {{ g.targetQty }} {{ g.unit }}，{{ (g.members || []).length }} 人参加</view>
      <view class="bar"><view class="bar-in" :style="{ width: pct(g) + '%' }" /></view>
      <view class="sub">
        <text v-if="g.status === 'ONGOING'">{{ remain(g.endAt) ? '还剩 ' + remain(g.endAt) + '，' : '' }}{{ dateTime(g.endAt) }} 截止</text>
        <text v-else>{{ dateTime(g.endAt) }} 截止</text>
        <text v-if="g.limitPerUser"> · 每人限 {{ g.limitPerUser }}{{ g.unit }}</text>
      </view>

      <!-- 没凑够，等老板决定 -->
      <view v-if="g.status === 'WAIT_DECISION'" class="decide">
        <view class="d-title">没凑够，要不要照样发货？</view>
        <view class="sub">照样发货：按凑单价卖给已参加的人；全部退款：钱原路退回。<text v-if="g.decisionDeadline">请在 {{ dateTime(g.decisionDeadline) }} 前决定。</text></view>
        <view class="row d-btns">
          <button class="btn big grow" @tap="decide(g, 'SHIP')">照样发货</button>
          <button class="btn big warn grow" style="margin-left: 20rpx" @tap="decide(g, 'REFUND')">全部退款</button>
        </view>
      </view>

      <view v-if="(g.members || []).length" class="members">
        <text v-for="(m, i) in g.members" :key="i" class="m">{{ m.nickname }}<text v-if="m.village">（{{ m.village }}）</text> {{ m.quantity }}{{ g.unit }}</text>
      </view>

      <view class="row acts">
        <button v-if="g.status === 'ONGOING'" class="btn big warn grow" @tap="close(g)">提前结束</button>
        <button class="btn big ghost grow" @tap="poster(g)">海报</button>
        <button class="btn big ghost grow" :data-id="g.id" open-type="share">分享</button>
      </view>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow wheat" @tap="create">＋ 开一个凑单</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh, onShareAppMessage } from '@dcloudio/uni-app'
import { merchantGroupBuys, decideGroupBuy, closeGroupBuy } from '../../api'
import { money, dateTime, remain, confirmBox } from '../../utils/format'
import { sharePath, loadUserId } from '../../utils/share'

const list = ref([])
const loaded = ref(false)

async function load() {
  try {
    list.value = (await merchantGroupBuys()) || []
  } finally {
    loaded.value = true
  }
}

onShow(() => {
  loadUserId()
  load().catch(() => {})
})
onPullDownRefresh(async () => {
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

onShareAppMessage((res) => {
  const id = res && res.target && res.target.dataset ? Number(res.target.dataset.id) : 0
  const g = list.value.find((x) => x.id === id) || list.value[0]
  if (!g) return { title: '粮满满', path: '/pages/index/index' }
  return {
    title: `一起凑单：${g.productName} ¥${money(g.groupPrice)}/${g.unit}，凑够 ${g.targetQty}${g.unit} 就发货`,
    path: sharePath('/pages/group/detail?id=' + g.id),
    imageUrl: g.productImage || '',
  }
})

function pct(g) {
  return Math.min(100, Math.round((g.currentQty / Math.max(g.targetQty, 1)) * 100))
}

function create() {
  uni.navigateTo({ url: '/pages/merchant/group-buy-edit' })
}

function poster(g) {
  uni.navigateTo({ url: '/pages/poster/poster?type=group&id=' + g.id })
}

async function decide(g, action) {
  const ok = await confirmBox(
    action === 'SHIP' ? '确定照样发货？' : '确定全部退款？',
    action === 'SHIP' ? `按凑单价 ¥${money(g.groupPrice)} 卖给已参加的 ${g.currentQty}${g.unit}，订单会进入待发货` : '已付的钱会原路退回给农户，这个凑单就结束了',
    action === 'SHIP' ? '照样发货' : '全部退款'
  )
  if (!ok) return
  await decideGroupBuy(g.id, action)
  uni.showToast({ title: action === 'SHIP' ? '好的，记得去订单里发货' : '已退款', icon: 'none' })
  load()
}

async function close(g) {
  const reached = g.currentQty >= g.targetQty
  const ok = await confirmBox('现在就结束凑单？', reached ? '已经凑够了，结束后订单进入待发货' : '还没凑够，结束后要你决定照样发货还是全部退款', '提前结束')
  if (!ok) return
  await closeGroupBuy(g.id)
  uni.showToast({ title: '已结束', icon: 'none' })
  load()
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.intro { background: $lm-wheat-light; padding: 28rpx; }
.i-title { font-size: 36rpx; font-weight: 800; color: #8A5A00; margin-bottom: 8rpx; }
.gb .img { width: 140rpx; height: 140rpx; border-radius: 14rpx; background: #EEE9DD; flex-shrink: 0; }
.pname { font-size: 34rpx; font-weight: 700; }
.gp { font-size: 40rpx; }
.status { font-size: 30rpx; font-weight: 800; color: $lm-green; margin-left: 12rpx; flex-shrink: 0; }
.status.WAIT_DECISION { color: $lm-red; }
.status.FAILED { color: $lm-ink-3; }
.status.SUCCESS { color: $lm-wheat; }
.prog-text { margin-top: 20rpx; font-size: 32rpx; }
.strong { color: $lm-green; font-size: 44rpx; font-weight: 800; }
.bar { height: 28rpx; background: $lm-green-light; border-radius: 999rpx; margin: 12rpx 0; overflow: hidden; }
.bar-in { height: 100%; background: $lm-green; }
.decide { background: #FBEAE7; border-radius: 16rpx; padding: 24rpx; margin-top: 20rpx; }
.d-title { font-size: 36rpx; font-weight: 800; color: $lm-red; margin-bottom: 8rpx; }
.d-btns { margin-top: 20rpx; }
.d-btns .btn { padding: 0; }
.members { margin-top: 16rpx; font-size: 26rpx; color: $lm-ink-2; }
.members .m { display: inline-block; margin-right: 20rpx; }
.acts { margin-top: 24rpx; }
.acts .btn { margin: 0 8rpx; padding: 0; }
</style>
