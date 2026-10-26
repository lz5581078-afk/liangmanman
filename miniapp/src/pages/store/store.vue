<template>
  <view v-if="detail">
    <view class="hero">
      <image class="cover" :src="detail.store.coverImage || '/static/store.png'" mode="aspectFill" />
      <view class="hero-body">
        <view class="name">{{ detail.store.name }}</view>
        <view v-if="detail.reviewSummary && detail.reviewSummary.count" class="row rating" @tap="goReviews">
          <text class="stars">{{ starText(detail.reviewSummary.rating) }}</text>
          <text class="r-num">{{ fmtRating(detail.reviewSummary.rating) }}</text>
          <text class="sub">{{ detail.reviewSummary.count }} 条评价 ›</text>
        </view>
        <view v-else class="row rating" @tap="goReviews"><text class="muted">还没有评价</text></view>
        <view class="row meta">
          <text v-if="!detail.store.isOpen" class="tag gray">休息中</text>
          <text v-if="detail.store.distanceKm != null" class="sub">{{ km(detail.store.distanceKm) }}</text>
          <text v-if="detail.businessHours" class="sub" style="margin-left: 16rpx">营业 {{ detail.businessHours }}</text>
        </view>
        <view class="tags">
          <text v-if="detail.store.canDeliver" class="tag">送货上门 · 起送¥{{ money(detail.store.minOrderAmount) }}</text>
          <text v-else-if="detail.store.deliveryEnabled" class="tag gray">你的位置超出送货范围</text>
          <text v-if="detail.store.pickupEnabled" class="tag">到店自提</text>
          <text v-if="detail.store.codEnabled" class="tag wheat">货到付款</text>
        </view>
        <view class="row actions">
          <button class="btn small ghost" @tap="call(detail.store.phone)">打电话</button>
          <button class="btn small ghost" @tap="nav">去店里</button>
          <button class="btn small ghost" open-type="share">分享给朋友</button>
        </view>
      </view>
      <view v-if="detail.notice" class="notice">公告：{{ detail.notice }}</view>
    </view>

    <!-- 进行中的凑单 -->
    <view v-if="detail.groupBuys && detail.groupBuys.length">
      <view class="section-title">正在凑单 · 人多价更低</view>
      <view v-for="g in detail.groupBuys" :key="g.id" class="card gb row" @tap="goGroup(g.id)">
        <image class="gb-img" :src="g.productImage" mode="aspectFill" />
        <view class="grow" style="margin-left: 20rpx">
          <view class="pname ellipsis">{{ g.productName }}</view>
          <view>
            <text class="price pprice">{{ money(g.groupPrice) }}</text>
            <text class="muted">/{{ g.unit }}</text>
            <text class="gb-orig">¥{{ money(g.originalPrice) }}</text>
          </view>
          <view class="gb-bar"><view class="gb-bar-in" :style="{ width: Math.min(100, Math.round((g.currentQty / Math.max(g.targetQty, 1)) * 100)) + '%' }" /></view>
          <view class="muted">已凑 {{ g.currentQty }}/{{ g.targetQty }}{{ g.unit }}<text v-if="remain(g.endAt)"> · 还剩 {{ remain(g.endAt) }}</text></view>
        </view>
        <text class="tag wheat">去凑单</text>
      </view>
    </view>

    <view v-if="!groupsCount" class="empty">老板还没上架商品</view>

    <view v-for="g in detail.groups" :key="g.categoryId || 0">
      <view class="section-title">{{ g.categoryName }}</view>
      <view class="card list">
        <view v-for="p in g.products" :key="p.id" class="item row" @tap="goProduct(p.id)">
          <image class="img" :src="p.images[0]" mode="aspectFill" />
          <view class="grow body">
            <view class="pname">{{ p.name }}<text v-if="p.spec && !p.name.includes(p.spec)" class="muted"> {{ p.spec }}</text></view>
            <view class="tags">
              <text v-if="!p.localDelivery" class="tag wheat">只快递</text>
              <text v-else-if="p.expressDelivery" class="tag wheat">可快递</text>
              <text v-if="p.stock != null && p.stock <= 10" class="muted">仅剩 {{ p.stock }}</text>
            </view>
            <view class="row between">
              <view>
                <text class="price pprice">{{ money(p.price) }}</text>
                <text class="muted">/{{ p.unit }}</text>
              </view>
              <QtyStepper :value="qtyOf(storeId, p.id)" @change="(q) => setQty(storeId, detail.store.name, p, q)" />
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="footer-space" />
    <CartBar :store-id="storeId" />
  </view>
  <view v-else class="empty">加载中…</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { storeDetail } from '../../api'
import { ensureLogin } from '../../api/request'
import { parseScene, sharePath, loadUserId, recordShareVisit } from '../../utils/share'
import { currentLoc } from '../../store/location'
import { qtyOf, setQty } from '../../store/cart'
import { money, km, call, navigate, remain } from '../../utils/format'
import QtyStepper from '../../components/QtyStepper.vue'
import CartBar from '../../components/CartBar.vue'

const storeId = ref(0)
const detail = ref(null)

const groupsCount = computed(() => (detail.value ? detail.value.groups.reduce((n, g) => n + g.products.length, 0) : 0))

onLoad(async (q) => {
  storeId.value = Number(q.id || parseScene(q).s)
  if (q.from) {
    ensureLogin()
      .then(() => recordShareVisit(q, 'STORE', storeId.value))
      .catch(() => {})
  }
  loadUserId()
  detail.value = await storeDetail(storeId.value, currentLoc())
  uni.setNavigationBarTitle({ title: detail.value.store.name })
})

onShareAppMessage(() => ({
  title: detail.value ? detail.value.store.name + ' · 粮满满' : '粮满满',
  path: sharePath('/pages/store/store?id=' + storeId.value),
}))

function nav() {
  const s = detail.value.store
  navigate(s.lat, s.lng, s.name, s.addressText)
}

function goGroup(id) {
  uni.navigateTo({ url: '/pages/group/detail?id=' + id })
}

function goReviews() {
  uni.navigateTo({ url: '/pages/store/reviews?id=' + storeId.value + '&name=' + encodeURIComponent(detail.value.store.name) + (detail.value.reviewSummary ? '&rating=' + (detail.value.reviewSummary.rating || 0) : '') })
}

function fmtRating(r) {
  const n = Number(r || 0)
  return n.toFixed(1)
}

function starText(r) {
  const n = Math.round(Number(r || 0))
  return '★★★★★'.slice(0, n) + '☆☆☆☆☆'.slice(0, 5 - n)
}

function goProduct(id) {
  uni.navigateTo({ url: '/pages/product/product?id=' + id })
}
</script>

<style lang="scss">
.hero { background: #fff; padding-bottom: 20rpx; }
.hero .cover { width: 100%; height: 300rpx; background: #EEE9DD; display: block; }
.hero-body { padding: 24rpx 28rpx 0; }
.hero .name { font-size: 40rpx; font-weight: 800; }
.hero .meta { margin: 8rpx 0; }
.rating { margin-top: 6rpx; }
.stars { color: $lm-wheat; font-size: 30rpx; letter-spacing: 2rpx; }
.r-num { color: $lm-wheat; font-weight: 800; margin: 0 12rpx 0 8rpx; }
.gb { align-items: center; background: $lm-wheat-light; }
.gb-img { width: 150rpx; height: 150rpx; border-radius: 14rpx; background: #EEE9DD; flex-shrink: 0; }
.gb-orig { color: $lm-ink-3; text-decoration: line-through; font-size: 24rpx; margin-left: 12rpx; }
.gb-bar { height: 16rpx; background: #fff; border-radius: 999rpx; margin: 8rpx 0 4rpx; overflow: hidden; }
.gb-bar-in { height: 100%; background: $lm-green; }
.hero .tags { margin: 8rpx 0 16rpx; }
.hero .actions .btn { margin-right: 16rpx; }
.notice {
  margin: 20rpx 28rpx 0;
  background: $lm-wheat-light;
  color: #8A5A00;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 28rpx;
}
.list { padding: 8rpx 24rpx; }
.item { padding: 20rpx 0; border-bottom: 2rpx solid $lm-line; align-items: flex-start; }
.item:last-child { border-bottom: none; }
.item .img { width: 170rpx; height: 170rpx; border-radius: 14rpx; background: #EEE9DD; flex-shrink: 0; }
.item .body { margin-left: 22rpx; }
.pname { font-size: 31rpx; font-weight: 600; }
.pprice { font-size: 36rpx; }
.item .tags { margin: 6rpx 0 10rpx; min-height: 10rpx; }
</style>
