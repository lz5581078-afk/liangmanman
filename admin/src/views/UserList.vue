<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listUsers } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { time, text } from '@/utils/format'
import FilterBar from '@/components/FilterBar.vue'
import Pager from '@/components/Pager.vue'

const router = useRouter()
const { query, records, total, loading, page, pageSize, load, search, reset } = usePagedList(listUsers, { keyword: '' })
onMounted(load)
</script>

<template>
  <div class="page-card">
    <FilterBar @search="search" @reset="reset">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="昵称 / 手机号" clearable style="width: 220px" />
      </el-form-item>
    </FilterBar>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="昵称" min-width="160">
        <template #default="{ row }">{{ text(row.nickname) }}</template>
      </el-table-column>
      <el-table-column label="手机号" min-width="140">
        <template #default="{ row }">{{ text(row.phone) }}</template>
      </el-table-column>
      <el-table-column label="身份" width="160">
        <template #default="{ row }">
          <el-tag v-if="row.storeId" type="success" size="small" disable-transitions>店主 · 店铺 #{{ row.storeId }}</el-tag>
          <span v-else class="muted">买家</span>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="180">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="row.phone" link type="primary" @click="router.push({ name: 'orders', query: { keyword: row.phone } })">查订单</el-button>
          <el-button v-if="row.storeId" link type="primary" @click="router.push({ name: 'products', query: { storeId: String(row.storeId) } })">看商品</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
  </div>
</template>
