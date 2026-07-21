<template>
  <div class="viewer-page">
    <!-- 顶部工具栏 -->
    <transition name="fade">
      <div v-show="showToolbar" class="viewer-toolbar">
        <van-icon name="arrow-left" size="24" color="white" @click="goBack" />
        <span class="viewer-title">{{ currentFile?.name }}</span>
        <span class="viewer-counter">{{ currentIndex + 1 }} / {{ files.length }}</span>
        <div class="toolbar-right">
          <van-icon
            v-if="isImage"
            :name="viewMode === 'scroll' ? 'apps-o' : 'photo-o'"
            size="20"
            color="white"
            style="cursor: pointer;"
            @click="toggleViewMode"
          />
          <van-icon name="cross" size="22" color="white" style="cursor: pointer;" @click="goBack" />
        </div>
      </div>
    </transition>

    <div v-if="loading" class="viewer-loading">加载中...</div>

    <!-- 图片查看器 -->
    <template v-else-if="isImage">
      <!-- 连续滚动模式 -->
      <div
        v-if="viewMode === 'scroll'"
        ref="scrollContainer"
        class="image-scroll"
        @scroll="onScroll"
      >
        <div
          v-for="(file, index) in files"
          :key="file.path || file.name + index"
          class="image-item"
        >
          <img
            :src="file.url"
            :alt="file.name"
            class="scroll-image"
            @error="onImageError"
            loading="lazy"
          />
        </div>
      </div>

      <!-- 单张滑动模式 -->
      <div v-else class="image-swipe-view">
        <div class="swipe-image-container">
          <img
            :src="currentFile?.url"
            :alt="currentFile?.name"
            class="swipe-image"
            @error="onImageError"
          />
        </div>
        <div
          ref="swipeLayer"
          class="swipe-layer"
          @touchstart="onTouchStart"
          @touchend="onTouchEnd"
          @wheel="onWheel"
        />
      </div>
    </template>

    <!-- 视频播放器 -->
    <div v-else-if="isVideo" class="video-viewer">
      <div v-if="videoError" class="video-error">
        <p>{{ videoError }}</p>
        <van-button size="small" type="primary" plain @click="retryVideo">重试</van-button>
      </div>
      <video
        v-else
        ref="videoRef"
        :key="(currentFile?.path || currentFile?.url || '') + '-' + videoRetryKey"
        :src="currentFile?.url"
        :poster="currentFile?.thumbnailUrl || undefined"
        controls
        playsinline
        preload="metadata"
        class="video-player"
        @loadedmetadata="onVideoLoaded"
        @error="onVideoError"
        @play="showToolbar = false"
        @pause="showToolbar = true"
      >
        您的浏览器不支持视频播放
      </video>
      <div class="video-nav" v-show="showToolbar && videoFiles.length > 1">
        <van-button size="mini" plain type="primary" :disabled="!hasPrevVideo" @click="prevVideo">上一个</van-button>
        <span class="video-nav-label">{{ videoPosLabel }}</span>
        <van-button size="mini" plain type="primary" :disabled="!hasNextVideo" @click="nextVideo">下一个</van-button>
      </div>
      <div class="speed-controls" v-show="showToolbar">
        <span
          v-for="s in speeds"
          :key="s"
          class="speed-btn"
          :class="{ active: playbackSpeed === s }"
          @click="setSpeed(s)"
        >{{ s }}x</span>
      </div>
      <p v-if="!isBrowserPlayable && showToolbar" class="video-hint">
        当前格式可能无法在浏览器中直接播放
      </p>
    </div>

    <div v-else-if="!loading" class="viewer-empty">未找到可预览文件</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import api from '@/api'
import { isImage as isImageExt, isVideo as isVideoExt, isBrowserPlayableVideo } from '@/utils/file'

const router = useRouter()
const route = useRoute()

const engineId = computed(() => Number(route.params.engineId))
const currentPath = ref(route.query.path || '/')
const targetFile = ref(route.query.file || '')
const currentIndex = ref(Number(route.query.index) || 0)

const files = ref([])
const loading = ref(true)
const showToolbar = ref(true)
const videoRef = ref(null)
const scrollContainer = ref(null)
const swipeLayer = ref(null)
const playbackSpeed = ref(Number(localStorage.getItem('xbrowse_video_speed')) || 1)
const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2, 3]
const viewMode = ref('scroll')
const videoError = ref('')
const videoRetryKey = ref(0)

const currentFile = computed(() => files.value[currentIndex.value])

const isImage = computed(() => currentFile.value && isImageExt(currentFile.value.ext))
const isVideo = computed(() => currentFile.value && isVideoExt(currentFile.value.ext))
const isBrowserPlayable = computed(() => isBrowserPlayableVideo(currentFile.value?.ext))

const videoFiles = computed(() => files.value.filter((f) => isVideoExt(f.ext)))
const videoIndexInList = computed(() => {
  if (!currentFile.value) return -1
  return videoFiles.value.findIndex((f) => f.name === currentFile.value.name)
})
const hasPrevVideo = computed(() => videoIndexInList.value > 0)
const hasNextVideo = computed(() => videoIndexInList.value >= 0 && videoIndexInList.value < videoFiles.value.length - 1)
const videoPosLabel = computed(() => {
  if (videoIndexInList.value < 0) return ''
  return `${videoIndexInList.value + 1} / ${videoFiles.value.length}`
})

let scrollTimer = null

async function loadFiles() {
  loading.value = true
  videoError.value = ''
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

    // 优先按文件名定位（与 Browse 打开方式一致）
    if (targetFile.value) {
      const idx = allFiles.findIndex((f) => f.name === targetFile.value)
      currentIndex.value = idx >= 0 ? idx : 0
    } else {
      currentIndex.value = Math.min(Math.max(0, currentIndex.value), Math.max(0, allFiles.length - 1))
    }

    nextTick(() => {
      if (isImage.value && viewMode.value === 'scroll') {
        scrollToIndex(currentIndex.value)
      }
    })
  } catch (error) {
    console.error('加载文件列表失败:', error)
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

function toggleViewMode() {
  const prevIndex = currentIndex.value
  if (viewMode.value === 'scroll') {
    viewMode.value = 'swipe'
  } else {
    viewMode.value = 'scroll'
    nextTick(() => {
      scrollToIndex(prevIndex)
    })
  }
}

function scrollToIndex(index) {
  if (!scrollContainer.value) return
  const items = scrollContainer.value.children
  if (items[index]) {
    scrollContainer.value.scrollTo({ top: items[index].offsetTop, behavior: 'auto' })
  }
}

function onScroll() {
  if (!scrollContainer.value) return
  const container = scrollContainer.value
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
  currentIndex.value = closest
}

function setSpeed(speed) {
  playbackSpeed.value = speed
  localStorage.setItem('xbrowse_video_speed', String(speed))
  if (videoRef.value) {
    videoRef.value.playbackRate = speed
  }
}

function onVideoLoaded() {
  videoError.value = ''
  if (videoRef.value) {
    videoRef.value.playbackRate = playbackSpeed.value
  }
}

function onVideoError() {
  videoError.value = isBrowserPlayable.value
    ? '视频加载失败，请检查网络或稍后重试'
    : '当前格式可能无法在浏览器中播放（如 mkv/avi 等）'
}

function retryVideo() {
  videoError.value = ''
  videoRetryKey.value++
}

function pauseVideo() {
  if (videoRef.value) {
    try {
      videoRef.value.pause()
    } catch (_) {
      // ignore
    }
  }
}

function releaseVideo() {
  if (videoRef.value) {
    try {
      videoRef.value.pause()
      videoRef.value.removeAttribute('src')
      videoRef.value.load()
    } catch (_) {
      // ignore
    }
  }
}

function jumpToVideo(offset) {
  const list = videoFiles.value
  const idx = videoIndexInList.value
  if (idx < 0) return
  const next = list[idx + offset]
  if (!next) return
  pauseVideo()
  videoError.value = ''
  const mediaIdx = files.value.findIndex((f) => f.name === next.name)
  if (mediaIdx >= 0) {
    currentIndex.value = mediaIdx
    targetFile.value = next.name
  }
}

function prevVideo() {
  jumpToVideo(-1)
}

function nextVideo() {
  jumpToVideo(1)
}

function goBack() {
  releaseVideo()
  router.back()
}

function onImageError(e) {
  if (!e.target.src.includes('placeholder')) {
    e.target.src = '/placeholder.svg'
  }
}

// 单张模式：触摸滑动
let touchStartY = 0
function onTouchStart(e) {
  touchStartY = e.touches[0].clientY
}
function onTouchEnd(e) {
  const dy = touchStartY - e.changedTouches[0].clientY
  if (Math.abs(dy) > 50) {
    if (dy > 0 && currentIndex.value < files.value.length - 1) {
      currentIndex.value++
    } else if (dy < 0 && currentIndex.value > 0) {
      currentIndex.value--
    }
  }
}

// 单张模式：鼠标滚轮
let wheelTimer = null
function onWheel(e) {
  e.preventDefault()
  if (wheelTimer) return
  wheelTimer = setTimeout(() => { wheelTimer = null }, 300)
  if (e.deltaY > 0 && currentIndex.value < files.value.length - 1) {
    currentIndex.value++
  } else if (e.deltaY < 0 && currentIndex.value > 0) {
    currentIndex.value--
  }
}

function handleKeydown(e) {
  if (e.key === 'Escape') {
    goBack()
    return
  }
  if (isVideo.value) {
    switch (e.key) {
      case ' ':
      case 'Spacebar':
        e.preventDefault()
        if (videoRef.value) {
          if (videoRef.value.paused) videoRef.value.play()
          else videoRef.value.pause()
        }
        break
      case 'ArrowLeft':
        e.preventDefault()
        if (videoRef.value) videoRef.value.currentTime = Math.max(0, videoRef.value.currentTime - 5)
        break
      case 'ArrowRight':
        e.preventDefault()
        if (videoRef.value) videoRef.value.currentTime = Math.min(videoRef.value.duration || 0, videoRef.value.currentTime + 5)
        break
      case 'ArrowUp':
        e.preventDefault()
        prevVideo()
        break
      case 'ArrowDown':
        e.preventDefault()
        nextVideo()
        break
    }
    return
  }
  if (viewMode.value === 'swipe') {
    switch (e.key) {
      case 'ArrowUp':
        e.preventDefault()
        if (currentIndex.value > 0) currentIndex.value--
        break
      case 'ArrowDown':
        e.preventDefault()
        if (currentIndex.value < files.value.length - 1) currentIndex.value++
        break
    }
  }
}

// 切换媒体时暂停旧视频
watch(currentIndex, (idx, prev) => {
  if (prev !== idx) {
    pauseVideo()
    videoError.value = ''
    showToolbar.value = true
  }
})

onMounted(() => {
  loadFiles()
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  releaseVideo()
  if (scrollTimer) clearTimeout(scrollTimer)
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
  margin-right: 8px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-left: auto;
}

.viewer-loading,
.viewer-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.7);
  font-size: 15px;
}

.image-scroll {
  width: 100%;
  height: 100%;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.image-item {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
}

.scroll-image {
  width: 100%;
  display: block;
}

.image-swipe-view {
  width: 100%;
  height: 100vh;
  position: relative;
  overflow: hidden;
}

.swipe-image-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.swipe-image {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
}

.swipe-layer {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 5;
}

.video-viewer {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 12px 16px;
  box-sizing: border-box;
}

.video-player {
  width: 100%;
  max-width: 100%;
  max-height: calc(100% - 100px);
  background: #000;
}

.video-error {
  color: rgba(255, 255, 255, 0.85);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px;
}

.video-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}

.video-nav-label {
  color: rgba(255, 255, 255, 0.75);
  font-size: 13px;
  min-width: 48px;
  text-align: center;
}

.speed-controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  padding: 10px 0 4px;
}

.speed-btn {
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.speed-btn:hover {
  color: white;
}

.speed-btn.active {
  color: #1989fa;
  background: rgba(25, 137, 250, 0.2);
}

.video-hint {
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
  margin: 4px 0 0;
  text-align: center;
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
  .video-player {
    max-height: calc(100% - 120px);
  }
}
</style>
