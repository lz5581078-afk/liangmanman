<script setup>
import { Search, Refresh } from '@element-plus/icons-vue'

defineEmits(['search', 'reset'])
</script>

<template>
  <el-form class="filter-bar" inline @submit.prevent="$emit('search')">
    <slot />
    <el-form-item>
      <el-button type="primary" :icon="Search" native-type="submit">查询</el-button>
      <el-button :icon="Refresh" @click="$emit('reset')">重置</el-button>
      <slot name="extra" />
    </el-form-item>
  </el-form>
</template>

<style scoped>
.filter-bar {
  margin-bottom: 4px;
}
.filter-bar :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 12px;
}
.filter-bar :deep(.el-input),
.filter-bar :deep(.el-select) {
  width: 180px;
}
</style>
