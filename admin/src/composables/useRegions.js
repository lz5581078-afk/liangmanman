import { ref } from 'vue'
import { listRegions } from '@/api'
import { toList } from '@/utils/format'

/** 开通区域列表（轮播图选择所属区域时使用） */
export function useRegions() {
  const regions = ref([])
  const loading = ref(false)
  async function load() {
    loading.value = true
    try {
      regions.value = toList(await listRegions())
    } catch {
      regions.value = []
    } finally {
      loading.value = false
    }
  }
  return { regions, loading, load }
}
