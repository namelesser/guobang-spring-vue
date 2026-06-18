<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-brand">
        <div class="login-mark">T</div>
        <div>
          <h1>国邦运输管理系统</h1>
          <p>过磅单核对、OCR 扫描、运价维护与月度报表</p>
        </div>
      </div>

      <div class="login-card">
        <n-h2 style="margin:0">登录控制台</n-h2>
        <n-text depth="3">请输入服务端配置的访问密码。</n-text>
        <n-form style="margin-top:24px" @submit.prevent="handleLogin">
          <n-form-item label="访问密码">
            <n-input
              v-model:value="password"
              type="password"
              size="large"
              show-password-on="click"
              placeholder="请输入密码"
              autofocus
              @keydown.enter="handleLogin"
            />
          </n-form-item>
          <n-button type="primary" size="large" block :disabled="!password" :loading="loading" @click="handleLogin">
            登录
          </n-button>
        </n-form>
      </div>

      <div class="login-panel">
        <div class="panel-grid">
          <div v-for="item in metrics" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useAppStore } from '@/store'

const router = useRouter()
const message = useMessage()
const store = useAppStore()
const password = ref('')
const loading = ref(false)

const metrics = [
  { label: '核对', value: 'Review' },
  { label: '扫描', value: 'OCR' },
  { label: '运价', value: 'Rate' },
  { label: '报表', value: 'Report' },
]

async function handleLogin() {
  if (!password.value || loading.value) return
  loading.value = true
  try {
    await store.login(password.value)
    message.success('登录成功')
    router.replace('/')
  } catch (error: any) {
    message.error(error?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(120deg, rgba(37, 99, 235, 0.18), transparent 34%),
    linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%);
}
.login-container {
  width: 100%;
  max-width: 440px;
  padding: 0 20px;
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}
.login-mark {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  color: #fff;
  font-size: 28px;
  font-weight: 900;
  background: linear-gradient(135deg, #2563eb, #0891b2);
  flex-shrink: 0;
}
h1 {
  font-size: 24px;
  line-height: 1.2;
  margin: 0;
}
p {
  margin: 4px 0 0;
  color: #475569;
  font-size: 14px;
}
.login-card {
  padding: 34px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.14);
}
.login-panel {
  margin-top: 24px;
  padding: 20px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 12px;
  background: rgba(255,255,255,0.7);
  backdrop-filter: blur(16px);
}
.panel-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.panel-grid div {
  padding: 12px 8px;
  border-radius: 8px;
  background: #fff;
  text-align: center;
}
.panel-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
}
.panel-grid strong {
  display: block;
  margin-top: 4px;
  font-size: 16px;
}
</style>
