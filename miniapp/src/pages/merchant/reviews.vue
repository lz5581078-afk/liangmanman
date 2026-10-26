<template>
  <view class="page">
    <view v-if="loaded && !list.length" class="empty">还没有人评价，用心做生意，评价会慢慢来</view>
    <view v-if="list.length" class="card list">
      <ReviewItem v-for="r in list" :key="r.id" :review="r">
        <view v-if="!r.reply" class="row reply-row">
          <view class="grow" />
          <button class="btn big" @tap="reply(r)">回复</button>
        </view>
      </ReviewItem>
    </view>
    <view v-if="list.length" class="muted end">{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</view>
  </view>
</template>

<script setup>
import { onShow, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { merchantReviews, replyReview } from '../../api'
import { usePaged } from '../../utils/paged'
import { askText } from '../../utils/format'
import ReviewItem from '../../components/ReviewItem.vue'

const { list, loading, finished, loaded, reload, more } = usePaged((p) => merchantReviews(p))

onShow(() => reload().catch(() => {}))
onReachBottom(() => more().catch(() => {}))
onPullDownRefresh(async () => {
  await reload().catch(() => {})
  uni.stopPullDownRefresh()
})

async function reply(r) {
  const text = await askText('回复这条评价', r.score >= 4 ? '如：谢谢支持，欢迎再来' : '如：不好意思，下次一定注意')
  if (text === null) return
  if (!text) return uni.showToast({ title: '请写点内容', icon: 'none' })
  const updated = await replyReview(r.id, text)
  r.reply = (updated && updated.reply) || text
  uni.showToast({ title: '已回复', icon: 'success' })
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.list { padding-top: 4rpx; padding-bottom: 4rpx; }
.reply-row { margin-top: 16rpx; }
.reply-row .btn { width: 240rpx; }
.end { text-align: center; padding: 20rpx 0 40rpx; }
</style>
