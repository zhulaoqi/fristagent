<script setup>
const props = defineProps({ status: String })

const config = {
  PENDING:  { label: '待扫描', color: '#4A5878', bg: 'rgba(74,88,120,0.15)' },
  SCANNING: { label: '扫描中', color: '#F59E0B', bg: 'rgba(245,158,11,0.12)' },
  DONE:     { label: '已完成', color: '#10B981', bg: 'rgba(16,185,129,0.12)' },
  FAILED:   { label: '失败',   color: '#EF4444', bg: 'rgba(239,68,68,0.12)'  },
}

const c = config[props.status] || config.PENDING
</script>

<template>
  <span class="badge" :style="{ color: c.color, background: c.bg }">
    <span v-if="status === 'SCANNING'" class="pulse" />
    {{ c.label }}
  </span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}
</style>
