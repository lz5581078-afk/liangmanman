<template>
  <view class="page">
    <view class="head">
      <view class="h-note">这里只是帮你记账，钱不经过平台</view>
      <view class="h-label">一共被欠</view>
      <view class="h-num">¥{{ money(totalOwed) }}</view>
    </view>

    <view class="bar row">
      <input class="search grow" v-model="keyword" confirm-type="search" placeholder="搜名字、村、电话" @confirm="load" />
      <text class="go" @tap="load">搜</text>
    </view>

    <view v-if="loaded && !list.length" class="empty">
      <view>{{ keyword ? '没找到这个人' : '还没记过赊账' }}</view>
      <view class="muted" style="margin-top: 12rpx">点下面“添加客户”，谁赊了多少记得清清楚楚</view>
    </view>

    <view v-for="c in list" :key="c.id" class="card cust row" @tap="goDetail(c)">
      <view class="avatar">{{ (c.name || '?').slice(0, 1) }}</view>
      <view class="grow">
        <view class="c-name">{{ c.name }}</view>
        <view class="muted">{{ [c.village, c.phone].filter(Boolean).join(' · ') || '　' }}</view>
      </view>
      <view class="right">
        <view v-if="Number(c.balance) > 0" class="owe">欠 ¥{{ money(c.balance) }}</view>
        <view v-else class="clear">已结清</view>
        <view class="muted">{{ dateTime(c.updatedAt) }}</view>
      </view>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow" @tap="addOpen = true">＋ 添加客户</button>
    </view>

    <view v-if="addOpen" class="mask" @tap="addOpen = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">添加客户</view>
        <view class="field">
          <view class="label">名字</view>
          <input class="input" v-model="form.name" maxlength="32" placeholder="如：张大哥" />
        </view>
        <view class="field">
          <view class="label">村（可不填）</view>
          <input class="input" v-model="form.village" maxlength="32" placeholder="如：东王村" />
        </view>
        <view class="field">
          <view class="label">电话（可不填）</view>
          <input class="input" type="number" v-model="form.phone" maxlength="11" placeholder="手机号" />
        </view>
        <button class="btn big" style="margin-top: 24rpx" :disabled="saving" @tap="addCustomer">保存</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { creditCustomers, addCreditCustomer } from '../../api'
import { money, dateTime } from '../../utils/format'

const totalOwed = ref(0)
const list = ref([])
const loaded = ref(false)
const keyword = ref('')
const addOpen = ref(false)
const saving = ref(false)
const form = reactive({ name: '', village: '', phone: '' })

async function load() {
  try {
    const r = (await creditCustomers(keyword.value.trim())) || {}
    totalOwed.value = r.totalOwed || 0
    list.value = r.customers || []
  } finally {
    loaded.value = true
  }
}

onShow(() => load().catch(() => {}))
onPullDownRefresh(async () => {
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

function goDetail(c) {
  uni.navigateTo({ url: '/pages/merchant/credit-detail?id=' + c.id })
}

async function addCustomer() {
  if (!form.name.trim()) return uni.showToast({ title: '请填名字', icon: 'none' })
  if (form.phone && !/^\d{7,11}$/.test(form.phone)) return uni.showToast({ title: '电话填得不对', icon: 'none' })
  saving.value = true
  try {
    const c = await addCreditCustomer({ name: form.name.trim(), village: form.village.trim() || null, phone: form.phone || null })
    Object.assign(form, { name: '', village: '', phone: '' })
    addOpen.value = false
    if (c && c.id) goDetail(c)
    else load()
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.head { background: $lm-green; color: #fff; padding: 28rpx 32rpx 40rpx; }
.h-note { background: rgba(255, 255, 255, 0.18); border-radius: 12rpx; padding: 10rpx 18rpx; font-size: 28rpx; display: inline-block; }
.h-label { margin-top: 20rpx; opacity: 0.9; }
.h-num { font-size: 76rpx; font-weight: 800; line-height: 1.2; }
.bar { background: #fff; padding: 20rpx 24rpx; }
.search { height: 80rpx; background: $lm-bg; border-radius: 999rpx; padding: 0 28rpx; font-size: 32rpx; }
.go { color: $lm-green; font-weight: 800; margin-left: 20rpx; font-size: 34rpx; }
.avatar {
  width: 88rpx; height: 88rpx; border-radius: 50%; background: $lm-green-light; color: $lm-green; font-size: 38rpx;
  display: flex; align-items: center; justify-content: center; font-weight: 800; margin-right: 20rpx; flex-shrink: 0;
}
.c-name { font-size: 36rpx; font-weight: 800; }
.right { text-align: right; margin-left: 12rpx; }
.owe { color: $lm-red; font-size: 36rpx; font-weight: 800; }
.clear { color: $lm-green; font-weight: 700; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(0, 0, 0, 0.45); z-index: 50; }
.sheet {
  position: absolute; left: 0; right: 0; bottom: 0; background: #fff;
  border-top-left-radius: 28rpx; border-top-right-radius: 28rpx; padding: 28rpx;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}
.sheet-title { font-size: 36rpx; font-weight: 800; }
.field { padding: 18rpx 0; border-bottom: 2rpx solid $lm-line; }
.label { color: $lm-ink-2; font-size: 28rpx; }
.input { height: 80rpx; font-size: 34rpx; }
</style>
