<template>
  <view class="stepper row" @tap.stop>
    <view v-if="value > 0" class="b minus" @tap.stop="$emit('change', value - 1)">−</view>
    <text v-if="value > 0" class="n">{{ value }}</text>
    <view class="b plus" @tap.stop="$emit('change', value + 1)">+</view>
  </view>
</template>

<script setup>
defineProps({ value: { type: Number, default: 0 } })
defineEmits(['change'])
</script>

<style lang="scss">
.stepper .b {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1;
}
.stepper .plus { background: $lm-green; color: #fff; }
.stepper .minus { border: 2rpx solid $lm-ink-3; color: $lm-ink-2; }
.stepper .n { min-width: 56rpx; text-align: center; font-size: 30rpx; font-weight: 600; }
</style>
