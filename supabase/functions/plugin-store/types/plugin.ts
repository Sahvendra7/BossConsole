/**
 * Plugin type definitions
 */

export type PluginType = 'panel' | 'tab' | 'hybrid'

export interface Plugin {
  id: string
  pluginId: string
  displayName: string
  description: string
  authorId: string | null
  authorName: string
  homepageUrl: string
  iconUrl: string
  type: PluginType
  apiVersion: string
  verified: boolean
  published: boolean
  createdAt: string
  updatedAt: string
}

export interface PluginVersion {
  id: string
  pluginId: string
  version: string
  changelog: string
  minBossVersion: string
  jarPath: string
  jarSize: number
  sha256: string
  dependencies: PluginDependency[]
  publishedAt: string
  downloadCount?: number
}

export interface PluginDependency {
  pluginId: string
  versionRange: string
}

export interface PluginWithStats extends Plugin {
  latestVersion: string | null
  latestVersionId: string | null
  avgRating: number
  ratingCount: number
  downloadCount: number
  tags: string[]
  screenshots: PluginScreenshot[]
}

export interface PluginScreenshot {
  url: string
  caption: string
}

export interface PluginListItem {
  id: string
  pluginId: string
  displayName: string
  description: string
  author: string
  type: PluginType
  apiVersion: string
  verified: boolean
  iconUrl: string
  url: string
  version: string | null
  rating: number
  ratingCount: number
  downloadCount: number
  tags: string[]
  updatedAt: string
}

export interface PluginSearchResult {
  plugins: PluginListItem[]
  totalCount: number
  page: number
  pageSize: number
}

export interface PluginRating {
  id: string
  pluginId: string
  userId: string
  rating: number
  review: string
  createdAt: string
  updatedAt: string
}

export interface DownloadInfo {
  downloadUrl: string
  sha256: string
  version: string
  size: number
  versionId: string
}
