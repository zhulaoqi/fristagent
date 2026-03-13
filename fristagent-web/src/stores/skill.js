import { defineStore } from 'pinia'
import { ref } from 'vue'
import { skillApi } from '@/api'
import { useWebSocket } from '@/composables/useWebSocket'
import { ElMessage } from 'element-plus'

export const useSkillStore = defineStore('skill', () => {
  const skills = ref([])
  const activeSkill = ref('')
  const loading = ref(false)

  async function fetchSkills() {
    skills.value = await skillApi.list()
  }

  async function fetchActive() {
    const res = await skillApi.getActive()
    activeSkill.value = res.name
  }

  async function activate(name) {
    loading.value = true
    try {
      await skillApi.activate(name)
      // 乐观更新，WebSocket 会二次确认
      activeSkill.value = name
      skills.value.forEach(s => { s.isActive = s.name === name })
      ElMessage.success(`已切换到 ${name}`)
    } finally {
      loading.value = false
    }
  }

  // 监听后端 SKILL_SWITCHED 广播，保持多端一致
  useWebSocket('SKILL_SWITCHED', (msg) => {
    activeSkill.value = msg.skillName
    skills.value.forEach(s => { s.isActive = s.name === msg.skillName })
  })

  return { skills, activeSkill, loading, fetchSkills, fetchActive, activate }
})
