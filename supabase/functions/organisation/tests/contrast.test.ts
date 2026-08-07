/**
 * Every colour pair the pages actually paint must clear its WCAG floor.
 *
 * This exists because looking at the page is not enough. The light theme was
 * screenshotted, read as fine, and had six sub-AA pairs in it: hint text at
 * 2.54:1, the admin pill at 4.27:1, and both banners around 4.1:1. None of that
 * is visible to a person who already knows what the words say.
 *
 * The tokens are parsed out of the real stylesheet rather than duplicated here.
 * A copy would drift, and a test that agrees with a stale copy of the thing it
 * guards is worse than no test.
 *
 * THE BINDING SURFACE IS OFTEN NOT THE OBVIOUS ONE. Banner text sits on a 10%
 * status tint, which on a light theme is DARKER than the card behind it, so
 * solving against the card left both banners failing. Each pair below names the
 * surface the text really lands on.
 */

import { assert } from "@std/assert"

const STYLESHEET = await Deno.readTextFile(new URL("../views/layout.ts", import.meta.url))

const TEXT_FLOOR = 4.5
/** WCAG 1.4.11: a UI component boundary, not text. */
const UI_FLOOR = 3.0

function tokensIn(block: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [, name, hex] of block.matchAll(/--([a-z0-9-]+):\s*(#[0-9A-Fa-f]{6})/g)) {
    out[name] = hex
  }
  return out
}

/** The `:root` block, and the light block layered over it as the cascade does. */
function palettes(): { dark: Record<string, string>; light: Record<string, string> } {
  const dark = tokensIn(STYLESHEET.split(":root {")[1].split("}")[0])
  const lightOnly = tokensIn(STYLESHEET.split("prefers-color-scheme: light")[1].split("}")[0])
  return { dark, light: { ...dark, ...lightOnly } }
}

function channel(value: number): number {
  const c = value / 255
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

function luminance(hex: string): number {
  const h = hex.replace("#", "")
  const [r, g, b] = [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16))
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

function contrast(a: string, b: string): number {
  const [la, lb] = [luminance(a), luminance(b)]
  const [hi, lo] = la > lb ? [la, lb] : [lb, la]
  return (hi + 0.05) / (lo + 0.05)
}

/** Flatten an rgba() fill onto its backdrop, the way the browser composites it. */
function over(fill: string, alpha: number, backdrop: string): string {
  const parse = (hex: string) =>
    [0, 2, 4].map((i) => parseInt(hex.replace("#", "").slice(i, i + 2), 16))
  const [f, b] = [parse(fill), parse(backdrop)]
  const mixed = f.map((v, i) => Math.round(v * alpha + b[i] * (1 - alpha)))
  return "#" + mixed.map((v) => v.toString(16).padStart(2, "0")).join("")
}

/** The status tints used by `.banner.ok` / `.banner.error`, at their real alpha. */
const OK_TINT = "#2FD98A"
const ERROR_TINT = "#FF5D5D"

interface Pair {
  what: string
  fg: string
  bg: (t: Record<string, string>) => string
  floor?: number
}

const PAIRS: Pair[] = [
  { what: "body text on the page", fg: "text", bg: (t) => t.ink },
  { what: "body text on a card", fg: "text", bg: (t) => t.raised },
  // Hints, table headers, empty states and the footer all use this. It replaced
  // the app's textMuted, which is 2.54:1 on a white card - fine as a label beside
  // a value in a 300px panel, not fine as the only text in a paragraph.
  { what: "hints and headers on a card", fg: "text-2", bg: (t) => t.raised },
  { what: "hints and headers on the page", fg: "text-2", bg: (t) => t.ink },
  { what: "a link on the page", fg: "signal-text", bg: (t) => t.ink },
  { what: "a link on a card", fg: "signal-text", bg: (t) => t.raised },
  { what: "the admin pill's glyph", fg: "signal-on-wash", bg: (t) => t["signal-wash"] },
  { what: "a primary button's label", fg: "on-signal", bg: (t) => t.signal },
  { what: "an ok pill on a card", fg: "ok-text", bg: (t) => t.raised },
  { what: "a warn pill on a card", fg: "warn-text", bg: (t) => t.raised },
  { what: "a danger button's label", fg: "alert-text", bg: (t) => t.raised },
  { what: "ok banner text on its tint", fg: "ok-text", bg: (t) => over(OK_TINT, 0.10, t.raised) },
  {
    what: "error banner text on its tint",
    fg: "alert-text",
    bg: (t) => over(ERROR_TINT, 0.10, t.raised),
  },
  // Not text: `signal` is a fill and a border. It is 3.8:1 on ink BY DESIGN, which
  // is why `signal-text` exists at all. Holding it to 4.5 would "fix" it by
  // brightening the brand blue, which is the wrong repair.
  { what: "signal as a control border", fg: "signal", bg: (t) => t.ink, floor: UI_FLOOR },
]

for (const [themeName, theme] of Object.entries(palettes())) {
  for (const pair of PAIRS) {
    Deno.test(`${themeName}: ${pair.what} clears its floor`, () => {
      const fg = theme[pair.fg]
      assert(fg, `no --${pair.fg} token in the ${themeName} palette`)
      const bg = pair.bg(theme)
      const floor = pair.floor ?? TEXT_FLOOR
      const ratio = contrast(fg, bg)
      assert(
        ratio >= floor,
        `${themeName} ${pair.what}: ${fg} on ${bg} is ${ratio.toFixed(2)}:1, needs ${floor}:1`,
      )
    })
  }
}

Deno.test("the light palette really does override the status text colours", () => {
  // A guard against the pairs above passing vacuously. If the light block stopped
  // redefining these, every light assertion would silently re-check the dark
  // values and still pass - the shape of vacuous test this suite has been bitten
  // by before.
  const { dark, light } = palettes()
  for (const token of ["ok-text", "warn-text", "alert-text", "signal-on-wash", "text-2", "ink"]) {
    assert(
      dark[token] !== light[token],
      `--${token} is identical in both palettes, so the light assertions prove nothing about it`,
    )
  }
})
