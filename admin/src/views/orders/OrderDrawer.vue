<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getOrder, forceCancelOrder } from '@/api'
import { ORDER_STATUS } from '@/utils/dict'
import { time, yuan, text } from '@/utils/format'
import { askReason, confirmAction } from '@/utils/prompt'
import StatusTag from '@/components/StatusTag.vue'
import Thumb from '@/components/Thumb.vue'

const visible = defineModel({ type: Boolean, default: false })
const props = defineProps({ orderId: { type: [Number, String], default: null } })
const emit = defineEmits(['changed'])

const loading = ref(false)
const order = ref(null)

async function load() {
  if (!props.orderId) return
  loading.value = true
  try {
    order.value = await getOrder(props.orderId)
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [visible.value, props.orderId],
  ([v]) => {
    if (v) {
      order.value = null
      load()
    }
  }
)

const o = computed(() => order.value || {})
const canCancel = computed(() => o.value.status && !['COMPLETED', 'CANCELLED'].includes(o.value.status))
const logs = computed(() => (o.value.logs || []).slice())

async function forceCancel() {
  const paidOnline = o.value.paid && o.value.payMethod !== 'COD'
  try {
    const reason = await askReason('仲裁取消订单', { placeholder: '仲裁原因（买家和商家都会看到）', confirmText: '下一步' })
    await confirmAction(
      `确定取消订单 ${o.value.orderNo}？${paidOnline ? `已支付的 ${yuan(o.value.totalAmount)} 将原路退回买家。` : '该订单未在线支付，不涉及退款。'}此操作不可撤销。`,
      '二次确认',
      'error'
    )
    await forceCancelOrder(o.value.id, reason)
  } catch {
    return
  }
  ElMessage.success(paidOnline ? '已取消，退款已发起' : '已取消')
  load()
  emit('changed')
}

const mapUrl = computed(() =>
  o.value.addressLat && o.value.addressLng
    ? `https://apis.map.qq.com/uri/v1/marker?marker=coord:${o.value.addressLat},${o.value.addressLng};title:${encodeURIComponent('收货地址')}&referer=liangmanman`
    : ''
)
</script>

<template>
  <el-drawer v-model="visible" size="680px" :title="o.orderNo ? `订单 ${o.orderNo}` : '订单详情'">
    <div v-loading="loading" class="body">
      <template v-if="order">
        <div class="head">
          <div>
            <StatusTag :dict="ORDER_STATUS" :value="o.status" :label="o.statusLabel" />
            <el-tag v-if="o.groupBuyId" type="warning" class="ml" disable-transitions>凑单 #{{ o.groupBuyId }}</el-tag>
            <el-tag v-if="o.reviewed" type="success" class="ml" disable-transitions>已评价</el-tag>
          </div>
          <el-button v-if="canCancel" type="danger" plain @click="forceCancel">仲裁取消</el-button>
        </div>
        <el-alert v-if="o.cancelReason" type="info" :closable="false" :title="`取消原因：${o.cancelReason}`" class="mb" />

        <div class="drawer-section">
          <h4>商品</h4>
          <el-table :data="o.items || []" size="small" border>
            <el-table-column label="商品" min-width="220">
              <template #default="{ row }">
                <div class="cell-main">
                  <Thumb :src="row.image" :size="36" />
                  <span>{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="单价" width="100" align="right">
              <template #default="{ row }">{{ yuan(row.price) }}<span class="muted">/{{ row.unit || '件' }}</span></template>
            </el-table-column>
            <el-table-column label="数量" width="70" align="right" prop="quantity" />
            <el-table-column label="小计" width="100" align="right">
              <template #default="{ row }">{{ yuan(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div class="amounts">
            <span>商品 {{ yuan(o.goodsAmount) }}</span>
            <span>配送费 {{ yuan(o.deliveryFee ?? 0) }}</span>
            <span class="total">合计 <b>{{ yuan(o.totalAmount) }}</b></span>
          </div>
        </div>

        <div class="drawer-section">
          <h4>配送与付款</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="配送方式">{{ o.fulfillmentLabel || text(o.fulfillment) }}</el-descriptions-item>
            <el-descriptions-item label="付款方式">{{ o.payMethodLabel || text(o.payMethod) }}</el-descriptions-item>
            <el-descriptions-item label="是否已付">
              <template v-if="o.payMethod === 'COD'">{{ o.codReceived ? '货到付款·已收款' : '货到付款·未收款' }}</template>
              <template v-else>{{ o.paid ? '已支付' : '未支付' }}</template>
            </el-descriptions-item>
            <el-descriptions-item label="期望日期">{{ text(o.expectedDate) }}</el-descriptions-item>
            <el-descriptions-item label="收货人">{{ text(o.contactName) }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ text(o.contactPhone) }}</el-descriptions-item>
            <el-descriptions-item label="村">{{ text(o.village) }}</el-descriptions-item>
            <el-descriptions-item label="坐标">
              <template v-if="mapUrl"><el-link :href="mapUrl" target="_blank" type="primary">{{ o.addressLat }}, {{ o.addressLng }}</el-link></template>
              <template v-else>-</template>
            </el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ text(o.addressText) }}</el-descriptions-item>
            <el-descriptions-item v-if="o.expressNo" label="快递" :span="2">{{ o.expressCompany || '' }} {{ o.expressNo }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ text(o.remark) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="drawer-section">
          <h4>店铺与时间</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="店铺">{{ text(o.storeName) }}（ID {{ text(o.storeId) }}）</el-descriptions-item>
            <el-descriptions-item label="店铺电话">{{ text(o.storePhone) }}</el-descriptions-item>
            <el-descriptions-item label="下单">{{ time(o.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="支付">{{ time(o.paidAt) }}</el-descriptions-item>
            <el-descriptions-item label="完成">{{ time(o.completedAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="drawer-section">
          <h4>状态日志</h4>
          <el-empty v-if="!logs.length" description="暂无日志" :image-size="48" />
          <el-timeline v-else>
            <el-timeline-item v-for="(l, i) in logs" :key="i" :timestamp="time(l.at)" placement="top">
              <b>{{ l.statusLabel || ORDER_STATUS.label[l.status] || l.status }}</b>
              <span v-if="l.operator" class="muted"> · {{ l.operator }}</span>
              <div v-if="l.note" class="note">{{ l.note }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.body {
  min-height: 200px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.ml {
  margin-left: 6px;
}
.mb {
  margin-bottom: 16px;
}
.amounts {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.total b {
  font-size: 16px;
  color: #d9480f;
}
.note {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
