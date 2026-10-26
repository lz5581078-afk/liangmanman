<template>
  <view class="page">
    <!-- 当前状态 -->
    <view v-if="acc" class="status-card" :class="acc.status">
      <view class="st-title">{{ statusTitle }}</view>
      <view class="st-sub">{{ statusSub }}</view>
      <view v-if="acc.bankCardMasked || acc.idNameMasked" class="st-info">
        <text v-if="acc.idNameMasked">开户人 {{ acc.idNameMasked }}</text>
        <text v-if="acc.bankCardMasked" style="margin-left: 24rpx">银行卡 {{ acc.bankCardMasked }}</text>
      </view>
      <view v-if="acc.status === 'REJECTED' && acc.rejectReason" class="st-reason">没通过的原因：{{ acc.rejectReason }}</view>
    </view>

    <!-- 待签约 -->
    <view v-if="acc && acc.status === 'NEED_SIGN'" class="card">
      <view class="big-t">还差最后一步：签约</view>
      <view class="sub">点下面按钮复制签约链接，发给自己（比如发到“文件传输助手”），在微信里打开，按提示完成签约。</view>
      <view v-if="acc.signUrl" class="url">{{ acc.signUrl }}</view>
      <button class="btn big" style="margin-top: 20rpx" @tap="copySign">复制签约链接</button>
      <button class="btn big ghost" style="margin-top: 20rpx" @tap="load">我签好了，刷新一下</button>
    </view>

    <!-- 表单：未开户 / 被拒 -->
    <view v-if="showForm">
      <view class="safe">身份证号和卡号只用于向微信支付开户，平台只保存打码后的信息。</view>

      <view class="step-title"><text class="num">1</text>拍身份证</view>
      <view class="row idcards">
        <view class="idc" @tap="pickId('front')">
          <image v-if="front.local" class="idimg" :src="front.local" mode="aspectFill" />
          <view v-else class="idph"><text class="cam">＋</text><text>人像面</text></view>
          <view v-if="front.uploading" class="idmask">上传中…</view>
          <view v-else-if="front.id" class="idok">✓ 已上传</view>
        </view>
        <view class="idc" @tap="pickId('back')">
          <image v-if="back.local" class="idimg" :src="back.local" mode="aspectFill" />
          <view v-else class="idph"><text class="cam">＋</text><text>国徽面</text></view>
          <view v-if="back.uploading" class="idmask">上传中…</view>
          <view v-else-if="back.id" class="idok">✓ 已上传</view>
        </view>
      </view>

      <view class="step-title"><text class="num">2</text>身份证上的信息</view>
      <view class="card">
        <view class="field">
          <view class="label">姓名</view>
          <input class="input" v-model="f.idName" maxlength="32" placeholder="和身份证上一样" />
        </view>
        <view class="field">
          <view class="label">身份证号</view>
          <input class="input" type="idcard" v-model="f.idNumber" maxlength="18" placeholder="18 位" />
        </view>
        <view class="field">
          <view class="label">有效期开始</view>
          <picker mode="date" :value="f.idValidFrom" @change="(e) => (f.idValidFrom = e.detail.value)">
            <view class="picker-v" :class="{ muted: !f.idValidFrom }">{{ f.idValidFrom || '点这里选日期' }}</view>
          </picker>
        </view>
        <view class="field">
          <view class="row between">
            <view class="label">有效期结束</view>
            <view class="row" @tap="f.longTerm = !f.longTerm">
              <view class="check" :class="{ on: f.longTerm }">{{ f.longTerm ? '✓' : '' }}</view>
              <text>长期</text>
            </view>
          </view>
          <picker v-if="!f.longTerm" mode="date" :value="f.idValidTo" @change="(e) => (f.idValidTo = e.detail.value)">
            <view class="picker-v" :class="{ muted: !f.idValidTo }">{{ f.idValidTo || '点这里选日期' }}</view>
          </picker>
        </view>
      </view>

      <view class="step-title"><text class="num">3</text>收钱的银行卡</view>
      <view class="card">
        <view class="field">
          <view class="label">开户银行</view>
          <picker :range="bankOptions" @change="onBank">
            <view class="picker-v" :class="{ muted: !f.bankName }">{{ bankDisplay }}</view>
          </picker>
          <input v-if="customBank" class="input" v-model="f.bankName" maxlength="64" placeholder="填银行全称" />
        </view>
        <view class="field">
          <view class="label">开户城市</view>
          <!-- #ifdef MP-WEIXIN -->
          <picker mode="region" level="city" :value="regionValue" @change="onRegion">
            <view class="picker-v" :class="{ muted: !f.bankCityCode }">{{ f.bankCityName || '点这里选省、市' }}</view>
          </picker>
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <input class="input" type="number" v-model="f.bankCityCode" maxlength="6" placeholder="城市代码，如 410100（郑州）" />
          <!-- #endif -->
        </view>
        <view class="field">
          <view class="label">银行卡号</view>
          <input class="input" type="number" v-model="f.bankAccount" maxlength="19" placeholder="12–19 位数字" />
        </view>
        <view class="field">
          <view class="label">手机号</view>
          <input class="input" type="number" v-model="f.mobile" maxlength="11" placeholder="11 位手机号" />
        </view>
        <view class="field">
          <view class="label">商户简称（农户付款时看到的名字）</view>
          <input class="input" v-model="f.shortName" maxlength="32" placeholder="如：老王农资店" />
        </view>
      </view>

      <view class="footer-space" />
      <view class="footer-bar">
        <button class="btn big grow" :disabled="saving || front.uploading || back.uploading" @tap="submit">提交开户</button>
      </view>
    </view>

    <view v-if="!acc" class="empty">加载中…</view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { merchantAccount, submitOnboarding, uploadOnboardingImage, myStore } from '../../api'
import { pickImages } from '../../utils/format'

const BANKS = ['中国农业银行', '中国邮政储蓄银行', '农村信用社/农商银行', '中国工商银行', '中国建设银行', '中国银行', '交通银行']
const bankOptions = [...BANKS, '其他银行（自己填）']

const acc = ref(null)
const saving = ref(false)
const customBank = ref(false)
const front = reactive({ local: '', id: '', uploading: false })
const back = reactive({ local: '', id: '', uploading: false })
const regionValue = ref([])
const f = reactive({
  idName: '', idNumber: '', idValidFrom: '', idValidTo: '', longTerm: false,
  bankName: '', bankCityCode: '', bankCityName: '', bankAccount: '', mobile: '', shortName: '',
})

const showForm = computed(() => acc.value && (acc.value.status === 'NONE' || acc.value.status === 'REJECTED'))
const bankDisplay = computed(() => (customBank.value ? '其他银行（在下面填）' : f.bankName || '点这里选银行'))

const statusTitle = computed(() => {
  const s = acc.value.status
  return {
    NONE: '还没开通收款账户',
    SUBMITTED: '已提交，等微信支付审核',
    AUDITING: '微信支付审核中',
    NEED_SIGN: '审核通过，等你签约',
    APPROVED: '收款账户已开通',
    REJECTED: '开户没通过',
  }[s] || acc.value.statusLabel || s
})
const statusSub = computed(() => {
  const a = acc.value
  if (a.status === 'APPROVED') return '农户在线付的钱，订单完成后进你的钱包，可以提现到银行卡。'
  if (a.status === 'SUBMITTED' || a.status === 'AUDITING') return '一般 1–3 个工作日，有结果会在这里显示。'
  if (a.status === 'REJECTED') return '按下面的原因改一下，重新提交。'
  if (a.status === 'NEED_SIGN') return '签约后就能收微信付款了。'
  return a.onlinePayEnabled
    ? '现在是测试环境，可以先收在线付款；正式上线前要开通，钱才能打到你自己的账户。'
    : '开通后，农户才能用微信付款给你。没开通前只能货到付款。'
})

async function load() {
  acc.value = await merchantAccount().catch(() => ({ status: 'NONE' }))
  if (!f.shortName || !f.mobile) {
    const st = await myStore().catch(() => null)
    if (st && st.name && !f.shortName) f.shortName = st.name
    if (st && !f.mobile && /^1\d{10}$/.test(st.phone || '')) f.mobile = st.phone
  }
}

onShow(load)

async function pickId(side) {
  const t = side === 'front' ? front : back
  let path
  try {
    ;[path] = await pickImages(1)
  } catch (e) {
    return
  }
  t.local = path
  t.id = ''
  t.uploading = true
  try {
    const r = await uploadOnboardingImage(path)
    t.id = r && r.imageId
  } catch (e) {
    t.local = ''
  } finally {
    t.uploading = false
  }
}

function onBank(e) {
  const i = Number(e.detail.value)
  if (i >= BANKS.length) {
    customBank.value = true
    f.bankName = ''
  } else {
    customBank.value = false
    f.bankName = BANKS[i]
  }
}

function onRegion(e) {
  const v = e.detail.value || []
  const codes = e.detail.code || []
  regionValue.value = v
  f.bankCityName = v.slice(0, 2).join(' ')
  f.bankCityCode = codes[1] || ''
  if (!f.bankCityCode) uni.showToast({ title: '请选到“市”', icon: 'none' })
}

function copySign() {
  uni.setClipboardData({
    data: acc.value.signUrl || '',
    success: () => uni.showToast({ title: '已复制，在微信里打开完成签约', icon: 'none', duration: 3000 }),
  })
}

function toast(t) {
  uni.showToast({ title: t, icon: 'none' })
  return false
}

function validate() {
  if (!front.id) return toast('请拍身份证人像面')
  if (!back.id) return toast('请拍身份证国徽面')
  if (!f.idName.trim()) return toast('请填姓名')
  f.idNumber = f.idNumber.trim().toUpperCase()
  if (!/^\d{17}[\dX]$/.test(f.idNumber)) return toast('身份证号是 18 位，最后一位可以是 X')
  if (!f.idValidFrom) return toast('请选身份证有效期开始日期')
  if (!f.longTerm && !f.idValidTo) return toast('请选有效期结束日期，或勾“长期”')
  if (!f.longTerm && f.idValidTo <= f.idValidFrom) return toast('结束日期要晚于开始日期')
  if (!f.bankName.trim()) return toast('请选开户银行')
  if (!/^\d{6}$/.test(f.bankCityCode)) return toast('请选开户城市')
  f.bankAccount = f.bankAccount.replace(/\s/g, '')
  if (!/^\d{12,19}$/.test(f.bankAccount)) return toast('银行卡号是 12–19 位数字')
  if (!/^1\d{10}$/.test(f.mobile)) return toast('手机号是 11 位')
  if (!f.shortName.trim()) return toast('请填商户简称')
  return true
}

async function submit() {
  if (!validate()) return
  saving.value = true
  try {
    await submitOnboarding({
      idCardFrontImageId: front.id,
      idCardBackImageId: back.id,
      idName: f.idName.trim(),
      idNumber: f.idNumber,
      idValidFrom: f.idValidFrom,
      idValidTo: f.longTerm ? '长期' : f.idValidTo,
      bankName: f.bankName.trim(),
      bankCityCode: f.bankCityCode,
      bankAccount: f.bankAccount,
      mobile: f.mobile,
      shortName: f.shortName.trim(),
    })
    // 提交后清掉敏感信息
    Object.assign(f, { idNumber: '', bankAccount: '' })
    uni.showToast({ title: '提交好了，等审核', icon: 'success' })
    uni.pageScrollTo({ scrollTop: 0, duration: 0 })
    load()
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.page { font-size: 32rpx; }
.status-card { background: $lm-green; color: #fff; padding: 36rpx 32rpx; }
.status-card.NONE, .status-card.REJECTED { background: $lm-red; }
.status-card.SUBMITTED, .status-card.AUDITING, .status-card.NEED_SIGN { background: $lm-wheat; }
.st-title { font-size: 42rpx; font-weight: 800; }
.st-sub { margin-top: 8rpx; opacity: 0.95; font-size: 30rpx; }
.st-info { margin-top: 12rpx; font-size: 28rpx; opacity: 0.9; }
.st-reason { margin-top: 12rpx; background: rgba(255, 255, 255, 0.18); border-radius: 12rpx; padding: 14rpx 18rpx; }
.big-t { font-size: 36rpx; font-weight: 800; margin-bottom: 8rpx; }
.url { word-break: break-all; color: $lm-ink-2; font-size: 26rpx; background: $lm-bg; padding: 12rpx 16rpx; border-radius: 12rpx; margin-top: 12rpx; }
.safe { margin: 20rpx 24rpx 0; background: $lm-green-light; color: $lm-green; border-radius: 16rpx; padding: 20rpx 24rpx; font-size: 28rpx; }
.step-title { font-size: 36rpx; font-weight: 800; margin: 36rpx 28rpx 12rpx; display: flex; align-items: center; }
.num {
  width: 48rpx; height: 48rpx; border-radius: 50%; background: $lm-green; color: #fff; font-size: 28rpx;
  display: inline-flex; align-items: center; justify-content: center; margin-right: 16rpx;
}
.idcards { padding: 0 16rpx; }
.idc { flex: 1; height: 220rpx; margin: 0 8rpx; border-radius: 20rpx; overflow: hidden; position: relative; background: #fff; }
.idimg { width: 100%; height: 100%; }
.idph {
  width: 100%; height: 100%; border: 4rpx dashed $lm-green; border-radius: 20rpx; box-sizing: border-box;
  display: flex; flex-direction: column; align-items: center; justify-content: center; color: $lm-green; font-weight: 700;
}
.cam { font-size: 64rpx; line-height: 1; }
.idmask, .idok {
  position: absolute; left: 0; right: 0; bottom: 0; text-align: center; color: #fff; font-size: 26rpx; padding: 8rpx 0;
  background: rgba(0, 0, 0, 0.55);
}
.idok { background: rgba(46, 107, 48, 0.85); }
.field { padding: 20rpx 0; border-bottom: 2rpx solid $lm-line; }
.field:last-child { border-bottom: none; }
.label { color: $lm-ink-2; font-size: 28rpx; margin-bottom: 8rpx; }
.input { height: 80rpx; font-size: 34rpx; }
.picker-v { height: 80rpx; line-height: 80rpx; font-size: 34rpx; }
.check {
  width: 44rpx; height: 44rpx; border-radius: 10rpx; border: 4rpx solid $lm-green; margin-right: 12rpx;
  color: #fff; text-align: center; line-height: 40rpx; font-weight: 800;
}
.check.on { background: $lm-green; }
</style>
