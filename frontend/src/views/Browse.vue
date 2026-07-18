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

    <!-- 文件列表 - 网格模式 -->
    <van-list
      v-if="viewMode === 'grid'"
      v-model:loading="loadingMore"
      :finished="!hasMore"
      finished-text="没有更多了"
      @load="loadMore"
      :immediate-check="false"
      offset="100"
    >
      <div class="grid-layout">
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
              v-else-if="item.url && isImage(item.ext)"
              :src="item.url"
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
    </van-list>

    <!-- 瀑布流模式 -->
    <div v-else class="waterfall">
      <div
        v-for="(item, index) in imageFiles"
        :key="item.name"
        class="waterfall-item"
        @click="openViewer(index)"
      >
        <img :src="item.url" :alt="item.name" loading="lazy" />
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && files.length === 0" class="empty-state">
      <van-icon name="folder-o" size="64" color="#dcdee0" />
      <p>此目录为空</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading && files.length === 0" class="loading-container">
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
const loadingMore = ref(false)
const viewMode = ref('grid')
const page = ref(1)
const perPage = ref(20)
const hasMore = ref(true)

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

async function loadFiles() {
  loading.value = true
  page.value = 1
  hasMore.value = true
  try {
    const res = await api.get('/api/files/list', {
      params: {
        engineId: engineId.value,
        path: currentPath.value,
        page: page.value,
        perPage: perPage.value
      }
    })
    if (res.code === 200) {
      files.value = res.data
      hasMore.value = res.data.length >= perPage.value
      preloadPageImages()
      preloadNextPage()
    }
  } catch (error) {
    console.error('加载文件列表失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  page.value++
  try {
    const res = await api.get('/api/files/list', {
      params: {
        engineId: engineId.value,
        path: currentPath.value,
        page: page.value,
        perPage: perPage.value
      }
    })
    if (res.code === 200) {
      files.value = [...files.value, ...res.data]
      hasMore.value = res.data.length >= perPage.value
    }
  } catch (error) {
    console.error('加载更多失败:', error)
  } finally {
    loadingMore.value = false
  }
}

function handleClick(item) {
  if (item.isDir) {
    currentPath.value = item.path
    router.replace({ query: { path: item.path } })
    loadFiles()
  } else if (isImage(item.ext)) {
    const index = imageFiles.value.findIndex((f) => f.name === item.name)
    openViewer(index >= 0 ? index : 0)
  } else if (isVideo(item.ext)) {
    const index = files.value.findIndex((f) => f.name === item.name)
    openViewer(index >= 0 ? index : 0)
  }
}

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

function navigateTo(path) {
  currentPath.value = path
  router.replace({ query: { path } })
  loadFiles()
}

function refresh() {
  loadFiles()
}

function toggleViewMode() {
  viewMode.value = viewMode.value === 'grid' ? 'waterfall' : 'grid'
}

function isImage(ext) {
  const exts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  return exts.includes(ext?.toLowerCase())
}

function isVideo(ext) {
  const exts = ['mp4', 'avi', 'mkv', 'mov', 'wmv', 'flv', 'webm']
  return exts.includes(ext?.toLowerCase())
}

function getFileIcon(ext) {
  if (isImage(ext)) return 'photo-o'
  if (isVideo(ext)) return 'video-o'
  return 'description'
}

function getFileColor(ext) {
  if (isImage(ext)) return '#07c160'
  if (isVideo(ext)) return '#ff976a'
  return '#969799'
}

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

function handleImgError(e) {
  e.target.style.display = 'none'
}

function preloadImages(urls) {
  urls.forEach(url => {
    if (url) {
      const img = new Image()
      img.src = url
    }
  })
}

function preloadPageImages() {
  const urls = files.value
    .filter(f => !f.isDir && f.url && isImage(f.ext))
    .map(f => f.url)
  preloadImages(urls)
}

function preloadNextPage() {
  if (!hasMore.value) return
  const nextPage = page.value + 1
  api.get('/api/files/list', {
    params: {
      engineId: engineId.value,
      path: currentPath.value,
      page: nextPage,
      perPage: perPage.value
    }
  }).then(res => {
    if (res.code === 200) {
      const urls = res.data
        .filter(f => !f.isDir && f.url && isImage(f.ext))
        .map(f => f.url)
      preloadImages(urls)
    }
  }).catch(() => {})
}

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
  padding: 8px 16px;
  font-size: 13px;
  color: #969799;
  background: #f7f8fa;
  overflow-x: auto;
  white-space: nowrap;
}

.breadcrumb-item {
  color: #1989fa;
  cursor: pointer;
}

.breadcrumb-item.active {
  color: #323233;
  font-weight: 500;
}

.breadcrumb-separator {
  margin: 0 4px;
  color: #dcdee0;
}

.grid-layout {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 12px;
}

.folder-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 4px;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.folder-card:active {
  background: #f2f3f5;
}

.file-icon {
  width: 100%;
  aspect-ratio: 1;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 8px;
  background: #f7f8fa;
}

.file-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file-info {
  text-align: center;
  width: 100%;
}

.file-name {
  font-size: 12px;
  color: #323233;
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 11px;
  color: #969799;
}

.waterfall {
  column-count: 2;
  column-gap: 8px;
  padding: 8px;
}

.waterfall-item {
  break-inside: avoid;
  margin-bottom: 8px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}

.waterfall-item img {
  width: 100%;
  display: block;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
  color: #969799;
}

.loading-container {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.fab {
  position: fixed;
  bottom: 24px;
  right: 24px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #1989fa;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  z-index: 100;
}
</style>
