package ai.rever.boss.components.overlays

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import java.awt.Window

/**
 * Diagnosis and repair for the transparency of a heavyweight overlay window.
 *
 * Its own file rather than a section of [rememberOverlayWindowState]'s: this is a temporary
 * instrumentation round aimed at one open question (see [EnsureOverlayWindowTransparent]), and
 * keeping it separable is what makes it cheap to delete or downgrade once that question is
 * answered.
 */
private val logger = BossLogger.forComponent("OverlayTransparency")

/**
 * Re-assert that an overlay window is actually translucent, and REPORT what every layer that
 * decides that is actually set to, because `transparent = true` intermittently does not take on
 * macOS.
 *
 * Observed on a live macOS HARDWARE build: opening the New Tab dialog produced a flat mid-grey
 * over the whole window instead of the app dimmed behind the dialog. The dialog itself drew
 * correctly, which is what identifies the layer at fault — the backdrop, not the content. The same
 * failure was later reported on the URL-bar suggestion list, on toasts and on the Ctrl+Tab
 * switcher, and NOT on every open — so it is per-window and intermittent, not a property of any one
 * caller.
 *
 * The colour is the evidence, and it has since been MEASURED rather than eyeballed: the reported
 * backdrop is exactly #999999 at every point of the window. The dialog scrim is
 * `Color.Black.copy(alpha = 0.4f)`, and 0.6 x 255 = 153 = #99 — so the scrim composited over an
 * opaque pure WHITE surface. Not the app beneath (that is dark, and would show structure), and not
 * AWT's default #ECECEC (which gives #8D). The overlay window's own surface is being cleared to
 * opaque white.
 *
 * ## The check below cannot fire on macOS, which is why its silence proved nothing
 *
 * `window.background` is not the layer at fault. Compose applies `transparent = true` through
 * skiko's `transparentWindowBackgroundHack(renderApi)`, which returns `Color(0,0,0,0)` for every
 * host EXCEPT Windows-with-Direct3D — so on macOS that alpha is always 0 and the branch below is
 * unreachable. The earlier version of this KDoc offered "the absence of the warning is itself the
 * signal that the diagnosis was wrong" as its falsification test; that test has now been met, on a
 * build that contained this code, in the session that produced the #999999 screenshot.
 *
 * The assignment is a no-op even when reached: `java.awt.Window.setBackground` returns early when
 * `oldBg.equals(bgColor)`, so re-assigning the identical `Color(0,0,0,0)` never reaches
 * `peer.setOpaque(...)`. It is kept only because it costs nothing and still covers the
 * Windows-Direct3D shape, where the hack colour is null and an opaque background IS observable
 * here.
 *
 * ## What actually decides, and what this reports
 *
 * From Compose Multiplatform 1.11.1 and skiko 0.144.6:
 *
 *  1. `SkiaLayer` clears every frame with
 *     `clear(if (transparency && redrawer.isTransparentBackgroundSupported) bg else bg or
 *     0xFF000000)`, and `defaultIsTransparentBackgroundSupported` is unconditionally true on macOS.
 *     So the ONLY way to get an opaque clear there is `transparency == false`.
 *  2. `WindowSkiaLayerComponent.setTransparency(false)` sets the layer background to null, and a
 *     null `JComponent` background inherits an opaque colour from the AWT parent chain. That pair —
 *     `transparency = false` plus an inherited white — is the one combination in this code that
 *     produces exactly the measured `clear(0xFFFFFFFF)`.
 *  3. skiko's macOS Metal path passes `layer.transparency` into the native `createMetalDevice(...)`
 *     ONCE, at device creation, and `MetalDevice` exposes no setter. A window whose Skia layer was
 *     non-transparent when its Metal device was built therefore stays opaque for its whole life,
 *     which is why re-asserting after the fact cannot repair it.
 *
 * Two causes remain and they need opposite fixes — the Java-side flag being wrong (fix: set it
 * before realization) versus everything Java-visible being right and the opacity living below it
 * (fix: recreate the window, or stop relying on per-pixel translucency). [overlayWillPaintOpaque]
 * is the single boolean that separates them, so this samples the state twice per window and logs
 * its own verdict rather than leaving it to be correlated against a screenshot's timestamp.
 *
 * [kind] names the overlay ("modal", "popup", "hud", "ghost", "corner") so a line can be tied to
 * what was on screen; all five kinds share this one code path.
 *
 * INFO rather than DEBUG deliberately: the healthy samples are as load-bearing as the broken ones,
 * because the failure is intermittent and only the ratio between them says anything. Drop it once
 * the question above is answered.
 */
@Composable
internal fun EnsureOverlayWindowTransparent(
    window: Window,
    kind: String,
) {
    // SideEffect, not DisposableEffect with an empty onDispose: there is nothing to undo when the
    // window goes away, and an empty onDispose invites a reader to wonder what is missing.
    SideEffect {
        runCatching {
            if (window.background?.alpha != 0) {
                logger.warn(
                    LogCategory.UI,
                    "Overlay window came up opaque - re-asserting transparency",
                    mapOf("background" to window.background.toString()),
                )
                window.background = java.awt.Color(0, 0, 0, 0)
            }
        }.onFailure {
            // Never fatal: an opaque overlay is ugly, an exception here would take the dialog
            // down with it.
            logger.warn(
                LogCategory.UI,
                "Could not re-assert overlay window transparency",
                mapOf("error" to it.toString()),
            )
        }
        // Sampled from inside the same SideEffect, so `displayable`/`showing` report whether this
        // effect runs BEFORE the window is realized. That is not incidental detail: it is exactly
        // what decides whether setting the flag from here could ever beat the Metal device's
        // creation, and nothing currently tells us either way.
        logOverlayTransparency(window, kind, phase = "preShow")
    }
    // The second sample has to come from a window listener rather than another effect. Compose
    // effects are driven by composition, which is finished long before AWT gets round to opening
    // the window, so a reading taken here would describe the same pre-realization state twice. The
    // invokeLater behind it defers one more EDT turn, past the first paint, because the Metal
    // device is created lazily on the first render rather than at windowOpened.
    DisposableEffect(window, kind) {
        val listener =
            object : java.awt.event.WindowAdapter() {
                override fun windowOpened(e: java.awt.event.WindowEvent?) {
                    logOverlayTransparency(window, kind, phase = "opened")
                    javax.swing.SwingUtilities.invokeLater {
                        logOverlayTransparency(window, kind, phase = "postShow")
                    }
                }
            }
        window.addWindowListener(listener)
        onDispose { window.removeWindowListener(listener) }
    }
}

/** One sample of every layer that decides whether an overlay window paints opaque. */
private fun logOverlayTransparency(
    window: Window,
    kind: String,
    phase: String,
) {
    runCatching {
        val layer = findSkiaLayer(window)
        val transparency = layer?.let { skiaLayerTransparency(it) }
        // Read through getBackground(), which INHERITS from the AWT parent when the layer's own
        // background is null - deliberately, because skiko's clear calls the same getter and so
        // resolves the same colour. Reading the private field instead would report "null" for
        // precisely the state under suspicion (Compose sets the layer background to null on the
        // Metal path) and hide the opaque colour that is actually being painted.
        val layerBackground = layer?.background
        logger.info(
            LogCategory.UI,
            "Overlay window transparency sample",
            mapOf(
                "kind" to kind,
                "phase" to phase,
                "displayable" to window.isDisplayable.toString(),
                "showing" to window.isShowing.toString(),
                "layerFound" to (layer != null).toString(),
                "transparency" to transparency.toString(),
                "layerBackground" to describeColor(layerBackground),
                "renderApi" to (layer?.let { skiaLayerRenderApi(it) } ?: "unknown"),
                "windowBackground" to describeColor(window.background),
                "translucencyCapable" to
                    (
                        runCatching {
                            window.graphicsConfiguration
                                ?.isTranslucencyCapable
                                ?.toString()
                        }.getOrNull() ?: "unknown"
                    ),
                "willPaintOpaque" to
                    overlayWillPaintOpaque(transparency, layerBackground?.alpha).toString(),
            ),
        )
    }.onFailure {
        // An overlay must never be taken down by its own instrumentation - the same rule the
        // re-assert above already follows.
        logger.warn(
            LogCategory.UI,
            "Could not sample overlay window transparency",
            mapOf("kind" to kind, "phase" to phase, "error" to it.toString()),
        )
    }
}

/** `Color` with its alpha spelled out, since the alpha is the whole point of every reading here. */
private fun describeColor(color: java.awt.Color?): String =
    color?.let { "#%02x%02x%02x alpha=%d".format(it.red, it.green, it.blue, it.alpha) } ?: "null"

/**
 * Whether the Skia surface will be cleared to an OPAQUE colour, mirroring skiko's own rule.
 *
 * `SkiaLayer` clears with `bg` when `transparency` is on and the redrawer supports a transparent
 * background (unconditionally true on macOS), and with `bg or 0xFF000000` otherwise. So the surface
 * is transparent only when the flag is on AND the background it clears to has zero alpha.
 *
 * **Unknown reads as opaque.** Both inputs are null when reflection could not reach the layer, and
 * a diagnostic that reported "healthy" for a window it failed to inspect would be worse than no
 * diagnostic - it is the one answer that cannot be distinguished from a genuine pass.
 */
internal fun overlayWillPaintOpaque(
    transparency: Boolean?,
    backgroundAlpha: Int?,
): Boolean = transparency != true || backgroundAlpha != 0

/**
 * Whether [name] is skiko's layer class.
 *
 * Split out so the match is pinned by a test: a headless test cannot construct a real `SkiaLayer`
 * (it needs a display and a render device), so the class-name predicate is the only part of
 * [findSkiaLayer] a unit test can reach - and it is the part that silently stops matching if skiko
 * ever moves the class.
 */
internal fun isSkiaLayerClassName(name: String): Boolean = name == "org.jetbrains.skiko.SkiaLayer"

/**
 * The skiko layer inside [root], found by walking the AWT component tree.
 *
 * Matched by class NAME and read reflectively rather than through a typed dependency. `composeApp`
 * declares no skiko dependency and gets it only transitively from Compose, so declaring one would
 * pin a version that has to be kept in step with every Compose bump - for code whose only job is to
 * report a field.
 */
private fun findSkiaLayer(root: java.awt.Component): javax.swing.JComponent? =
    if (isSkiaLayerClassName(root.javaClass.name)) {
        root as? javax.swing.JComponent
    } else {
        (root as? java.awt.Container)
            ?.components
            ?.firstNotNullOfOrNull { findSkiaLayer(it) }
    }

/** `SkiaLayer.transparency`, or null if it could not be read. Never throws. */
private fun skiaLayerTransparency(layer: javax.swing.JComponent): Boolean? =
    runCatching {
        layer.javaClass
            .getMethod("getTransparency")
            .invoke(layer) as? Boolean
    }.getOrNull()

/** `SkiaLayer.renderApi` as a name (METAL, SOFTWARE_FAST, ...), or null. Never throws. */
private fun skiaLayerRenderApi(layer: javax.swing.JComponent): String? =
    runCatching {
        layer.javaClass
            .getMethod("getRenderApi")
            .invoke(layer)
            ?.toString()
    }.getOrNull()
