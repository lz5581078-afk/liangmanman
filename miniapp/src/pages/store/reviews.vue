<template>
  <view>
    <view v-if="total" class="card summary row">
      <text v-if="rating" class="big-num">{{ rating }}</text>
      <text v-if="rating" class="stars">{{ stars }}</text>
      <text class="sub">{{ total }} 条评价</text>
    </view>

    <view v-if="loaded && !list.length" class="empty">还没有人评价这家店</view>
    <view v-if="list.length" class="card list">
      <ReviewItem v-for="r in list" :key="r.id" :review="r" />
    </view>
    <view v-if="list.length" class="muted end">{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { storeReviews } from '../../api'
import { usePaged } from '../../utils/paged'
import ReviewItem from '../../components/ReviewItem.vue'

const storeId = ref(0)
const rating = ref('')
const stars = ref('')
const { list, loading, finished, loaded, total, reload, more } = usePaged((p) => storeReviews(storeId.value, p))

onLoad((q) => {
  storeId.value = Number(q.id)
  if (Number(q.rating) > 0) {
    rating.value = Number(q.rating).toFixed(1)
    const n = Math.round(Number(q.rating))
    stars.value = '★★★★★'.slice(0, n) + '☆☆☆☆☆'.slice(0, 5 - n)
  }
  if (q.name) {
    try {
      uni.setNavigationBarTitle({ title: decodeURIComponent(q.name) + ' 的评价' })
    } catch (e) {
      // 忽略
    }
  }
  reload().catch(() => {})
})

onReachBottom(() => more().catch(() => {}))
onPullDownRefresh(async () => {
  await reload().catch(() => {})
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss">
.summary { align-items: baseline; }
.stars { color: $lm-wheat; font-size: 32rpx; margin-right: 16rpx; }
.big-num { font-size: 52rpx; font-weight: 800; color: $lm-wheat; margin-right: 12rpx; }
.list { padding-top: 4rpx; padding-bottom: 4rpx; }
.end { text-align: center; padding: 20rpx 0 40rpx; }
</style>
