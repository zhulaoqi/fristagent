import { ref, onUnmounted } from 'vue'

const WS_URL = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`

// 全局单例 WebSocket（整个应用共享一条连接）
let socket = null
const listeners = new Map()   // type → Set<callback>
const connected = ref(false)

function getOrConnect() {
  if (socket && socket.readyState <= WebSocket.OPEN) return

  socket = new WebSocket(
    import.meta.env.DEV
      ? `ws://localhost:8080/ws`   // 开发时直连后端
      : WS_URL
  )

  socket.onopen = () => {
    connected.value = true
    console.log('[WS] connected')
  }

  socket.onclose = () => {
    connected.value = false
    socket = null
    // 断线自动重连，间隔 3s
    setTimeout(getOrConnect, 3000)
  }

  socket.onerror = () => {
    socket?.close()
  }

  socket.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data)
      const handlers = listeners.get(msg.type) || new Set()
      handlers.forEach(fn => fn(msg))
      // 通配符订阅
      const allHandlers = listeners.get('*') || new Set()
      allHandlers.forEach(fn => fn(msg))
    } catch (e) {
      console.warn('[WS] parse error', e)
    }
  }
}

/**
 * 在组件中使用 WebSocket 订阅
 * @param {string|string[]} types  消息类型，'*' 表示全部
 * @param {Function} callback
 */
export function useWebSocket(types, callback) {
  getOrConnect()

  const typeList = Array.isArray(types) ? types : [types]

  typeList.forEach(type => {
    if (!listeners.has(type)) listeners.set(type, new Set())
    listeners.get(type).add(callback)
  })

  onUnmounted(() => {
    typeList.forEach(type => listeners.get(type)?.delete(callback))
  })

  return { connected }
}
