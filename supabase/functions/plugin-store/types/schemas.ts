import { z } from "zod"

// ============================================================================
// Plugin Type Schema
// ============================================================================

export const PluginTypeSchema = z.enum(['panel', 'tab', 'hybrid', 'mixed', 'service'])

// ============================================================================
// Browse Route Schemas
// ============================================================================

export const ListPluginsQuerySchema = z.object({
  page: z.string().optional().default('1').transform(Number),
  pageSize: z.string().optional().default('20').transform(Number),
  sortBy: z.enum(['name', 'downloads', 'rating', 'newest', 'updated']).optional().default('downloads')
})

export const SearchPluginsRequestSchema = z.object({
  query: z.string().optional().default(''),
  type: PluginTypeSchema.optional(),
  tags: z.array(z.string()).optional(),
  minRating: z.number().min(0).max(5).optional().default(0),
  verifiedOnly: z.boolean().optional().default(false),
  page: z.number().min(1).optional().default(1),
  pageSize: z.number().min(1).max(100).optional().default(20),
  sortBy: z.enum(['name', 'downloads', 'rating', 'newest', 'updated']).optional().default('downloads')
})

export const PluginListItemSchema = z.object({
  id: z.string().uuid(),
  pluginId: z.string(),
  displayName: z.string(),
  description: z.string(),
  author: z.string(),
  type: PluginTypeSchema,
  apiVersion: z.string(),
  verified: z.boolean(),
  iconUrl: z.string(),
  version: z.string().nullable(),
  rating: z.number(),
  ratingCount: z.number(),
  downloadCount: z.number(),
  tags: z.array(z.string()),
  updatedAt: z.string()
})

export const PluginListResponseSchema = z.object({
  plugins: z.array(PluginListItemSchema),
  totalCount: z.number(),
  page: z.number(),
  pageSize: z.number()
})

export const PluginScreenshotSchema = z.object({
  url: z.string(),
  caption: z.string()
})

export const PluginVersionSchema = z.object({
  id: z.string().uuid(),
  version: z.string(),
  changelog: z.string(),
  minBossVersion: z.string(),
  jarSize: z.number(),
  sha256: z.string(),
  dependencies: z.array(z.object({
    pluginId: z.string(),
    versionRange: z.string()
  })),
  publishedAt: z.string(),
  downloadCount: z.number()
})

export const PluginDetailResponseSchema = z.object({
  id: z.string().uuid(),
  pluginId: z.string(),
  displayName: z.string(),
  description: z.string(),
  authorId: z.string().uuid().nullable(),
  authorName: z.string(),
  homepageUrl: z.string(),
  iconUrl: z.string(),
  type: PluginTypeSchema,
  apiVersion: z.string(),
  verified: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  latestVersion: z.string().nullable(),
  avgRating: z.number(),
  ratingCount: z.number(),
  downloadCount: z.number(),
  tags: z.array(z.string()),
  screenshots: z.array(PluginScreenshotSchema),
  versions: z.array(PluginVersionSchema)
})

// ============================================================================
// Download Route Schemas
// ============================================================================

export const DownloadInfoResponseSchema = z.object({
  downloadUrl: z.string().url(),
  sha256: z.string(),
  version: z.string(),
  size: z.number(),
  versionId: z.string().uuid()
})

// ============================================================================
// Rating Route Schemas
// ============================================================================

export const RatePluginRequestSchema = z.object({
  rating: z.number().int().min(1).max(5),
  review: z.string().max(2000).optional().default('')
})

export const RatePluginResponseSchema = z.object({
  success: z.boolean(),
  ratingId: z.string().uuid().optional(),
  created: z.boolean().optional(),
  error: z.string().optional()
})

// ============================================================================
// Publish Route Schemas
// ============================================================================

export const PublishPluginRequestSchema = z.object({
  pluginId: z.string().min(3).max(100).regex(/^[a-z0-9.-]+$/i, 'Plugin ID must contain only alphanumeric characters, dots, and hyphens'),
  displayName: z.string().min(1).max(100),
  description: z.string().max(5000).optional().default(''),
  authorName: z.string().min(1).max(100).optional(), // Optional custom author name, defaults to email username
  homepageUrl: z.string().url('homepageUrl must be a valid URL (required for publishing)'),
  iconUrl: z.union([z.string().url(), z.literal('')]).optional().default(''),
  type: PluginTypeSchema.optional().default('panel'),
  apiVersion: z.string().optional().default('1.0'),
  tags: z.array(z.string().max(50)).max(10).optional().default([])
})

export const PublishPluginResponseSchema = z.object({
  success: z.boolean(),
  id: z.string().uuid().optional(),
  pluginId: z.string().optional(),
  error: z.string().optional()
})

export const PublishVersionRequestSchema = z.object({
  version: z.string().regex(/^\d+\.\d+\.\d+$/, 'Version must be in semver format (e.g., 1.0.0)'),
  changelog: z.string().max(5000).optional().default(''),
  minBossVersion: z.string().optional().default('1.0.0'),
  dependencies: z.array(z.object({
    pluginId: z.string(),
    versionRange: z.string()
  })).optional().default([])
})

export const PublishVersionResponseSchema = z.object({
  success: z.boolean(),
  versionId: z.string().uuid().optional(),
  uploadUrl: z.string().url().optional(),
  error: z.string().optional()
})

export const FinalizeVersionRequestSchema = z.object({
  versionId: z.string().uuid(),
  sha256: z.string().length(64, 'SHA-256 hash must be 64 characters'),
  jarSize: z.number().int().positive()
})

export const FinalizeVersionResponseSchema = z.object({
  success: z.boolean(),
  error: z.string().optional()
})

// ============================================================================
// Simplified GitHub Publish Schema
// ============================================================================

export const PublishFromGitHubRequestSchema = z.object({
  githubUrl: z.string().url('Must be a valid GitHub URL').refine(
    (url) => url.includes('github.com'),
    'URL must be a GitHub repository URL'
  ),
  changelog: z.string().max(5000).optional(),
  tags: z.array(z.string().max(50)).max(10).optional().default([])
})

export const PublishFromGitHubResponseSchema = z.object({
  success: z.boolean(),
  pluginId: z.string().optional(),
  displayName: z.string().optional(),
  version: z.string().optional(),
  created: z.boolean().optional(), // true if new plugin, false if updated
  error: z.string().optional()
})

// ============================================================================
// GitHub Metadata-Only Publish Schema (for large JARs)
// ============================================================================

export const PublishFromGitHubMetadataRequestSchema = z.object({
  githubUrl: z.string().url('Must be a valid GitHub URL').refine(
    (url) => url.includes('github.com'),
    'URL must be a GitHub repository URL'
  ),
  manifest: z.object({
    pluginId: z.string(),
    displayName: z.string(),
    version: z.string(),
    apiVersion: z.string().optional().default('1.0.0'),
    mainClass: z.string().optional().default(''),
    type: z.string().optional().default('panel'),
    description: z.string().optional().default(''),
  }),
  jarSize: z.number().int().positive(),
  changelog: z.string().max(5000).optional(),
  tags: z.array(z.string().max(50)).max(10).optional().default([])
})

export const PublishFromGitHubMetadataResponseSchema = z.object({
  success: z.boolean(),
  pluginId: z.string().optional(),
  displayName: z.string().optional(),
  version: z.string().optional(),
  created: z.boolean().optional(),
  error: z.string().optional()
})

// ============================================================================
// Common Schemas
// ============================================================================

export const ErrorResponseSchema = z.object({
  error: z.string()
})

export const PopularTagsResponseSchema = z.object({
  tags: z.array(z.object({
    tag: z.string(),
    count: z.number()
  }))
})
