/**
 * 前端图片内存缓存（blob URL）
 * - 后台预加载
 * - 最大内存上限，超出按 FIFO 清理
 * - 定时按 TTL 清理
 * - 可在返回上级目录时整页清空
 */

const MAX_CACHE_MEMORY = 80 * 1024 * 1024 // 80MB
const CACHE_TTL = 15 * 60 * 1000 // 15 分钟
const CLEAN_INTERVAL = 60 * 1000
const PRELOAD_CONCURRENCY = 4
const PRELOAD_BATCH = 12
const DEFAULT_ENTRY_SIZE = 80 * 1024

/** @type {Map<string, { blobUrl: string, size: number, ts: number }>} */
const cache = new Map()
/** @type {Set<string>} */
const inflight = new Set()
/** @type {string[]} */
const queue = []

let memoryUsage = 0
let activeLoads = 0
let timer = null

function now() {
  return Date.now()
}

function estimateBlobSize(blob) {
  return blob?.size > 0 ? blob.size : DEFAULT_ENTRY_SIZE
}

function revokeEntry(entry) {
  if (entry?.blobUrl) {
    try {
      URL.revokeObjectURL(entry.blobUrl)
    } catch (_) {
      /* ignore */
    }
  }
}

/** FIFO：按插入顺序删最旧 */
function evictFifo(needed = 0) {
  while (cache.size > 0 && memoryUsage + needed > MAX_CACHE_MEMORY) {
    const firstKey = cache.keys().next().value
    if (firstKey == null) break
    const entry = cache.get(firstKey)
    cache.delete(firstKey)
    if (entry) {
      memoryUsage = Math.max(0, memoryUsage - entry.size)
      revokeEntry(entry)
    }
  }
}

function evictExpired() {
  const t = now()
  for (const [url, entry] of cache) {
    if (t - entry.ts > CACHE_TTL) {
      memoryUsage = Math.max(0, memoryUsage - entry.size)
      cache.delete(url)
      revokeEntry(entry)
    }
  }
}

function put(url, blobUrl, size) {
  if (!url || !blobUrl) return
  const existing = cache.get(url)
  if (existing) {
    memoryUsage = Math.max(0, memoryUsage - existing.size)
    cache.delete(url)
    revokeEntry(existing)
  }
  evictFifo(size)
  // 重新 set 保证在 Map 尾部（较新）
  cache.set(url, { blobUrl, size, ts: now() })
  memoryUsage += size
  // 再保险一次
  if (memoryUsage > MAX_CACHE_MEMORY) {
    evictFifo(0)
  }
}

/**
 * 取缓存展示地址：有则刷新 TTL，无则返回原 URL
 */
export function getCachedSrc(url) {
  if (!url) return ''
  const entry = cache.get(url)
  if (!entry) return url
  // 刷新时间戳并挪到尾部（近似 LRU，仍受 FIFO 上限约束）
  cache.delete(url)
  entry.ts = now()
  cache.set(url, entry)
  return entry.blobUrl || url
}

export function hasCached(url) {
  return !!url && cache.has(url)
}

/**
 * 后台缓存单张图片（fetch -> blob URL）
 */
export function cacheImage(url) {
  if (!url || cache.has(url) || inflight.has(url)) return Promise.resolve(getCachedSrc(url))
  inflight.add(url)
  return fetch(url, { credentials: 'same-origin', cache: 'force-cache' })
    .then((res) => {
      if (!res.ok) throw new Error(`cache fetch ${res.status}`)
      return res.blob()
    })
    .then((blob) => {
      const size = estimateBlobSize(blob)
      const blobUrl = URL.createObjectURL(blob)
      put(url, blobUrl, size)
      return blobUrl
    })
    .catch(() => url)
    .finally(() => {
      inflight.delete(url)
      activeLoads = Math.max(0, activeLoads - 1)
      pumpQueue()
    })
}

function pumpQueue() {
  while (activeLoads < PRELOAD_CONCURRENCY && queue.length > 0) {
    const url = queue.shift()
    if (!url || cache.has(url) || inflight.has(url)) continue
    activeLoads++
    cacheImage(url)
  }
}

/**
 * 后台批量预加载（限并发、限批次）
 */
export function preloadImages(urls, { limit = PRELOAD_BATCH } = {}) {
  if (!urls?.length) return
  evictExpired()
  const toAdd = []
  for (const url of urls) {
    if (!url || cache.has(url) || inflight.has(url) || queue.includes(url)) continue
    toAdd.push(url)
    if (toAdd.length >= limit) break
  }
  if (toAdd.length === 0) return
  queue.push(...toAdd)
  pumpQueue()
}

/**
 * 图片元素 load 后登记（无 blob 时用 Image 尺寸估算，仅记“已热”标记用原 url）
 * 更稳妥：继续走 cacheImage 拉 blob。
 */
export function rememberLoaded(url, imgEl) {
  if (!url || cache.has(url)) return
  // 已在页面解码成功：后台再拉一份 blob 入池，失败则跳过
  cacheImage(url)
  void imgEl
}

/** 清空全部缓存（返回上级/离开页面） */
export function clearImageCache() {
  for (const [, entry] of cache) {
    revokeEntry(entry)
  }
  cache.clear()
  memoryUsage = 0
  queue.length = 0
  inflight.clear()
  activeLoads = 0
}

export function startCacheTimer() {
  if (timer) return
  timer = setInterval(() => {
    evictExpired()
    if (memoryUsage > MAX_CACHE_MEMORY) {
      evictFifo(0)
    }
  }, CLEAN_INTERVAL)
}

export function stopCacheTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

export function getCacheStats() {
  return {
    count: cache.size,
    memoryUsage,
    maxMemory: MAX_CACHE_MEMORY,
    queue: queue.length,
    inflight: inflight.size
  }
}
