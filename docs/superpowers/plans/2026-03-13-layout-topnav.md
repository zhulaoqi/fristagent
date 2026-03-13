# Layout Redesign: Top Navigation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将左侧固定侧边栏替换为顶部横向导航栏，配置区增加二级导航，解决左重右空的布局失衡问题。

**Architecture:** AppLayout.vue 从 row 方向 flex（sidebar + main）改为 column 方向 flex（top-nav + sub-nav + main），侧边栏全部移除；Dashboard 图表行从 2 列扩展为 3 列，新增扫描状态分布图；配置页移除 max-width 约束。

**Tech Stack:** Vue 3, Vite, Element Plus, ECharts (vue-echarts), Lucide Vue Next

---

## Chunk 1: AppLayout.vue 重构

### Task 1: 清理 CSS 变量

**Files:**
- Modify: `fristagent-web/src/styles/variables.css`

- [ ] **Step 1: 删除已废弃的侧边栏变量**

在 `variables.css` 中找到并删除以下两行（在 `:root` 块 `/* Sidebar */` 注释下）：

```css
/* 删除这两行 */
--sidebar-width: 220px;
--sidebar-collapsed: 64px;
```

同时删除 `/* Sidebar */` 注释行。

- [ ] **Step 2: 确认无其他文件引用这两个变量**

在终端运行：
```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
grep -r "sidebar-width\|sidebar-collapsed" src/
```

预期输出：无结果（空）。

- [ ] **Step 3: 构建验证**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build 2>&1 | tail -20
```

预期：无 error，出现 `✓ built in` 字样。

- [ ] **Step 4: Commit**

```bash
git -C /Users/zhujinqi/Documents/learn/AI/fristagent add fristagent-web/src/styles/variables.css
git -C /Users/zhujinqi/Documents/learn/AI/fristagent commit -m "style: remove unused sidebar CSS variables"
```

---

### Task 2: 重写 AppLayout.vue — Script

**Files:**
- Modify: `fristagent-web/src/components/AppLayout.vue`

- [ ] **Step 1: 更新 `<script setup>` 的 import 和逻辑**

将 `<script setup>` 全部替换为以下内容（保留所有原有功能，移除 `collapsed` 和折叠相关逻辑）：

```javascript
<script setup>
import { ref, computed } from 'vue'
import { useRoute, RouterLink, RouterView } from 'vue-router'
import {
  LayoutDashboard, GitPullRequest, MessageSquare,
  Settings, GitBranch, Cpu, Bell, Puzzle,
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
```

注意：`ChevronLeft`、`ChevronRight`、`collapsed` ref 均已移除。`Settings` 图标导入可视实际使用情况保留或删除（当前 configItems 中各项使用独立图标，`Settings` 未使用则删除）。

- [ ] **Step 2: 构建确认无语法错误**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build 2>&1 | grep -E "error|Error|✓"
```

预期：无 error，出现 `✓ built in`。

---

### Task 3: 重写 AppLayout.vue — Template

**Files:**
- Modify: `fristagent-web/src/components/AppLayout.vue`

- [ ] **Step 1: 替换 `<template>` 内容**

将整个 `<template>` 替换为：

```html
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
        <span
          class="nav-link"
          :class="{ active: isConfigRoute }"
          style="cursor: default"
        >配置</span>
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
```

- [ ] **Step 2: 构建确认**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build 2>&1 | grep -E "error|Error|✓"
```

预期：无 error。

---

### Task 4: 重写 AppLayout.vue — CSS

**Files:**
- Modify: `fristagent-web/src/components/AppLayout.vue`

- [ ] **Step 1: 替换 `<style scoped>` 内容**

将整个 `<style scoped>` 替换为：

```css
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

/* ── Transitions ── */
.fade-enter-active { transition: opacity 150ms ease; }
.fade-leave-active { transition: opacity 100ms ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
```

- [ ] **Step 2: 构建验证**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build 2>&1 | grep -E "error|Error|✓"
```

预期：无 error，`✓ built in`。

- [ ] **Step 3: 启动 dev server 进行视觉验证**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run dev
```

打开浏览器访问 `http://localhost:5173`，检查：
- [ ] 顶部导航栏显示 Logo + FristAgent + 4 个导航项
- [ ] 右侧显示 ActiveSkill pill 和主题切换按钮
- [ ] 点击「配置」→ 出现二级导航栏（仓库/Skill/模型/通知）
- [ ] 切换页面时活跃导航项正确高亮
- [ ] 左侧无侧边栏，内容区占满全宽

- [ ] **Step 4: Commit**

```bash
git -C /Users/zhujinqi/Documents/learn/AI/fristagent add fristagent-web/src/components/AppLayout.vue
git -C /Users/zhujinqi/Documents/learn/AI/fristagent commit -m "feat: replace sidebar with top navigation bar"
```

---

## Chunk 2: Dashboard + Config 页面更新

### Task 5: Dashboard.vue — 添加扫描状态分布图

**Files:**
- Modify: `fristagent-web/src/views/Dashboard.vue`

- [ ] **Step 1: 在 script 中注册 BarChart（确认已存在）并添加 `statusCountOption` computed**

在 `use([LineChart, BarChart, ...])` 之后，在 `issueTypeOption` computed 之后添加：

```javascript
const statusCountOption = computed(() => {
  const counts = statsData.value.statusCounts || {}
  const categories = ['FAILED', 'SCANNING', 'PENDING', 'DONE']
  const colors = {
    DONE:     '#10B981',
    SCANNING: '#3B82F6',
    PENDING:  '#4A5878',
    FAILED:   '#EF4444',
  }
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: '#161B2B', borderColor: '#1E2D44', textStyle: { color: '#E8EDF5' } },
    grid: { left: 12, right: 20, top: 10, bottom: 0, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#1E2D44', type: 'dashed' } },
      axisLabel: { color: '#4A5878', fontSize: 11 },
    },
    yAxis: {
      type: 'category',
      data: categories,
      axisLine: { lineStyle: { color: '#1E2D44' } },
      axisTick: { show: false },
      axisLabel: { color: '#4A5878', fontSize: 11 },
    },
    series: [{
      type: 'bar',
      data: categories.map(k => ({
        value: counts[k] ?? 0,
        itemStyle: { color: colors[k], borderRadius: [0, 3, 3, 0] },
      })),
      barMaxWidth: 20,
    }],
  }
})
```

- [ ] **Step 2: 在 template 中更新图表行**

找到：
```html
<div class="charts-row">
  <div class="card chart-card">
    <div class="card-title">评分趋势（7天）</div>
    <VChart :option="scoreTrendOption" style="height: 180px" autoresize :key="JSON.stringify(statsData.scoreTrend)" />
  </div>
  <div class="card chart-card">
    <div class="card-title">问题类型分布</div>
    <VChart :option="issueTypeOption" style="height: 180px" autoresize :key="JSON.stringify(statsData.issueTypeCounts)" />
  </div>
</div>
```

替换为：
```html
<div class="charts-row">
  <div class="card chart-card">
    <div class="card-title">评分趋势（7天）</div>
    <VChart :option="scoreTrendOption" style="height: 180px" autoresize :key="JSON.stringify(statsData.scoreTrend)" />
  </div>
  <div class="card chart-card">
    <div class="card-title">问题类型分布</div>
    <VChart :option="issueTypeOption" style="height: 180px" autoresize :key="JSON.stringify(statsData.issueTypeCounts)" />
  </div>
  <div class="card chart-card">
    <div class="card-title">扫描状态分布</div>
    <VChart :option="statusCountOption" style="height: 180px" autoresize :key="JSON.stringify(statsData.statusCounts)" />
  </div>
</div>
```

- [ ] **Step 3: 更新 `.charts-row` CSS**

找到：
```css
.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
```

替换为：
```css
.charts-row { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 14px; }
```

- [ ] **Step 4: 构建 + 视觉验证**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build 2>&1 | grep -E "error|Error|✓"
```

预期：无 error。在 dev server 访问 Dashboard 页面，确认：
- [ ] 图表行显示 3 个卡片
- [ ] 第一个（评分趋势）比后两个更宽
- [ ] 第三个（扫描状态分布）显示水平条形图

- [ ] **Step 5: Commit**

```bash
git -C /Users/zhujinqi/Documents/learn/AI/fristagent add fristagent-web/src/views/Dashboard.vue
git -C /Users/zhujinqi/Documents/learn/AI/fristagent commit -m "feat: add status distribution chart to dashboard, expand to 3-column grid"
```

---

### Task 6: config/Model.vue — 移除 max-width

**Files:**
- Modify: `fristagent-web/src/views/config/Model.vue`

- [ ] **Step 1: 更新 CSS**

找到并修改：
```css
/* 原 */
.page-header { margin-bottom: 24px; max-width: 960px; }

/* 改为 */
.page-header { margin-bottom: 24px; }
```

找到并修改：
```css
/* 原 */
.content-grid { display: grid; grid-template-columns: 1fr 280px; gap: 20px; align-items: start; max-width: 960px; }

/* 改为 */
.content-grid { display: grid; grid-template-columns: 1fr 280px; gap: 20px; align-items: start; max-width: 1400px; }
```

- [ ] **Step 2: 构建验证**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build 2>&1 | grep -E "error|Error|✓"
```

- [ ] **Step 3: Commit**

```bash
git -C /Users/zhujinqi/Documents/learn/AI/fristagent add fristagent-web/src/views/config/Model.vue
git -C /Users/zhujinqi/Documents/learn/AI/fristagent commit -m "style: remove 960px max-width constraint in model config page"
```

---

### Task 7: config/Notify.vue — 移除 max-width

**Files:**
- Modify: `fristagent-web/src/views/config/Notify.vue`

- [ ] **Step 1: 更新 CSS**

找到并修改（三处，均在 `<style scoped>` 中）：

```css
/* 原 */
.page-header { margin-bottom: 24px; max-width: 960px; }
/* 改为 */
.page-header { margin-bottom: 24px; }

/* 原 */
.notify-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; max-width: 960px; }
/* 改为 */
.notify-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; max-width: 1400px; }

/* 原 */
.save-row { display: flex; justify-content: flex-end; margin-top: 20px; max-width: 960px; }
/* 改为 */
.save-row { display: flex; justify-content: flex-end; margin-top: 20px; max-width: 1400px; }
```

- [ ] **Step 2: 构建验证**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build 2>&1 | grep -E "error|Error|✓"
```

- [ ] **Step 3: 视觉验证**

在 dev server 访问 `/config/notify`，确认：
- [ ] 顶导「配置」高亮，二级导航「通知配置」高亮
- [ ] 两个卡片（飞书 + 邮件）宽度延伸超过原来的 960px 上限
- [ ] 底部保存按钮行与上方 grid 右边对齐

- [ ] **Step 4: Commit**

```bash
git -C /Users/zhujinqi/Documents/learn/AI/fristagent add fristagent-web/src/views/config/Notify.vue
git -C /Users/zhujinqi/Documents/learn/AI/fristagent commit -m "style: remove 960px max-width constraint in notify config page"
```

---

### Task 8: 全页面最终视觉回归检查

- [ ] **Step 1: 逐页检查**

启动 `npm run dev`，逐一访问并确认：

| 页面 | 检查项 |
|------|--------|
| `/dashboard` | 4 统计卡 + 3 图表（2fr/1fr/1fr）+ 最近扫描，全宽无空白 |
| `/scans` | 顶导「PR 扫描」高亮，表格全宽 |
| `/chat` | 顶导「智能对话」高亮，内容全宽 |
| `/config/repos` | 二级导航出现，「仓库配置」高亮 |
| `/config/skills` | 二级导航「Skill 管理」高亮 |
| `/config/model` | 二级导航「模型配置」高亮，表单区超出原 960px |
| `/config/notify` | 二级导航「通知配置」高亮，两列卡片超出原 960px |

- [ ] **Step 2: 主题切换测试**

点击右上角主题按钮，确认 dark → system → light 循环正常，页面主题正确切换。

- [ ] **Step 3: 最终构建**

```bash
cd /Users/zhujinqi/Documents/learn/AI/fristagent/fristagent-web
npm run build
```

预期：无 error，无 warning（或仅有已知 Element Plus 动态组件 warning）。

- [ ] **Step 4: 最终 Commit**

```bash
git -C /Users/zhujinqi/Documents/learn/AI/fristagent add -A
git -C /Users/zhujinqi/Documents/learn/AI/fristagent commit -m "chore: layout redesign complete — top navigation"
```
