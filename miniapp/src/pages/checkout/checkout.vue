<template>
  <view v-if="pv">
    <!-- 凑单标识 -->
    <view v-if="groupBuyId" class="group-banner row">
      <text class="gb-tag">凑单</text>
      <text class="grow">按凑单价下单，需在线付款；凑不成自动全额退款</text>
    </view>

    <!-- 配送方式 -->
    <view class="section-title">怎么拿货</view>
    <view class="card opts">
      <view
        v-for="o in pv.options"
        :key="o.fulfillment"
        class="opt"
        :class="{ on: fulfillment === o.fulfillment, off: !o.available }"
        @tap="pickFulfillment(o)"
      >
        <view class="row between">
          <text class="opt-name">{{ o.label }}</text>
          <text v-if="o.available" class="sub">{{ Number(o.fee) ? '运费 ¥' + money(o.fee) : '免运费' }}</text>
        </view>
        <view v-if="!o.available" class="muted">{{ o.reason }}</view>
      </view>
    </view>

    <!-- 地址 / 联系人 -->
    <view v-if="needAddress" class="card row" @tap="chooseAddress">
      <view v-if="address" class="grow">
        <view class="addr-name">{{ address.contactName }} {{ address.phone }}</view>
        <view class="sub">{{ [address.town, address.village, address.detail].filter(Boolean).join(' ') }}</view>
      </view>
      <view v-else class="grow strong">+ 添加收货地址</view>
      <text class="sub">›</text>
    </view>
    <view v-if="fulfillment === 'PICKUP'" class="card">
      <view class="sub">到店自提：{{ storeName }}</view>
      <view class="row field">
        <text class="label">联系电话</text>
        <input class="grow input" type="number" maxlength="11" v-model="contactPhone" placeholder="方便老板联系你" />
      </view>
    </view>

    <!-- 期望送达 -->
    <view v-if="fulfillment === 'DELIVERY'" class="card">
      <view class="sub">希望哪天送到</view>
      <view class="row days">
        <view v-for="d in days" :key="d.value" class="day" :class="{ on: expectedDate === d.value }" @tap="expectedDate = d.value">
          {{ d.label }}
        </view>
      </view>
    </view>

    <!-- 付款方式 -->
    <view v-if="current" class="section-title">怎么付钱</view>
    <view v-if="current" class="card row pays">
      <view
        v-for="m in shownPayMethods"
        :key="m"
        class="pay"
        :class="{ on: payMethod === m }"
        @tap="payMethod = m"
      >
        {{ m === 'COD' ? (fulfillment === 'PICKUP' ? '到店付款' : '货到付款') : '微信支付' }}
      </view>
    </view>

    <!-- 商品 -->
    <view class="section-title">{{ storeName }}</view>
    <view class="card">
      <view v-for="l in pv.items" :key="l.productId" class="line row">
        <image class="img" :src="l.image" mode="aspectFill" />
        <view class="grow" style="margin-left: 16rpx">
          <view class="ellipsis">{{ l.name }}</view>
          <view class="muted">¥{{ money(l.price) }} × {{ l.quantity }}{{ l.unit }}</view>
        </view>
        <text>¥{{ money(l.amount) }}</text>
      </view>
      <view class="row between sum"><text class="sub">商品</text><text>¥{{ money(pv.goodsAmount) }}</text></view>
      <view class="row between sum"><text class="sub">运费</text><text>¥{{ money(current ? current.fee : 0) }}</text></view>
      <input class="input remark" v-model="remark" maxlength="200" placeholder="给老板留言（可不填）" />
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <view class="grow">
        <text class="muted">合计 </text>
        <text class="price total">{{ money(current ? current.total : pv.goodsAmount) }}</text>
      </view>
      <button class="btn big" :disabled="!canSubmit || submitting" @tap="submit">提交订单</button>
    </view>
  </view>
  <view v-else class="empty">{{ error || '加载中…' }}</view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { listAddresses, previewOrder, createOrder, getMe } from '../../api'
import { subscribeOrderNotice } from '../../utils/subscribe'
import { itemsForOrder, clearStore, summary } from '../../store/cart'
import { payForOrder } from '../../utils/pay'
import { money, today } from '../../utils/format'

const storeId = ref(0)
const storeName = ref('')
const pv = ref(null)
const error = ref('')
const address = ref(null)
const fulfillment = ref('')
const payMethod = ref('')
const expectedDate = ref(today())
const contactPhone = ref('')
const remark = ref('')
const submitting = ref(false)
// 凑单：商品来自页面参数，不走购物车
const groupBuyId = ref(0)
const groupItem = ref(null)

const days = [
  { label: '今天', value: today() },
  { label: '明天', value: today(1) },
  { label: '后天', value: today(2) },
]

const current = computed(() => (pv.value ? pv.value.options.find((o) => o.fulfillment === fulfillment.value) : null))
const shownPayMethods = computed(() => {
  const ms = current.value ? current.value.payMethods : []
  return groupBuyId.value ? ms.filter((m) => m === 'ONLINE') : ms
})
const needAddress = computed(() => fulfillment.value === 'DELIVERY' || fulfillment.value === 'EXPRESS')
const canSubmit = computed(() => {
  if (!current.value || !current.value.available || !payMethod.value) return false
  if (needAddress.value && !address.value) return false
  if (fulfillment.value === 'PICKUP' && !/^1\d{10}$/.test(contactPhone.value)) return false
  return true
})

function onAddressSelected(a) {
  address.value = a
  refresh()
}

function orderItems() {
  return groupBuyId.value ? [groupItem.value] : itemsForOrder(storeId.value)
}

onLoad(async (q) => {
  storeId.value = Number(q.storeId)
  storeName.value = summary(storeId.value).storeName
  if (q.groupBuyId) {
    groupBuyId.value = Number(q.groupBuyId)
    groupItem.value = { productId: Number(q.productId), quantity: Math.max(1, Number(q.qty) || 1) }
    try {
      storeName.value = q.storeName ? decodeURIComponent(q.storeName) : ''
    } catch (e) {
      storeName.value = q.storeName || ''
    }
    uni.setNavigationBarTitle({ title: '确认凑单' })
  }
  uni.$on('lm-address-selected', onAddressSelected)
  try {
    const [addrs, me] = await Promise.all([listAddresses(), getMe()])
    address.value = addrs.find((a) => a.isDefault) || addrs[0] || null
    contactPhone.value = (address.value && address.value.phone) || me.phone || ''
  } catch (e) {
    // 未登录等情况，下面 refresh 会提示
  }
  refresh()
})

onUnload(() => uni.$off('lm-address-selected', onAddressSelected))

async function refresh() {
  const items = orderItems()
  if (!items.length || !items[0].productId) {
    error.value = groupBuyId.value ? '凑单信息不对，请返回重试' : '购物车是空的'
    pv.value = null
    return
  }
  try {
    const body = { storeId: storeId.value, items, addressId: address.value ? address.value.id : null }
    if (groupBuyId.value) body.groupBuyId = groupBuyId.value
    pv.value = await previewOrder(body)
  } catch (e) {
    error.value = e.msg || '加载失败'
    return
  }
  // 默认选第一个可用的方式；当前选择不可用了就换掉
  if (!current.value || !current.value.available) {
    const first = pv.value.options.find((o) => o.available)
    fulfillment.value = first ? first.fulfillment : pv.value.options[0].fulfillment
  }
  fixPayMethod()
}

function fixPayMethod() {
  let methods = current.value ? current.value.payMethods : []
  if (groupBuyId.value) methods = methods.filter((m) => m === 'ONLINE')
  if (!methods.includes(payMethod.value)) {
    // 农村习惯先收货后付款：有货到付款时默认选它
    payMethod.value = methods.includes('COD') ? 'COD' : methods[0] || ''
  }
}

function pickFulfillment(o) {
  if (!o.available) {
    uni.showToast({ title: o.reason, icon: 'none' })
    return
  }
  fulfillment.value = o.fulfillment
  fixPayMethod()
}

function chooseAddress() {
  uni.navigateTo({ url: '/pages/address/list?select=1' })
}

async function submit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  // 必须在点击里先弹订阅授权，拒绝了也照常下单
  await subscribeOrderNotice()
  try {
    const order = await createOrder({
      storeId: storeId.value,
      groupBuyId: groupBuyId.value || null,
      items: orderItems(),
      fulfillment: fulfillment.value,
      payMethod: payMethod.value,
      addressId: needAddress.value && address.value ? address.value.id : null,
      contactPhone: fulfillment.value === 'PICKUP' && !address.value ? contactPhone.value : null,
      expectedDate: fulfillment.value === 'DELIVERY' ? expectedDate.value : null,
      remark: remark.value || null,
    })
    // 凑单下单不动购物车
    if (!groupBuyId.value) clearStore(storeId.value)
    if (payMethod.value === 'ONLINE') {
      const paid = await payForOrder(order.id).catch(() => false)
      if (!paid) uni.showToast({ title: '还没付款，可在订单里继续支付', icon: 'none' })
    } else {
      uni.showToast({ title: '下单成功，等老板接单', icon: 'none' })
    }
    uni.redirectTo({ url: '/pages/order/detail?id=' + order.id })
  } catch (e) {
    // 错误已由 request 提示；可能是库存、范围变化，刷新一下
    refresh()
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss">
.group-banner {
  background: $lm-wheat-light;
  color: #8A5A00;
  padding: 20rpx 28rpx;
  font-size: 28rpx;
}
.gb-tag {
  background: $lm-wheat;
  color: #fff;
  font-weight: 800;
  border-radius: 8rpx;
  padding: 4rpx 14rpx;
  margin-right: 16rpx;
  font-size: 26rpx;
}
.opts { padding: 12rpx 24rpx; }
.opt {
  border: 2rpx solid $lm-line;
  border-radius: 16rpx;
  padding: 22rpx 24rpx;
  margin: 12rpx 0;
}
.opt.on { border-color: $lm-green; background: $lm-green-light; }
.opt.off { opacity: 0.55; }
.opt-name { font-size: 32rpx; font-weight: 700; }
.addr-name { font-size: 32rpx; font-weight: 600; }
.strong { color: $lm-green; font-weight: 700; font-size: 32rpx; }
.field { margin-top: 16rpx; }
.label { width: 150rpx; color: $lm-ink-2; }
.input { height: 72rpx; background: $lm-bg; border-radius: 12rpx; padding: 0 20rpx; font-size: 30rpx; }
.days { margin-top: 12rpx; }
.day, .pay {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 12rpx;
  border: 2rpx solid $lm-line;
  margin-right: 16rpx;
  font-size: 30rpx;
}
.day:last-child, .pay:last-child { margin-right: 0; }
.day.on, .pay.on { border-color: $lm-green; color: $lm-green; font-weight: 700; background: $lm-green-light; }
.line { padding: 12rpx 0; }
.line .img { width: 80rpx; height: 80rpx; border-radius: 10rpx; background: #EEE9DD; }
.sum { padding: 8rpx 0; }
.remark { margin-top: 16rpx; }
.total { font-size: 44rpx; }
</style>
