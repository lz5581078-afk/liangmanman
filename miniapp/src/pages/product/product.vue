<template>
  <view v-if="d">
    <swiper class="gallery" indicator-dots circular>
      <swiper-item v-for="(img, i) in p.images" :key="i">
        <image class="gimg" :src="img" mode="aspectFill" @tap="preview(i)" />
      </swiper-item>
    </swiper>

    <view class="card">
      <view>
        <text class="price big">{{ money(p.price) }}</text>
        <text class="muted">/{{ p.unit }}</text>
      </view>
      <view class="pname">{{ p.name }}</view>
      <view v-if="p.brand || p.spec" class="sub spec">{{ [p.brand, p.spec].filter(Boolean).join(' · ') }}</view>
      <view v-if="d.sku && d.sku.regNo" class="muted spec">登记证号 {{ d.sku.regNo }}</view>
      <view class="tags">
        <text v-if="p.localDelivery && d.store.canDeliver" class="tag">可送货上门</text>
        <text v-if="p.localDelivery && d.store.pickupEnabled" class="tag">可到店自提</text>
        <text v-if="p.expressDelivery" class="tag wheat">快递发货{{ Number(p.expressFee) ? ' · 运费¥' + money(p.expressFee) : ' · 包邮' }}</text>
        <text v-if="!p.localDelivery" class="tag gray">不支持本地配送</text>
      </view>
      <view class="muted">
        <text>已售 {{ p.sales }}</text>
        <text v-if="p.stock != null" style="margin-left: 24rpx">库存 {{ p.stock }}</text>
      </view>
      <view v-if="p.description" class="desc">{{ p.description }}</view>
    </view>

    <view class="card row" @tap="goStore">
      <image class="scover" :src="d.store.coverImage || '/static/store.png'" mode="aspectFill" />
      <view class="grow" style="margin-left: 20rpx">
        <view class="sname">{{ d.store.name }}</view>
        <view class="muted">
          <text v-if="d.store.distanceKm != null">{{ km(d.store.distanceKm) }} · </text>
          <text>{{ d.store.isOpen ? '营业中' : '休息中' }}</text>
        </view>
      </view>
      <text class="sub">进店 ›</text>
    </view>

    <view class="row share-row">
      <button class="btn small ghost grow" open-type="share">分享给朋友</button>
      <button class="btn small ghost grow" style="margin-left: 20rpx" @tap="poster">生成海报</button>
    </view>

    <view v-if="d.sku && d.otherStoresCount > 0" class="card row" @tap="compare">
      <text class="grow">还有 <text class="strong">{{ d.otherStoresCount }}</text> 家店卖同一款</text>
      <text class="sub">比一比 ›</text>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <view class="grow row">
        <text v-if="cart.count" class="sub">购物车 {{ cart.count }} 件 · ¥{{ money(cart.total) }}</text>
      </view>
      <QtyStepper v-if="qty > 0" :value="qty" @change="(q) => setQty(d.store.id, d.store.name, p, q)" />
      <button v-if="qty === 0" class="btn ghost" style="margin-right: 16rpx" @tap="addOne">加入购物车</button>
      <button class="btn" style="margin-left: 16rpx" @tap="buyNow">去结算</button>
    </view>
  </view>
  <view v-else class="empty">加载中…</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { productDetail } from '../../api'
import { ensureLogin } from '../../api/request'
import { parseScene, sharePath, loadUserId, recordShareVisit } from '../../utils/share'
import { currentLoc } from '../../store/location'
import { qtyOf, setQty, add, summary } from '../../store/cart'
import { money, km } from '../../utils/format'
import QtyStepper from '../../components/QtyStepper.vue'

const d = ref(null)
const p = computed(() => d.value.product)
const qty = computed(() => (d.value ? qtyOf(d.value.store.id, p.value.id) : 0))
const cart = computed(() => (d.value ? summary(d.value.store.id) : { count: 0, total: 0 }))

onLoad(async (q) => {
  const pid = Number(q.id || parseScene(q).p)
  if (q.from) {
    ensureLogin()
      .then(() => recordShareVisit(q, 'PRODUCT', pid))
      .catch(() => {})
  }
  loadUserId()
  d.value = await productDetail(pid, currentLoc())
})

onShareAppMessage(() => ({
  title: d.value ? `${p.value.name} ¥${money(p.value.price)} · ${d.value.store.name}` : '粮满满',
  path: sharePath('/pages/product/product?id=' + (d.value ? p.value.id : '')),
}))

function addOne() {
  add(d.value.store.id, d.value.store.name, p.value, 1)
  uni.showToast({ title: '已加入', icon: 'success' })
}

function buyNow() {
  if (qty.value === 0) add(d.value.store.id, d.value.store.name, p.value, 1)
  uni.navigateTo({ url: '/pages/checkout/checkout?storeId=' + d.value.store.id })
}

function goStore() {
  uni.navigateTo({ url: '/pages/store/store?id=' + d.value.store.id })
}

function compare() {
  uni.navigateTo({ url: '/pages/sku/compare?id=' + d.value.sku.id })
}

function poster() {
  uni.navigateTo({ url: '/pages/poster/poster?type=product&id=' + p.value.id })
}

function preview(i) {
  uni.previewImage({ urls: p.value.images, current: i })
}
</script>

<style lang="scss">
.gallery { width: 750rpx; height: 750rpx; background: #EEE9DD; }
.gimg { width: 750rpx; height: 750rpx; }
.big { font-size: 52rpx; }
.pname { font-size: 36rpx; font-weight: 700; margin: 8rpx 0 12rpx; }
.spec { margin: -6rpx 0 12rpx; }
.tags { margin-bottom: 12rpx; }
.desc { margin-top: 20rpx; color: $lm-ink-2; white-space: pre-wrap; }
.scover { width: 96rpx; height: 96rpx; border-radius: 12rpx; background: #EEE9DD; }
.sname { font-size: 32rpx; font-weight: 600; }
.strong { color: $lm-green; font-weight: 700; }
.share-row { padding: 0 24rpx; }
</style>
