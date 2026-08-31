package ai.rever.boss.plugin.browser

/**
 * The page-side half of automatic Picture-in-Picture.
 *
 * Split out of [BrowserJavaScripts] because these grew into a feature of their own: capturing the
 * site's media-session handler at document start, opening a pop-out by whichever route the page
 * allows, building its contents when the site opens one and abandons it, and serving its controls.
 *
 * **Nothing here may assign `innerHTML`.** A pop-out inherits the opener's CSP, and Google Meet
 * ships Trusted Types - so even `innerHTML = ''` throws "This document requires 'TrustedHTML'
 * assignment", which silently left the whole window blank. Build with `createElement` and clear
 * with `removeChild`.
 */
internal object PopOutScripts {
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
                // Chrome's PiP mic/camera/hang-up buttons are media-session actions: the PiP
                // window fires togglemicrophone/togglecamera/hangup and the SITE mutes itself.
                // Meet registers all three, so the pop-out's buttons invoke these handlers -
                // never a scraped button, never a synthesized click.
                window.__bossPipActions = {};
                window.__bossPipLog = [];
                var log = function (line) {
                    window.__bossPipLog.push(Date.now() % 100000 + ' ' + line);
                    if (window.__bossPipLog.length > 40) window.__bossPipLog.shift();
                };
                // While a pop-out of ours is open, the page is told it is visible. A hidden
                // tab's DOM is FROZEN: Meet's rendering is rAF-driven and visibility-gated, so
                // someone joining, leaving or starting a share never reaches the DOM of a hidden
                // tab - the pop-out showed whatever was mounted at the moment of the switch and
                // nothing after. Everything that DID keep working (mute state, camera-off)
                // travels through tracks and the media session, no DOM needed - which is the
                // tell. Chrome avoids this because Meet renders into the PiP window itself,
                // which is visible; an embedder cannot get Meet to populate its window, so the
                // page is kept rendering instead. Off by default and toggled only around our own
                // pop-out, so a backgrounded tab with no pop-out keeps its normal throttling.
                window.__bossPipSpoofVisible = false;
                try {
                    var vsDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState');
                    var hidDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'hidden');
                    if (vsDesc && vsDesc.get && hidDesc && hidDesc.get) {
                        var origVs = vsDesc.get;
                        var origHid = hidDesc.get;
                        Object.defineProperty(Document.prototype, 'visibilityState', {
                            configurable: true,
                            get: function () {
                                return window.__bossPipSpoofVisible ? 'visible' : origVs.call(this);
                            }
                        });
                        Object.defineProperty(Document.prototype, 'hidden', {
                            configurable: true,
                            get: function () {
                                return window.__bossPipSpoofVisible ? false : origHid.call(this);
                            }
                        });
                        // rAF is throttled by REAL visibility, which the getters cannot change,
                        // so while spoofed-and-actually-hidden it falls back to a timer. Hidden
                        // timers run at ~1Hz, which is enough for tile layout - the video pixels
                        // flow through MediaStream clones in the visible pop-out regardless.
                        var origRaf = window.requestAnimationFrame.bind(window);
                        var origCaf = window.cancelAnimationFrame.bind(window);
                        window.requestAnimationFrame = function (cb) {
                            if (window.__bossPipSpoofVisible && origHid.call(document)) {
                                return window.setTimeout(function () {
                                    try { cb(performance.now()); } catch (e) { /* page's own */ }
                                }, 100);
                            }
                            return origRaf(cb);
                        };
                        window.cancelAnimationFrame = function (handle) {
                            // The handle may belong to either space; cancelling in both is safe.
                            origCaf(handle);
                            window.clearTimeout(handle);
                        };
                    }
                } catch (e) { /* a page that sealed Document.prototype keeps real visibility */ }

                // The media layer is the only source that keeps flowing while the tab is
                // hidden. Measured: a NEW participant or share never reaches a hidden tab's DOM
                // - mounting an element needs the rendering pipeline (layout, Intersection/
                // ResizeObserver), which Chromium does not run for a hidden widget regardless
                // of what visibilityState says - but RTCPeerConnection 'track' events fire on
                // signaling, network-driven. So every remote video track is recorded here and
                // the pop-out renders tracks the DOM has no tile for.
                // The CONNECTIONS are recorded, and their receivers are enumerated at read
                // time - never the 'track' events. Measured on a live call: every track the
                // event hook had recorded was ended/muted while a remote video was visibly
                // playing, because Meet pre-creates recvonly transceivers and takes
                // `receiver.track` directly, a path that fires no event worth having.
                // `pc.getReceivers()` holds the CURRENT tracks whatever route delivered them.
                window.__bossPipPeerConnections = [];
                try {
                    var OrigPC = window.RTCPeerConnection;
                    if (OrigPC) {
                        window.RTCPeerConnection = function (config) {
                            var pc = new OrigPC(config);
                            try {
                                window.__bossPipPeerConnections.push(pc);
                                log('pc created (' + window.__bossPipPeerConnections.length + ')');
                            } catch (e) { /* never break the page's call */ }
                            return pc;
                        };
                        window.RTCPeerConnection.prototype = OrigPC.prototype;
                        // Statics (generateCertificate) keep working through the wrapper.
                        Object.setPrototypeOf(window.RTCPeerConnection, OrigPC);
                    }
                } catch (e) { /* a page that sealed RTCPeerConnection keeps its own */ }
                // The user's own screen share, same reason: started from the pop-out, the
                // hidden DOM never mounts its tile, but the stream is handed to the page right
                // here.
                try {
                    var md = navigator.mediaDevices;
                    if (md && typeof md.getDisplayMedia === 'function') {
                        var gdm = md.getDisplayMedia.bind(md);
                        md.getDisplayMedia = function (constraints) {
                            return gdm(constraints).then(function (stream) {
                                window.__bossPipLocalShare = stream;
                                log('getDisplayMedia stream captured');
                                return stream;
                            });
                        };
                    }
                } catch (e) { /* keep the page's own capture behaviour */ }

                var original = ms.setActionHandler.bind(ms);
                ms.setActionHandler = function (action, handler) {
                    try {
                        if (action === 'enterpictureinpicture') {
                            window.__bossPipEnterHandler = handler;
                        }
                        if (action === 'togglemicrophone' || action === 'togglecamera' ||
                            action === 'hangup' || action === 'togglescreenshare') {
                            window.__bossPipActions[action] = handler;
                        }
                        // Every registration is logged, not only the kept ones - which actions a
                        // site registers is exactly what the next control needs measured.
                        log('setActionHandler ' + action + ' ' + (handler ? 'set' : 'cleared'));
                    } catch (e) { /* never break the page's registration */ }
                    return original(action, handler);
                };
                // Chrome's PiP button STATE comes from the site too: Meet reports through
                // setMicrophoneActive/setCameraActive, which is what flips the crossed-out icon
                // in Chrome's own window. Recorded here for the pop-out to read - the authority
                // on mute state, not the aria of a control bar that may not even be rendered.
                window.__bossPipMediaState = {};
                ['setMicrophoneActive', 'setCameraActive', 'setScreenshareActive']
                    .forEach(function (name) {
                    if (typeof ms[name] !== 'function') return;
                    var fn = ms[name].bind(ms);
                    var slot = name === 'setMicrophoneActive' ? 'micActive'
                        : name === 'setCameraActive' ? 'camActive' : 'screenshareActive';
                    ms[name] = function (active) {
                        try {
                            window.__bossPipMediaState[slot] = !!active;
                            log(name + '(' + active + ')');
                        } catch (e) { /* never break the page's report */ }
                        return fn(active);
                    };
                });
            } catch (e) { /* a page that froze navigator.mediaSession keeps its own behaviour */ }
        })();
        """.trimIndent()

    val enterCallPictureInPicture =
        (
            PopOutFillScript.fillPopOutFunction + "\n" +
                """
                (function () {
                    window.__bossPip = { state: 'pending', picked: null, activation: null, videos: 0, route: null,
                        t0: performance.now(), tHandler: null, tWindow: null, tFilled: null };
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
                    // No early return on a missing video. A camera-off call has no <video> at
                    // all, and the routes below - the site's handler, and our own Document PiP
                    // window - need none: only the native element fallback does, which guards
                    // itself. Returning here is what left a camera-off call with no pop-out.
                    window.__bossPip.picked = best ? best.videoWidth + 'x' + best.videoHeight : null;

                    function poppedOut() {
                        return !!document.pictureInPictureElement ||
                            !!(window.documentPictureInPicture && documentPictureInPicture.window);
                    }

                    // Open a pop-out ourselves. Needed because every other route depends on
                    // something the page owns: the site's media-session handler may not be
                    // registered yet (Meet registers late, so a quick tab switch misses it), and
                    // the native element route needs a live <video>, which a call with the camera
                    // off does not have. A Document PiP window needs neither, and filling it is
                    // already our job - so this is the branch that makes a pop-out appear at all,
                    // rather than the one that makes it the site's.
                    // Only for a page that actually has a call in it. Eligibility is "capturing
                    // audio or video on https", which a dictation box, a voice note or a
                    // whiteboard holding the microphone all satisfy - and those have no tiles, so
                    // popping them out produced an empty window offering to mute something the
                    // user never asked to pop out. Deliberately NOT gated on video capture, which
                    // was the other candidate fix: a call joined with the camera off captures no
                    // video at all, and that is exactly the case this feature was extended to
                    // cover.
                    function hasCall() {
                        var tiles = document.querySelectorAll('[data-participant-id]');
                        for (var i = 0; i < tiles.length; i++) {
                            if (tiles[i].getBoundingClientRect().width > 1) return true;
                        }
                        return false;
                    }
                    if (!hasCall()) { window.__bossPip.state = 'no call'; return 'no call'; }

                    function ownWindow(reason) {
                        if (poppedOut()) return;
                        window.__bossPip.route = 'own' + (reason ? ' (' + reason + ')' : '');
                        try {
                            documentPictureInPicture
                                .requestWindow({ width: $OWN_PIP_WIDTH, height: $OWN_PIP_HEIGHT })
                                .then(function () {
                                    window.__bossPip.state = 'entered';
                                    try {
                                        window.__bossPip.filled = __bossFillPip();
                                    } catch (e) {
                                        window.__bossPip.filled = 'threw ' + e.name + ': ' + e.message;
                                    }
                                })
                                .catch(function (e) { native('own window rejected ' + e.name); });
                        } catch (e) {
                            native('own window threw ' + e.name);
                        }
                    }

                    function native(reason) {
                        if (poppedOut()) return;
                        // The only branch that genuinely needs a video element.
                        if (!best) { window.__bossPip.state = 'no call video'; return; }
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
                    if (typeof handler !== 'function') { ownWindow('no handler yet'); return 'requested'; }

                    window.__bossPip.route = 'site';
                    var settled = false;
                    try {
                        // Shaped like the details Chrome passes. `contentoccluded` is the reason it uses
                        // when the tab stopped being the active one, which is exactly this case.
                        handler({ action: 'enterpictureinpicture', enterPictureInPictureReason: 'contentoccluded' });
                        window.__bossPip.tHandler = Math.round(performance.now() - window.__bossPip.t0);
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
                            window.__bossPip.tWindow = Math.round(performance.now() - window.__bossPip.t0);
                            clearInterval(tick);
                            // Filled here, in the same pass, rather than waiting for the host to come
                            // back with a second script. This is what makes the pop-out appear with
                            // content in it instead of blank for a beat first.
                            try {
                                window.__bossPip.filled = __bossFillPip();
                                window.__bossPip.tFilled = Math.round(performance.now() - window.__bossPip.t0);
                            } catch (e) {
                                window.__bossPip.filled = 'threw ' + e.name + ': ' + e.message;
                            }
                            return;
                        }
                        if (settled) { clearInterval(tick); return; }
                        waited += $SITE_PIP_POLL_MS;
                        if (waited >= $SITE_PIP_DEADLINE_MS) {
                            clearInterval(tick);
                            ownWindow('site timed out');
                        }
                    }, $SITE_PIP_POLL_MS);
                    return 'requested';
                })()
                """.trimIndent()
        )

    /** How long the site's own Picture-in-Picture gets before we fall back to the video tile. */
    // These four MUST stay `const val`. They are interpolated into the raw strings above as
    // `${'$'}NAME` templates, and a compile-time constant resolves even though it is declared
    // after its use. Drop `const` from any of them and the template reads an uninitialized
    // member as 0 - `setInterval(..., 0)` and a deadline already expired on the first tick, so
    // the site route dies instantly and silently. (There used to be a `.replace` chain here that
    // looked like it did the substitution; it was searching for text interpolation had already
    // consumed, so it did nothing and hid this requirement.)
    const val SITE_PIP_DEADLINE_MS = 1500

    /** How often the site route is checked for having opened a window. */
    const val SITE_PIP_POLL_MS = 40

    /** Size of a pop-out we open ourselves, when the page will not open one. */
    const val OWN_PIP_WIDTH = 480

    const val OWN_PIP_HEIGHT = 320

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
     * Clicks a control the pop-out asked for, for the ones a synthetic click still drives.
     *
     * Mic and camera are NOT among them - Meet ignores an untrusted event for those, so the host
     * sends a real keystroke instead. Leave and the overflow menu are ordinary buttons.
     */
    fun clickPopOutControl(action: String): String =
        """
        (function () {
            var patterns = {
                present: /present now|share screen|presenting/i,
                more: /more options|^more${'$'}/i,
                leave: /leave call|hang up/i
            };
            var pattern = patterns['$action'];
            if (!pattern) return 'unknown';
            var all = document.querySelectorAll('button[aria-label],[role=button][aria-label]');
            for (var i = 0; i < all.length; i++) {
                if (pattern.test(all[i].getAttribute('aria-label') || '')) {
                    all[i].click();
                    return 'clicked';
                }
            }
            return 'not found';
        })()
        """.trimIndent()

    /**
     * Where to click for a control the pop-out asked for, as `x,y` in CSS pixels, or empty.
     *
     * The pop-out's buttons cannot act for themselves: Meet ignores an untrusted event, and
     * `element.click()` plus a full pointer sequence were both measured leaving `data-is-muted`
     * unchanged. Only real input works, and only the host can produce it - so this reports a
     * point and the host dispatches a genuine mouse press there.
     *
     * **The point is verified before it is offered.** `elementFromPoint` has to land inside the
     * button we found, or this returns nothing: a stale or covered rect would otherwise put a
     * real click at whatever is under it, and the neighbouring control is Leave call. Refusing is
     * always safe - the caller falls back to a keystroke.
     */
    fun locatePopOutControl(action: String): String =
        """
        (function () {
            var patterns = {
                mic: /microphone/i,
                cam: /camera/i,
                present: /present now|share screen|presenting/i,
                more: /more options/i,
                leave: /leave call|hang up/i
            };
            var pattern = patterns['$action'];
            if (!pattern) return '';
            var target = null;
            var all = document.querySelectorAll('button[aria-label],[role=button][aria-label]');
            for (var i = 0; i < all.length; i++) {
                if (pattern.test(all[i].getAttribute('aria-label') || '')) {
                    target = all[i];
                    break;
                }
            }
            if (!target) return '';
            var r = target.getBoundingClientRect();
            if (r.width < 4 || r.height < 4) return '';
            var cx = Math.round(r.left + r.width / 2);
            var cy = Math.round(r.top + r.height / 2);
            if (cx < 0 || cy < 0 || cx > window.innerWidth || cy > window.innerHeight) return '';
            var hit = document.elementFromPoint(cx, cy);
            if (!hit || (hit !== target && !target.contains(hit))) return '';
            return cx + ',' + cy;
        })()
        """.trimIndent()

    /** Takes the control the pop-out asked for, if any, and clears it. */
    val readPopOutAction =
        """
        (function () {
            var action = window.__bossPipAction || '';
            window.__bossPipAction = null;
            return action;
        })()
        """.trimIndent()

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
            if (window.__bossPipSpoofVisible) {
                window.__bossPipSpoofVisible = false;
                try { document.dispatchEvent(new Event('visibilitychange')); } catch (e) { }
            }
            if (document.pictureInPictureElement) {
                document.exitPictureInPicture();
                closed.push('element');
            }
            if (window.documentPictureInPicture && documentPictureInPicture.window) {
                documentPictureInPicture.window.close();
                closed.push('document');
            }
            // Closing the window is not enough for Google Meet here. Measured: the window
            // closes (documentPictureInPicture.window goes null) and Meet still shows "Your
            // Meet call is in another window" - the pagehide its restore hangs off is not
            // delivered in this embedder. Meet ships its own recovery for exactly this state,
            // the "Bring the call back here" button, and unlike the media controls it accepts
            // a synthetic click (measured working live). Polled briefly because Meet renders
            // the placeholder on its own schedule after the close. English-only match, the
            // same known limitation as the control labels.
            var tries = 0;
            var restore = function () {
                tries += 1;
                var link = null;
                var all = document.querySelectorAll('button,[role=button],a');
                for (var i = 0; i < all.length; i++) {
                    if (/bring the call back/i.test((all[i].textContent || '').trim())) {
                        link = all[i];
                        break;
                    }
                }
                if (link) {
                    link.click();
                    return;
                }
                if (tries < 10) setTimeout(restore, 200);
            };
            setTimeout(restore, 0);
            return closed.join(',') || 'nothing';
        })()
        """.trimIndent()
}
