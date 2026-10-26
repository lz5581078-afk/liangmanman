<template>
  <view class="page">
    <view class="note-top">这里只是帮你记账，钱不经过平台</view>
    <view v-if="c" class="head">
      <view class="row between">
        <view class="grow">
          <view class="c-name">{{ c.name }}</view>
          <view class="c-sub">{{ [c.village, c.phone].filter(Boolean).join(' · ') || '没填村和电话' }}</view>
        </view>
        <view class="edit" @tap="openEdit">改资料</view>
      </view>
      <view class="owe-label">{{ Number(c.balance) > 0 ? '还欠你' : Number(c.balance) < 0 ? '多给了你' : '已经结清' }}</view>
      <view class="owe-num">¥{{ money(Math.abs(Number(c.balance) || 0)) }}</view>
      <button v-if="c.phone" class="btn small ghost call-btn" @tap="call(c.phone)">打电话</button>
    </view>

    <view class="row big-btns">
      <button class="btn big warn grow" @tap="openRecord('CREDIT')">记一笔赊账</button>
      <button class="btn big grow" style="margin-left: 20rpx" @tap="openRecord('REPAY')">收到还款</button>
    </view>

    <view class="section-title">流水</view>
    <view v-if="loaded && !list.length" class="empty">还没有记录</view>
    <view v-if="list.length" class="card list">
      <view v-for="r in list" :key="r.id" class="line row">
        <view class="grow">
          <view class="r-type">{{ r.typeLabel || (r.type === 'CREDIT' ? '赊账' : '还款') }}</view>
          <view v-if="r.note" class="sub">{{ r.note }}</view>
          <view class="muted">{{ dateTime(r.createdAt) }}</view>
        </view>
        <text class="amt" :class="r.type">{{ r.type === 'CREDIT' ? '+' : '-' }}{{ money(r.amount) }}</text>
      </view>
    </view>
    <view v-if="list.length" class="muted end">{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</view>

    <!-- 记一笔 -->
    <view v-if="recType" class="mask" @tap="recType = ''">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">{{ recType === 'CREDIT' ? '记一笔赊账' : '收到还款' }}</view>
        <view class="field">
          <view class="label">多少钱</view>
          <view class="row">
            <text class="yuan">¥</text>
            <input class="grow big-input" type="digit" v-model="rec.amount" placeholder="0" :focus="true" />
          </view>
        </view>
        <view class="field">
          <view class="label">备注（可不填）</view>
          <input class="input" v-model="rec.note" maxlength="100" :placeholder="recType === 'CREDIT' ? '如：复合肥 2 袋' : '如：还了一部分'" />
        </view>
        <button class="btn big" :class="recType === 'CREDIT' ? 'wheat' : ''" style="margin-top: 24rpx" :disabled="saving" @tap="saveRecord">记下来</button>
      </view>
    </view>

    <!-- 改资料 -->
    <view v-if="editOpen" class="mask" @tap="editOpen = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">改资料</view>
        <view class="field">
          <view class="label">名字</view>
          <input class="input" v-model="ef.name" maxlength="32" />
        </view>
        <view class="field">
          <view class="label">村</view>
          <input class="input" v-model="ef.village" maxlength="32" />
        </view>
        <view class="field">
          <view class="label">电话</view>
          <input class="input" type="number" v-model="ef.phone" maxlength="11" />
        </view>
        <button class="btn big" style="margin-top: 24rpx" :disabled="saving" @tap="saveEdit">保存</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad, onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { creditCustomers, creditRecords, addCreditRecord, updateCreditCustomer } from '../../api'
import { usePaged } from '../../utils/paged'
import { money, dateTime, call } from '../../utils/format'

const id = ref(0)
const c = ref(null)
const recType = ref('')
const rec = reactive({ amount: '', note: '' })
const editOpen = ref(false)
const ef = reactive({ name: '', village: '', phone: '' })
const saving = ref(false)
const { list, loading, finished, loaded, reload, more } = usePaged((p) => creditRecords(id.value, p))

async function loadCustomer() {
  // 契约没有单个客户接口：从列表里找
  const r = (await creditCustomers()) || {}
  c.value = (r.customers || []).find((x) => x.id === id.value) || c.value
  if (c.value) uni.setNavigationBarTitle({ title: c.value.name + ' 的账' })
}

onLoad((q) => {
  id.value = Number(q.id)
  loadCustomer().catch(() => {})
  reload().catch(() => {})
})
onReachBottom(() => more().catch(() => {}))
onPullDownRefresh(async () => {
  await Promise.all([loadCustomer().catch(() => {}), reload().catch(() => {})])
  uni.stopPullDownRefresh()
})

function openRecord(t) {
  recType.value = t
  rec.amount = ''
  rec.note = ''
}

async function saveRecord() {
  const amount = Math.round(Number(rec.amount) * 100) / 100
  if (!(amount > 0)) return uni.showToast({ title: '请填金额', icon: 'none' })
  saving.value = true
  try {
    const updated = await addCreditRecord(id.value, { type: recType.value, amount, note: rec.note.trim() || null })
    if (updated) c.value = updated
    uni.showToast({ title: '记好了', icon: 'success' })
    recType.value = ''
    reload().catch(() => {})
  } finally {
    saving.value = false
  }
}

function openEdit() {
  Object.assign(ef, { name: c.value.name || '', village: c.value.village || '', phone: c.value.phone || '' })
  editOpen.value = true
}

async function saveEdit() {
  if (!ef.name.trim()) return uni.showToast({ title: '请填名字', icon: 'none' })
  saving.value = true
  try {
    const updated = await updateCreditCustomer(id.value, { name: ef.name.trim(), village: ef.village.trim() || null, phone: ef.phone || null })
    if (updated && updated.id) c.value = updated
    else await loadCustomer()
    editOpen.value = false
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.note-top { background: $lm-wheat-light; color: #8A5A00; text-align: center; padding: 16rpx; font-size: 28rpx; }
.head { background: #fff; padding: 32rpx; position: relative; }
.c-name { font-size: 44rpx; font-weight: 800; }
.c-sub { color: $lm-ink-2; }
.edit { color: $lm-green; font-weight: 700; padding: 12rpx; }
.owe-label { margin-top: 20rpx; color: $lm-ink-2; }
.owe-num { font-size: 76rpx; font-weight: 800; color: $lm-red; line-height: 1.2; }
.call-btn { position: absolute; right: 32rpx; bottom: 40rpx; }
.big-btns { padding: 24rpx; }
.big-btns .btn { padding: 0; }
.list { padding-top: 4rpx; padding-bottom: 4rpx; }
.line { padding: 22rpx 0; border-bottom: 2rpx solid $lm-line; }
.line:last-child { border-bottom: none; }
.r-type { font-size: 32rpx; font-weight: 700; }
.amt { font-size: 38rpx; font-weight: 800; margin-left: 16rpx; }
.amt.CREDIT { color: $lm-red; }
.amt.REPAY { color: $lm-green; }
.end { text-align: center; padding: 20rpx 0 40rpx; }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(0, 0, 0, 0.45); z-index: 50; }
.sheet {
  position: absolute; left: 0; right: 0; bottom: 0; background: #fff;
  border-top-left-radius: 28rpx; border-top-right-radius: 28rpx; padding: 28rpx;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}
.sheet-title { font-size: 36rpx; font-weight: 800; }
.field { padding: 18rpx 0; border-bottom: 2rpx solid $lm-line; }
.label { color: $lm-ink-2; font-size: 28rpx; }
.input { height: 80rpx; font-size: 34rpx; }
.yuan { font-size: 52rpx; color: $lm-wheat; font-weight: 800; margin-right: 8rpx; }
.big-input { font-size: 56rpx; font-weight: 800; height: 96rpx; }
</style>
