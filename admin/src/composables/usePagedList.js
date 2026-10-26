import { reactive, ref } from 'vue'
import { cleanParams, toPage } from '@/utils/format'

/**
 * 分页列表通用逻辑。
 * fetcher(params) 返回 { records, total }；filters 是筛选条件的初始值。
 */
export function usePagedList(fetcher, filters = {}, { size = 20 } = {}) {
  const initial = { ...filters }
  const query = reactive({ ...filters })
  const page = ref(1)
  const pageSize = ref(size)
  const records = ref([])
  const total = ref(0)
  const loading = ref(false)
  let seq = 0

  async function load() {
    const current = ++seq
    loading.value = true
    try {
      const data = await fetcher(cleanParams({ ...query, page: page.value, size: pageSize.value }))
      if (current !== seq) return
      const p = toPage(data)
      records.value = p.records
      total.value = p.total
    } catch {
      if (current === seq) records.value = []
    } finally {
      if (current === seq) loading.value = false
    }
  }

  function search() {
    page.value = 1
    load()
  }

  function reset() {
    Object.assign(query, initial)
    search()
  }

  return { query, page, pageSize, records, total, loading, load, search, reset }
}
