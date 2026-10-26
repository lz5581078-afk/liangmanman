import { ref } from 'vue'

/**
 * 分页加载：fetcher(page) 返回 { records, total, page, size }
 * 用法：const { list, loading, finished, reload, more } = usePaged((p) => api(p))
 */
export function usePaged(fetcher) {
  const list = ref([])
  const loading = ref(false)
  const finished = ref(false)
  const loaded = ref(false)
  const total = ref(0)
  let page = 0
  let seq = 0

  async function load(reset) {
    // 重新加载（换了排序 / 筛选）时不等上一次，旧的结果回来直接丢掉
    if (loading.value && !reset) return
    if (!reset && finished.value) return
    const my = ++seq
    loading.value = true
    try {
      const next = reset ? 1 : page + 1
      const r = (await fetcher(next)) || {}
      if (my !== seq) return
      const recs = r.records || []
      list.value = reset ? recs : list.value.concat(recs)
      page = next
      total.value = r.total || 0
      const size = r.size || 20
      finished.value = recs.length < size || list.value.length >= total.value
    } finally {
      if (my === seq) {
        loading.value = false
        loaded.value = true
      }
    }
  }

  return {
    list,
    loading,
    finished,
    loaded,
    total,
    reload: () => load(true),
    more: () => load(false),
  }
}
