/**
 * GitHub Service for Plugin Publishing
 *
 * Handles fetching releases and downloading JAR files from GitHub repositories.
 */

import { PluginManifest } from "../types/plugin.ts"

/**
 * GitHub release asset information
 */
interface GitHubAsset {
  name: string
  browser_download_url: string
  size: number
  content_type: string
}

/**
 * GitHub release information
 */
interface GitHubRelease {
  tag_name: string
  name: string
  assets: GitHubAsset[]
  published_at: string
  body: string
}

/**
 * Result of fetching a plugin from GitHub
 */
export interface GitHubPluginResult {
  manifest: PluginManifest
  jarData: ArrayBuffer
  jarSize: number
  sha256: string
  releaseNotes: string
  version: string
}

/**
 * Parse GitHub URL to extract owner and repo
 *
 * Supports formats:
 * - https://github.com/owner/repo
 * - https://github.com/owner/repo/releases
 * - https://github.com/owner/repo/releases/tag/v1.0.0
 * - github.com/owner/repo
 */
export function parseGitHubUrl(url: string): { owner: string; repo: string; tag?: string } | null {
  // Normalize URL
  let normalized = url.trim()
  if (!normalized.startsWith("http")) {
    normalized = `https://${normalized}`
  }

  try {
    const parsed = new URL(normalized)
    if (parsed.hostname !== "github.com") {
      return null
    }

    const parts = parsed.pathname.split("/").filter((p) => p.length > 0)
    if (parts.length < 2) {
      return null
    }

    const owner = parts[0]
    const repo = parts[1]

    // Check for specific tag
    if (parts.length >= 4 && parts[2] === "releases" && parts[3] === "tag") {
      return { owner, repo, tag: parts[4] }
    }

    return { owner, repo }
  } catch {
    return null
  }
}

/**
 * Fetch the latest release from a GitHub repository
 */
export async function fetchLatestRelease(
  owner: string,
  repo: string,
  tag?: string
): Promise<GitHubRelease> {
  const apiUrl = tag
    ? `https://api.github.com/repos/${owner}/${repo}/releases/tags/${tag}`
    : `https://api.github.com/repos/${owner}/${repo}/releases/latest`

  const response = await fetch(apiUrl, {
    headers: {
      Accept: "application/vnd.github.v3+json",
      "User-Agent": "BOSS-Plugin-Store/1.0",
    },
  })

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error(
        tag
          ? `Release tag '${tag}' not found for ${owner}/${repo}`
          : `No releases found for ${owner}/${repo}. Make sure the repository has at least one release.`
      )
    }
    throw new Error(`GitHub API error: ${response.status} ${response.statusText}`)
  }

  return await response.json()
}

/**
 * Find the plugin JAR asset in a release
 */
export function findJarAsset(release: GitHubRelease): GitHubAsset | null {
  // Look for .jar files, prefer ones with "plugin" in the name
  const jarAssets = release.assets.filter((a) => a.name.endsWith(".jar"))

  if (jarAssets.length === 0) {
    return null
  }

  // Prefer assets with "plugin" in name
  const pluginJar = jarAssets.find(
    (a) => a.name.toLowerCase().includes("plugin") || a.name.toLowerCase().includes("boss")
  )

  return pluginJar || jarAssets[0]
}

/**
 * Download a JAR file from GitHub
 */
export async function downloadJar(downloadUrl: string): Promise<ArrayBuffer> {
  const response = await fetch(downloadUrl, {
    headers: {
      "User-Agent": "BOSS-Plugin-Store/1.0",
    },
  })

  if (!response.ok) {
    throw new Error(`Failed to download JAR: ${response.status} ${response.statusText}`)
  }

  return await response.arrayBuffer()
}

/**
 * Extract plugin.json from a JAR file (which is a ZIP)
 */
export async function extractManifestFromJar(jarData: ArrayBuffer): Promise<PluginManifest> {
  // JAR files are ZIP files - we need to parse the ZIP to find plugin.json
  const uint8Array = new Uint8Array(jarData)

  // Find the plugin.json entry in the ZIP
  const manifestPath = "META-INF/boss-plugin/plugin.json"
  const manifestContent = await extractFileFromZip(uint8Array, manifestPath)

  if (!manifestContent) {
    throw new Error(
      `Plugin manifest not found at ${manifestPath}. Make sure your plugin JAR contains a valid plugin.json.`
    )
  }

  try {
    const manifest = JSON.parse(manifestContent) as PluginManifest
    validateManifest(manifest)
    return manifest
  } catch (e) {
    if (e instanceof Error && e.message.startsWith("Invalid plugin manifest")) {
      throw e
    }
    throw new Error(`Failed to parse plugin.json: ${(e as Error).message}`)
  }
}

/**
 * Extract a file from a ZIP archive (simplified implementation)
 */
async function extractFileFromZip(
  zipData: Uint8Array,
  targetPath: string
): Promise<string | null> {
  // ZIP file structure:
  // - Local file headers followed by file data
  // - Central directory at the end

  const view = new DataView(zipData.buffer)
  let offset = 0

  while (offset < zipData.length - 4) {
    // Look for local file header signature (0x04034b50)
    const signature = view.getUint32(offset, true)
    if (signature !== 0x04034b50) {
      // Not a local file header, might be central directory
      break
    }

    // Parse local file header
    const compressionMethod = view.getUint16(offset + 8, true)
    const compressedSize = view.getUint32(offset + 18, true)
    const uncompressedSize = view.getUint32(offset + 22, true)
    const fileNameLength = view.getUint16(offset + 26, true)
    const extraFieldLength = view.getUint16(offset + 28, true)

    // Read filename
    const fileNameBytes = zipData.slice(offset + 30, offset + 30 + fileNameLength)
    const fileName = new TextDecoder().decode(fileNameBytes)

    // Calculate data offset
    const dataOffset = offset + 30 + fileNameLength + extraFieldLength

    if (fileName === targetPath) {
      // Found our file
      const fileData = zipData.slice(dataOffset, dataOffset + compressedSize)

      if (compressionMethod === 0) {
        // Stored (no compression)
        return new TextDecoder().decode(fileData)
      } else if (compressionMethod === 8) {
        // Deflate compression - use DecompressionStream
        try {
          const ds = new DecompressionStream("deflate-raw")
          const writer = ds.writable.getWriter()
          writer.write(fileData)
          writer.close()

          const reader = ds.readable.getReader()
          const chunks: Uint8Array[] = []
          let totalLength = 0

          while (true) {
            const { done, value } = await reader.read()
            if (done) break
            chunks.push(value)
            totalLength += value.length
          }

          const result = new Uint8Array(totalLength)
          let position = 0
          for (const chunk of chunks) {
            result.set(chunk, position)
            position += chunk.length
          }

          return new TextDecoder().decode(result)
        } catch {
          throw new Error("Failed to decompress plugin.json from JAR")
        }
      } else {
        throw new Error(`Unsupported compression method: ${compressionMethod}`)
      }
    }

    // Move to next entry
    offset = dataOffset + compressedSize
  }

  return null
}

/**
 * Validate a plugin manifest
 */
function validateManifest(manifest: PluginManifest): void {
  const errors: string[] = []

  if (!manifest.pluginId || typeof manifest.pluginId !== "string") {
    errors.push("pluginId is required")
  } else if (!/^[a-zA-Z][a-zA-Z0-9_-]*(?:\.[a-zA-Z0-9_-]+)+$/.test(manifest.pluginId)) {
    errors.push("pluginId must follow reverse domain notation (e.g., com.example.plugin)")
  }

  if (!manifest.displayName || typeof manifest.displayName !== "string") {
    errors.push("displayName is required")
  }

  if (!manifest.version || typeof manifest.version !== "string") {
    errors.push("version is required")
  } else if (!/^\d+\.\d+\.\d+/.test(manifest.version)) {
    errors.push("version must follow semantic versioning (e.g., 1.0.0)")
  }

  if (!manifest.apiVersion || typeof manifest.apiVersion !== "string") {
    errors.push("apiVersion is required")
  }

  if (!manifest.mainClass || typeof manifest.mainClass !== "string") {
    errors.push("mainClass is required")
  }

  if (errors.length > 0) {
    throw new Error(`Invalid plugin manifest: ${errors.join("; ")}`)
  }
}

/**
 * Calculate SHA-256 hash of data
 */
export async function calculateSha256(data: ArrayBuffer): Promise<string> {
  const hashBuffer = await crypto.subtle.digest("SHA-256", data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("")
}

/**
 * Fetch plugin from GitHub - main entry point
 *
 * @param githubUrl - GitHub repository URL
 * @returns Plugin manifest, JAR data, and metadata
 */
export async function fetchPluginFromGitHub(githubUrl: string): Promise<GitHubPluginResult> {
  // Parse GitHub URL
  const parsed = parseGitHubUrl(githubUrl)
  if (!parsed) {
    throw new Error(
      "Invalid GitHub URL. Expected format: https://github.com/owner/repo"
    )
  }

  // Fetch release
  const release = await fetchLatestRelease(parsed.owner, parsed.repo, parsed.tag)

  // Find JAR asset
  const jarAsset = findJarAsset(release)
  if (!jarAsset) {
    throw new Error(
      `No JAR file found in release ${release.tag_name}. Make sure your release includes a .jar file.`
    )
  }

  // Download JAR
  const jarData = await downloadJar(jarAsset.browser_download_url)

  // Extract manifest
  const manifest = await extractManifestFromJar(jarData)

  // Calculate SHA-256
  const sha256 = await calculateSha256(jarData)

  // Extract version from tag (remove 'v' prefix if present)
  const version = manifest.version

  return {
    manifest,
    jarData,
    jarSize: jarData.byteLength,
    sha256,
    releaseNotes: release.body || "",
    version,
  }
}
