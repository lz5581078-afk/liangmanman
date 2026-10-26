<template>
  <view>
    <view class="row top-bar">
      <text class="grow sub">附近老板看到你的求购，有货会在这里回复</text>
      <button class="btn small" @tap="create">发求购</button>
    </view>

    <view v-if="loaded && !list.length" class="empty">
      <view>还没发过求购</view>
      <view class="muted" style="margin-top: 12rpx">搜不到想要的货时，发个求购让老板来找你</view>
    </view>

    <view v-for="w in list" :key="w.id" class="card">
      <view class="row between">
        <text class="kw grow">{{ w.keyword }}</text>
        <text class="tag" :class="w.status === 'OPEN' ? '' : 'gray'">{{ STATUS[w.status] || w.status }}</text>
      </view>
      <view v-if="w.note" class="sub">{{ w.note }}</view>
      <view class="muted">{{ dateTime(w.createdAt) }} 发布<text v-if="w.status === 'OPEN' && w.expireAt"> · {{ dateTime(w.expireAt) }} 过期</text></view>

      <view class="replies">
        <view v-if="!(w.replies || []).length" class="muted">还没有老板回复，再等等</view>
        <view v-for="r in w.replies || []" :key="r.id" class="reply">
          <view class="row between">
            <text class="sname ellipsis grow" @tap="goStore(r.storeId)">{{ r.storeName }} ›</text>
            <text v-if="r.price != null" class="price rprice">{{ money(r.price) }}</text>
          </view>
          <view class="rnote">{{ r.note }}</view>
          <view class="row r-acts">
            <text class="muted grow">{{ dateTime(r.createdAt) }}</text>
            <button v-if="r.productId" class="btn small ghost" @tap="goProduct(r.productId)">看商品</button>
            <button v-if="r.storePhone" class="btn small" style="margin-left: 12rpx" @tap="call(r.storePhone)">打电话</button>
          </view>
        </view>
      </view>

      <view v-if="w.status === 'OPEN'" class="row close-row">
        <view class="grow" />
        <button class="btn small warn" @tap="close(w)">买到了，关掉</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { myWanted, closeWanted } from '../../api'
import { ensureLogin } from '../../api/request'
import { money, dateTime, call, confirmBox } from '../../utils/format'

const STATUS = { OPEN: '求购中', CLOSED: '已关闭', EXPIRED: '已过期' }
const list = ref([])
const loaded = ref(false)

async function load() {
  await ensureLogin().catch(() => {})
  try {
    list.value = (await myWanted()) || []
  } finally {
    loaded.value = true
  }
}

onShow(() => load().catch(() => {}))
onPullDownRefresh(async () => {
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

function create() {
  uni.navigateTo({ url: '/pages/wanted/create' })
}

async function close(w) {
  if (!(await confirmBox('关掉这条求购？', '关掉后老板就看不到了'))) return
  await closeWanted(w.id)
  uni.showToast({ title: '已关闭', icon: 'none' })
  load()
}

function goStore(id) {
  uni.navigateTo({ url: '/pages/store/store?id=' + id })
}

function goProduct(id) {
  uni.navigateTo({ url: '/pages/product/product?id=' + id })
}
</script>

<style lang="scss">
.top-bar { background: #fff; padding: 20rpx 24rpx; }
.kw { font-size: 34rpx; font-weight: 800; }
.replies { margin-top: 16rpx; border-top: 2rpx solid $lm-line; padding-top: 8rpx; }
.reply { padding: 16rpx 0; border-bottom: 2rpx dashed $lm-line; }
.reply:last-child { border-bottom: none; }
.sname { font-size: 31rpx; font-weight: 700; color: $lm-green; }
.rprice { font-size: 36rpx; margin-left: 12rpx; }
.rnote { margin: 6rpx 0; }
.r-acts { margin-top: 6rpx; }
.close-row { margin-top: 12rpx; }
</style>
