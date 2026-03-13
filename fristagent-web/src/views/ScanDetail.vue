<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ExternalLink, ShieldAlert, Bug, Zap, Paintbrush, Lightbulb } from 'lucide-vue-next'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ScoreRing from '@/components/common/ScoreRing.vue'
import { useScanStore } from '@/stores/scan'
import { scanApi } from '@/api'

const route = useRoute()
const router = useRouter()
const scanStore = useScanStore()

const scan = ref(null)
const issues = ref([])
const loading = ref(true)

// 向后兼容：模板里用 task 引用
const task = scan

async function loadDetail() {
  loading.value = true
  try {
    const [scanData, issueData] = await Promise.all([
      scanApi.get(route.params.id),
      scanApi.getIssues(route.params.id)
    ])
    scan.value = scanData
    issues.value = issueData
  } catch (e) {
    // ElMessage.error 已在 axios 拦截器处理
  } finally {
    loading.value = false
  }
}

onMounted(() => loadDetail())

// 实时进度（WebSocket 推送覆盖静态 task 数据）
const liveProgress = computed(() => scanStore.getProgress(route.params.id))
const displayStatus  = computed(() => liveProgress.value?.status  ?? task.value?.status)
const displayScore   = computed(() => liveProgress.value?.score   ?? task.value?.score)
const displaySummary = computed(() => liveProgress.value?.summary || task.value?.summary)
const scanningStep   = computed(() => liveProgress.value?.step    || '')
const scanningPct    = computed(() => liveProgress.value?.percent ?? 0)
const isScanning     = computed(() => displayStatus.value === 'SCANNING')

const activeFilter = ref('ALL')
const filters = [
  { label: '全部', value: 'ALL' },
  { label: 'BUG', value: 'BUG' },
  { label: '安全', value: 'SECURITY' },
  { label: '性能', value: 'PERFORMANCE' },
  { label: '风格', value: 'STYLE' },
  { label: '建议', value: 'SUGGESTION' },
]

const filteredIssues = computed(() =>
  activeFilter.value === 'ALL' ? issues.value : issues.value.filter(i => i.issueType === activeFilter.value)
)

const issueConfig = {
  BUG:         { icon: Bug,         color: '#EF4444', label: 'BUG' },
  SECURITY:    { icon: ShieldAlert,  color: '#F97316', label: '安全' },
  PERFORMANCE: { icon: Zap,         color: '#8B5CF6', label: '性能' },
  STYLE:       { icon: Paintbrush,  color: '#3B82F6', label: '风格' },
  SUGGESTION:  { icon: Lightbulb,   color: '#10B981', label: '建议' },
}

const severityColor = { HIGH: '#EF4444', MEDIUM: '#F59E0B', LOW: '#4A5878' }
const severityLabel = { HIGH: '高', MEDIUM: '中', LOW: '低' }

const issueCount = (type) => issues.value.filter(i => i.issueType === type).length

// 进度步骤配置（对应后端广播的 percent 区间）
const SCAN_STEPS = [
  { label: '连接仓库获取 Diff',  threshold: 10 },
  { label: '加载 Skill',         threshold: 30 },
  { label: 'AI 分析代码',        threshold: 50 },
  { label: '保存结果',           threshold: 85 },
  { label: '发送通知',           threshold: 95 },
]

const getStepState = (index, pct) => {
  const step = SCAN_STEPS[index]
  const next = SCAN_STEPS[index + 1]
  if (pct >= (next?.threshold ?? 100)) return 'done'
  if (pct >= step.threshold) return 'active'
  return 'pending'
}
</script>

<template>
  <div class="scan-detail">
    <!-- Loading placeholder -->
    <div v-if="loading && !scan" class="loading-placeholder">加载中...</div>

    <!-- Header -->
    <div v-if="scan" class="page-header">
      <button class="back-btn" @click="router.push('/scans')">
        <ArrowLeft :size="16" />
        <span>PR 扫描</span>
      </button>
      <div class="header-main">
        <div class="header-left">
          <h1>{{ task.prTitle }}</h1>
          <div class="header-meta">
            <span class="mono">#{{ task.prNumber }}</span>
            <span>{{ task.prAuthor }}</span>
            <span>{{ task.sourceRef }} → {{ task.targetBranch }}</span>
            <span class="skill-tag">{{ task.skillName }}</span>
          </div>
        </div>
        <div class="header-right">
          <StatusBadge :status="displayStatus" />
          <ScoreRing v-if="displayScore !== null" :score="displayScore" :size="56" />
          <a :href="task.prUrl" target="_blank" class="pr-link">
            <ExternalLink :size="14" />
            直达 PR
          </a>
        </div>
      </div>
    </div>

    <!-- 实时扫描进度面板（扫描中时显示） -->
    <template v-if="scan">
    <Transition name="slide-down">
      <div v-if="isScanning" class="card scanning-card">
        <div class="scanning-header">
          <span class="scanning-pulse" />
          <span class="scanning-title">AI 正在审查代码...</span>
          <span class="scanning-pct">{{ scanningPct }}%</span>
        </div>
        <div class="scanning-bar-wrap">
          <div class="scanning-bar" :style="{ width: scanningPct + '%' }" />
        </div>
        <div class="scanning-steps">
          <div
            v-for="(s, i) in SCAN_STEPS"
            :key="i"
            class="step-item"
            :class="getStepState(i, scanningPct)"
          >
            <span class="step-dot" />
            <span>{{ s.label }}</span>
          </div>
        </div>
        <div class="scanning-current">{{ scanningStep }}</div>
      </div>
    </Transition>

    <!-- Summary -->
    <div class="card summary-card">
      <div class="summary-label">Agent 审查摘要</div>
      <p class="summary-text">{{ displaySummary || (isScanning ? '扫描完成后将显示 AI 综合评价...' : '') }}</p>
      <div class="issue-stats">
        <div v-for="(cfg, type) in issueConfig" :key="type" class="issue-stat">
          <component :is="cfg.icon" :size="14" :color="cfg.color" />
          <span :style="{ color: cfg.color }">{{ issueCount(type) }}</span>
          <span class="text-muted">{{ cfg.label }}</span>
        </div>
      </div>
    </div>

    <!-- Issues -->
    <div class="card">
      <div class="issues-header">
        <span class="card-title">问题列表</span>
        <div class="filter-tabs">
          <button
            v-for="f in filters" :key="f.value"
            class="filter-btn"
            :class="{ active: activeFilter === f.value }"
            @click="activeFilter = f.value"
          >{{ f.label }}</button>
        </div>
      </div>

      <div class="issue-list">
        <div v-for="issue in filteredIssues" :key="issue.id" class="issue-item">
          <div class="issue-header">
            <div class="issue-type-badge" :style="{ color: issueConfig[issue.issueType].color }">
              <component :is="issueConfig[issue.issueType].icon" :size="14" />
              <span>{{ issueConfig[issue.issueType].label }}</span>
            </div>
            <span class="severity-badge" :style="{ color: severityColor[issue.severity] }">
              {{ severityLabel[issue.severity] }}危
            </span>
            <span class="file-path">
              {{ issue.filePath }}
              <span v-if="issue.lineStart" class="line-num">:{{ issue.lineStart }}</span>
            </span>
          </div>
          <div class="issue-desc">{{ issue.description }}</div>
          <div class="issue-suggestion">
            <span class="suggestion-label">修复建议</span>
            {{ issue.suggestion }}
          </div>
        </div>

        <div v-if="filteredIssues.length === 0" class="empty-state">
          该类型无问题
        </div>
      </div>
    </div>
    </template>
  </div>
</template>

<style scoped>
.scan-detail { padding: 28px; display: flex; flex-direction: column; gap: 18px; }

.back-btn {
  display: inline-flex; align-items: center; gap: 6px;
  background: none; border: none; color: var(--text-muted);
  font-size: 13px; cursor: pointer;
  padding: 4px 0;
  transition: color var(--transition);
}
.back-btn:hover { color: var(--text-primary); }

.header-main { display: flex; justify-content: space-between; align-items: flex-start; margin-top: 10px; gap: 20px; }
.header-left h1 { font-size: 20px; font-weight: 650; }
.header-meta { display: flex; gap: 12px; align-items: center; margin-top: 6px; font-size: 12.5px; color: var(--text-secondary); flex-wrap: wrap; }
.mono { font-family: var(--font-mono); }
.skill-tag { background: var(--bg-hover); padding: 2px 8px; border-radius: 4px; font-family: var(--font-mono); font-size: 11px; color: var(--text-muted); }

.header-right { display: flex; align-items: center; gap: 14px; flex-shrink: 0; }
.pr-link {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 12.5px;
  transition: all var(--transition);
}
.pr-link:hover { border-color: var(--accent-blue); color: var(--accent-blue-2); }

.card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 18px; }

.summary-card { }
.summary-label { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.07em; color: var(--text-muted); margin-bottom: 10px; }
.summary-text { font-size: 13.5px; color: var(--text-secondary); line-height: 1.7; }
.issue-stats { display: flex; gap: 20px; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }
.issue-stat { display: flex; align-items: center; gap: 5px; font-size: 13px; }

.issues-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-title { font-size: 13px; font-weight: 600; color: var(--text-secondary); }
.filter-tabs { display: flex; gap: 4px; }
.filter-btn {
  padding: 4px 10px;
  border-radius: 5px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}
.filter-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.filter-btn.active { background: rgba(37,99,235,0.12); border-color: rgba(37,99,235,0.3); color: var(--accent-blue-2); }

.issue-list { display: flex; flex-direction: column; gap: 10px; }

.issue-item {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 14px;
}

.issue-header { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }

.issue-type-badge { display: flex; align-items: center; gap: 4px; font-size: 12px; font-weight: 600; }

.severity-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  background: currentColor;
  color: white;
}
/* Override to show colored bg */
.severity-badge { background-color: currentColor; filter: contrast(0.3) brightness(1.5); }

.file-path {
  font-size: 12px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  margin-left: auto;
}
.line-num { color: var(--accent-blue-2); }

.issue-desc { font-size: 13.5px; color: var(--text-primary); line-height: 1.6; margin-bottom: 10px; }

.issue-suggestion {
  font-size: 12.5px;
  color: var(--text-secondary);
  background: rgba(16, 185, 129, 0.06);
  border-left: 2px solid var(--accent-green);
  padding: 8px 12px;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  line-height: 1.6;
}
.suggestion-label { font-weight: 600; color: var(--accent-green); margin-right: 6px; }

.empty-state { text-align: center; padding: 32px; color: var(--text-muted); font-size: 13px; }

/* ---- 实时扫描进度面板 ---- */
.scanning-card {
  border-color: rgba(37, 99, 235, 0.3);
  background: rgba(37, 99, 235, 0.04);
}

.scanning-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.scanning-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent-blue-2);
  box-shadow: 0 0 8px var(--accent-blue);
  animation: pulse 1.4s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.75); }
}

.scanning-title { font-size: 14px; font-weight: 600; color: var(--text-primary); flex: 1; }
.scanning-pct { font-size: 13px; color: var(--accent-blue-2); font-variant-numeric: tabular-nums; }

.scanning-bar-wrap {
  height: 4px;
  background: var(--bg-hover);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 18px;
}

.scanning-bar {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--accent-blue), #60a5fa);
  transition: width 0.5s ease;
  position: relative;
}

.scanning-bar::after {
  content: '';
  position: absolute;
  right: 0; top: 0;
  width: 60px; height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.5));
  animation: shine 1.4s ease-in-out infinite;
}

@keyframes shine {
  0% { opacity: 0; transform: translateX(-20px); }
  50% { opacity: 1; }
  100% { opacity: 0; transform: translateX(20px); }
}

.scanning-steps {
  display: flex;
  gap: 0;
  margin-bottom: 12px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  font-size: 11.5px;
  color: var(--text-muted);
  transition: color 0.3s;
  position: relative;
}

.step-item:not(:last-child)::after {
  content: '';
  position: absolute;
  right: 0; top: 50%;
  width: calc(100% - 100px);
  height: 1px;
  background: var(--border);
}

.step-item.done { color: var(--accent-green); }
.step-item.active { color: var(--accent-blue-2); font-weight: 500; }

.step-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.step-item.active .step-dot {
  box-shadow: 0 0 6px currentColor;
  animation: pulse 1.2s ease-in-out infinite;
}

.scanning-current {
  font-size: 12px;
  color: var(--text-muted);
  font-style: italic;
}

/* Transition */
.slide-down-enter-active { transition: all 0.25s ease; }
.slide-down-leave-active { transition: all 0.2s ease; }
.slide-down-enter-from { opacity: 0; transform: translateY(-10px); }
.slide-down-leave-to { opacity: 0; transform: translateY(-6px); }

.loading-placeholder { padding: 48px; text-align: center; color: var(--text-muted); font-size: 14px; }
</style>
