<script setup>
import { computed, ref, watch } from 'vue'
import { getStore } from '@/api'
import { STORE_STATUS, LICENSE_STATUS, LICENSE_TYPE, ACCOUNT_STATUS } from '@/utils/dict'
import { time, text, yuan } from '@/utils/format'
import StatusTag from '@/components/StatusTag.vue'
import Thumb from '@/components/Thumb.vue'
import { useStoreActions } from './useStoreActions'

const visible = defineModel({ type: Boolean, default: false })
const props = defineProps({ storeId: { type: [Number, String], default: null } })
const emit = defineEmits(['changed'])

const loading = ref(false)
const detail = ref(null)

async function load() {
  if (!props.storeId) return
  loading.value = true
  try {
    detail.value = await getStore(props.storeId)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => [visible.value, props.storeId],
  ([v]) => {
    if (v) {
      detail.value = null
      load()
    }
  }
)

const actions = useStoreActions(() => {
  load()
  emit('changed')
})

const store = computed(() => detail.value?.store || {})
const owner = computed(() => detail.value?.owner || {})
const licenses = computed(() => detail.value?.licenses || [])
const account = computed(() => detail.value?.account || null)
const mapUrl = computed(() =>
  store.value.lat && store.value.lng
    ? `https://apis.map.qq.com/uri/v1/marker?marker=coord:${store.value.lat},${store.value.lng};title:${encodeURIComponent(store.value.name || '店铺')}&referer=liangmanman`
    : ''
)
const bool = (v) => (v ? '是' : '否')
</script>

<template>
  <el-drawer v-model="visible" size="640px" :title="store.name ? `店铺详情 · ${store.name}` : '店铺详情'">
    <div v-loading="loading" class="body">
      <template v-if="detail">
        <div class="head">
          <Thumb :src="store.coverImage" :size="96" />
          <div class="head-info">
            <div class="name">
              {{ store.name }}
              <StatusTag :dict="STORE_STATUS" :value="store.status" />
            </div>
            <div class="muted">ID {{ store.id }} · 创建于 {{ time(store.createdAt) }}</div>
            <div class="counts">
              <span>商品 <b>{{ detail.productCount ?? store.productCount ?? 0 }}</b></span>
              <span>订单 <b>{{ detail.orderCount ?? 0 }}</b></span>
              <span v-if="store.rating">评分 <b>{{ store.rating }}</b></span>
            </div>
            <div class="ops">
              <template v-if="store.status === 'PENDING'">
                <el-button type="success" size="small" @click="actions.approve(store)">通过</el-button>
                <el-button type="danger" size="small" plain @click="actions.reject(store)">拒绝</el-button>
              </template>
              <el-button v-else-if="store.status === 'APPROVED'" type="danger" size="small" plain @click="actions.ban(store)">停用</el-button>
              <el-button v-else-if="store.status === 'BANNED'" type="success" size="small" @click="actions.unban(store)">恢复</el-button>
              <el-button v-else-if="store.status === 'REJECTED'" type="success" size="small" plain @click="actions.approve(store)">改为通过</el-button>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <h4>店铺信息</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="店铺电话">{{ text(store.phone) }}</el-descriptions-item>
            <el-descriptions-item label="乡镇">{{ text(store.town) }}</el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ text(store.addressText) }}</el-descriptions-item>
            <el-descriptions-item label="经纬度" :span="2">
              <template v-if="store.lat && store.lng">
                {{ store.lat }}, {{ store.lng }}
                <el-link :href="mapUrl" target="_blank" type="primary" style="margin-left: 8px">在地图中查看</el-link>
              </template>
              <template v-else>-</template>
            </el-descriptions-item>
            <el-descriptions-item label="主营">{{ text(store.mainCategories) }}</el-descriptions-item>
            <el-descriptions-item label="营业时间">{{ text(store.businessHours) }}</el-descriptions-item>
            <el-descriptions-item label="营业中">{{ bool(store.isOpen) }}</el-descriptions-item>
            <el-descriptions-item label="送货上门">{{ bool(store.deliveryEnabled) }}</el-descriptions-item>
            <el-descriptions-item label="送货范围">{{ store.deliveryRadiusKm != null ? `${store.deliveryRadiusKm} 公里` : '-' }}</el-descriptions-item>
            <el-descriptions-item label="配送费">{{ yuan(store.deliveryFee) }}</el-descriptions-item>
            <el-descriptions-item label="起送金额">{{ yuan(store.minOrderAmount) }}</el-descriptions-item>
            <el-descriptions-item label="到店自提">{{ bool(store.pickupEnabled) }}</el-descriptions-item>
            <el-descriptions-item label="货到付款">{{ bool(store.codEnabled) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ time(store.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="店铺公告" :span="2">{{ text(store.notice) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="drawer-section">
          <h4>店主</h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="昵称">
              <span class="cell-main">
                <el-avatar v-if="owner.avatar" :src="owner.avatar" :size="22" />
                {{ text(owner.nickname || store.ownerNickname) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="手机号">{{ text(owner.phone || store.ownerPhone) }}</el-descriptions-item>
            <el-descriptions-item label="用户 ID">{{ text(owner.id || store.ownerId) }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ time(owner.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="drawer-section">
          <h4>证件</h4>
          <el-empty v-if="!licenses.length" description="未上传证件" :image-size="48" />
          <div v-else class="licenses">
            <div v-for="l in licenses" :key="l.id" class="license">
              <Thumb :src="l.image" :size="110" />
              <div class="license-info">
                <div>
                  <b>{{ l.typeLabel || LICENSE_TYPE.label[l.type] || l.type }}</b>
                  <StatusTag :dict="LICENSE_STATUS" :value="l.status" style="margin-left: 6px" />
                </div>
                <div class="muted">有效期至 {{ text(l.validUntil) }}</div>
                <div class="muted">上传于 {{ time(l.createdAt) }}</div>
                <div v-if="l.rejectReason" class="reject">拒绝原因：{{ l.rejectReason }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <h4>收款开户</h4>
          <el-descriptions v-if="account" :column="2" border size="small">
            <el-descriptions-item label="状态">
              <StatusTag :dict="ACCOUNT_STATUS" :value="account.status" :label="account.statusLabel" />
            </el-descriptions-item>
            <el-descriptions-item label="在线收款">
              <el-tag :type="account.onlinePayEnabled ? 'success' : 'info'" size="small">{{ account.onlinePayEnabled ? '已开通' : '未开通' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="子商户号">{{ text(account.subMchId) }}</el-descriptions-item>
            <el-descriptions-item label="开户人">{{ text(account.idNameMasked) }}</el-descriptions-item>
            <el-descriptions-item label="银行卡">{{ text(account.bankCardMasked) }}</el-descriptions-item>
            <el-descriptions-item label="驳回原因">{{ text(account.rejectReason) }}</el-descriptions-item>
          </el-descriptions>
          <div v-else class="muted">未提交开户</div>
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
  gap: 16px;
  margin-bottom: 20px;
}
.head-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.name {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.counts {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.ops {
  margin-top: 2px;
}
.licenses {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.license {
  display: flex;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.license-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  min-width: 0;
}
.reject {
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
