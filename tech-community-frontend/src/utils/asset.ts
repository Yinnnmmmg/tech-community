const ABSOLUTE_URL_PATTERN = /^(?:[a-z][a-z\d+\-.]*:)?\/\//i
const LEGACY_OSS_HOST = 'https://tech-community-dev.oss-cn-hangzhou.aliyuncs.com'
const CURRENT_OSS_HOST = 'https://hyw-tech-community.oss-cn-beijing.aliyuncs.com'

export function resolveAssetUrl(url?: string | null) {
  if (!url) {
    return ''
  }

  const normalized = url.trim()
  if (!normalized) {
    return ''
  }

  if (normalized.startsWith(LEGACY_OSS_HOST)) {
    return normalized.replace(LEGACY_OSS_HOST, CURRENT_OSS_HOST)
  }

  if (
    normalized.startsWith('blob:') ||
    normalized.startsWith('data:') ||
    ABSOLUTE_URL_PATTERN.test(normalized)
  ) {
    return normalized
  }

  if (normalized.startsWith('/api/') || normalized.startsWith('/covers/')) {
    return normalized
  }

  return normalized.startsWith('/') ? `/api${normalized}` : `/api/${normalized}`
}
