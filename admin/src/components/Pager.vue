<script setup>
const page = defineModel('page', { type: Number, default: 1 })
const size = defineModel('size', { type: Number, default: 20 })
defineProps({ total: { type: Number, default: 0 } })
const emit = defineEmits(['change'])

function onPage() {
  emit('change')
}
function onSize() {
  page.value = 1
  emit('change')
}
</script>

<template>
  <div class="pager">
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      background
      @current-change="onPage"
      @size-change="onSize"
    />
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
