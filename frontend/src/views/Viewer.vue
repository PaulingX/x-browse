<template>
  <div class="viewer-page">
    <!-- 顶部工具栏 -->
    <div class="viewer-toolbar">
      <van-icon name="arrow-left" size="24" color="white" @click="goBack" />
      <span class="viewer-title">{{ files[currentIndex]?.name }}</span>
      <span class="viewer-counter">{{ currentIndex + 1 }} / {{ files.length }}</span>
    </div>

    <!-- 图片查看器 -->
    <div v-if="isImage" class="image-viewer" @click="toggleToolbar">
      <van-swipe
        :initial-index="currentIndex"
        :loop="true"
        :show-indicators="false"
        @change="onSwipeChange"
        class="image-swipe"
      >
        <van-swipe-item v-for="(file, index) in imageFiles" :key="file.name">
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
    <div v-show="showToolbar" class="viewer-info">
      <div class="file-details">
        <p>文件名: {{ currentFile?.name }}</p>
        <p>大小: {{ formatSize(currentFile?.size) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
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

// 计算属性
const imageFiles = computed(() => {
  return files.value.filter((f) => !f.isDir && isImage(f.ext))
})

const currentFile = computed(() => {
  return files.value[currentIndex.value]
})

const isImage = computed(() => {
  return currentFile.value && isImageExt(currentFile.value.ext)
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
      // 过滤出媒体文件
      files.value = res.data.filter(
        (f) => !f.isDir && (isImageExt(f.ext) || isVideoExt(f.ext))
      )
    }
  } catch (error) {
    console.error('加载文件列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 切换工具栏显示
function toggleToolbar() {
  showToolbar.value = !showToolbar.value
}

// 返回上一页
function goBack() {
  router.back()
}

// 轮播切换
function onSwipeChange(index) {
  currentIndex.value = index
}

// 图片加载完成
function onImageLoad(e) {
  // 可以添加加载动画
}

// 图片加载失败
function onImageError(e) {
  e.target.src = '/placeholder.png'
}

// 判断是否为图片
function isImageExt(ext) {
  const exts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  return exts.includes(ext?.toLowerCase())
}

// 判断是否为视频
function isVideoExt(ext) {
  const exts = ['mp4', 'avi', 'mkv', 'mov', 'wmv', 'flv', 'webm']
  return exts.includes(ext?.toLowerCase())
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

// 键盘事件处理
function handleKeydown(e) {
  switch (e.key) {
    case 'ArrowLeft':
      if (currentIndex.value > 0) {
        currentIndex.value--
      }
      break
    case 'ArrowRight':
      if (currentIndex.value < files.value.length - 1) {
        currentIndex.value++
      }
      break
    case 'Escape':
      goBack()
      break
  }
}

onMounted(() => {
  loadFiles()
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
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
  margin-left: 12px;
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

/* 移动端适配 */
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
