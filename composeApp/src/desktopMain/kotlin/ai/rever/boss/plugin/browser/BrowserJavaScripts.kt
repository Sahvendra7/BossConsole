package ai.rever.boss.plugin.browser

/**
 * Centralized repository of JavaScript code snippets used in JxBrowser.
 *
 * This object contains all JavaScript code executed in the browser context,
 * making it easier to maintain, test, and reuse across the codebase.
 *
 * Benefits:
 * - Keeps JxBrowserCompose.kt cleaner and more focused on UI logic
 * - Provides a single source of truth for browser JavaScript
 * - Makes JavaScript code easier to find, update, and document
 * - Enables future testing of JavaScript snippets if needed
 */
object BrowserJavaScripts {
    /**
     * JavaScript to inject for Cmd+Click (Mac) / Ctrl+Click (Windows/Linux) to open links in new tabs.
     * Should be injected once after page load.
     *
     * When the user holds Cmd/Ctrl and clicks on a link, this intercepts the click,
     * prevents the default navigation, and calls window.open() with _blank target.
     * JxBrowser's OpenPopupCallback then routes this to open as a new tab.
     *
     * Uses capture phase (true) to intercept before normal click handlers.
     *
     * The guards all exist because this hijacks the click before the page sees it, so anything
     * it claims wrongly is a link the page can no longer handle itself:
     * - never a `download` anchor, whose whole point is not to navigate;
     * - only http(s), so `javascript:`, `mailto:`, `blob:` and `data:` stay with the page. This
     *   also covers an SVG `<a>`, whose `href` is an `SVGAnimatedString` rather than a string -
     *   it has no `protocol`, so it falls through to Chromium instead of being opened as
     *   `[object SVGAnimatedString]`. Do not "simplify" this to `new URL(link.href)`.
     * - only the primary button. Near-dead as written, since Chromium fires `auxclick` rather
     *   than `click` for the others, but the cost is one comparison.
     *
     * `defaultPrevented` is checked for the case where another capture-phase listener above us
     * has already cancelled the click. It says nothing about the page's own handlers: this runs
     * on the capture phase, so those have not run yet.
     *
     * Anything that falls through reaches Chromium's native cmd+click, which arrives at
     * `CreatePopupCallback` with a correct target URL - so falling through is always safe.
     */
    val injectCmdClickHandler =
        """
        (function() {
            if (!window._cmdClickHandlerAdded) {
                document.addEventListener('click', function(event) {
                    if (!(event.metaKey || event.ctrlKey)) return;
                    if (event.button !== 0 || event.defaultPrevented) return;
                    const link = event.target.closest('a');
                    if (!link || !link.href) return;
                    if (link.hasAttribute('download')) return;
                    const protocol = link.protocol;
                    if (protocol !== 'http:' && protocol !== 'https:') return;
                    event.preventDefault();
                    event.stopPropagation();
                    window.open(link.href, '_blank');
                }, true);
                window._cmdClickHandlerAdded = true;
            }
        })();
        """.trimIndent()

    /**
     * Generate JavaScript to find a link element at given screen coordinates.
     *
     * Uses document.elementFromPoint() to find the element, then traverses up
     * the DOM tree to find the nearest anchor tag with an href.
     *
     * **Usage**: `frame.executeJavaScript<String?>(BrowserJavaScripts.getLinkAtPoint(x, y))`
     *
     * @param x The x coordinate in the viewport
     * @param y The y coordinate in the viewport
     * @return JavaScript code that returns the link URL or null
     */
    fun getLinkAtPoint(
        x: Int,
        y: Int,
    ): String =
        """
        (function() {
            var el = document.elementFromPoint($x, $y);
            while (el) {
                if (el.tagName === 'A' && el.href) return el.href;
                el = el.parentElement;
            }
            return null;
        })()
        """.trimIndent()

    /**
     * Put the video a call is actually showing into Picture-in-Picture.
     *
     * Returns `"entered"` on success and a short reason otherwise, so the caller can tell whether
     * a pop-out is ours to close later.
     *
     * Picking the element is the whole difficulty, and [enablePictureInPicture] gets it wrong on a
     * call: it takes the first `<video>` over 100x100, and a meeting page has many - the
     * self-view, one per participant, and a screen share. So this scores them instead:
     *
     * - **A live `srcObject` is required.** Every participant tile is a `MediaStream`; a `<video>`
     *   with a `src` is an advert or a background loop, never the call.
     * - **Muted loses heavily.** Intended to demote the self-view, and worth keeping for sites
     *   where it discriminates - but measured on a live Meet call it does not: Meet routes call
     *   audio separately, so *every* tile is muted and the penalty applies uniformly, leaving
     *   size to decide. Do not read this line as "the muted one is the self-view".
     * - **Bigger wins**, by painted area, because the speaker's tile is the large one.
     * - Zero-dimension and `disablePictureInPicture` elements are skipped: a video that has not
     *   produced a frame rejects with `InvalidStateError`, which is the same failure as picking
     *   nothing but harder to read in a log.
     */

    /**
     * Captures the page's `enterpictureinpicture` media-session handler at document start.
     *
     * This is how Chrome actually pops a call out, and it is not by calling
     * `requestPictureInPicture()` on a video. Chrome's path is
     * `MediaSessionImpl::EnterAutoPictureInPicture()` -> Blink's `MediaSession::DidReceiveAction`,
     * which calls `LocalFrame::NotifyUserActivation()` and then invokes **the handler the site
     * registered**. The site opens its own window; the browser only fires the action.
     *
     * That matters for Google Meet, whose pop-out is a Document PiP window it builds itself -
     * participant tiles, mute, camera, leave, chat, captions. Meet additionally installs an own
     * property `requestPictureInPicture` on its video elements that returns a promise which never
     * settles, which reads like a hang and is better understood as Meet refusing native element
     * PiP so that its own handler is the only route in.
     *
     * `navigator.mediaSession` exposes no way to read a handler back, so the only way to invoke it
     * is to hold a reference from when it was registered - hence document start, before the page's
     * own scripts run. The wrapper is otherwise transparent: it forwards every call, including the
     * `null` that unregisters, and it never lets a throw of ours escape into the page.
     */
    val captureMediaSessionPipHandler =
        """
        (function () {
            if (window.__bossPipCaptureInstalled) return;
            window.__bossPipCaptureInstalled = true;
            try {
                var ms = navigator.mediaSession;
                if (!ms || typeof ms.setActionHandler !== 'function') return;
                // Also record the geometry a Document PiP request asks for. The popup arrives at
                // the host with empty bounds, so without this the window is sized by guesswork -
                // and a site that lays its pop-out out against the size it asked for gets a
                // window it did not ask for.
                if (window.documentPictureInPicture &&
                    typeof documentPictureInPicture.requestWindow === 'function') {
                    var rw = documentPictureInPicture.requestWindow.bind(documentPictureInPicture);
                    documentPictureInPicture.requestWindow = function (options) {
                        try {
                            var o = options || {};
                            if (o.width && o.height) {
                                window.__bossPipRequestedSize = o.width + 'x' + o.height;
                            }
                        } catch (e) { /* never break the request itself */ }
                        return rw(options);
                    };
                }
                var original = ms.setActionHandler.bind(ms);
                ms.setActionHandler = function (action, handler) {
                    try {
                        if (action === 'enterpictureinpicture') {
                            window.__bossPipEnterHandler = handler;
                        }
                    } catch (e) { /* never break the page's registration */ }
                    return original(action, handler);
                };
            } catch (e) { /* a page that froze navigator.mediaSession keeps its own behaviour */ }
        })();
        """.trimIndent()

    val enterCallPictureInPicture =
        """
        (function () {
            window.__bossPip = { state: 'pending', picked: null, activation: null, videos: 0, route: null };
            if (document.pictureInPictureElement) { window.__bossPip.state = 'already'; return 'already'; }
            if (window.documentPictureInPicture && documentPictureInPicture.window) {
                window.__bossPip.state = 'already'; return 'already';
            }
            window.__bossPip.activation =
                navigator.userActivation ? navigator.userActivation.isActive : 'unknown';

            var best = null;
            var bestScore = -1;
            var videos = document.querySelectorAll('video');
            window.__bossPip.videos = videos.length;
            for (var i = 0; i < videos.length; i++) {
                var v = videos[i];
                if (!v.srcObject) continue;
                if (v.disablePictureInPicture) continue;
                if (!v.videoWidth || !v.videoHeight) continue;
                var score = v.videoWidth * v.videoHeight;
                if (v.muted) score = score / 1000;
                if (score > bestScore) { bestScore = score; best = v; }
            }
            if (!best) { window.__bossPip.state = 'no call video'; return 'no call video'; }
            window.__bossPip.picked = best.videoWidth + 'x' + best.videoHeight;

            function poppedOut() {
                return !!document.pictureInPictureElement ||
                    !!(window.documentPictureInPicture && documentPictureInPicture.window);
            }

            function native(reason) {
                if (poppedOut()) return;
                window.__bossPip.route = 'native' + (reason ? ' (' + reason + ')' : '');
                try {
                    HTMLVideoElement.prototype.requestPictureInPicture.call(best)
                        .then(function () { window.__bossPip.state = 'entered'; })
                        .catch(function (e) { window.__bossPip.state = 'rejected: ' + e.name + ': ' + e.message; });
                } catch (e) {
                    window.__bossPip.state = 'threw: ' + e.name;
                }
            }

            // Prefer the SITE's own Picture-in-Picture when it has installed one.
            //
            // Google Meet replaces requestPictureInPicture with an own property on the element,
            // and that override is the entry point to Meet's real pop-out - participant tiles,
            // mute, camera, leave, chat, captions - built as a Document PiP window. It is what
            // Chrome ends up invoking through the media-session action, so calling it is what
            // makes this match Chrome rather than floating a lone video tile.
            //
            // It gets a deadline, because the same override hangs forever when its Document PiP
            // window cannot be shown. The native call is the fallback, and the minted activation
            // lasts five seconds, so it is still valid when the deadline fires.
            // Chrome's own route: invoke the site's media-session handler, which is what opens
            // Meet's real pop-out. Calling requestPictureInPicture() on the element is NOT what
            // Chrome does, and on Meet that method is an own-property override that never settles.
            var handler = window.__bossPipEnterHandler;
            if (typeof handler !== 'function') { native('no media-session handler'); return 'requested'; }

            window.__bossPip.route = 'site';
            var settled = false;
            try {
                // Shaped like the details Chrome passes. `contentoccluded` is the reason it uses
                // when the tab stopped being the active one, which is exactly this case.
                handler({ action: 'enterpictureinpicture', enterPictureInPictureReason: 'contentoccluded' });
            } catch (e) {
                settled = true;
                native('handler threw ' + e.name);
            }
            // Poll rather than wait out the whole deadline: the site opens its window
            // asynchronously, and the caller reads this state back on its own clock. Settling
            // only at the deadline meant the read happened first and every site-route pop-out
            // was recorded as a failure - so nothing closed it again on the way back.
            var waited = 0;
            var tick = setInterval(function () {
                if (poppedOut()) {
                    window.__bossPip.state = 'entered';
                    clearInterval(tick);
                    return;
                }
                if (settled) { clearInterval(tick); return; }
                waited += $SITE_PIP_POLL_MS;
                if (waited >= $SITE_PIP_DEADLINE_MS) {
                    clearInterval(tick);
                    native('site timed out');
                }
            }, $SITE_PIP_POLL_MS);
            return 'requested';
        })()
        """.trimIndent()
            .replace("${'$'}SITE_PIP_DEADLINE_MS", SITE_PIP_DEADLINE_MS.toString())
            .replace("${'$'}SITE_PIP_POLL_MS", SITE_PIP_POLL_MS.toString())

    /** How long the site's own Picture-in-Picture gets before we fall back to the video tile. */
    const val SITE_PIP_DEADLINE_MS = 1500

    /** How often the site route is checked for having opened a window. */
    const val SITE_PIP_POLL_MS = 100

    /**
     * Reads back what [enterCallPictureInPicture] actually achieved.
     *
     * Separate because `requestPictureInPicture()` returns a **promise**, and `executeJavaScript`
     * cannot await one. Reporting the synchronous return as success is how this shipped a log
     * line saying `entered` while no window ever appeared: a rejected promise is not a throw, so
     * the try/catch around the call sees nothing. The state is settled asynchronously and read
     * back a beat later.
     */
    val readCallPictureInPictureResult =
        """
        JSON.stringify(window.__bossPip || { state: 'no attempt recorded' })
        """.trimIndent()

    /** The `{width, height}` a Document PiP request asked for, as `WxH`, or empty. */
    val readRequestedPipSize =
        """
        String(window.__bossPipRequestedSize || '')
        """.trimIndent()

    /**
     * Fills a Document Picture-in-Picture window with the call, when the site opened one and then
     * left it empty.
     *
     * Google Meet accepts the `enterpictureinpicture` action, opens its window, copies its
     * stylesheets in - and stops. Its own pop-out never arrives, and the window stays blank. So
     * this builds the equivalent: the video tiles plus the controls people actually reach for.
     *
     * **Nothing is moved out of the page.** A `MediaStream` can feed any number of `<video>`
     * elements, so the tiles here are new elements sharing the page's streams. Moving Meet's own
     * nodes across would have been closer to what Meet does and far more destructive - the call UI
     * would vanish from the tab, and putting it back correctly on every exit path is the kind of
     * thing that fails once and loses somebody their meeting.
     *
     * The controls are proxies: each one clicks the real button in the page, so Meet stays the
     * only thing that actually mutes a microphone or leaves a call.
     *
     * Two signals do the work, and neither is a class name, because Meet's are obfuscated and
     * change:
     * - `track.getSettings().deviceId` is present only on a **local** capture track, which is how
     *   your own tile is told from everyone else's without guessing at markup.
     * - `data-is-muted` on the mic and camera buttons is Meet's own state, so the pop-out's
     *   buttons cannot drift out of sync with the page's.
     *
     * `aria-label` matching is the weak part and is known to be: it is English-only, so on a
     * localised Meet the controls simply do not appear and the tiles still do.
     */
    val populateCallPictureInPicture =
        """
        (function () {
            var w = window.documentPictureInPicture && documentPictureInPicture.window;
            if (!w) return 'no window';
            var doc = w.document;
            if (doc.querySelector('[data-boss-pip]')) return 'already populated';

            function findButton(pattern) {
                var all = document.querySelectorAll('button[aria-label],[role=button][aria-label]');
                for (var i = 0; i < all.length; i++) {
                    if (pattern.test(all[i].getAttribute('aria-label') || '')) return all[i];
                }
                return null;
            }

            var root = doc.createElement('div');
            root.setAttribute('data-boss-pip', '1');
            root.setAttribute(
                'style',
                'position:fixed;inset:0;margin:0;background:#202124;color:#e8eaed;' +
                    'font:13px system-ui,-apple-system,sans-serif;display:flex;flex-direction:column'
            );

            var tiles = doc.createElement('div');
            tiles.setAttribute(
                'style',
                'flex:1;display:grid;gap:6px;padding:8px 8px 4px;min-height:0;' +
                    'grid-template-columns:repeat(auto-fit,minmax(120px,1fr))'
            );
            root.appendChild(tiles);

            // A local track carries a deviceId; a remote one does not. Remote participants are
            // what a pop-out is for, so they lead and the self-view only appears alone.
            var local = [];
            var remote = [];
            var sources = document.querySelectorAll('video');
            for (var i = 0; i < sources.length; i++) {
                var v = sources[i];
                if (!v.srcObject || !v.videoWidth || !v.videoHeight) continue;
                var track = v.srcObject.getVideoTracks()[0];
                if (!track || track.readyState !== 'live') continue;
                var isLocal = false;
                try { isLocal = !!(track.getSettings() || {}).deviceId; } catch (e) { isLocal = false; }
                // Carry the source tile's own mirroring across. Meet flips the self-view - a
                // preview of yourself should read like a mirror - and leaves everyone else alone,
                // so copying the computed transform per tile reproduces exactly that without
                // deciding anything about which tile is which.
                var flipped = false;
                try {
                    flipped = (getComputedStyle(v).transform || '').indexOf('-1') === 7;
                } catch (e) { flipped = false; }
                // Meet writes the participant's name inside the tile; the pop-out shows it in the
                // same corner Chrome's does. Read from the nearest ancestor that has one rather
                // than a class name, since Meet's are obfuscated.
                var name = '';
                try {
                    var scope = v.closest('[data-participant-id]') || v.parentElement;
                    for (var hops = 0; scope && hops < 4 && !name; hops++) {
                        var texts = scope.querySelectorAll('div,span');
                        for (var t = 0; t < texts.length; t++) {
                            var candidate = (texts[t].textContent || '').trim();
                            if (candidate && candidate.length < 40 && texts[t].children.length === 0) {
                                name = candidate;
                                break;
                            }
                        }
                        scope = scope.parentElement;
                    }
                } catch (e) { name = ''; }
                (isLocal ? local : remote).push({ stream: v.srcObject, mirrored: flipped, name: name });
            }
            var streams = remote.length ? remote : local;
            if (!streams.length) return 'no live tiles';

            for (var j = 0; j < streams.length && j < $MAX_PIP_TILES; j++) {
                var card = doc.createElement('div');
                card.setAttribute(
                    'style',
                    'position:relative;background:#3c4043;border-radius:10px;overflow:hidden;' +
                        'min-height:0;display:flex;align-items:center;justify-content:center'
                );
                var tile = doc.createElement('video');
                tile.autoplay = true;
                tile.playsInline = true;
                tile.muted = true;
                tile.srcObject = streams[j].stream;
                tile.setAttribute(
                    'style',
                    'width:100%;height:100%;background:#000;border-radius:6px'
                );
                // contain, not cover: Chrome letterboxes a pop-out rather than cropping heads out
                // of frame, and a 480px window crops hard.
                tile.style.setProperty('object-fit', 'contain', 'important');
                // Set explicitly either way, and as important: Meet's own stylesheets are in
                // this window (it copies them before abandoning the pop-out) and one of them
                // mirrors `video`, which would otherwise flip every tile including remote ones.
                // An inline style attribute loses to a stylesheet rule, so this cannot be folded
                // into the style string above.
                var mirror = streams[j].mirrored ? 'scaleX(-1)' : 'none';
                tile.style.setProperty('transform', mirror, 'important');
                tile.style.setProperty('-webkit-transform', mirror, 'important');
                card.appendChild(tile);
                if (streams[j].name) {
                    var label = doc.createElement('div');
                    label.textContent = streams[j].name;
                    label.setAttribute(
                        'style',
                        'position:absolute;left:10px;bottom:8px;color:#fff;font:500 13px system-ui;' +
                            'text-shadow:0 1px 3px rgba(0,0,0,.8);pointer-events:none;' +
                            'max-width:calc(100% - 20px);overflow:hidden;text-overflow:ellipsis;' +
                            'white-space:nowrap'
                    );
                    card.appendChild(label);
                }
                tiles.appendChild(card);
            }

            var bar = doc.createElement('div');
            bar.setAttribute(
                'style',
                'display:flex;gap:8px;justify-content:center;align-items:center;' +
                    'padding:10px 8px 12px;background:#202124'
            );
            root.appendChild(bar);

            // Meet's icons are ligatures in its own "Google Symbols" font, and its stylesheets are
            // already inside this window - it copies them before abandoning the pop-out. So the
            // real icon node is cloned rather than an icon name being hardcoded: the glyph is
            // exactly Meet's, and the ligature text flips itself (mic <-> mic_off) as state
            // changes, which a hardcoded name could not do.
            function iconOf(button) {
                return button.querySelector('i.google-symbols, .google-symbols, i[class*=symbols]');
            }

            // The set and order Chrome's pop-out shows: mic, camera, present, more, hang up.
            var controls = [
                { key: 'mic', match: /microphone/i, danger: false },
                { key: 'cam', match: /camera/i, danger: false },
                { key: 'present', match: /present now|share screen|presenting/i, danger: false },
                { key: 'more', match: /more options|^more$/i, danger: false },
                { key: 'leave', match: /leave call|hang up/i, danger: true }
            ];
            var live = [];
            for (var c = 0; c < controls.length; c++) {
                (function (spec) {
                    var source = findButton(spec.match);
                    if (!source) return;
                    var btn = doc.createElement('button');
                    btn.title = source.getAttribute('aria-label') || spec.key;
                    btn.setAttribute('aria-label', btn.title);
                    // Hang up is a wider pill, the rest are circles - Chrome's shape.
                    btn.setAttribute(
                        'style',
                        (spec.danger ? 'width:56px;border-radius:18px;' : 'width:36px;border-radius:50%;') +
                            'height:36px;border:0;cursor:pointer;' +
                            'display:flex;align-items:center;justify-content:center;' +
                            'font-size:18px;line-height:1;padding:0'
                    );
                    var sourceIcon = iconOf(source);
                    if (sourceIcon) {
                        btn.appendChild(sourceIcon.cloneNode(true));
                    } else {
                        btn.textContent = spec.key;
                        btn.style.fontSize = '11px';
                    }
                    btn.onclick = function () {
                        var target = findButton(spec.match) || source;
                        target.click();
                    };
                    bar.appendChild(btn);
                    live.push({ spec: spec, btn: btn });
                })(controls[c]);
            }

            // Meet owns the state; the pop-out only reflects it. Polling rather than observing
            // because the button is replaced, not mutated, when Meet re-renders its bar.
            function sync() {
                for (var k = 0; k < live.length; k++) {
                    var entry = live[k];
                    var source = findButton(entry.spec.match);
                    if (!source) continue;
                    var muted = source.getAttribute('data-is-muted') === 'true';
                    var sourceIcon = iconOf(source);
                    var ourIcon = entry.btn.firstElementChild;
                    if (sourceIcon && ourIcon && ourIcon.textContent !== sourceIcon.textContent) {
                        ourIcon.textContent = sourceIcon.textContent;
                    }
                    var label = source.getAttribute('aria-label');
                    if (label) {
                        entry.btn.title = label;
                        entry.btn.setAttribute('aria-label', label);
                    }
                    if (entry.spec.danger) {
                        entry.btn.style.background = '#d93025';
                        entry.btn.style.color = '#fff';
                    } else {
                        entry.btn.style.background = muted ? '#f9dedc' : '#3c4043';
                        entry.btn.style.color = muted ? '#8c1d18' : '#e8eaed';
                    }
                }
            }
            sync();
            var timer = w.setInterval(sync, $PIP_SYNC_MS);
            w.addEventListener('pagehide', function () { w.clearInterval(timer); });

            doc.body.style.cssText = 'margin:0;overflow:hidden;background:#202124';
            doc.body.appendChild(root);
            return 'populated ' + Math.min(streams.length, $MAX_PIP_TILES) + ' tile(s), ' +
                live.length + ' control(s)';
        })()
        """.trimIndent()
            .replace("${'$'}MAX_PIP_TILES", MAX_PIP_TILES.toString())
            .replace("${'$'}PIP_SYNC_MS", PIP_SYNC_MS.toString())

    /** How many participant tiles a pop-out shows before it stops being readable. */
    const val MAX_PIP_TILES = 4

    /** How often the pop-out's buttons re-read Meet's own mute state. */
    const val PIP_SYNC_MS = 500

    /**
     * Puts a pop-out back, whichever kind it is.
     *
     * A site-owned pop-out is a Document PiP window and leaves `document.pictureInPictureElement`
     * null, so closing only the element kind would strand Meet's window on screen after the user
     * returned to the tab.
     */
    val exitCallPictureInPicture =
        """
        (function () {
            var closed = [];
            if (document.pictureInPictureElement) {
                document.exitPictureInPicture();
                closed.push('element');
            }
            if (window.documentPictureInPicture && documentPictureInPicture.window) {
                documentPictureInPicture.window.close();
                closed.push('document');
            }
            return closed.join(',') || 'nothing';
        })()
        """.trimIndent()

    /**
     * Enable Picture-in-Picture mode for videos on the page.
     *
     * Attempts to find and activate PiP on:
     * 1. YouTube's main video player
     * 2. The only video on the page
     * 3. The largest visible video (if multiple)
     *
     * Toggles PiP off if already active.
     *
     * **Usage**: `frame.executeJavaScript<Unit>(BrowserJavaScripts.enablePictureInPicture)`
     */
    val enablePictureInPicture =
        """
        (function() {
            // Find all video elements on the page
            const videos = document.querySelectorAll('video');

            // For YouTube and similar sites, find the main video player
            let targetVideo = null;

            // Check for YouTube specific video
            const ytVideo = document.querySelector('video.html5-main-video, video.video-stream');
            if (ytVideo) {
                targetVideo = ytVideo;
            } else if (videos.length === 1) {
                // If there's only one video, use it
                targetVideo = videos[0];
            } else if (videos.length > 1) {
                // If multiple videos, try to find the visible one
                for (let video of videos) {
                    const rect = video.getBoundingClientRect();
                    if (rect.width > 100 && rect.height > 100 &&
                        video.readyState >= 2) { // HAVE_CURRENT_DATA
                        targetVideo = video;
                        break;
                    }
                }
            }

            if (targetVideo) {
                if (document.pictureInPictureElement) {
                    document.exitPictureInPicture();
                } else if (HTMLVideoElement.prototype.requestPictureInPicture) {
                    // Prototype, not instance: a page can shadow this per element, and Google
                    // Meet does - its override never settles. See enterCallPictureInPicture.
                    HTMLVideoElement.prototype.requestPictureInPicture.call(targetVideo).catch(err => {
                        console.error('PiP failed:', err);
                    });
                }
            }
        })();
        """.trimIndent()
}
