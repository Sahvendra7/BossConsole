package ai.rever.boss.plugin.browser

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.frame.Frame
import com.teamdev.jxbrowser.js.JsAccessible
import com.teamdev.jxbrowser.js.JsObject
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host end of the WebAuthn page bridge. Injected as `window.__bossWebAuthn` on the
 * TOP-LEVEL frame only (see [install]); [WebAuthnScripts] wraps
 * `navigator.credentials.*` and calls [request] for each ceremony it wants routed to
 * the native macOS platform authenticator ([MacPlatformPasskeys]).
 *
 * SECURITY — origin binding. The `com.apple.developer.web-browser.public-key-credential`
 * entitlement makes the OS trust the browser to enforce WebAuthn's origin binding.
 * `request()` is `@JsAccessible`, so a hostile page can call it directly with a forged
 * `origin`/`rpId`. Therefore the page-supplied `origin` and `rpId` are NOT trusted:
 * we overwrite `origin` with the engine's real top-frame URL ([Browser.url]) and
 * reject unless the page's `rpId` is a registrable-domain suffix of that origin's
 * host. Anything that fails validation is declined so the page falls back to
 * Chromium's own WebAuthn (which independently enforces the same rules).
 *
 * Cross-origin iframes: the bridge is installed only on the main frame, so subframe
 * WebAuthn keeps Chromium's default Permissions-Policy gating (publickey-credentials-*
 * off by default) instead of being silently routed to the platform authenticator.
 *
 * Flow: the page calls [request] on a JxBrowser JS thread; we validate + accept
 * (return `true`) and run the blocking native ceremony on [executor] (serialized to a
 * single in-flight ceremony), then settle the page-side promise via
 * `window.__bossWebAuthnSettle(id, ok, payload)` on [frame]. Returning `false`
 * declines the ceremony so the page falls back to Chromium's WebAuthn.
 *
 * Known limitation: the credential the shim reconstructs is a plain JS object, not a
 * real `PublicKeyCredential`, so relying parties that do `instanceof PublicKeyCredential`
 * / `instanceof AuthenticatorAssertionResponse` will not match (see [WebAuthnScripts]).
 */
internal class WebAuthnBridge(private val frame: Frame) {
    @JsAccessible
    fun request(id: String, op: String, requestJson: String): Boolean {
        if (!MacPlatformPasskeys.isAvailable()) return false
        // Derive the trusted request from the engine's real top-frame URL — never
        // from the page-supplied origin/rpId. Declining lets the page fall back.
        val trusted = sanitizeRequest(requestJson) ?: return false
        return try {
            executor.execute { runCeremony(id, op, trusted) }
            true
        } catch (e: Throwable) {
            // e.g. RejectedExecutionException — nothing was queued and no permit was
            // taken (the gate lives inside runCeremony), so decline → page falls back.
            logger.warn(LogCategory.BROWSER, "WebAuthn ceremony could not be scheduled", error = e)
            false
        }
    }

    /**
     * The engine's real top-frame URL is the trusted origin source; the page-supplied
     * origin/rpId are validated + rewritten by [WebAuthnOrigin.buildTrustedRequest].
     * Returns null (→ decline → page falls back) on a dead frame, insecure context, or
     * an rpId not bound to the real origin (a phishing attempt).
     */
    private fun sanitizeRequest(requestJson: String): String? {
        val browserUrl = try {
            frame.browser().url()
        } catch (e: Throwable) {
            return null
        }
        return WebAuthnOrigin.buildTrustedRequest(browserUrl, requestJson)
    }

    private fun runCeremony(id: String, op: String, requestJson: String) {
        // Single-flight across all tabs — the system auth sheet is app-modal. On
        // contention settle a distinct retriable error rather than declining: declining
        // would fall back to Chromium, which has no platform authenticator and would hang
        // a platform-passkey site (the exact bug this feature fixes). The page rejects
        // with NotAllowedError and the RP can prompt a retry.
        if (!gate.tryAcquire()) {
            settle(id, false, """{"error":"NotAllowedError","message":"A passkey request is already in progress. Please try again."}""")
            return
        }
        try {
            if (!MacPlatformPasskeys.ensureAuthorized()) {
                // App-level passkey access isn't granted (denied or undecided). This is
                // categorically different from a per-ceremony user cancel: we simply
                // can't use platform passkeys, so fall back to Chromium's WebAuthn (e.g.
                // a USB security key) instead of a dead NotAllowedError. A NotAllowedError
                // here would also permanently block USB fallback for the session once the
                // availability probe has cached true (isAvailable now re-flips on denial).
                settle(id, false, """{"error":"__BOSS_FALLBACK__"}""")
                return
            }
            val result = when (op) {
                "get" -> MacPlatformPasskeys.getAssertion(requestJson)
                "create" -> MacPlatformPasskeys.makeCredential(requestJson)
                else -> null
            }
            if (result == null) {
                // Native infrastructure failure (JNA/lib), NOT a user decision — route the
                // page back to Chromium's WebAuthn (e.g. a USB security key) instead of a
                // dead error. A user cancel/decline arrives as a structured ok:false result
                // below and is surfaced as-is (no fallback), per WebAuthn semantics.
                settle(id, false, """{"error":"__BOSS_FALLBACK__"}""")
                return
            }
            settle(id, resultIsOk(result), result)
        } catch (e: Throwable) {
            logger.warn(LogCategory.BROWSER, "WebAuthn ceremony failed", error = e)
            settle(id, false, """{"error":"__BOSS_FALLBACK__"}""")
        } finally {
            gate.release()
        }
    }

    /** Resolve/reject the page-side promise. Runs off the JS thread; frame may be gone. */
    private fun settle(id: String, ok: Boolean, payloadJson: String) {
        val script = "window.__bossWebAuthnSettle && window.__bossWebAuthnSettle(" +
            "${jsStr(id)}, $ok, ${jsStr(payloadJson)});"
        try {
            frame.executeJavaScript<Any?>(script)
        } catch (e: Throwable) {
            logger.debug(LogCategory.BROWSER, "WebAuthn settle failed (frame gone?)",
                mapOf("error" to (e.message ?: "")))
        }
    }

    companion object {
        private val logger = BossLogger.forComponent("WebAuthnBridge")
        private val threadCounter = AtomicInteger()
        // Bounded to match the single-flight invariant: at most one ceremony holds the
        // gate, so one thread runs it while a second promptly rejects any concurrent
        // request (tryAcquire fails → instant settle). A cached pool would spawn a fresh
        // thread per request() — pointless when all but one immediately bounce.
        private val executor = Executors.newFixedThreadPool(2) { r ->
            Thread(r, "boss-webauthn-${threadCounter.incrementAndGet()}").apply { isDaemon = true }
        }
        // App-modal system UI ⇒ at most one native ceremony at a time, across all tabs.
        private val gate = Semaphore(1)

        private fun jsStr(s: String): String = Json.encodeToString(String.serializer(), s)

        private fun resultIsOk(resultJson: String): Boolean = try {
            Json.parseToJsonElement(resultJson).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        } catch (e: Throwable) {
            false
        }

        /**
         * Installs `window.__bossWebAuthn` + the [WebAuthnScripts] shim on the TOP-LEVEL
         * frame of [browser] at document-start. Subframes are left to Chromium's default
         * WebAuthn (Permissions-Policy gated). No-op effect on non-macOS builds (shim
         * reports the platform authenticator unavailable).
         *
         * Registers through [BrowserInjectDispatcher] rather than `browser.set(InjectJsCallback…)`
         * directly, because JxBrowser allows only one InjectJsCallback per browser: any
         * other document-start injector (e.g. co-browse on `feat/cobrowse-tab-sharing`,
         * which today still calls `browser.set` directly) would otherwise clobber this
         * one and silently disable passkeys. That branch must adopt the dispatcher too.
         */
        fun install(browser: Browser) {
            val available = MacPlatformPasskeys.isAvailable()
            BrowserInjectDispatcher.register(browser) { frame ->
                if (frame.isMain()) {
                    val window = frame.executeJavaScript<JsObject>("window")
                    window?.putProperty("__bossWebAuthn", WebAuthnBridge(frame))
                    frame.executeJavaScript<Any?>(WebAuthnScripts.shim(available))
                }
            }
        }
    }
}
