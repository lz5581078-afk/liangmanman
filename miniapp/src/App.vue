<script setup>
import { onLaunch } from '@dcloudio/uni-app'
import { ensureLogin } from './api/request'
import { refreshLocation } from './store/location'
import { preloadSubscribeConfig } from './utils/subscribe'

onLaunch(() => {
  // 后台静默登录和定位，失败不打扰用户；需要时页面会再试
  ensureLogin().catch(() => {})
  refreshLocation().catch(() => {})
  preloadSubscribeConfig()
})
</script>

<style lang="scss">
page {
  background: $lm-bg;
  color: $lm-ink;
  font-size: 30rpx;
  line-height: 1.5;
  font-family: -apple-system, "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
}

.card {
  background: $lm-card;
  border-radius: $lm-radius;
  margin: 20rpx 24rpx;
  padding: 28rpx;
}

.row { display: flex; align-items: center; }
.between { justify-content: space-between; }
.grow { flex: 1; min-width: 0; }
.muted { color: $lm-ink-3; font-size: 26rpx; }
.sub { color: $lm-ink-2; font-size: 26rpx; }
.price { color: $lm-wheat; font-weight: 700; }
.price::before { content: "¥"; font-size: 0.75em; margin-right: 2rpx; }
.ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }

.tag {
  display: inline-block;
  font-size: 22rpx;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  margin-right: 10rpx;
  background: $lm-green-light;
  color: $lm-green;
}
.tag.wheat { background: $lm-wheat-light; color: $lm-wheat; }
.tag.gray { background: #EEEBE4; color: $lm-ink-2; }

/* 按钮：商家端用大号，买家端用中号 */
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  font-weight: 600;
  height: 88rpx;
  padding: 0 40rpx;
  font-size: 32rpx;
  background: $lm-green;
  color: #fff;
  border: none;
  line-height: 1;
}
.btn::after { border: none; }
.btn.big { height: 104rpx; font-size: 36rpx; }
.btn.small { height: 64rpx; font-size: 28rpx; padding: 0 28rpx; }
.btn.ghost { background: #fff; color: $lm-green; border: 2rpx solid $lm-green; }
.btn.warn { background: #fff; color: $lm-red; border: 2rpx solid $lm-red; }
.btn.wheat { background: $lm-wheat; }
.btn[disabled] { opacity: 0.45; }

.empty {
  text-align: center;
  color: $lm-ink-3;
  padding: 120rpx 40rpx;
  font-size: 28rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  margin: 32rpx 24rpx 8rpx;
}

/* 底部固定操作条 */
.footer-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  z-index: 10;
}
.footer-space { height: calc(140rpx + env(safe-area-inset-bottom)); }
</style>
