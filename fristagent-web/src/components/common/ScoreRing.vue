<script setup>
import { computed } from 'vue'

const props = defineProps({ score: { type: Number, default: 0 }, size: { type: Number, default: 48 } })

const r = computed(() => (props.size / 2) - 5)
const circumference = computed(() => 2 * Math.PI * r.value)
const offset = computed(() => circumference.value - (props.score / 100) * circumference.value)
const color = computed(() => {
  if (props.score >= 80) return '#10B981'
  if (props.score >= 60) return '#F59E0B'
  return '#EF4444'
})
</script>

<template>
  <div class="score-ring" :style="{ width: size + 'px', height: size + 'px' }">
    <svg :width="size" :height="size">
      <circle :cx="size/2" :cy="size/2" :r="r" fill="none" stroke="var(--border)" stroke-width="4" />
      <circle
        :cx="size/2" :cy="size/2" :r="r"
        fill="none" :stroke="color" stroke-width="4"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="offset"
        transform="rotate(-90, 0, 0)"
        :transform-origin="`${size/2} ${size/2}`"
        style="transition: stroke-dashoffset 0.5s ease"
      />
    </svg>
    <span class="score-label" :style="{ color }">{{ score }}</span>
  </div>
</template>

<style scoped>
.score-ring { position: relative; display: inline-flex; align-items: center; justify-content: center; }
.score-label {
  position: absolute;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
</style>
