<script setup>
import { computed } from 'vue'
import { useRoute, RouterLink, RouterView } from 'vue-router'
import {
  LayoutDashboard, GitPullRequest, MessageSquare,
  GitBranch, Cpu, Bell, Puzzle,
  Zap,
  Moon, Sun, Monitor
} from 'lucide-vue-next'
import { useSkillStore } from '@/stores/skill'
import { useTheme } from '@/composables/useTheme'

const route = useRoute()
const skillStore = useSkillStore()

const navItems = [
  { label: 'Dashboard', icon: LayoutDashboard, to: '/dashboard' },
  { label: 'PR 扫描', icon: GitPullRequest, to: '/scans' },
  { label: '智能对话', icon: MessageSquare, to: '/chat' },
]

const configItems = [
  { label: '仓库配置', icon: GitBranch, to: '/config/repos' },
  { label: 'Skill 管理', icon: Puzzle, to: '/config/skills' },
  { label: '模型配置', icon: Cpu, to: '/config/model' },
  { label: '通知配置', icon: Bell, to: '/config/notify' },
]

const isActive = (to) => route.path.startsWith(to)
const isConfigRoute = computed(() => route.path.startsWith('/config'))

skillStore.fetchActive()

const { theme, setTheme } = useTheme()

const themeOptions = [
  { value: 'dark',   label: 'Dark',   icon: Moon },
  { value: 'system', label: '跟随系统', icon: Monitor },
  { value: 'light',  label: 'Light',  icon: Sun },
]

const currentThemeIcon = computed(() =>
  themeOptions.find(o => o.value === theme.value)?.icon ?? Moon
)

function cycleTheme() {
  const idx = themeOptions.findIndex(o => o.value === theme.value)
  setTheme(themeOptions[(idx + 1) % themeOptions.length].value)
}
</script>

<template>
  <div class="app-shell">
    <!-- Top Navigation Bar -->
    <header class="top-nav">
      <div class="nav-logo">
        <div class="logo-icon">
          <Zap :size="16" color="#2563EB" />
        </div>
        <span class="logo-text">FristAgent</span>
      </div>
      <div class="nav-sep"></div>
      <nav class="nav-links">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ active: isActive(item.to) }"
        >
          {{ item.label }}
        </RouterLink>
        <RouterLink
          to="/config/repos"
          class="nav-link"
          :class="{ active: isConfigRoute }"
        >配置</RouterLink>
      </nav>
      <div class="nav-right">
        <div v-if="skillStore.activeSkill" class="skill-pill">
          <span class="skill-dot" />
          <span class="skill-name">{{ skillStore.activeSkill }}</span>
        </div>
        <button class="theme-icon-btn" @click="cycleTheme">
          <component :is="currentThemeIcon" :size="14" />
        </button>
      </div>
    </header>

    <!-- Secondary Config Navigation (only on /config/* routes) -->
    <nav v-if="isConfigRoute" class="sub-nav">
      <RouterLink
        v-for="item in configItems"
        :key="item.to"
        :to="item.to"
        class="sub-link"
        :class="{ active: route.path === item.to }"
      >
        {{ item.label }}
      </RouterLink>
    </nav>

    <!-- Main content -->
    <main class="main-content">
      <div class="content-wrap">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
/* ── Shell ── */
.app-shell {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
}

/* ── Top Navigation Bar ── */
.top-nav {
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.logo-icon {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: rgba(37, 99, 235, 0.12);
  border: 1px solid rgba(37, 99, 235, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

.nav-sep {
  width: 1px;
  height: 16px;
  background: var(--border);
  margin: 0 12px;
  flex-shrink: 0;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-link {
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 450;
  color: var(--text-secondary);
  text-decoration: none;
  white-space: nowrap;
  transition: background var(--transition), color var(--transition);
}

.nav-link:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.nav-link.active {
  background: rgba(37, 99, 235, 0.14);
  color: var(--accent-blue-2);
  font-weight: 500;
}

.nav-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 10px;
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.skill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent-green);
  flex-shrink: 0;
  box-shadow: 0 0 6px var(--accent-green);
}

.skill-name {
  font-size: 11px;
  color: var(--accent-green);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

.theme-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 7px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition);
}

.theme-icon-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

/* ── Secondary Config Nav ── */
.sub-nav {
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: stretch;
  padding: 0 16px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
}

.sub-link {
  display: flex;
  align-items: center;
  padding: 0 14px;
  font-size: 13px;
  color: var(--text-muted);
  text-decoration: none;
  white-space: nowrap;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color var(--transition), border-color var(--transition);
}

.sub-link:hover {
  color: var(--text-secondary);
}

.sub-link.active {
  color: var(--text-primary);
  border-bottom-color: var(--accent-blue);
}

/* ── Main Content ── */
.main-content {
  flex: 1;
  overflow-y: auto;
  background: var(--bg-base);
}

.content-wrap {
  width: 100%;
  height: 100%;
}

</style>
