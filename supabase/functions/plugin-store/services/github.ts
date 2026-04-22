/**
 * GitHub Service for Plugin Publishing
 *
 * Handles fetching releases and downloading JAR files from GitHub repositories.
 */

import { PluginManifest } from "../types/plugin.ts"
import { createHash } from "node:crypto"

/**
 * Hosts permitted to serve externally-hosted plugin JARs.
 *
 * `github.com` is where `browser_download_url` points; GitHub 302-redirects
 * to `objects.githubusercontent.com` (legacy CDN) or
 * `release-assets.githubusercontent.com` (current CDN). Redirects are
 * followed by `fetch` transparently, but we still allow those hosts for
 * any future code path that stores a resolved URL directly.
 */
const ALLOWED_EXTERNAL_JAR_HOSTS = new Set([
  "github.com",
  "objects.githubusercontent.com",
  "release-assets.githubusercontent.com",
])

/**
 * Returns true if [url] is an HTTPS URL hosted on a GitHub release asset host.
 * Used as an allowlist for both publish and download paths so a corrupted or
 * malicious `jar_path` cannot redirect clients to an arbitrary HTTPS origin.
 */
export function isAllowedExternalJarUrl(url: string): boolean {
  try {
    const parsed = new URL(url)
    return (
      parsed.protocol === "https:" &&
      ALLOWED_EXTERNAL_JAR_HOSTS.has(parsed.hostname)
    )
  } catch {
    return false
  }
}

/**
 * Stream-compute the SHA-256 of a remote JAR without buffering it in memory.
 *
 * Used by /github/metadata to derive the authoritative hash server-side
 * instead of trusting the publisher's submitted value. Streaming keeps peak
 * memory bounded (one fetch chunk at a time — typically 16–64 KB), so this
 * works for JARs that exceed the edge function's ArrayBuffer limits.
 *
 * @param downloadUrl URL of the JAR (must be on an allowed host)
 * @returns The hex-encoded SHA-256 and the number of bytes streamed
 */
export async function computeRemoteSha256(
  downloadUrl: string
): Promise<{ sha256: string; totalBytes: number }> {
  const response = await fetch(downloadUrl, {
    headers: { "User-Agent": "BOSS-Plugin-Store/1.0" },
  })
  if (!response.ok) {
    throw new Error(
      `Failed to fetch JAR for hashing: ${response.status} ${response.statusText}`
    )
  }
  if (!response.body) {
    throw new Error("Remote JAR response has no body")
  }

  const hash = createHash("sha256")
  const reader = response.body.getReader()
  let totalBytes = 0
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      if (value && value.length > 0) {
        hash.update(value)
        totalBytes += value.length
      }
    }
  } finally {
    reader.releaseLock()
  }

  return { sha256: hash.digest("hex"), totalBytes }
}

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
 * Download a byte range from a URL.
 * Returns the bytes and the total file size (from Content-Range header).
 */
async function downloadRange(
  url: string,
  start: number,
  end: number
): Promise<{ data: Uint8Array; totalSize: number }> {
  const response = await fetch(url, {
    headers: {
      "User-Agent": "BOSS-Plugin-Store/1.0",
      Range: `bytes=${start}-${end}`,
    },
  })

  if (response.status !== 206 && response.status !== 200) {
    throw new Error(`Range request failed: ${response.status}`)
  }

  const data = new Uint8Array(await response.arrayBuffer())

  // Parse total size from Content-Range: bytes 0-999/12345
  let totalSize = data.length
  const contentRange = response.headers.get("Content-Range")
  if (contentRange) {
    const match = contentRange.match(/\/(\d+)/)
    if (match) totalSize = parseInt(match[1], 10)
  }

  return { data, totalSize }
}

/**
 * Extract plugin manifest from a remote JAR using range requests.
 * Only downloads ~128 KB instead of the full JAR.
 *
 * 1. Fetch the last 65 KB to find the End of Central Directory.
 * 2. Locate plugin.json in the central directory.
 * 3. Fetch just the local file header + data for that entry.
 */
export async function extractManifestFromRemoteJar(
  downloadUrl: string
): Promise<{ manifest: PluginManifest; totalSize: number }> {
  // Step 1: fetch the tail of the JAR to find EOCD
  const tailSize = 65_536
  // We don't know the file size yet, so request the last tailSize bytes
  const tailResp = await fetch(downloadUrl, {
    headers: {
      "User-Agent": "BOSS-Plugin-Store/1.0",
      Range: `bytes=-${tailSize}`,
    },
  })

  if (!tailResp.ok && tailResp.status !== 206) {
    throw new Error(`Range request for EOCD failed: ${tailResp.status}`)
  }

  const tailData = new Uint8Array(await tailResp.arrayBuffer())
  let totalSize = tailData.length
  const contentRange = tailResp.headers.get("Content-Range")
  if (contentRange) {
    const match = contentRange.match(/\/(\d+)/)
    if (match) totalSize = parseInt(match[1], 10)
  }

  // The tail starts at this absolute offset in the file
  const tailOffset = totalSize - tailData.length

  // Find EOCD in the tail
  const tailView = new DataView(tailData.buffer)
  let eocdPos = -1
  for (let i = tailData.length - 22; i >= 0; i--) {
    if (tailView.getUint32(i, true) === 0x06054b50) {
      eocdPos = i
      break
    }
  }

  if (eocdPos === -1) {
    throw new Error("Cannot find EOCD in JAR (range request)")
  }

  const cdSize = tailView.getUint32(eocdPos + 12, true)
  const cdOffset = tailView.getUint32(eocdPos + 16, true) // absolute offset in file

  // Step 2: fetch the central directory (if not already in tail)
  let cdData: Uint8Array
  let cdBaseOffset: number

  if (cdOffset >= tailOffset) {
    // Central directory is within the tail we already fetched
    const relStart = cdOffset - tailOffset
    cdData = tailData.slice(relStart, relStart + cdSize)
    cdBaseOffset = 0
  } else {
    // Need a separate range request for the central directory
    const { data } = await downloadRange(downloadUrl, cdOffset, cdOffset + cdSize - 1)
    cdData = data
    cdBaseOffset = 0
  }

  // Walk the central directory looking for plugin.json
  const cdView = new DataView(cdData.buffer, cdData.byteOffset, cdData.byteLength)
  const manifestPath = "META-INF/boss-plugin/plugin.json"
  let offset = cdBaseOffset

  while (offset < cdData.length - 46) {
    const sig = cdView.getUint32(offset, true)
    if (sig !== 0x02014b50) break

    const compressionMethod = cdView.getUint16(offset + 10, true)
    const compressedSize = cdView.getUint32(offset + 20, true)
    const fileNameLength = cdView.getUint16(offset + 28, true)
    const extraFieldLength = cdView.getUint16(offset + 30, true)
    const commentLength = cdView.getUint16(offset + 32, true)
    const localHeaderOffset = cdView.getUint32(offset + 42, true)

    const fnBytes = cdData.slice(offset + 46, offset + 46 + fileNameLength)
    const fileName = new TextDecoder().decode(fnBytes)
    offset += 46 + fileNameLength + extraFieldLength + commentLength

    if (fileName !== manifestPath) continue

    // Step 3: fetch just this file's local header + data
    // Local header is 30 bytes + filename + extra, then compressed data
    const fetchSize = 30 + fileNameLength + 256 + compressedSize // 256 extra for safety
    const { data: localData } = await downloadRange(
      downloadUrl,
      localHeaderOffset,
      localHeaderOffset + fetchSize - 1
    )
    const localView = new DataView(localData.buffer, localData.byteOffset, localData.byteLength)
    const lhFnLen = localView.getUint16(26, true)
    const lhExtraLen = localView.getUint16(28, true)
    const dataStart = 30 + lhFnLen + lhExtraLen
    const fileData = localData.slice(dataStart, dataStart + compressedSize)

    let content: string
    if (compressionMethod === 0) {
      content = new TextDecoder().decode(fileData)
    } else if (compressionMethod === 8) {
      const ds = new DecompressionStream("deflate-raw")
      const writer = ds.writable.getWriter()
      writer.write(fileData)
      writer.close()
      const reader = ds.readable.getReader()
      const chunks: Uint8Array[] = []
      let len = 0
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        chunks.push(value)
        len += value.length
      }
      const result = new Uint8Array(len)
      let pos = 0
      for (const c of chunks) { result.set(c, pos); pos += c.length }
      content = new TextDecoder().decode(result)
    } else {
      throw new Error(`Unsupported compression: ${compressionMethod}`)
    }

    const manifest = JSON.parse(content) as PluginManifest
    validateManifest(manifest)
    return { manifest, totalSize }
  }

  throw new Error(
    `Plugin manifest not found at ${manifestPath}. Make sure your plugin JAR contains a valid plugin.json.`
  )
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
 * Extract a file from a ZIP archive using the central directory.
 *
 * Reads the End of Central Directory record at the tail of the ZIP to locate
 * the central directory, then looks up the target file by name. This is
 * reliable for large JARs (e.g., 95 MB fat JARs with 35k+ entries) where a
 * linear scan of local file headers can break on data descriptors or ZIP64
 * extended fields.
 */
async function extractFileFromZip(
  zipData: Uint8Array,
  targetPath: string
): Promise<string | null> {
  const view = new DataView(zipData.buffer)

  // --- Locate End of Central Directory (EOCD) record ---
  // Signature: 0x06054b50.  The EOCD is at most 65535 + 22 bytes from the end.
  const eocdMinSize = 22
  const maxCommentLen = 65535
  const searchStart = Math.max(0, zipData.length - eocdMinSize - maxCommentLen)
  let eocdOffset = -1

  for (let i = zipData.length - eocdMinSize; i >= searchStart; i--) {
    if (view.getUint32(i, true) === 0x06054b50) {
      eocdOffset = i
      break
    }
  }

  if (eocdOffset === -1) {
    // Fallback: try the linear scan for small JARs
    return extractFileFromZipLinear(zipData, targetPath)
  }

  const cdEntries = view.getUint16(eocdOffset + 10, true)
  const cdSize = view.getUint32(eocdOffset + 12, true)
  const cdOffset = view.getUint32(eocdOffset + 16, true)

  // --- Walk the central directory to find our file ---
  let offset = cdOffset
  for (let i = 0; i < cdEntries; i++) {
    if (offset + 46 > zipData.length) break
    const sig = view.getUint32(offset, true)
    if (sig !== 0x02014b50) break // not a central dir entry

    const compressionMethod = view.getUint16(offset + 10, true)
    const compressedSize = view.getUint32(offset + 20, true)
    const fileNameLength = view.getUint16(offset + 28, true)
    const extraFieldLength = view.getUint16(offset + 30, true)
    const commentLength = view.getUint16(offset + 32, true)
    const localHeaderOffset = view.getUint32(offset + 42, true)

    const fileNameBytes = zipData.slice(offset + 46, offset + 46 + fileNameLength)
    const fileName = new TextDecoder().decode(fileNameBytes)

    offset += 46 + fileNameLength + extraFieldLength + commentLength

    if (fileName !== targetPath) continue

    // --- Read from the local file header to get the actual data ---
    const lhOffset = localHeaderOffset
    if (lhOffset + 30 > zipData.length) return null
    const lhFileNameLen = view.getUint16(lhOffset + 26, true)
    const lhExtraLen = view.getUint16(lhOffset + 28, true)
    const dataOffset = lhOffset + 30 + lhFileNameLen + lhExtraLen
    const fileData = zipData.slice(dataOffset, dataOffset + compressedSize)

    if (compressionMethod === 0) {
      return new TextDecoder().decode(fileData)
    } else if (compressionMethod === 8) {
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

  return null
}

/**
 * Legacy linear scan fallback for small JARs without a valid EOCD.
 */
async function extractFileFromZipLinear(
  zipData: Uint8Array,
  targetPath: string
): Promise<string | null> {
  const view = new DataView(zipData.buffer)
  let offset = 0

  while (offset < zipData.length - 4) {
    const signature = view.getUint32(offset, true)
    if (signature !== 0x04034b50) break

    const compressionMethod = view.getUint16(offset + 8, true)
    const compressedSize = view.getUint32(offset + 18, true)
    const fileNameLength = view.getUint16(offset + 26, true)
    const extraFieldLength = view.getUint16(offset + 28, true)
    const fileNameBytes = zipData.slice(offset + 30, offset + 30 + fileNameLength)
    const fileName = new TextDecoder().decode(fileNameBytes)
    const dataOffset = offset + 30 + fileNameLength + extraFieldLength

    if (fileName === targetPath) {
      const fileData = zipData.slice(dataOffset, dataOffset + compressedSize)
      if (compressionMethod === 0) {
        return new TextDecoder().decode(fileData)
      } else if (compressionMethod === 8) {
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
      }
    }

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
 * For small JARs (< 50 MB), downloads the full JAR into memory.
 * For large JARs (>= 50 MB), uses range requests to extract the manifest
 * without downloading the full file, then streams the download for upload.
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

  const LARGE_JAR_THRESHOLD = 50 * 1024 * 1024 // 50 MB

  if (jarAsset.size >= LARGE_JAR_THRESHOLD) {
    // Large JAR: use range requests for manifest, then download full JAR
    console.log(`Large JAR detected (${jarAsset.size} bytes), using range requests for manifest`)

    const { manifest, totalSize } = await extractManifestFromRemoteJar(
      jarAsset.browser_download_url
    )

    // Still need to download the full JAR for storage upload and SHA-256
    const jarData = await downloadJar(jarAsset.browser_download_url)
    const sha256 = await calculateSha256(jarData)

    return {
      manifest,
      jarData,
      jarSize: jarData.byteLength,
      sha256,
      releaseNotes: release.body || "",
      version: manifest.version,
    }
  }

  // Small JAR: download fully and extract manifest from memory
  const jarData = await downloadJar(jarAsset.browser_download_url)
  const manifest = await extractManifestFromJar(jarData)
  const sha256 = await calculateSha256(jarData)

  return {
    manifest,
    jarData,
    jarSize: jarData.byteLength,
    sha256,
    releaseNotes: release.body || "",
    version: manifest.version,
  }
}
