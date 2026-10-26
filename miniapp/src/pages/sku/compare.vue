<template>
  <view v-if="d">
    <view class="card head row">
      <image class="img" :src="headImage || '/static/store.png'" mode="aspectFill" />
      <view class="grow" style="margin-left: 20rpx">
        <view class="title">{{ d.sku.displayName }}</view>
        <view v-if="d.sku.regNo" class="muted">登记证号 {{ d.sku.regNo }}</view>
        <view v-if="d.sku.barcode" class="muted">条码 {{ d.sku.barcode }}</view>
        <view v-if="d.sku.manufacturer" class="muted">生产企业 {{ d.sku.manufacturer }}</view>
        <view v-if="d.sku.status === 'PENDING'" class="pending">待核实 · 信息由商家上架时识别，平台还在核实</view>
      </view>
    </view>

    <view class="row bar">
      <text class="grow sub">
        <text v-if="d.offers.length">{{ d.offers.length }} 家在卖 · 最低 <text class="price">{{ money(d.minPrice) }}</text></text>
      </text>
      <text class="sort-item" :class="{ on: sort === 'PRICE_ASC' }" @tap="setSort('PRICE_ASC')">价格最低</text>
      <text class="sort-item" :class="{ on: sort === 'DISTANCE' }" @tap="setSort('DISTANCE')">离我最近</text>
    </view>

    <view v-if="!d.offers.length" class="empty">附近还没有店在卖这一款</view>
    <view v-else class="card list">
      <view v-for="o in d.offers" :key="o.productId" class="offer row" @tap="goProduct(o.productId)">
        <image class="oimg" :src="o.image" mode="aspectFill" />
        <view class="grow" style="margin-left: 20rpx">
          <view class="row">
            <text class="store-name ellipsis">{{ o.storeName }}</text>
            <text class="tag" :class="o.storeOpen ? '' : 'gray'" style="margin-left: 10rpx">{{ o.storeOpen ? '营业中' : '打烊' }}</text>
          </view>
          <view v-if="o.distanceKm != null" class="muted">{{ km(o.distanceKm) }}</view>
          <view class="tags">
            <text v-if="o.canDeliver" class="tag">送货上门</text>
            <text v-if="o.canPickup" class="tag">自提</text>
            <text v-if="o.express" class="tag wheat">快递{{ Number(o.expressFee) ? ' ¥' + money(o.expressFee) : '包邮' }}</text>
          </view>
        </view>
        <view class="right">
          <view v-if="isLowest(o)"><text class="lowest">最低价</text></view>
          <view>
            <text class="price big-price" :class="{ low: isLowest(o) }">{{ money(o.price) }}</text>
            <text class="muted">/{{ o.unit }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
  <view v-else class="empty">{{ failed ? '没找到这一款' : '加载中…' }}</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { skuOffers } from '../../api'
import { currentLoc } from '../../store/location'
import { money, km } from '../../utils/format'

const id = ref(0)
const sort = ref('PRICE_ASC')
const d = ref(null)
const failed = ref(false)

const headImage = computed(() => (d.value ? d.value.sku.image || (d.value.offers[0] && d.value.offers[0].image) : ''))

onLoad((q) => {
  id.value = Number(q.id)
  load()
})

async function load() {
  try {
    d.value = await skuOffers(id.value, currentLoc(), { sort: sort.value })
  } catch (e) {
    if (!d.value) failed.value = true
  }
}

function setSort(s) {
  if (sort.value === s) return
  sort.value = s
  load()
}

function isLowest(o) {
  return d.value.offers.length > 1 && d.value.minPrice != null && Number(o.price) === Number(d.value.minPrice)
}

function goProduct(pid) {
  uni.navigateTo({ url: '/pages/product/product?id=' + pid })
}
</script>

<style lang="scss">
.head { align-items: flex-start; }
.img { width: 160rpx; height: 160rpx; border-radius: 12rpx; background: #EEE9DD; flex-shrink: 0; }
.title { font-size: 34rpx; font-weight: 700; margin-bottom: 6rpx; }
.pending {
  margin-top: 10rpx; font-size: 24rpx; color: $lm-wheat; background: $lm-wheat-light;
  border-radius: 8rpx; padding: 6rpx 14rpx;
}
.bar { padding: 4rpx 28rpx; }
.sort-item { margin-left: 32rpx; font-size: 28rpx; color: $lm-ink-2; }
.sort-item.on { color: $lm-green; font-weight: 700; }
.list { padding-top: 8rpx; padding-bottom: 8rpx; }
.offer { padding: 22rpx 0; border-bottom: 2rpx solid $lm-line; }
.offer:last-child { border-bottom: none; }
.oimg { width: 120rpx; height: 120rpx; border-radius: 12rpx; background: #EEE9DD; flex-shrink: 0; }
.store-name { font-size: 30rpx; font-weight: 600; max-width: 300rpx; }
.tags { margin-top: 6rpx; }
.right { text-align: right; margin-left: 16rpx; flex-shrink: 0; }
.big-price { font-size: 36rpx; }
.big-price.low { color: $lm-red; font-size: 40rpx; }
.lowest {
  display: inline-block; font-size: 22rpx; color: #fff; background: $lm-red;
  border-radius: 8rpx; padding: 0 10rpx; margin-bottom: 4rpx;
}
</style>
