<template>
  <div class="page-container">
    <!-- 顶部导航 -->
    <van-nav-bar title="x-browse" fixed placeholder>
      <template #right>
        <van-icon name="search" size="20" @click="showSearch = true" />
        <van-icon name="setting-o" size="20" style="margin-left: 16px" @click="goSettings" />
      </template>
    </van-nav-bar>

    <!-- 目录卡片列表 -->
    <div class="grid-layout">
      <div
        v-for="dir in directories"
        :key="dir.id"
        class="folder-card"
        @click="goBrowse(dir)"
      >
        <div class="folder-icon">
          <van-icon name="folder-o" size="48" color="#1989fa" />
        </div>
        <div class="folder-info">
          <div class="folder-name">{{ dir.name || dir.path }}</div>
          <div class="folder-path">{{ dir.path }}</div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && directories.length === 0" class="empty-state">
      <van-icon name="folder-o" size="64" color="#dcdee0" />
      <p>暂无浏览目录</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <van-loading type="spinner" />
    </div>

    <!-- 搜索弹窗 -->
    <van-popup v-model:show="showSearch" position="bottom" round>
      <div class="search-popup">
        <van-search
          v-model="searchText"
          placeholder="搜索文件夹"
          autofocus
          @search="onSearch"
        />
        <div class="search-results">
          <div
            v-for="dir in filteredDirectories"
            :key="dir.id"
            class="search-item"
            @click="goBrowse(dir)"
          >
            <van-icon name="folder-o" color="#1989fa" />
            <span>{{ dir.name || dir.path }}</span>
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import api from '@/api'

const router = useRouter()
const userStore = useUserStore()

const directories = ref([])
const loading = ref(true)
const showSearch = ref(false)
const searchText = ref('')

// 过滤后的目录
const filteredDirectories = computed(() => {
  if (!searchText.value) return directories.value
  const keyword = searchText.value.toLowerCase()
  return directories.value.filter(
    (dir) =>
      dir.name?.toLowerCase().includes(keyword) ||
      dir.path?.toLowerCase().includes(keyword)
  )
})

// 加载目录列表
async function loadDirectories() {
  try {
    const res = await api.get('/api/directories')
    if (res.code === 200) {
      // 根据用户权限过滤目录
      if (userStore.isAdmin) {
        directories.value = res.data
      } else {
        const userDirs = userStore.userInfo?.directoryIds || []
        directories.value = res.data.filter((dir) => userDirs.includes(dir.id))
      }
    }
  } catch (error) {
    console.error('加载目录失败:', error)
  } finally {
    loading.value = false
  }
}

// 进入目录浏览
function goBrowse(dir) {
  router.push({
    name: 'Browse',
    params: { engineId: dir.engineId },
    query: { path: dir.path }
  })
}

// 跳转设置页
function goSettings() {
  router.push('/settings')
}

// 搜索
function onSearch() {
  showSearch.value = false
}

onMounted(() => {
  loadDirectories()
})
</script>

<style scoped>
.folder-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 12px;
}

.folder-icon {
  margin-bottom: 12px;
}

.folder-info {
  text-align: center;
  width: 100%;
}

.folder-name {
  font-size: 14px;
  font-weight: 500;
  color: #323233;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-path {
  font-size: 12px;
  color: #969799;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-popup {
  padding: 16px;
  max-height: 60vh;
}

.search-results {
  max-height: 40vh;
  overflow-y: auto;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}

.search-item:active {
  background-color: #f5f5f5;
}
</style>
