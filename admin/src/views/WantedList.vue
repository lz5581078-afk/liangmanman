<script setup>
import { onMounted } from 'vue'
import { listWanted } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { WANTED_STATUS } from '@/utils/dict'
import { time, yuan, text } from '@/utils/format'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'

const { query, records, total, loading, page, pageSize, load, search } = usePagedList(listWanted, { status: '' })
onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="query.status" @change="search">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="o in WANTED_STATUS.options" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
      </el-radio-group>
      <span class="muted">展开行可查看商家回复</span>
    </div>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column type="expand" width="40">
        <template #default="{ row }">
          <div class="replies">
            <div v-if="!row.replies || !row.replies.length" class="muted">还没有商家回复</div>
            <el-table v-else :data="row.replies" size="small" border>
              <el-table-column label="店铺" min-width="140">
                <template #default="{ row: r }">{{ r.storeName || `#${r.storeId}` }} <span class="muted">{{ r.storePhone || '' }}</span></template>
              </el-table-column>
              <el-table-column label="报价" width="100" align="right">
                <template #default="{ row: r }">{{ r.price != null ? yuan(r.price) : '-' }}</template>
              </el-table-column>
              <el-table-column label="说明" min-width="200">
                <template #default="{ row: r }">{{ text(r.note) }}</template>
              </el-table-column>
              <el-table-column label="关联商品" width="100">
                <template #default="{ row: r }">{{ r.productId ? `#${r.productId}` : '-' }}</template>
              </el-table-column>
              <el-table-column label="时间" width="160">
                <template #default="{ row: r }">{{ time(r.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="求购" min-width="200">
        <template #default="{ row }">
          <div><b>{{ row.keyword }}</b></div>
          <div v-if="row.note" class="muted">{{ row.note }}</div>
        </template>
      </el-table-column>
      <el-table-column label="联系电话" width="130">
        <template #default="{ row }">{{ text(row.contactPhone) }}</template>
      </el-table-column>
      <el-table-column label="回复数" width="80" align="right">
        <template #default="{ row }">{{ row.replies ? row.replies.length : 0 }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag :dict="WANTED_STATUS" :value="row.status" /></template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="过期时间" width="170">
        <template #default="{ row }">{{ time(row.expireAt) }}</template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
  </div>
</template>

<style scoped>
.replies {
  padding: 4px 16px 4px 48px;
}
</style>
