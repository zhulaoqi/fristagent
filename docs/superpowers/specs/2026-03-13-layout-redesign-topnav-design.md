# Layout Redesign: Top Navigation

**Date:** 2026-03-13
**Status:** Approved

## Problem

当前布局采用 220px 左侧固定侧边栏 + 右侧内容区方案，存在以下视觉问题：

- 左侧侧边栏内容密集（Logo、Skill 指示器、7 个导航项、主题切换），视觉重心严重偏左
- 配置页（`max-width: 960px`）右侧大量空白
- 内容区在宽屏下没有有效利用横向空间

## Solution

将左侧侧边栏替换为顶部横向导航栏，配置子页增加二级导航栏。

---

## Layout Structure

### Primary Top Navigation Bar

```
[ Logo Icon  FristAgent ] | [ Dashboard ] [ PR扫描 ] [ 智能对话 ] [ 配置 ]   ···   [ ● UniversalCodeReviewer ] [ 🌙 ]
```

- 高度：`44px`，通过 `display: flex; align-items: center; height: 44px; flex-shrink: 0` 实现
- 背景 `var(--bg-primary)`，下边框 `1px solid var(--border)`
- **左区**：`.nav-logo`（logo icon + 文字）→ `.nav-sep`（竖线分隔）→ `<nav class="nav-links">` 3 个 RouterLink（navItems）+ 1 个 `<span>`（配置，用 `isConfigRoute` 判断活跃态）
- **右区**：`margin-left: auto` 撑开 → `.skill-pill` → `.theme-btn`

**导航活跃态**：navItems 的 3 个 RouterLink 复用现有 `isActive` helper（`route.path.startsWith(to)`）；「配置」span 单独使用 `isConfigRoute` computed。活跃样式统一：`background: rgba(37,99,235,0.14); color: var(--accent-blue-2); font-weight: 500`。

**Skill Pill**：复用现有 `skillStore`（`useSkillStore()`）和 `skillStore.fetchActive()` 调用（保持不变），展示 `skillStore.activeSkill`（有值时显示，无值时隐藏）。

**主题切换**：复用现有 `useTheme` composable、`themeOptions` 数组、`cycleTheme()` 函数、`currentThemeIcon` computed，这些均保留在 AppLayout.vue script 中。改动仅是将 sidebar-footer 中的三段式 pill 改为单图标循环按钮（该按钮在折叠态已有实现：`.theme-icon-btn`），移入 `.nav-right` 即可。

### Secondary Config Navigation Bar

- 仅在 `isConfigRoute`（`computed(() => route.path.startsWith('/config'))`）为 true 时渲染
- 高度：`36px`，通过 `display: flex; align-items: center; height: 36px; flex-shrink: 0` 实现
- 背景 `var(--bg-primary)`，下边框 `1px solid var(--border)`
- 渲染 `configItems` 数组（顺序不变：仓库配置 → Skill 管理 → 模型配置 → 通知配置）
- 活跃判断：`route.path === item.to`（精确匹配），样式：文字 `var(--text-primary)` + 底部 `border-bottom: 2px solid var(--accent-blue)`；链接高度撑满 36px，通过 `align-self: stretch; display: flex; align-items: center` 实现，margin-bottom: -1px 覆盖下边框

### Main Content Area

- `app-shell`：`display: flex; flex-direction: column; height: 100vh`，移除 `overflow: hidden`
- `main.main-content`：`flex: 1; overflow-y: auto; background: var(--bg-base)`
- 保留现有 `div.content-wrap`（`width: 100%; height: 100%`），RouterView 不变

---

## Page-Level Changes

### Dashboard.vue

当前有两个图表：① `scoreTrendOption`（评分趋势折线图）、② `issueTypeOption`（问题类型分布柱状图）。

改动：
- `.charts-row` 从 `grid-template-columns: 1fr 1fr` 改为 `grid-template-columns: 2fr 1fr 1fr`
- ① 折线图占 `2fr`，无其他变化
- ② 问题类型柱状图占 `1fr`，无其他变化
- 新增第三个图表卡片（占 `1fr`）：**扫描状态分布**
  - computed 名称：`statusCountOption`
  - 数据来源：`statsData.value.statusCounts`（对象，key 为 DONE / SCANNING / PENDING / FAILED）
  - 渲染类型：ECharts **水平条形图**（`type: 'bar'`，`yAxis: { type: 'category' }`，`xAxis: { type: 'value' }`）
  - 分类顺序（yAxis.data）：`['FAILED', 'SCANNING', 'PENDING', 'DONE']`（DONE 在上）
  - 颜色：DONE `#10B981`，SCANNING `#3B82F6`，PENDING `#4A5878`，FAILED `#EF4444`（通过 `data` 数组的 `itemStyle.color` 设置）
  - 其余样式（grid、axisLabel、tooltip、backgroundColor）与 `issueTypeOption` 保持一致

### ScanList.vue / ScanDetail.vue / Chat.vue

无结构变化。

### Config 页面

#### config/Model.vue

- 移除 `.page-header` 的 `max-width: 960px`
- 移除 `.content-grid` 的 `max-width: 960px`；保持 `grid-template-columns: 1fr 280px`；添加 `max-width: 1400px` 防止超宽屏下表单列过宽

#### config/Notify.vue

- `.page-header`、`.notify-grid`、`.save-row` 均是 `.notify-page` 的直接子元素（兄弟关系）
- 移除 `.page-header` 的 `max-width: 960px`
- 移除 `.notify-grid` 的 `max-width: 960px`；添加 `max-width: 1400px`
- 移除 `.save-row` 的 `max-width: 960px`；添加 `max-width: 1400px`，三者对齐

#### config/Repos.vue / config/Skills.vue

- 均无 `max-width` 约束，auto-fill 布局已自适应宽度
- 无改动

---

## AppLayout.vue — Detailed Changes

### Script 改动

移除：
- `collapsed` ref
- `ChevronLeft`、`ChevronRight` 图标导入

保留：
- `skillStore`、`skillStore.fetchActive()`
- `useTheme`、`theme`、`setTheme`、`themeOptions`、`currentThemeIcon`、`cycleTheme`
- `navItems`、`configItems`、`isActive`、`useRoute`、`RouterLink`、`RouterView`

新增：
- `isConfigRoute = computed(() => route.path.startsWith('/config'))`

### Template 改动

移除：
- `<aside class="sidebar">` 整体（包含 sidebar-logo、skill-badge、sidebar-nav、sidebar-footer 所有内容）

新增（替换 aside）：
```html
<header class="top-nav">
  <div class="nav-logo">
    <div class="logo-icon"><Zap :size="16" color="#2563EB" /></div>
    <span class="logo-text">FristAgent</span>
  </div>
  <div class="nav-sep"></div>
  <nav class="nav-links">
    <RouterLink v-for="item in navItems" :key="item.to" :to="item.to"
      class="nav-link" :class="{ active: isActive(item.to) }">
      {{ item.label }}
    </RouterLink>
    <span class="nav-link" :class="{ active: isConfigRoute }"
      style="cursor:default">配置</span>
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

<nav v-if="isConfigRoute" class="sub-nav">
  <RouterLink v-for="item in configItems" :key="item.to" :to="item.to"
    class="sub-link" :class="{ active: route.path === item.to }">
    {{ item.label }}
  </RouterLink>
</nav>
```

> 注：「配置」顶级链接不跳转（无固定目标页），点击行为可改为跳转到 `/config/repos`（第一个配置项）。工程师选其一即可，不影响整体布局。

### CSS 改动

**移除 class**：`.sidebar`、`.sidebar.collapsed`、`.sidebar-logo`、`.skill-badge`、`.sidebar-nav`、`.nav-item`、`.nav-section-label`、`.sidebar-footer`、`.theme-pill`、`.theme-seg`、`.collapse-btn`

**保留并复用**：
- `.logo-icon` 和 `.logo-text`：新 template 的 `.nav-logo` 内复用相同 class，CSS 规则保持不变
- `.skill-dot` 和 `.skill-name`：CSS 规则保留，仅修改 `.skill-dot` 的上下文（原在 `.skill-badge` 下，现在 `.skill-pill` 下，样式一致）；`.skill-name` 新增 `color: var(--accent-green)` 覆盖原有 `color: var(--text-muted)`
- `.theme-icon-btn`：直接复用到 nav-right；icon 尺寸从 `:size="15"` 改为 `:size="14"`（小幅调整以匹配顶栏密度）

**修改**：`.app-shell` 改为 `flex-direction: column`，移除 `overflow: hidden`；`.main-content` 移除与侧边栏相关的任何 `margin-left` 或 `flex` 修正（原本已是 `flex: 1`，无需额外处理）

**新增 class**：

```css
.top-nav {
  height: 44px; flex-shrink: 0;
  display: flex; align-items: center; gap: 0;
  padding: 0 16px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
}
.nav-logo { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.nav-sep { width: 1px; height: 16px; background: var(--border); margin: 0 12px; flex-shrink: 0; }
.nav-links { display: flex; align-items: center; gap: 2px; }
.nav-link {
  padding: 5px 10px; border-radius: var(--radius-sm);
  font-size: 13px; font-weight: 450; color: var(--text-secondary);
  text-decoration: none; white-space: nowrap; cursor: pointer;
  transition: background var(--transition), color var(--transition);
}
.nav-link:hover { background: var(--bg-hover); color: var(--text-primary); }
.nav-link.active { background: rgba(37,99,235,0.14); color: var(--accent-blue-2); font-weight: 500; }
.nav-right { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.skill-pill {
  display: flex; align-items: center; gap: 6px;
  padding: 3px 10px; border-radius: 10px;
  background: rgba(16,185,129,0.08); border: 1px solid rgba(16,185,129,0.2);
}
/* .skill-dot 和 .skill-name 已在原 sidebar CSS 中定义，但作用域隔离，需在此处重新声明 */
.skill-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--accent-green); box-shadow: 0 0 6px var(--accent-green); flex-shrink: 0;
}
.skill-name { font-size: 11px; color: var(--accent-green); white-space: nowrap; }

.sub-nav {
  height: 36px; flex-shrink: 0;
  display: flex; align-items: stretch;
  padding: 0 16px;
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
}
.sub-link {
  display: flex; align-items: center;
  padding: 0 14px;
  font-size: 13px; color: var(--text-muted);
  text-decoration: none; white-space: nowrap;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color var(--transition), border-color var(--transition);
}
.sub-link:hover { color: var(--text-secondary); }
.sub-link.active { color: var(--text-primary); border-bottom-color: var(--accent-blue); }
```

---

## CSS Variables (variables.css)

移除（仅 AppLayout.vue 和 variables.css 两处引用，已确认无其他文件使用）：
- `--sidebar-width: 220px`
- `--sidebar-collapsed: 64px`

无新增变量。

---

## Out of Scope

- Chat 页、ScanDetail 页内容布局调整
- 响应式 / 移动端适配
- 动画 / 过渡效果优化
- 「配置」顶级链接点击行为（跳转 vs 无操作，工程师自选）
