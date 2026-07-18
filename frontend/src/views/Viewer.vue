<template>
  <div class="viewer-page">
    <!-- 顶部工具栏 -->
    <transition name="fade">
      <div v-show="showToolbar" class="viewer-toolbar">
        <van-icon name="arrow-left" size="24" color="white" @click="goBack" />
        <span class="viewer-title">{{ currentFile?.name }}</span>
        <span class="viewer-counter">{{ currentIndex + 1 }} / {{ files.length }}</span>
        <div class="toolbar-right">
          <van-icon name="cross" size="22" color="white" style="cursor: pointer;" @click="goBack" />
        </div>
      </div>
    </transition>

    <!-- 图片查看器 - 连续滚动 -->
    <div v-if="isImage" ref="scrollContainer" class="image-scroll" @scroll="onScroll">
      <div
        v-for="(file, index) in files"
        :key="file.name + index"
        :ref="el => { if (index === currentIndex) currentImageRef = el }"
        class="image-item"
      >
        <span class="image-label">{{ file.name.replace(/\.[^.]+$/, '') }}</span>
        <img
          :src="file.url"
          :alt="file.name"
          class="scroll-image"
          @error="onImageError"
          loading="lazy"
        />
      </div>
    </div>

    <!-- 视频播放器 -->
    <div v-else class="video-viewer">
      <video
        ref="videoRef"
        :src="currentFile?.url"
        controls
        playsinline
        class="video-player"
      >
        您的浏览器不支持视频播放
      </video>
      <div class="speed-controls" v-show="showToolbar">
        <span
          v-for="s in speeds"
          :key="s"
          class="speed-btn"
          :class="{ active: playbackSpeed === s }"
          @click="setSpeed(s)"
        >{{ s }}x</span>
      </div>
    </div>

    <!-- 底部信息栏 -->
    <transition name="fade">
      <div v-show="showToolbar" class="viewer-info">
        <div class="file-details">
          <p>{{ currentFile?.name }}</p>
          <p>{{ formatSize(currentFile?.size) }}</p>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '@/api'

const router = useRouter()
const route = useRoute()

const engineId = computed(() => Number(route.params.engineId))
const currentPath = ref(route.query.path || '/')
const currentIndex = ref(Number(route.query.index) || 0)

const files = ref([])
const loading = ref(true)
const showToolbar = ref(true)
const videoRef = ref(null)
const scrollContainer = ref(null)
const currentImageRef = ref(null)
const playbackSpeed = ref(1)
const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2, 3]

const currentFile = computed(() => files.value[currentIndex.value])

const isImage = computed(() => {
  return currentFile.value && isImageExt(currentFile.value.ext)
})

let scrollLock = false

async function loadFiles() {
  loading.value = true
  try {
    const allFiles = []
    let page = 1
    let hasMore = true
    while (hasMore) {
      const res = await api.get('/api/files/list', {
        params: {
          engineId: engineId.value,
          path: currentPath.value,
          page,
          perPage: 100
        }
      })
      if (res.code === 200) {
        const media = res.data.filter(
          (f) => !f.isDir && (isImageExt(f.ext) || isVideoExt(f.ext))
        )
        allFiles.push(...media)
        hasMore = res.data.length >= 100
        page++
      } else {
        hasMore = false
      }
    }
    files.value = allFiles
    nextTick(() => {
      scrollToIndex(currentIndex.value)
    })
  } catch (error) {
    console.error('加载文件列表失败:', error)
  } finally {
    loading.value = false
  }
}

function scrollToIndex(index) {
  if (!scrollContainer.value) return
  const items = scrollContainer.value.children
  if (items[index]) {
    items[index].scrollIntoView({ behavior: 'instant' })
  }
}

let scrollTimer = null
function onScroll() {
  if (!scrollContainer.value) return
  const container = scrollContainer.value
  const scrollTop = container.scrollTop
  const children = container.children
  let closest = 0
  let minDist = Infinity
  for (let i = 0; i < children.length; i++) {
    const rect = children[i].getBoundingClientRect()
    const containerRect = container.getBoundingClientRect()
    const dist = Math.abs(rect.top - containerRect.top)
    if (dist < minDist) {
      minDist = dist
      closest = i
    }
  }
  scrollLock = true
  currentIndex.value = closest
  clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => { scrollLock = false }, 100)
}

function toggleToolbar() {
  showToolbar.value = !showToolbar.value
}

function setSpeed(speed) {
  playbackSpeed.value = speed
  if (videoRef.value) {
    videoRef.value.playbackRate = speed
  }
}

function goBack() {
  router.back()
}

function onImageError(e) {
  if (!e.target.src.includes('placeholder')) {
    e.target.src = '/placeholder.svg'
  }
}

function isImageExt(ext) {
  const exts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  return exts.includes(ext?.toLowerCase())
}

function isVideoExt(ext) {
  const exts = ['mp4', 'avi', 'mkv', 'mov', 'wmv', 'flv', 'webm']
  return exts.includes(ext?.toLowerCase())
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

function handleKeydown(e) {
  if (!scrollContainer.value) return
  switch (e.key) {
    case 'ArrowUp':
      e.preventDefault()
      if (currentIndex.value > 0) scrollToIndex(currentIndex.value - 1)
      break
    case 'ArrowDown':
      e.preventDefault()
      if (currentIndex.value < files.value.length - 1) scrollToIndex(currentIndex.value + 1)
      break
    case 'Escape':
      goBack()
      break
  }
}

let wheelTimer = null
function handleWheel(e) {
  e.preventDefault()
}

onMounted(() => {
  loadFiles()
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('wheel', handleWheel, { passive: false })
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('wheel', handleWheel)
  if (wheelTimer) clearTimeout(wheelTimer)
  if (scrollTimer) clearTimeout(scrollTimer)
})
</script>

<style scoped>
.viewer-page {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #000;
  z-index: 2000;
}

.viewer-toolbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.7), transparent);
  z-index: 10;
}

.viewer-title {
  flex: 1;
  color: white;
  font-size: 16px;
  margin-left: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer-counter {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.image-scroll {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  scroll-snap-type: y mandatory;
}

.image-item {
  width: 100%;
  scroll-snap-align: start;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.image-label {
  position: absolute;
  top: 8px;
  left: 8px;
  color: #fff;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.45);
  padding: 2px 8px;
  border-radius: 4px;
  z-index: 1;
  pointer-events: none;
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scroll-image {
  width: 100%;
  display: block;
}

.video-viewer {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.video-player {
  max-width: 100%;
  max-height: calc(100% - 48px);
}

.speed-controls {
  display: flex;
  gap: 8px;
  padding: 8px 0;
}

.speed-btn {
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.speed-btn:hover {
  color: white;
}

.speed-btn.active {
  color: #1989fa;
  background: rgba(25, 137, 250, 0.2);
}

.viewer-info {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.7), transparent);
  z-index: 10;
}

.file-details p {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  margin-bottom: 4px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .viewer-toolbar {
    height: 44px;
    padding: 0 12px;
  }
  .viewer-title {
    font-size: 14px;
  }
}
</style>
