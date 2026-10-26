<template>
  <view>
    <!-- 顶部：位置 + 搜索 -->
    <view class="top">
      <view class="loc row" @tap="pickLocation">
        <text class="loc-pin">●</text>
        <text class="loc-name ellipsis">{{ location.name }}</text>
        <text class="loc-arrow">▾</text>
      </view>
      <view class="search row">
        <view class="search-box grow" @tap="goSearch()">
          <text class="muted">搜化肥、农药、种子…</text>
        </view>
        <view class="scan" @tap="scan">扫码</view>
      </view>
    </view>

    <!-- 轮播图 -->
    <swiper v-if="bannerList.length" class="banners" circular autoplay :interval="4000" :indicator-dots="bannerList.length > 1">
      <swiper-item v-for="b in bannerList" :key="b.id">
        <image class="banner-img" :src="b.image" mode="aspectFill" @tap="openBanner(b)" />
      </swiper-item>
    </swiper>

    <!-- 分类 -->
    <scroll-view scroll-x class="cats">
      <view class="cat" :class="{ on: !categoryId }" @tap="pickCategory(null)">全部</view>
      <view v-for="c in cats" :key="c.id" class="cat" :class="{ on: categoryId === c.id }" @tap="pickCategory(c.id)">
        {{ c.name }}
      </view>
    </scroll-view>

    <!-- 附近正在凑单 -->
    <view v-if="groups.length">
      <view class="section-title row between">
        <text>附近正在凑单</text>
        <text class="muted gb-more">人多价更低</text>
      </view>
      <scroll-view scroll-x class="gb-scroll">
        <view v-for="g in groups" :key="g.id" class="gb-card" @tap="goGroup(g.id)">
          <image class="gb-img" :src="g.productImage" mode="aspectFill" />
          <view class="gb-body">
            <view class="gb-name ellipsis">{{ g.productName }}</view>
            <view class="row">
              <text class="price gb-price">{{ money(g.groupPrice) }}</text>
              <text class="gb-orig">¥{{ money(g.originalPrice) }}</text>
            </view>
            <view class="gb-bar"><view class="gb-bar-in" :style="{ width: pct(g) + '%' }" /></view>
            <view class="muted ellipsis">已凑 {{ g.currentQty }}/{{ g.targetQty }}{{ g.unit }}</view>
            <view class="muted ellipsis">{{ g.storeName }}<text v-if="g.distanceKm != null"> · {{ km(g.distanceKm) }}</text></view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="section-title">附近的农资店</view>

    <view v-if="loading && !stores.length" class="empty">正在找附近的店…</view>
    <view v-else-if="!stores.length" class="empty">
      <view>附近 30 公里内还没有店铺</view>
      <view class="muted" style="margin-top: 12rpx">可以点上方的位置，换个地方看看</view>
    </view>

    <view v-for="s in stores" :key="s.id" class="card store" @tap="goStore(s.id)">
      <view class="row">
        <image class="cover" :src="s.coverImage || '/static/store.png'" mode="aspectFill" />
        <view class="grow info">
          <view class="row between">
            <text class="name ellipsis grow">{{ s.name }}</text>
            <text class="dist">{{ km(s.distanceKm) }}</text>
          </view>
          <view class="tags">
            <text v-if="!s.isOpen" class="tag gray">休息中</text>
            <text v-if="s.canDeliver" class="tag">送货上门</text>
            <text v-if="s.pickupEnabled" class="tag">到店自提</text>
            <text v-if="s.codEnabled" class="tag wheat">货到付款</text>
          </view>
          <view class="sub">
            <text v-if="s.canDeliver">起送 ¥{{ money(s.minOrderAmount) }} · 配送费 {{ Number(s.deliveryFee) ? '¥' + money(s.deliveryFee) : '免费' }}</text>
            <text v-else>超出送货范围，可自提或快递</text>
          </view>
          <view v-if="s.mainCategories" class="muted ellipsis">主营：{{ s.mainCategories }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow, onPullDownRefresh, onShareAppMessage } from '@dcloudio/uni-app'
import { nearbyStores, categories, banners, nearbyGroupBuys } from '../../api'
import { location, refreshLocation, chooseOnMap } from '../../store/location'
import { money, km } from '../../utils/format'
import { scanForKeyword } from '../../utils/scan'

const stores = ref([])
const cats = ref([])
const categoryId = ref(null)
const loading = ref(false)
const bannerList = ref([])
const groups = ref([])
let lastKey = ''

// 轮播和附近凑单：失败或没有就不显示
function loadExtras() {
  const loc = { lat: location.lat, lng: location.lng }
  banners(loc)
    .then((r) => (bannerList.value = (r || []).filter((b) => b.image)))
    .catch(() => (bannerList.value = []))
  nearbyGroupBuys(loc)
    .then((r) => (groups.value = r || []))
    .catch(() => (groups.value = []))
}

async function load() {
  loading.value = true
  if (!lastKey || !lastKey.startsWith(`${location.lat},${location.lng},`)) loadExtras()
  try {
    stores.value = await nearbyStores(location.lat, location.lng, categoryId.value)
    lastKey = `${location.lat},${location.lng},${categoryId.value}`
  } finally {
    loading.value = false
  }
}

onLoad(async () => {
  categories().then((c) => (cats.value = c)).catch(() => {})
  if (!location.auto) {
    await refreshLocation().catch(() => {})
  }
  load()
})

onShow(() => {
  // 从别的页面改了位置回来，重新加载
  if (lastKey && lastKey !== `${location.lat},${location.lng},${categoryId.value}`) load()
})

onPullDownRefresh(async () => {
  await refreshLocation().catch(() => {})
  lastKey = ''
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

onShareAppMessage(() => ({ title: '粮满满 · 附近的农资店，送货到村', path: '/pages/index/index' }))

function pickCategory(id) {
  categoryId.value = id
  load()
}

async function pickLocation() {
  try {
    await chooseOnMap()
    load()
  } catch (e) {
    // 用户取消
  }
}

function goSearch(keyword) {
  uni.navigateTo({ url: '/pages/search/search' + (keyword ? '?keyword=' + encodeURIComponent(keyword) : '') })
}

async function scan() {
  try {
    const kw = await scanForKeyword()
    if (kw) goSearch(kw)
  } catch (e) {
    // 取消扫码
  }
}

function pct(g) {
  return Math.min(100, Math.round((g.currentQty / Math.max(g.targetQty, 1)) * 100))
}

function goGroup(id) {
  uni.navigateTo({ url: '/pages/group/detail?id=' + id })
}

const TAB_PAGES = ['/pages/index/index', '/pages/order/list', '/pages/mine/mine']

function openBanner(b) {
  if (!b.link) return
  const url = b.link.startsWith('/') ? b.link : '/' + b.link
  const path = url.split('?')[0]
  if (TAB_PAGES.includes(path)) uni.switchTab({ url: path })
  else uni.navigateTo({ url, fail: () => {} })
}

function goStore(id) {
  uni.navigateTo({ url: '/pages/store/store?id=' + id })
}
</script>

<style lang="scss">
.top {
  background: $lm-green;
  padding: 16rpx 24rpx 28rpx;
}
.loc { color: #fff; font-size: 30rpx; padding: 8rpx 0 20rpx; }
.loc-pin { color: #F7C948; margin-right: 10rpx; font-size: 22rpx; }
.loc-name { max-width: 520rpx; font-weight: 600; }
.loc-arrow { margin-left: 8rpx; opacity: 0.8; }
.search-box {
  background: #fff;
  border-radius: 999rpx;
  height: 76rpx;
  display: flex;
  align-items: center;
  padding: 0 32rpx;
}
.scan {
  margin-left: 16rpx;
  color: #fff;
  border: 2rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 999rpx;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 26rpx;
  font-size: 28rpx;
}
.banners { height: 260rpx; margin: 20rpx 24rpx 0; border-radius: $lm-radius; overflow: hidden; }
.banner-img { width: 100%; height: 260rpx; display: block; }
.gb-more { font-weight: 400; }
.gb-scroll { white-space: nowrap; padding: 8rpx 16rpx 0; }
.gb-card {
  display: inline-block;
  width: 300rpx;
  margin: 8rpx;
  background: $lm-card;
  border-radius: $lm-radius;
  overflow: hidden;
  vertical-align: top;
  white-space: normal;
}
.gb-img { width: 300rpx; height: 200rpx; background: #EEE9DD; display: block; }
.gb-body { padding: 14rpx 18rpx 18rpx; }
.gb-name { font-size: 28rpx; font-weight: 700; }
.gb-price { font-size: 34rpx; }
.gb-orig { color: $lm-ink-3; text-decoration: line-through; font-size: 22rpx; margin-left: 10rpx; }
.gb-bar { height: 14rpx; background: $lm-green-light; border-radius: 999rpx; margin: 8rpx 0 4rpx; overflow: hidden; }
.gb-bar-in { height: 100%; background: $lm-green; }
.cats { white-space: nowrap; padding: 20rpx 16rpx 0; }
.cat {
  display: inline-block;
  padding: 12rpx 30rpx;
  margin: 0 8rpx;
  border-radius: 999rpx;
  background: #fff;
  font-size: 28rpx;
  color: $lm-ink-2;
}
.cat.on { background: $lm-green; color: #fff; }
.store .cover { width: 150rpx; height: 150rpx; border-radius: 16rpx; background: #EEE9DD; flex-shrink: 0; }
.store .info { margin-left: 24rpx; }
.store .name { font-size: 34rpx; font-weight: 700; }
.store .dist { color: $lm-ink-2; font-size: 26rpx; margin-left: 12rpx; flex-shrink: 0; }
.store .tags { margin: 8rpx 0; }
</style>
