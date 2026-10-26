<script setup>
import { computed } from 'vue'

/** 缩略图，点击看大图；src 可以是单张或数组 */
const props = defineProps({
  src: { type: [String, Array], default: '' },
  size: { type: Number, default: 48 },
  /** 不传时与 size 相同（正方形） */
  height: { type: Number, default: 0 }
})

const box = computed(() => ({ width: `${props.size}px`, height: `${props.height || props.size}px` }))

const list = computed(() => (Array.isArray(props.src) ? props.src : props.src ? [props.src] : []).filter(Boolean))
</script>

<template>
  <el-image
    v-if="list.length"
    :src="list[0]"
    :preview-src-list="list"
    :style="box"
    fit="cover"
    class="thumb"
    preview-teleported
    hide-on-click-modal
  >
    <template #error><div class="thumb-empty">加载失败</div></template>
  </el-image>
  <div v-else class="thumb-empty" :style="box">无图</div>
</template>

<style scoped>
.thumb {
  border-radius: 6px;
  display: block;
  cursor: zoom-in;
  background: var(--el-fill-color-light);
}
.thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  border-radius: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
}
</style>
