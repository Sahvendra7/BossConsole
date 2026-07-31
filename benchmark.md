# Speedometer 3.1 across browsers, including BOSS's fluck browser

Benchmark: [Speedometer 3.1](https://browserbench.org/Speedometer3.1/) (browserbench.org, run against the live site — not a local mirror)
Date: 2026-07-30
Machine: MacBook Pro `Mac15,8`, Apple M3 Max (12 performance + 4 efficiency cores), 128 GB, macOS 26.5.2 (25F84), on AC power at 100%

**Speedometer 3's score is "iterations per minute" — higher is better.** Each run used
`iterationCount=10` and `startAutomatically=1`, so every browser ran the identical
workload via the identical URL.

Every number below is the **median of 3 independent runs**. Read the
[Caveats](#caveats-read-before-quoting-these-numbers) before quoting any of it: this
machine was **not** quiet, and one browser's figure is not directly comparable to the rest.

## Results

| Browser | Version | Engine | Runs | **Median** | Spread | Method |
|---|---|---|---:|---:|---:|---|
| **BOSS fluck browser** | BOSS 9.2.63 | — | 47.9 / 47.5 / 48.4 | **47.9** | 1.9% | MCP `browser_run_js` |
| Comet | 150.0.7871.228 | Chromium 150 | 45.9 / 46.2 / 46.5 | **46.2** | 1.3% | CDP |
| Google Chrome | 151.0.7922.71 | Chromium 151 | 36.2 / 35.3 / 35.5 | **35.5** | 2.5% | CDP |
| ChatGPT Atlas | 1.2026.189.1 | Chromium 150.0.7871.115 | 34.8 / 34.6 / 34.4 | **34.6** | 1.2% | screenshot |
| Safari | 26.5.2 | WebKit 626 | 28.8 / 29.9 / 29.9 | **29.9** ⚠️ | 3.7% | screenshot |
| Firefox | 153.0.1 | Gecko / SpiderMonkey | 22.5 / 30.9 / 22.0 | **22.5** | **40%** | WebDriver |

⚠️ Safari's number is **not comparable** to the others — see [Caveats](#caveats-read-before-quoting-these-numbers).

### Not benchmarked

| Target | Why not |
|---|---|
| **Dia** 1.42.1 | Installed (WebKit-based, links `/System/Library/Frameworks/WebKit.framework`). Blocked on a one-time first-run onboarding — it plays an intro and never loads the requested URL until a human completes sign-in. Needs an account, so it was not automated. |
| **Antigravity** 1.11.5 | Not a browser. It is a VS Code fork (an IDE). Its "browser" feature drives a separate real Chrome install via an extension, so benchmarking it would just re-measure Chrome. |
| **Codex** | Not a browser. `/opt/homebrew/bin/codex` is OpenAI's CLI — no browser engine, nothing to run a web benchmark in. |

## The headline finding: the engine version barely matters here

Comet and ChatGPT Atlas ship **the same Chromium 150 build family** — yet they differ
by 34%:

| Embedder | Chromium | Median |
|---|---|---:|
| Comet | 150.0.7871.228 | 46.2 |
| ChatGPT Atlas | 150.0.7871.115 | 34.6 |

Two browsers, essentially one engine build (`.228` vs `.115`), a third of the score
apart — with Chrome 151 landing between them at 35.5. Whatever separates these
browsers on Speedometer 3.1, it is **not** the engine version; it is how each product
configures and wraps the engine it ships. That is the main reason a score here should
be read as a property of the *browser*, not of its underlying engine.

### Where Chrome loses to Comet: DOM mutation, not rendering

Comparing per-suite means from `chrome-run2.json` and `comet-run2.json` (ratio > 1
means Chrome is slower):

| Suite | Chrome ms | Comet ms | Ratio |
|---|---:|---:|---:|
| TodoMVC-WebComponents | 19.59 | 10.36 | **1.89** |
| TodoMVC-jQuery | 114.14 | 62.33 | **1.83** |
| TodoMVC-Preact-Complex-DOM | 14.24 | 7.78 | **1.83** |
| TodoMVC-JavaScript-ES6-Webpack | 37.20 | 24.23 | 1.54 |
| TodoMVC-Vue | 20.50 | 13.46 | 1.52 |
| TodoMVC-Lit-Complex-DOM | 14.46 | 9.96 | 1.45 |
| Editor-TipTap | 66.89 | 46.95 | 1.42 |
| … | | | |
| React-Stockcharts-SVG | 50.18 | 47.52 | 1.06 |
| Perf-Dashboard | 27.62 | 26.03 | 1.06 |
| Charts-chartjs (canvas) | 28.79 | 27.63 | **1.04** |

The gap is **concentrated in DOM-mutation / style-recalc workloads** and nearly absent
in canvas rendering (`Charts-chartjs`, 1.04×), SVG (1.06×), and page navigation
(`NewsSite-Next` 1.07×, `NewsSite-Nuxt` 1.08×). This is a specific signature, not a
uniform slowdown — which is what you would expect from a feature/configuration
difference in the DOM path rather than a slower CPU or a slower JIT.

### What was ruled out

- **Chrome's field-trial / variations config — REFUTED.** Re-running Chrome with
  `--disable-field-trial-config --disable-features=OptimizationHints` scored **35.1**,
  statistically identical to 35.5 with them enabled. Experiment groups do not explain
  Chrome's position.
- **Chromium version — REFUTED.** Atlas (150.0.7871.115) and Comet (150.0.7871.228)
  differ by 34% on the same build family.
- **Background CPU load — ruled out for the Chrome/Comet gap specifically.** Median
  ambient CPU recorded during the runs was comparable: Chrome 843/858/1138%,
  Comet 912/861/878%. Chrome was not measured under a heavier machine.

**The cause is not established.** The evidence narrows it to embedder configuration
affecting the DOM path (candidates: site-isolation and sandboxing policy, injected
agent/automation instrumentation, or differing Blink feature flags), but this
benchmark did not isolate which. Do not quote a cause from this document.

## Caveats — read before quoting these numbers

1. **The machine was under heavy competing load.** Ambient CPU during the runs was
   **800–1300%** (8–13 of 16 cores) — Docker Desktop's Linux VM alone held ~400%,
   plus endpoint-security agents (~218%) and Spotlight/Siri indexing (~105%).
   **All absolute scores here are depressed** and should not be compared to published
   browserbench numbers from a quiet machine. Relative comparison is the usable signal.
2. **Safari ran with a real profile; the Chromium browsers did not.** Chrome, Comet,
   and Atlas each got a fresh throwaway `--user-data-dir` (no extensions, no tabs,
   no prior state). Safari was measured with the operator's live profile — roughly 14
   background tabs and installed extensions. That handicap is unquantified and Safari's
   29.9 is very likely an **underestimate** of WebKit on this hardware. Safari's figure
   is not comparable to the fresh-profile numbers, and it is the one row in the table
   that should not be used to rank engines.
3. **Firefox has 40% run-to-run spread** (22.0–30.9) where every Chromium browser held
   within 2.5% at the same ambient load. Its median is the least trustworthy number in
   the table; three runs are not enough for Gecko here.
4. **Run order was interleaved, deliberately.** Runs go round-robin (run 1 of every
   browser, then run 2, …) rather than three-in-a-row per browser. An earlier
   grouped-by-browser sweep produced badly skewed results — Firefox read 23.1 → 18.0 →
   15.8 and Comet 45.2 → 36.0 purely from load drift and a leaked process landing on
   whichever browser ran last. Those numbers were discarded, not reported.
5. **Screenshot-derived scores are transcribed by eye.** Safari and Atlas expose no
   automation endpoint, so their scores were read off Speedometer's own result screen
   (cropped images under `results-final/cropped/`) with no programmatic value to
   cross-check against. Chrome, Comet, Firefox, and fluck numbers came from reading
   `#result-number` directly out of the live DOM.
6. **Speedometer reported `valid: true` for every run counted here**, and every viewport
   exceeded Speedometer's 850×650 minimum (1280×783 Chrome, 1270×780 Comet, 1280×786
   Firefox, 1432×729 fluck).

## How to reproduce

All scripts live in [`benchmarks/speedometer/`](benchmarks/speedometer/). They are
dependency-free — Node ≥21 (for the built-in global `WebSocket`) plus `geckodriver` for
Firefox.

```bash
cd benchmarks/speedometer
brew install geckodriver                     # Firefox only

# Full interleaved sweep, 3 runs per browser
RESULTS=results-final REPEATS=3 ITERATIONS=10 \
  BROWSERS="chrome comet firefox safari atlas" ./run-all.sh
```

| Script | Role |
|---|---|
| [`run-all.sh`](benchmarks/speedometer/run-all.sh) | Orchestrator. Round-robin interleaving, `REPEATS`/`ITERATIONS`/`BROWSERS` knobs. |
| [`run-chromium.mjs`](benchmarks/speedometer/run-chromium.mjs) | Chrome, Comet. Drives CDP over Node's built-in `WebSocket`; fresh profile per run; records ambient CPU; verifies the browser actually died before returning. |
| [`run-webdriver.mjs`](benchmarks/speedometer/run-webdriver.mjs) | Firefox via `geckodriver` (W3C WebDriver is plain HTTP + JSON, so `fetch` suffices). Also supports Safari if you run `sudo safaridriver --enable`. |
| [`run-screenshot.mjs`](benchmarks/speedometer/run-screenshot.mjs) | Safari, Atlas, Dia — browsers with no automation endpoint. Detects run completion from CPU falling back toward a pre-launch baseline, then captures the result screen. |
| [`crop-score.sh`](benchmarks/speedometer/crop-score.sh) | Crops a full-screen capture to the score card. |

Raw per-run JSON (including 197–206 per-suite metrics for the CDP/WebDriver browsers)
is in `results-final/`. `fluck.json` holds the fluck browser's full run.

### Why the fluck browser is driven differently

The fluck browser is a tab inside BOSS, not a separate process, so it is driven through
the host's own MCP tools (`tab_focus`, `browser_navigate`, `browser_run_js`) rather than
by `run-all.sh`.

One non-obvious requirement: **the fluck tab must be the active tab, with BOSS
frontmost, for the whole run.** Speedometer 3.1 measures with `requestAnimationFrame`
by default, and a background BOSS tab reports `document.hidden === true`, which throttles
rAF to a crawl — an early attempt sat at 11/580 steps after 40 seconds. Verify before
trusting a run:

```js
// must report hidden:false
JSON.stringify({ hidden: document.hidden, vw: innerWidth, vh: innerHeight })
```

## Harness bugs found and fixed while building this

Recorded because each one silently produced plausible-looking wrong numbers:

- **Leaked browser processes poisoned later runs.** A failed Comet run left its process
  alive; the next run then contended with it and read 36.0 instead of ~46 — which looked
  exactly like "Comet is as slow as Chrome". `run-chromium.mjs` now polls until no
  process matching the run's profile directory remains.
- **Absolute CPU-idle threshold hung Safari.** Matching `com.apple.WebKit` catches every
  WKWebView app on the machine, so "idle means < 15%" never fired while an unrelated chat
  app was busy, and the run would have spun to its 45-minute timeout. Now measured
  relative to a pre-launch baseline, with a hard 180s cap.
- **A command-line URL is only a hint.** Comet intermittently showed its own startup page
  and dropped the benchmark URL. The runner now falls back to `Target.createTarget` over CDP.
- **Ambient CPU counted the browser's own helpers.** Excluding by main-binary path missed
  Chromium's renderer/GPU processes under `Contents/Frameworks/…`, inflating "ambient".
  Now excluded by `.app` bundle.
- **`sips` exits 0 on a missing input.** `crop-score.sh` reported "wrote …" having produced
  nothing. It now checks the input exists and the output is non-empty.

## Notes for the operator

- **ChatGPT Atlas disappeared mid-session.** Atlas 1.2025.288.14 was present at the start
  (its `Info.plist` read `com.openai.atlas`), then vanished from `/Applications` — bundle
  and `Application Support` both gone, nothing in Trash, no unified-log evidence. It went
  missing after being launched with `--remote-debugging-port`, a pattern EDR products flag
  as browser credential theft, and this machine runs managed endpoint-security/EDR
  agents. Cause
  unproven. It was reinstalled from the `chatgpt-atlas` Homebrew cask as **1.2026.189.1**
  (newer than what was there), and that is the version benchmarked.
- **Atlas has no usable `--remote-debugging-port`** — the outer bundle is a Swift/SwiftUI
  shell and the engine is a *nested* app at `Contents/Support/ChatGPT Atlas.app`, so the
  flag never reaches Chromium.
- **Four orphaned `WebKit.WebContent` processes** (parented to `launchd`, Safari not
  running, PIDs long predating this session) were burning ~180% CPU during the early runs.
  They exited on their own before the final sweep.
- Newly installed for this benchmark: `firefox`, `comet`, `geckodriver`, `chatgpt-atlas`
  (Homebrew), and Dia 1.42.1 from `releases.diabrowser.com`.
