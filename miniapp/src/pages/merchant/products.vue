<template>
  <view>
    <view class="tabs row">
      <view class="tab" :class="{ on: onShelf }" @tap="setTab(true)">在卖的</view>
      <view class="tab" :class="{ on: !onShelf }" @tap="setTab(false)">已下架</view>
    </view>

    <view v-if="loaded && !list.length" class="empty">{{ onShelf ? '还没有商品，点下面发一个' : '没有下架的商品' }}</view>

    <view v-for="p in list" :key="p.id" class="card row item">
      <image class="img" :src="p.images[0]" mode="aspectFill" />
      <view class="grow body">
        <view class="name">{{ p.name }}<text v-if="p.spec && !p.name.includes(p.spec)" class="muted"> {{ p.spec }}</text></view>
        <view>
          <text class="price">{{ money(p.price) }}</text><text class="muted">/{{ p.unit }}</text>
          <text class="muted" style="margin-left: 16rpx">已售 {{ p.sales }}{{ p.stock != null ? ' · 库存 ' + p.stock : '' }}</text>
        </view>
        <view class="tags">
          <text v-if="p.localDelivery" class="tag">本地配送</text>
          <text v-if="p.expressDelivery" class="tag wheat">快递</text>
        </view>
        <view class="row ops">
          <button class="btn small ghost" @tap="edit(p.id)">修改</button>
          <button class="btn small" :class="p.onShelf ? 'warn' : ''" @tap="toggle(p)">{{ p.onShelf ? '下架' : '上架' }}</button>
        </view>
      </view>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow wheat" @tap="edit()">＋ 发商品</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { myProducts, setOnShelf } from '../../api'
import { money } from '../../utils/format'

const onShelf = ref(true)
const list = ref([])
const loaded = ref(false)

async function load() {
  try {
    list.value = await myProducts(onShelf.value)
  } finally {
    loaded.value = true
  }
}

onShow(load)

function setTab(v) {
  onShelf.value = v
  load()
}

function edit(id) {
  uni.navigateTo({ url: '/pages/merchant/product-edit' + (id ? '?id=' + id : '') })
}

async function toggle(p) {
  await setOnShelf(p.id, !p.onShelf)
  uni.showToast({ title: p.onShelf ? '已下架' : '已上架', icon: 'none' })
  load()
}
</script>

<style lang="scss">
.tabs { background: #fff; }
.tab { flex: 1; text-align: center; padding: 26rpx 0; font-size: 32rpx; color: $lm-ink-2; }
.tab.on { color: $lm-green; font-weight: 800; border-bottom: 6rpx solid $lm-green; }
.item { align-items: flex-start; }
.img { width: 180rpx; height: 180rpx; border-radius: 16rpx; background: #EEE9DD; flex-shrink: 0; }
.body { margin-left: 20rpx; }
.name { font-size: 32rpx; font-weight: 700; }
.tags { margin: 8rpx 0; }
.ops .btn { margin-right: 16rpx; }
</style>
