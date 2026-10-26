<script setup>
import { computed, ref } from 'vue'

/**
 * 单序列柱状图（纯 SVG），带悬停提示。
 * data: [{ label, value }]；format 用于格式化数值。
 */
const props = defineProps({
  data: { type: Array, default: () => [] },
  color: { type: String, default: '#2a78d6' },
  format: { type: Function, default: (v) => String(v) },
  height: { type: Number, default: 200 }
})

const W = 640
const PAD = { top: 16, right: 8, bottom: 26, left: 48 }
const hover = ref(-1)

const plotW = W - PAD.left - PAD.right
const plotH = computed(() => props.height - PAD.top - PAD.bottom)

/** 取一个好看的刻度上限 */
const ticks = computed(() => {
  const max = Math.max(0, ...props.data.map((d) => Number(d.value) || 0))
  if (max === 0) return [0, 1]
  const raw = max / 4
  const mag = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => s >= raw)
  const top = Math.ceil(max / step) * step
  const out = []
  for (let v = 0; v <= top + step / 2; v += step) out.push(+v.toFixed(6))
  return out
})
const yMax = computed(() => ticks.value[ticks.value.length - 1] || 1)
const y = (v) => PAD.top + plotH.value - (v / yMax.value) * plotH.value

const band = computed(() => plotW / Math.max(props.data.length, 1))
const barW = computed(() => Math.min(28, band.value * 0.6))

const bars = computed(() =>
  props.data.map((d, i) => {
    const v = Number(d.value) || 0
    const x = PAD.left + band.value * i + (band.value - barW.value) / 2
    const top = y(v)
    const h = PAD.top + plotH.value - top
    return { ...d, v, x, top, h, cx: x + barW.value / 2 }
  })
)

/** 圆角只在顶端：用 path 画 */
function barPath(b) {
  const r = Math.min(4, b.h, barW.value / 2)
  const x0 = b.x
  const x1 = b.x + barW.value
  const yb = b.top + b.h
  if (b.h <= 0) return ''
  return `M${x0},${yb} V${b.top + r} Q${x0},${b.top} ${x0 + r},${b.top} H${x1 - r} Q${x1},${b.top} ${x1},${b.top + r} V${yb} Z`
}

const hovered = computed(() => (hover.value >= 0 ? bars.value[hover.value] : null))
const tipLeft = computed(() => (hovered.value ? `${(hovered.value.cx / W) * 100}%` : '0'))
</script>

<template>
  <div class="trend" @mouseleave="hover = -1">
    <svg :viewBox="`0 0 ${W} ${height}`" preserveAspectRatio="none" :style="{ height: `${height}px` }" role="img">
      <g class="grid">
        <g v-for="t in ticks" :key="t">
          <line :x1="PAD.left" :x2="W - PAD.right" :y1="y(t)" :y2="y(t)" />
          <text :x="PAD.left - 8" :y="y(t)" text-anchor="end" dominant-baseline="middle">{{ format(t) }}</text>
        </g>
      </g>
      <g>
        <rect
          v-if="hovered"
          :x="PAD.left + band * hover"
          :y="PAD.top"
          :width="band"
          :height="plotH"
          class="hover-band"
        />
        <path v-for="(b, i) in bars" :key="b.label" :d="barPath(b)" :fill="color" :opacity="hover === -1 || hover === i ? 1 : 0.45" />
      </g>
      <g class="x-labels">
        <text
          v-for="(b, i) in bars"
          :key="b.label"
          :x="b.cx"
          :y="height - 8"
          text-anchor="middle"
          :class="{ strong: hover === i }"
        >{{ i % 2 === 0 || hover === i || bars.length <= 8 ? b.label : '' }}</text>
      </g>
      <rect
        v-for="(b, i) in bars"
        :key="`hit-${b.label}`"
        :x="PAD.left + band * i"
        :y="0"
        :width="band"
        :height="height"
        fill="transparent"
        @mouseenter="hover = i"
      />
    </svg>
    <div v-if="hovered" class="tip" :style="{ left: tipLeft }">
      <div class="tip-label">{{ hovered.full || hovered.label }}</div>
      <div class="tip-value"><i :style="{ background: color }" />{{ format(hovered.v) }}</div>
    </div>
  </div>
</template>

<style scoped>
.trend {
  position: relative;
  width: 100%;
}
svg {
  width: 100%;
  display: block;
  overflow: visible;
}
.grid line {
  stroke: var(--el-border-color-lighter);
  stroke-width: 1;
}
.grid text,
.x-labels text {
  font-size: 11px;
  fill: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.x-labels text.strong {
  fill: var(--el-text-color-primary);
  font-weight: 600;
}
.hover-band {
  fill: var(--el-fill-color-light);
}
.tip {
  position: absolute;
  top: 4px;
  transform: translateX(-50%);
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.tip-label {
  color: var(--el-text-color-secondary);
}
.tip-value {
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}
.tip-value i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  display: inline-block;
}
</style>
