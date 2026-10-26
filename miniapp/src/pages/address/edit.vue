<template>
  <view>
    <view class="card">
      <view class="field row">
        <text class="label">收货人</text>
        <input class="grow input" v-model="f.contactName" maxlength="32" placeholder="怎么称呼" />
      </view>
      <view class="field row">
        <text class="label">手机号</text>
        <input class="grow input" type="number" v-model="f.phone" maxlength="11" placeholder="11 位手机号" />
      </view>
      <view class="field row" @tap="chooseLoc">
        <text class="label">位置</text>
        <text class="grow" :class="f.lat ? '' : 'muted'">{{ locText || '点这里在地图上选（用于算送货距离）' }}</text>
        <text class="sub">›</text>
      </view>
      <view class="field row">
        <text class="label">乡镇</text>
        <input class="grow input" v-model="f.town" maxlength="32" placeholder="如：城关镇" />
      </view>
      <view class="field row">
        <text class="label">村</text>
        <input class="grow input" v-model="f.village" maxlength="32" placeholder="如：东王村" />
      </view>
      <view class="field row">
        <text class="label">详细</text>
        <input class="grow input" v-model="f.detail" maxlength="200" placeholder="如：村东头小卖部旁" />
      </view>
      <view class="field row">
        <text class="label">设为默认</text>
        <switch :checked="!!f.isDefault" color="#2E6B30" @change="(e) => (f.isDefault = e.detail.value)" />
      </view>
    </view>

    <view style="padding: 24rpx">
      <button class="btn big" :disabled="saving" @tap="save">保存</button>
      <button v-if="id" class="btn warn" style="margin-top: 24rpx" @tap="remove">删除这个地址</button>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listAddresses, addAddress, updateAddress, deleteAddress } from '../../api'
import { location } from '../../store/location'

const id = ref(0)
const saving = ref(false)
const locText = ref('')
const f = reactive({ contactName: '', phone: '', lat: null, lng: null, town: '', village: '', detail: '', isDefault: false })

onLoad(async (q) => {
  if (q && q.id) {
    id.value = Number(q.id)
    const a = (await listAddresses()).find((x) => x.id === id.value)
    if (a) {
      Object.assign(f, a)
      locText.value = '已选位置'
    }
  }
})

function chooseLoc() {
  uni.chooseLocation({
    latitude: f.lat || location.lat,
    longitude: f.lng || location.lng,
    success: (r) => {
      f.lat = r.latitude
      f.lng = r.longitude
      locText.value = r.name || r.address || '已选位置'
      if (!f.detail && r.name) f.detail = r.name
    },
  })
}

async function save() {
  if (!f.contactName.trim()) return uni.showToast({ title: '请填写收货人', icon: 'none' })
  if (!/^1\d{10}$/.test(f.phone)) return uni.showToast({ title: '手机号格式不对', icon: 'none' })
  if (f.lat == null) return uni.showToast({ title: '请在地图上选位置', icon: 'none' })
  saving.value = true
  const body = {
    contactName: f.contactName, phone: f.phone, lat: f.lat, lng: f.lng,
    town: f.town || null, village: f.village || null, detail: f.detail || null, isDefault: !!f.isDefault,
  }
  try {
    const saved = id.value ? await updateAddress(id.value, body) : await addAddress(body)
    // 从结算页来新增的，直接选中
    uni.$emit('lm-address-selected', saved)
    uni.navigateBack()
  } finally {
    saving.value = false
  }
}

function remove() {
  uni.showModal({
    title: '删除这个地址？',
    success: async (r) => {
      if (r.confirm) {
        await deleteAddress(id.value)
        uni.navigateBack()
      }
    },
  })
}
</script>

<style lang="scss">
.field { padding: 18rpx 0; border-bottom: 2rpx solid $lm-line; min-height: 72rpx; }
.field:last-child { border-bottom: none; }
.label { width: 150rpx; color: $lm-ink-2; flex-shrink: 0; }
.input { height: 64rpx; font-size: 30rpx; }
</style>
