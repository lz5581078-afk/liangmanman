<template>
  <view class="page">
    <!-- 第 1 步：拍照 -->
    <view class="step-title"><text class="num">1</text>拍商品照片</view>
    <view class="photos">
      <view v-for="(img, i) in f.images" :key="img" class="photo">
        <image :src="img" mode="aspectFill" class="pimg" @tap="previewImg(i)" />
        <view class="del" @tap="removeImg(i)">×</view>
      </view>
      <view v-if="f.images.length < 5" class="photo add" @tap="addImages">
        <text class="cam">＋</text>
        <text class="cam-text">{{ f.images.length ? '再拍一张' : '点这里拍照' }}</text>
      </view>
    </view>
    <view v-if="uploading" class="muted tip">照片上传中…</view>
    <view v-if="f.standardProductId && stdName" class="sku-ok row">
      <text class="sku-ok-mark">✓</text>
      <view class="grow">
        <view class="sku-ok-title">认出来了：{{ stdName }}</view>
        <view class="sku-ok-sub">名称、规格已经填好，买家能和别家同款比价</view>
      </view>
      <text class="sku-ok-no" @tap="unlink">不是</text>
    </view>

    <!-- 第 2 步：价格 -->
    <view class="step-title"><text class="num">2</text>卖多少钱</view>
    <view class="card row price-row">
      <text class="yuan">¥</text>
      <input class="price-input grow" type="digit" v-model="f.price" placeholder="0.00" />
      <text class="muted">每</text>
      <input class="unit-input" v-model="f.unit" maxlength="4" placeholder="袋" />
    </view>
    <view class="units row">
      <text v-for="u in units" :key="u" class="unit-chip" :class="{ on: f.unit === u }" @tap="f.unit = u">{{ u }}</text>
    </view>

    <!-- 第 3 步：怎么卖 -->
    <view class="step-title"><text class="num">3</text>怎么送到农户手里（可多选）</view>
    <view class="card mode" :class="{ on: f.localDelivery }" @tap="f.localDelivery = !f.localDelivery">
      <view class="row">
        <view class="check">{{ f.localDelivery ? '✓' : '' }}</view>
        <view class="grow">
          <view class="mode-name">本地配送</view>
          <view class="sub">送货上门 或 到店自提，按店铺设置的范围和运费</view>
        </view>
      </view>
    </view>
    <view class="card mode" :class="{ on: f.expressDelivery }" @tap="f.expressDelivery = !f.expressDelivery">
      <view class="row">
        <view class="check">{{ f.expressDelivery ? '✓' : '' }}</view>
        <view class="grow">
          <view class="mode-name">快递发货</view>
          <view class="sub">不限距离，远处的人也能买</view>
        </view>
      </view>
      <view v-if="f.expressDelivery" class="row fee-row" @tap.stop>
        <text class="sub">快递运费 ¥</text>
        <input class="fee-input" type="digit" v-model="f.expressFee" placeholder="0" />
        <text class="muted">填 0 就是包邮</text>
      </view>
    </view>

    <!-- 可选信息 -->
    <view class="step-title light">以下可以不填</view>
    <view class="card">
      <view class="field row">
        <text class="label">名称</text>
        <input class="grow input" v-model="f.name" maxlength="64" placeholder="不填就叫“商品”" />
      </view>
      <view class="field row">
        <view class="grow row" @tap="openPicker">
          <text class="label wide">从商品库找这一款</text>
          <text class="grow" :class="stdName ? 'linked' : 'muted'">{{ stdName || '找到了名称和分类自动填好' }}</text>
          <text class="sub">›</text>
        </view>
        <text v-if="f.standardProductId" class="unlink" @tap="unlink">不是</text>
      </view>
      <view class="field row">
        <button class="btn big ghost grow" @tap="ocr">拍包装识别</button>
        <button class="btn big ghost scan-btn" @tap="scanBarcode">扫条码</button>
      </view>
      <view v-if="f.regNo || f.barcode" class="field muted">
        已认出：<text v-if="f.regNo">登记证号 {{ f.regNo }}</text><text v-if="f.regNo && f.barcode"> · </text><text v-if="f.barcode">条码 {{ f.barcode }}</text>
      </view>
      <view class="field">
        <view class="row" @tap="moreOpen = !moreOpen">
          <text class="grow sub">更多信息（可不填）</text>
          <text class="sub">{{ moreOpen ? '收起 ˄' : '展开 ˅' }}</text>
        </view>
        <view v-if="moreOpen">
          <view class="row sub-field">
            <text class="label">品牌</text>
            <input class="grow input" v-model="f.brand" maxlength="32" placeholder="如：史丹利" />
          </view>
          <view class="row sub-field">
            <text class="label">规格</text>
            <input class="grow input" v-model="f.spec" maxlength="32" placeholder="如：50kg、200ml" />
          </view>
        </view>
      </view>
      <view class="field">
        <text class="label">分类</text>
        <view class="cats">
          <text v-for="c in cats" :key="c.id" class="unit-chip" :class="{ on: f.categoryId === c.id }" @tap="f.categoryId = c.id">{{ c.name }}</text>
        </view>
      </view>
      <view class="field row">
        <text class="label">库存</text>
        <switch :checked="unlimited" color="#2E6B30" @change="(e) => (unlimited = e.detail.value)" />
        <text class="sub" style="margin-left: 12rpx">{{ unlimited ? '不限量' : '' }}</text>
        <input v-if="!unlimited" class="grow input" type="number" v-model="f.stock" placeholder="还有多少" />
      </view>
      <view class="field">
        <text class="label">说明</text>
        <textarea class="desc" v-model="f.description" maxlength="1000" placeholder="用法、产地、注意事项…" auto-height />
      </view>
    </view>

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow" :disabled="saving || uploading" @tap="save">{{ id ? '保存修改' : '发布' }}</button>
    </view>

    <!-- 认出了商品库里的这一款：请老板确认 -->
    <view v-if="confirmSku" class="mask">
      <view class="sheet" @tap.stop>
        <view class="ocr-title">是不是这个？</view>
        <view class="row confirm-sku">
          <image v-if="confirmSku.image" class="sku-img" :src="confirmSku.image" mode="aspectFill" />
          <view class="grow">
            <view class="sku-name">{{ confirmSku.displayName }}</view>
            <view v-if="confirmSku.regNo" class="muted">登记证号 {{ confirmSku.regNo }}</view>
            <view v-if="confirmSku.barcode" class="muted">条码 {{ confirmSku.barcode }}</view>
          </view>
        </view>
        <view class="row">
          <button class="btn big ghost grow" @tap="confirmNo">不是</button>
          <button class="btn big grow" style="margin-left: 24rpx" @tap="confirmYes">是这个</button>
        </view>
      </view>
    </view>

    <!-- 商品库选择 -->
    <view v-if="pickerOpen" class="mask" @tap="pickerOpen = false">
      <view class="sheet" @tap.stop>
        <view class="row">
          <input class="grow input sheet-input" v-model="stdKeyword" confirm-type="search" placeholder="搜：复合肥、尿素…" @confirm="loadStd" />
          <text class="go" @tap="loadStd">搜索</text>
        </view>
        <scroll-view scroll-y class="std-list">
          <view v-for="s in stds" :key="s.id" class="std row" @tap="pickStd(s)">
            <text class="grow">{{ stdLabel(s) }}</text>
            <text class="sub">选</text>
          </view>
          <view v-if="!stds.length" class="empty">没找到，可以直接填名称</view>
        </scroll-view>
      </view>
    </view>

    <!-- 拍包装识别出的候选 -->
    <view v-if="ocrCands.length" class="mask" @tap="ocrCands = []">
      <view class="sheet" @tap.stop>
        <view class="ocr-title">认出来像是这些，点一个就关联上</view>
        <scroll-view scroll-y class="std-list">
          <view v-for="s in ocrCands" :key="s.id" class="std row" @tap="pickStd(s, true); ocrCands = []">
            <text class="grow">{{ stdLabel(s) }}</text>
            <text class="sub">选</text>
          </view>
          <view class="std row" @tap="ocrCands = []">
            <text class="grow muted">都不是，就用认出来的名称</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { categories, standardProducts, createProduct, updateProduct, myProducts, skuLookup, productDetail } from '../../api'
import { uploadImage } from '../../api/request'
import { pickImages } from '../../utils/format'
import { photoRecognize, recognizePath, stdLabel } from '../../utils/ocr'

const units = ['袋', '瓶', '桶', '包', '斤', '公斤', '件', '台']
const id = ref(0)
const cats = ref([])
const uploading = ref(false)
const saving = ref(false)
const unlimited = ref(true)
const stdName = ref('')
const pickerOpen = ref(false)
const stdKeyword = ref('')
const stds = ref([])
const ocrCands = ref([])
const confirmSku = ref(null) // 认出的这一款，等老板点“是这个 / 不是”
const moreOpen = ref(false)
let laterCands = [] // 老板说“不是”之后再给他看的候选

/** 点“拍包装识别”：另拍一张包装照去认字 */
async function ocr() {
  const r = await photoRecognize()
  if (r) applyOcr(r, true)
}

/**
 * 把识别结果填进表单。overwriteName = true 时名称用认出的覆盖（老板主动点了识别），
 * 否则只在名称还空着时填。
 */
function applyOcr(r, overwriteName) {
  for (const k of ['brand', 'spec', 'regNo', 'barcode', 'manufacturer']) {
    if (r[k]) f[k] = r[k]
  }
  if (r.brand || r.spec) moreOpen.value = true
  const name = (r.match && r.match.name) || r.keyword
  if (name && (overwriteName || !f.name)) f.name = name
  laterCands = r.candidates || []
  if (r.match) {
    confirmSku.value = r.match
  } else if (laterCands.length) {
    ocrCands.value = laterCands
    laterCands = []
  } else if (name) {
    uni.showToast({ title: '名称已填好，可以再改', icon: 'none' })
  }
}

function confirmYes() {
  const s = confirmSku.value
  confirmSku.value = null
  laterCands = []
  pickStd(s, true)
}

// 老板明确说了“不是这一款”：编辑时要让后端去掉原来挂的款
const notThisSku = ref(false)

function confirmNo() {
  confirmSku.value = null
  notThisSku.value = true
  // 不是这一款：条码 / 登记证号也不要了，免得后端又挂到这一款上
  f.standardProductId = null
  f.barcode = ''
  f.regNo = ''
  stdName.value = ''
  if (laterCands.length) ocrCands.value = laterCands
  laterCands = []
}

function unlink() {
  notThisSku.value = true
  f.standardProductId = null
  f.barcode = ''
  f.regNo = ''
  stdName.value = ''
}

/** 扫包装上的条码：商品库里有这一款就请老板确认，没有就记下条码（后端会自动建档） */
function scanBarcode() {
  uni.scanCode({
    scanType: ['barCode'],
    success: async (r) => {
      const code = String(r.result || '').trim()
      if (!code) return
      f.barcode = code
      let sku = null
      try {
        sku = await skuLookup({ barcode: code })
      } catch (e) {
        sku = null
      }
      if (sku) {
        laterCands = []
        confirmSku.value = sku
      } else {
        uni.showToast({ title: '已记下条码', icon: 'none' })
      }
    },
  })
}

const f = reactive({
  images: [],
  price: '',
  unit: '袋',
  name: '',
  categoryId: null,
  standardProductId: null,
  brand: '',
  spec: '',
  barcode: '',
  regNo: '',
  manufacturer: '',
  stock: '',
  description: '',
  localDelivery: true,
  expressDelivery: false,
  expressFee: '0',
})

onLoad(async (q) => {
  categories().then((c) => (cats.value = c)).catch(() => {})
  if (q && q.id) {
    id.value = Number(q.id)
    uni.setNavigationBarTitle({ title: '修改商品' })
    const p = (await myProducts()).find((x) => x.id === id.value)
    if (p) {
      Object.assign(f, {
        images: p.images,
        price: String(p.price),
        unit: p.unit,
        name: p.name,
        categoryId: p.categoryId,
        standardProductId: p.standardProductId,
        brand: p.brand || '',
        spec: p.spec || '',
        stock: p.stock == null ? '' : String(p.stock),
        description: p.description || '',
        localDelivery: p.localDelivery,
        expressDelivery: p.expressDelivery,
        expressFee: String(p.expressFee || 0),
      })
      unlimited.value = p.stock == null
      if (p.brand || p.spec) moreOpen.value = true
      if (p.standardProductId) stdName.value = '已认出是哪一款'
      if (p.standardProductId) {
        productDetail(p.id)
          .then((d) => {
            if (d && d.sku) {
              stdName.value = stdLabel(d.sku)
              f.regNo = d.sku.regNo || ''
              f.barcode = d.sku.barcode || ''
            }
          })
          .catch(() => {})
      }
    }
  }
})

async function addImages() {
  let paths
  try {
    paths = await pickImages(5 - f.images.length)
  } catch (e) {
    return
  }
  const first = !f.images.length
  uploading.value = true
  try {
    for (const p of paths) {
      f.images.push(await uploadImage(p))
    }
  } catch (e) {
    // 已提示
  } finally {
    uploading.value = false
  }
  // 发新商品拍的第一张照片，顺手认一下包装上的字；认不出也不打扰
  if (first && !id.value && !f.standardProductId && f.images.length) {
    const r = await recognizePath(paths[0], true)
    if (r) applyOcr(r, false)
  }
}

function removeImg(i) {
  f.images.splice(i, 1)
}

function previewImg(i) {
  uni.previewImage({ urls: f.images, current: i })
}

function openPicker() {
  pickerOpen.value = true
  if (!stds.value.length) loadStd()
}

async function loadStd() {
  stds.value = await standardProducts(stdKeyword.value.trim(), null)
}

/** 选定商品库里的一款：名称、品牌、规格、分类从这一款带出 */
function pickStd(s, overwriteName) {
  notThisSku.value = false
  f.standardProductId = s.id
  if (s.categoryId) f.categoryId = s.categoryId
  stdName.value = stdLabel(s)
  if (s.name && (overwriteName || !f.name)) f.name = s.name
  if (s.brand) f.brand = s.brand
  if (s.spec) f.spec = s.spec
  if (s.regNo) f.regNo = s.regNo
  if (s.barcode) f.barcode = s.barcode
  if (s.manufacturer) f.manufacturer = s.manufacturer
  pickerOpen.value = false
}

async function save() {
  if (!f.images.length) return uni.showToast({ title: '请先拍一张照片', icon: 'none' })
  const price = Number(f.price)
  if (!(price > 0)) return uni.showToast({ title: '请填写价格', icon: 'none' })
  if (!f.localDelivery && !f.expressDelivery) return uni.showToast({ title: '本地配送和快递至少选一个', icon: 'none' })
  if (!unlimited.value && !(Number(f.stock) >= 0 && f.stock !== '')) return uni.showToast({ title: '请填写库存', icon: 'none' })

  const body = {
    images: f.images,
    price,
    unit: f.unit || '件',
    name: f.name || null,
    categoryId: f.categoryId,
    standardProductId: f.standardProductId,
    brand: f.brand.trim() || null,
    spec: f.spec.trim() || null,
    barcode: f.barcode || null,
    regNo: f.regNo || null,
    manufacturer: f.manufacturer || null,
    notThisSku: notThisSku.value && !f.standardProductId,
    stock: unlimited.value ? null : Number(f.stock),
    description: f.description || null,
    localDelivery: f.localDelivery,
    expressDelivery: f.expressDelivery,
    expressFee: f.expressDelivery ? Number(f.expressFee || 0) : 0,
  }
  saving.value = true
  try {
    if (id.value) {
      await updateProduct(id.value, body)
      uni.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 600)
    } else {
      const p = await createProduct(body)
      afterCreate(p)
    }
  } finally {
    saving.value = false
  }
}

function afterCreate(p) {
  uni.showModal({
    title: '发布成功',
    content: '附近的农户现在就能看到了。要不要接着发下一个？',
    confirmText: '再发一个',
    cancelText: '去看看',
    success: (r) => {
      if (r.confirm) {
        Object.assign(f, {
          images: [], price: '', name: '', standardProductId: null, stock: '', description: '',
          brand: '', spec: '', barcode: '', regNo: '', manufacturer: '',
        })
        moreOpen.value = false
        stdName.value = ''
        unlimited.value = true
        uni.pageScrollTo({ scrollTop: 0, duration: 0 })
      } else {
        uni.redirectTo({ url: '/pages/product/product?id=' + p.id })
      }
    },
  })
}
</script>

<style lang="scss">
.sku-ok {
  margin: 16rpx 24rpx 0;
  padding: 20rpx 24rpx;
  border-radius: 16rpx;
  background: $lm-green-light;
  gap: 16rpx;
}
.sku-ok-mark {
  width: 48rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  border-radius: 50%;
  background: $lm-green;
  color: #fff;
  font-weight: 700;
}
.sku-ok-title {
  font-size: 30rpx;
  font-weight: 600;
  color: $lm-green;
}
.sku-ok-sub {
  font-size: 24rpx;
  color: $lm-ink-2;
}
.sku-ok-no {
  font-size: 26rpx;
  color: $lm-ink-3;
  padding: 8rpx 12rpx;
}

.page { font-size: 32rpx; }
.step-title { font-size: 36rpx; font-weight: 800; margin: 36rpx 28rpx 12rpx; display: flex; align-items: center; }
.step-title.light { font-size: 28rpx; font-weight: 400; color: $lm-ink-3; }
.num {
  width: 48rpx; height: 48rpx; border-radius: 50%;
  background: $lm-green; color: #fff; font-size: 28rpx;
  display: inline-flex; align-items: center; justify-content: center; margin-right: 16rpx;
}
.photos { display: flex; flex-wrap: wrap; padding: 0 16rpx; }
.photo { width: 220rpx; height: 220rpx; margin: 8rpx; border-radius: 20rpx; position: relative; overflow: hidden; }
.pimg { width: 100%; height: 100%; }
.photo.add {
  background: #fff; border: 4rpx dashed $lm-green;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.photos .photo.add:first-child { width: 694rpx; height: 300rpx; }
.cam { font-size: 80rpx; color: $lm-green; line-height: 1; }
.cam-text { color: $lm-green; font-weight: 700; margin-top: 8rpx; }
.del {
  position: absolute; right: 0; top: 0; width: 56rpx; height: 56rpx;
  background: rgba(0, 0, 0, 0.55); color: #fff; text-align: center; line-height: 52rpx; font-size: 40rpx;
  border-bottom-left-radius: 16rpx;
}
.tip { padding: 8rpx 28rpx; }
.price-row { padding: 20rpx 28rpx; }
.yuan { font-size: 56rpx; color: $lm-wheat; font-weight: 800; margin-right: 8rpx; }
.price-input { font-size: 64rpx; font-weight: 800; height: 100rpx; color: $lm-wheat; }
.unit-input { width: 100rpx; font-size: 36rpx; text-align: center; border-bottom: 2rpx solid $lm-line; margin-left: 8rpx; }
.units { flex-wrap: wrap; padding: 0 20rpx; }
.unit-chip {
  display: inline-block; padding: 12rpx 28rpx; margin: 8rpx; border-radius: 999rpx;
  background: #fff; border: 2rpx solid $lm-line; font-size: 30rpx;
}
.unit-chip.on { border-color: $lm-green; color: $lm-green; background: $lm-green-light; font-weight: 700; }
.mode { border: 4rpx solid transparent; }
.mode.on { border-color: $lm-green; background: $lm-green-light; }
.check {
  width: 56rpx; height: 56rpx; border-radius: 12rpx; border: 4rpx solid $lm-green; margin-right: 24rpx;
  color: #fff; font-weight: 800; text-align: center; line-height: 50rpx; font-size: 36rpx; flex-shrink: 0;
}
.mode.on .check { background: $lm-green; }
.mode-name { font-size: 36rpx; font-weight: 800; }
.fee-row { margin-top: 20rpx; padding-left: 80rpx; }
.fee-input { width: 140rpx; font-size: 36rpx; font-weight: 700; border-bottom: 2rpx solid $lm-green; margin: 0 16rpx; }
.field { padding: 22rpx 0; border-bottom: 2rpx solid $lm-line; }
.field:last-child { border-bottom: none; }
.label { width: 180rpx; color: $lm-ink-2; flex-shrink: 0; }
.label.wide { width: auto; margin-right: 20rpx; }
.input { height: 64rpx; font-size: 32rpx; }
.cats { margin-top: 8rpx; }
.desc { width: 100%; min-height: 120rpx; margin-top: 12rpx; font-size: 30rpx; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(0, 0, 0, 0.45); z-index: 50; }
.sheet {
  position: absolute; left: 0; right: 0; bottom: 0; background: #fff;
  border-top-left-radius: 28rpx; border-top-right-radius: 28rpx; padding: 28rpx;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}
.sheet-input { background: $lm-bg; border-radius: 999rpx; padding: 0 28rpx; height: 76rpx; }
.go { color: $lm-green; font-weight: 700; margin-left: 20rpx; }
.std-list { height: 700rpx; margin-top: 16rpx; }
.ocr-title { font-size: 34rpx; font-weight: 800; }
.std { padding: 28rpx 8rpx; border-bottom: 2rpx solid $lm-line; font-size: 32rpx; }
.linked { color: $lm-green; font-weight: 600; }
.unlink { color: $lm-ink-3; font-size: 28rpx; margin-left: 16rpx; padding: 8rpx 0 8rpx 16rpx; }
.scan-btn { margin-left: 20rpx; flex-shrink: 0; }
.sub-field { padding-top: 20rpx; }
.confirm-sku { margin: 24rpx 0 36rpx; padding: 24rpx; background: $lm-bg; border-radius: 16rpx; }
.sku-img { width: 120rpx; height: 120rpx; border-radius: 12rpx; margin-right: 20rpx; flex-shrink: 0; background: #EEE9DD; }
.sku-name { font-size: 36rpx; font-weight: 800; }
</style>
