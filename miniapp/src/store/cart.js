import { reactive, computed } from 'vue'

const KEY = 'lm_cart'

/**
 * 购物车：像美团一样每家店分开。
 * carts[storeId] = { storeId, storeName, items: { [productId]: { product, qty } } }
 */
const state = reactive(uni.getStorageSync(KEY) || { carts: {} })

function save() {
  uni.setStorageSync(KEY, { carts: state.carts })
}

function cartOf(storeId, storeName) {
  if (!state.carts[storeId]) {
    state.carts[storeId] = { storeId, storeName: storeName || '', items: {} }
  }
  if (storeName) state.carts[storeId].storeName = storeName
  return state.carts[storeId]
}

export function qtyOf(storeId, productId) {
  const c = state.carts[storeId]
  return (c && c.items[productId] && c.items[productId].qty) || 0
}

export function setQty(storeId, storeName, product, qty) {
  const c = cartOf(storeId, storeName)
  if (qty <= 0) {
    delete c.items[product.id]
  } else {
    // 只存下单需要的字段
    c.items[product.id] = {
      product: {
        id: product.id,
        name: product.name,
        price: product.price,
        unit: product.unit,
        image: (product.images && product.images[0]) || product.image || '',
        localDelivery: product.localDelivery,
        expressDelivery: product.expressDelivery,
      },
      qty,
    }
  }
  if (Object.keys(c.items).length === 0) delete state.carts[storeId]
  save()
}

export function add(storeId, storeName, product, delta = 1) {
  setQty(storeId, storeName, product, qtyOf(storeId, product.id) + delta)
}

export function clearStore(storeId) {
  delete state.carts[storeId]
  save()
}

/** 某家店购物车的汇总（在 computed 里调用即可随购物车变化自动更新） */
export function summary(storeId) {
  const c = state.carts[storeId]
  const lines = c ? Object.values(c.items) : []
  const count = lines.reduce((s, l) => s + l.qty, 0)
  const total = lines.reduce((s, l) => s + Number(l.product.price) * l.qty, 0)
  return { lines, count, total: Math.round(total * 100) / 100, storeName: c ? c.storeName : '' }
}

export function storeCart(storeId) {
  return computed(() => summary(storeId))
}

export function itemsForOrder(storeId) {
  const c = state.carts[storeId]
  return c ? Object.values(c.items).map((l) => ({ productId: l.product.id, quantity: l.qty })) : []
}
