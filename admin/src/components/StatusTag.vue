<script setup>
import { computed } from 'vue'

/** dict 来自 utils/dict.js；label 是后端给的 statusLabel（优先显示） */
const props = defineProps({
  dict: { type: Object, required: true },
  value: { type: String, default: '' },
  label: { type: String, default: '' }
})

const text = computed(() => props.label || props.dict.label[props.value] || props.value || '-')
const type = computed(() => props.dict.type[props.value] || 'info')
</script>

<template>
  <el-tag :type="type" disable-transitions>{{ text }}</el-tag>
</template>
