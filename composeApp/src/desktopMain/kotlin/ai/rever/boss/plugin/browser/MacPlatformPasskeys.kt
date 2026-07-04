package ai.rever.boss.plugin.browser

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Loads `libBossWebAuthn.dylib` (see BossWebAuthn.swift + the `compileWebAuthnDylib`
 * Gradle task) and exposes the macOS platform authenticator to [WebAuthnBridge].
 *
 * Everything degrades to "unavailable" — wrong OS, missing dylib, missing symbols,
 * or an OS `denied` state — so callers fall back to Chromium's own WebAuthn (USB
 * security keys) and nothing regresses when the native path is absent.
 *
 * Blocking calls ([getAssertion]/[makeCredential]) MUST be invoked off the JVM main
 * thread: the native side dispatches the ASAuthorization UI onto the Cocoa main
 * queue and blocks the calling thread on a semaphore until it completes. [WebAuthnBridge]
 * already runs ceremonies on a background executor.
 */
internal object MacPlatformPasskeys {
    private val logger = BossLogger.forComponent("MacPlatformPasskeys")

    private interface NativeLib : Library {
        fun boss_webauthn_available(): Int
        fun boss_webauthn_authorize(): Pointer?
        fun boss_webauthn_get(req: String): Pointer?
        fun boss_webauthn_create(req: String): Pointer?
        fun boss_webauthn_free(p: Pointer?)
    }

    private val isMac: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private val lib: NativeLib? by lazy { if (isMac) loadLib() else null }

    private fun loadLib(): NativeLib? {
        // JNA marshals String args as char* using this encoding; the Swift side reads UTF-8.
        if (System.getProperty("jna.encoding") == null) {
            System.setProperty("jna.encoding", "UTF-8")
        }
        for (candidate in dylibCandidates()) {
            try {
                if (candidate.exists()) {
                    val loaded = Native.load(candidate.absolutePath, NativeLib::class.java)
                    logger.info(LogCategory.BROWSER, "Loaded native passkey library",
                        mapOf("path" to candidate.absolutePath))
                    return loaded
                }
            } catch (e: Throwable) {
                logger.debug(LogCategory.BROWSER, "Native passkey lib candidate failed",
                    mapOf("path" to candidate.absolutePath, "error" to (e.message ?: "")))
            }
        }
        // Last resort: let the loader search jna.library.path / DYLD_LIBRARY_PATH.
        return try {
            Native.load("BossWebAuthn", NativeLib::class.java)
        } catch (e: Throwable) {
            logger.info(LogCategory.BROWSER,
                "Native passkey library unavailable; falling back to Chromium WebAuthn")
            null
        }
    }

    private fun dylibCandidates(): List<File> {
        val name = "libBossWebAuthn.dylib"
        val out = mutableListOf<File>()
        System.getProperty("boss.webauthn.dylib")?.let { out += File(it) }
        // Packaged Compose Desktop app resources.
        System.getProperty("compose.application.resources.dir")?.let { out += File(it, name) }
        // Relative to the running jar/app: Contents/app + Contents/Frameworks.
        runCatching {
            val src = File(MacPlatformPasskeys::class.java.protectionDomain.codeSource.location.toURI())
            var dir: File? = src.parentFile
            repeat(6) {
                dir?.let {
                    out += File(it, name)
                    out += File(it, "Frameworks/$name")
                }
                dir = dir?.parentFile
            }
        }
        // Dev / worktree build output.
        val userDir = System.getProperty("user.dir").orEmpty()
        out += File(userDir, "composeApp/build/webauthn/$name")
        out += File(userDir, "build/webauthn/$name")
        return out
    }

    // Memoized so we don't construct the ObjC manager + read state on every JS-thread
    // request()/install() call. OS version and class presence are constant for the
    // process. The mutable case is the passkey-access permission:
    //  - denied mid-session → ensureAuthorized() flips this to false; since request()
    //    re-reads isAvailable() per ceremony, EVERY tab (including already-open ones)
    //    then declines to Chromium fallback — recovery is immediate, not restart-gated.
    //    (Only the isUVPAA() hint baked into already-open pages stays stale, which at
    //    worst mis-advertises availability; the ceremony itself still falls back.)
    //  - denied → later re-granted in System Settings: we cached false and don't
    //    re-probe, so that direction needs a restart. Acceptable (rare).
    @Volatile private var availabilityCache: Boolean? = null

    /**
     * Whether the native platform authenticator can be used. The native probe requires
     * all of: macOS 13.5+, the managed entitlement actually provisioned (an
     * `embedded.provisionprofile` granting it — see BossWebAuthn.swift), and the OS not
     * denying access; plus the dylib loaded here. An unprovisioned build (current
     * releases, dev runs) returns false → shim reports unavailable → Chromium fallback.
     */
    fun isAvailable(): Boolean {
        availabilityCache?.let { return it }
        val result = try {
            (lib?.boss_webauthn_available() ?: 0) == 1
        } catch (e: Throwable) {
            logger.debug(LogCategory.BROWSER, "boss_webauthn_available threw", mapOf("error" to (e.message ?: "")))
            false
        }
        availabilityCache = result
        return result
    }

    /**
     * Ensures the app has (or obtains) permission to use the person's passkeys.
     * Returns true when authorized. Blocking; call off the main thread.
     *
     * On an explicit OS denial we invalidate [availabilityCache] so that [isAvailable]
     * flips to false — otherwise a cached `true` (from the initial `notDetermined`
     * probe) would keep steering ceremonies onto the native path forever, and since a
     * denial can't be recovered in-process the user would lose the Chromium/USB
     * fallback for the rest of the session. `notDetermined` (e.g. consent timeout) is
     * transient and left uncached so a later attempt can re-prompt.
     */
    fun ensureAuthorized(): Boolean {
        val l = lib ?: return false
        val json = call { l.boss_webauthn_authorize() } ?: return false
        val state = try {
            Json.parseToJsonElement(json).jsonObject["state"]?.jsonPrimitive?.contentOrNull
        } catch (e: Throwable) {
            null
        }
        if (state == "denied") availabilityCache = false
        return state == "authorized"
    }

    /** Runs an assertion ceremony; returns the native JSON result, or null on hard failure. */
    fun getAssertion(requestJson: String): String? {
        val l = lib ?: return null
        return call { l.boss_webauthn_get(requestJson) }
    }

    /** Runs a registration ceremony; returns the native JSON result, or null on hard failure. */
    fun makeCredential(requestJson: String): String? {
        val l = lib ?: return null
        return call { l.boss_webauthn_create(requestJson) }
    }

    /** Reads a returned char*, copies it to a Kotlin String, and frees the native buffer. */
    private inline fun call(block: () -> Pointer?): String? {
        val ptr = try {
            block()
        } catch (e: Throwable) {
            logger.warn(LogCategory.BROWSER, "Native passkey call failed", error = e)
            return null
        } ?: return null
        return try {
            ptr.getString(0, "UTF-8")
        } finally {
            try { lib?.boss_webauthn_free(ptr) } catch (_: Throwable) {}
        }
    }
}
