<template>
  <view>
    <view class="tabs row">
      <view v-for="t in tabs" :key="t.key" class="tab" :class="{ on: tab === t.key }" @tap="setTab(t.key)">{{ t.label }}</view>
    </view>

    <view v-if="loaded && !list.length" class="empty">{{ tab === 'NEW' ? '暂时没有新订单' : '没有订单' }}</view>

    <MerchantOrderCard v-for="o in list" :key="o.id" :order="o" @act="onAct" />
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { merchantOrders } from '../../api'
import { runMerchantAction } from '../../utils/merchant-actions'
import MerchantOrderCard from '../../components/MerchantOrderCard.vue'

const tabs = [
  { key: 'NEW', label: '新订单' },
  { key: 'DOING', label: '进行中' },
  { key: 'DONE', label: '已完成' },
  { key: 'CANCELLED', label: '已取消' },
]
const tab = ref('NEW')
const list = ref([])
const loaded = ref(false)

async function load() {
  try {
    list.value = await merchantOrders(tab.value)
  } finally {
    loaded.value = true
  }
}

onShow(load)
onPullDownRefresh(async () => {
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

function setTab(k) {
  tab.value = k
  load()
}

async function onAct(order, action) {
  try {
    const updated = await runMerchantAction(order, action)
    if (updated) load()
  } catch (e) {
    load() // 状态可能已变化，刷新
  }
}
</script>

<style lang="scss">
.tabs { background: #fff; position: sticky; top: 0; z-index: 5; }
.tab { flex: 1; text-align: center; padding: 26rpx 0; font-size: 32rpx; color: $lm-ink-2; }
.tab.on { color: $lm-green; font-weight: 800; border-bottom: 6rpx solid $lm-green; }
</style>
