<template>
  <div class="admin-section">
    <div class="section-header">
      <span>用户管理</span>
      <van-button type="primary" size="small" @click="showAdd = true">
        <van-icon name="plus" /> 添加
      </van-button>
    </div>

    <van-cell-group inset>
      <van-cell
        v-for="user in users"
        :key="user.id"
        :title="user.username"
        :label="user.displayName || user.username"
        is-link
        @click="editUser(user)"
      >
        <template #label>
          <div class="user-info">
            <span>{{ user.displayName || user.username }}</span>
            <van-tag v-if="user.admin" type="danger" size="small">管理员</van-tag>
            <van-tag v-else type="primary" size="small">普通用户</van-tag>
          </div>
        </template>
        <template #right-icon>
          <div class="action-btns">
            <van-button size="mini" plain @click.stop="showResetPwdDialog(user)">重置密码</van-button>
            <van-button size="mini" plain type="primary" @click.stop="showDirPermDialog(user)">权限</van-button>
            <van-button type="danger" size="mini" plain @click.stop="deleteUser(user)">删除</van-button>
          </div>
        </template>
      </van-cell>
    </van-cell-group>

    <div v-if="users.length === 0" class="empty-state">
      <p>暂无用户</p>
    </div>

    <!-- 添加/编辑用户弹窗 -->
    <van-popup v-model:show="showAdd" position="bottom" round>
      <div class="popup-content">
        <h3>{{ editingUser ? '编辑用户' : '添加用户' }}</h3>
        <van-form @submit="onSubmit">
          <van-cell-group inset>
            <van-field
              v-model="form.username"
              label="用户名"
              placeholder="请输入用户名"
              :disabled="!!editingUser"
              :rules="[{ required: true, message: '请输入用户名' }]"
            />
            <van-field
              v-if="!editingUser"
              v-model="form.password"
              type="password"
              label="密码"
              placeholder="请输入密码"
              :rules="[
                { required: true, message: '请输入密码' },
                { validator: (v) => v.length >= 6, message: '密码长度至少6位' }
              ]"
            />
            <van-field
              v-model="form.displayName"
              label="显示名称"
              placeholder="如：张三"
            />
            <van-field label="管理员">
              <template #input>
                <van-switch v-model="form.admin" size="20" />
              </template>
            </van-field>
            <van-field v-if="editingUser" label="启用">
              <template #input>
                <van-switch v-model="form.enabled" size="20" />
              </template>
            </van-field>
          </van-cell-group>
          <div class="submit-btn">
            <van-button round block type="primary" native-type="submit" :loading="submitting">
              保存
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 重置密码弹窗 -->
    <van-popup v-model:show="showResetPwd" position="bottom" round>
      <div class="popup-content">
        <h3>重置密码</h3>
        <van-form @submit="onResetPassword">
          <van-cell-group inset>
            <van-field
              v-model="resetPwdForm.newPassword"
              type="password"
              label="新密码"
              placeholder="请输入新密码"
              :rules="[
                { required: true, message: '请输入新密码' },
                { validator: (v) => v.length >= 6, message: '密码长度至少6位' }
              ]"
            />
          </van-cell-group>
          <div class="submit-btn">
            <van-button round block type="primary" native-type="submit" :loading="resetting">
              确认重置
            </van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 目录权限弹窗 -->
    <van-popup v-model:show="showDirPerm" position="bottom" round>
      <div class="popup-content">
        <h3>目录权限 - {{ editingUser?.username }}</h3>
        <van-checkbox-group v-model="selectedDirs">
          <van-cell-group inset>
            <van-cell
              v-for="dir in allDirectories"
              :key="dir.id"
              :title="dir.name || dir.path"
              clickable
              @click="toggleDir(dir.id)"
            >
              <template #right-icon>
                <van-checkbox :name="dir.id" />
              </template>
            </van-cell>
          </van-cell-group>
        </van-checkbox-group>
        <div class="submit-btn">
          <van-button round block type="primary" @click="saveDirPerm" :loading="savingPerm">
            保存权限
          </van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { showToast, showConfirmDialog } from 'vant'

const users = ref([])
const allDirectories = ref([])
const showAdd = ref(false)
const showResetPwd = ref(false)
const showDirPerm = ref(false)
const editingUser = ref(null)
const submitting = ref(false)
const resetting = ref(false)
const savingPerm = ref(false)
const selectedDirs = ref([])

const form = ref({
  username: '',
  password: '',
  displayName: '',
  admin: false,
  enabled: true
})

const resetPwdForm = ref({
  newPassword: ''
})

// 加载数据
async function loadData() {
  try {
    const [usersRes, dirsRes] = await Promise.all([
      api.get('/api/users'),
      api.get('/api/directories')
    ])
    if (usersRes.code === 200) users.value = usersRes.data
    if (dirsRes.code === 200) allDirectories.value = dirsRes.data
  } catch (error) {
    console.error('加载数据失败:', error)
  }
}

// 编辑用户
function editUser(user) {
  editingUser.value = user
  form.value = {
    username: user.username,
    password: '',
    displayName: user.displayName || '',
    admin: user.admin,
    enabled: user.enabled
  }
  showAdd.value = true
}

// 删除用户
async function deleteUser(user) {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除用户 "${user.username}" 吗？`
    })
    const res = await api.delete(`/api/users/${user.id}`)
    if (res.code === 200) {
      showToast('删除成功')
      loadData()
    }
  } catch (error) {
    if (error !== 'cancel') {
      showToast('删除失败')
    }
  }
}

// 提交表单
async function onSubmit() {
  submitting.value = true
  try {
    let res
    if (editingUser.value) {
      res = await api.put(`/api/users/${editingUser.value.id}`, {
        displayName: form.value.displayName,
        enabled: form.value.enabled
      })
    } else {
      res = await api.post('/api/users', form.value)
    }
    if (res.code === 200) {
      showToast(editingUser.value ? '更新成功' : '添加成功')
      showAdd.value = false
      editingUser.value = null
      form.value = { username: '', password: '', displayName: '', admin: false, enabled: true }
      loadData()
    }
  } catch (error) {
    showToast('操作失败')
  } finally {
    submitting.value = false
  }
}

// 重置密码
function showResetPwdDialog(user) {
  editingUser.value = user
  resetPwdForm.value = { newPassword: '' }
  showResetPwd.value = true
}

async function onResetPassword() {
  resetting.value = true
  try {
    const res = await api.post(`/api/users/${editingUser.value.id}/reset-password`, {
      newPassword: resetPwdForm.value.newPassword
    })
    if (res.code === 200) {
      showToast('密码重置成功')
      showResetPwd.value = false
    }
  } catch (error) {
    showToast('重置失败')
  } finally {
    resetting.value = false
  }
}

// 目录权限
function showDirPermDialog(user) {
  editingUser.value = user
  selectedDirs.value = user.directoryIds || []
  showDirPerm.value = true
}

function toggleDir(dirId) {
  const index = selectedDirs.value.indexOf(dirId)
  if (index > -1) {
    selectedDirs.value.splice(index, 1)
  } else {
    selectedDirs.value.push(dirId)
  }
}

async function saveDirPerm() {
  savingPerm.value = true
  try {
    const res = await api.put(`/api/users/${editingUser.value.id}/directories`, selectedDirs.value)
    if (res.code === 200) {
      showToast('权限保存成功')
      showDirPerm.value = false
      loadData()
    }
  } catch (error) {
    showToast('保存失败')
  } finally {
    savingPerm.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.admin-section {
  padding: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  font-size: 16px;
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.popup-content {
  padding: 20px;
  max-height: 80vh;
  overflow-y: auto;
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
