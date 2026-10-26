<template>
  <view class="page">
    <view class="intro">附近农户想买这些货，你有的话点“我有货”回他，他会看到你的价钱和电话。</view>

    <view v-if="loaded && !list.length" class="empty">附近暂时没人求购，过会儿再来看看</view>

    <view v-for="w in list" :key="w.id" class="card">
      <view class="row between">
        <text class="kw grow">{{ w.keyword }}</text>
        <text v-if="(w.replies || []).length" class="tag">已回复</text>
      </view>
      <view v-if="w.note" class="note">{{ w.note }}</view>
      <view class="muted">
        <text v-if="w.distanceKm != null">离你 {{ km(w.distanceKm) }} · </text>
        <text>{{ dateTime(w.createdAt) }}</text>
      </view>
      <view v-for="r in w.replies || []" :key="r.id" class="my-reply">
        你回复：<text v-if="r.price != null">¥{{ money(r.price) }}，</text>{{ r.note }}
      </view>
      <view class="row acts">
        <button v-if="w.contactPhone" class="btn big ghost grow" @tap="call(w.contactPhone)">打电话</button>
        <button class="btn big grow" style="margin-left: 20rpx" @tap="openReply(w)">{{ (w.replies || []).length ? '再回一条' : '我有货' }}</button>
      </view>
    </view>

    <!-- 回复弹层 -->
    <view v-if="cur" class="mask" @tap="cur = null">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">回复：{{ cur.keyword }}</view>
        <view class="field">
          <view class="label">价钱（可不填）</view>
          <view class="row">
            <text class="yuan">¥</text>
            <input class="grow big-input" type="digit" v-model="form.price" placeholder="如 45" />
          </view>
        </view>
        <view class="field">
          <view class="label">说明</view>
          <textarea class="ta" v-model="form.note" maxlength="200" placeholder="如：有货，今天就能送" />
        </view>
        <view class="field row" @tap="productOpen = !productOpen">
          <text class="label grow">关联我的商品（可不选）</text>
          <text class="sub">{{ pickedName || '不关联' }} ›</text>
        </view>
        <scroll-view v-if="productOpen" scroll-y class="plist">
          <view class="pitem row" @tap="pickProduct(null)"><text class="grow">不关联</text></view>
          <view v-for="p in products" :key="p.id" class="pitem row" @tap="pickProduct(p)">
            <text class="grow ellipsis">{{ p.name }}</text>
            <text class="muted">¥{{ money(p.price) }}/{{ p.unit }}</text>
          </view>
        </scroll-view>
        <button class="btn big" style="margin-top: 24rpx" :disabled="saving" @tap="send">发出回复</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { merchantWanted, replyWanted, myProducts } from '../../api'
import { money, km, dateTime, call } from '../../utils/format'

const list = ref([])
const loaded = ref(false)
const cur = ref(null)
const products = ref([])
const productOpen = ref(false)
const saving = ref(false)
const form = reactive({ price: '', note: '有货', productId: null })

const pickedName = computed(() => {
  const p = products.value.find((x) => x.id === form.productId)
  return p ? p.name : ''
})

async function load() {
  try {
    list.value = (await merchantWanted()) || []
  } finally {
    loaded.value = true
  }
}

onShow(() => load().catch(() => {}))
onPullDownRefresh(async () => {
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

async function openReply(w) {
  cur.value = w
  Object.assign(form, { price: '', note: '有货', productId: null })
  productOpen.value = false
  if (!products.value.length) products.value = (await myProducts(true).catch(() => [])) || []
}

function pickProduct(p) {
  form.productId = p ? p.id : null
  if (p && !form.price) form.price = String(p.price)
  productOpen.value = false
}

async function send() {
  const note = form.note.trim()
  if (!note) return uni.showToast({ title: '请写一句说明', icon: 'none' })
  let price = null
  if (form.price !== '' && form.price != null) {
    price = Number(form.price)
    if (!(price >= 0)) return uni.showToast({ title: '价钱填得不对', icon: 'none' })
  }
  saving.value = true
  try {
    await replyWanted(cur.value.id, { price, note, productId: form.productId })
    uni.showToast({ title: '回复好了', icon: 'success' })
    cur.value = null
    load()
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.intro { background: $lm-wheat-light; color: #8A5A00; padding: 24rpx 28rpx; font-size: 30rpx; }
.kw { font-size: 38rpx; font-weight: 800; }
.note { font-size: 32rpx; margin: 6rpx 0; }
.my-reply { background: $lm-green-light; color: $lm-green; border-radius: 12rpx; padding: 12rpx 18rpx; margin-top: 12rpx; font-size: 28rpx; }
.acts { margin-top: 20rpx; }
.acts .btn { padding: 0; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(0, 0, 0, 0.45); z-index: 50; }
.sheet {
  position: absolute; left: 0; right: 0; bottom: 0; background: #fff;
  border-top-left-radius: 28rpx; border-top-right-radius: 28rpx; padding: 28rpx;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}
.sheet-title { font-size: 36rpx; font-weight: 800; }
.field { padding: 18rpx 0; border-bottom: 2rpx solid $lm-line; }
.label { color: $lm-ink-2; font-size: 30rpx; }
.yuan { font-size: 48rpx; color: $lm-wheat; font-weight: 800; margin-right: 8rpx; }
.big-input { font-size: 48rpx; font-weight: 800; height: 88rpx; }
.ta { width: 100%; height: 140rpx; font-size: 32rpx; margin-top: 8rpx; }
.plist { max-height: 400rpx; }
.pitem { padding: 22rpx 8rpx; border-bottom: 2rpx solid $lm-line; font-size: 32rpx; }
</style>
