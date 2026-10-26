<script setup>
import { onMounted, ref } from 'vue'
import { listRefunds } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { REFUND_STATUS } from '@/utils/dict'
import { time, yuan, text } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import OrderDrawer from '../orders/OrderDrawer.vue'

const { records, total, loading, page, pageSize, load } = usePagedList(listRefunds)
const orderId = ref(null)
const orderVisible = ref(false)

function openOrder(id) {
  orderId.value = id
  orderVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="订单" min-width="180">
        <template #default="{ row }">
          <el-link v-if="row.orderId" type="primary" underline="never" @click="openOrder(row.orderId)">{{ row.orderNo || `#${row.orderId}` }}</el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="退款单号" min-width="200">
        <template #default="{ row }">{{ text(row.outRefundNo) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }"><span class="money">{{ yuan(row.amount) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusTag :dict="REFUND_STATUS" :value="row.status" :label="row.statusLabel" /></template>
      </el-table-column>
      <el-table-column label="原因" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ text(row.reason) }}</template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
    <OrderDrawer v-model="orderVisible" :order-id="orderId" />
  </div>
</template>
