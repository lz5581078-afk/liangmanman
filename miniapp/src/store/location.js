import { reactive } from 'vue'
import { DEFAULT_LOCATION } from '../config'

const KEY = 'lm_location'

/** 买家当前位置（gcj02，与微信地图一致）。手动选过的位置会记住。 */
export const location = reactive(
  uni.getStorageSync(KEY) || { ...DEFAULT_LOCATION, auto: false, ready: false }
)

function save() {
  uni.setStorageSync(KEY, { ...location })
}

/** 自动定位；用户拒绝授权时保留之前的位置 */
export function refreshLocation() {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      success: (r) => {
        location.lat = r.latitude
        location.lng = r.longitude
        location.name = '当前位置'
        location.auto = true
        location.ready = true
        save()
        resolve(location)
      },
      fail: (e) => {
        location.ready = true
        reject(e)
      },
    })
  })
}

/** 在地图上手动选位置 */
export function chooseOnMap() {
  return new Promise((resolve, reject) => {
    uni.chooseLocation({
      latitude: location.lat,
      longitude: location.lng,
      success: (r) => {
        location.lat = r.latitude
        location.lng = r.longitude
        location.name = r.name || r.address || '已选位置'
        location.auto = false
        location.ready = true
        save()
        resolve(location)
      },
      fail: reject,
    })
  })
}

export function currentLoc() {
  return { lat: location.lat, lng: location.lng }
}
