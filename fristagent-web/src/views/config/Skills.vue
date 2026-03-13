<script setup>
import { ref, onMounted } from 'vue'
import { Puzzle, Check, Download, Trash2, Plus } from 'lucide-vue-next'
import { useSkillStore } from '@/stores/skill'
import { skillApi } from '@/api'
import { ElMessageBox, ElMessage } from 'element-plus'

const skillStore = useSkillStore()
const showInstall = ref(false)
const installForm = ref({ githubUrl: '' })
const installing = ref(false)

const skills = ref([])

onMounted(async () => {
  try {
    skills.value = await skillApi.list()
  } catch (e) {
    // 错误已在 axios 拦截器显示
  }
  skillStore.fetchActive()
})

const activate = async (name) => {
  await skillStore.activate(name)
  skills.value.forEach(s => { s.isActive = s.name === name })
}

const uninstall = async (skill) => {
  await ElMessageBox.confirm(`确定卸载 ${skill.displayName}？`, '卸载 Skill', {
    confirmButtonText: '卸载',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await skillApi.uninstall(skill.name)
  skills.value = skills.value.filter(s => s.name !== skill.name)
  ElMessage.success('已卸载')
}

const doInstall = async () => {
  if (!installForm.value.githubUrl.trim()) return
  installing.value = true
  try {
    const newSkill = await skillApi.install({ githubUrl: installForm.value.githubUrl })
    skills.value.push(newSkill)
    showInstall.value = false
    installForm.value.githubUrl = ''
    ElMessage.success(`Skill "${newSkill.displayName || newSkill.name}" 安装成功`)
  } catch (e) {
    // 错误已在 axios 拦截器显示
  } finally {
    installing.value = false
  }
}
</script>

<template>
  <div class="skills-page">
    <div class="page-header">
      <div>
        <h1>Skill 管理</h1>
        <p>热插拔 · 当前激活：<span class="active-name">{{ skillStore.activeSkill }}</span></p>
      </div>
      <el-button type="primary" :icon="Plus" @click="showInstall = true">安装 Skill</el-button>
    </div>

    <div class="skill-grid">
      <div
        v-for="skill in skills"
        :key="skill.name"
        class="skill-card"
        :class="{ active: skill.isActive }"
      >
        <div class="skill-card-header">
          <div class="skill-icon" :class="{ active: skill.isActive }">
            <Puzzle :size="20" />
          </div>
          <div class="skill-badges">
            <span class="type-badge" :class="skill.skillType.toLowerCase()">{{ skill.skillType === 'BUILTIN' ? '内置' : '自定义' }}</span>
            <span class="version">v{{ skill.version }}</span>
          </div>
          <div v-if="skill.isActive" class="active-indicator">
            <span class="active-dot" />
            激活中
          </div>
        </div>

        <div class="skill-name">{{ skill.displayName }}</div>
        <div class="skill-code">{{ skill.name }}</div>
        <p class="skill-desc">{{ skill.description }}</p>

        <div class="skill-actions">
          <button
            class="action-btn primary"
            :disabled="skill.isActive || skillStore.loading"
            @click="activate(skill.name)"
          >
            <Check :size="14" />
            {{ skill.isActive ? '已激活' : '激活' }}
          </button>

          <button
            v-if="skill.skillType === 'CUSTOM'"
            class="action-btn danger"
            :disabled="skill.isActive"
            @click="uninstall(skill)"
          >
            <Trash2 :size="14" />
            卸载
          </button>
        </div>
      </div>
    </div>

    <!-- Install dialog -->
    <el-dialog v-model="showInstall" title="安装自定义 Skill" width="480px" :close-on-click-modal="false">
      <div class="install-form">
        <label>GitHub 仓库 URL</label>
        <el-input
          v-model="installForm.githubUrl"
          placeholder="https://github.com/owner/repo-name"
          size="large"
        />
        <p class="install-hint">
          将从该仓库的 main 分支下载 <code>skill.yaml</code> 和 <code>system-prompt.md</code>
        </p>
      </div>
      <template #footer>
        <el-button @click="showInstall = false">取消</el-button>
        <el-button type="primary" :loading="installing" :disabled="!installForm.githubUrl.trim()" @click="doInstall">
          <Download :size="14" style="margin-right:5px" />安装
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.skills-page { padding: 28px; }

.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 650; }
.page-header p { font-size: 13px; color: var(--text-muted); margin-top: 3px; }
.active-name { color: var(--accent-green); font-family: var(--font-mono); font-size: 12px; }

.skill-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }

.skill-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color var(--transition), box-shadow var(--transition);
}
.skill-card:hover { border-color: var(--border-light); }
.skill-card.active {
  border-color: rgba(37, 99, 235, 0.4);
  box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.15), var(--shadow-glow);
}

.skill-card-header { display: flex; align-items: center; gap: 10px; }

.skill-icon {
  width: 40px; height: 40px;
  border-radius: var(--radius-md);
  background: var(--bg-hover);
  border: 1px solid var(--border);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-muted);
  flex-shrink: 0;
}
.skill-icon.active { background: rgba(37,99,235,0.12); border-color: rgba(37,99,235,0.3); color: var(--accent-blue-2); }

.skill-badges { display: flex; gap: 6px; align-items: center; }
.type-badge {
  font-size: 10px; font-weight: 600;
  padding: 2px 7px;
  border-radius: 4px;
}
.type-badge.builtin { background: rgba(59,130,246,0.12); color: var(--accent-blue-2); }
.type-badge.custom { background: rgba(139,92,246,0.12); color: var(--accent-purple); }
.version { font-size: 11px; color: var(--text-muted); font-family: var(--font-mono); }

.active-indicator {
  margin-left: auto;
  display: flex; align-items: center; gap: 5px;
  font-size: 12px;
  color: var(--accent-green);
  font-weight: 500;
}
.active-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--accent-green);
  box-shadow: 0 0 6px var(--accent-green);
}

.skill-name { font-size: 16px; font-weight: 650; color: var(--text-primary); }
.skill-code { font-size: 12px; color: var(--text-muted); font-family: var(--font-mono); margin-top: -6px; }
.skill-desc { font-size: 12.5px; color: var(--text-secondary); line-height: 1.65; flex: 1; }

.skill-actions { display: flex; gap: 8px; margin-top: 4px; }

.action-btn {
  display: flex; align-items: center; gap: 5px;
  padding: 7px 14px;
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid;
  transition: all var(--transition);
}
.action-btn.primary {
  background: rgba(37,99,235,0.12);
  border-color: rgba(37,99,235,0.3);
  color: var(--accent-blue-2);
}
.action-btn.primary:hover:not(:disabled) {
  background: rgba(37,99,235,0.2);
}
.action-btn.primary:disabled {
  background: rgba(37,99,235,0.06);
  border-color: rgba(37,99,235,0.15);
  color: var(--accent-blue);
  cursor: not-allowed;
}
.action-btn.danger {
  background: rgba(239,68,68,0.08);
  border-color: rgba(239,68,68,0.2);
  color: var(--accent-red);
}
.action-btn.danger:hover:not(:disabled) { background: rgba(239,68,68,0.15); }
.action-btn.danger:disabled { opacity: 0.4; cursor: not-allowed; }

/* Install form */
.install-form { display: flex; flex-direction: column; gap: 10px; }
.install-form label { font-size: 13px; color: var(--text-secondary); }
.install-hint { font-size: 12px; color: var(--text-muted); }
.install-hint code { background: var(--bg-secondary); padding: 1px 5px; border-radius: 3px; font-family: var(--font-mono); }
</style>
