<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listSettlements, retrySettlement } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { SETTLEMENT_STATUS } from '@/utils/dict'
import { time, yuan, text } from '@/utils/format'
import { confirmAction } from '@/utils/prompt'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import OrderDrawer from '../orders/OrderDrawer.vue'

const { query, records, total, loading, page, pageSize, load, search } = usePagedList(listSettlements, { status: '' })
const retrying = ref(null)
const orderId = ref(null)
const orderVisible = ref(false)

async function retry(row) {
  try {
    await confirmAction(`重新发起结算 #${row.id}（${yuan(row.amount)}）？`, '重试结算', 'info')
  } catch {
    return
  }
  retrying.value = row.id
  try {
    await retrySettlement(row.id)
    ElMessage.success('已重新发起')
    load()
  } catch {
    /* 已提示 */
  } finally {
    retrying.value = null
  }
}

function openOrder(id) {
  orderId.value = id
  orderVisible.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <el-radio-group v-model="query.status" class="mb" @change="search">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button v-for="o in SETTLEMENT_STATUS.options" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
    </el-radio-group>
    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="订单" min-width="190">
        <template #default="{ row }">
          <el-link v-if="row.orderId" type="primary" underline="never" @click="openOrder(row.orderId)">{{ row.orderNo || `#${row.orderId}` }}</el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="店铺" min-width="140">
        <template #default="{ row }">
          {{ row.storeName || (row.storeId ? `店铺 #${row.storeId}` : '-') }}
        </template>
      </el-table-column>
      <el-table-column label="结算金额" width="120" align="right">
        <template #default="{ row }"><span class="money">{{ yuan(row.amount) }}</span></template>
      </el-table-column>
      <el-table-column label="平台服务费" width="110" align="right">
        <template #default="{ row }"><span class="money">{{ yuan(row.platformFee) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusTag :dict="SETTLEMENT_STATUS" :value="row.status" :label="row.statusLabel" /></template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ text(row.reason || row.error) }}</template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'FAILED'" link type="primary" :loading="retrying === row.id" @click="retry(row)">重试</el-button>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
    <OrderDrawer v-model="orderVisible" :order-id="orderId" />
  </div>
</template>

<style scoped>
.mb {
  margin-bottom: 12px;
}
</style>
