import { ocrProduct } from '../api'

/**
 * 拍包装 → 上传识别。返回 { keyword, brand, spec, regNo, barcode, manufacturer, match, candidates }；
 * match 是商品库里精确认出的这一款（SkuView），candidates 是可能的款。
 * 用户取消返回 null；识别不出提示后返回 null。
 */
export async function photoRecognize() {
  let path
  try {
    path = await new Promise((resolve, reject) => {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['camera', 'album'],
        success: (r) => resolve(r.tempFilePaths[0]),
        fail: reject,
      })
    })
  } catch (e) {
    return null
  }
  return recognizePath(path)
}

/** 已有一张照片（比如商家刚拍的商品照），直接拿去识别；quiet = true 认不出时不提示 */
export async function recognizePath(path, quiet = false) {
  uni.showLoading({ title: '正在认字…', mask: true })
  let r
  try {
    r = await ocrProduct(path, { silent: true })
  } catch (e) {
    uni.hideLoading()
    if (!quiet) uni.showToast({ title: e.msg || '识别失败，请重试', icon: 'none' })
    return null
  }
  uni.hideLoading()
  const candidates = (r && r.candidates) || []
  if (!r || (!r.keyword && !r.match && !candidates.length && !r.barcode && !r.regNo)) {
    if (!quiet) uni.showToast({ title: '没认出来，换个角度拍清楚包装上的字', icon: 'none', duration: 3000 })
    return null
  }
  return { ...r, candidates }
}

export function stdLabel(s) {
  if (!s) return ''
  return s.displayName || [s.brand, s.name, s.spec].filter(Boolean).join(' ')
}
