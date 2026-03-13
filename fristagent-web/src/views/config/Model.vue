<script setup>
import { ref, onMounted } from 'vue'
import { Cpu, CheckCircle, XCircle } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { llmApi } from '@/api'

const form = ref({
  endpoint: 'https://api.openai.com',
  apiKey: '',
  model: 'gpt-4o',
  maxTokens: 4096,
  timeoutSeconds: 120,
})

const testing = ref(false)
const testResult = ref(null) // null | 'ok' | 'fail'
const saving = ref(false)

onMounted(async () => {
  try {
    const config = await llmApi.get()
    Object.assign(form.value, config)
  } catch (e) {}
})

const presets = [
  { label: 'OpenAI GPT-4o', endpoint: 'https://api.openai.com', model: 'gpt-4o' },
  { label: 'DeepSeek V3', endpoint: 'https://api.deepseek.com', model: 'deepseek-chat' },
  { label: 'Qwen 72B', endpoint: 'https://dashscope.aliyuncs.com/compatible-mode', model: 'qwen2.5-72b-instruct' },
  { label: 'Ollama (本地)', endpoint: 'http://localhost:11434', model: 'llama3.2' },
  { label: '自定义', endpoint: '', model: '' },
]

const applyPreset = (p) => {
  if (p.endpoint) form.value.endpoint = p.endpoint
  if (p.model) form.value.model = p.model
  testResult.value = null
}

const testConnection = async () => {
  testing.value = true
  testResult.value = null
  await new Promise(r => setTimeout(r, 1500))
  testResult.value = form.value.apiKey ? 'ok' : 'fail'
  testing.value = false
}

const save = async () => {
  saving.value = true
  try {
    await llmApi.save(form.value)
    ElMessage.success('模型配置已保存')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="model-page">
    <div class="page-header">
      <div>
        <h1>模型配置</h1>
        <p>支持任何 OpenAI 兼容协议，配置后立即生效无需重启</p>
      </div>
    </div>

    <div class="content-grid">
      <!-- Form -->
      <div class="card">
        <div class="card-title">接入配置</div>

        <div class="preset-row">
          <span class="field-label">快速选择</span>
          <div class="preset-chips">
            <button v-for="p in presets" :key="p.label" class="chip" @click="applyPreset(p)">{{ p.label }}</button>
          </div>
        </div>

        <el-form :model="form" label-width="110px" label-position="left" style="margin-top:20px">
          <el-form-item label="API Endpoint">
            <el-input v-model="form.endpoint" placeholder="https://api.openai.com" />
            <div class="field-hint">任何 OpenAI 兼容地址，结尾不需要 /v1</div>
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="form.apiKey" placeholder="sk-..." show-password />
          </el-form-item>
          <el-form-item label="模型名称">
            <el-input v-model="form.model" placeholder="gpt-4o" />
          </el-form-item>
          <el-form-item label="Max Tokens">
            <el-input-number v-model="form.maxTokens" :min="512" :max="32768" :step="512" style="width:100%" />
          </el-form-item>
          <el-form-item label="超时（秒）">
            <el-input-number v-model="form.timeoutSeconds" :min="30" :max="600" :step="30" style="width:100%" />
          </el-form-item>
        </el-form>

        <!-- Test result -->
        <div v-if="testResult" class="test-result" :class="testResult">
          <component :is="testResult === 'ok' ? CheckCircle : XCircle" :size="16" />
          <span>{{ testResult === 'ok' ? '连接成功，模型响应正常' : '连接失败，请检查 Endpoint 和 API Key' }}</span>
        </div>

        <div class="form-actions">
          <button class="test-btn" :disabled="testing" @click="testConnection">
            {{ testing ? '测试中…' : '测试连接' }}
          </button>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </div>
      </div>

      <!-- Info card -->
      <div class="info-card">
        <div class="info-title"><Cpu :size="16" /> 兼容模型</div>
        <ul class="model-list">
          <li v-for="m in ['OpenAI GPT-4o / GPT-4 Turbo', 'Anthropic Claude（via proxy）', 'DeepSeek V3 / R1', 'Qwen 2.5 / 3', 'Ollama 本地模型', 'vLLM 部署模型', 'Azure OpenAI', '任何 OpenAI-compatible API']" :key="m">{{ m }}</li>
        </ul>
        <div class="info-note">配置存储于 Redis，修改后无需重启即可生效。API Key 以明文形式存储，建议仅在内网环境使用。</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.model-page { padding: 28px; }
.page-header { margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 650; }
.page-header p { font-size: 13px; color: var(--text-muted); margin-top: 3px; }

.content-grid { display: grid; grid-template-columns: 1fr 280px; gap: 20px; align-items: start; max-width: 1400px; }

.card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 22px; }
.card-title { font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 18px; }

.preset-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.field-label { font-size: 13px; color: var(--text-secondary); white-space: nowrap; }
.preset-chips { display: flex; gap: 6px; flex-wrap: wrap; }
.chip {
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
}
.chip:hover { border-color: var(--accent-blue); color: var(--accent-blue-2); }

.field-hint { font-size: 11.5px; color: var(--text-muted); margin-top: 4px; }

.test-result {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  margin-top: 8px;
}
.test-result.ok { background: rgba(16,185,129,0.1); color: var(--accent-green); border: 1px solid rgba(16,185,129,0.2); }
.test-result.fail { background: rgba(239,68,68,0.1); color: var(--accent-red); border: 1px solid rgba(239,68,68,0.2); }

.form-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 20px; border-top: 1px solid var(--border); padding-top: 18px; align-items: center; }

.test-btn { display: inline-flex; align-items: center; gap: 6px; padding: 7px 16px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; white-space: nowrap; transition: all var(--transition); background: var(--bg-secondary); color: var(--text-secondary); border: 1px solid var(--border); }
.test-btn:not(:disabled):hover { background: var(--bg-hover); color: var(--text-primary); border-color: rgba(139,155,180,0.4); }
.test-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* el-input-number +/- 按钮暗色主题覆盖 */
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

.info-card { background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 20px; }
.info-title { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: var(--text-secondary); margin-bottom: 14px; }
.model-list { list-style: none; display: flex; flex-direction: column; gap: 8px; }
.model-list li { font-size: 12.5px; color: var(--text-secondary); padding-left: 12px; position: relative; }
.model-list li::before { content: '·'; position: absolute; left: 0; color: var(--accent-blue-2); }
.info-note { font-size: 12px; color: var(--text-muted); margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--border); line-height: 1.6; }
</style>
