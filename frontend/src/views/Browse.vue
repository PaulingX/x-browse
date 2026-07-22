<template>
  <div class="page-container">
    <!-- 顶部导航 -->
    <van-nav-bar :title="currentDirName" left-arrow @click-left="goBack" fixed placeholder>
      <template #right>
        <van-icon name="replay" size="20" @click="refresh" />
        <van-icon v-if="isRoot" name="sort" size="20" style="margin-left: 12px" @click="cycleSortMode" />
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

    <!-- 搜索栏 -->
    <div class="search-bar" v-if="files.length > 0">
      <van-search
        v-model="searchText"
        placeholder="搜索当前目录"
        shape="round"
        background="transparent"
        :clearable="true"
      />
      <div v-if="isRoot" class="sort-indicator" @click="cycleSortMode">
        <van-icon name="sort" size="14" />
        <span>{{ sortLabel }}</span>
      </div>
    </div>

    <!-- 文件列表 - 网格模式 -->
    <div v-if="viewMode === 'grid'" class="grid-container">
      <div class="grid-layout">
        <div
          v-for="item in displayFiles"
          :key="item.name"
          class="folder-card"
          :data-dir-path="item.isDir ? item.path : undefined"
          @click="handleClick(item)"
        >
          <div class="file-icon">
            <img
              v-if="item.isDir && dirPreviewSrc(item)"
              :src="dirPreviewSrc(item)"
              :alt="item.name"
              class="file-thumbnail dir-preview"
              loading="lazy"
              decoding="async"
              @error="onDirThumbError($event, item)"
            />
            <van-icon
              v-else-if="item.isDir"
              name="folder-o"
              size="48"
              color="#1989fa"
            />
            <img
              v-else-if="isImage(item.ext) && listThumbSrc(item)"
              :src="getCachedImage(listThumbSrc(item))?.src || listThumbSrc(item)"
              :alt="item.name"
              class="file-thumbnail"
              loading="lazy"
              decoding="async"
              @load="onThumbLoad($event, listThumbSrc(item))"
              @error="handleImgError"
            />
            <div v-else-if="isVideo(item.ext) && listThumbSrc(item)" class="video-thumb-wrap">
              <img
                :src="listThumbSrc(item)"
                :alt="item.name"
                class="file-thumbnail"
                loading="lazy"
                decoding="async"
                @error="handleImgError"
              />
              <van-icon name="play-circle-o" class="video-play-badge" />
            </div>
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
      <div ref="sentinelRef" class="load-sentinel">
        <van-loading v-if="loadingMore" type="spinner" size="24" />
        <span v-else-if="!hasMore && displayFiles.length > 0" class="no-more-text">没有更多了</span>
      </div>
    </div>

    <!-- 瀑布流模式 -->
    <div v-else class="waterfall">
      <div
        v-for="(item, index) in displayImageFiles"
        :key="item.name"
        class="waterfall-item"
        @click="openViewer(item)"
      >
        <img
          :src="listThumbSrc(item)"
          :alt="item.name"
          loading="lazy"
          decoding="async"
        />
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && displayFiles.length === 0" class="empty-state">
      <van-icon name="folder-o" size="64" color="#dcdee0" />
      <p>{{ searchText ? '没有匹配的文件' : '此目录为空' }}</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading && files.length === 0" class="loading-container">
      <van-loading type="spinner" />
    </div>

    <!-- 浮动返回按钮 -->
    <div v-if="viewMode === 'waterfall'" class="fab fab-grid" @click="viewMode = 'grid'">
      <van-icon name="apps-o" size="24" />
    </div>
    <transition name="fab-fade">
      <div v-if="showBackButton" class="fab fab-back" @click="goBack">
        <van-icon name="arrow-left" size="20" />
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '@/api'
import { isImage, isVideo, formatSize, getFileIcon, getFileColor } from '@/utils/file'

const router = useRouter()
const route = useRoute()

const engineId = computed(() => Number(route.params.engineId))
const mediaTypeKey = computed(() => `xbrowse_media_${engineId.value}`)
const storageKey = computed(() => `xbrowse_root_${engineId.value}`)
const rootPath = ref(sessionStorage.getItem(storageKey.value) || route.query.path || '/')
const currentPath = ref(route.query.path || '/')
const mediaType = ref(
  route.query.mediaType
  || sessionStorage.getItem(mediaTypeKey.value)
  || 'all'
)

const files = ref([])
const loading = ref(true)
const loadingMore = ref(false)
const viewMode = ref('grid')
const page = ref(1)
const perPage = ref(20)
const hasMore = ref(true)
const dirThumbnails = ref({})
const pendingDirFetch = ref(false)
const searchText = ref('')
const searchResults = ref([])
const searching = ref(false)
const sentinelRef = ref(null)
const showBackButton = ref(false)
const sortMode = ref(localStorage.getItem('xbrowse_sort') || 'name_asc')
const isRoot = computed(() => currentPath.value === rootPath.value)

function browseQuery(extra = {}) {
  return {
    path: currentPath.value,
    mediaType: mediaType.value || 'all',
    ...extra
  }
}
let searchTimer = null
let dirObserver = null
let dirObserverTimer = null
let scrollObserver = null
let canLoadMoreOnIntersect = true
const scrollPositions = new Map()
const scrollStorageKey = computed(() => `xbrowse_scroll_${engineId.value}`)

function saveScrollPositions() {
  const obj = {}
  scrollPositions.forEach((v, k) => { obj[k] = v })
  sessionStorage.setItem(scrollStorageKey.value, JSON.stringify(obj))
}

function loadScrollPositions() {
  try {
    const raw = sessionStorage.getItem(scrollStorageKey.value)
    if (raw) {
      const obj = JSON.parse(raw)
      Object.entries(obj).forEach(([k, v]) => scrollPositions.set(k, v))
    }
  } catch {}
}

const currentDirName = computed(() => {
  const parts = currentPath.value.split('/').filter(Boolean)
  return parts[parts.length - 1] || '根目录'
})

const displayFiles = computed(() => {
  return searchText.value ? searchResults.value : files.value
})

const displayImageFiles = computed(() => {
  return displayFiles.value.filter((f) => !f.isDir && isImage(f.ext))
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

async function fetchPage(path, targetPage, { append = false, restoreY, preload = false } = {}) {
  try {
    const res = await api.get('/api/files/list', {
      params: buildListParams(path, targetPage)
    })
    if (res.code !== 200) return
    files.value = append ? [...files.value, ...res.data] : res.data
    hasMore.value = res.data.length >= perPage.value
    if (!append) {
      dirThumbnails.value = {}
    }
    if (preload) {
      preloadPageImages()
      preloadNextPage()
    }
    observeDirCards()
    nextTick(() => {
      if (restoreY != null) window.scrollTo(0, restoreY)
      setupSentinelObserver()
    })
  } catch (error) {
    console.error('加载文件列表失败:', error)
  }
}

async function loadFiles() {
  loading.value = true
  page.value = 1
  hasMore.value = true
  canLoadMoreOnIntersect = true
  try {
    await fetchPage(currentPath.value, page.value, { preload: true })
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value || searchText.value) return
  loadingMore.value = true
  page.value++
  try {
    await fetchPage(currentPath.value, page.value, { append: true })
  } finally {
    loadingMore.value = false
  }
}

function handleClick(item) {
  if (item.isDir) {
    const pos = { y: window.scrollY, page: page.value }
    scrollPositions.set(currentPath.value, pos)
    saveScrollPositions()
    currentPath.value = item.path
    showBackButton.value = true
    searchText.value = ''
    searchResults.value = []
    router.push({ query: browseQuery({ path: item.path }) })
    loadFiles()
    window.scrollTo(0, 0)
  } else if (isImage(item.ext) || isVideo(item.ext)) {
    // 与 Viewer 使用同一套媒体列表，用文件名定位，避免 index 错位
    openViewer(item)
  }
}

function openViewer(item) {
  router.push({
    name: 'Viewer',
    params: { engineId: engineId.value },
    query: {
      path: currentPath.value,
      file: item.name,
      mediaType: mediaType.value || 'all'
    }
  })
}

function buildListParams(path, targetPage) {
  return {
    engineId: engineId.value,
    path,
    page: targetPage,
    perPage: perPage.value,
    sort: path === rootPath.value ? sortMode.value : 'name_asc',
    mediaType: mediaType.value || 'all'
  }
}

function resetListState() {
  files.value = []
  dirThumbnails.value = {}
  window.scrollTo(0, 0)
  loadFiles()
}

async function restorePath(path, { showBack = false, preload = false } = {}) {
  currentPath.value = path
  showBackButton.value = showBack
  searchText.value = ''
  searchResults.value = []
  router.replace({ query: browseQuery({ path }) })

  const cached = scrollPositions.get(path)
  page.value = cached ? cached.page : 1
  hasMore.value = true
  loading.value = true
  try {
    await fetchPage(path, page.value, {
      restoreY: cached ? cached.y : 0,
      preload
    })
  } finally {
    loading.value = false
  }
}

function goBack() {
  searchText.value = ''
  searchResults.value = []
  if (currentPath.value === rootPath.value) {
    showBackButton.value = false
    sessionStorage.removeItem(storageKey.value)
    sessionStorage.removeItem(scrollStorageKey.value)
    router.push('/')
    return
  }
  const rootParts = rootPath.value.split('/').filter(Boolean)
  const curParts = currentPath.value.split('/').filter(Boolean)
  if (curParts.length > rootParts.length) {
    curParts.pop()
    const parentPath = '/' + curParts.join('/')
    restorePath(parentPath.startsWith(rootPath.value) ? parentPath : rootPath.value)
  } else {
    restorePath(rootPath.value)
  }
}

function navigateTo(path) {
  restorePath(path, { showBack: false, preload: true })
}

function refresh() {
  loadFiles()
}

function toggleViewMode() {
  if (mediaType.value === 'video') {
    viewMode.value = 'grid'
    return
  }
  viewMode.value = viewMode.value === 'grid' ? 'waterfall' : 'grid'
}

const SORT_MODES = ['name_asc', 'name_desc', 'time_asc', 'time_desc']
function cycleSortMode() {
  const idx = SORT_MODES.indexOf(sortMode.value)
  sortMode.value = SORT_MODES[(idx + 1) % SORT_MODES.length]
  localStorage.setItem('xbrowse_sort', sortMode.value)
  if (isRoot.value) {
    scrollPositions.delete(currentPath.value)
    saveScrollPositions()
    resetListState()
  }
}

const sortLabel = computed(() => {
  const map = { name_asc: '名称 A→Z', name_desc: '名称 Z→A', time_asc: '时间 ↑', time_desc: '时间 ↓' }
  return map[sortMode.value]
})

function handleImgError(e) {
  e.target.style.display = 'none'
}

function onThumbLoad(e, url) {
  if (url && !imageCache.has(url)) {
    const img = e.target
    const memory = estimateImageMemory(img)
    if (cacheMemoryUsage + memory > MAX_CACHE_MEMORY) {
      evictOldestCache(memory)
    }
    imageCache.set(url, { img: img.cloneNode(true), memory, timestamp: Date.now() })
    cacheMemoryUsage += memory
    evictOverBudgetCache()
  }
}

const imageCache = new Map()
const MAX_CACHE_MEMORY = 100 * 1024 * 1024
const CACHE_TTL = 30 * 60 * 1000
/** 列表预加载并发上限，避免弱网挤爆 */
const PRELOAD_CONCURRENCY = 4
const PRELOAD_BATCH = 6
let cacheMemoryUsage = 0
let cacheTimer = null
let preloadActive = 0
const preloadQueue = []

/** 列表展示用缩略图：优先 thumbnailUrl，回退 url */
function listThumbSrc(item) {
  if (!item) return ''
  return item.thumbnailUrl || item.url || ''
}

/** 目录预览：批量接口 > 列表自带 thumbnail/url */
function dirPreviewSrc(item) {
  if (!item) return ''
  return dirThumbnails.value[item.path] || item.thumbnailUrl || item.url || ''
}

function onDirThumbError(e, item) {
  // 失败后清掉错误地址，避免一直空白；回退文件夹图标
  if (item?.path && dirThumbnails.value[item.path]) {
    const next = { ...dirThumbnails.value }
    delete next[item.path]
    dirThumbnails.value = next
  }
  e.target.style.display = 'none'
}

function estimateImageMemory(img) {
  if (img.naturalWidth && img.naturalHeight) {
    return img.naturalWidth * img.naturalHeight * 4
  }
  return 200 * 1024
}

function evictExpiredCache() {
  const now = Date.now()
  for (const [url, entry] of imageCache) {
    if (now - entry.timestamp > CACHE_TTL) {
      cacheMemoryUsage -= entry.memory
      imageCache.delete(url)
    }
  }
}

function evictOldestCache(needed) {
  const entries = [...imageCache.entries()].sort((a, b) => a[1].timestamp - b[1].timestamp)
  for (const [url, entry] of entries) {
    if (cacheMemoryUsage - entry.memory + needed <= MAX_CACHE_MEMORY || imageCache.size <= 1) break
    cacheMemoryUsage -= entry.memory
    imageCache.delete(url)
  }
}

function evictOverBudgetCache() {
  if (cacheMemoryUsage <= MAX_CACHE_MEMORY) return
  const entries = [...imageCache.entries()].sort((a, b) => a[1].timestamp - b[1].timestamp)
  for (const [url, entry] of entries) {
    if (cacheMemoryUsage <= MAX_CACHE_MEMORY * 0.8) break
    cacheMemoryUsage -= entry.memory
    imageCache.delete(url)
  }
}

function getCachedImage(url) {
  if (!url) return null
  const entry = imageCache.get(url)
  if (entry) {
    entry.timestamp = Date.now()
    return entry.img
  }
  return null
}

function cacheImage(url) {
  if (!url || imageCache.has(url)) return
  evictExpiredCache()
  const img = new Image()
  img.decoding = 'async'
  img.onload = () => {
    const memory = estimateImageMemory(img)
    if (cacheMemoryUsage + memory > MAX_CACHE_MEMORY) {
      evictOldestCache(memory)
    }
    imageCache.set(url, { img, memory, timestamp: Date.now() })
    cacheMemoryUsage += memory
    evictOverBudgetCache()
    preloadActive = Math.max(0, preloadActive - 1)
    pumpPreloadQueue()
  }
  img.onerror = () => {
    preloadActive = Math.max(0, preloadActive - 1)
    pumpPreloadQueue()
  }
  img.src = url
}

function pumpPreloadQueue() {
  while (preloadActive < PRELOAD_CONCURRENCY && preloadQueue.length > 0) {
    const url = preloadQueue.shift()
    if (!url || imageCache.has(url)) continue
    preloadActive++
    cacheImage(url)
  }
}

function clearImageCache() {
  for (const [, entry] of imageCache) {
    entry.img.src = ''
  }
  imageCache.clear()
  cacheMemoryUsage = 0
  preloadQueue.length = 0
  preloadActive = 0
  if (cacheTimer) {
    clearInterval(cacheTimer)
    cacheTimer = null
  }
}

function preloadImages(urls) {
  if (!urls || urls.length === 0) return
  evictExpiredCache()
  const toLoad = urls
    .filter(url => url && !imageCache.has(url) && !preloadQueue.includes(url))
    .slice(0, PRELOAD_BATCH)
  preloadQueue.push(...toLoad)
  pumpPreloadQueue()
  evictOverBudgetCache()
}

function preloadPageImages() {
  // 只预加载列表缩略图，不预加载原图
  const urls = files.value
    .filter(f => !f.isDir && isImage(f.ext))
    .map(f => listThumbSrc(f))
    .filter(Boolean)
  preloadImages(urls)
}

function preloadNextPage() {
  if (!hasMore.value) return
  const nextPage = page.value + 1
  api.get('/api/files/list', {
    params: buildListParams(currentPath.value, nextPage)
  }).then(res => {
    if (res.code === 200) {
      const urls = res.data
        .filter(f => !f.isDir && isImage(f.ext))
        .map(f => listThumbSrc(f))
        .filter(Boolean)
      preloadImages(urls)
    }
  }).catch(() => {})
}

function startCacheTimer() {
  if (cacheTimer) clearInterval(cacheTimer)
  cacheTimer = setInterval(() => {
    evictExpiredCache()
    evictOverBudgetCache()
  }, 60 * 1000)
}

async function fetchDirThumbnails(dirPaths) {
  if (!dirPaths || dirPaths.length === 0) return
  const toFetch = dirPaths.filter(p => !(p in dirThumbnails.value))
  if (toFetch.length === 0) return
  pendingDirFetch.value = true
  try {
    const parts = [`engineId=${engineId.value}`]
    toFetch.forEach(p => parts.push(`paths=${encodeURIComponent(p)}`))
    const res = await api.get(`/api/files/dir-thumbnail?${parts.join('&')}`)
    if (res.code === 200) {
      const merged = { ...dirThumbnails.value }
      for (const [path, url] of Object.entries(res.data || {})) {
        // 仅写入有效 URL；null 不占位，便于同步完成后再次拉取
        if (url) merged[path] = url
      }
      dirThumbnails.value = merged
    }
  } catch (e) {
    console.error('加载目录预览图失败:', e)
  } finally {
    pendingDirFetch.value = false
  }
}

function setupDirObserver() {
  if (dirObserver) dirObserver.disconnect()
  dirObserver = new IntersectionObserver((entries) => {
    const paths = []
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const path = entry.target.dataset.dirPath
        if (path && !(path in dirThumbnails.value)) {
          paths.push(path)
        }
      }
    }
    if (paths.length > 0) {
      fetchDirThumbnails(paths)
    }
  }, { rootMargin: '200px' })

  nextTick(() => {
    document.querySelectorAll('[data-dir-path]').forEach(el => {
      dirObserver.observe(el)
    })
  })
}

function observeDirCards() {
  if (dirObserverTimer) clearTimeout(dirObserverTimer)
  dirObserverTimer = setTimeout(() => setupDirObserver(), 100)
}

function setupSentinelObserver() {
  if (scrollObserver) scrollObserver.disconnect()
  if (!sentinelRef.value) return
  scrollObserver = new IntersectionObserver((entries) => {
    const entry = entries[0]
    if (!entry.isIntersecting) {
      canLoadMoreOnIntersect = true
      return
    }
    if (canLoadMoreOnIntersect && hasMore.value && !loadingMore.value && !searchText.value) {
      canLoadMoreOnIntersect = false
      loadMore()
    }
  }, { rootMargin: '200px' })
  scrollObserver.observe(sentinelRef.value)
}

watch(searchText, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!val) {
    searchResults.value = []
    observeDirCards()
    return
  }
  searchTimer = setTimeout(() => doSearch(val), 300)
})

async function doSearch(keyword) {
  if (!keyword) return
  searching.value = true
  try {
    const res = await api.get('/api/files/search', {
      params: {
        engineId: engineId.value,
        keyword,
        parentPath: currentPath.value,
        mediaType: mediaType.value || 'all'
      }
    })
    if (res.code === 200) {
      searchResults.value = res.data
    }
  } catch (e) {
    console.error('搜索失败:', e)
  } finally {
    searching.value = false
  }
}

watch(
  () => route.query.path,
  (newPath) => {
    if (newPath && newPath !== currentPath.value) {
      clearImageCache()
      currentPath.value = newPath
      showBackButton.value = false
      loadFiles()
    }
  }
)

onMounted(() => {
  loadScrollPositions()
  sessionStorage.setItem(storageKey.value, rootPath.value)
  sessionStorage.setItem(mediaTypeKey.value, mediaType.value || 'all')
  if (mediaType.value === 'video' && viewMode.value === 'waterfall') {
    viewMode.value = 'grid'
  }
  if (currentPath.value !== rootPath.value) {
    showBackButton.value = true
  }
  loadFiles()
  startCacheTimer()
})

onUnmounted(() => {
  clearImageCache()
  if (dirObserver) dirObserver.disconnect()
  if (scrollObserver) scrollObserver.disconnect()
  if (dirObserverTimer) clearTimeout(dirObserverTimer)
})
</script>

<style scoped>
.page-container {
  width: 100vw;
  max-width: 100%;
  overflow-x: hidden;
}

.breadcrumb {
  position: sticky;
  top: 46px;
  z-index: 10;
  padding: 8px 16px;
  font-size: 13px;
  color: #969799;
  background: #f7f8fa;
  overflow-x: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
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

.search-bar {
  padding: 0 8px;
  background: #f7f8fa;
  overflow: hidden;
}

.search-bar :deep(.van-search) {
  padding: 8px 0;
}

.sort-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  margin: 0 8px 8px;
  font-size: 12px;
  color: #969799;
  background: #fff;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid #ebedf0;
}

.sort-indicator:active {
  background: #f2f3f5;
}

.load-sentinel {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px;
}

.no-more-text {
  color: #969799;
  font-size: 13px;
}

.grid-layout {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  padding: 12px;
  overflow: hidden;
}

@media (max-width: 768px) {
  .grid-layout {
    grid-template-columns: repeat(3, 1fr);
  }
}

.folder-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 4px;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  min-width: 0;
  overflow: hidden;
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
  min-width: 0;
}

.file-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-thumb-wrap {
  position: relative;
  width: 100%;
  height: 100%;
}

.video-thumb-wrap .file-thumbnail {
  display: block;
}

.video-play-badge {
  position: absolute;
  right: 4px;
  bottom: 4px;
  color: #fff;
  font-size: 18px;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.55));
  pointer-events: none;
}

.dir-preview {
  position: relative;
}

.dir-preview + .van-icon {
  display: none;
}

.file-info {
  text-align: center;
  width: 100%;
  min-width: 0;
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
  overflow: hidden;
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
  height: 48px;
  border-radius: 24px;
  background: #1989fa;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  z-index: 100;
  gap: 4px;
}

.fab-grid {
  right: 24px;
  width: 48px;
}

.fab-back {
  left: 50%;
  transform: translateX(-50%);
  width: 48px;
  padding: 0;
}

.fab-fade-enter-active,
.fab-fade-leave-active {
  transition: all 0.25s ease;
}

.fab-fade-enter-from,
.fab-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(12px);
}
</style>
