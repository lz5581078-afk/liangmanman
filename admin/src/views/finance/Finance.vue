<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SettlementTab from './SettlementTab.vue'
import WithdrawalTab from './WithdrawalTab.vue'
import RefundTab from './RefundTab.vue'

const route = useRoute()
const router = useRouter()
const TABS = ['settlements', 'withdrawals', 'refunds']
const tab = ref(TABS.includes(route.query.tab) ? route.query.tab : 'settlements')

function onTab(name) {
  router.replace({ query: { ...route.query, tab: name } })
}
</script>

<template>
  <div class="page-card">
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="结算记录" name="settlements" lazy><SettlementTab /></el-tab-pane>
      <el-tab-pane label="提现记录" name="withdrawals" lazy><WithdrawalTab /></el-tab-pane>
      <el-tab-pane label="退款记录" name="refunds" lazy><RefundTab /></el-tab-pane>
    </el-tabs>
  </div>
</template>
