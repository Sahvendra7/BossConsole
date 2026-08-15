package ai.rever.boss.components.auth.forms

import androidx.compose.runtime.Composable

/**
 * The bundled page the brand panel shows, as a Compose resource path.
 *
 * **Local, not `https://bossconsole.ai`.** Loading the live site put a network fetch on the sign-in
 * path - the one screen a user cannot get past - so a blank pane was likeliest exactly when the
 * network was bad and someone was trying to sign in. It also meant a live, fully interactive page with
 * no browser chrome: one stray click navigated the panel away with no way back until relaunch. The
 * bundled copy has neither problem and cannot change under the app.
 *
 * It is three sections of the real site, using the site's own markup classes, copy and stylesheet -
 * see the comment at the top of `index.html` for exactly what was trimmed and the one deliberate
 * difference (no webfont fetch, so mono falls back to the platform's).
 */
internal const val AUTH_BRAND_PAGE = "files/auth-brand/index.html"

/** The stylesheet [AUTH_BRAND_PAGE] links, which has to be unpacked beside it to resolve. */
internal const val AUTH_BRAND_STYLESHEET = "files/auth-brand/site.css"

/**
 * Whether the brand panel should show [AUTH_BRAND_PAGE] over the drawn art.
 *
 * Still opt-in even now the page is local, because the remaining cost is not the network: rendering it
 * starts a Chromium engine on the sign-in path, and `AuthBrandArt` is a complete panel without one.
 *
 * The art stays underneath either way, so a failure to render, a missing engine or a disabled flag all
 * land on the same drawn panel rather than on nothing.
 */
@Composable
internal expect fun authBrandSiteEnabled(): Boolean

/**
 * Renders the bundled brand page, reporting when it is ready to be shown.
 *
 * [onReady] fires when the page has finished loading AND been positioned on its first section, NOT
 * when the view is created: the art has to stay up until there is something worth showing, or the
 * panel flashes empty for as long as Chromium takes to start and paint. [onFailed] covers an engine
 * that will not initialise and a page that cannot be unpacked, and both mean "keep the art".
 */
@Composable
internal expect fun AuthBrandSite(
    onReady: () -> Unit,
    onFailed: () -> Unit,
)
