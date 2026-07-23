/**
 * 前端图片内存缓存（blob URL）
 * - 后台预加载 + 路径分区
 * - 最大内存上限，超出 FIFO
 * - 定时 TTL 清理
 * - 进入子目录/返回上级可清旧路径区
 */

import { withMediaToken } from '@/utils/mediaAuth'

const MAX_CACHE_MEMORY = 80 * 1024 * 1024
const CACHE_TTL = 15 * 60 * 1000
const CLEAN_INTERVAL = 60 * 1000
const PRELOAD_CONCURRENCY = 4
const PRELOAD_BATCH = 12
const DEFAULT_ENTRY_SIZE = 80 * 1024

/** @type {Map<string, { blobUrl: string, size: number, ts: number, pathKey: string }>} */
const cache = new Map()
const inflight = new Set()
/** @type {string[]} */
const queue = []

let memoryUsage = 0
let activeLoads = 0
let timer = null
/** 当前浏览路径分区 */
let activePathKey = ''

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
    } catch (_) { /* ignore */ }
  }
}

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
  cache.set(url, { blobUrl, size, ts: now(), pathKey: activePathKey || '' })
  memoryUsage += size
  if (memoryUsage > MAX_CACHE_MEMORY) evictFifo(0)
}

function cacheKey(url) {
  return withMediaToken(url) || url
}

/**
 * 切换浏览路径：清理非当前路径的缓存（进入子目录 / 返回上级）
 */
export function setCachePath(pathKey) {
  const next = pathKey || ''
  if (next === activePathKey) return
  activePathKey = next
  // 清队列里尚未加载的
  queue.length = 0
  for (const [url, entry] of [...cache.entries()]) {
    if (entry.pathKey && entry.pathKey !== activePathKey) {
      memoryUsage = Math.max(0, memoryUsage - entry.size)
      cache.delete(url)
      revokeEntry(entry)
    }
  }
}

export function getCachedSrc(url) {
  if (!url) return ''
  const key = cacheKey(url)
  const entry = cache.get(key)
  if (!entry) return key
  cache.delete(key)
  entry.ts = now()
  cache.set(key, entry)
  return entry.blobUrl || key
}

export function hasCached(url) {
  return !!url && cache.has(cacheKey(url))
}

export function cacheImage(url) {
  const key = cacheKey(url)
  if (!key || cache.has(key) || inflight.has(key)) return Promise.resolve(getCachedSrc(url))
  inflight.add(key)
  return fetch(key, { credentials: 'same-origin', cache: 'force-cache' })
    .then((res) => {
      if (!res.ok) throw new Error(`cache fetch ${res.status}`)
      return res.blob()
    })
    .then((blob) => {
      const size = estimateBlobSize(blob)
      const blobUrl = URL.createObjectURL(blob)
      put(key, blobUrl, size)
      return blobUrl
    })
    .catch(() => key)
    .finally(() => {
      inflight.delete(key)
      activeLoads = Math.max(0, activeLoads - 1)
      pumpQueue()
    })
}

function pumpQueue() {
  while (activeLoads < PRELOAD_CONCURRENCY && queue.length > 0) {
    const url = queue.shift()
    const key = cacheKey(url)
    if (!key || cache.has(key) || inflight.has(key)) continue
    activeLoads++
    cacheImage(key)
  }
}

export function preloadImages(urls, { limit = PRELOAD_BATCH } = {}) {
  if (!urls?.length) return
  evictExpired()
  const toAdd = []
  for (const url of urls) {
    const key = cacheKey(url)
    if (!key || cache.has(key) || inflight.has(key) || queue.includes(key)) continue
    toAdd.push(key)
    if (toAdd.length >= limit) break
  }
  if (toAdd.length === 0) return
  queue.push(...toAdd)
  pumpQueue()
}

export function rememberLoaded(url, imgEl) {
  if (!url) return
  if (cache.has(cacheKey(url))) return
  cacheImage(url)
  void imgEl
}

export function clearImageCache() {
  for (const [, entry] of cache) revokeEntry(entry)
  cache.clear()
  memoryUsage = 0
  queue.length = 0
  inflight.clear()
  activeLoads = 0
  activePathKey = ''
}

export function startCacheTimer() {
  if (timer) return
  timer = setInterval(() => {
    evictExpired()
    if (memoryUsage > MAX_CACHE_MEMORY) evictFifo(0)
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
    inflight: inflight.size,
    pathKey: activePathKey
  }
}
