<script setup>
import { onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listLicenses, approveLicense, rejectLicense } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { LICENSE_STATUS, LICENSE_TYPE } from '@/utils/dict'
import { time, text } from '@/utils/format'
import { askReason, confirmAction } from '@/utils/prompt'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import Thumb from '@/components/Thumb.vue'

const { query, records, total, loading, page, pageSize, load, search } = usePagedList(listLicenses, { status: 'PENDING' })

async function approve(row) {
  try {
    await confirmAction(`确定通过「${row.storeName || '该店'}」的${typeLabel(row)}？`, '证件审核', 'info')
    await approveLicense(row.id)
  } catch {
    return
  }
  ElMessage.success('已通过')
  load()
}

async function reject(row) {
  try {
    const reason = await askReason(`拒绝 ${typeLabel(row)}`, { placeholder: '如：照片模糊、证件已过期、名称与店铺不符' })
    await rejectLicense(row.id, reason)
  } catch {
    return
  }
  ElMessage.success('已拒绝')
  load()
}

const typeLabel = (row) => row.typeLabel || LICENSE_TYPE.label[row.type] || row.type || '证件'

/** 有效期已过 */
const expired = (d) => d && /^\d{4}-\d{2}-\d{2}/.test(d) && new Date(d) < new Date(new Date().toDateString())

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="query.status" @change="search">
        <el-radio-button v-for="o in LICENSE_STATUS.options" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button>
        <el-radio-button value="">全部</el-radio-button>
      </el-radio-group>
      <span class="muted">点击图片可放大查看，滚轮缩放</span>
    </div>

    <el-table v-loading="loading" :data="records" row-key="id">
      <el-table-column label="证件照" width="150">
        <template #default="{ row }"><Thumb :src="row.image" :size="120" /></template>
      </el-table-column>
      <el-table-column label="店铺" min-width="180">
        <template #default="{ row }">
          <div>{{ text(row.storeName) }}</div>
          <div class="muted">店铺 ID {{ text(row.storeId) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="140">
        <template #default="{ row }">{{ typeLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="有效期至" width="130">
        <template #default="{ row }">
          <span :class="{ expired: expired(row.validUntil) }">{{ text(row.validUntil) }}</span>
          <div v-if="expired(row.validUntil)" class="expired">已过期</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :dict="LICENSE_STATUS" :value="row.status" />
          <div v-if="row.rejectReason" class="muted">{{ row.rejectReason }}</div>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ time(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button type="success" size="small" @click="approve(row)">通过</el-button>
            <el-button type="danger" size="small" plain @click="reject(row)">拒绝</el-button>
          </template>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>
    <Pager v-model:page="page" v-model:size="pageSize" :total="total" @change="load" />
  </div>
</template>

<style scoped>
.expired {
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
