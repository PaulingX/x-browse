<template>
  <div class="page-container">
    <!-- 顶部导航 -->
    <van-nav-bar :title="currentDirName" left-arrow @click-left="goBack" fixed placeholder>
      <template #right>
        <van-icon name="replay" size="20" @click="refresh" />
        <van-icon name="grid-o" size="20" style="margin-left: 12px" @click="toggleViewMode" />
      </template>
    </van-nav-bar>

    <!-- 面包屑导航 -->
    <div class="breadcrumb">
      <span class="breadcrumb-item" @click="navigateTo('/')">根目录</span>
      <template v-for="(crumb, index) in breadcrumbs" :key="index">
        <span class="breadcrumb-separator">/</span>
        <span
          class="breadcrumb-item"
          :class="{ active: index === breadcrumbs.length - 1 }"
          @click="navigateTo(crumb.path)"
        >
          {{ crumb.name }}
        </span>
      </template>
    </div>

    <!-- 文件列表 -->
    <div v-if="viewMode === 'grid'" class="grid-layout">
      <div
        v-for="item in files"
        :key="item.name"
        class="folder-card"
        @click="handleClick(item)"
      >
        <div class="file-icon">
          <van-icon
            v-if="item.isDir"
            name="folder-o"
            size="48"
            color="#1989fa"
          />
          <img
            v-else-if="item.thumbnail"
            :src="item.thumbnail"
            :alt="item.name"
            class="file-thumbnail"
            @error="handleImgError"
          />
          <van-icon
            v-else
            :name="getFileIcon(item.ext)"
            size="48"
            :color="getFileColor(item.ext)"
          />
        </div>
        <div class="file-info">
          <div class="file-name" :title="item.name">{{ item.name }}</div>
          <div v-if="!item.isDir" class="file-size">{{ formatSize(item.size) }}</div>
        </div>
      </div>
    </div>

    <!-- 瀑布流模式 -->
    <div v-else class="waterfall">
      <div
        v-for="(item, index) in imageFiles"
        :key="item.name"
        class="waterfall-item"
        @click="openViewer(index)"
      >
        <img :src="item.thumbnail || item.url" :alt="item.name" loading="lazy" />
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && files.length === 0" class="empty-state">
      <van-icon name="folder-o" size="64" color="#dcdee0" />
      <p>此目录为空</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <van-loading type="spinner" />
    </div>

    <!-- 浮动返回按钮 -->
    <div v-if="viewMode === 'waterfall'" class="fab" @click="viewMode = 'grid'">
      <van-icon name="apps-o" size="24" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '@/api'

const router = useRouter()
const route = useRoute()

const engineId = computed(() => Number(route.params.engineId))
const currentPath = ref(route.query.path || '/')

const files = ref([])
const loading = ref(true)
const viewMode = ref('grid')

// 计算属性
const currentDirName = computed(() => {
  const parts = currentPath.value.split('/').filter(Boolean)
  return parts[parts.length - 1] || '根目录'
})

const breadcrumbs = computed(() => {
  const parts = currentPath.value.split('/').filter(Boolean)
  return parts.map((name, index) => ({
    name,
    path: '/' + parts.slice(0, index + 1).join('/')
  }))
})

const imageFiles = computed(() => {
  return files.value.filter((f) => !f.isDir && isImage(f.ext))
})

// 加载文件列表
async function loadFiles() {
  loading.value = true
  try {
    const res = await api.get('/api/files/list', {
      params: {
        engineId: engineId.value,
        path: currentPath.value
      }
    })
    if (res.code === 200) {
      files.value = res.data
    }
  } catch (error) {
    console.error('加载文件列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 点击文件/目录
function handleClick(item) {
  if (item.isDir) {
    // 进入子目录
    currentPath.value = item.path
    router.replace({
      query: { path: item.path }
    })
    loadFiles()
  } else if (isImage(item.ext)) {
    // 打开图片查看器
    const index = imageFiles.value.findIndex((f) => f.name === item.name)
    openViewer(index)
  } else if (isVideo(item.ext)) {
    // 打开视频播放
    openViewer(0)
  }
}

// 打开查看器
function openViewer(index) {
  router.push({
    name: 'Viewer',
    params: { engineId: engineId.value },
    query: {
      path: currentPath.value,
      index
    }
  })
}

// 返回上一级
function goBack() {
  if (currentPath.value === '/') {
    router.push('/')
  } else {
    const parts = currentPath.value.split('/').filter(Boolean)
    parts.pop()
    currentPath.value = parts.length ? '/' + parts.join('/') : '/'
    router.replace({ query: { path: currentPath.value } })
    loadFiles()
  }
}

// 导航到指定路径
function navigateTo(path) {
  currentPath.value = path
  router.replace({ query: { path } })
  loadFiles()
}

// 刷新
function refresh() {
  loadFiles()
}

// 切换视图模式
function toggleViewMode() {
  viewMode.value = viewMode.value === 'grid' ? 'waterfall' : 'grid'
}

// 判断是否为图片
function isImage(ext) {
  const exts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  return exts.includes(ext?.toLowerCase())
}

// 判断是否为视频
function isVideo(ext) {
  const exts = ['mp4', 'avi', 'mkv', 'mov', 'wmv', 'flv', 'webm']
  return exts.includes(ext?.toLowerCase())
}

// 获取文件图标
function getFileIcon(ext) {
  if (isImage(ext)) return 'photo-o'
  if (isVideo(ext)) return 'video-o'
  return 'description'
}

// 获取文件颜色
function getFileColor(ext) {
  if (isImage(ext)) return '#07c160'
  if (isVideo(ext)) return '#ff976a'
  return '#969799'
}

// 格式化文件大小
function formatSize(bytes) {
  if (!bytes) return ''
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let size = bytes
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index++
  }
  return `${size.toFixed(1)} ${units[index]}`
}

// 处理图片加载错误
function handleImgError(e) {
  e.target.src = '/placeholder.png'
}

// 监听路径变化
watch(
  () => route.query.path,
  (newPath) => {
    if (newPath && newPath !== currentPath.value) {
      currentPath.value = newPath
      loadFiles()
    }
  }
)

onMounted(() => {
  loadFiles()
})
</script>

<style scoped>
.breadcrumb {
  position: sticky;
  top: 46px;
  z-index: 10;
}

.grid-layout {
  padding-top: 12px;
}

.file-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-thumbnail {
  width: 48px;
  height: 48px;
  object-fit: cover;
  border-radius: 8px;
}

.file-info {
  text-align: center;
}

.file-name {
  font-size: 13px;
  color: #323233;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 11px;
  color: #969799;
}

.breadcrumb-item.active {
  font-weight: 500;
}
</style>
