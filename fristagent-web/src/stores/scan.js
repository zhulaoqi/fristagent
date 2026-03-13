import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'

/**
 * 扫描任务实时状态 store
 * 保存各 taskId 的实时进度，供 ScanList / ScanDetail 消费
 */
export const useScanStore = defineStore('scan', () => {
  // taskId → { status, step, percent, score, summary }
  const progress = ref({})

  useWebSocket(['SCAN_PROGRESS', 'SCAN_DONE', 'SCAN_FAILED'], (msg) => {
    const id = String(msg.taskId)
    progress.value[id] = {
      status:  msg.status,
      step:    msg.step    || '',
      percent: msg.percent ?? (msg.status === 'DONE' ? 100 : 0),
      score:   msg.score   ?? null,
      summary: msg.summary ?? '',
    }
  })

  function getProgress(taskId) {
    return progress.value[String(taskId)] || null
  }

  return { progress, getProgress }
})
