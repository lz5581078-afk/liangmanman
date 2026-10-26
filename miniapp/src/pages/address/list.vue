<template>
  <view>
    <view v-if="loaded && !list.length" class="empty">还没有收货地址</view>
    <view v-for="a in list" :key="a.id" class="card row" @tap="pick(a)">
      <view class="grow">
        <view class="name">
          {{ a.contactName }} {{ a.phone }}
          <text v-if="a.isDefault" class="tag" style="margin-left: 12rpx">默认</text>
        </view>
        <view class="sub">{{ [a.town, a.village, a.detail].filter(Boolean).join(' ') }}</view>
      </view>
      <text class="edit" @tap.stop="edit(a.id)">编辑</text>
    </view>
    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow" @tap="edit()">+ 新增地址</button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listAddresses } from '../../api'

const list = ref([])
const loaded = ref(false)
let selectMode = false

onLoad((q) => {
  selectMode = q && q.select === '1'
})

onShow(async () => {
  try {
    list.value = await listAddresses()
  } finally {
    loaded.value = true
  }
})

function pick(a) {
  if (!selectMode) return edit(a.id)
  uni.$emit('lm-address-selected', a)
  uni.navigateBack()
}

function edit(id) {
  uni.navigateTo({ url: '/pages/address/edit' + (id ? '?id=' + id : '') })
}
</script>

<style lang="scss">
.name { font-size: 32rpx; font-weight: 600; }
.edit { color: $lm-green; padding: 16rpx; }
</style>
