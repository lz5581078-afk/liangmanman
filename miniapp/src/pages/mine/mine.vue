<template>
  <view>
    <view class="head row">
      <image class="avatar" :src="me && me.avatar ? me.avatar : '/static/avatar.png'" mode="aspectFill" />
      <view class="grow" style="margin-left: 24rpx">
        <view class="nick">{{ me ? me.nickname : '登录中…' }}</view>
        <view class="phone">{{ me && me.phone ? me.phone : '' }}</view>
      </view>
    </view>

    <view class="card menu">
      <view class="item row" @tap="go('/pages/order/list', true)"><text class="grow">我的订单</text><text class="sub">›</text></view>
      <view class="item row" @tap="go('/pages/wanted/mine')"><text class="grow">我的求购</text><text class="sub">›</text></view>
      <view class="item row" @tap="go('/pages/address/list')"><text class="grow">收货地址</text><text class="sub">›</text></view>
      <view class="item row" @tap="editPhone"><text class="grow">联系电话</text><text class="sub">{{ me && me.phone ? me.phone : '去填写' }} ›</text></view>
    </view>

    <view class="card merchant row" @tap="go('/pages/merchant/home')">
      <view class="grow">
        <view class="m-title">{{ me && me.storeId ? '进入我的店' : '我是农资店老板，免费开店' }}</view>
        <view class="sub">{{ me && me.storeId ? storeStatusText : '拍照就能上架，附近农户都能看到' }}</view>
      </view>
      <text class="m-arrow">›</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMe, updateMe } from '../../api'
import { ensureLogin } from '../../api/request'

const me = ref(null)

const storeStatusText = computed(() => {
  const s = me.value && me.value.storeStatus
  return { PENDING: '店铺审核中', APPROVED: '营业管理、接单、发商品', REJECTED: '审核未通过，请联系平台', BANNED: '店铺已停用' }[s] || ''
})

onShow(async () => {
  await ensureLogin().catch(() => {})
  me.value = await getMe().catch(() => null)
})

function go(url, isTab) {
  if (isTab) uni.switchTab({ url })
  else uni.navigateTo({ url })
}

function editPhone() {
  uni.showModal({
    title: '联系电话',
    editable: true,
    placeholderText: '11 位手机号',
    content: (me.value && me.value.phone) || '',
    success: async (r) => {
      if (!r.confirm) return
      const phone = (r.content || '').trim()
      if (!/^1\d{10}$/.test(phone)) return uni.showToast({ title: '手机号格式不对', icon: 'none' })
      me.value = await updateMe({ phone })
    },
  })
}
</script>

<style lang="scss">
.head { background: $lm-green; padding: 48rpx 32rpx; color: #fff; }
.avatar { width: 120rpx; height: 120rpx; border-radius: 50%; background: rgba(255, 255, 255, 0.3); }
.nick { font-size: 38rpx; font-weight: 700; }
.phone { opacity: 0.85; }
.menu { padding: 0 28rpx; }
.item { padding: 30rpx 0; border-bottom: 2rpx solid $lm-line; font-size: 32rpx; }
.item:last-child { border-bottom: none; }
.merchant { background: $lm-wheat-light; }
.m-title { font-size: 34rpx; font-weight: 800; color: #8A5A00; }
.m-arrow { font-size: 48rpx; color: #8A5A00; }
</style>
