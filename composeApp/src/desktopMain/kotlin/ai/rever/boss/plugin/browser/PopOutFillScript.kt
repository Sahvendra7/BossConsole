package ai.rever.boss.plugin.browser

/**
 * Builds the inside of a Document Picture-in-Picture window the site opened and abandoned.
 *
 * Split from [PopOutScripts], which holds the capture/enter/exit halves; this is the fill - the
 * tiles, the control bar, and the sync loop that keeps both honest. The same two rules apply:
 * no `innerHTML` anywhere (the window inherits Google Meet's Trusted Types CSP, and even
 * `innerHTML = ''` throws), and controls act through the media-session handlers captured at
 * document start, never through synthesized clicks, which Meet was measured ignoring.
 */
internal object PopOutFillScript {
    /**
     * The filling routine, as a declaration so it can be called from inside the enter script
     * rather than on a second round trip from the host.
     *
     * That distinction is the whole latency budget: a browser opens its pop-out synchronously,
     * while a host-side `executeJavaScript` plus the delay before it was most of the gap between
     * the tab going away and the window having anything in it.
     */
    val fillPopOutFunction: String
        get() =
            """
            function __bossFillPip() {
                var w = window.documentPictureInPicture && documentPictureInPicture.window;
                if (!w) return 'no window';
                var doc = w.document;
                if (doc.querySelector('[data-boss-pip]')) return 'already populated';
                // Leave a site that fills its own pop-out completely alone. Meet's leftovers are an
                // off-screen accessibility node, so presence of children proves nothing - only a
                // child that actually occupies space means somebody else is drawing here.
                var existing = doc.body ? doc.body.children : [];
                for (var e = 0; e < existing.length; e++) {
                    var box = existing[e].getBoundingClientRect();
                    if (box.height > 0 && box.width > 0) return 'site populated it';
                }

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

                // Built from Meet's OWN tiles, not from a scan of every <video> on the page.
            // Scanning found only cameras, so a call with everybody's video off produced nothing
            // to show. A tile is the unit Meet already thinks in: it holds a video when there is
            // one, an avatar when there is not, and a screen share is simply another tile.
            function readSources() {
                var found = [];
                var meetTiles = document.querySelectorAll('[data-participant-id]');
                for (var m = 0; m < meetTiles.length; m++) {
                    var tileEl = meetTiles[m];
                    if (tileEl.getBoundingClientRect().width < 1) continue;
                    var vid = null;
                    var candidates = tileEl.querySelectorAll('video');
                    for (var q = 0; q < candidates.length; q++) {
                        var cv = candidates[q];
                        if (!cv.srcObject || !cv.videoWidth || !cv.videoHeight) continue;
                        var vt = cv.srcObject.getVideoTracks()[0];
                        // A camera turned off leaves the element in place with its track ended or
                        // muted, so readyState alone still reports a video and the tile would keep
                        // showing a frozen last frame instead of falling back to the avatar.
                        if (!vt || vt.readyState !== 'live' || vt.muted) continue;
                        vid = cv;
                        break;
                    }
                    var isLocal = false;
                    var flipped = false;
                    if (vid) {
                        try {
                            isLocal = !!(vid.srcObject.getVideoTracks()[0].getSettings() || {}).deviceId;
                        } catch (e) { isLocal = false; }
                        try {
                            flipped = (getComputedStyle(vid).transform || '').indexOf('-1') === 7;
                        } catch (e) { flipped = false; }
                    }
                    var avatar = null;
                    // Chosen by NATURAL size, never layout size. Measured on a live call: the
                    // avatar <img> reports a 0x0 rect even on a visible tab (a 320x320 profile
                    // photo collapsed under the video layer), so a layout filter rejected it
                    // every time and a camera-off tile fell through to 'Waiting for video'.
                    var best = 0;
                    var imgs = tileEl.querySelectorAll('img');
                    for (var g = 0; g < imgs.length; g++) {
                        var im = imgs[g];
                        if (!im.src || im.naturalWidth < 32) continue;
                        var area = im.naturalWidth * im.naturalHeight;
                        if (area > best) {
                            best = area;
                            avatar = im;
                        }
                    }
                    if (!vid && !avatar) continue;
                    found.push({
                        id: tileEl.getAttribute('data-participant-id') || String(m),
                        stream: vid ? vid.srcObject : null,
                        mirrored: flipped,
                        avatar: avatar,
                        local: isLocal
                    });
                }
                var remoteOnly = found.filter(function (x) { return !x.local; });
                return remoteOnly.length ? remoteOnly : found;
            }

            // Re-rendered rather than built once. A camera toggled mid-call changes a tile from
            // video to avatar and back, and the pop-out has to follow - building at open time
            // froze it at whatever the call looked like the moment it popped out.
            function renderTiles() {
                var streams = readSources();
                // A signature over what would be drawn, so an unchanged call costs one string
                // compare per poll rather than rebuilding video elements (which would restart
                // playback and flicker) several times a second.
                var signature = streams.slice(0, $MAX_PIP_TILES).map(function (x) {
                    return x.id + (x.stream ? ':v' + (x.mirrored ? 'm' : '') : ':a');
                }).join('|');
                // An empty signature must never be treated as "already drawn": the window can be
                // filled before Meet has rebuilt its tiles, and caching that state would leave the
                // placeholder up for the rest of the call.
                if (signature && signature === tiles.getAttribute('data-boss-sig')) {
                    return streams.length;
                }
                tiles.setAttribute('data-boss-sig', signature);
                // removeChild, not innerHTML. Google Meet ships a Trusted Types CSP, so ANY
                // innerHTML assignment in this document throws "This document requires
                // 'TrustedHTML' assignment" - including the empty string, which is what silently
                // turned the whole pop-out white. The pop-out inherits the opener's policy, so
                // nothing built here may assign innerHTML at all.
                while (tiles.firstChild) { tiles.removeChild(tiles.firstChild); }

                // Owned by the render, never appended alongside it. Appending after the first
                // render left this sitting on top of live video for the rest of the call: the
                // signature guard early-returns while nothing changes, so nothing ever cleared it.
                if (!streams.length) {
                    var empty = doc.createElement('div');
                    empty.textContent = 'Waiting for video';
                    empty.setAttribute(
                        'style',
                        'grid-column:1/-1;display:flex;align-items:center;justify-content:center;' +
                            'color:#9aa0a6;font:13px system-ui'
                    );
                    tiles.appendChild(empty);
                }

                for (var j = 0; j < streams.length && j < $MAX_PIP_TILES; j++) {
                    var card = doc.createElement('div');
                    card.setAttribute(
                        'style',
                        'position:relative;background:#3c4043;border-radius:10px;overflow:hidden;' +
                            'min-height:0;display:flex;align-items:center;justify-content:center'
                    );
                    if (streams[j].stream) {
                        var tile = doc.createElement('video');
                        tile.autoplay = true;
                        tile.playsInline = true;
                        tile.muted = true;
                        tile.srcObject = streams[j].stream;
                        tile.setAttribute('style', 'width:100%;height:100%;background:#000;border-radius:10px');
                        // contain, not cover: a browser letterboxes a pop-out rather than cropping
                        // heads out of frame, and a small window crops hard.
                        tile.style.setProperty('object-fit', 'contain', 'important');
                        // Set either way, and as important: Meet's stylesheets are in this window
                        // and one of them mirrors `video`, which is right for a self-view and
                        // wrong for everyone else. An inline style attribute loses to a rule.
                        var mirror = streams[j].mirrored ? 'scaleX(-1)' : 'none';
                        tile.style.setProperty('transform', mirror, 'important');
                        tile.style.setProperty('-webkit-transform', mirror, 'important');
                        card.appendChild(tile);
                    } else {
                        var face = streams[j].avatar.cloneNode(true);
                        face.setAttribute(
                            'style',
                            'width:64px;height:64px;border-radius:50%;object-fit:cover'
                        );
                        card.appendChild(face);
                    }
                    tiles.appendChild(card);
                }
                return streams.length;
            }

            // Deliberately NOT an early return when there is nothing to draw yet. Meet tears its
            // tiles down while the tab is hidden and rebuilds them a moment later, so a fill that
            // ran during that gap used to abort here - before the control bar, the sync loop or
            // the body append - leaving a permanently white window with no way to recover. The
            // shell is built either way and the sync below fills it the moment tiles exist.
            renderTiles();

            var bar = doc.createElement('div');
                bar.setAttribute(
                    'style',
                    'display:flex;gap:8px;justify-content:center;align-items:center;' +
                        'padding:10px 8px 12px;background:#202124'
                );
                root.appendChild(bar);

                // The strip: mic, camera, present, hang up.
                // Mic, camera and hang up work exactly as Chrome's do: the button fires the
                // media-session action Meet registered (togglemicrophone/togglecamera/hangup),
                // captured at document start, and Meet mutes itself. Present and more have no
                // media-session action and goes through the host as real input: the share
                // picker is a host-level dialog, so it is visible from any tab. There is
                // deliberately no More button - in Chrome that menu exists because MEET builds
                // the PiP window and renders the menu inside it; here the click could only open
                // the menu in the hidden tab, where nobody can see it. Measured, not assumed:
                // the host fallback clicked it fine and nothing user-visible happened.
                var controls = [
                    { key: 'mic', action: 'togglemicrophone', match: /microphone/i,
                      onIcon: 'mic', offIcon: 'mic_off', slot: 'micActive', danger: false },
                    { key: 'cam', action: 'togglecamera', match: /camera/i,
                      onIcon: 'videocam', offIcon: 'videocam_off', slot: 'camActive', danger: false },
                    // Icons are the ligatures Meet's own buttons were measured using
                    // (computer_arrow_up on Share screen), so the glyphs exist in the copied font.
                    { key: 'present', match: /present now|share screen|presenting/i,
                      onIcon: 'computer_arrow_up', danger: false },
                    { key: 'leave', action: 'hangup', match: /leave call|hang up/i,
                      onIcon: 'call_end', offIcon: 'call_end', danger: true }
                ];
                function pipLog(line) {
                    var l = window.__bossPipLog = window.__bossPipLog || [];
                    l.push(Date.now() % 100000 + ' ' + line);
                    if (l.length > 40) l.shift();
                }
                function actionHandler(spec) {
                    return spec.action ? (window.__bossPipActions || {})[spec.action] : null;
                }
                // Meet's icons are ligature text in its "Google Symbols" font, whose stylesheet
                // Meet already copied into this window. Named by font-family, not by Meet's
                // class names, which are obfuscated and change.
                function makeIcon(name) {
                    var i = doc.createElement('span');
                    i.textContent = name;
                    i.style.cssText = "font-family:'Google Symbols','Material Symbols Outlined';" +
                        'font-size:18px;line-height:1';
                    return i;
                }
                var live = [];
                for (var c = 0; c < controls.length; c++) {
                    (function (spec) {
                        var source = findButton(spec.match);
                        // A media-session control exists when its HANDLER does - Meet drops the
                        // control bar from the DOM while the tab is hidden, which is exactly
                        // when this window is on screen, so requiring the page button here would
                        // build a pop-out with no mic control most of the time.
                        if (!actionHandler(spec) && !source) return;
                        var btn = doc.createElement('button');
                        btn.title = (source && source.getAttribute('aria-label')) || spec.key;
                        btn.setAttribute('aria-label', btn.title);
                        // Hang up is a wider pill, the rest are circles - Chrome's shape.
                        btn.setAttribute(
                            'style',
                            (spec.danger ? 'width:56px;border-radius:18px;' : 'width:36px;border-radius:50%;') +
                                'height:36px;border:0;cursor:pointer;' +
                                'display:flex;align-items:center;justify-content:center;' +
                                'font-size:18px;line-height:1;padding:0'
                        );
                        btn.appendChild(makeIcon(spec.onIcon || 'more_vert'));
                        btn.onclick = function () {
                            var handler = actionHandler(spec);
                            if (handler) {
                                // Chrome's own path: DidReceiveAction invokes the site's handler
                                // with the action name. Meet takes it from there.
                                pipLog('invoke ' + spec.action);
                                try {
                                    handler({ action: spec.action });
                                } catch (e) {
                                    pipLog('handler threw ' + e.name + ': ' + e.message);
                                }
                                return;
                            }
                            // No handler registered (present/more, or a page without media
                            // session): the host performs it as real input, because a synthetic
                            // click was measured doing nothing on Meet.
                            pipLog('host fallback ' + spec.key);
                            window.__bossPipAction = spec.key;
                        };
                        bar.appendChild(btn);
                        live.push({ spec: spec, btn: btn });
                    })(controls[c]);
                }

                // State, Chrome's way: Meet reports mute through setMicrophoneActive and
                // setCameraActive - captured at document start - which is what flips the
                // crossed-out icon in Chrome's PiP. The control bar is NOT scraped for state:
                // Meet drops it from the DOM while the tab is hidden, so a scraped icon froze
                // the moment the pop-out appeared (if (!source) continue skipped every tick).
                function sync() {
                    // Tiles as well as buttons: a camera toggled mid-call has to change the tile,
                    // not only the icon on the button that toggled it.
                    renderTiles();
                    var state = window.__bossPipMediaState || {};
                    for (var k = 0; k < live.length; k++) {
                        var entry = live[k];
                        var spec = entry.spec;
                        var active = spec.slot ? state[spec.slot] : undefined;
                        if (active === undefined) {
                            // Never reported: fall back to the page button when it exists.
                            var source = findButton(spec.match);
                            if (source) active = source.getAttribute('data-is-muted') !== 'true';
                        }
                        if (spec.onIcon && active !== undefined) {
                            var want = active ? spec.onIcon : (spec.offIcon || spec.onIcon);
                            var icon = entry.btn.firstElementChild;
                            if (icon && icon.textContent !== want) icon.textContent = want;
                        }
                        if (spec.danger) {
                            entry.btn.style.background = '#d93025';
                            entry.btn.style.color = '#fff';
                        } else {
                            var muted = active === false;
                            entry.btn.style.background = muted ? '#f9dedc' : '#3c4043';
                            entry.btn.style.color = muted ? '#8c1d18' : '#e8eaed';
                        }
                    }
                }
                // Guarded, because setInterval drops a callback that throws and never calls it
                // again - one bad tick would freeze the pop-out for the rest of the call with no
                // error anywhere the user could see.
                function safeSync() {
                    try {
                        sync();
                    } catch (e) {
                        window.__bossPip.syncError = e.name + ': ' + e.message;
                    }
                }
                safeSync();
                var timer = w.setInterval(safeSync, $PIP_SYNC_MS);
                w.addEventListener('pagehide', function () { w.clearInterval(timer); });

                doc.body.style.cssText = 'margin:0;overflow:hidden;background:#202124';
                doc.body.appendChild(root);
                return 'populated ' + Math.min(streams.length, $MAX_PIP_TILES) + ' tile(s), ' +
                    live.length + ' control(s)';
            }
            """.trimIndent()
                .replace("${'$'}MAX_PIP_TILES", MAX_PIP_TILES.toString())
                .replace("${'$'}PIP_SYNC_MS", PIP_SYNC_MS.toString())

    /** The same routine as a standalone call, kept as the host-side safety net. */
    val populateCallPictureInPicture = fillPopOutFunction + "\n__bossFillPip()"

    /** How many participant tiles a pop-out shows before it stops being readable. */
    const val MAX_PIP_TILES = 4

    /** How often the pop-out's buttons re-read Meet's own mute state. */
    const val PIP_SYNC_MS = 500
}
