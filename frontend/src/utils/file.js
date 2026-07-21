const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
const VIDEO_EXTS = ['mp4', 'avi', 'mkv', 'mov', 'wmv', 'flv', 'webm']

export function isImage(ext) {
  return IMAGE_EXTS.includes(ext?.toLowerCase())
}

export function isVideo(ext) {
  return VIDEO_EXTS.includes(ext?.toLowerCase())
}

export function formatSize(bytes) {
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

export function getFileIcon(ext) {
  if (isImage(ext)) return 'photo-o'
  if (isVideo(ext)) return 'video-o'
  return 'description'
}

export function getFileColor(ext) {
  if (isImage(ext)) return '#07c160'
  if (isVideo(ext)) return '#ff976a'
  return '#969799'
}
