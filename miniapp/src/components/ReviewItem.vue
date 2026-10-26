<template>
  <view class="rv">
    <view class="row between">
      <text class="rv-name">{{ review.nickname || '匿名' }}</text>
      <text class="muted">{{ dateTime(review.createdAt) }}</text>
    </view>
    <view class="rv-stars">{{ stars }}</view>
    <view v-if="review.content" class="rv-content">{{ review.content }}</view>
    <view v-if="review.images && review.images.length" class="rv-imgs">
      <image v-for="(img, i) in review.images" :key="i" class="rv-img" :src="img" mode="aspectFill" @tap="preview(i)" />
    </view>
    <view v-if="review.reply" class="rv-reply">
      <text class="rv-reply-t">老板回复：</text>{{ review.reply }}
    </view>
    <slot />
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { dateTime } from '../utils/format'

const props = defineProps({ review: { type: Object, required: true } })
const stars = computed(() => {
  const n = Math.max(0, Math.min(5, Number(props.review.score) || 0))
  return '★★★★★'.slice(0, n) + '☆☆☆☆☆'.slice(0, 5 - n)
})

function preview(i) {
  uni.previewImage({ urls: props.review.images, current: i })
}
</script>

<style lang="scss">
.rv { padding: 24rpx 0; border-bottom: 2rpx solid $lm-line; }
.rv:last-child { border-bottom: none; }
.rv-name { font-weight: 700; font-size: 30rpx; }
.rv-stars { color: $lm-wheat; font-size: 30rpx; letter-spacing: 2rpx; margin: 4rpx 0; }
.rv-content { font-size: 30rpx; white-space: pre-wrap; }
.rv-imgs { display: flex; margin-top: 12rpx; }
.rv-img { width: 180rpx; height: 180rpx; border-radius: 12rpx; margin-right: 12rpx; background: #EEE9DD; }
.rv-reply { margin-top: 12rpx; background: $lm-bg; border-radius: 12rpx; padding: 14rpx 18rpx; color: $lm-ink-2; font-size: 28rpx; }
.rv-reply-t { color: $lm-green; font-weight: 700; }
</style>
