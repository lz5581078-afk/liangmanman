<template>
  <view class="card mo">
    <view class="row between">
      <text class="status">{{ order.statusLabel }}</text>
      <text class="muted">{{ dateTime(order.createdAt) }}</text>
    </view>
    <view class="row meta">
      <text class="tag">{{ order.fulfillmentLabel }}</text>
      <text class="tag" :class="order.payMethod === 'COD' ? 'wheat' : ''">{{ order.payMethod === 'COD' ? '货到付款' : '已在线支付' }}</text>
      <text v-if="order.expectedDate" class="tag gray">{{ order.expectedDate }} 送</text>
    </view>

    <view class="who row">
      <view class="grow">
        <view class="contact">{{ order.contactName }} {{ order.contactPhone }}</view>
        <view v-if="order.addressText" class="sub">{{ order.addressText }}</view>
      </view>
      <view class="icon-btn" @tap="call(order.contactPhone)">电话</view>
      <view v-if="order.addressLat" class="icon-btn" @tap="navigate(order.addressLat, order.addressLng, order.contactName, order.addressText)">导航</view>
    </view>

    <view class="lines">
      <view v-for="l in order.items" :key="l.productId" class="row between line">
        <text class="ellipsis grow">{{ l.name }}</text>
        <text class="qty">× {{ l.quantity }}{{ l.unit }}</text>
      </view>
    </view>
    <view v-if="order.remark" class="remark">留言：{{ order.remark }}</view>
    <view v-if="order.expressNo" class="sub">快递：{{ order.expressCompany }} {{ order.expressNo }}</view>

    <view class="row between total-row">
      <text>{{ order.payMethod === 'COD' && !order.codReceived ? '要收' : '金额' }} <text class="price amount">{{ money(order.totalAmount) }}</text></text>
    </view>

    <view v-if="order.merchantActions.length" class="row acts">
      <button
        v-for="a in order.merchantActions"
        :key="a"
        class="btn big act"
        :class="a === 'REJECT' || a === 'CANCEL' ? 'warn' : ''"
        @tap="$emit('act', order, a)"
      >{{ LABELS[a] }}</button>
    </view>
  </view>
</template>

<script setup>
import { money, dateTime, call, navigate, MERCHANT_ACTION_LABELS as LABELS } from '../utils/format'

defineProps({ order: { type: Object, required: true } })
defineEmits(['act'])
</script>

<style lang="scss">
.mo .status { font-size: 36rpx; font-weight: 800; color: $lm-wheat; }
.mo .meta { margin: 10rpx 0; }
.mo .who { padding: 16rpx 0; border-top: 2rpx solid $lm-line; border-bottom: 2rpx solid $lm-line; }
.mo .contact { font-size: 34rpx; font-weight: 700; }
.icon-btn {
  margin-left: 16rpx; padding: 14rpx 22rpx; border-radius: 999rpx;
  border: 2rpx solid $lm-green; color: $lm-green; font-size: 28rpx; font-weight: 600;
}
.mo .lines { padding: 12rpx 0; }
.mo .line { font-size: 32rpx; padding: 6rpx 0; }
.mo .qty { font-weight: 800; margin-left: 16rpx; }
.mo .remark { background: $lm-wheat-light; color: #8A5A00; padding: 12rpx 16rpx; border-radius: 10rpx; margin: 8rpx 0; }
.mo .total-row { margin: 8rpx 0; font-size: 32rpx; }
.mo .amount { font-size: 44rpx; }
.mo .acts { margin-top: 16rpx; }
.mo .act { flex: 1; margin: 0 8rpx; padding: 0; }
</style>
