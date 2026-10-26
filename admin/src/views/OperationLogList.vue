<script setup>
import { onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listLogs } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { time, text } from '@/utils/format'
import Pager from '@/components/Pager.vue'

const { records, total, loading, page, pageSize, load } = usePagedList(listLogs)
onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <span class="muted">记录管理员的审核、下架、仲裁、配置修改等操作。</span>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="管理员" width="120">
        <template #default="{ row }">{{ text(row.adminName) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">{{ text(row.action) }}</template>
      </el-table-column>
      <el-table-column label="对象" width="180">
        <template #default="{ row }">{{ text(row.target) }}</template>
      </el-table-column>
      <el-table-column label="详情" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">{{ text(row.detail) }}</template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
  </div>
</template>
