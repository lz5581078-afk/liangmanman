<template>
  <view>
    <view class="head">
      <view class="bar row">
        <input
          class="input grow"
          v-model="keyword"
          confirm-type="search"
          placeholder="输入商品名，如：复合肥、草甘膦"
          :focus="!keyword"
          @confirm="doSearch"
        />
        <view class="scan" @tap="photo">拍照</view>
        <view class="scan" @tap="scan">扫码</view>
        <view class="go" @tap="doSearch">搜索</view>
      </view>

      <!-- 排序 + 筛选（吸顶） -->
      <view v-if="searched" class="sort row">
        <text class="sort-item" :class="{ on: sort === 'DEFAULT' }" @tap="setSort('DEFAULT')">综合</text>
        <text class="sort-item" :class="{ on: sort === 'DISTANCE' }" @tap="setSort('DISTANCE')">离我近</text>
        <text class="sort-item" :class="{ on: isPrice }" @tap="togglePrice">价格{{ priceArrow }}</text>
        <text class="sort-item" :class="{ on: sort === 'SALES' }" @tap="setSort('SALES')">销量</text>
      </view>
      <view v-if="searched" class="chips row">
        <text v-for="c in deliveryChips" :key="c.v" class="chip" :class="{ on: delivery === c.v }" @tap="setDelivery(c.v)">{{ c.t }}</text>
      </view>
    </view>

    <!-- 精准找货（条码 / 登记证号 / 扫码）：这一款在哪几家卖、多少钱 -->
    <view v-for="e in exact" :key="e.sku.id" class="card exact">
      <view class="exact-label">找到这一款</view>
      <view class="row">
        <image v-if="e.sku.image || (e.offers[0] && e.offers[0].image)" class="ex-img" :src="e.sku.image || e.offers[0].image" mode="aspectFill" />
        <view class="grow">
          <view class="ex-name">{{ e.sku.displayName }}</view>
          <view v-if="e.sku.regNo || e.sku.barcode" class="muted">
            <text v-if="e.sku.regNo">{{ e.sku.regNo }}</text>
            <text v-if="e.sku.regNo && e.sku.barcode"> · </text>
            <text v-if="e.sku.barcode">条码 {{ e.sku.barcode }}</text>
          </view>
          <view class="sub">
            <text v-if="e.offers.length">{{ e.offers.length }} 家在卖 · 最低 <text class="price">{{ money(e.minPrice) }}</text></text>
            <text v-else>附近还没有店在卖这一款</text>
          </view>
        </view>
      </view>
      <view v-if="e.offers.length" class="ex-offers">
      <view v-for="o in e.offers.slice(0, 3)" :key="o.productId" class="offer row" @tap="goProduct(o.productId)">
        <view class="grow">
          <view class="row">
            <text class="store-name ellipsis">{{ o.storeName }}</text>
            <text v-if="!o.storeOpen" class="tag gray" style="margin-left: 10rpx">打烊</text>
          </view>
          <view class="tags">
            <text v-if="o.distanceKm != null" class="muted" style="margin-right: 12rpx">{{ km(o.distanceKm) }}</text>
            <text v-if="o.canDeliver" class="tag">送货上门</text>
            <text v-if="o.canPickup" class="tag">自提</text>
            <text v-if="o.express" class="tag wheat">快递{{ Number(o.expressFee) ? ' ¥' + money(o.expressFee) : '包邮' }}</text>
          </view>
        </view>
        <view class="right">
          <text class="price big-price">{{ money(o.price) }}</text>
          <text class="muted">/{{ o.unit }}</text>
        </view>
      </view>
      </view>
      <view v-if="e.offers.length" class="ex-all" @tap="goCompare(e.sku.id)">看全部 {{ e.offers.length }} 家比价 ›</view>
    </view>

    <view v-if="searched && loaded && total > 0" class="count muted">找到 {{ total }} 件商品</view>

    <view v-if="searched && !loaded && loading" class="empty">搜索中…</view>
    <view v-else-if="searched && loaded && !list.length && !exact.length" class="empty">
      <block v-if="delivery">
        <view>没有符合条件的「{{ searched }}」</view>
        <view class="muted" style="margin-top: 12rpx">去掉上面的筛选试试</view>
      </block>
      <block v-else>
        <view>附近还没有店在卖「{{ searched }}」</view>
        <view class="muted" style="margin-top: 12rpx">换个说法试试，比如“复合肥”“除草剂”</view>
      </block>
      <button class="btn wheat want-btn" @tap="goWanted">发个求购，附近老板有货会回你</button>
    </view>

    <!-- 平铺列表：每家店的每个商品一条 -->
    <view v-for="r in list" :key="r.productId" class="card item row" @tap="goProduct(r.productId)">
      <image class="img" :src="r.image" mode="aspectFill" />
      <view class="grow info">
        <view class="name">{{ r.name }}</view>
        <view v-if="r.brand || r.spec" class="muted ellipsis">{{ [r.brand, r.spec].filter(Boolean).join(' · ') }}</view>
        <view class="row price-line">
          <text class="price big-price">{{ money(r.price) }}</text>
          <text class="muted">/{{ r.unit }}</text>
          <text v-if="r.sales" class="muted grow sales">已售 {{ r.sales }}</text>
        </view>
        <view class="sub ellipsis">{{ r.storeName }}<text v-if="r.distanceKm != null"> · {{ km(r.distanceKm) }}</text></view>
        <view class="tags">
          <text v-if="!r.storeOpen" class="tag gray">打烊</text>
          <text v-if="r.canDeliver" class="tag">送货上门</text>
          <text v-if="r.canPickup" class="tag">自提</text>
          <text v-if="r.express" class="tag wheat">快递{{ Number(r.expressFee) ? ' ¥' + money(r.expressFee) : '包邮' }}</text>
        </view>
        <view v-if="r.skuId && r.sameCount > 1" class="same" @tap.stop="goCompare(r.skuId)">{{ r.sameCount }} 家有同款 · 比一比 ›</view>
      </view>
    </view>

    <view v-if="list.length" class="muted end">{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</view>

    <!-- 拍照识别的候选 -->
    <view v-if="cands.length" class="mask" @tap="cands = []">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">认出来可能是这些，点一下就搜</view>
        <scroll-view scroll-y class="cand-list">
          <view v-for="c in cands" :key="c.id" class="cand row" @tap="pickCand(c)">
            <image v-if="c.image" class="cand-img" :src="c.image" mode="aspectFill" />
            <text class="grow">{{ stdLabel(c) }}</text>
            <text class="go">搜</text>
          </view>
          <view v-if="ocrKeyword" class="cand row" @tap="pickKeyword">
            <text class="grow">都不是，就搜「{{ ocrKeyword }}」</text>
            <text class="go">搜</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { search } from '../../api'
import { currentLoc } from '../../store/location'
import { money, km } from '../../utils/format'
import { usePaged } from '../../utils/paged'
import { scanForKeyword } from '../../utils/scan'
import { photoRecognize, stdLabel } from '../../utils/ocr'

const PAGE_SIZE = 20
const deliveryChips = [
  { v: 'DELIVER', t: '送货上门' },
  { v: 'PICKUP', t: '到店自提' },
  { v: 'EXPRESS', t: '快递' },
]

const keyword = ref('')
const searched = ref('') // 当前列表对应的关键词
const sort = ref('DEFAULT')
const delivery = ref('')
const exact = ref([])
const cands = ref([])
const ocrKeyword = ref('')

let firstSeq = 0
const { list, loading, finished, loaded, total, reload, more } = usePaged(async (page) => {
  const my = page === 1 ? ++firstSeq : firstSeq
  const r = await search(searched.value, currentLoc(), {
    sort: sort.value,
    delivery: delivery.value,
    page,
    size: PAGE_SIZE,
  })
  if (page === 1 && my === firstSeq) exact.value = (r && r.exact) || []
  return r
})

const isPrice = computed(() => sort.value === 'PRICE_ASC' || sort.value === 'PRICE_DESC')
const priceArrow = computed(() => (sort.value === 'PRICE_ASC' ? ' ↑' : sort.value === 'PRICE_DESC' ? ' ↓' : ' ↕'))

onLoad((q) => {
  if (q && q.keyword) {
    keyword.value = decodeURIComponent(q.keyword)
    doSearch()
  }
})

onReachBottom(() => {
  if (searched.value) more().catch(() => {})
})

function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) return
  searched.value = kw
  exact.value = []
  reload().catch(() => {})
  uni.pageScrollTo({ scrollTop: 0, duration: 0 })
}

function refresh() {
  if (!searched.value) return
  reload().catch(() => {})
  uni.pageScrollTo({ scrollTop: 0, duration: 0 })
}

function setSort(s) {
  if (sort.value === s) return
  sort.value = s
  refresh()
}

function togglePrice() {
  sort.value = sort.value === 'PRICE_ASC' ? 'PRICE_DESC' : 'PRICE_ASC'
  refresh()
}

function setDelivery(v) {
  delivery.value = delivery.value === v ? '' : v
  refresh()
}

async function scan() {
  try {
    // 条码 / 登记证号直接搜，后端会在 exact 里给出这一款的比价
    keyword.value = await scanForKeyword()
    doSearch()
  } catch (e) {
    // 取消
  }
}

async function photo() {
  const r = await photoRecognize()
  if (!r) return
  if (r.match) {
    goCompare(r.match.id)
    return
  }
  if (r.candidates.length) {
    ocrKeyword.value = r.keyword || ''
    cands.value = r.candidates
    return
  }
  const kw = r.keyword || r.barcode || r.regNo
  if (kw) {
    keyword.value = kw
    doSearch()
  }
}

function pickCand(c) {
  cands.value = []
  keyword.value = c.name || stdLabel(c)
  doSearch()
}

function pickKeyword() {
  cands.value = []
  keyword.value = ocrKeyword.value
  doSearch()
}

function goWanted() {
  const kw = searched.value || keyword.value
  uni.navigateTo({ url: '/pages/wanted/create?keyword=' + encodeURIComponent(kw || '') })
}

function goProduct(id) {
  uni.navigateTo({ url: '/pages/product/product?id=' + id })
}

function goCompare(skuId) {
  uni.navigateTo({ url: '/pages/sku/compare?id=' + skuId })
}
</script>

<style lang="scss">
.head {
  position: sticky;
  top: var(--window-top, 0);
  z-index: 20;
  background: #fff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.bar { padding: 20rpx 24rpx; }
.input {
  height: 76rpx;
  background: $lm-bg;
  border-radius: 999rpx;
  padding: 0 28rpx;
  font-size: 30rpx;
}
.scan, .go { margin-left: 16rpx; font-size: 30rpx; padding: 0 8rpx; flex-shrink: 0; }
.scan { color: $lm-ink-2; }
.go { color: $lm-green; font-weight: 700; }
.sort { padding: 4rpx 0 12rpx; }
.sort-item { flex: 1; text-align: center; font-size: 28rpx; color: $lm-ink-2; padding: 8rpx 0; }
.sort-item.on { color: $lm-green; font-weight: 700; }
.chips { padding: 0 24rpx 16rpx; }
.chip {
  font-size: 24rpx; padding: 6rpx 22rpx; margin-right: 16rpx; border-radius: 999rpx;
  background: $lm-bg; color: $lm-ink-2; border: 2rpx solid transparent;
}
.chip.on { background: $lm-green-light; color: $lm-green; border-color: $lm-green; font-weight: 600; }
.count { padding: 16rpx 28rpx 0; }

/* 平铺列表 */
.item { align-items: flex-start; margin: 16rpx 24rpx; padding: 20rpx; }
.item .img { width: 200rpx; height: 200rpx; border-radius: 12rpx; background: #EEE9DD; flex-shrink: 0; }
.info { margin-left: 20rpx; }
.name {
  font-size: 30rpx; font-weight: 600; line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.price-line { align-items: baseline; margin: 4rpx 0; }
.sales { text-align: right; }
.tags { margin-top: 6rpx; }
.same {
  display: inline-block; margin-top: 10rpx; font-size: 24rpx; color: $lm-wheat; font-weight: 600;
  background: $lm-wheat-light; border-radius: 8rpx; padding: 4rpx 14rpx;
}
.end { text-align: center; padding: 24rpx 0 48rpx; }

/* 精准找货块 */
.exact { border: 3rpx solid $lm-green; }
.exact-label {
  display: inline-block; font-size: 24rpx; font-weight: 700; color: #fff; background: $lm-green;
  border-radius: 8rpx; padding: 2rpx 14rpx; margin-bottom: 14rpx;
}
.ex-img { width: 110rpx; height: 110rpx; border-radius: 12rpx; background: #EEE9DD; flex-shrink: 0; margin-right: 20rpx; }
.ex-name { font-size: 32rpx; font-weight: 700; }
.offer { padding: 20rpx 0; border-bottom: 2rpx solid $lm-line; }
.ex-offers { margin-top: 16rpx; border-top: 2rpx solid $lm-line; }
.store-name { font-size: 30rpx; font-weight: 600; max-width: 380rpx; }
.right { text-align: right; margin-left: 16rpx; flex-shrink: 0; }
.big-price { font-size: 36rpx; }
.ex-all { text-align: center; color: $lm-green; font-weight: 700; padding-top: 20rpx; font-size: 30rpx; }

.want-btn { margin: 40rpx auto 0; width: 600rpx; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(0, 0, 0, 0.45); z-index: 50; }
.sheet {
  position: absolute; left: 0; right: 0; bottom: 0; background: #fff;
  border-top-left-radius: 28rpx; border-top-right-radius: 28rpx; padding: 28rpx;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}
.sheet-title { font-size: 32rpx; font-weight: 700; margin-bottom: 12rpx; }
.cand-list { max-height: 700rpx; }
.cand { padding: 26rpx 8rpx; border-bottom: 2rpx solid $lm-line; font-size: 32rpx; }
.cand-img { width: 80rpx; height: 80rpx; border-radius: 10rpx; margin-right: 16rpx; background: #EEE9DD; }
</style>
