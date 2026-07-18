<template>
  <div class="admin-section">
    <van-cell-group inset>
      <van-cell title="缩略图生成" label="关闭后将不生成缩略图">
        <template #right-icon>
          <van-switch v-model="settings.thumbnailEnabled" @change="saveSettings" />
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="数据目录" :value="settings.dataDir" />
    </van-cell-group>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { showToast } from 'vant'

const settings = ref({
  thumbnailEnabled: true,
  dataDir: ''
})

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

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.admin-section {
  padding: 12px;
}
</style>
