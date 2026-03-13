<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'
import { GitPullRequest, AlertTriangle, TrendingUp, CheckCircle } from 'lucide-vue-next'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ScoreRing from '@/components/common/ScoreRing.vue'
import { scanApi } from '@/api'

use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()

const statsData = ref({ totalScans: 0, weeklyScans: 0, avgScore: null, statusCounts: {}, scoreTrend: [] })
const recentScans = ref([])

onMounted(async () => {
  try {
    statsData.value = await scanApi.stats()
  } catch (e) {
    // 静默失败，保留默认值
  }
  try {
    const res = await scanApi.list({ page: 0, size: 5 })
    recentScans.value = res.content || res
  } catch (e) {
    // 静默失败
  }
})

const stats = computed(() => [
  { label: '总扫描数', value: statsData.value.totalScans, icon: GitPullRequest, color: '#3B82F6', change: '' },
  { label: '平均评分', value: statsData.value.avgScore !== null ? Math.round(statsData.value.avgScore) : '—', icon: TrendingUp, color: '#10B981', change: '' },
  { label: '本周扫描', value: statsData.value.weeklyScans, icon: AlertTriangle, color: '#8B5CF6', change: '' },
  { label: '已完成', value: statsData.value.statusCounts?.DONE ?? 0, icon: CheckCircle, color: '#10B981', change: '' },
])

const scoreTrendOption = computed(() => {
  const trend = statsData.value.scoreTrend || []
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: '#161B2B', borderColor: '#1E2D44', textStyle: { color: '#E8EDF5' } },
    grid: { left: 12, right: 12, top: 16, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: trend.length ? trend.map(t => t.date) : ['3/6', '3/7', '3/8', '3/9', '3/10', '3/11', '3/12'],
      axisLine: { lineStyle: { color: '#1E2D44' } },
      axisTick: { show: false },
      axisLabel: { color: '#4A5878', fontSize: 11 },
    },
    yAxis: {
      type: 'value', min: 0, max: 100,
      splitLine: { lineStyle: { color: '#1E2D44', type: 'dashed' } },
      axisLabel: { color: '#4A5878', fontSize: 11 },
    },
    series: [{
      type: 'line',
      data: trend.length ? trend.map(t => t.avgScore) : [],
      smooth: true,
      symbol: 'circle', symbolSize: 5,
      lineStyle: { color: '#3B82F6', width: 2 },
      itemStyle: { color: '#3B82F6' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.25)' }, { offset: 1, color: 'rgba(59,130,246,0)' }] } },
    }],
  }
})

// 固定顺序：数据库字段名 → 显示标签 → 颜色
const ISSUE_TYPE_META = [
  { key: 'BUG',         label: 'BUG',        color: '#EF4444' },
  { key: 'SECURITY',    label: 'SECURITY',    color: '#F97316' },
  { key: 'PERFORMANCE', label: 'PERF',        color: '#8B5CF6' },
  { key: 'STYLE',       label: 'STYLE',       color: '#3B82F6' },
  { key: 'SUGGESTION',  label: 'SUGGESTION',  color: '#10B981' },
]

const issueTypeOption = computed(() => {
  const counts = statsData.value.issueTypeCounts || {}
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: '#161B2B', borderColor: '#1E2D44', textStyle: { color: '#E8EDF5' } },
    grid: { left: 12, right: 12, top: 10, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: ISSUE_TYPE_META.map(t => t.label),
      axisLine: { lineStyle: { color: '#1E2D44' } },
      axisTick: { show: false },
      axisLabel: { color: '#4A5878', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#1E2D44', type: 'dashed' } },
      axisLabel: { color: '#4A5878', fontSize: 11 },
    },
    series: [{
      type: 'bar',
      data: ISSUE_TYPE_META.map((t, i) => ({
        value: counts[t.key] ?? 0,
        itemStyle: { color: t.color },
      })),
      barMaxWidth: 32,
      borderRadius: [4, 4, 0, 0],
    }],
  }
})

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
</script>

<template>
  <div class="dashboard">
    <!-- Header -->
    <div class="page-header">
      <div>
        <h1>Dashboard</h1>
        <p>代码审查实时概览</p>
      </div>
    </div>

    <!-- Stats cards -->
    <div class="stats-grid">
      <div v-for="s in stats" :key="s.label" class="stat-card">
        <div class="stat-icon" :style="{ background: s.color + '1a', color: s.color }">
          <component :is="s.icon" :size="20" />
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ s.value }}<span v-if="s.change" class="stat-change" :class="s.change.startsWith('-') ? 'neg' : 'pos'">{{ s.change }}</span></div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- Charts row -->
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

    <!-- Recent scans -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">最近扫描</span>
        <el-button text size="small" @click="router.push('/scans')">查看全部 →</el-button>
      </div>
      <div class="scan-list">
        <div
          v-for="scan in recentScans"
          :key="scan.id"
          class="scan-row"
          @click="router.push(`/scans/${scan.id}`)"
        >
          <div class="scan-info">
            <div class="scan-title">{{ scan.prTitle }}</div>
            <div class="scan-meta">
              <span>{{ scan.prAuthor }}</span>
              <span class="sep">·</span>
              <span>{{ scan.skillName }}</span>
            </div>
          </div>
          <div class="scan-right">
            <StatusBadge :status="scan.status" />
            <ScoreRing v-if="scan.score !== null" :score="scan.score" :size="44" />
            <span v-else class="score-empty">—</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard { padding: 28px; display: flex; flex-direction: column; gap: 20px; }

.page-header { display: flex; align-items: center; justify-content: space-between; }
.page-header h1 { font-size: 22px; font-weight: 650; }
.page-header p { font-size: 13px; color: var(--text-muted); margin-top: 3px; }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: border-color var(--transition);
}
.stat-card:hover { border-color: var(--border-light); }

.stat-icon {
  width: 44px; height: 44px;
  border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-variant-numeric: tabular-nums;
}

.stat-change { font-size: 12px; font-weight: 500; }
.stat-change.pos { color: var(--accent-green); }
.stat-change.neg { color: var(--accent-red); }
.stat-label { font-size: 12px; color: var(--text-muted); margin-top: 2px; }

.charts-row { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 14px; }

.card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 18px;
}

.card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.card-title { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 14px; display: block; }
.card-header .card-title { margin-bottom: 0; }

.scan-list { display: flex; flex-direction: column; }

.scan-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition);
}
.scan-row:hover { background: var(--bg-hover); }
.scan-row + .scan-row { border-top: 1px solid var(--border); }

.scan-title { font-size: 13.5px; font-weight: 500; color: var(--text-primary); }
.scan-meta { font-size: 12px; color: var(--text-muted); margin-top: 3px; display: flex; align-items: center; gap: 5px; }
.sep { color: var(--text-muted); }
.scan-right { display: flex; align-items: center; gap: 14px; }
.score-empty { font-size: 16px; color: var(--text-muted); width: 44px; text-align: center; }
</style>
