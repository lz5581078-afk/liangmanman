<script setup>
import { onMounted, ref } from 'vue'
import { listNotifyLogs } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { time, text } from '@/utils/format'
import FilterBar from '@/components/FilterBar.vue'
import Pager from '@/components/Pager.vue'
import OrderDrawer from './orders/OrderDrawer.vue'

const { query, records, total, loading, page, pageSize, load, search, reset } = usePagedList(listNotifyLogs, { orderId: '' })

const CHANNEL = { SMS: '短信', VOICE: '语音电话', SUBSCRIBE: '订阅消息', WX_SUBSCRIBE: '订阅消息', LOG: '日志' }
const channelLabel = (c) => CHANNEL[c] || c || '-'

const orderId = ref(null)
const orderVisible = ref(false)
function openOrder(id) {
  orderId.value = id
  orderVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <FilterBar @search="search" @reset="reset">
      <el-form-item label="订单 ID">
        <el-input v-model="query.orderId" placeholder="订单 ID" clearable style="width: 140px" />
      </el-form-item>
    </FilterBar>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="渠道" width="110">
        <template #default="{ row }"><el-tag size="small" disable-transitions>{{ channelLabel(row.channel) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="对象" width="150">
        <template #default="{ row }">{{ text(row.target) }}</template>
      </el-table-column>
      <el-table-column label="订单" width="100">
        <template #default="{ row }">
          <el-link v-if="row.orderId" type="primary" underline="never" @click="openOrder(row.orderId)">#{{ row.orderId }}</el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">{{ text(row.content) }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small" disable-transitions>{{ row.success ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="错误" min-width="180" show-overflow-tooltip>
        <template #default="{ row }"><span :class="{ err: row.error }">{{ text(row.error) }}</span></template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
    <OrderDrawer v-model="orderVisible" :order-id="orderId" />
  </div>
</template>

<style scoped>
.err {
  color: var(--el-color-danger);
}
</style>
