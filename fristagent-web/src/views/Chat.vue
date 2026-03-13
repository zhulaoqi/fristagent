<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Send, Puzzle, ChevronDown } from 'lucide-vue-next'
import { useSkillStore } from '@/stores/skill'
import { chatApi } from '@/api'

const skillStore = useSkillStore()
const input = ref('')
const loading = ref(false)
const messagesEl = ref(null)
const showSkillMenu = ref(false)

// sessionId 用 localStorage 存储，跨刷新保持
const SESSION_KEY = 'fristagent_chat_session'
let sessionId = localStorage.getItem(SESSION_KEY)
if (!sessionId) {
  sessionId = 'session_' + Date.now()
  localStorage.setItem(SESSION_KEY, sessionId)
}

const messages = ref([
  {
    role: 'assistant',
    content: '你好！我是 FristAgent 智能助手。你可以问我关于历史扫描结果的问题，或者让我切换当前使用的 Skill。\n\n例如：\n• "最近一周哪个开发者提交的高危问题最多？"\n• "切换到 LangChain-CR-Pro"\n• "总结本周所有安全漏洞"',
  },
])

const builtinSkills = [
  'universal-code-reviewer',
  'langchain-cr-pro',
  'team-style-enforcer',
]

const scrollToBottom = async () => {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

// 加载历史记录
onMounted(async () => {
  try {
    const history = await chatApi.history(sessionId)
    if (history && history.length > 0) {
      messages.value = history.map(m => ({
        role: m.role,
        content: m.content,
        time: m.createdAt
      }))
    }
  } catch (e) {
    // 加载历史失败不影响新对话
  }
  skillStore.fetchSkills()
})

const send = async () => {
  const text = input.value.trim()
  if (!text || loading.value) return

  // Detect skill switch intent (handle locally without API)
  const switchMatch = builtinSkills.find(s => text.includes(s))
  if (switchMatch) {
    messages.value.push({ role: 'user', content: text, time: new Date().toISOString() })
    input.value = ''
    await skillStore.activate(switchMatch)
    messages.value.push({
      role: 'assistant',
      content: `已切换到 **${switchMatch}**，下次 PR 扫描将使用该 Skill。`,
      time: new Date().toISOString(),
    })
    await scrollToBottom()
    return
  }

  // 立即显示用户消息
  messages.value.push({ role: 'user', content: text, time: new Date().toISOString() })
  input.value = ''
  loading.value = true

  // 显示 typing 占位
  const typingId = Date.now()
  messages.value.push({ role: 'assistant', content: '...', typing: true, id: typingId })
  scrollToBottom()

  try {
    const res = await chatApi.send({ sessionId, message: text })
    // 移除 typing 占位，插入真实回复
    const idx = messages.value.findIndex(m => m.id === typingId)
    if (idx !== -1) {
      messages.value.splice(idx, 1, { role: 'assistant', content: res.reply, time: new Date().toISOString() })
    }
  } catch (e) {
    const idx = messages.value.findIndex(m => m.id === typingId)
    if (idx !== -1) messages.value.splice(idx, 1)
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

const quickSwitch = async (name) => {
  showSkillMenu.value = false
  await skillStore.activate(name)
  messages.value.push({ role: 'assistant', content: `已切换到 **${name}**`, time: new Date().toISOString() })
  await scrollToBottom()
}
</script>

<template>
  <div class="chat-page">
    <!-- Header -->
    <div class="chat-header">
      <div>
        <h1>智能对话</h1>
        <p>查询历史扫描、总结问题、切换 Skill</p>
      </div>
      <!-- Skill quick switch -->
      <div class="skill-switcher">
        <button class="skill-switch-btn" @click="showSkillMenu = !showSkillMenu">
          <Puzzle :size="14" />
          <span>{{ skillStore.activeSkill || '选择 Skill' }}</span>
          <ChevronDown :size="13" :style="{ transform: showSkillMenu ? 'rotate(180deg)' : '', transition: 'transform 180ms' }" />
        </button>
        <Transition name="dropdown">
          <div v-if="showSkillMenu" class="skill-menu">
            <button
              v-for="skill in builtinSkills"
              :key="skill"
              class="skill-menu-item"
              :class="{ active: skill === skillStore.activeSkill }"
              @click="quickSwitch(skill)"
            >
              <span class="skill-dot" :class="{ on: skill === skillStore.activeSkill }" />
              {{ skill }}
            </button>
          </div>
        </Transition>
      </div>
    </div>

    <!-- Messages -->
    <div ref="messagesEl" class="messages">
      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="message"
        :class="msg.role"
      >
        <div class="msg-avatar">{{ msg.role === 'assistant' ? '🤖' : '👤' }}</div>
        <div v-if="msg.typing" class="msg-bubble typing">
          <span /><span /><span />
        </div>
        <div v-else class="msg-bubble" v-html="renderMd(msg.content)" />
      </div>
    </div>

    <!-- Input -->
    <div class="chat-input-area">
      <div class="input-wrap">
        <textarea
          v-model="input"
          class="chat-input"
          placeholder="输入问题，或说「切换到 langchain-cr-pro」... (Shift+Enter 换行)"
          rows="1"
          @keydown="handleKeydown"
          @input="$event.target.style.height = 'auto'; $event.target.style.height = Math.min($event.target.scrollHeight, 120) + 'px'"
        />
        <button class="send-btn" :disabled="!input.trim() || loading" @click="send">
          <Send :size="16" />
        </button>
      </div>
    </div>
  </div>
</template>

<script>
// Simple markdown-like renderer (bold + newlines)
function renderMd(text) {
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
    .replace(/^• /gm, '• ')
}
</script>

<style scoped>
.chat-page { height: 100%; display: flex; flex-direction: column; }

.chat-header {
  padding: 20px 28px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.chat-header h1 { font-size: 18px; font-weight: 650; }
.chat-header p { font-size: 12.5px; color: var(--text-muted); margin-top: 2px; }

/* Skill switcher */
.skill-switcher { position: relative; }
.skill-switch-btn {
  display: flex; align-items: center; gap: 7px;
  padding: 7px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition: all var(--transition);
}
.skill-switch-btn:hover { border-color: var(--accent-blue); color: var(--accent-blue-2); }

.skill-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 6px;
  min-width: 220px;
  z-index: 100;
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
}
.skill-menu-item {
  display: flex; align-items: center; gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  font-family: var(--font-mono);
  transition: all var(--transition);
}
.skill-menu-item:hover { background: var(--bg-hover); color: var(--text-primary); }
.skill-menu-item.active { color: var(--accent-blue-2); }

.skill-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--border-light); flex-shrink: 0; }
.skill-dot.on { background: var(--accent-green); box-shadow: 0 0 6px var(--accent-green); }

/* Messages */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.message { display: flex; gap: 12px; align-items: flex-start; }
.message.user { flex-direction: row-reverse; }

.msg-avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 1px solid var(--border);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.msg-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-size: 13.5px;
  line-height: 1.7;
  border: 1px solid var(--border);
}

.message.assistant .msg-bubble {
  background: var(--bg-card);
  color: var(--text-primary);
  border-radius: 2px var(--radius-md) var(--radius-md) var(--radius-md);
}

.message.user .msg-bubble {
  background: rgba(37, 99, 235, 0.15);
  border-color: rgba(37, 99, 235, 0.3);
  color: var(--text-primary);
  border-radius: var(--radius-md) 2px var(--radius-md) var(--radius-md);
}

:deep(code) {
  background: var(--bg-secondary);
  padding: 1px 5px;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--accent-blue-2);
}

/* Typing */
.typing { display: flex; gap: 5px; align-items: center; padding: 14px 16px; }
.typing span {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--text-muted);
  animation: bounce 1.2s ease-in-out infinite;
}
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 80%, 100% { transform: translateY(0); } 40% { transform: translateY(-6px); } }

/* Input */
.chat-input-area {
  padding: 16px 28px 20px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}
.input-wrap {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  transition: border-color var(--transition);
}
.input-wrap:focus-within { border-color: var(--accent-blue); }

.chat-input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 13.5px;
  line-height: 1.6;
  resize: none;
  font-family: var(--font-sans);
  min-height: 24px;
  max-height: 120px;
}
.chat-input::placeholder { color: var(--text-muted); }

.send-btn {
  width: 34px; height: 34px;
  border-radius: var(--radius-sm);
  border: none;
  background: var(--accent-blue);
  color: white;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: background var(--transition), opacity var(--transition);
}
.send-btn:hover:not(:disabled) { background: var(--accent-blue-2); }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* Transitions */
.dropdown-enter-active { transition: opacity 150ms, transform 150ms; }
.dropdown-leave-active { transition: opacity 100ms, transform 100ms; }
.dropdown-enter-from, .dropdown-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
