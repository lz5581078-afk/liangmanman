<template>
  <view class="page">
    <view class="card">
      <view class="q">{{ storeName ? '这次在「' + storeName + '」买得怎么样？' : '这次买得怎么样？' }}</view>
      <view class="stars row">
        <text v-for="n in 5" :key="n" class="star" :class="{ on: n <= score }" @tap="score = n">★</text>
      </view>
      <view class="score-text">{{ LABELS[score] }}</view>
    </view>

    <view class="card">
      <textarea class="content" v-model="content" maxlength="500" placeholder="说说货好不好、送得快不快（可不填）" />
      <view class="muted count">{{ content.length }}/500</view>
      <view class="photos">
        <view v-for="(img, i) in images" :key="img" class="photo">
          <image class="pimg" :src="img" mode="aspectFill" />
          <view class="del" @tap="images.splice(i, 1)">×</view>
        </view>
        <view v-if="images.length < 3" class="photo add" @tap="addPhoto">
          <text class="cam">＋</text>
          <text class="muted">晒照片</text>
        </view>
      </view>
      <view v-if="uploading" class="muted">照片上传中…</view>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow" :disabled="!score || saving || uploading" @tap="submit">提交评价</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { reviewOrder } from '../../api'
import { uploadImage } from '../../api/request'
import { pickImages } from '../../utils/format'

const LABELS = ['点星星打分', '很差', '不太好', '一般', '不错', '非常好']
const orderId = ref(0)
const storeName = ref('')
const score = ref(5)
const content = ref('')
const images = ref([])
const uploading = ref(false)
const saving = ref(false)

onLoad((q) => {
  orderId.value = Number(q.orderId)
  try {
    storeName.value = q.storeName ? decodeURIComponent(q.storeName) : ''
  } catch (e) {
    storeName.value = ''
  }
})

async function addPhoto() {
  let paths
  try {
    paths = await pickImages(3 - images.value.length)
  } catch (e) {
    return
  }
  uploading.value = true
  try {
    for (const p of paths) {
      if (images.value.length >= 3) break
      images.value.push(await uploadImage(p))
    }
  } catch (e) {
    // 已提示
  } finally {
    uploading.value = false
  }
}

async function submit() {
  if (!score.value) return uni.showToast({ title: '请点星星打分', icon: 'none' })
  saving.value = true
  try {
    await reviewOrder(orderId.value, {
      score: score.value,
      content: content.value.trim() || null,
      images: images.value,
    })
    uni.showToast({ title: '谢谢你的评价', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 700)
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.q { font-size: 34rpx; font-weight: 700; text-align: center; }
.stars { justify-content: center; margin: 24rpx 0 8rpx; }
.star { font-size: 88rpx; color: #DDD7CA; margin: 0 10rpx; line-height: 1; }
.star.on { color: $lm-wheat; }
.score-text { text-align: center; color: $lm-wheat; font-weight: 700; font-size: 32rpx; }
.content { width: 100%; height: 220rpx; font-size: 32rpx; }
.count { text-align: right; }
.photos { display: flex; flex-wrap: wrap; margin-top: 12rpx; }
.photo { width: 200rpx; height: 200rpx; margin: 8rpx 16rpx 8rpx 0; border-radius: 16rpx; position: relative; overflow: hidden; }
.pimg { width: 100%; height: 100%; }
.photo.add { border: 4rpx dashed $lm-ink-3; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.cam { font-size: 64rpx; color: $lm-ink-3; line-height: 1; }
.del {
  position: absolute; right: 0; top: 0; width: 52rpx; height: 52rpx;
  background: rgba(0, 0, 0, 0.55); color: #fff; text-align: center; line-height: 48rpx; font-size: 36rpx;
  border-bottom-left-radius: 14rpx;
}
</style>
