<template>
  <view v-if="cart.count > 0" class="footer-bar">
    <view class="grow">
      <text class="price total">{{ money(cart.total) }}</text>
      <text class="muted" style="margin-left: 12rpx">共 {{ cart.count }} 件</text>
    </view>
    <view class="muted clear" @tap="clear">清空</view>
    <button class="btn" @tap="checkout">去结算</button>
  </view>
</template>

<script setup>
import { storeCart, clearStore } from '../store/cart'
import { money } from '../utils/format'

const props = defineProps({ storeId: { type: [Number, String], required: true } })
const cart = storeCart(Number(props.storeId))

function clear() {
  uni.showModal({
    title: '清空购物车？',
    success: (r) => r.confirm && clearStore(Number(props.storeId)),
  })
}

function checkout() {
  uni.navigateTo({ url: '/pages/checkout/checkout?storeId=' + props.storeId })
}
</script>

<style lang="scss">
.total { font-size: 40rpx; }
.clear { margin-right: 24rpx; padding: 12rpx; }
</style>
