<template>
  <view class="page">
    <view class="intro">说说你要什么，附近的农资店老板有货会回你，留的电话他们能直接打过来。</view>
    <view class="card">
      <view class="field">
        <view class="label">要什么</view>
        <input class="input" v-model="keyword" maxlength="64" placeholder="如：玉米种子 郑单958" />
      </view>
      <view class="field">
        <view class="label">备注（要多少、什么时候要）</view>
        <textarea class="ta" v-model="note" maxlength="200" placeholder="如：要 20 袋，下周用" />
      </view>
      <view class="field">
        <view class="label">联系电话</view>
        <input class="input" type="number" maxlength="11" v-model="phone" placeholder="11 位手机号" />
      </view>
      <view class="field row">
        <text class="grow sub">位置：{{ location.name }}</text>
        <text class="link" @tap="changeLoc">换个位置</text>
      </view>
    </view>
    <view class="muted tip">求购 3 天后自动过期，买到了可以在“我的 → 我的求购”里关掉。</view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow" :disabled="saving" @tap="submit">发布求购</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createWanted, getMe } from '../../api'
import { ensureLogin } from '../../api/request'
import { location, chooseOnMap } from '../../store/location'

const keyword = ref('')
const note = ref('')
const phone = ref('')
const saving = ref(false)

onLoad(async (q) => {
  if (q && q.keyword) {
    try {
      keyword.value = decodeURIComponent(q.keyword)
    } catch (e) {
      keyword.value = q.keyword
    }
  }
  await ensureLogin().catch(() => {})
  const me = await getMe().catch(() => null)
  if (me && me.phone) phone.value = me.phone
})

function changeLoc() {
  chooseOnMap().catch(() => {})
}

async function submit() {
  if (!keyword.value.trim()) return uni.showToast({ title: '请写上要什么', icon: 'none' })
  if (!/^1\d{10}$/.test(phone.value)) return uni.showToast({ title: '请填 11 位手机号', icon: 'none' })
  saving.value = true
  try {
    await createWanted({
      keyword: keyword.value.trim(),
      note: note.value.trim() || null,
      contactPhone: phone.value,
      lat: location.lat,
      lng: location.lng,
    })
    uni.showToast({ title: '发好了，等老板回复', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: '/pages/wanted/mine' }), 700)
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.intro { background: $lm-wheat-light; color: #8A5A00; padding: 24rpx 28rpx; font-size: 28rpx; }
.field { padding: 20rpx 0; border-bottom: 2rpx solid $lm-line; }
.field:last-child { border-bottom: none; }
.label { color: $lm-ink-2; font-size: 28rpx; margin-bottom: 8rpx; }
.input { height: 80rpx; font-size: 34rpx; }
.ta { width: 100%; height: 160rpx; font-size: 32rpx; }
.link { color: $lm-green; font-weight: 700; margin-left: 16rpx; }
.tip { padding: 0 28rpx; }
</style>
