<template>
  <div class="viewer-page">
    <!-- 顶部工具栏 -->
    <transition name="fade">
      <div v-show="showToolbar" class="viewer-toolbar">
        <van-icon name="arrow-left" size="24" color="white" @click="goBack" />
        <span class="viewer-title">{{ currentFile?.name }}</span>
        <span class="viewer-counter">{{ currentIndex + 1 }} / {{ files.length }}</span>
        <div class="toolbar-right">
          <van-icon :name="swipeMode === 'vertical' ? 'arrow-left' : 'arrow-up'" size="20" color="white" style="cursor: pointer;" @click="toggleSwipeMode" />
          <van-icon name="cross" size="22" color="white" style="cursor: pointer;" @click="goBack" />
        </div>
      </div>
    </transition>

    <!-- 图片查看器 - 上下滑动 -->
    <div v-if="isImage" class="image-viewer" @click="toggleToolbar">
      <van-swipe
        ref="swipeRef"
        :initial-index="currentIndex"
        :loop="false"
        :show-indicators="false"
        :vertical="swipeMode === 'vertical'"
        @change="onSwipeChange"
        class="image-swipe"
      >
        <van-swipe-item v-for="(file, index) in files" :key="file.name + index">
          <div class="image-container">
            <img
              :src="file.url"
              :alt="file.name"
              class="viewer-image"
              @load="onImageLoad"
              @error="onImageError"
            />
          </div>
        </van-swipe-item>
      </van-swipe>
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
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
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
const swipeRef = ref(null)
const isSwiping = ref(false)
const swipeMode = ref('vertical')

const currentFile = computed(() => files.value[currentIndex.value])

const isImage = computed(() => {
  return currentFile.value && isImageExt(currentFile.value.ext)
})

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
      if (swipeRef.value && currentIndex.value > 0) {
        swipeRef.value.swipeTo(currentIndex.value)
      }
      preloadAdjacentImages()
    })
  } catch (error) {
    console.error('加载文件列表失败:', error)
  } finally {
    loading.value = false
  }
}

function toggleToolbar() {
  showToolbar.value = !showToolbar.value
}

function toggleSwipeMode() {
  swipeMode.value = swipeMode.value === 'vertical' ? 'horizontal' : 'vertical'
}

function goBack() {
  router.back()
}

function onSwipeChange(index) {
  isSwiping.value = true
  currentIndex.value = index
  preloadAdjacentImages()
  setTimeout(() => { isSwiping.value = false }, 50)
}

watch(currentIndex, (newIndex) => {
  if (!isSwiping.value && swipeRef.value) {
    swipeRef.value.swipeTo(newIndex)
  }
})

function onImageLoad() {
  preloadAdjacentImages()
}

function onImageError(e) {
  if (!e.target.src.includes('placeholder')) {
    e.target.src = '/placeholder.svg'
  }
}

function preloadAdjacentImages() {
  const range = 2
  for (let i = currentIndex.value - range; i <= currentIndex.value + range; i++) {
    if (i >= 0 && i < files.value.length && i !== currentIndex.value) {
      const file = files.value[i]
      if (file?.url) {
        const img = new Image()
        img.src = file.url
      }
    }
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
  switch (e.key) {
    case 'ArrowUp':
      e.preventDefault()
      if (currentIndex.value > 0) currentIndex.value--
      break
    case 'ArrowDown':
      e.preventDefault()
      if (currentIndex.value < files.value.length - 1) currentIndex.value++
      break
    case 'Escape':
      goBack()
      break
  }
}

let wheelTimer = null
function handleWheel(e) {
  e.preventDefault()
  if (wheelTimer) return
  wheelTimer = setTimeout(() => { wheelTimer = null }, 300)
  if (e.deltaY > 0) {
    if (currentIndex.value < files.value.length - 1) currentIndex.value++
  } else if (e.deltaY < 0) {
    if (currentIndex.value > 0) currentIndex.value--
  }
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

.image-viewer {
  width: 100%;
  height: 100%;
}

.image-swipe {
  height: 100%;
}

.image-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.viewer-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.video-viewer {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-player {
  max-width: 100%;
  max-height: 100%;
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
