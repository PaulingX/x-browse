import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api'

/**
 * 用户状态管理
 */
export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.admin === true)
  const displayName = computed(() => userInfo.value?.displayName || userInfo.value?.username || '用户')

  // 登录
  async function login(username, password) {
    try {
      const res = await api.post('/api/auth/login', { username, password })
      if (res.code === 200) {
        token.value = res.data.token
        userInfo.value = res.data
        localStorage.setItem('token', res.data.token)
        return true
      }
      return false
    } catch (error) {
      console.error('登录失败:', error)
      return false
    }
  }

  // 登出
  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  // 获取用户信息
  async function fetchUserInfo() {
    if (!token.value) return
    try {
      const res = await api.get('/api/auth/me')
      if (res.code === 200) {
        userInfo.value = res.data
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
      logout()
    }
  }

  // 修改密码
  async function changePassword(oldPassword, newPassword) {
    const res = await api.post('/api/auth/change-password', { oldPassword, newPassword })
    return res.code === 200
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isAdmin,
    displayName,
    login,
    logout,
    fetchUserInfo,
    changePassword
  }
})
