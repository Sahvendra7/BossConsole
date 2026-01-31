import { createRoute, OpenAPIHono, z } from "@hono/zod-openapi"
import type { PluginStoreContext } from "../types/context.ts"
import {
  PublishPluginRequestSchema,
  PublishPluginResponseSchema,
  PublishVersionRequestSchema,
  PublishVersionResponseSchema,
  FinalizeVersionRequestSchema,
  FinalizeVersionResponseSchema,
  ErrorResponseSchema
} from "../types/schemas.ts"
import { getPlugin, createPlugin, setPluginTags, getPluginById } from "../services/plugins.ts"
import { createVersion, versionExists, finalizeVersion, getVersionById } from "../services/versions.ts"
import { getSignedUploadUrl, generateJarPath } from "../services/storage.ts"
import { getUserFromToken, getUserDisplayName } from "../utils/auth.ts"

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

    // Verify authentication
    const authHeader = ctx.req.header('Authorization')
    const user = await getUserFromToken(supabase, authHeader)
    
    if (!user) {
      return ctx.json({ success: false, error: 'Authentication required' }, 401)
    }

    // Check if plugin ID already exists
    const existing = await getPlugin(supabase, body.pluginId)
    if (existing) {
      return ctx.json({ success: false, error: 'Plugin ID already exists' }, 400)
    }

    // Get author display name
    const authorName = await getUserDisplayName(supabase, user.userId)

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

    // Verify authentication
    const authHeader = ctx.req.header('Authorization')
    const user = await getUserFromToken(supabase, authHeader)
    
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

    // Verify authentication
    const authHeader = ctx.req.header('Authorization')
    const user = await getUserFromToken(supabase, authHeader)
    
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

    return ctx.json({ success: true }, 200)
  } catch (error) {
    console.error('Error finalizing version:', error)
    return ctx.json({ 
      success: false, 
      error: (error as Error).message 
    }, 500)
  }
})

export default publish
