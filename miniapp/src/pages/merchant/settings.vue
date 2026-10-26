<template>
  <view v-if="f">
    <view class="section-title">基本信息</view>
    <view class="card">
      <view class="field row"><text class="label">店名</text><input class="grow input" v-model="f.name" maxlength="64" /></view>
      <view class="field row"><text class="label">电话</text><input class="grow input" type="number" v-model="f.phone" maxlength="20" /></view>
      <view class="field row" @tap="chooseLoc">
        <text class="label">位置</text>
        <text class="grow ellipsis">{{ f.addressText || '已选' }}</text>
        <text class="sub">修改 ›</text>
      </view>
      <view class="field row"><text class="label">营业时间</text><input class="grow input" v-model="f.businessHours" maxlength="64" placeholder="如：7:00-19:00" /></view>
      <view class="field">
        <text class="label">店铺公告</text>
        <textarea class="area" v-model="f.notice" maxlength="500" placeholder="如：每周六送货进村，满 300 包送" auto-height />
      </view>
      <view class="field row" @tap="pickCover">
        <text class="label">门头照</text>
        <image v-if="f.coverImage" class="cover" :src="f.coverImage" mode="aspectFill" />
        <text v-else class="muted grow">点这里拍</text>
      </view>
    </view>

    <view class="section-title">送货上门</view>
    <view class="card">
      <view class="field row">
        <text class="label grow">提供送货上门</text>
        <switch :checked="f.deliveryEnabled" color="#2E6B30" @change="(e) => (f.deliveryEnabled = e.detail.value)" />
      </view>
      <template v-if="f.deliveryEnabled">
        <view class="field row"><text class="label">送多远</text><input class="grow input" type="digit" v-model="f.deliveryRadiusKm" /><text class="unit">公里内</text></view>
        <view class="field row"><text class="label">起送金额</text><input class="grow input" type="digit" v-model="f.minOrderAmount" /><text class="unit">元</text></view>
        <view class="field row"><text class="label">配送费</text><input class="grow input" type="digit" v-model="f.deliveryFee" /><text class="unit">元（0 免费）</text></view>
      </template>
    </view>

    <view class="section-title">其他</view>
    <view class="card">
      <view class="field row">
        <text class="label grow">允许到店自提</text>
        <switch :checked="f.pickupEnabled" color="#2E6B30" @change="(e) => (f.pickupEnabled = e.detail.value)" />
      </view>
      <view class="field row">
        <text class="label grow">接受货到付款</text>
        <switch :checked="f.codEnabled" color="#2E6B30" @change="(e) => (f.codEnabled = e.detail.value)" />
      </view>
    </view>

    <view style="padding: 24rpx 24rpx 60rpx">
      <button class="btn big" :disabled="saving" @tap="save">保存</button>
    </view>
  </view>
  <view v-else class="empty">加载中…</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { myStore, updateStore } from '../../api'
import { uploadImage } from '../../api/request'
import { pickImages } from '../../utils/format'

const f = ref(null)
const saving = ref(false)

onLoad(async () => {
  const s = await myStore()
  if (!s) {
    uni.redirectTo({ url: '/pages/merchant/home' })
    return
  }
  f.value = {
    ...s,
    deliveryRadiusKm: String(s.deliveryRadiusKm),
    minOrderAmount: String(s.minOrderAmount),
    deliveryFee: String(s.deliveryFee),
  }
})

function chooseLoc() {
  uni.chooseLocation({
    latitude: f.value.lat,
    longitude: f.value.lng,
    success: (r) => {
      f.value.lat = r.latitude
      f.value.lng = r.longitude
      f.value.addressText = r.address || r.name
    },
  })
}

async function pickCover() {
  try {
    const [p] = await pickImages(1)
    f.value.coverImage = await uploadImage(p)
  } catch (e) {
    // 取消或已提示
  }
}

async function save() {
  const v = f.value
  const radius = Number(v.deliveryRadiusKm)
  if (v.deliveryEnabled && !(radius >= 0.5 && radius <= 100)) {
    return uni.showToast({ title: '送货范围填 0.5 到 100 公里', icon: 'none' })
  }
  saving.value = true
  try {
    await updateStore({
      name: v.name, phone: v.phone, lat: v.lat, lng: v.lng, addressText: v.addressText,
      coverImage: v.coverImage || null, notice: v.notice || '', businessHours: v.businessHours || '',
      deliveryEnabled: v.deliveryEnabled, pickupEnabled: v.pickupEnabled, codEnabled: v.codEnabled,
      deliveryRadiusKm: v.deliveryEnabled ? radius : undefined,
      minOrderAmount: Number(v.minOrderAmount || 0),
      deliveryFee: Number(v.deliveryFee || 0),
    })
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 600)
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.field { padding: 20rpx 0; border-bottom: 2rpx solid $lm-line; font-size: 32rpx; }
.field:last-child { border-bottom: none; }
.label { width: 180rpx; color: $lm-ink-2; flex-shrink: 0; }
.input { height: 64rpx; font-size: 32rpx; }
.unit { color: $lm-ink-3; font-size: 28rpx; margin-left: 8rpx; }
.area { width: 100%; min-height: 100rpx; margin-top: 10rpx; font-size: 30rpx; }
.cover { width: 200rpx; height: 130rpx; border-radius: 12rpx; }
</style>
