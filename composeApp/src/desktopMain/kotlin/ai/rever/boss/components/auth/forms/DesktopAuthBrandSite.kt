package ai.rever.boss.components.auth.forms

import ai.rever.boss.plugin.browser.FluckEngine
import ai.rever.boss.plugin.browser.LocalAwtWindow
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import boss_kotlin.composeapp.generated.resources.Res
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Frame
import java.awt.Window
import java.nio.file.Files
import kotlin.io.path.writeBytes

private val logger = BossLogger.forComponent("AuthBrandSite")

/** Environment variable that opts a deployment in. */
private const val BRAND_SITE_KEY = "BOSS_AUTH_BRAND_SITE"

/** System property equivalent, so `-Dboss.auth.brand.site=true` works as well as the env var. */
private const val BRAND_SITE_PROPERTY = "boss.auth.brand.site"

/**
 * Read once per composition and never cached across launches, matching the other flags of this shape:
 * `1`, `yes` and `on` count as well as `true` (see [FluckEngine.isTruthyFlag]), and a blank env var
 * does not shadow the property.
 */
@Composable
internal actual fun authBrandSiteEnabled(): Boolean =
    remember {
        val env = System.getenv(BRAND_SITE_KEY)?.takeIf { it.isNotBlank() }
        FluckEngine.isTruthyFlag(env ?: System.getProperty(BRAND_SITE_PROPERTY))
    }

/**
 * Unpacks the bundled page and its stylesheet to a temp directory and returns a `file:` URL.
 *
 * A file URL rather than `data:`, because the page links its stylesheet relatively - a data URL has no
 * base to resolve that against, and the sections would render unstyled. The two files are written
 * together for the same reason.
 *
 * `deleteOnExit` rather than an explicit cleanup: the page outlives the browser that loads it (Chromium
 * reads lazily), and the directory is a few tens of kilobytes in the system temp dir.
 */
private suspend fun unpackBrandPage(): String {
    val dir = withContext(Dispatchers.IO) { Files.createTempDirectory("boss-auth-brand") }
    dir.toFile().deleteOnExit()
    val page = dir.resolve("index.html")
    val stylesheet = dir.resolve("site.css")
    withContext(Dispatchers.IO) {
        page.writeBytes(Res.readBytes(AUTH_BRAND_PAGE))
        stylesheet.writeBytes(Res.readBytes(AUTH_BRAND_STYLESHEET))
    }
    page.toFile().deleteOnExit()
    stylesheet.toFile().deleteOnExit()
    return page.toUri().toString()
}

// Broad catches on purpose, both of them: this panel is decoration on the one screen a user cannot
// get past, so ANY way the engine can fail - no licence, headless, a Chromium that did not unpack, a
// browser closed under us - has to end at the drawn art rather than at a crashed sign-in screen.
// Narrowing to JxBrowser's own exception types would let exactly the unforeseen failure through.
@Suppress("TooGenericExceptionCaught")
@Composable
internal actual fun AuthBrandSite(
    onReady: () -> Unit,
    onFailed: () -> Unit,
) {
    var browser by remember { mutableStateOf<Browser?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // LaunchedEffect, not DisposableEffect: unpacking the page reads a Compose resource, which
    // suspends. Cleanup is a separate effect below, keyed on the browser it has to close.
    LaunchedEffect(Unit) {
        try {
            val page = unpackBrandPage()
            // Throws when the engine cannot start - no licence, headless CI, a Chromium that failed
            // to unpack. All of those mean "stay on the art".
            val created = FluckEngine.engine.newBrowser()
            created.navigation().on(LoadFinished::class.java) {
                scope.launch(Dispatchers.Main) { loaded = true }
            }
            created.navigation().loadUrl(page)
            browser = created
        } catch (e: Exception) {
            logger.warn(
                LogCategory.BROWSER,
                "Brand page unavailable; keeping the drawn panel",
                error = e,
            )
            onFailed()
        }
    }

    val current = browser

    DisposableEffect(current) {
        onDispose {
            // The login screen is transient - it goes away on the first successful sign-in - so this
            // browser must not outlive it. A leaked Chromium instance per sign-in attempt would be
            // the worst kind of cost for a decorative panel.
            try {
                current?.close()
            } catch (e: Exception) {
                logger.warn(LogCategory.BROWSER, "Error closing brand page browser", error = e)
            }
        }
    }
    // Reveal only once the page has actually painted; the art stays up until then.
    LaunchedEffect(current, loaded) {
        if (current != null && loaded) onReady()
    }

    if (current == null) return
    val localWindow = LocalAwtWindow.current
    val window =
        remember(localWindow) {
            localWindow ?: Window.getWindows().firstOrNull() ?: Frame()
        }
    val state = remember(current, window) { BrowserViewState(current, MainScope(), window) }
    BrowserView(state = state, modifier = Modifier.fillMaxSize())
}
