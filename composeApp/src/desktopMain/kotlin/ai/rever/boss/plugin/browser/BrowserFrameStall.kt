package ai.rever.boss.plugin.browser

import com.teamdev.jxbrowser.engine.RenderingMode

/**
 * Recovery for a committed document whose view never starts producing frames.
 *
 * **The symptom, measured rather than inferred.** Clicking Google's "AI Mode" tab from a results
 * page lands on `…&udm=50&aep=1`, and the pane goes blank and stays blank. The document itself is
 * completely healthy: the DOM is complete, layout is correct, `setTimeout` and `setInterval` keep
 * firing, microtasks resolve, and JS evaluates normally. What never happens is a frame -
 * `requestAnimationFrame` does not fire once, and painting a fresh background colour into `body`
 * changes nothing on screen. Measured on the click path, 0 of 5 navigations painted (raf=0 after
 * 3s) against 5 of 5 for the same queries without the AI Mode click (raf around 420).
 *
 * **What it is not.** Reproduced with the GPU fully out of the picture - a build reporting
 * `Canvas/Compositing/Rasterization: Software only` and `Skia Graphite: Disabled` blanks
 * identically - so it is not raster, not Graphite and not the hardware path. It is not CSS either:
 * forcing every element opaque leaves the pane blank, and elements at effective opacity 1 (the
 * "AI Mode / All / Images" nav row) are just as unpainted as the animated ones. Response headers
 * are byte-identical to the variant that works, so no process swap is involved, and BOSS logs
 * nothing at all across the navigation.
 *
 * **Why re-attaching is the fix.** Switching to another tab and back restores frame production
 * immediately and permanently (raf 0 before, 850 after). Nothing about the document changes across
 * that switch - the surface is even retained under HARDWARE_ACCELERATED. What changes is that the
 * view leaves and re-enters composition, so the native view is attached again. That makes this a
 * presentation-side stall, and re-attaching is the same repair the user already performs by hand.
 *
 * The underlying fault is inside the JxBrowser/Chromium widget rather than in BOSS, so this is
 * deliberately a recovery and not a cure: it detects a document that committed but never drew, and
 * performs the tab-switch repair on the user's behalf.
 */
internal object BrowserFrameStall {
    /**
     * Arms a one-shot beacon and reports whether a frame has been served yet.
     *
     * Returns `"1"` once rAF has run, `"0"` while it has not. Re-running it is what reads the
     * result, so the same snippet both arms and polls - there is no second script to keep in step
     * with this one.
     *
     * `__bossFrameBeacon` is deliberately re-armed per call site rather than per document: a
     * document that is replaced under us (a redirect committing between arm and read) would
     * otherwise be read through a beacon that belonged to the previous page and look stalled.
     */
    const val BEACON_SCRIPT: String =
        """
        (function () {
          if (typeof window.__bossFrameBeacon === 'undefined') {
            window.__bossFrameBeacon = '0';
            requestAnimationFrame(function () { window.__bossFrameBeacon = '1'; });
          }
          return window.__bossFrameBeacon;
        })()
        """

    /** The beacon's answer for "a frame has been served". */
    const val BEACON_PAINTED: String = "1"

    /**
     * Whether a committed navigation is worth watching.
     *
     * Gated to HARDWARE_ACCELERATED because that is the mode where the browser is a real native
     * view whose attachment can go stale; under OFF_SCREEN the frames are exported as a bitmap and
     * this failure has never been observed. Gated to http(s) so `about:blank`, `chrome://` and the
     * dashboard's empty states - none of which a user would call blank - never arm a probe or
     * trigger a re-attach.
     */
    fun shouldWatch(
        url: String?,
        mode: RenderingMode,
    ): Boolean {
        if (mode != RenderingMode.HARDWARE_ACCELERATED) return false
        val trimmed = url?.trim().orEmpty()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    /**
     * Whether the beacon's reading means "stalled".
     *
     * A null reading is NOT a stall. It is what a failed or raced JS round-trip returns - a
     * navigation that committed again mid-probe, a frame that went away, a renderer busy enough to
     * miss the call - and treating those as stalls would re-attach the view on ordinary pages,
     * which costs a visible flicker for nothing. Only an explicit "not painted yet" counts.
     */
    fun isStalled(beaconReading: String?): Boolean = beaconReading != null && beaconReading != BEACON_PAINTED

    /** How long to give a healthy page before the first reading. */
    const val FIRST_CHECK_MS: Long = 700L

    /**
     * How long to wait again before believing the first reading.
     *
     * Two readings, not one. A slow page can genuinely have served no frame at 700ms, and a
     * single reading would re-attach its view mid-load for no reason. Both must say "no frame"
     * before anything is done.
     */
    const val CONFIRM_MS: Long = 900L
}
