<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { listOrders } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { ORDER_STATUS } from '@/utils/dict'
import { time, yuan, text } from '@/utils/format'
import FilterBar from '@/components/FilterBar.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import OrderDrawer from './OrderDrawer.vue'

const route = useRoute()
const { query, records, total, loading, page, pageSize, load, search, reset } = usePagedList(listOrders, {
  status: '',
  keyword: '',
  storeId: ''
})
const drawerVisible = ref(false)
const drawerId = ref(null)

function open(row) {
  drawerId.value = row.id
  drawerVisible.value = true
}

const itemSummary = (row) => {
  const items = row.items || []
  if (!items.length) return '-'
  const first = `${items[0].name} ×${items[0].quantity}`
  return items.length > 1 ? `${first} 等 ${items.length} 件` : first
}

onMounted(() => {
  if (typeof route.query.status === 'string') query.status = route.query.status
  if (typeof route.query.storeId === 'string') query.storeId = route.query.storeId
  if (typeof route.query.keyword === 'string') query.keyword = route.query.keyword
  load()
})
</script>

<template>
  <div class="page-card">
    <FilterBar @search="search" @reset="reset">
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option v-for="o in ORDER_STATUS.options" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="订单号 / 手机号" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="店铺 ID">
        <el-input v-model="query.storeId" placeholder="店铺 ID" clearable style="width: 110px" />
      </el-form-item>
    </FilterBar>

    <el-table v-loading="loading" :data="records" row-key="id" @row-dblclick="open">
      <el-table-column label="订单号" min-width="190">
        <template #default="{ row }">
          <el-link type="primary" underline="never" @click="open(row)">{{ row.orderNo || row.id }}</el-link>
          <div class="muted">
            {{ time(row.createdAt) }}
            <el-tag v-if="row.groupBuyId" size="small" type="warning" disable-transitions>凑单</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="店铺" min-width="140">
        <template #default="{ row }">
          <div>{{ text(row.storeName) }}</div>
          <div class="muted">ID {{ text(row.storeId) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="商品" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ itemSummary(row) }}</template>
      </el-table-column>
      <el-table-column label="买家" min-width="130">
        <template #default="{ row }">
          <div>{{ text(row.contactName) }}</div>
          <div class="muted">{{ row.contactPhone || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="实付" width="110" align="right">
        <template #default="{ row }"><span class="money">{{ yuan(row.totalAmount) }}</span></template>
      </el-table-column>
      <el-table-column label="配送 / 付款" width="130">
        <template #default="{ row }">
          <div>{{ row.fulfillmentLabel || row.fulfillment || '-' }}</div>
          <div class="muted">
            {{ row.payMethodLabel || row.payMethod || '' }}
            <template v-if="row.payMethod === 'ONLINE'">{{ row.paid ? '·已付' : '·未付' }}</template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag :dict="ORDER_STATUS" :value="row.status" :label="row.statusLabel" /></template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />

    <OrderDrawer v-model="drawerVisible" :order-id="drawerId" @changed="load" />
  </div>
</template>
