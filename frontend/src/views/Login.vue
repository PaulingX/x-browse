<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <img src="/favicon.svg" alt="Logo" class="logo" />
        <h1 class="title">x-browse</h1>
        <p class="subtitle">多媒体浏览系统</p>
      </div>

      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.username"
            name="username"
            label="用户名"
            placeholder="请输入用户名"
            :rules="[{ required: true, message: '请输入用户名' }]"
          />
          <van-field
            v-model="form.password"
            type="password"
            name="password"
            label="密码"
            placeholder="请输入密码"
            :rules="[{ required: true, message: '请输入密码' }]"
          />
        </van-cell-group>

        <div class="submit-btn">
          <van-button round block type="primary" native-type="submit" :loading="loading">
            登录
          </van-button>
        </div>
      </van-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { showToast } from 'vant'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = ref({
  username: '',
  password: ''
})
const loading = ref(false)

// 登录提交
async function onSubmit() {
  loading.value = true
  try {
    const success = await userStore.login(form.value.username, form.value.password)
    if (success) {
      showToast('登录成功')
      const redirect = route.query.redirect || '/'
      router.push(redirect)
    } else {
      showToast('用户名或密码错误')
    }
  } catch (error) {
    showToast('登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: white;
  border-radius: 16px;
  padding: 40px 24px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo {
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 8px;
}

.subtitle {
  font-size: 14px;
  color: #969799;
}

.submit-btn {
  margin-top: 24px;
  padding: 0 16px;
}
</style>
