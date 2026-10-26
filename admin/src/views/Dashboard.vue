<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, ArrowRight } from '@element-plus/icons-vue'
import { getDashboard } from '@/api'
import { money } from '@/utils/format'
import TrendBars from '@/components/TrendBars.vue'

const router = useRouter()
const loading = ref(false)
const d = ref({})

async function load() {
  loading.value = true
  try {
    d.value = (await getDashboard()) || {}
  } finally {
    loading.value = false
  }
}
onMounted(load)

const num = (v) => (v === null || v === undefined ? 0 : v)

const cards = computed(() => [
  { label: '用户', value: num(d.value.users) },
  { label: '已上线店铺', value: num(d.value.storesApproved), to: { name: 'stores', query: { status: 'APPROVED' } } },
  { label: '待审核店铺', value: num(d.value.storesPending), alert: true, to: { name: 'stores', query: { status: 'PENDING' } } },
  { label: '商品', value: num(d.value.products), to: { name: 'products' } },
  { label: '今日订单', value: num(d.value.ordersToday), sub: `金额 ¥${money(num(d.value.amountToday))}`, to: { name: 'orders' } },
  { label: '近 7 天订单', value: num(d.value.orders7d), sub: `金额 ¥${money(num(d.value.amount7d))}` },
  { label: '待审证件', value: num(d.value.pendingLicenses), alert: true, to: { name: 'licenses' } },
  { label: '待归一商品', value: num(d.value.pendingNormalize), alert: true, to: { name: 'products', query: { normalize: 'PENDING' } } },
  { label: '待核实的款', value: num(d.value.pendingSkus), alert: true, to: { name: 'standard-products', query: { status: 'PENDING' } } }
])

const daily = computed(() => d.value.daily || [])
const shortDate = (s) => (s ? String(s).slice(5).replace('-', '/') : '')
const orderSeries = computed(() => daily.value.map((x) => ({ label: shortDate(x.date), full: x.date, value: num(x.orders) })))
const amountSeries = computed(() => daily.value.map((x) => ({ label: shortDate(x.date), full: x.date, value: num(x.amount) })))
const sum = (arr) => arr.reduce((s, x) => s + (Number(x.value) || 0), 0)

const fmtCount = (v) => `${Math.round(v)}`
const fmtAmount = (v) => (v >= 10000 ? `${+(v / 10000).toFixed(1)}万` : `${+Number(v).toFixed(2)}`)
const fmtAmountTip = (v) => `¥${money(v)}`

const keywords = computed(() => d.value.noResultKeywords || [])
const maxKw = computed(() => Math.max(1, ...keywords.value.map((k) => k.count || 0)))
</script>

<template>
  <div v-loading="loading">
    <div class="cards">
      <div
        v-for="c in cards"
        :key="c.label"
        class="stat"
        :class="{ clickable: c.to, alert: c.alert && c.value > 0 }"
        @click="c.to && router.push(c.to)"
      >
        <div class="stat-label">
          {{ c.label }}
          <el-icon v-if="c.to" class="go"><ArrowRight /></el-icon>
        </div>
        <div class="stat-value">{{ c.value }}</div>
        <div v-if="c.sub" class="stat-sub">{{ c.sub }}</div>
        <div v-else-if="c.alert" class="stat-sub">{{ c.value > 0 ? '待处理，点击前往' : '暂无待处理' }}</div>
      </div>
    </div>

    <div class="page-card">
      <div class="toolbar">
        <div>
          <b>近 14 天趋势</b>
          <span class="muted">（订单数含全部状态；金额以后端统计口径为准）</span>
        </div>
        <el-button :icon="Refresh" text @click="load">刷新</el-button>
      </div>
      <el-empty v-if="!daily.length" description="暂无数据" :image-size="60" />
      <div v-else class="charts">
        <div class="chart">
          <div class="chart-head">
            <span><i class="dot" style="background: #2a78d6" />订单数</span>
            <span class="muted">合计 {{ sum(orderSeries) }} 单</span>
          </div>
          <TrendBars :data="orderSeries" color="#2a78d6" :format="fmtCount" />
        </div>
        <div class="chart">
          <div class="chart-head">
            <span><i class="dot" style="background: #1baf7a" />订单金额（元）</span>
            <span class="muted">合计 ¥{{ money(sum(amountSeries)) }}</span>
          </div>
          <TrendBars :data="amountSeries" color="#1baf7a" :format="fmtAmount" />
        </div>
      </div>
    </div>

    <div class="page-card">
      <div class="toolbar">
        <div>
          <b>近 7 天搜索无结果的关键词</b>
          <span class="muted">（农户搜了没找到：叫法不同的加到搜索同义词；确实没人卖的，可以据此招商）</span>
        </div>
        <el-button text type="primary" @click="router.push({ name: 'search-words' })">去加同义词</el-button>
      </div>
      <el-table :data="keywords" size="default" empty-text="近 7 天没有无结果的搜索">
        <el-table-column type="index" label="#" width="56" />
        <el-table-column prop="keyword" label="关键词" min-width="200" />
        <el-table-column label="搜索次数" min-width="260">
          <template #default="{ row }">
            <div class="kw-bar">
              <div class="kw-fill" :style="{ width: `${((row.count || 0) / maxKw) * 100}%` }" />
              <span>{{ row.count || 0 }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
@media (max-width: 1100px) {
  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.stat {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  border: 1px solid transparent;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.stat.clickable {
  cursor: pointer;
}
.stat.clickable:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}
.stat-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.go {
  color: var(--el-text-color-placeholder);
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  margin-top: 6px;
  font-variant-numeric: tabular-nums;
}
.stat.alert .stat-value {
  color: #d9480f;
}
.stat.alert {
  border-left: 3px solid #f08c00;
}
.stat-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 32px;
  padding-top: 24px;
}
@media (max-width: 1100px) {
  .charts {
    grid-template-columns: 1fr;
  }
}
.chart-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
}
.dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 6px;
  vertical-align: -1px;
}
.kw-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.kw-fill {
  height: 8px;
  border-radius: 0 4px 4px 0;
  background: #eda100;
  min-width: 2px;
  max-width: 70%;
}
.kw-bar span {
  font-variant-numeric: tabular-nums;
}
</style>
