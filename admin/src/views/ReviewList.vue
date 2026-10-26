<script setup>
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listReviews, deleteReview } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { time, text } from '@/utils/format'
import { confirmAction } from '@/utils/prompt'
import FilterBar from '@/components/FilterBar.vue'
import Pager from '@/components/Pager.vue'
import Thumb from '@/components/Thumb.vue'

const route = useRoute()
const { query, records, total, loading, page, pageSize, load, search, reset } = usePagedList(listReviews, { storeId: '' })

async function remove(row) {
  try {
    await confirmAction(`确定删除这条评价？删除后店铺页不再展示，且不可恢复。\n\n“${(row.content || '（无文字）').slice(0, 60)}”`, '删除违规评价')
    await deleteReview(row.id)
  } catch {
    return
  }
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  if (typeof route.query.storeId === 'string') query.storeId = route.query.storeId
  load()
})
</script>

<template>
  <div class="page-card">
    <FilterBar @search="search" @reset="reset">
      <el-form-item label="店铺 ID">
        <el-input v-model="query.storeId" placeholder="店铺 ID" clearable style="width: 120px" />
      </el-form-item>
    </FilterBar>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="店铺" min-width="130">
        <template #default="{ row }">
          <div>{{ row.storeName || (row.storeId ? `店铺 #${row.storeId}` : '-') }}</div>
          <div class="muted">订单 #{{ text(row.orderId) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="评分" width="150">
        <template #default="{ row }"><el-rate :model-value="row.score || 0" disabled size="small" /></template>
      </el-table-column>
      <el-table-column label="内容" min-width="280">
        <template #default="{ row }">
          <div class="content">{{ row.content || '（未填写文字）' }}</div>
          <div v-if="row.images && row.images.length" class="img-row mt">
            <Thumb v-for="(img, i) in row.images" :key="i" :src="row.images.slice(i).concat(row.images.slice(0, i))" :size="44" />
          </div>
          <div v-if="row.reply" class="reply">商家回复：{{ row.reply }} <span class="muted">{{ time(row.replyAt) }}</span></div>
        </template>
      </el-table-column>
      <el-table-column label="买家" width="100">
        <template #default="{ row }">{{ text(row.nickname) }}</template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
  </div>
</template>

<style scoped>
.content {
  white-space: pre-wrap;
  word-break: break-all;
}
.mt {
  margin-top: 6px;
}
.reply {
  margin-top: 6px;
  padding: 6px 8px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 13px;
}
</style>
