/**
 * 为媒体 URL 追加 JWT query token（&lt;img&gt;/&lt;video&gt;/fetch 无法带 Authorization 时使用）
 */

export function getAuthToken() {
  return localStorage.getItem('token') || ''
}

/**
 * 给相对媒体路径追加 ?token=
 */
export function withMediaToken(url) {
  if (!url || typeof url !== 'string') return url || ''
  if (url.startsWith('blob:') || url.startsWith('data:')) return url
  if (url.startsWith('http://') || url.startsWith('https://')) {
    // 同源 API 也补 token
    try {
      const u = new URL(url, window.location.origin)
      if (u.origin !== window.location.origin) return url
      if (!u.pathname.startsWith('/api/files/')) return url
      if (u.searchParams.has('token')) return url
      const token = getAuthToken()
      if (!token) return url
      u.searchParams.set('token', token)
      return u.pathname + u.search + u.hash
    } catch (_) {
      return url
    }
  }
  if (!url.startsWith('/api/files/')) return url
  if (url.includes('token=')) return url
  const token = getAuthToken()
  if (!token) return url
  return url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(token)
}
