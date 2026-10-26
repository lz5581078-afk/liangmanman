<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { listStores } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { STORE_STATUS } from '@/utils/dict'
import { time, text } from '@/utils/format'
import FilterBar from '@/components/FilterBar.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import Thumb from '@/components/Thumb.vue'
import StoreDrawer from './StoreDrawer.vue'
import { useStoreActions } from './useStoreActions'

const route = useRoute()
const list = usePagedList(listStores, { status: '', keyword: '' })
const { query, records, total, loading, page, pageSize, load, search, reset } = list
const drawerId = ref(null)
const drawerVisible = ref(false)
const actions = useStoreActions(load)

function open(row) {
  drawerId.value = row.id
  drawerVisible.value = true
}

onMounted(() => {
  if (typeof route.query.status === 'string') query.status = route.query.status
  load()
})
</script>

<template>
  <div class="page-card">
    <el-radio-group v-model="query.status" class="status-tabs" @change="search">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button v-for="o in STORE_STATUS.options" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
    </el-radio-group>
    <FilterBar @search="search" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="店名 / 电话 / 店主" clearable style="width: 220px" />
      </el-form-item>
    </FilterBar>

    <el-table v-loading="loading" :data="records" row-key="id" @row-dblclick="open">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="店铺" min-width="240">
        <template #default="{ row }">
          <div class="cell-main">
            <Thumb :src="row.coverImage" :size="44" />
            <div class="cell-text">
              <el-link type="primary" underline="never" @click="open(row)">{{ row.name || '-' }}</el-link>
              <div class="muted ellipsis">{{ [row.town, row.addressText].filter(Boolean).join(' · ') || '-' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="店主" min-width="140">
        <template #default="{ row }">
          <div>{{ text(row.ownerNickname) }}</div>
          <div class="muted">{{ row.ownerPhone || row.phone || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="店铺电话" prop="phone" width="130">
        <template #default="{ row }">{{ text(row.phone) }}</template>
      </el-table-column>
      <el-table-column label="商品数" width="80" align="right">
        <template #default="{ row }">{{ row.productCount ?? 0 }}</template>
      </el-table-column>
      <el-table-column label="营业" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isOpen" type="success" size="small">营业中</el-tag>
          <el-tag v-else type="info" size="small">打烊</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag :dict="STORE_STATUS" :value="row.status" /></template>
      </el-table-column>
      <el-table-column label="申请时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">详情</el-button>
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" @click="actions.approve(row)">通过</el-button>
            <el-button link type="danger" @click="actions.reject(row)">拒绝</el-button>
          </template>
          <el-button v-else-if="row.status === 'APPROVED'" link type="danger" @click="actions.ban(row)">停用</el-button>
          <el-button v-else-if="row.status === 'BANNED'" link type="success" @click="actions.unban(row)">恢复</el-button>
          <el-button v-else-if="row.status === 'REJECTED'" link type="success" @click="actions.approve(row)">改为通过</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />

    <StoreDrawer v-model="drawerVisible" :store-id="drawerId" @changed="load" />
  </div>
</template>

<style scoped>
.status-tabs {
  margin-bottom: 16px;
}
</style>
