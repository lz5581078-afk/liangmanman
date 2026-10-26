<template>
  <view class="page">
    <!-- 1 选商品 -->
    <view class="step-title"><text class="num">1</text>凑哪个货</view>
    <view class="card row" @tap="pickerOpen = true">
      <template v-if="product">
        <image class="img" :src="product.images && product.images[0]" mode="aspectFill" />
        <view class="grow" style="margin-left: 20rpx">
          <view class="pname">{{ product.name }}</view>
          <view class="sub">现价 ¥{{ money(product.price) }}/{{ product.unit }}</view>
        </view>
        <text class="sub">换 ›</text>
      </template>
      <template v-else>
        <text class="grow pick">点这里选一个在卖的商品</text>
        <text class="sub">›</text>
      </template>
    </view>

    <!-- 2 凑单价 -->
    <view class="step-title"><text class="num">2</text>凑够了每{{ unit }}卖多少钱</view>
    <view class="card row price-row">
      <text class="yuan">¥</text>
      <input class="price-input grow" type="digit" v-model="f.groupPrice" placeholder="0.00" />
      <text class="muted">/{{ unit }}</text>
    </view>
    <view v-if="product && Number(f.groupPrice) > 0" class="hint" :class="{ bad: Number(f.groupPrice) >= Number(product.price) }">
      {{ Number(f.groupPrice) >= Number(product.price) ? '凑单价要比现价低，农户才愿意凑' : '比现价便宜 ¥' + money(Number(product.price) - Number(f.groupPrice)) }}
    </view>

    <!-- 3 凑够多少 -->
    <view class="step-title"><text class="num">3</text>凑够多少{{ unit }}</view>
    <view class="card row price-row">
      <input class="price-input grow" type="number" v-model="f.targetQty" placeholder="如 50" />
      <text class="muted">{{ unit }}</text>
    </view>

    <!-- 4 几天 -->
    <view class="step-title"><text class="num">4</text>凑几天</view>
    <view class="chips row">
      <text v-for="d in [1, 3, 5, 7]" :key="d" class="chip" :class="{ on: !customDays && f.days === d }" @tap="pickDays(d)">{{ d }} 天</text>
      <text class="chip" :class="{ on: customDays }" @tap="customDays = true">自己填</text>
    </view>
    <view v-if="customDays" class="card row price-row">
      <input class="price-input grow" type="number" v-model="f.customDaysValue" placeholder="1–30" />
      <text class="muted">天</text>
    </view>

    <!-- 5 限购 -->
    <view class="step-title"><text class="num">5</text>每人最多要几{{ unit }}</view>
    <view class="card row">
      <text class="grow sw-label">不限</text>
      <switch :checked="noLimit" color="#2E6B30" @change="(e) => (noLimit = e.detail.value)" />
    </view>
    <view v-if="!noLimit" class="card row price-row">
      <input class="price-input grow" type="number" v-model="f.limitPerUser" placeholder="如 10" />
      <text class="muted">{{ unit }}</text>
    </view>

    <!-- 6 立刻截止 -->
    <view class="card row">
      <view class="grow">
        <view class="sw-label">凑够了就立刻截止</view>
        <view class="muted">不开：到时间才截止，凑够了还能继续多卖</view>
      </view>
      <switch :checked="f.endWhenReached" color="#2E6B30" @change="(e) => (f.endWhenReached = e.detail.value)" />
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow wheat" :disabled="saving" @tap="save">开始凑单</button>
    </view>

    <!-- 选商品 -->
    <view v-if="pickerOpen" class="mask" @tap="pickerOpen = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">选一个在卖的商品</view>
        <scroll-view scroll-y class="plist">
          <view v-for="p in products" :key="p.id" class="pitem row" @tap="pick(p)">
            <image class="img sm" :src="p.images && p.images[0]" mode="aspectFill" />
            <view class="grow" style="margin-left: 16rpx">
              <view class="ellipsis">{{ p.name }}</view>
              <view class="muted">¥{{ money(p.price) }}/{{ p.unit }}</view>
            </view>
            <text class="go">选</text>
          </view>
          <view v-if="!products.length" class="empty">还没有在卖的商品，先去发商品</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { myProducts, createGroupBuy } from '../../api'
import { money } from '../../utils/format'

const products = ref([])
const product = ref(null)
const pickerOpen = ref(false)
const customDays = ref(false)
const noLimit = ref(true)
const saving = ref(false)
const f = reactive({ groupPrice: '', targetQty: '', days: 3, customDaysValue: '', limitPerUser: '', endWhenReached: false })

const unit = computed(() => (product.value ? product.value.unit : '件'))

onLoad(async (q) => {
  products.value = (await myProducts(true).catch(() => [])) || []
  if (q && q.productId) {
    product.value = products.value.find((p) => p.id === Number(q.productId)) || null
  }
})

function pick(p) {
  product.value = p
  pickerOpen.value = false
}

function pickDays(d) {
  customDays.value = false
  f.days = d
}

async function save() {
  if (!product.value) return uni.showToast({ title: '先选一个商品', icon: 'none' })
  const groupPrice = Number(f.groupPrice)
  if (!(groupPrice > 0)) return uni.showToast({ title: '请填凑单价', icon: 'none' })
  if (groupPrice >= Number(product.value.price)) return uni.showToast({ title: '凑单价要比现价低', icon: 'none' })
  const targetQty = Math.floor(Number(f.targetQty))
  if (!(targetQty >= 2)) return uni.showToast({ title: '凑够多少至少填 2', icon: 'none' })
  const days = customDays.value ? Math.floor(Number(f.customDaysValue)) : f.days
  if (!(days >= 1 && days <= 30)) return uni.showToast({ title: '天数填 1 到 30', icon: 'none' })
  let limitPerUser = null
  if (!noLimit.value) {
    limitPerUser = Math.floor(Number(f.limitPerUser))
    if (!(limitPerUser >= 1)) return uni.showToast({ title: '请填每人最多几' + unit.value, icon: 'none' })
  }
  saving.value = true
  try {
    const g = await createGroupBuy({
      productId: product.value.id,
      groupPrice,
      targetQty,
      days,
      limitPerUser,
      endWhenReached: f.endWhenReached,
    })
    uni.showModal({
      title: '凑单开好了',
      content: '分享到村里的微信群，或者生成海报贴在店门口，让更多人来凑。',
      confirmText: '生成海报',
      cancelText: '回列表',
      success: (r) => {
        if (r.confirm && g && g.id) uni.redirectTo({ url: '/pages/poster/poster?type=group&id=' + g.id })
        else uni.navigateBack()
      },
    })
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.step-title { font-size: 36rpx; font-weight: 800; margin: 36rpx 28rpx 12rpx; display: flex; align-items: center; }
.num {
  width: 48rpx; height: 48rpx; border-radius: 50%; background: $lm-green; color: #fff; font-size: 28rpx;
  display: inline-flex; align-items: center; justify-content: center; margin-right: 16rpx;
}
.img { width: 120rpx; height: 120rpx; border-radius: 14rpx; background: #EEE9DD; flex-shrink: 0; }
.img.sm { width: 96rpx; height: 96rpx; }
.pname { font-size: 34rpx; font-weight: 700; }
.pick { color: $lm-green; font-weight: 700; font-size: 34rpx; }
.price-row { padding: 20rpx 28rpx; }
.yuan { font-size: 56rpx; color: $lm-wheat; font-weight: 800; margin-right: 8rpx; }
.price-input { font-size: 56rpx; font-weight: 800; height: 96rpx; }
.hint { padding: 0 32rpx; color: $lm-green; font-size: 28rpx; }
.hint.bad { color: $lm-red; }
.chips { flex-wrap: wrap; padding: 0 20rpx; }
.chip {
  padding: 18rpx 36rpx; margin: 8rpx; border-radius: 999rpx; background: #fff; border: 2rpx solid $lm-line; font-size: 32rpx;
}
.chip.on { border-color: $lm-green; color: $lm-green; background: $lm-green-light; font-weight: 700; }
.sw-label { font-size: 34rpx; font-weight: 700; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(0, 0, 0, 0.45); z-index: 50; }
.sheet {
  position: absolute; left: 0; right: 0; bottom: 0; background: #fff;
  border-top-left-radius: 28rpx; border-top-right-radius: 28rpx; padding: 28rpx;
}
.sheet-title { font-size: 34rpx; font-weight: 800; margin-bottom: 12rpx; }
.plist { height: 760rpx; }
.pitem { padding: 20rpx 0; border-bottom: 2rpx solid $lm-line; font-size: 32rpx; }
.go { color: $lm-green; font-weight: 700; margin-left: 16rpx; font-size: 34rpx; }
</style>
