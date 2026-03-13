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
  // taskId → 累积的 LLM 流式文本（SCAN_LOG chunks）
  const streamLog = ref({})

  useWebSocket(['SCAN_PROGRESS', 'SCAN_DONE', 'SCAN_FAILED'], (msg) => {
    const id = String(msg.taskId)
    progress.value[id] = {
      status:  msg.status,
      step:    msg.step    || '',
      percent: msg.percent ?? (msg.status === 'DONE' ? 100 : 0),
      score:   msg.score   ?? null,
      summary: msg.summary ?? '',
    }
    // 扫描结束时清空流式日志
    if (msg.status === 'DONE' || msg.status === 'FAILED') {
      delete streamLog.value[id]
    }
  })

  useWebSocket('SCAN_LOG', (msg) => {
    const id = String(msg.taskId)
    if (!streamLog.value[id]) streamLog.value[id] = ''
    streamLog.value[id] += msg.chunk
  })

  function getProgress(taskId) {
    return progress.value[String(taskId)] || null
  }

  function getStreamLog(taskId) {
    return streamLog.value[String(taskId)] || ''
  }

  return { progress, streamLog, getProgress, getStreamLog }
})
