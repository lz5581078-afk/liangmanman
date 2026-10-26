<template>
  <view v-if="loaded">
    <!-- 还没开店：开店表单 -->
    <view v-if="!store">
      <view class="welcome">
        <view class="w-title">在粮满满免费开店</view>
        <view class="w-sub">附近的农户在手机上就能看到你的货、下单，你送货或让他来店里拿</view>
      </view>

      <view class="card">
        <view class="field">
          <view class="label">店名</view>
          <input class="input" v-model="form.name" maxlength="64" placeholder="如：老王农资店" />
        </view>
        <view class="field">
          <view class="label">电话（农户打给你）</view>
          <input class="input" type="number" v-model="form.phone" maxlength="20" placeholder="手机号或座机" />
        </view>
        <view class="field" @tap="chooseLoc">
          <view class="label">店在哪里</view>
          <view class="row">
            <text class="grow" :class="form.lat ? '' : 'muted'">{{ form.addressText || '点这里在地图上选' }}</text>
            <text class="sub">›</text>
          </view>
        </view>
        <view class="field">
          <view class="label">乡镇（可不填）</view>
          <input class="input" v-model="form.town" maxlength="32" placeholder="如：城关镇" />
        </view>
        <view class="field">
          <view class="label">门头照（可不填）</view>
          <image v-if="form.coverImage" class="cover-preview" :src="form.coverImage" mode="aspectFill" @tap="pickCover" />
          <view v-else class="cover-add" @tap="pickCover">＋ 拍门头</view>
        </view>
      </view>
      <view style="padding: 24rpx">
        <button class="btn big" :disabled="saving" @tap="submitOpen">开店</button>
      </view>
    </view>

    <!-- 已开店：工作台 -->
    <view v-else>
      <view class="dash">
        <view class="row between">
          <view class="grow">
            <view class="d-name">{{ store.name }}</view>
            <view class="d-status">{{ statusText }}</view>
          </view>
          <view class="open-toggle" :class="{ off: !store.isOpen }" @tap="toggleOpen">
            {{ store.isOpen ? '营业中' : '已打烊' }}
          </view>
        </view>
        <view v-if="store.status !== 'APPROVED'" class="pending">{{ pendingText }}</view>
        <view v-if="stats" class="stats row">
          <view class="stat">
            <view class="s-num">{{ stats.todayOrders || 0 }}</view>
            <view class="s-label">今天订单</view>
          </view>
          <view class="stat">
            <view class="s-num">¥{{ money(stats.todayAmount) }}</view>
            <view class="s-label">今天卖了</view>
          </view>
          <view class="stat">
            <view class="s-num">{{ stats.monthOrders || 0 }}</view>
            <view class="s-label">本月订单</view>
          </view>
          <view class="stat">
            <view class="s-num">¥{{ money(stats.monthAmount) }}</view>
            <view class="s-label">本月卖了</view>
          </view>
        </view>
        <view v-if="stats" class="s-note">金额只算已完成的订单</view>
      </view>


      <view class="big-actions">
        <view class="big-act primary" @tap="go('/pages/merchant/product-edit')">
          <text class="ba-icon">＋</text>
          <text class="ba-text">发商品</text>
        </view>
        <view class="big-act" @tap="go('/pages/merchant/orders')">
          <text class="ba-num">{{ newCount }}</text>
          <text class="ba-text">新订单</text>
        </view>
      </view>

      <!-- 收款开户提醒 -->
      <view v-if="account && account.status !== 'APPROVED'" class="pay-tip" :class="{ weak: account.onlinePayEnabled }" @tap="go('/pages/merchant/onboarding')">
        <view class="grow">
          <view class="pt-title">{{ accountTitle }}</view>
          <view class="pt-sub">{{ accountSub }}</view>
        </view>
        <text class="pt-go">{{ account.status === 'NONE' || account.status === 'REJECTED' ? '去开通' : '去看看' }} ›</text>
      </view>

      <view class="grid">
        <view class="cell" @tap="go('/pages/merchant/delivery-sheet')">送货清单</view>
        <view class="cell" @tap="go('/pages/merchant/products')">商品管理</view>
        <view class="cell" @tap="go('/pages/merchant/group-buys')">
          <text>凑单</text>
          <text class="cell-sub">人多价低，一次多卖</text>
        </view>
        <view class="cell" @tap="go('/pages/merchant/wanted')">
          <text>求购</text>
          <text class="cell-sub">附近有人要货</text>
          <text v-if="stats && stats.openWanted" class="badge">{{ stats.openWanted > 99 ? '99+' : stats.openWanted }}</text>
        </view>
        <view class="cell" @tap="go('/pages/merchant/reviews')">
          <text>评价</text>
          <text v-if="stats && stats.pendingReviews" class="cell-sub warn-t">{{ stats.pendingReviews }} 条没回复</text>
          <text v-else class="cell-sub">农户怎么说</text>
          <text v-if="stats && stats.pendingReviews" class="badge">{{ stats.pendingReviews }}</text>
        </view>
        <view class="cell" @tap="go('/pages/merchant/onboarding')">
          <text>收款账户</text>
          <text class="cell-sub" :class="{ 'warn-t': account && account.status !== 'APPROVED' }">{{ account ? (account.status === 'APPROVED' ? '已开通' : account.statusLabel || '未开通') : '' }}</text>
        </view>
        <view class="cell" @tap="go('/pages/merchant/wallet')">
          <text>钱包</text>
          <text class="cell-sub">余额、提现</text>
        </view>
        <view class="cell" @tap="go('/pages/merchant/credit')">
          <text>赊账本</text>
          <text class="cell-sub">谁欠多少记清楚</text>
        </view>
        <view class="cell" @tap="go('/pages/merchant/licenses')">
          <text>证件</text>
          <text class="cell-sub">营业执照、许可证</text>
        </view>
        <view class="cell" @tap="go('/pages/poster/poster?type=store&id=' + store.id)">
          <text>店铺海报</text>
          <text class="cell-sub">打印贴店门口</text>
        </view>
        <view class="cell" @tap="go('/pages/merchant/settings')">店铺设置</view>
        <button class="cell share" open-type="share">分享店铺</button>
      </view>

      <view v-if="stats && stats.topProducts && stats.topProducts.length" class="card">
        <view class="t-title">本月卖得最好</view>
        <view v-for="(t, i) in stats.topProducts" :key="t.productId" class="row top-line">
          <text class="top-no">{{ i + 1 }}</text>
          <text class="grow ellipsis">{{ t.name }}</text>
          <text class="sub">{{ t.quantity }} 件 · ¥{{ money(t.amount) }}</text>
        </view>
      </view>

      <view class="card tip">
        <view class="t-title">让更多农户找到你</view>
        <view class="sub">把店铺分享到村里的微信群；点上面“店铺海报”，保存到手机拿去打印，贴在店门口，农户扫一扫就能下单。</view>
      </view>
    </view>
  </view>
  <view v-else class="empty">加载中…</view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow, onPullDownRefresh, onShareAppMessage } from '@dcloudio/uni-app'
import { myStore, openStore, updateStore, merchantOrders, merchantStats, merchantAccount } from '../../api'
import { uploadImage, ensureLogin } from '../../api/request'
import { pickImages, money } from '../../utils/format'
import { location } from '../../store/location'

const loaded = ref(false)
const store = ref(null)
const newCount = ref(0)
const stats = ref(null)
const account = ref(null)
const saving = ref(false)
const form = reactive({ name: '', phone: '', lat: null, lng: null, addressText: '', town: '', coverImage: '' })

const statusText = computed(() => {
  if (!store.value) return ''
  return { PENDING: '审核中', APPROVED: '已上线，附近农户能看到', REJECTED: '审核未通过', BANNED: '已停用' }[store.value.status]
})
const accountTitle = computed(() => {
  const a = account.value
  if (!a) return ''
  if (a.status === 'NEED_SIGN') return '收款账户还差一步：签约'
  if (a.status === 'SUBMITTED' || a.status === 'AUDITING') return '收款账户审核中'
  if (a.status === 'REJECTED') return '收款开户没通过，改一下再交'
  return a.onlinePayEnabled ? '开通收款账户' : '开通收款，才能收微信付款'
})
const accountSub = computed(() => {
  const a = account.value
  if (!a) return ''
  if (a.status === 'NEED_SIGN') return '点这里，复制签约链接到微信里打开'
  if (a.status === 'SUBMITTED' || a.status === 'AUDITING') return '微信支付正在审核，一般 1–3 天'
  if (a.status === 'REJECTED') return a.rejectReason || '点这里看原因'
  return a.onlinePayEnabled ? '正式上线后，农户在线付的钱打到你自己的账户' : '没开通前，农户只能选货到付款'
})
const pendingText = computed(() => {
  const s = store.value && store.value.status
  if (s === 'PENDING') return '平台正在审核，通常 1 个工作日内。审核期间可以先把商品发上来。'
  if (s === 'REJECTED') return '审核没通过，请联系平台客服。'
  return '店铺已停用，请联系平台客服。'
})

async function load() {
  await ensureLogin().catch(() => {})
  store.value = await myStore()
  if (store.value) {
    const [news, st, acc] = await Promise.all([
      merchantOrders('NEW').catch(() => []),
      merchantStats().catch(() => null),
      merchantAccount().catch(() => null),
    ])
    newCount.value = st && st.newOrders != null ? st.newOrders : news.length
    stats.value = st
    account.value = acc
  }
  loaded.value = true
}

onShow(load)
onPullDownRefresh(async () => {
  await load().catch(() => {})
  uni.stopPullDownRefresh()
})

onShareAppMessage(() => ({
  title: store.value ? store.value.name + ' · 在粮满满下单，送货上门' : '粮满满',
  path: store.value ? '/pages/store/store?id=' + store.value.id : '/pages/index/index',
}))

function go(url) {
  uni.navigateTo({ url })
}

function chooseLoc() {
  uni.chooseLocation({
    latitude: form.lat || location.lat,
    longitude: form.lng || location.lng,
    success: (r) => {
      form.lat = r.latitude
      form.lng = r.longitude
      form.addressText = r.address || r.name || '已选位置'
    },
  })
}

async function pickCover() {
  try {
    const [p] = await pickImages(1)
    form.coverImage = await uploadImage(p)
  } catch (e) {
    // 取消或已提示
  }
}

async function submitOpen() {
  if (!form.name.trim()) return uni.showToast({ title: '请填写店名', icon: 'none' })
  if (!/^[0-9-]{7,20}$/.test(form.phone)) return uni.showToast({ title: '请填写电话', icon: 'none' })
  if (form.lat == null) return uni.showToast({ title: '请在地图上选店的位置', icon: 'none' })
  saving.value = true
  try {
    store.value = await openStore({
      name: form.name.trim(), phone: form.phone, lat: form.lat, lng: form.lng,
      addressText: form.addressText || null, town: form.town || null, coverImage: form.coverImage || null,
    })
    uni.showModal({
      title: '开店成功',
      content: '现在发第一个商品吧：拍张照、填个价就行。',
      confirmText: '去发商品',
      showCancel: false,
      success: () => go('/pages/merchant/product-edit'),
    })
  } finally {
    saving.value = false
  }
}

async function toggleOpen() {
  const next = !store.value.isOpen
  store.value = await updateStore({ isOpen: next })
  uni.showToast({ title: next ? '开始营业' : '已打烊，暂不接单', icon: 'none' })
}
</script>

<style lang="scss">
.welcome { background: $lm-green; color: #fff; padding: 48rpx 32rpx; }
.w-title { font-size: 44rpx; font-weight: 800; }
.w-sub { margin-top: 12rpx; opacity: 0.9; font-size: 30rpx; }
.field { padding: 20rpx 0; border-bottom: 2rpx solid $lm-line; font-size: 32rpx; }
.field:last-child { border-bottom: none; }
.label { color: $lm-ink-2; font-size: 28rpx; margin-bottom: 8rpx; }
.input { height: 80rpx; font-size: 34rpx; }
.cover-preview { width: 300rpx; height: 200rpx; border-radius: 16rpx; }
.cover-add { width: 300rpx; height: 200rpx; border: 4rpx dashed $lm-ink-3; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; color: $lm-ink-2; }
.dash { background: $lm-green; color: #fff; padding: 36rpx 32rpx 44rpx; }
.d-name { font-size: 42rpx; font-weight: 800; }
.d-status { opacity: 0.9; margin-top: 4rpx; }
.open-toggle { background: #fff; color: $lm-green; font-weight: 800; padding: 18rpx 32rpx; border-radius: 999rpx; font-size: 32rpx; }
.open-toggle.off { background: rgba(255, 255, 255, 0.25); color: #fff; }
.pending { margin-top: 20rpx; background: rgba(255, 255, 255, 0.15); border-radius: 12rpx; padding: 16rpx 20rpx; font-size: 28rpx; }
.big-actions { display: flex; padding: 0 24rpx; margin-top: -24rpx; }
.big-act {
  flex: 1; background: #fff; border-radius: 24rpx; height: 220rpx; margin: 0 8rpx;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}
.big-act.primary { background: $lm-wheat; color: #fff; }
.ba-icon { font-size: 80rpx; line-height: 1; font-weight: 300; }
.ba-num { font-size: 72rpx; line-height: 1; font-weight: 800; color: $lm-green; }
.ba-text { font-size: 36rpx; font-weight: 800; margin-top: 12rpx; }
.grid { display: flex; flex-wrap: wrap; padding: 16rpx; }
.cell {
  width: 330rpx; margin: 10rpx; height: 130rpx; background: #fff; border-radius: 20rpx;
  display: flex; align-items: center; justify-content: center; font-size: 34rpx; font-weight: 700; color: $lm-ink;
}
.cell { position: relative; flex-direction: column; }
.cell-sub { font-size: 24rpx; font-weight: 400; color: $lm-ink-3; margin-top: 4rpx; }
.warn-t { color: $lm-red; }
.badge {
  position: absolute; top: 14rpx; right: 18rpx; min-width: 44rpx; height: 44rpx; padding: 0 10rpx;
  border-radius: 999rpx; background: $lm-red; color: #fff; font-size: 26rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center; box-sizing: border-box;
}
.cell.share { padding: 0; line-height: 130rpx; border: none; }
.stats { margin-top: 28rpx; background: rgba(255, 255, 255, 0.14); border-radius: 16rpx; padding: 20rpx 0; }
.stat { flex: 1; text-align: center; }
.s-num { font-size: 34rpx; font-weight: 800; }
.s-label { font-size: 24rpx; opacity: 0.85; }
.s-note { font-size: 22rpx; opacity: 0.7; margin-top: 8rpx; text-align: right; }
.pay-tip {
  display: flex; align-items: center; margin: 24rpx 24rpx 0; padding: 24rpx 28rpx; border-radius: $lm-radius;
  background: $lm-red; color: #fff;
}
.pay-tip.weak { background: $lm-wheat-light; color: #8A5A00; }
.pt-title { font-size: 34rpx; font-weight: 800; }
.pt-sub { font-size: 26rpx; opacity: 0.9; margin-top: 4rpx; }
.pt-go { font-size: 32rpx; font-weight: 800; margin-left: 16rpx; flex-shrink: 0; }
.top-line { padding: 10rpx 0; font-size: 30rpx; }
.top-no { width: 44rpx; color: $lm-wheat; font-weight: 800; }
.cell.share::after { border: none; }
.tip .t-title { font-weight: 800; margin-bottom: 8rpx; }
</style>
