<template>
  <view>
    <view class="bar row">
      <view v-for="d in days" :key="d.value" class="day" :class="{ on: date === d.value }" @tap="setDate(d.value)">{{ d.label }}</view>
      <view class="grow" />
      <view class="copy" @tap="copy">复制清单</view>
    </view>
    <view class="muted hint">送货上门、已接单还没送达的订单，按村分好了</view>

    <view v-if="loaded && !villages.length" class="empty">这天没有要送的货</view>

    <view v-for="v in villages" :key="v">
      <view class="section-title">{{ v }}（{{ sheet[v].length }} 单）</view>
      <MerchantOrderCard v-for="o in sheet[v]" :key="o.id" :order="o" @act="onAct" />
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { deliverySheet } from '../../api'
import { today } from '../../utils/format'
import { runMerchantAction } from '../../utils/merchant-actions'
import MerchantOrderCard from '../../components/MerchantOrderCard.vue'

const days = [
  { label: '今天', value: today() },
  { label: '明天', value: today(1) },
]
const date = ref(today())
const sheet = ref({})
const loaded = ref(false)
const villages = computed(() => Object.keys(sheet.value))

async function load() {
  try {
    sheet.value = await deliverySheet(date.value)
  } finally {
    loaded.value = true
  }
}

onShow(load)

function setDate(d) {
  date.value = d
  load()
}

async function onAct(order, action) {
  try {
    if (await runMerchantAction(order, action)) load()
  } catch (e) {
    load()
  }
}

/** 复制成文字，发给送货的伙计 */
function copy() {
  const lines = [`${date.value} 送货清单`]
  for (const v of villages.value) {
    lines.push(`\n【${v}】`)
    for (const o of sheet.value[v]) {
      const goods = o.items.map((l) => `${l.name}×${l.quantity}${l.unit || ''}`).join('，')
      const money = o.payMethod === 'COD' && !o.codReceived ? `，收款${o.totalAmount}元` : '，已付款'
      lines.push(`${o.contactName} ${o.contactPhone}｜${o.addressText || ''}｜${goods}${money}`)
    }
  }
  uni.setClipboardData({ data: lines.join('\n') })
}
</script>

<style lang="scss">
.bar { background: #fff; padding: 20rpx 24rpx; }
.day { padding: 14rpx 32rpx; border-radius: 999rpx; border: 2rpx solid $lm-line; margin-right: 16rpx; font-size: 32rpx; }
.day.on { border-color: $lm-green; color: $lm-green; background: $lm-green-light; font-weight: 800; }
.copy { color: $lm-green; font-weight: 700; font-size: 30rpx; }
.hint { padding: 12rpx 28rpx 0; }
</style>
