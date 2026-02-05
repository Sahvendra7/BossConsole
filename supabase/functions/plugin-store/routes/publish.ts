import { createRoute, OpenAPIHono, z } from "@hono/zod-openapi"
import type { PluginStoreContext } from "../types/context.ts"
import {
  PublishPluginRequestSchema,
  PublishPluginResponseSchema,
  PublishVersionRequestSchema,
  PublishVersionResponseSchema,
  FinalizeVersionRequestSchema,
  FinalizeVersionResponseSchema,
  PublishFromGitHubRequestSchema,
  PublishFromGitHubResponseSchema,
  ErrorResponseSchema
} from "../types/schemas.ts"
import { getPlugin, createPlugin, setPluginTags, getPluginById, updatePlugin } from "../services/plugins.ts"
import { createVersion, versionExists, finalizeVersion, getVersionById } from "../services/versions.ts"
import { getSignedUploadUrl, generateJarPath, uploadJar } from "../services/storage.ts"
import { getAuthenticatedUser, getUserDisplayName, logApiKeyAction } from "../utils/auth.ts"
import { fetchPluginFromGitHub } from "../services/github.ts"

const publish = new OpenAPIHono<{ Variables: PluginStoreContext }>()

// ============================================================================
// POST /publish - Publish a new plugin
// ============================================================================

const publishPluginRoute = createRoute({
  method: 'post',
  path: '/publish',
  tags: ['Publish'],
  summary: 'Publish a new plugin',
  description: 'Create a new plugin entry in the store. Requires authentication.',
  request: {
    body: {
      content: {
        'application/json': {
          schema: PublishPluginRequestSchema
        }
      }
    }
  },
  responses: {
    201: {
      description: 'Plugin published successfully',
      content: {
        'application/json': {
          schema: PublishPluginResponseSchema
        }
      }
    },
    400: {
      description: 'Invalid request or plugin ID already exists',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    401: {
      description: 'Authentication required',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    500: {
      description: 'Internal server error',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    }
  }
})

publish.openapi(publishPluginRoute, async (ctx) => {
  try {
    const supabase = ctx.get("supabase")
    const body = ctx.req.valid('json')

    // Verify authentication (JWT or API key with 'publish' scope)
    const authHeader = ctx.req.header('Authorization')
    const apiKeyHeader = ctx.req.header('X-API-Key')
    const user = await getAuthenticatedUser(supabase, authHeader, apiKeyHeader, {
      allowApiKey: true,
      requiredScopes: ['publish'],
    })
    
    if (!user) {
      return ctx.json({ success: false, error: 'Authentication required' }, 401)
    }

    // Check if plugin ID already exists
    const existing = await getPlugin(supabase, body.pluginId)
    if (existing) {
      return ctx.json({ success: false, error: 'Plugin ID already exists' }, 400)
    }

    // Get author display name - use custom name if provided, otherwise derive from email
    const authorName = body.authorName || await getUserDisplayName(supabase, user.userId)

    // Create plugin
    const result = await createPlugin(
      supabase,
      user.userId,
      authorName,
      body.pluginId,
      body.displayName,
      body.description,
      body.homepageUrl,
      body.iconUrl,
      body.type,
      body.apiVersion
    )

    // Set tags
    if (body.tags.length > 0) {
      await setPluginTags(supabase, result.id, body.tags)
    }

    // Log API key usage if applicable
    if (user.apiKeyId) {
      await logApiKeyAction(
        supabase,
        user.apiKeyId,
        'publish',
        body.pluginId,
        ctx.req.raw,
        true
      )
    }

    return ctx.json({
      success: true,
      id: result.id,
      pluginId: body.pluginId
    }, 201)
  } catch (error) {
    console.error('Error publishing plugin:', error)
    return ctx.json({ 
      success: false, 
      error: (error as Error).message 
    }, 500)
  }
})

// ============================================================================
// POST /:pluginId/version - Publish a new version
// ============================================================================

const publishVersionRoute = createRoute({
  method: 'post',
  path: '/{pluginId}/version',
  tags: ['Publish'],
  summary: 'Publish a new version',
  description: 'Create a new version for an existing plugin. Returns an upload URL for the JAR file. Requires authentication.',
  request: {
    params: z.object({
      pluginId: z.string()
    }),
    body: {
      content: {
        'application/json': {
          schema: PublishVersionRequestSchema
        }
      }
    }
  },
  responses: {
    201: {
      description: 'Version created successfully, use uploadUrl to upload JAR',
      content: {
        'application/json': {
          schema: PublishVersionResponseSchema
        }
      }
    },
    400: {
      description: 'Invalid request or version already exists',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    401: {
      description: 'Authentication required',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    403: {
      description: 'Not authorized to publish to this plugin',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    404: {
      description: 'Plugin not found',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    500: {
      description: 'Internal server error',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    }
  }
})

publish.openapi(publishVersionRoute, async (ctx) => {
  try {
    const supabase = ctx.get("supabase")
    const { pluginId } = ctx.req.valid('param')
    const body = ctx.req.valid('json')

    // Verify authentication (JWT or API key with 'version' scope)
    const authHeader = ctx.req.header('Authorization')
    const apiKeyHeader = ctx.req.header('X-API-Key')
    const user = await getAuthenticatedUser(supabase, authHeader, apiKeyHeader, {
      allowApiKey: true,
      requiredScopes: ['version'],
    })
    
    if (!user) {
      return ctx.json({ success: false, error: 'Authentication required' }, 401)
    }

    // Get plugin
    const plugin = await getPlugin(supabase, pluginId)
    if (!plugin) {
      return ctx.json({ success: false, error: 'Plugin not found' }, 404)
    }

    // Verify ownership
    if (plugin.authorId !== user.userId) {
      return ctx.json({ success: false, error: 'Not authorized to publish to this plugin' }, 403)
    }

    // Check if version already exists
    if (await versionExists(supabase, plugin.id, body.version)) {
      return ctx.json({ success: false, error: 'Version already exists' }, 400)
    }

    // Generate JAR path
    const jarPath = generateJarPath(pluginId, body.version)

    // Create version record
    const result = await createVersion(
      supabase,
      plugin.id,
      body.version,
      body.changelog,
      body.minBossVersion,
      body.dependencies,
      jarPath
    )

    // Generate upload URL
    const uploadUrl = await getSignedUploadUrl(supabase, jarPath)

    // Log API key usage if applicable
    if (user.apiKeyId) {
      await logApiKeyAction(
        supabase,
        user.apiKeyId,
        'version',
        pluginId,
        ctx.req.raw,
        true
      )
    }

    return ctx.json({
      success: true,
      versionId: result.id,
      uploadUrl
    }, 201)
  } catch (error) {
    console.error('Error publishing version:', error)
    return ctx.json({ 
      success: false, 
      error: (error as Error).message 
    }, 500)
  }
})

// ============================================================================
// POST /version/finalize - Finalize version after JAR upload
// ============================================================================

const finalizeVersionRoute = createRoute({
  method: 'post',
  path: '/version/finalize',
  tags: ['Publish'],
  summary: 'Finalize version after JAR upload',
  description: 'Update version metadata after JAR has been uploaded. Requires authentication.',
  request: {
    body: {
      content: {
        'application/json': {
          schema: FinalizeVersionRequestSchema
        }
      }
    }
  },
  responses: {
    200: {
      description: 'Version finalized successfully',
      content: {
        'application/json': {
          schema: FinalizeVersionResponseSchema
        }
      }
    },
    400: {
      description: 'Invalid request',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    401: {
      description: 'Authentication required',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    403: {
      description: 'Not authorized',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    404: {
      description: 'Version not found',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    500: {
      description: 'Internal server error',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    }
  }
})

publish.openapi(finalizeVersionRoute, async (ctx) => {
  try {
    const supabase = ctx.get("supabase")
    const body = ctx.req.valid('json')

    // Verify authentication (JWT or API key with 'finalize' scope)
    const authHeader = ctx.req.header('Authorization')
    const apiKeyHeader = ctx.req.header('X-API-Key')
    const user = await getAuthenticatedUser(supabase, authHeader, apiKeyHeader, {
      allowApiKey: true,
      requiredScopes: ['finalize'],
    })
    
    if (!user) {
      return ctx.json({ success: false, error: 'Authentication required' }, 401)
    }

    // Get version
    const version = await getVersionById(supabase, body.versionId)
    if (!version) {
      return ctx.json({ success: false, error: 'Version not found' }, 404)
    }

    // Get plugin to verify ownership
    const plugin = await getPluginById(supabase, version.pluginId)
    if (!plugin) {
      return ctx.json({ success: false, error: 'Plugin not found' }, 404)
    }

    // Verify ownership
    if (plugin.authorId !== user.userId) {
      return ctx.json({ success: false, error: 'Not authorized' }, 403)
    }

    // Finalize version
    await finalizeVersion(supabase, body.versionId, body.sha256, body.jarSize)

    // Log API key usage if applicable
    if (user.apiKeyId) {
      await logApiKeyAction(
        supabase,
        user.apiKeyId,
        'finalize',
        plugin.pluginId, // Use the string pluginId from the looked-up plugin
        ctx.req.raw,
        true
      )
    }

    return ctx.json({ success: true }, 200)
  } catch (error) {
    console.error('Error finalizing version:', error)
    return ctx.json({ 
      success: false, 
      error: (error as Error).message 
    }, 500)
  }
})

// ============================================================================
// POST /github - Simplified publish from GitHub URL
// ============================================================================

const publishFromGitHubRoute = createRoute({
  method: 'post',
  path: '/github',
  tags: ['Publish'],
  summary: 'Publish plugin from GitHub release',
  description: 'Simplified endpoint that fetches a plugin JAR from GitHub releases, extracts metadata from plugin.json, and publishes in one step. Requires authentication.',
  request: {
    body: {
      content: {
        'application/json': {
          schema: PublishFromGitHubRequestSchema
        }
      }
    }
  },
  responses: {
    201: {
      description: 'Plugin published successfully',
      content: {
        'application/json': {
          schema: PublishFromGitHubResponseSchema
        }
      }
    },
    400: {
      description: 'Invalid request or GitHub URL',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    401: {
      description: 'Authentication required',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    403: {
      description: 'Not authorized to publish to this plugin',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    },
    500: {
      description: 'Internal server error',
      content: {
        'application/json': {
          schema: ErrorResponseSchema
        }
      }
    }
  }
})

publish.openapi(publishFromGitHubRoute, async (ctx) => {
  try {
    const supabase = ctx.get("supabase")
    const body = ctx.req.valid('json')

    // Verify authentication (JWT or API key with 'publish' scope)
    const authHeader = ctx.req.header('Authorization')
    const apiKeyHeader = ctx.req.header('X-API-Key')
    const user = await getAuthenticatedUser(supabase, authHeader, apiKeyHeader, {
      allowApiKey: true,
      requiredScopes: ['publish'],
    })

    if (!user) {
      return ctx.json({ success: false, error: 'Authentication required' }, 401)
    }

    // Fetch plugin from GitHub
    console.log(`Fetching plugin from GitHub: ${body.githubUrl}`)
    const githubResult = await fetchPluginFromGitHub(body.githubUrl)
    const { manifest, jarData, jarSize, sha256, releaseNotes, version } = githubResult

    console.log(`Extracted manifest: ${manifest.pluginId} v${version}`)

    // Check if plugin already exists
    const existingPlugin = await getPlugin(supabase, manifest.pluginId)
    let pluginUuid: string
    let isNewPlugin = false

    if (existingPlugin) {
      // Plugin exists - verify ownership
      if (existingPlugin.authorId !== user.userId) {
        return ctx.json({
          success: false,
          error: 'Not authorized to publish to this plugin. You are not the owner.'
        }, 403)
      }

      pluginUuid = existingPlugin.id

      // Update plugin metadata from manifest
      await updatePlugin(supabase, pluginUuid, {
        displayName: manifest.displayName,
        description: manifest.description,
        homepageUrl: manifest.url || manifest.homepageUrl,
        iconUrl: manifest.iconUrl,
        type: manifest.type as string,
        apiVersion: manifest.apiVersion
      })
    } else {
      // Create new plugin
      isNewPlugin = true
      const authorName = manifest.author || await getUserDisplayName(supabase, user.userId)

      const result = await createPlugin(
        supabase,
        user.userId,
        authorName,
        manifest.pluginId,
        manifest.displayName,
        manifest.description || '',
        manifest.url || manifest.homepageUrl || body.githubUrl, // Fall back to GitHub URL if no homepage
        manifest.iconUrl || '',
        (manifest.type as string) || 'panel',
        manifest.apiVersion
      )

      pluginUuid = result.id
    }

    // Set tags (from manifest or request body)
    const tags = body.tags?.length ? body.tags : (manifest.tags || [])
    if (tags.length > 0) {
      await setPluginTags(supabase, pluginUuid, tags)
    }

    // Check if version already exists
    if (await versionExists(supabase, pluginUuid, version)) {
      return ctx.json({
        success: false,
        error: `Version ${version} already exists for ${manifest.pluginId}`
      }, 400)
    }

    // Generate JAR path and upload
    const jarPath = generateJarPath(manifest.pluginId, version)
    console.log(`Uploading JAR to: ${jarPath}`)
    await uploadJar(supabase, jarPath, jarData)

    // Create version record
    const changelog = body.changelog || releaseNotes || ''
    const versionResult = await createVersion(
      supabase,
      pluginUuid,
      version,
      changelog,
      manifest.minBossVersion || '1.0.0',
      manifest.dependencies || [],
      jarPath
    )

    // Finalize version with SHA256 and size
    await finalizeVersion(supabase, versionResult.id, sha256, jarSize)

    // Log API key usage if applicable
    if (user.apiKeyId) {
      await logApiKeyAction(
        supabase,
        user.apiKeyId,
        'publish',
        manifest.pluginId,
        ctx.req.raw,
        true
      )
    }

    console.log(`Successfully published ${manifest.pluginId} v${version}`)

    return ctx.json({
      success: true,
      pluginId: manifest.pluginId,
      displayName: manifest.displayName,
      version: version,
      created: isNewPlugin
    }, 201)
  } catch (error) {
    console.error('Error publishing from GitHub:', error)
    return ctx.json({
      success: false,
      error: (error as Error).message
    }, 500)
  }
})

export default publish
