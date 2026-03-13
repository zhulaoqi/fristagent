<script setup>
import { ref, onMounted } from 'vue'
import { Bell, Mail } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { notifyApi } from '@/api'

const feishu = ref({ appId: '', appSecret: '' })
const mail = ref({ host: '', port: 587, username: '', password: '', fromName: 'FristAgent' })
const testEmail = ref('')
const testFeishuOpenId = ref('')
const saving = ref(false)
const testingFeishu = ref(false)
const testingMail = ref(false)

onMounted(async () => {
  try {
    const config = await notifyApi.get()
    Object.assign(feishu.value, {
      appId:     config.feishuAppId,
      appSecret: config.feishuAppSecret,
    })
    Object.assign(mail.value, {
      host:     config.smtpHost,
      port:     config.smtpPort,
      username: config.smtpUsername,
      fromName: config.smtpFromName,
    })
  } catch (e) {}
})

const save = async () => {
  saving.value = true
  try {
    await notifyApi.save({
      feishuAppId:     feishu.value.appId,
      feishuAppSecret: feishu.value.appSecret,
      smtpHost:        mail.value.host,
      smtpPort:        mail.value.port,
      smtpUsername:    mail.value.username,
      smtpPassword:    mail.value.password,
      smtpFromName:    mail.value.fromName,
    })
    ElMessage.success('通知配置已保存')
  } finally {
    saving.value = false
  }
}

const testFeishu = async () => {
  if (!testFeishuOpenId.value) { ElMessage.warning('请输入测试目标的飞书 Open ID'); return }
  testingFeishu.value = true
  try {
    const result = await notifyApi.testFeishu(testFeishuOpenId.value)
    if (result.success) {
      ElMessage.success(result.message)
    } else {
      ElMessage.error(result.message)
    }
  } catch (e) {
    ElMessage.error('验证请求失败')
  } finally {
    testingFeishu.value = false
  }
}

const testMail = async () => {
  if (!testEmail.value) { ElMessage.warning('请输入测试邮箱'); return }
  testingMail.value = true
  try {
    const result = await notifyApi.testEmail(testEmail.value)
    if (result.success) {
      ElMessage.success(result.message)
    } else {
      ElMessage.error(result.message)
    }
  } catch (e) {
    ElMessage.error('发送请求失败')
  } finally {
    testingMail.value = false
  }
}
</script>

<template>
  <div class="notify-page">
    <div class="page-header">
      <div>
        <h1>通知配置</h1>
        <p>配置通知渠道凭据。接收人（邮箱 / 飞书 Open ID）在各仓库的「编辑」页面中按人配置</p>
      </div>
    </div>

    <div class="notify-grid">
      <!-- Feishu -->
      <div class="card">
        <div class="card-header-row">
          <div class="card-icon feishu">
            <Bell :size="18" />
          </div>
          <div>
            <div class="card-title">飞书开放平台</div>
            <div class="card-sub">发送个人卡片消息（需要飞书自建应用）</div>
          </div>
        </div>

        <el-form :model="feishu" label-width="100px" label-position="left" style="margin-top:16px">
          <el-form-item label="App ID">
            <el-input v-model="feishu.appId" placeholder="cli_xxxxxx" />
          </el-form-item>
          <el-form-item label="App Secret">
            <el-input v-model="feishu.appSecret" placeholder="xxxxxx" show-password />
          </el-form-item>
        </el-form>

        <div class="card-tips">
          <p>① 在飞书开放平台创建「自建应用」</p>
          <p>② 开启「发送消息」权限（im:message:send_as_bot）</p>
          <p>③ 发布应用，将 App ID / App Secret 填入上方</p>
          <p>④ 在「仓库配置」→ 编辑 → 添加接收人时填写各人的飞书 Open ID</p>
        </div>

        <div class="test-row">
          <el-input v-model="testFeishuOpenId" placeholder="输入测试目标的飞书 Open ID" size="small" />
          <button class="test-btn feishu-btn" :disabled="testingFeishu" @click="testFeishu">
            {{ testingFeishu ? '发送中…' : '发送测试卡片' }}
          </button>
        </div>
      </div>

      <!-- Email -->
      <div class="card">
        <div class="card-header-row">
          <div class="card-icon mail">
            <Mail :size="18" />
          </div>
          <div>
            <div class="card-title">邮件通知</div>
            <div class="card-sub">通过 SMTP 发送 HTML 邮件，含评分和问题摘要</div>
          </div>
        </div>

        <el-form :model="mail" label-width="100px" label-position="left" style="margin-top:16px">
          <el-form-item label="SMTP 服务器">
            <el-input v-model="mail.host" placeholder="smtp.example.com" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="mail.port" :min="1" :max="65535" style="width:100%" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="mail.username" placeholder="noreply@example.com" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="mail.password" show-password />
          </el-form-item>
          <el-form-item label="发件人名称">
            <el-input v-model="mail.fromName" placeholder="FristAgent" />
          </el-form-item>
        </el-form>

        <div class="card-tips">
          <p>发件地址 = 用户名（SMTP 登录邮箱）</p>
          <p>在「仓库配置」→ 编辑 → 添加接收人时填写各人的邮箱地址</p>
        </div>

        <div class="test-row">
          <el-input v-model="testEmail" placeholder="输入测试邮箱" size="small" />
          <button class="test-btn mail-btn" :disabled="testingMail" @click="testMail">
            <Mail :size="13" />{{ testingMail ? '发送中…' : '发送测试邮件' }}
          </button>
        </div>
      </div>
    </div>

    <div class="save-row">
      <el-button type="primary" size="large" :loading="saving" @click="save">保存通知配置</el-button>
    </div>
  </div>
</template>

<style scoped>
.notify-page { padding: 28px; }
.page-header { margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 650; }
.page-header p { font-size: 13px; color: var(--text-muted); margin-top: 3px; }

.notify-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; max-width: 1400px; }

.card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 22px; display: flex; flex-direction: column; gap: 0; }

.card-header-row { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 4px; }
.card-icon { width: 40px; height: 40px; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.card-icon.feishu { background: rgba(59,130,246,0.12); color: var(--accent-blue-2); border: 1px solid rgba(59,130,246,0.2); }
.card-icon.mail { background: rgba(16,185,129,0.1); color: var(--accent-green); border: 1px solid rgba(16,185,129,0.2); }

.card-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.card-sub { font-size: 12px; color: var(--text-muted); margin-top: 3px; }

.card-tips { background: var(--bg-secondary); border-radius: var(--radius-sm); padding: 12px 14px; display: flex; flex-direction: column; gap: 5px; margin-top: 8px; }
.card-tips p { font-size: 12px; color: var(--text-secondary); }

.card-actions { display: flex; margin-top: 14px; }

.test-row { display: flex; gap: 8px; align-items: center; margin-top: 14px; }

.test-btn { display: inline-flex; align-items: center; gap: 6px; padding: 5px 13px; border-radius: 6px; font-size: 12.5px; font-weight: 500; cursor: pointer; white-space: nowrap; transition: all var(--transition); border: 1px solid var(--border); }
.test-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.feishu-btn { background: rgba(59,130,246,0.08); color: var(--accent-blue-2); border-color: rgba(59,130,246,0.25); }
.feishu-btn:not(:disabled):hover { background: rgba(59,130,246,0.15); border-color: rgba(59,130,246,0.45); }
.mail-btn { background: rgba(16,185,129,0.07); color: var(--accent-green); border-color: rgba(16,185,129,0.22); }
.mail-btn:not(:disabled):hover { background: rgba(16,185,129,0.14); border-color: rgba(16,185,129,0.4); }

/* el-input-number 的 +/- 按钮暗色主题覆盖 */
:deep(.el-input-number__decrease),
:deep(.el-input-number__increase) {
  background: var(--bg-secondary);
  border-color: var(--border);
  color: var(--text-secondary);
}
:deep(.el-input-number__decrease:hover),
:deep(.el-input-number__increase:hover) {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.save-row { display: flex; justify-content: flex-end; margin-top: 20px; max-width: 1400px; }
</style>
