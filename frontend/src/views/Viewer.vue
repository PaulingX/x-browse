<template>
  <div class="viewer-page" @keydown="handleKeydown" tabindex="0" ref="viewerPage">
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
      <div v-if="viewMode === 'scroll'" ref="scrollContainer" class="image-scroll" @scroll="onScroll">
        <div v-for="(file, index) in files" :key="file.path || file.name + index" class="image-item">
          <img :src="imageDisplaySrc(file, index)" :alt="file.name" class="scroll-image"
            @error="onImageError" @load="onFullImageLoad(file, index)" loading="lazy" decoding="async" />
        </div>
      </div>
      <div v-else class="image-swipe-view">
        <div class="swipe-image-container">
          <img :src="imageDisplaySrc(currentFile, currentIndex)" :alt="currentFile?.name"
            class="swipe-image" @error="onImageError" @load="onFullImageLoad(currentFile, currentIndex)" decoding="async" />
        </div>
        <div ref="swipeLayer" class="swipe-layer"
          @touchstart="onTouchStart" @touchend="onTouchEnd" @wheel="onWheel" />
      </div>
    </template>

    <!-- 视频播放器（自定义控件） -->
    <div
      v-else-if="isVideo"
      ref="videoViewerRef"
      class="video-viewer"
      :class="orientationClass"
      @mousemove="onVideoMouseMove"
      @mouseleave="hideToolbarTimer"
    >
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
        playsinline
        preload="metadata"
        class="video-player"
        @loadedmetadata="onVideoLoaded"
        @timeupdate="onTimeUpdate"
        @play="isPlaying = true"
        @pause="isPlaying = false"
        @ended="isPlaying = false"
        @volumechange="onVolumeChange"
        @error="onVideoError"
        @click="togglePlay"
        @dblclick="toggleFullscreen"
      />

      <!-- 自定义底部控件栏 -->
      <transition name="fade">
        <div v-show="showToolbar && !videoError" class="video-controls">
          <!-- 进度条 -->
          <div class="progress-bar" ref="progressBarRef" @mousedown="onProgressDown" @touchstart.prevent="onProgressTouchStart">
            <div class="progress-buffered" :style="{ width: bufferedPercent + '%' }" />
            <div class="progress-played" :style="{ width: playedPercent + '%' }">
              <div class="progress-thumb" />
            </div>
            <div v-if="seekPreview.visible" class="progress-tooltip" :style="{ left: seekPreview.left + 'px' }">
              {{ seekPreview.time }}
            </div>
          </div>

          <!-- 控制按钮行 -->
          <div class="controls-row">
            <div class="controls-left">
              <span class="ctrl-btn" @click="togglePlay" :title="isPlaying ? '暂停' : '播放'">
                <svg v-if="isPlaying" class="play-icon" viewBox="0 0 24 24" width="22" height="22" fill="white" aria-hidden="true">
                  <rect x="5" y="4" width="5" height="16" rx="1.2" />
                  <rect x="14" y="4" width="5" height="16" rx="1.2" />
                </svg>
                <svg v-else class="play-icon" viewBox="0 0 24 24" width="22" height="22" fill="white" aria-hidden="true">
                  <path d="M7 4.5v15l12.5-7.5L7 4.5z" />
                </svg>
              </span>
              <span class="volume-control">
                <span class="ctrl-btn" @click="toggleMute" :title="isMuted || volume === 0 ? '取消静音' : '静音'">
                  <svg v-if="isMuted || volume === 0" viewBox="0 0 24 24" width="18" height="18" fill="white" aria-hidden="true">
                    <path d="M4.5 9v6h3.5l4.5 3.5V5.5L8 9H4.5z" />
                    <path d="M16.5 9.5l4 4m0-4l-4 4" stroke="white" stroke-width="1.8" stroke-linecap="round" fill="none" />
                  </svg>
                  <svg v-else-if="volume < 0.5" viewBox="0 0 24 24" width="18" height="18" fill="white" aria-hidden="true">
                    <path d="M4.5 9v6h3.5l4.5 3.5V5.5L8 9H4.5z" />
                    <path d="M15.2 9.8a3.2 3.2 0 010 4.4" stroke="white" stroke-width="1.8" stroke-linecap="round" fill="none" />
                  </svg>
                  <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="white" aria-hidden="true">
                    <path d="M4.5 9v6h3.5l4.5 3.5V5.5L8 9H4.5z" />
                    <path d="M15.2 9.8a3.2 3.2 0 010 4.4M17.5 7.5a6 6 0 010 9" stroke="white" stroke-width="1.8" stroke-linecap="round" fill="none" />
                  </svg>
                </span>
                <input
                  class="volume-slider"
                  type="range"
                  min="0"
                  max="1"
                  step="0.05"
                  :value="isMuted ? 0 : volume"
                  @input="onVolumeInput"
                  title="音量"
                />
              </span>
              <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
            </div>
            <div class="controls-right">
              <span v-for="s in speeds" :key="s" class="speed-chip" :class="{ active: playbackSpeed === s }" @click="setSpeed(s)">
                {{ s }}x
              </span>
              <span class="ctrl-btn" :title="orientationTitle" @click="toggleOrientation">
                <svg v-if="orientationMode === 'landscape'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="white" stroke-width="1.8" aria-hidden="true">
                  <rect x="2" y="6" width="20" height="12" rx="2" />
                </svg>
                <svg v-else-if="orientationMode === 'portrait'" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="white" stroke-width="1.8" aria-hidden="true">
                  <rect x="6" y="2" width="12" height="20" rx="2" />
                </svg>
                <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="white" stroke-width="1.8" aria-hidden="true">
                  <rect x="3" y="5" width="10" height="14" rx="1.5" transform="rotate(-20 8 12)" />
                  <rect x="11" y="7" width="10" height="10" rx="1.5" />
                </svg>
              </span>
              <span class="ctrl-btn" :title="isFullscreen ? '退出全屏' : '进入全屏'" @click="toggleFullscreen">
                <svg v-if="isFullscreen" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="white" stroke-width="1.8" aria-hidden="true">
                  <path d="M9 3v6H3M15 3v6h6M9 21v-6H3M15 21v-6h6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="white" stroke-width="1.8" aria-hidden="true">
                  <path d="M3 9V3h6M15 3h6v6M21 15v6h-6M9 21H3v-6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
            </div>
          </div>
        </div>
      </transition>

      <p v-if="!isBrowserPlayable && showToolbar" class="video-hint">当前格式可能无法在浏览器中直接播放</p>
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
import { getCachedSrc, cacheImage, rememberLoaded, preloadImages } from '@/utils/imageCache'

const router = useRouter()
const route = useRoute()
const viewerPage = ref(null)

const engineId = computed(() => Number(route.params.engineId))
const currentPath = ref(route.query.path || '/')
const targetFile = ref(route.query.file || '')
const mediaType = ref(route.query.mediaType || 'all')
const currentIndex = ref(Number(route.query.index) || 0)

const files = ref([])
const loading = ref(true)
const showToolbar = ref(true)
const videoRef = ref(null)
const videoViewerRef = ref(null)
const scrollContainer = ref(null)
const swipeLayer = ref(null)
const progressBarRef = ref(null)
const playbackSpeed = ref(Number(localStorage.getItem('xbrowse_video_speed')) || 1)
const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2, 3]
const viewMode = ref('scroll')
const videoError = ref('')
const videoRetryKey = ref(0)
const fullImageReady = ref(new Set())

// 视频状态
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const bufferedPercent = ref(0)
const isFullscreen = ref(false)
// auto | landscape | portrait
const orientationMode = ref(localStorage.getItem('xbrowse_video_orientation') || 'auto')
const seekPreview = ref({ visible: false, left: 0, time: '' })
const volume = ref(Number(localStorage.getItem('xbrowse_video_volume') ?? 1))
const isMuted = ref(localStorage.getItem('xbrowse_video_muted') === '1')
let volumeBeforeMute = volume.value > 0 ? volume.value : 1

const orientationClass = computed(() => {
  if (orientationMode.value === 'landscape') return 'orient-landscape'
  if (orientationMode.value === 'portrait') return 'orient-portrait'
  return 'orient-auto'
})
const orientationTitle = computed(() => {
  if (orientationMode.value === 'landscape') return '横屏（点击切换竖屏）'
  if (orientationMode.value === 'portrait') return '竖屏（点击切换自动）'
  return '自动（点击切换横屏）'
})

const currentFile = computed(() => files.value[currentIndex.value])
const isImage = computed(() => currentFile.value && isImageExt(currentFile.value.ext))
const isVideo = computed(() => currentFile.value && isVideoExt(currentFile.value.ext))
const isBrowserPlayable = computed(() => isBrowserPlayableVideo(currentFile.value?.ext))

const playedPercent = computed(() => duration.value > 0 ? (currentTime.value / duration.value) * 100 : 0)

const videoFiles = computed(() => files.value.filter((f) => isVideoExt(f.ext)))
const videoIndexInList = computed(() => {
  if (!currentFile.value) return -1
  return videoFiles.value.findIndex((f) => f.name === currentFile.value.name)
})
let scrollTimer = null
let toolbarTimer = null
let progressDragging = false

function formatTime(sec) {
  if (!sec || !isFinite(sec)) return '00:00'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = Math.floor(sec % 60)
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function onVideoLoaded() {
  videoError.value = ''
  if (videoRef.value) {
    videoRef.value.playbackRate = playbackSpeed.value
    duration.value = videoRef.value.duration || 0
    applyVolume()
    isPlaying.value = !videoRef.value.paused
  }
}

function applyVolume() {
  if (!videoRef.value) return
  videoRef.value.volume = volume.value
  videoRef.value.muted = isMuted.value || volume.value === 0
}

function onVolumeChange() {
  if (!videoRef.value) return
  volume.value = videoRef.value.volume
  isMuted.value = videoRef.value.muted || videoRef.value.volume === 0
}

function onVolumeInput(e) {
  const v = Number(e.target.value)
  volume.value = v
  isMuted.value = v === 0
  if (v > 0) volumeBeforeMute = v
  localStorage.setItem('xbrowse_video_volume', String(v))
  localStorage.setItem('xbrowse_video_muted', isMuted.value ? '1' : '0')
  applyVolume()
}

function toggleMute() {
  if (!videoRef.value) return
  if (isMuted.value || volume.value === 0) {
    isMuted.value = false
    volume.value = volumeBeforeMute > 0 ? volumeBeforeMute : 1
  } else {
    volumeBeforeMute = volume.value > 0 ? volume.value : 1
    isMuted.value = true
  }
  localStorage.setItem('xbrowse_video_volume', String(volume.value))
  localStorage.setItem('xbrowse_video_muted', isMuted.value ? '1' : '0')
  applyVolume()
}

function onTimeUpdate() {
  if (!videoRef.value || progressDragging) return
  currentTime.value = videoRef.value.currentTime || 0
  // 缓冲进度
  if (videoRef.value.buffered.length > 0) {
    bufferedPercent.value = (videoRef.value.buffered.end(videoRef.value.buffered.length - 1) / (videoRef.value.duration || 1)) * 100
  }
}

function togglePlay() {
  if (!videoRef.value) return
  if (videoRef.value.paused) {
    videoRef.value.play()
  } else {
    videoRef.value.pause()
  }
}

function setSpeed(speed) {
  playbackSpeed.value = speed
  localStorage.setItem('xbrowse_video_speed', String(speed))
  if (videoRef.value) videoRef.value.playbackRate = speed
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
    try { videoRef.value.pause() } catch (_) { /* ignore */ }
  }
}

function releaseVideo() {
  if (videoRef.value) {
    try {
      videoRef.value.pause()
      videoRef.value.removeAttribute('src')
      videoRef.value.load()
    } catch (_) { /* ignore */ }
  }
}

// 进度条交互
function seekTo(e) {
  if (!videoRef.value || !progressBarRef.value) return
  const rect = progressBarRef.value.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  videoRef.value.currentTime = ratio * (videoRef.value.duration || 0)
  currentTime.value = videoRef.value.currentTime
}

function onProgressDown(e) {
  progressDragging = true
  seekTo(e)
  const onMove = (ev) => seekTo(ev)
  const onUp = () => {
    progressDragging = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function onProgressTouchStart(e) {
  progressDragging = true
  seekToTouch(e)
  const onMove = (ev) => seekToTouch(ev)
  const onEnd = () => {
    progressDragging = false
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onEnd)
  }
  document.addEventListener('touchmove', onMove)
  document.addEventListener('touchend', onEnd)
}

function seekToTouch(e) {
  if (!videoRef.value || !progressBarRef.value) return
  const touch = e.touches[0]
  if (!touch) return
  const rect = progressBarRef.value.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (touch.clientX - rect.left) / rect.width))
  videoRef.value.currentTime = ratio * (videoRef.value.duration || 0)
  currentTime.value = videoRef.value.currentTime
}

// 全屏（整块播放器容器，保留自定义控件）
function toggleFullscreen() {
  const el = videoViewerRef.value || videoRef.value
  if (!el) return
  if (document.fullscreenElement) {
    document.exitFullscreen?.()
  } else {
    const req = el.requestFullscreen || el.webkitRequestFullscreen || el.msRequestFullscreen
    req?.call(el)?.catch?.(() => {
      // 部分浏览器不允许非用户手势触发，静默失败
    })
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

// 横竖屏：auto → landscape → portrait → auto
function toggleOrientation() {
  const order = ['auto', 'landscape', 'portrait']
  const idx = order.indexOf(orientationMode.value)
  orientationMode.value = order[(idx + 1) % order.length]
  localStorage.setItem('xbrowse_video_orientation', orientationMode.value)
  applyScreenOrientation()
  showToolbarTemporarily()
}

async function applyScreenOrientation() {
  const so = screen.orientation
  if (!so || typeof so.lock !== 'function') return
  try {
    if (orientationMode.value === 'landscape') {
      await so.lock('landscape')
    } else if (orientationMode.value === 'portrait') {
      await so.lock('portrait')
    } else {
      so.unlock?.()
    }
  } catch (_) {
    // 多数桌面浏览器不支持 lock，仅用 CSS 布局兜底
  }
}

// 工具栏自动隐藏
function showToolbarTemporarily() {
  showToolbar.value = true
  clearTimeout(toolbarTimer)
  toolbarTimer = setTimeout(() => {
    if (isPlaying.value && !progressDragging) {
      showToolbar.value = false
    }
  }, 3000)
}

function hideToolbarTimer() {
  clearTimeout(toolbarTimer)
  toolbarTimer = setTimeout(() => {
    if (isPlaying.value && !progressDragging) {
      showToolbar.value = false
    }
  }, 1500)
}

function onVideoMouseMove() {
  showToolbarTemporarily()
}

// 快捷键
function handleKeydown(e) {
  if (e.key === 'Escape') {
    if (document.fullscreenElement) {
      document.exitFullscreen()
    } else {
      goBack()
    }
    return
  }
  if (isVideo.value) {
    switch (e.key) {
      case ' ':
      case 'k':
        e.preventDefault()
        togglePlay()
        break
      case 'ArrowLeft':
      case 'j':
        e.preventDefault()
        if (videoRef.value) videoRef.value.currentTime = Math.max(0, videoRef.value.currentTime - 5)
        break
      case 'ArrowRight':
      case 'l':
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
      case 'f':
        e.preventDefault()
        toggleFullscreen()
        break
      case 'r':
        e.preventDefault()
        toggleOrientation()
        break
      case ',':
      case '<':
        e.preventDefault()
        setSpeed(Math.max(0.5, playbackSpeed.value - 0.25))
        break
      case '.':
      case '>':
        e.preventDefault()
        setSpeed(Math.min(3, playbackSpeed.value + 0.25))
        break
      case '0':
      case 'Home':
        e.preventDefault()
        if (videoRef.value) videoRef.value.currentTime = 0
        break
      case 'End':
        e.preventDefault()
        if (videoRef.value) videoRef.value.currentTime = videoRef.value.duration || 0
        break
      case 'm':
        e.preventDefault()
        toggleMute()
        break
    }
    return
  }
  if (viewMode.value === 'swipe') {
    if (e.key === 'ArrowUp') { e.preventDefault(); if (currentIndex.value > 0) currentIndex.value-- }
    else if (e.key === 'ArrowDown') { e.preventDefault(); if (currentIndex.value < files.value.length - 1) currentIndex.value++ }
  }
}

// 切换媒体时暂停旧视频
watch(currentIndex, (idx, prev) => {
  if (prev !== idx) {
    pauseVideo()
    videoError.value = ''
    showToolbar.value = true
    isPlaying.value = false
    currentTime.value = 0
    duration.value = 0
    bufferedPercent.value = 0
  }
  const file = files.value[idx]
  if (file && isImageExt(file.ext)) {
    onFullImageLoad(file, idx)
  }
})

watch(isPlaying, (playing) => {
  if (!playing) showToolbar.value = true
})

// 加载文件列表
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
          perPage: 100,
          mediaType: mediaType.value || 'all'
        }
      })
      if (res.code === 200) {
        const media = res.data.filter((f) => {
          if (f.isDir) return false
          if (mediaType.value === 'image') return isImageExt(f.ext)
          if (mediaType.value === 'video') return isVideoExt(f.ext)
          return isImageExt(f.ext) || isVideoExt(f.ext)
        })
        allFiles.push(...media)
        hasMore = res.data.length >= 100
        page++
      } else {
        hasMore = false
      }
    }
    files.value = allFiles
    if (targetFile.value) {
      const idx = allFiles.findIndex((f) => f.name === targetFile.value)
      currentIndex.value = idx >= 0 ? idx : 0
    } else {
      currentIndex.value = Math.min(Math.max(0, currentIndex.value), Math.max(0, allFiles.length - 1))
    }
    // 后台预缓存附近图片原图/缩略图
    const nearby = allFiles
      .filter((f) => isImageExt(f.ext))
      .slice(Math.max(0, currentIndex.value - 2), currentIndex.value + 8)
      .flatMap((f) => [f.thumbnailUrl, f.url].filter(Boolean))
    preloadImages(nearby, { limit: 12 })
    nextTick(() => {
      if (isImage.value && viewMode.value === 'scroll') scrollToIndex(currentIndex.value)
    })
  } catch (error) {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

function toggleViewMode() {
  const prevIndex = currentIndex.value
  viewMode.value = viewMode.value === 'scroll' ? 'swipe' : 'scroll'
  if (viewMode.value === 'scroll') nextTick(() => scrollToIndex(prevIndex))
}

function scrollToIndex(index) {
  if (!scrollContainer.value) return
  const items = scrollContainer.value.children
  if (items[index]) scrollContainer.value.scrollTo({ top: items[index].offsetTop, behavior: 'auto' })
}

function onScroll() {
  if (!scrollContainer.value) return
  let closest = 0, minDist = Infinity
  for (let i = 0; i < scrollContainer.value.children.length; i++) {
    const dist = Math.abs(scrollContainer.value.children[i].getBoundingClientRect().top)
    if (dist < minDist) { minDist = dist; closest = i }
  }
  currentIndex.value = closest
}

function jumpToVideo(offset) {
  const list = videoFiles.value
  const idx = videoIndexInList.value
  const next = list[idx + offset]
  if (!next) return
  pauseVideo()
  videoError.value = ''
  const mediaIdx = files.value.findIndex((f) => f.name === next.name)
  if (mediaIdx >= 0) { currentIndex.value = mediaIdx; targetFile.value = next.name }
}

function prevVideo() { jumpToVideo(-1) }
function nextVideo() { jumpToVideo(1) }

function goBack() { releaseVideo(); router.back() }

function imageDisplaySrc(file, index) {
  if (!file) return ''
  const key = file.path || file.name || String(index)
  const url = fullImageReady.value.has(key) && file.url ? file.url : (file.thumbnailUrl || file.url || '')
  return getCachedSrc(url) || url
}

function onFullImageLoad(file, index) {
  if (!file) return
  const key = file.path || file.name || String(index)
  if (file.thumbnailUrl) rememberLoaded(file.thumbnailUrl)
  if (fullImageReady.value.has(key)) {
    if (file.url) rememberLoaded(file.url)
    return
  }
  if (file.url && file.thumbnailUrl && file.url !== file.thumbnailUrl) {
    cacheImage(file.url).then(() => {
      const s = new Set(fullImageReady.value)
      s.add(key)
      fullImageReady.value = s
    })
  } else if (file.url) {
    rememberLoaded(file.url)
    const s = new Set(fullImageReady.value)
    s.add(key)
    fullImageReady.value = s
  }
}

function onImageError(e) { if (!e.target.src.includes('placeholder')) e.target.src = '/placeholder.svg' }

let touchStartY = 0
function onTouchStart(e) { touchStartY = e.touches[0].clientY }
function onTouchEnd(e) {
  const dy = touchStartY - e.changedTouches[0].clientY
  if (Math.abs(dy) > 50) {
    if (dy > 0 && currentIndex.value < files.value.length - 1) currentIndex.value++
    else if (dy < 0 && currentIndex.value > 0) currentIndex.value--
  }
}

let wheelTimer = null
function onWheel(e) {
  e.preventDefault()
  if (wheelTimer) return
  wheelTimer = setTimeout(() => { wheelTimer = null }, 300)
  if (e.deltaY > 0 && currentIndex.value < files.value.length - 1) currentIndex.value++
  else if (e.deltaY < 0 && currentIndex.value > 0) currentIndex.value--
}

onMounted(() => {
  loadFiles()
  viewerPage.value?.focus()
  document.addEventListener('fullscreenchange', onFullscreenChange)
  applyScreenOrientation()
})

onUnmounted(() => {
  releaseVideo()
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  try { screen.orientation?.unlock?.() } catch (_) { /* ignore */ }
  if (scrollTimer) clearTimeout(scrollTimer)
  if (wheelTimer) clearTimeout(wheelTimer)
  if (toolbarTimer) clearTimeout(toolbarTimer)
})
</script>

<style scoped>
.viewer-page {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: #000;
  z-index: 2000;
  outline: none;
}

.viewer-toolbar {
  position: absolute; top: 0; left: 0; right: 0; height: 48px;
  display: flex; align-items: center; padding: 0 16px;
  background: linear-gradient(to bottom, rgba(0,0,0,0.8), transparent);
  z-index: 10;
}
.viewer-title { flex: 1; color: white; font-size: 16px; margin-left: 16px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.viewer-counter { color: rgba(255,255,255,0.8); font-size: 14px; margin-right: 8px; }
.toolbar-right { display: flex; align-items: center; gap: 16px; margin-left: auto; }

.viewer-loading, .viewer-empty {
  height: 100%; display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.7); font-size: 15px;
}

/* 图片 */
.image-scroll { width: 100%; height: 100%; overflow-y: auto; -webkit-overflow-scrolling: touch; }
.image-item { width: 100%; display: flex; }
.scroll-image { width: 100%; display: block; }
.image-swipe-view { width: 100%; height: 100vh; position: relative; overflow: hidden; }
.swipe-image-container { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.swipe-image { max-width: 100%; max-height: 100%; object-fit: contain; }
.swipe-layer { position: absolute; top: 0; left: 0; right: 0; bottom: 0; z-index: 5; }

/* 视频播放器 */
.video-viewer {
  width: 100%; height: 100%;
  display: flex; flex-direction: column; align-items: center; justify-content: flex-end;
  position: relative; background: #000; overflow: hidden;
}
.video-viewer:fullscreen,
.video-viewer:-webkit-full-screen {
  width: 100vw; height: 100vh; background: #000;
}
.video-player {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  object-fit: contain; background: #000; cursor: pointer; transition: transform 0.25s ease;
}
/* 横竖屏：不支持 Screen Orientation 时用 CSS 旋转兜底 */
.video-viewer.orient-auto .video-player,
.video-viewer.orient-landscape .video-player,
.video-viewer.orient-portrait .video-player {
  object-fit: contain;
}
/* 当前视口偏竖时强制横屏观看：整块播放区旋转 90° */
@media (orientation: portrait) {
  .video-viewer.orient-landscape {
    position: fixed;
    top: 50%; left: 50%;
    width: 100vh; height: 100vw;
    transform: translate(-50%, -50%) rotate(90deg);
    transform-origin: center center;
  }
  .video-viewer.orient-landscape:fullscreen,
  .video-viewer.orient-landscape:-webkit-full-screen {
    width: 100vh; height: 100vw;
  }
}
/* 当前视口偏横时强制竖屏观看：整块播放区旋转 -90° */
@media (orientation: landscape) {
  .video-viewer.orient-portrait {
    position: fixed;
    top: 50%; left: 50%;
    width: 100vh; height: 100vw;
    transform: translate(-50%, -50%) rotate(-90deg);
    transform-origin: center center;
  }
  .video-viewer.orient-portrait:fullscreen,
  .video-viewer.orient-portrait:-webkit-full-screen {
    width: 100vh; height: 100vw;
  }
}
.video-error {
  color: rgba(255,255,255,0.85); text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 24px;
  position: relative; z-index: 5;
}

/* 底部控件栏 */
.video-controls {
  position: relative; z-index: 10; width: 100%; padding: 0 12px 12px;
  background: linear-gradient(transparent, rgba(0,0,0,0.85));
}
.controls-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 0 2px;
}
.controls-left, .controls-right {
  display: flex; align-items: center; gap: 8px;
}
.controls-right { margin-left: auto; }
.time-display { color: rgba(255,255,255,0.9); font-size: 12px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.ctrl-btn {
  display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; cursor: pointer; border-radius: 4px;
  transition: background 0.2s; flex-shrink: 0;
}
.ctrl-btn:hover { background: rgba(255,255,255,0.15); }
.ctrl-btn.disabled { opacity: 0.3; pointer-events: none; }
.play-icon { display: block; }
.volume-control {
  display: flex; align-items: center; gap: 2px;
}
.volume-slider {
  width: 0; opacity: 0; pointer-events: none;
  height: 4px; margin: 0; cursor: pointer;
  accent-color: #1989fa; transition: width 0.2s, opacity 0.2s;
}
.volume-control:hover .volume-slider,
.volume-slider:focus {
  width: 72px; opacity: 1; pointer-events: auto;
}

.speed-chip {
  padding: 2px 8px; border-radius: 10px; font-size: 11px;
  color: rgba(255,255,255,0.6); cursor: pointer; transition: all 0.2s; user-select: none;
}
.speed-chip:hover { color: white; }
.speed-chip.active { color: #1989fa; background: rgba(25,137,250,0.25); }

/* 进度条 */
.progress-bar {
  position: relative; width: 100%; height: 20px; cursor: pointer;
  display: flex; align-items: center; touch-action: none;
}
.progress-bar::before {
  content: ''; position: absolute; left: 0; right: 0;
  height: 3px; background: rgba(255,255,255,0.25); border-radius: 2px;
  top: 50%; transform: translateY(-50%);
}
.progress-buffered {
  position: absolute; left: 0; height: 3px; background: rgba(255,255,255,0.35);
  border-radius: 2px; top: 50%; transform: translateY(-50%);
}
.progress-played {
  position: absolute; left: 0; height: 3px; background: #1989fa;
  border-radius: 2px; top: 50%; transform: translateY(-50%);
}
.progress-thumb {
  position: absolute; right: -7px; top: 50%; transform: translateY(-50%);
  width: 14px; height: 14px; border-radius: 50%; background: #1989fa;
  box-shadow: 0 0 4px rgba(0,0,0,0.4); opacity: 0; transition: opacity 0.2s;
}
.progress-bar:hover .progress-thumb { opacity: 1; }
.progress-bar:hover::before,
.progress-bar:hover .progress-buffered,
.progress-bar:hover .progress-played { height: 5px; }

.progress-tooltip {
  position: absolute; bottom: 24px; transform: translateX(-50%);
  background: rgba(0,0,0,0.85); color: white; font-size: 12px;
  padding: 3px 8px; border-radius: 4px; pointer-events: none;
  white-space: nowrap; font-variant-numeric: tabular-nums;
}

.video-hint { color: rgba(255,255,255,0.45); font-size: 12px; text-align: center; padding: 4px 0 0; position: relative; z-index: 10; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 768px) {
  .viewer-toolbar { height: 44px; padding: 0 12px; }
  .viewer-title { font-size: 14px; }
  .speed-chip { padding: 2px 6px; font-size: 10px; }
  .volume-slider { width: 56px; opacity: 1; pointer-events: auto; }
}
</style>
