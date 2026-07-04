package ai.rever.boss.plugin.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Table-driven tests for the WebAuthn origin-binding gate. These functions are the
 * sole enforcement point on the native passkey path (Apple signs exactly what they
 * accept — no downstream Chromium check), so the phishing / cross-site-scoping cases
 * below are security regressions if they ever flip.
 */
class WebAuthnOriginPolicyTest {

    @Test
    fun `rpId accepted only when equal to or a registrable parent of the host`() {
        // rpId, host, expected
        val accept = listOf(
            "example.com" to "example.com",              // exact
            "example.com" to "login.example.com",        // registrable parent
            "example.com" to "a.b.example.com",          // deep subdomain
            "example.co.uk" to "www.example.co.uk",      // registrable under multi-label suffix
            "localhost" to "localhost",                  // dev exact match
        )
        for ((rpId, host) in accept) {
            assertEquals(true, WebAuthnOrigin.isRegistrableSuffix(rpId, host), "expected accept: rpId=$rpId host=$host")
        }
    }

    @Test
    fun `rpId rejected for public suffixes and suffix-confusion`() {
        val reject = listOf(
            "com" to "example.com",                      // bare TLD
            "co.uk" to "example.co.uk",                  // ccTLD public suffix
            "github.io" to "evil.github.io",             // gTLD-style private public suffix (the reported hole)
            "web.app" to "evil.web.app",                 // Firebase hosting suffix
            "pages.dev" to "evil.pages.dev",             // Cloudflare Pages suffix
            "example.com" to "notexample.com",           // suffix-confusion: not a dotted boundary
            "example.com" to "evilexample.com",          // suffix-confusion
            "" to "example.com",                         // empty rpId
            "example.com" to "",                         // empty host
        )
        for ((rpId, host) in reject) {
            assertEquals(false, WebAuthnOrigin.isRegistrableSuffix(rpId, host), "expected reject: rpId=$rpId host=$host")
        }
    }

    @Test
    fun `originAndHost accepts secure contexts and normalizes`() {
        assertEquals("https://example.com" to "example.com", WebAuthnOrigin.originAndHost("https://example.com/path?q=1"))
        assertEquals("https://example.com:8443" to "example.com", WebAuthnOrigin.originAndHost("https://example.com:8443/"))
        assertEquals("https://example.com" to "example.com", WebAuthnOrigin.originAndHost("https://example.com:443/"))
        // http allowed only on loopback
        assertEquals("http://localhost:3000" to "localhost", WebAuthnOrigin.originAndHost("http://localhost:3000/"))
        assertNotNull(WebAuthnOrigin.originAndHost("http://127.0.0.1/"))
        // Already-punycode host (the form Chromium's browser.url() actually emits) is
        // preserved as the ASCII origin.
        assertEquals("https://xn--bcher-kva.example" to "xn--bcher-kva.example",
            WebAuthnOrigin.originAndHost("https://xn--bcher-kva.example/"))
    }

    @Test
    fun `originAndHost rejects insecure and malformed contexts`() {
        assertNull(WebAuthnOrigin.originAndHost("http://example.com/"))   // http on non-loopback
        assertNull(WebAuthnOrigin.originAndHost("ftp://example.com/"))    // wrong scheme
        assertNull(WebAuthnOrigin.originAndHost("about:blank"))
        assertNull(WebAuthnOrigin.originAndHost("not a url"))
        assertNull(WebAuthnOrigin.originAndHost(""))
    }

    @Test
    fun `buildTrustedRequest rewrites origin and defaults rpId to the real host`() {
        // No rpId supplied → defaults to the real host; page origin is overwritten.
        val out = WebAuthnOrigin.buildTrustedRequest(
            "https://login.example.com/signin",
            """{"challenge":"abc","origin":"https://evil.test","allowCredentials":["x"]}"""
        )
        assertNotNull(out)
        val obj = Json.parseToJsonElement(out).jsonObject
        assertEquals("https://login.example.com", obj["origin"]!!.jsonPrimitive.contentOrNull)
        assertEquals("login.example.com", obj["rpId"]!!.jsonPrimitive.contentOrNull)
        // Passthrough fields survive.
        assertEquals("abc", obj["challenge"]!!.jsonPrimitive.contentOrNull)
        assertTrue(obj.containsKey("allowCredentials"))
    }

    @Test
    fun `buildTrustedRequest accepts a registrable parent rpId and overwrites origin`() {
        val out = WebAuthnOrigin.buildTrustedRequest(
            "https://login.example.com/",
            """{"challenge":"c","rpId":"example.com","origin":"https://spoof"}"""
        )
        assertNotNull(out)
        val obj = Json.parseToJsonElement(out).jsonObject
        assertEquals("example.com", obj["rpId"]!!.jsonPrimitive.contentOrNull)
        assertEquals("https://login.example.com", obj["origin"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun `buildTrustedRequest rejects phishing rpId, public-suffix rpId, and insecure origin`() {
        // Cross-registrable-domain phishing.
        assertNull(WebAuthnOrigin.buildTrustedRequest(
            "https://evil.example.com/",
            """{"challenge":"c","rpId":"google.com"}"""))
        // Public-suffix rpId (the github.io class of hole).
        assertNull(WebAuthnOrigin.buildTrustedRequest(
            "https://evil.github.io/",
            """{"challenge":"c","rpId":"github.io"}"""))
        // Insecure top-frame context declines regardless of rpId.
        assertNull(WebAuthnOrigin.buildTrustedRequest(
            "http://example.com/",
            """{"challenge":"c","rpId":"example.com"}"""))
        // Unparseable request JSON.
        assertNull(WebAuthnOrigin.buildTrustedRequest("https://example.com/", "not json"))
    }
}
