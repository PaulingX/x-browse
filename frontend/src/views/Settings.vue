<template>
  <div class="page-container">
    <van-nav-bar title="设置" left-arrow @click-left="goBack" fixed placeholder />

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="账号信息" is-link @click="showAccount = true" />
      <van-cell v-if="userStore.isAdmin" title="系统管理" is-link @click="goAdmin" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="修改密码" is-link @click="showPassword = true" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="退出登录" @click="handleLogout" style="color: #ee0a24" />
    </van-cell-group>

    <!-- 账号信息弹窗 -->
    <van-popup v-model:show="showAccount" position="bottom" round>
      <div class="popup-content">
        <h3>账号信息</h3>
        <van-cell-group>
          <van-cell title="用户名" :value="userStore.userInfo?.username" />
          <van-cell title="显示名称" :value="userStore.userInfo?.displayName" />
          <van-cell title="角色" :value="userStore.isAdmin ? '管理员' : '普通用户'" />
        </van-cell-group>
      </div>
    </van-popup>

    <!-- 修改密码弹窗 -->
    <van-popup v-model:show="showPassword" position="bottom" round>
      <div class="popup-content">
        <h3>修改密码</h3>
        <van-form @submit="onChangePassword">
          <van-cell-group inset>
            <van-field
              v-model="passwordForm.oldPassword"
              type="password"
              label="原密码"
              placeholder="请输入原密码"
              :rules="[{ required: true, message: '请输入原密码' }]"
            />
            <van-field
              v-model="passwordForm.newPassword"
              type="password"
              label="新密码"
              placeholder="请输入新密码"
              :rules="[
                { required: true, message: '请输入新密码' },
                { validator: (v) => v.length >= 6, message: '密码长度至少6位' }
              ]"
            />
            <van-field
              v-model="passwordForm.confirmPassword"
              type="password"
              label="确认密码"
              placeholder="请再次输入新密码"
              :rules="[
                { required: true, message: '请确认密码' },
                { validator: (v) => v === passwordForm.newPassword, message: '两次密码不一致' }
              ]"
            />
          </van-cell-group>
          <div class="submit-btn">
            <van-button round block type="primary" native-type="submit" :loading="loading">
              确认修改
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { showToast } from 'vant'

const router = useRouter()
const userStore = useUserStore()

const showAccount = ref(false)
const showPassword = ref(false)
const loading = ref(false)

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 返回
function goBack() {
  router.back()
}

// 跳转管理页面
function goAdmin() {
  router.push('/admin')
}

// 修改密码
async function onChangePassword() {
  loading.value = true
  try {
    const success = await userStore.changePassword(
      passwordForm.value.oldPassword,
      passwordForm.value.newPassword
    )
    if (success) {
      showToast('密码修改成功')
      showPassword.value = false
      passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    }
  } catch (error) {
    showToast(error.message || '修改失败')
  } finally {
    loading.value = false
  }
}

// 退出登录
function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.popup-content {
  padding: 20px;
  max-height: 80vh;
}

.popup-content h3 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  text-align: center;
}

.submit-btn {
  margin-top: 20px;
  padding: 0 16px;
}
</style>
