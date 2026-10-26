import { computed, ref } from 'vue'
import { listCategories } from '@/api'
import { toList } from '@/utils/format'

const categories = ref([])
let pending = null

/** 分类列表（全局缓存，分类页修改后调用 reload） */
export function useCategories() {
  function reload() {
    pending = listCategories()
      .then((data) => {
        categories.value = toList(data)
          .slice()
          .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0) || a.id - b.id)
      })
      .catch(() => {})
    return pending
  }
  if (!pending) reload()
  const nameOf = computed(() => Object.fromEntries(categories.value.map((c) => [c.id, c.name])))
  return { categories, nameOf, reload }
}
