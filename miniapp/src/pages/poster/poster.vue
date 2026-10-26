<template>
  <view class="page">
    <view class="preview">
      <image v-if="imgPath" class="poster-img" :src="imgPath" mode="widthFix" @tap="previewPoster" />
      <view v-else class="placeholder">{{ error || '海报生成中…' }}</view>
    </view>

    <view class="tips muted">保存到手机后，可以发到微信群、朋友圈，也可以拿去打印店打印，贴在店门口</view>

    <!-- 画布放在屏幕外，只用来生成图片 -->
    <!-- #ifdef MP-WEIXIN -->
    <canvas type="2d" id="posterCanvas" class="hidden-canvas" :style="{ width: W + 'px', height: H + 'px' }" />
    <!-- #endif -->

    <view class="footer-space" />
    <view class="footer-bar">
      <button class="btn big grow" :disabled="!imgPath || saving" @tap="save">保存到相册</button>
    </view>
  </view>
</template>

<script setup>
import { ref, getCurrentInstance } from 'vue'
import { onLoad, onReady } from '@dcloudio/uni-app'
import { storeDetail, productDetail, groupBuyDetail, qrcodeUrl } from '../../api'
import { money, remain } from '../../utils/format'

// 海报尺寸（逻辑像素），导出时按 SCALE 倍放大，打印更清楚
const W = 375
const H = 600
const SCALE = 3

const instance = getCurrentInstance()
const type = ref('store')
const id = ref(0)
const imgPath = ref('')
const error = ref('')
const saving = ref(false)
let ready = false
let loadedQuery = false

onLoad((q) => {
  type.value = ['store', 'product', 'group'].includes(q.type) ? q.type : 'store'
  id.value = Number(q.id)
  uni.setNavigationBarTitle({ title: { store: '店铺海报', product: '商品海报', group: '凑单海报' }[type.value] })
  loadedQuery = true
  if (ready) build()
})

onReady(() => {
  ready = true
  if (loadedQuery) build()
})

/** 取海报要画的内容 */
async function fetchContent() {
  if (type.value === 'store') {
    const d = await storeDetail(id.value)
    const s = d.store
    return {
      image: s.coverImage || '',
      title: s.name,
      lines: [s.addressText || '', s.phone ? '电话 ' + s.phone : ''].filter(Boolean),
      priceText: '',
      badge: [s.deliveryEnabled ? '送货上门' : '', s.pickupEnabled ? '到店自提' : '', s.codEnabled ? '货到付款' : ''].filter(Boolean).join(' · '),
      cta: '微信扫码 进店下单',
    }
  }
  if (type.value === 'product') {
    const d = await productDetail(id.value)
    const p = d.product
    return {
      image: (p.images && p.images[0]) || '',
      title: p.name,
      lines: [d.store.name],
      priceText: '¥' + money(p.price) + ' /' + p.unit,
      badge: '',
      cta: '微信扫码 进店下单',
    }
  }
  const g = await groupBuyDetail(id.value)
  const left = Math.max(0, g.targetQty - g.currentQty)
  const r = remain(g.endAt)
  return {
    image: g.productImage || '',
    title: g.productName,
    lines: [g.storeName],
    priceText: '凑单价 ¥' + money(g.groupPrice) + ' /' + g.unit,
    origText: '原价 ¥' + money(g.originalPrice),
    progress: { cur: g.currentQty, target: g.targetQty, text: `已凑 ${g.currentQty}/${g.targetQty} ${g.unit}` + (left > 0 ? `，还差 ${left} ${g.unit}` : '，已凑够') + (r ? ` · 还剩 ${r}` : '') },
    badge: '',
    cta: '扫码一起凑单',
  }
}

/** 把远程图片变成本地可画的路径 */
function download(url) {
  return new Promise((resolve) => {
    if (!url) return resolve('')
    // #ifdef MP-WEIXIN
    uni.downloadFile({
      url,
      success: (r) => resolve(r.statusCode === 200 ? r.tempFilePath : ''),
      fail: () => resolve(''),
    })
    // #endif
    // #ifndef MP-WEIXIN
    resolve(url)
    // #endif
  })
}

function getCanvas() {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni
      .createSelectorQuery()
      .in(instance.proxy)
      .select('#posterCanvas')
      .fields({ node: true, size: true })
      .exec((res) => {
        const node = res && res[0] && res[0].node
        if (node) resolve(node)
        else reject(new Error('no canvas'))
      })
    // #endif
    // #ifndef MP-WEIXIN
    if (typeof document !== 'undefined') resolve(document.createElement('canvas'))
    else reject(new Error('no canvas'))
    // #endif
  })
}

function loadImage(canvas, src) {
  return new Promise((resolve) => {
    if (!src) return resolve(null)
    let img
    // #ifdef MP-WEIXIN
    img = canvas.createImage()
    // #endif
    // #ifndef MP-WEIXIN
    img = new Image()
    img.crossOrigin = 'anonymous'
    // #endif
    img.onload = () => resolve(img)
    img.onerror = () => resolve(null)
    img.src = src
  })
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

/** 按宽度折行，最多 maxLines 行 */
function wrap(ctx, text, maxW, maxLines) {
  const out = []
  let line = ''
  for (const ch of String(text || '')) {
    if (ctx.measureText(line + ch).width > maxW && line) {
      out.push(line)
      line = ch
      if (out.length === maxLines) break
    } else {
      line += ch
    }
  }
  if (out.length < maxLines && line) out.push(line)
  else if (out.length === maxLines && line) out[maxLines - 1] = out[maxLines - 1].slice(0, -1) + '…'
  return out
}

/** 等比裁剪铺满 */
function drawCover(ctx, img, x, y, w, h) {
  const iw = img.width
  const ih = img.height
  const s = Math.max(w / iw, h / ih)
  const sw = w / s
  const sh = h / s
  ctx.drawImage(img, (iw - sw) / 2, (ih - sh) / 2, sw, sh, x, y, w, h)
}

const C = { green: '#2E6B30', greenLight: '#E6F0E3', wheat: '#D98E04', wheatLight: '#FDF3DC', ink: '#1F1D19', ink2: '#5C574D', ink3: '#9A9488', bg: '#F7F5EF' }
const FONT = '"PingFang SC", "Microsoft YaHei", sans-serif'

async function draw(canvas, content, heroSrc, qrSrc) {
  canvas.width = W * SCALE
  canvas.height = H * SCALE
  const ctx = canvas.getContext('2d')
  ctx.scale(SCALE, SCALE)

  // 底色
  ctx.fillStyle = C.bg
  ctx.fillRect(0, 0, W, H)

  // 顶部图片
  const heroH = 230
  const [hero, qr] = await Promise.all([loadImage(canvas, heroSrc), loadImage(canvas, qrSrc)])
  if (hero) {
    drawCover(ctx, hero, 0, 0, W, heroH)
  } else {
    ctx.fillStyle = C.green
    ctx.fillRect(0, 0, W, heroH)
    ctx.fillStyle = 'rgba(255,255,255,0.9)'
    ctx.font = `bold 40px ${FONT}`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText('粮满满', W / 2, heroH / 2)
  }
  ctx.textAlign = 'left'
  ctx.textBaseline = 'alphabetic'

  // 凑单角标
  if (type.value === 'group') {
    ctx.fillStyle = C.wheat
    roundRect(ctx, 16, 16, 76, 30, 15)
    ctx.fill()
    ctx.fillStyle = '#fff'
    ctx.font = `bold 16px ${FONT}`
    ctx.fillText('一起凑单', 22, 37)
  }

  // 白色信息卡
  const cardY = heroH - 20
  ctx.fillStyle = '#fff'
  roundRect(ctx, 12, cardY, W - 24, H - cardY - 48, 14)
  ctx.fill()

  let y = cardY + 40
  const x = 30
  const maxW = W - 60

  // 大标题
  ctx.fillStyle = C.ink
  ctx.font = `bold 24px ${FONT}`
  wrap(ctx, content.title, maxW, 2).forEach((l) => {
    ctx.fillText(l, x, y)
    y += 32
  })

  // 价格
  if (content.priceText) {
    y += 6
    ctx.fillStyle = C.wheat
    ctx.font = `bold 26px ${FONT}`
    ctx.fillText(content.priceText, x, y)
    if (content.origText) {
      const pw = ctx.measureText(content.priceText).width
      ctx.fillStyle = C.ink3
      ctx.font = `14px ${FONT}`
      const ox = x + pw + 12
      ctx.fillText(content.origText, ox, y)
      const ow = ctx.measureText(content.origText).width
      ctx.strokeStyle = C.ink3
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(ox, y - 5)
      ctx.lineTo(ox + ow, y - 5)
      ctx.stroke()
    }
    y += 26
  }

  // 凑单进度
  if (content.progress) {
    const p = content.progress
    const pct = Math.min(1, p.cur / Math.max(p.target, 1))
    ctx.fillStyle = C.greenLight
    roundRect(ctx, x, y - 6, maxW, 12, 6)
    ctx.fill()
    if (pct > 0) {
      ctx.fillStyle = C.green
      roundRect(ctx, x, y - 6, Math.max(12, maxW * pct), 12, 6)
      ctx.fill()
    }
    y += 26
    ctx.fillStyle = C.ink2
    ctx.font = `15px ${FONT}`
    wrap(ctx, p.text, maxW, 2).forEach((l) => {
      ctx.fillText(l, x, y)
      y += 22
    })
  }

  // 其他行
  ctx.fillStyle = C.ink2
  ctx.font = `15px ${FONT}`
  ;(content.lines || []).forEach((t) => {
    wrap(ctx, t, maxW, 1).forEach((l) => {
      ctx.fillText(l, x, y)
      y += 22
    })
  })
  if (content.badge) {
    ctx.fillStyle = C.green
    ctx.font = `bold 15px ${FONT}`
    ctx.fillText(content.badge, x, y)
    y += 22
  }

  // 底部：小程序码 + 引导
  const qrSize = 116
  const qrY = H - 48 - qrSize - 22
  ctx.strokeStyle = '#E8E3D8'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(x, qrY - 14)
  ctx.lineTo(W - x, qrY - 14)
  ctx.stroke()
  if (qr) {
    ctx.drawImage(qr, W - x - qrSize, qrY, qrSize, qrSize)
  } else {
    ctx.fillStyle = C.bg
    ctx.fillRect(W - x - qrSize, qrY, qrSize, qrSize)
    ctx.fillStyle = C.ink3
    ctx.font = `13px ${FONT}`
    ctx.fillText('小程序码', W - x - qrSize + 30, qrY + qrSize / 2 + 4)
  }
  ctx.fillStyle = C.green
  ctx.font = `bold 22px ${FONT}`
  const ctaLines = content.cta.split(' ')
  ctaLines.forEach((l, i) => ctx.fillText(l, x, qrY + 40 + i * 32))
  ctx.fillStyle = C.ink3
  ctx.font = `13px ${FONT}`
  ctx.fillText('长按或用微信扫一扫', x, qrY + qrSize - 6)

  // 页脚
  ctx.fillStyle = C.green
  ctx.fillRect(0, H - 40, W, 40)
  ctx.fillStyle = '#fff'
  ctx.font = `bold 16px ${FONT}`
  ctx.textAlign = 'center'
  ctx.fillText('粮满满 · 附近的农资店', W / 2, H - 14)
  ctx.textAlign = 'left'
}

function exportImage(canvas) {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.canvasToTempFilePath(
      {
        canvas,
        x: 0,
        y: 0,
        width: W * SCALE,
        height: H * SCALE,
        destWidth: W * SCALE,
        destHeight: H * SCALE,
        fileType: 'png',
        success: (r) => resolve(r.tempFilePath),
        fail: reject,
      },
      instance.proxy
    )
    // #endif
    // #ifndef MP-WEIXIN
    try {
      resolve(canvas.toDataURL('image/png'))
    } catch (e) {
      reject(e)
    }
    // #endif
  })
}

async function build() {
  error.value = ''
  imgPath.value = ''
  if (!id.value) {
    error.value = '参数不对'
    return
  }
  try {
    const content = await fetchContent()
    const [heroSrc, qrSrc] = await Promise.all([download(content.image), download(qrcodeUrl(type.value, id.value))])
    const canvas = await getCanvas()
    await draw(canvas, content, heroSrc, qrSrc)
    imgPath.value = await exportImage(canvas)
  } catch (e) {
    error.value = (e && e.msg) || '海报生成失败，返回再试一次'
  }
}

function previewPoster() {
  if (imgPath.value) uni.previewImage({ urls: [imgPath.value] })
}

function save() {
  // #ifdef H5
  uni.showModal({
    title: '提示',
    content: '网页里不能直接存相册，请长按海报图片保存，或在微信小程序里打开',
    showCancel: false,
  })
  // #endif
  // #ifndef H5
  saving.value = true
  uni.saveImageToPhotosAlbum({
    filePath: imgPath.value,
    success: () => uni.showToast({ title: '已保存到相册', icon: 'success' }),
    fail: (e) => {
      const msg = (e && e.errMsg) || ''
      if (/auth|deny|denied|authorize/i.test(msg)) {
        uni.showModal({
          title: '需要相册权限',
          content: '要把海报存到手机相册，请在设置里打开“保存到相册”',
          confirmText: '去设置',
          success: (r) => {
            if (r.confirm) uni.openSetting({})
          },
        })
      } else if (!/cancel/i.test(msg)) {
        uni.showToast({ title: '保存失败，请重试', icon: 'none' })
      }
    },
    complete: () => {
      saving.value = false
    },
  })
  // #endif
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.preview { padding: 24rpx 48rpx 0; }
.poster-img { width: 100%; border-radius: 16rpx; box-shadow: 0 6rpx 24rpx rgba(0, 0, 0, 0.12); display: block; }
.placeholder {
  height: 900rpx; background: #fff; border-radius: 16rpx; display: flex; align-items: center; justify-content: center;
  color: $lm-ink-3; text-align: center; padding: 0 40rpx;
}
.tips { text-align: center; padding: 24rpx 48rpx; }
.hidden-canvas { position: fixed; left: -9999px; top: 0; }
</style>
