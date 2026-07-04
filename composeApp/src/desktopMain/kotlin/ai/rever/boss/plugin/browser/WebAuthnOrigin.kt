package ai.rever.boss.plugin.browser

import com.google.common.net.InternetDomainName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.IDN
import java.net.URI

/**
 * WebAuthn origin-binding policy — the single security gate that the
 * `com.apple.developer.web-browser.public-key-credential` entitlement delegates to
 * the browser. On the native path Apple signs exactly the origin/rpId we accept here;
 * there is NO downstream Chromium check. Kept as a separate `internal` object of pure,
 * deterministic functions so it can be unit-tested without any native/JxBrowser code
 * (see WebAuthnOriginPolicyTest).
 */
internal object WebAuthnOrigin {

    /**
     * Parse a browser URL into (origin, host) as an ASCII-normalized WebAuthn-eligible
     * secure context, or null if it isn't one (declining → Chromium fallback).
     *
     * - Host is punycode/ASCII-normalized ([IDN.toASCII]) so the origin baked into
     *   clientDataJSON matches what relying parties expect for IDN sites. In practice
     *   Chromium's `browser.url()` already emits punycode (and [URI.getHost] returns
     *   null for a raw-Unicode host anyway), so this is a defensive no-op on the real
     *   path.
     * - IPv6 literals arrive from [URI.getHost] bracketed (`[::1]`); brackets are
     *   stripped for the returned host and re-added when rebuilding the origin.
     * - Secure context = https, or http on loopback (localhost / 127.0.0.1 / ::1).
     */
    fun originAndHost(url: String): Pair<String, String>? {
        val uri = try {
            URI(url)
        } catch (e: Throwable) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val rawHost = uri.host?.lowercase() ?: return null
        // Strip IPv6 brackets that URI.getHost includes (per RFC 2732).
        val stripped = rawHost.removePrefix("[").removeSuffix("]")
        if (stripped.isEmpty()) return null
        val host = try {
            // No-op for already-ASCII hosts; converts Unicode IDNs to punycode.
            IDN.toASCII(stripped).lowercase()
        } catch (e: Throwable) {
            stripped
        }

        val loopback = host == "localhost" || host == "127.0.0.1" || host == "::1"
        val secure = scheme == "https" || (scheme == "http" && loopback)
        if (!secure) return null

        val port = uri.port
        val defaultPort = (scheme == "https" && port == 443) || (scheme == "http" && port == 80)
        val hostForOrigin = if (host.contains(':')) "[$host]" else host  // re-bracket IPv6
        val origin = if (port == -1 || defaultPort) "$scheme://$hostForOrigin" else "$scheme://$hostForOrigin:$port"
        return origin to host
    }

    /**
     * WebAuthn rpId rule: the rpId must equal the origin's host, or be a registrable
     * parent domain of it. The registrable-domain constraint is enforced with the
     * Public Suffix List (Guava's [InternetDomainName]), so an rpId that is itself a
     * public suffix — `com`, `co.uk`, `github.io`, `web.app`, … — is rejected on the
     * suffix path. Without this, a page at `evil.github.io` could request
     * `rpId=github.io` and mint/assert a passkey scoped to every `*.github.io` site.
     *
     * Exact host matches are always allowed (covers `localhost`, IP literals, and the
     * same-origin case) — a subdomain attacker cannot serve content at the apex host,
     * so the wildcard-scoping concern only exists on the suffix path.
     */
    fun isRegistrableSuffix(rpId: String, host: String): Boolean {
        if (rpId.isEmpty() || host.isEmpty()) return false
        if (rpId == host) return true
        if (!host.endsWith(".$rpId")) return false
        return try {
            // True iff rpId has a registrable label beyond its public suffix — i.e. it
            // is eTLD+1 or lower, not a bare TLD/public suffix.
            InternetDomainName.from(rpId).isUnderPublicSuffix
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Produce the trusted request JSON to hand to the native authenticator, or null to
     * decline (→ Chromium fallback). This is the second trust boundary after
     * [originAndHost]/[isRegistrableSuffix]: the page-supplied `origin` is discarded and
     * replaced with the engine's real [browserUrl] origin, `rpId` defaults to the real
     * host when absent, and a page rpId that isn't a registrable suffix of the real host
     * is rejected. Everything else in the request (challenge, allow/exclude credentials,
     * user, timeout, …) is passed through unchanged. Pure/deterministic for testing.
     */
    fun buildTrustedRequest(browserUrl: String, requestJson: String): String? {
        val (origin, host) = originAndHost(browserUrl) ?: return null
        val obj = try {
            Json.parseToJsonElement(requestJson).jsonObject
        } catch (e: Throwable) {
            return null
        }
        val rpId = obj["rpId"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: host
        if (!isRegistrableSuffix(rpId, host)) return null
        val rewritten = obj.toMutableMap().apply {
            put("origin", JsonPrimitive(origin))
            put("rpId", JsonPrimitive(rpId))
        }
        return Json.encodeToString(JsonObject.serializer(), JsonObject(rewritten))
    }
}
