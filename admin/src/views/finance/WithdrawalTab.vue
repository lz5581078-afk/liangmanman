<script setup>
import { onMounted } from 'vue'
import { listWithdrawals } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { WITHDRAW_STATUS } from '@/utils/dict'
import { time, yuan, text } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'

const { query, records, total, loading, page, pageSize, load, search } = usePagedList(listWithdrawals, { status: '' })
onMounted(load)
</script>

<template>
  <div>
    <el-radio-group v-model="query.status" class="mb" @change="search">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button v-for="o in WITHDRAW_STATUS.options" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
    </el-radio-group>
    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="店铺" min-width="160">
        <template #default="{ row }">{{ row.storeName || (row.storeId ? `店铺 #${row.storeId}` : '-') }}</template>
      </el-table-column>
      <el-table-column label="金额" width="130" align="right">
        <template #default="{ row }"><span class="money">{{ yuan(row.amount) }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusTag :dict="WITHDRAW_STATUS" :value="row.status" :label="row.statusLabel" /></template>
      </el-table-column>
      <el-table-column label="说明 / 失败原因" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ text(row.reason) }}</template>
      </el-table-column>
      <el-table-column label="申请时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
  </div>
</template>

<style scoped>
.mb {
  margin-bottom: 12px;
}
</style>
