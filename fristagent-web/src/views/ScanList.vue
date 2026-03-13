<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Search, RefreshCw } from 'lucide-vue-next'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ScoreRing from '@/components/common/ScoreRing.vue'
import { useScanStore } from '@/stores/scan'
import { scanApi } from '@/api'
import dayjs from 'dayjs'

const router = useRouter()
const scanStore = useScanStore()

const loading = ref(false)
const search = ref('')
const filterStatus = ref('')
const scans = ref([])
const total = ref(0)
const page = ref(0)

async function loadScans() {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (filterStatus.value) params.status = filterStatus.value
    if (search.value) params.search = search.value
    const res = await scanApi.list(params)
    scans.value = res.content || res
    total.value = res.totalElements || scans.value.length
  } finally {
    loading.value = false
  }
}

onMounted(() => loadScans())
watch(filterStatus, () => { page.value = 0; loadScans() })

// 合并实时进度到扫描列表
const mergedScans = computed(() => scans.value.map(s => {
  const live = scanStore.getProgress(s.id)
  if (!live) return s
  return {
    ...s,
    status: live.status,
    score:  live.score ?? s.score,
    _step:  live.step,
    _pct:   live.percent,
  }
}))

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待扫描', value: 'PENDING' },
  { label: '扫描中', value: 'SCANNING' },
  { label: '已完成', value: 'DONE' },
  { label: '失败', value: 'FAILED' },
]

const platformBadge = (p) => p === 'GITHUB'
  ? { label: 'GitHub', color: '#8B9BB4', bg: 'rgba(139,155,180,0.1)' }
  : { label: 'GitLab', color: '#F97316', bg: 'rgba(249,115,22,0.1)' }

const fmt = (d) => dayjs(d).format('MM/DD HH:mm')
</script>

<template>
  <div class="scan-list-page">
    <div class="page-header">
      <div>
        <h1>PR 扫描</h1>
        <p>所有仓库的 PR/MR 扫描记录</p>
      </div>
    </div>

    <!-- Filters -->
    <div class="filters">
      <div class="search-wrap">
        <Search :size="14" class="search-icon" />
        <input v-model="search" class="search-input" placeholder="搜索 PR 标题、作者..." />
      </div>
      <div class="status-tabs">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          class="tab-btn"
          :class="{ active: filterStatus === opt.value }"
          @click="filterStatus = opt.value"
        >{{ opt.label }}</button>
      </div>
      <button class="icon-btn" @click="loadScans">
        <RefreshCw :size="15" :class="{ spin: loading }" />
      </button>
    </div>

    <!-- Empty state -->
    <div v-if="scans.length === 0 && !loading" class="empty-state">
      <p>暂无扫描记录</p>
      <p class="text-muted">配置仓库 Webhook 后，新 PR/MR 将自动触发扫描</p>
    </div>

    <!-- Table -->
    <div v-else class="card">
      <table class="scan-table">
        <thead>
          <tr>
            <th>PR</th>
            <th>作者</th>
            <th>平台</th>
            <th>Skill</th>
            <th>状态</th>
            <th>评分</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="s in mergedScans" :key="s.id">
          <tr
            class="scan-tr"
            @click="router.push(`/scans/${s.id}`)"
          >
            <td>
              <div class="pr-title">{{ s.prTitle }}</div>
              <div class="pr-num">#{{ s.prNumber }}</div>
            </td>
            <td>
              <div class="author">{{ s.prAuthor }}</div>
            </td>
            <td>
              <span class="mini-badge" :style="{ color: platformBadge(s.platform).color, background: platformBadge(s.platform).bg }">
                {{ platformBadge(s.platform).label }}
              </span>
            </td>
            <td>
              <span class="skill-tag">{{ s.skillName }}</span>
            </td>
            <td><StatusBadge :status="s.status" /></td>
            <td>
              <ScoreRing v-if="s.score !== null" :score="s.score" :size="40" />
              <span v-else class="text-muted">—</span>
            </td>
            <td><span class="time">{{ fmt(s.createdAt) }}</span></td>
          </tr>
          <!-- 实时进度行（仅 SCANNING 状态显示） -->
          <tr v-if="s.status === 'SCANNING'" class="progress-tr">
            <td colspan="7">
              <div class="scan-progress">
                <div class="progress-bar-wrap">
                  <div class="progress-bar" :style="{ width: (s._pct || 10) + '%' }" />
                </div>
                <span class="progress-step">{{ s._step || '初始化扫描...' }}</span>
                <span class="progress-pct">{{ s._pct || 10 }}%</span>
              </div>
            </td>
          </tr>
          </template>
        </tbody>
      </table>

      <!-- Pagination -->
      <div v-if="total > 20" class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="prev, pager, next"
          @current-change="(p) => { page = p - 1; loadScans() }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.scan-list-page { padding: 28px; display: flex; flex-direction: column; gap: 18px; }

.page-header h1 { font-size: 22px; font-weight: 650; }
.page-header p { font-size: 13px; color: var(--text-muted); margin-top: 3px; }

.filters { display: flex; align-items: center; gap: 12px; }

.search-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.search-icon { position: absolute; left: 10px; color: var(--text-muted); pointer-events: none; }
.search-input {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 7px 12px 7px 30px;
  color: var(--text-primary);
  font-size: 13px;
  width: 240px;
  outline: none;
  transition: border-color var(--transition);
}
.search-input:focus { border-color: var(--accent-blue); }

.status-tabs { display: flex; gap: 4px; }
.tab-btn {
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  cursor: pointer;
  transition: all var(--transition);
}
.tab-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.tab-btn.active { background: rgba(37,99,235,0.12); border-color: rgba(37,99,235,0.3); color: var(--accent-blue-2); }

.icon-btn {
  width: 34px; height: 34px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: all var(--transition);
}
.icon-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); overflow: hidden; }

.scan-table { width: 100%; border-collapse: collapse; }
.scan-table th {
  padding: 10px 16px;
  background: var(--bg-secondary);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  text-align: left;
  border-bottom: 1px solid var(--border);
}
.scan-table td { padding: 14px 16px; border-bottom: 1px solid var(--border); vertical-align: middle; }
.scan-table tbody tr:last-child td { border-bottom: none; }

.scan-tr { cursor: pointer; transition: background var(--transition); }
.scan-tr:hover td { background: var(--bg-hover); }

.pr-title { font-size: 13.5px; font-weight: 500; color: var(--text-primary); }
.pr-num { font-size: 12px; color: var(--text-muted); margin-top: 2px; font-family: var(--font-mono); }
.author { font-size: 13px; color: var(--text-secondary); }
.time { font-size: 12px; color: var(--text-muted); font-variant-numeric: tabular-nums; }

.mini-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.skill-tag {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--bg-hover);
  padding: 2px 8px;
  border-radius: 4px;
  font-family: var(--font-mono);
}

/* 进度条行 */
.progress-tr td { padding: 0 16px 10px; border-bottom: 1px solid var(--border); }
.progress-tr:hover td { background: transparent !important; }

.scan-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar-wrap {
  flex: 1;
  height: 3px;
  background: var(--bg-hover);
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--accent-blue), var(--accent-blue-2));
  border-radius: 2px;
  transition: width 0.4s ease;
  position: relative;
}

.progress-bar::after {
  content: '';
  position: absolute;
  right: 0;
  top: 0;
  width: 40px;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.4));
  animation: shimmer 1.2s ease-in-out infinite;
}

@keyframes shimmer {
  0% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}

.progress-step { font-size: 11.5px; color: var(--text-muted); white-space: nowrap; }
.progress-pct { font-size: 11px; color: var(--accent-blue-2); font-variant-numeric: tabular-nums; white-space: nowrap; }

.empty-state { padding: 48px; text-align: center; }
.empty-state p { font-size: 14px; color: var(--text-secondary); }
.empty-state .text-muted { font-size: 13px; color: var(--text-muted); margin-top: 6px; }

.pagination-row { display: flex; justify-content: center; padding: 14px; border-top: 1px solid var(--border); }
</style>
