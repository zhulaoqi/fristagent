<script setup>
import { ref, onMounted } from 'vue'
import { Plus, GitBranch, Pencil, Trash2, Copy, UserPlus, X, Mail, MessageCircle } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import { repoApi } from '@/api'

const repos = ref([])

onMounted(async () => {
  try {
    repos.value = await repoApi.list()
  } catch (e) {}
})

const showDialog = ref(false)
const editMode = ref(false)

function emptyForm() {
  return { name: '', platform: 'GITHUB', repoUrl: '', webhookSecret: '', enabled: true, admins: [] }
}
const form = ref(emptyForm())

const openCreate = () => {
  editMode.value = false
  form.value = emptyForm()
  showDialog.value = true
}

const openEdit = (repo) => {
  editMode.value = true
  form.value = {
    id: repo.id,
    name: repo.name,
    platform: repo.platform,
    repoUrl: repo.repoUrl,
    webhookSecret: repo.webhookSecret || '',
    enabled: repo.enabled,
    admins: (repo.admins || []).map(a => ({ id: a.id, name: a.name, email: a.email, feishuOpenId: a.feishuOpenId || '' })),
  }
  showDialog.value = true
}

const addAdmin = () => {
  form.value.admins.push({ name: '', email: '', feishuOpenId: '' })
}

const removeAdmin = (idx) => {
  form.value.admins.splice(idx, 1)
}

const save = async () => {
  if (!form.value.name || !form.value.repoUrl) {
    ElMessage.warning('请填写仓库名称和地址')
    return
  }
  // 过滤空行
  const admins = form.value.admins.filter(a => a.name || a.email)
  const payload = { ...form.value, admins }
  try {
    if (editMode.value) {
      await repoApi.update(form.value.id, payload)
    } else {
      await repoApi.create(payload)
    }
    repos.value = await repoApi.list()
    ElMessage.success(editMode.value ? '已保存' : '仓库已添加')
    showDialog.value = false
  } catch (e) {}
}

const remove = async (repo) => {
  await ElMessageBox.confirm(`确定删除仓库 ${repo.name}？`, '删除', {
    type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
  })
  try {
    await repoApi.remove(repo.id)
    repos.value = repos.value.filter(r => r.id !== repo.id)
    ElMessage.success('已删除')
  } catch (e) {}
}

const copyWebhook = (repo) => {
  const url = `${window.location.origin}/webhook/${repo.platform.toLowerCase()}/${repo.id}`
  navigator.clipboard.writeText(url)
  ElMessage.success('Webhook URL 已复制')
}

</script>

<template>
  <div class="repos-page">
    <div class="page-header">
      <div>
        <h1>仓库配置</h1>
        <p>管理需要监听 PR/MR 的 Git 仓库，并配置每个仓库的通知接收人</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">添加仓库</el-button>
    </div>

    <div class="repo-list">
      <div v-for="repo in repos" :key="repo.id" class="repo-card">
        <div class="repo-top">
          <div class="repo-icon">
            <GitBranch :size="18" />
          </div>
          <div class="repo-info">
            <div class="repo-name">{{ repo.name }}</div>
            <div class="repo-url">{{ repo.repoUrl }}</div>
          </div>
          <div class="repo-meta">
            <span class="platform-badge" :class="repo.platform.toLowerCase()">
              <!-- GitHub Octocat -->
              <svg v-if="repo.platform === 'GITHUB'" viewBox="0 0 16 16" width="13" height="13" fill="currentColor">
                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
              </svg>
              <!-- GitLab Tanuki -->
              <svg v-else-if="repo.platform === 'GITLAB'" viewBox="0 0 16 16" width="13" height="13" fill="currentColor">
                <path d="M15.97 9.058l-.895-2.756L13.3.842a.38.38 0 00-.722 0L10.8 6.302H5.2L3.422.842a.38.38 0 00-.722 0L.925 6.302.03 9.058a.76.76 0 00.276.85L8 15.47l7.694-5.562a.76.76 0 00.276-.85"/>
              </svg>
              {{ repo.platform === 'GITHUB' ? 'GitHub' : 'GitLab' }}
            </span>
          </div>
        </div>

        <!-- Webhook URL -->
        <div class="repo-webhook">
          <span class="webhook-label">Webhook URL</span>
          <code class="webhook-url">{{ window?.location?.origin || 'http://localhost:8080' }}/webhook/{{ repo.platform.toLowerCase() }}/{{ repo.id }}</code>
          <button class="copy-btn" @click="copyWebhook(repo)"><Copy :size="12" /></button>
        </div>

        <!-- 通知接收人 -->
        <div v-if="repo.admins && repo.admins.length > 0" class="admin-pills">
          <span class="admin-section-label">通知接收人</span>
          <div class="pills">
            <span v-for="a in repo.admins" :key="a.id" class="admin-pill">
              <span class="pill-name">{{ a.name }}</span>
              <span v-if="a.email" class="pill-channel"><Mail :size="10" />{{ a.email }}</span>
              <span v-if="a.feishuOpenId" class="pill-channel feishu"><MessageCircle :size="10" />飞书</span>
            </span>
          </div>
        </div>
        <div v-else class="admin-pills empty-admins">
          <span class="admin-section-label">通知接收人</span>
          <span class="no-admins">未配置，点击编辑添加</span>
        </div>

        <div class="repo-footer">
          <el-switch v-model="repo.enabled" size="small" active-text="启用" />
          <div class="repo-actions">
            <button class="text-btn" @click="openEdit(repo)"><Pencil :size="14" /> 编辑</button>
            <button class="text-btn danger" @click="remove(repo)"><Trash2 :size="14" /> 删除</button>
          </div>
        </div>
      </div>

      <div v-if="repos.length === 0" class="empty">
        <GitBranch :size="40" color="var(--text-muted)" />
        <p>还没有仓库，点击「添加仓库」开始配置</p>
      </div>
    </div>

    <!-- Dialog -->
    <el-dialog
      v-model="showDialog"
      :title="editMode ? '编辑仓库' : '添加仓库'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px" label-position="left" style="margin-top:8px">
        <el-form-item label="仓库名称">
          <el-input v-model="form.name" placeholder="backend-service" />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="form.platform" style="width:100%">
            <el-option label="GitHub" value="GITHUB" />
            <el-option label="GitLab" value="GITLAB" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库地址">
          <el-input v-model="form.repoUrl" placeholder="https://github.com/org/repo" />
        </el-form-item>
        <el-form-item label="Webhook Secret">
          <el-input v-model="form.webhookSecret" placeholder="留空则跳过签名校验" show-password />
        </el-form-item>
      </el-form>

      <!-- 通知接收人 -->
      <div class="admins-section">
        <div class="admins-header">
          <span class="admins-title">通知接收人</span>
          <span class="admins-hint">扫描完成后，以下人员将收到飞书 / 邮件通知</span>
          <button class="add-admin-btn" @click="addAdmin">
            <UserPlus :size="13" />添加
          </button>
        </div>

        <div v-if="form.admins.length === 0" class="admins-empty">
          点击「添加」配置通知接收人
        </div>

        <div v-else class="admins-table">
          <div class="admins-thead">
            <span class="col-name">姓名</span>
            <span class="col-email">邮箱</span>
            <span class="col-feishu">飞书 Open ID</span>
            <span class="col-del"></span>
          </div>
          <div v-for="(admin, idx) in form.admins" :key="idx" class="admins-row">
            <el-input v-model="admin.name" placeholder="张三" size="small" class="col-name" />
            <el-input v-model="admin.email" placeholder="zhangsan@example.com" size="small" class="col-email" />
            <el-input v-model="admin.feishuOpenId" placeholder="ou_xxxxxxxx" size="small" class="col-feishu" />
            <button class="del-btn col-del" @click="removeAdmin(idx)"><X :size="13" /></button>
          </div>
        </div>

        <div class="admins-note">
          <span>飞书 Open ID 和邮箱填一个即可；填写前请在「通知配置」页完成飞书 / SMTP 服务端设置</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.repos-page { padding: 28px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 650; }
.page-header p { font-size: 13px; color: var(--text-muted); margin-top: 3px; }

.repo-list { display: flex; flex-direction: column; gap: 14px; }

.repo-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 18px; display: flex; flex-direction: column; gap: 12px; }

.repo-top { display: flex; align-items: center; gap: 14px; }
.repo-icon { width: 40px; height: 40px; border-radius: var(--radius-md); background: var(--bg-hover); border: 1px solid var(--border); display: flex; align-items: center; justify-content: center; color: var(--text-secondary); flex-shrink: 0; }
.repo-info { flex: 1; min-width: 0; }
.repo-name { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.repo-url { font-size: 12px; color: var(--text-muted); font-family: var(--font-mono); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }
.repo-meta { display: flex; gap: 10px; align-items: center; }
.platform-badge { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; font-weight: 600; padding: 3px 9px; border-radius: 5px; letter-spacing: 0.02em; }
.platform-badge.github { color: #8B9BB4; background: rgba(139,155,180,0.1); border: 1px solid rgba(139,155,180,0.2); }
.platform-badge.gitlab { color: #F97316; background: rgba(249,115,22,0.08); border: 1px solid rgba(249,115,22,0.2); }

.repo-webhook { display: flex; align-items: center; gap: 8px; background: var(--bg-secondary); border-radius: var(--radius-sm); padding: 8px 12px; }
.webhook-label { font-size: 11px; color: var(--text-muted); font-weight: 600; white-space: nowrap; }
.webhook-url { flex: 1; font-size: 11.5px; color: var(--text-secondary); font-family: var(--font-mono); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.copy-btn { background: none; border: none; color: var(--text-muted); cursor: pointer; padding: 2px; display: flex; align-items: center; transition: color var(--transition); }
.copy-btn:hover { color: var(--text-primary); }

/* Admin pills on card */
.admin-pills { display: flex; align-items: flex-start; gap: 10px; }
.admin-pills.empty-admins { align-items: center; }
.admin-section-label { font-size: 11px; font-weight: 600; color: var(--text-muted); white-space: nowrap; padding-top: 3px; }
.pills { display: flex; flex-wrap: wrap; gap: 6px; }
.admin-pill { display: flex; align-items: center; gap: 5px; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 20px; padding: 3px 10px; }
.pill-name { font-size: 12px; color: var(--text-primary); font-weight: 500; }
.pill-channel { display: flex; align-items: center; gap: 3px; font-size: 11px; color: var(--text-muted); }
.pill-channel.feishu { color: var(--accent-blue-2); }
.no-admins { font-size: 12px; color: var(--text-muted); font-style: italic; }

.repo-footer { display: flex; align-items: center; justify-content: space-between; border-top: 1px solid var(--border); padding-top: 12px; }
.repo-actions { display: flex; gap: 8px; }
.text-btn { display: flex; align-items: center; gap: 5px; background: none; border: none; color: var(--text-muted); font-size: 12.5px; cursor: pointer; padding: 4px 8px; border-radius: 5px; transition: all var(--transition); }
.text-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.text-btn.danger:hover { color: var(--accent-red); }

.empty { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px; color: var(--text-muted); font-size: 13px; }

/* Admins section in dialog */
.admins-section { margin-top: 16px; border-top: 1px solid var(--border); padding-top: 16px; }

.admins-header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.admins-title { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.admins-hint { font-size: 12px; color: var(--text-muted); flex: 1; }
.add-admin-btn { display: flex; align-items: center; gap: 5px; background: var(--bg-hover); border: 1px solid var(--border); border-radius: 6px; color: var(--text-secondary); font-size: 12px; cursor: pointer; padding: 4px 10px; transition: all var(--transition); }
.add-admin-btn:hover { color: var(--accent-blue-2); border-color: rgba(59,130,246,0.4); }

.admins-empty { font-size: 12.5px; color: var(--text-muted); text-align: center; padding: 14px; background: var(--bg-secondary); border-radius: var(--radius-sm); }

.admins-table { display: flex; flex-direction: column; gap: 6px; }

.admins-thead { display: grid; grid-template-columns: 90px 1fr 130px 28px; gap: 8px; padding: 0 0 4px; }
.admins-thead span { font-size: 11px; color: var(--text-muted); font-weight: 600; }

.admins-row { display: grid; grid-template-columns: 90px 1fr 130px 28px; gap: 8px; align-items: center; }
.admins-row .col-name { }
.admins-row .col-email { }
.admins-row .col-feishu { }

.del-btn { display: flex; align-items: center; justify-content: center; width: 26px; height: 26px; background: none; border: 1px solid var(--border); border-radius: 5px; color: var(--text-muted); cursor: pointer; transition: all var(--transition); flex-shrink: 0; }
.del-btn:hover { color: var(--accent-red); border-color: rgba(239,68,68,0.4); background: rgba(239,68,68,0.06); }

.admins-note { margin-top: 8px; font-size: 11.5px; color: var(--text-muted); line-height: 1.5; }
</style>
