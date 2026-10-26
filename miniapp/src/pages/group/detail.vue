<template>
  <view v-if="g">
    <view class="hero">
      <image class="pimg" :src="g.productImage" mode="aspectFill" @tap="previewImg" />
      <view class="hero-body">
        <view class="row">
          <text class="tag wheat">凑单</text>
          <text class="tag" :class="g.status === 'ONGOING' ? '' : 'gray'">{{ g.statusLabel }}</text>
        </view>
        <view class="pname">{{ g.productName }}</view>
        <view class="row prices">
          <text class="price gprice">{{ money(g.groupPrice) }}</text>
          <text class="muted">/{{ g.unit }}</text>
          <text class="orig">原价 ¥{{ money(g.originalPrice) }}</text>
          <text v-if="save > 0" class="tag wheat" style="margin-left: 12rpx">每{{ g.unit }}省 ¥{{ money(save) }}</text>
        </view>
      </view>
    </view>

    <!-- 进度 -->
    <view class="card">
      <view class="prog-text">
        已凑 <text class="strong">{{ g.currentQty }}</text>/{{ g.targetQty }} {{ g.unit }}
        <text v-if="left > 0">，还差 <text class="wheat-t">{{ left }}</text> {{ g.unit }}</text>
        <text v-else>，已凑够</text>
      </view>
      <view class="bar"><view class="bar-in" :style="{ width: percent + '%' }" /></view>
      <view v-if="g.status === 'ONGOING'" class="row between countdown">
        <text class="sub">{{ remainText ? '还剩 ' + remainText : '即将截止' }}</text>
        <text class="muted">{{ dateTime(g.endAt) }} 截止</text>
      </view>
      <view v-if="g.endWhenReached && g.status === 'ONGOING'" class="muted">凑够了就立刻截止，先到先得</view>
      <view v-if="g.myQuantity" class="mine">你已参加 {{ g.myQuantity }} {{ g.unit }}</view>
    </view>

    <!-- 非进行中：结果说明 -->
    <view v-if="g.status !== 'ONGOING'" class="card result" :class="g.status">
      <view class="r-title">{{ resultTitle }}</view>
      <view class="sub">{{ resultText }}</view>
    </view>

    <!-- 参加 -->
    <view v-if="g.status === 'ONGOING'" class="card">
      <view class="row between">
        <text class="join-t">我也要</text>
        <view class="row">
          <view class="sb" :class="{ dis: qty <= 1 }" @tap="setQ(qty - 1)">−</view>
          <input class="qinput" type="number" :value="String(qty)" @blur="(e) => setQ(Number(e.detail.value))" />
          <view class="sb plus" :class="{ dis: maxQty && qty >= maxQty }" @tap="setQ(qty + 1)">+</view>
          <text class="unit">{{ g.unit }}</text>
        </view>
      </view>
      <view v-if="g.limitPerUser" class="muted">每人最多 {{ g.limitPerUser }} {{ g.unit }}{{ g.myQuantity ? '，你还能再要 ' + Math.max(maxQty, 0) + ' ' + g.unit : '' }}</view>
      <view class="muted">需要先在线付款，凑不成会自动全额退款</view>
    </view>

    <!-- 已参加的人 -->
    <view class="section-title">已参加 {{ (g.members || []).length }} 人</view>
    <view class="card">
      <view v-if="!(g.members || []).length" class="muted">还没人参加，你来当第一个</view>
      <view v-for="(m, i) in g.members || []" :key="i" class="member row">
        <view class="avatar">{{ (m.nickname || '农').slice(0, 1) }}</view>
        <view class="grow">
          <text class="m-name">{{ m.nickname }}</text>
          <text v-if="m.village" class="sub" style="margin-left: 12rpx">{{ m.village }}</text>
        </view>
        <text class="m-qty">{{ m.quantity }} {{ g.unit }}</text>
      </view>
    </view>

    <!-- 店铺 -->
    <view class="card row" @tap="goStore">
      <view class="grow">
        <view class="sname">{{ g.storeName }}</view>
        <view class="muted">
          <text v-if="g.distanceKm != null">{{ km(g.distanceKm) }} · </text>
          <text>{{ [g.localDelivery ? '可送货/自提' : '', g.expressDelivery ? '可快递' : ''].filter(Boolean).join(' · ') }}</text>
        </view>
      </view>
      <button v-if="g.storePhone" class="btn small ghost" @tap.stop="call(g.storePhone)">打电话</button>
      <text class="sub" style="margin-left: 12rpx">进店 ›</text>
    </view>

    <view class="row share-row">
      <button class="btn ghost grow" open-type="share">分享到微信群</button>
      <button class="btn ghost grow" style="margin-left: 20rpx" @tap="poster">生成海报</button>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <view class="grow">
        <text v-if="g.status === 'ONGOING'" class="muted">合计 </text>
        <text v-if="g.status === 'ONGOING'" class="price total">{{ money(g.groupPrice * qty) }}</text>
        <text v-else class="sub">{{ g.statusLabel }}</text>
      </view>
      <button class="btn big wheat" :disabled="!canJoin" @tap="join">{{ joinText }}</button>
    </view>
  </view>
  <view v-else class="empty">{{ error || '加载中…' }}</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onUnload, onShareAppMessage, onPullDownRefresh } from '@dcloudio/uni-app'
import { groupBuyDetail } from '../../api'
import { ensureLogin } from '../../api/request'
import { currentLoc } from '../../store/location'
import { money, km, dateTime, call, remain } from '../../utils/format'
import { parseScene, sharePath, loadUserId, recordShareVisit } from '../../utils/share'

const id = ref(0)
const g = ref(null)
const error = ref('')
const qty = ref(1)
const remainText = ref('')
let timer = null
let firstLoaded = false

const percent = computed(() => (g.value ? Math.min(100, Math.round((g.value.currentQty / Math.max(g.value.targetQty, 1)) * 100)) : 0))
const left = computed(() => (g.value ? Math.max(0, g.value.targetQty - g.value.currentQty) : 0))
const save = computed(() => (g.value ? Math.round((g.value.originalPrice - g.value.groupPrice) * 100) / 100 : 0))
const maxQty = computed(() => (g.value && g.value.limitPerUser ? g.value.limitPerUser - (g.value.myQuantity || 0) : 0))
const canJoin = computed(() => g.value && g.value.status === 'ONGOING' && qty.value >= 1 && (!g.value.limitPerUser || maxQty.value >= qty.value))
const joinText = computed(() => {
  if (!g.value || g.value.status !== 'ONGOING') return '已结束'
  if (g.value.limitPerUser && maxQty.value <= 0) return '已达限购'
  return g.value.myQuantity ? '再要 ' + qty.value + ' ' + g.value.unit : '参加凑单'
})

const resultTitle = computed(() => ({
  SUCCESS: '已凑成，老板会按订单发货',
  WAIT_DECISION: '没凑够，等老板决定',
  FAILED: '没凑成，钱已原路退回',
}[g.value.status] || g.value.statusLabel))
const resultText = computed(() => ({
  SUCCESS: '参加了的人可以在“订单”里看进度。',
  WAIT_DECISION: '老板会决定照样按凑单价发货，还是全部退款' + (g.value.decisionDeadline ? '（' + dateTime(g.value.decisionDeadline) + '前）' : '') + '。',
  FAILED: '这次没凑够，付过的钱已经全部退回。可以进店看看别的。',
}[g.value.status] || '这个凑单已经结束了。'))

async function load() {
  try {
    g.value = await groupBuyDetail(id.value, currentLoc())
    error.value = ''
    tick()
    if (maxQty.value > 0 && qty.value > maxQty.value) qty.value = maxQty.value
  } catch (e) {
    error.value = e.msg || '加载失败'
  }
}

function tick() {
  remainText.value = g.value ? remain(g.value.endAt) : ''
}

onLoad(async (q) => {
  const sc = parseScene(q)
  id.value = Number(q.id || sc.g)
  if (!id.value) {
    error.value = '凑单不存在'
    return
  }
  timer = setInterval(tick, 30000)
  // 先登录再加载，才能拿到“我已参加多少”
  await ensureLogin().catch(() => {})
  loadUserId()
  recordShareVisit(q, 'GROUP_BUY', id.value)
  await load()
  firstLoaded = true
})

// 从结算页回来时刷新进度
onShow(() => firstLoaded && load())
onUnload(() => timer && clearInterval(timer))
onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

onShareAppMessage(() => ({
  title: g.value
    ? `一起凑单：${g.value.productName} ¥${money(g.value.groupPrice)}/${g.value.unit}，还差 ${left.value} ${g.value.unit}`
    : '一起凑单 · 粮满满',
  path: sharePath('/pages/group/detail?id=' + id.value),
  imageUrl: g.value ? g.value.productImage : '',
}))

function setQ(n) {
  let v = Math.floor(Number(n) || 1)
  if (v < 1) v = 1
  if (maxQty.value > 0 && v > maxQty.value) {
    v = maxQty.value
    uni.showToast({ title: '每人最多 ' + g.value.limitPerUser + ' ' + g.value.unit, icon: 'none' })
  }
  qty.value = v
}

function join() {
  if (!canJoin.value) return
  uni.navigateTo({
    url: `/pages/checkout/checkout?groupBuyId=${g.value.id}&productId=${g.value.productId}&qty=${qty.value}&storeId=${g.value.storeId}&storeName=${encodeURIComponent(g.value.storeName || '')}`,
  })
}

function goStore() {
  uni.navigateTo({ url: '/pages/store/store?id=' + g.value.storeId })
}

function poster() {
  uni.navigateTo({ url: '/pages/poster/poster?type=group&id=' + id.value })
}

function previewImg() {
  if (g.value.productImage) uni.previewImage({ urls: [g.value.productImage] })
}
</script>

<style lang="scss">
.hero { background: #fff; }
.hero .pimg { width: 750rpx; height: 520rpx; background: #EEE9DD; display: block; }
.hero-body { padding: 24rpx 28rpx; }
.pname { font-size: 38rpx; font-weight: 800; margin: 10rpx 0; }
.prices { align-items: baseline; flex-wrap: wrap; }
.gprice { font-size: 56rpx; }
.orig { color: $lm-ink-3; text-decoration: line-through; margin-left: 20rpx; font-size: 28rpx; }
.prog-text { font-size: 34rpx; font-weight: 700; }
.strong { color: $lm-green; font-size: 44rpx; }
.wheat-t { color: $lm-wheat; font-size: 44rpx; }
.bar { height: 32rpx; background: $lm-green-light; border-radius: 999rpx; margin: 16rpx 0; overflow: hidden; }
.bar-in { height: 100%; background: $lm-green; border-radius: 999rpx; }
.countdown { font-size: 30rpx; }
.mine { margin-top: 12rpx; color: $lm-green; font-weight: 700; }
.result { background: $lm-wheat-light; }
.result.FAILED { background: #EEEBE4; }
.result.SUCCESS { background: $lm-green-light; }
.r-title { font-size: 34rpx; font-weight: 800; margin-bottom: 6rpx; }
.join-t { font-size: 34rpx; font-weight: 800; }
.sb {
  width: 72rpx; height: 72rpx; border-radius: 50%; border: 2rpx solid $lm-ink-3; color: $lm-ink-2;
  display: flex; align-items: center; justify-content: center; font-size: 40rpx; font-weight: 700;
}
.sb.plus { background: $lm-green; border-color: $lm-green; color: #fff; }
.sb.dis { opacity: 0.35; }
.qinput { width: 120rpx; text-align: center; font-size: 40rpx; font-weight: 800; }
.unit { margin-left: 12rpx; font-size: 30rpx; }
.member { padding: 14rpx 0; border-bottom: 2rpx solid $lm-line; }
.member:last-child { border-bottom: none; }
.avatar {
  width: 64rpx; height: 64rpx; border-radius: 50%; background: $lm-green-light; color: $lm-green;
  display: flex; align-items: center; justify-content: center; font-weight: 700; margin-right: 20rpx;
}
.m-name { font-size: 30rpx; font-weight: 600; }
.m-qty { font-weight: 700; }
.sname { font-size: 32rpx; font-weight: 700; }
.share-row { padding: 8rpx 24rpx; }
.total { font-size: 44rpx; }
</style>
