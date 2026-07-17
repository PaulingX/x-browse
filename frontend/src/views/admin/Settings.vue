<template>
  <div class="admin-section">
    <van-cell-group inset>
      <van-cell title="缩略图生成" label="关闭后将不生成缩略图缓存">
        <template #right-icon>
          <van-switch v-model="settings.thumbnailEnabled" @change="saveSettings" />
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="缓存目录" :value="settings.cacheDir" />
      <van-cell title="数据目录" :value="settings.dataDir" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="缓存大小" :value="formatSize(cacheInfo.cacheSize || 0)" is-link @click="loadCacheInfo" />
    </van-cell-group>

    <div class="btn-group">
      <van-button type="danger" round block @click="clearCache">
        清空缓存
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { showToast, showConfirmDialog } from 'vant'

const settings = ref({
  thumbnailEnabled: true,
  cacheDir: '',
  dataDir: ''
})

const cacheInfo = ref({
  cacheSize: 0
})

// 加载设置
async function loadSettings() {
  try {
    const res = await api.get('/api/settings')
    if (res.code === 200) {
      settings.value = res.data
    }
  } catch (error) {
    console.error('加载设置失败:', error)
  }
}

// 加载缓存信息
async function loadCacheInfo() {
  try {
    const res = await api.get('/api/settings/cache/info')
    if (res.code === 200) {
      cacheInfo.value = res.data
    }
  } catch (error) {
    console.error('加载缓存信息失败:', error)
  }
}

// 保存设置
async function saveSettings() {
  try {
    const res = await api.put('/api/settings', {
      thumbnailEnabled: settings.value.thumbnailEnabled
    })
    if (res.code === 200) {
      showToast('设置已保存')
    }
  } catch (error) {
    showToast('保存失败')
  }
}

// 清空缓存
async function clearCache() {
  try {
    await showConfirmDialog({
      title: '确认清空',
      message: '确定要清空所有缓存吗？此操作不可恢复。'
    })
    const res = await api.delete('/api/settings/cache')
    if (res.code === 200) {
      showToast('缓存已清空')
      loadCacheInfo()
    }
  } catch (error) {
    if (error !== 'cancel') {
      showToast('清空失败')
    }
  }
}

// 格式化文件大小
function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let index = 0
  let size = bytes
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index++
  }
  return `${size.toFixed(2)} ${units[index]}`
}

onMounted(() => {
  loadSettings()
  loadCacheInfo()
})
</script>

<style scoped>
.admin-section {
  padding: 12px;
}

.btn-group {
  margin-top: 20px;
  padding: 0 16px;
}
</style>
