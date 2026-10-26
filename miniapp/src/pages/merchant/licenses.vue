<template>
  <view class="page">
    <view class="intro">卖农药、种子、兽药要有对应的证件。上传后平台会审核，审核通过农户能在店里看到“证件齐全”。</view>

    <view v-if="loaded && !list.length" class="empty">还没上传证件</view>
    <view v-for="l in list" :key="l.id" class="card lic row">
      <image class="img" :src="l.image" mode="aspectFill" @tap="preview(l.image)" />
      <view class="grow" style="margin-left: 20rpx">
        <view class="l-type">{{ l.typeLabel || TYPE_LABEL[l.type] || l.type }}</view>
        <view class="st" :class="l.status">{{ STATUS[l.status] || l.status }}</view>
        <view v-if="l.status === 'REJECTED' && l.rejectReason" class="reason">原因：{{ l.rejectReason }}</view>
        <view class="muted">有效期：{{ l.validUntil || '没填' }}</view>
      </view>
    </view>

    <!-- 上传新证件 -->
    <view class="section-title">上传新证件</view>
    <view class="card">
      <view class="label">什么证件</view>
      <view class="types">
        <text v-for="t in TYPES" :key="t.value" class="chip" :class="{ on: f.type === t.value }" @tap="f.type = t.value">{{ t.label }}</text>
      </view>
      <view class="label" style="margin-top: 20rpx">拍证件</view>
      <view v-if="f.image" class="photo" @tap="pick">
        <image class="pimg" :src="f.image" mode="aspectFill" />
        <view class="redo">重拍</view>
      </view>
      <view v-else class="photo add" @tap="pick">
        <text class="cam">＋</text>
        <text>{{ uploading ? '上传中…' : '点这里拍照' }}</text>
      </view>
      <view class="row between" style="margin-top: 20rpx">
        <text class="label">有效期到（可不填）</text>
        <text v-if="f.validUntil" class="link" @tap="f.validUntil = ''">清除</text>
      </view>
      <picker mode="date" :value="f.validUntil" @change="(e) => (f.validUntil = e.detail.value)">
        <view class="picker-v" :class="{ muted: !f.validUntil }">{{ f.validUntil || '点这里选日期' }}</view>
      </picker>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow" :disabled="saving || uploading" @tap="submit">提交审核</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { myLicenses, addLicense } from '../../api'
import { uploadImage } from '../../api/request'
import { pickImages } from '../../utils/format'

const TYPES = [
  { value: 'BUSINESS', label: '营业执照' },
  { value: 'PESTICIDE', label: '农药经营许可证' },
  { value: 'SEED', label: '种子经营备案' },
  { value: 'VET', label: '兽药经营许可证' },
]
const TYPE_LABEL = Object.fromEntries(TYPES.map((t) => [t.value, t.label]))
const STATUS = { PENDING: '审核中', APPROVED: '已通过', REJECTED: '没通过' }

const list = ref([])
const loaded = ref(false)
const uploading = ref(false)
const saving = ref(false)
const f = reactive({ type: 'BUSINESS', image: '', validUntil: '' })

async function load() {
  try {
    list.value = (await myLicenses()) || []
  } finally {
    loaded.value = true
  }
}

onShow(() => load().catch(() => {}))

async function pick() {
  let path
  try {
    ;[path] = await pickImages(1)
  } catch (e) {
    return
  }
  uploading.value = true
  try {
    f.image = await uploadImage(path)
  } catch (e) {
    // 已提示
  } finally {
    uploading.value = false
  }
}

function preview(url) {
  if (url) uni.previewImage({ urls: [url] })
}

async function submit() {
  if (!f.image) return uni.showToast({ title: '请先拍证件照片', icon: 'none' })
  saving.value = true
  try {
    await addLicense({ type: f.type, image: f.image, validUntil: f.validUntil || null })
    uni.showToast({ title: '交上去了，等审核', icon: 'success' })
    f.image = ''
    f.validUntil = ''
    load()
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.intro { background: $lm-wheat-light; color: #8A5A00; padding: 24rpx 28rpx; font-size: 28rpx; }
.lic .img { width: 160rpx; height: 120rpx; border-radius: 12rpx; background: #EEE9DD; flex-shrink: 0; }
.l-type { font-size: 34rpx; font-weight: 800; }
.st { font-weight: 700; color: $lm-wheat; }
.st.APPROVED { color: $lm-green; }
.st.REJECTED { color: $lm-red; }
.reason { color: $lm-red; font-size: 28rpx; }
.label { color: $lm-ink-2; font-size: 30rpx; }
.types { margin-top: 8rpx; }
.chip {
  display: inline-block; padding: 16rpx 28rpx; margin: 8rpx 12rpx 8rpx 0; border-radius: 999rpx;
  background: #fff; border: 2rpx solid $lm-line; font-size: 32rpx;
}
.chip.on { border-color: $lm-green; color: $lm-green; background: $lm-green-light; font-weight: 700; }
.photo { width: 100%; height: 360rpx; border-radius: 20rpx; overflow: hidden; margin-top: 12rpx; position: relative; }
.pimg { width: 100%; height: 100%; }
.photo.add {
  border: 4rpx dashed $lm-green; box-sizing: border-box; color: $lm-green; font-weight: 700;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.cam { font-size: 80rpx; line-height: 1; }
.redo { position: absolute; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.55); color: #fff; padding: 10rpx 24rpx; border-top-left-radius: 16rpx; }
.picker-v { height: 80rpx; line-height: 80rpx; font-size: 34rpx; }
.link { color: $lm-green; font-weight: 700; }
</style>
