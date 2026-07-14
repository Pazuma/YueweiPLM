<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({
  username: 'engineer01',
  password: 'plm123456'
})

const accounts = [
  'engineer01 / plm123456 - 工程部测试账号',
  'engineer02 / plm123456 - 工程部测试账号'
]

async function handleLogin() {
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push(String(route.query.redirect || '/dashboard'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-shell">
    <section class="login-panel">
      <div class="login-panel__brand">
        <h1>Yuewei PLM</h1>
        <p>手机壳制造业产品研发协同系统</p>
      </div>

      <el-form :model="form" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">登录</el-button>
      </el-form>

      <div class="login-panel__tips">
        <strong>后端 M1 测试账号</strong>
        <ul>
          <li v-for="item in accounts" :key="item">{{ item }}</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-shell {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.16), transparent 30%),
    linear-gradient(180deg, #eef4ff 0%, #f4f6f8 42%, #ebf0f7 100%);
}

.login-panel {
  width: min(440px, 100%);
  padding: 32px;
  border: 1px solid rgba(220, 223, 230, 0.9);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--plm-shadow-lg);
}

.login-panel__brand h1 {
  margin: 0;
  font-size: 28px;
}

.login-panel__brand p {
  margin: 10px 0 24px;
  color: var(--plm-color-text-secondary);
}

.login-panel__tips {
  margin-top: 20px;
  color: var(--plm-color-text-secondary);
  font-size: 13px;
}

.login-panel__tips ul {
  padding-left: 18px;
}
</style>
