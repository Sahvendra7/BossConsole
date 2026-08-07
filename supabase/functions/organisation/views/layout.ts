/**
 * Page chrome shared by every view.
 *
 * The stylesheet is inline and nonce-stamped rather than a separate file: the
 * CSP is `default-src 'none'` with no `style-src 'self'`, and an edge function
 * has no static asset route to serve one from anyway.
 *
 * ## The palette is BOSS Blueprint, and it is a mirror
 *
 * Values are copied from `BossBlueprintColorScheme` /
 * `BossBlueprintLightColorScheme` in
 * `plugin-platform/plugin-ui-core/.../ui/BossThemes.kt`, which is the source of
 * truth. These pages are opened from the desktop panel, so a page in the old
 * amber identity next to a Blueprint app reads as a different product. If the
 * app palette moves, move these with it.
 *
 * SIGNAL IS THE FILL, SIGNALTEXT IS THE GLYPH. `--signal` (#0F5BFF) is 3.5:1 on
 * ink: enough for a control's edge or a filled button, not enough for text. Any
 * accent-coloured WORD uses `--signal-text` (#88A9FF, 8.1:1). The previous
 * stylesheet had `.linkish` and `.pill.admin` painting the accent as text, which
 * is the same regression the app corrected across ~90 call sites.
 *
 * ## Light mode is not decoration here
 *
 * The in-app browser bridges the host theme to `prefers-color-scheme`
 * (`FluckEngine` calls `engine.setTheme` from `BossThemeController`), so a page
 * that honours the media query tracks the app the user is looking at. Without
 * the light half, someone running Blueprint Light gets a dark page inside a
 * light app.
 */

import { esc } from "../utils/html.ts"

const STYLES = `
  :root {
    --ink: #05070B;
    --raised: #0E141E;
    --line: #1C2432;
    --line-strong: #2E3B4F;
    --text: #E7EDFA;
    --text-2: #9AA7BB;
    --signal: #0F5BFF;
    --signal-dim: #0A45C4;
    --signal-wash: #0A1A3C;
    --signal-text: #88A9FF;
    /* Kept because it is used as a BORDER (danger hover). Its ok/warn siblings
       were removed: nothing painted them, and an unused vivid status colour is
       exactly what gets reached for as text. */
    --alert: #FF5D5D;
    /* Text-safe variants. On ink the status colours already clear 4.5:1, so these
       alias them; on paper they do not, which is why the pair exists at all. Same
       fill-versus-glyph split the app makes for signal, applied to status. */
    --ok-text: #2FD98A;
    --warn-text: #F0B429;
    --alert-text: #FF5D5D;
    --signal-on-wash: #88A9FF;
    --on-signal: #FFFFFF;
    --token-wash: rgba(154, 167, 187, 0.12);
    --shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
  }

  @media (prefers-color-scheme: light) {
    :root {
      --ink: #F5F7FB;
      --raised: #FFFFFF;
      --line: #DCE2EB;
      --line-strong: #A8B2C2;
      --text: #05070B;
      --text-2: #687081;
      --signal: #0F5BFF;
      --signal-dim: #0A45C4;
      --signal-wash: #DCE7FF;
      --signal-text: #0F5BFF;
      --alert: #D33B4A;
      /* Darkened until each clears 4.5:1 on EVERY light surface it lands on: the
         white card, the paper page, and the 10% status tint behind a banner. The
         tint is the binding one - it is darker than white, so solving against the
         card alone left both banners at ~4.2:1. The fills above stay as the mirror. */
      --ok-text: #188050;
      --warn-text: #996709;
      --alert-text: #C83846;
      --signal-on-wash: #0C3FBF;
      --on-signal: #FFFFFF;
      --token-wash: rgba(104, 112, 129, 0.10);
      --shadow: 0 1px 2px rgba(5, 7, 11, 0.06);
    }
  }

  * { margin: 0; padding: 0; box-sizing: border-box; }

  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    background-color: var(--ink);
    color: var(--text);
    min-height: 100vh;
    line-height: 1.55;
    padding: 32px 24px 24px;
    -webkit-font-smoothing: antialiased;
  }
  .wrap { width: 100%; max-width: 960px; margin: 0 auto; }

  /* ---- page header ---------------------------------------------------- */

  header.page { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
  header.page h1 { font-size: 28px; font-weight: 650; letter-spacing: -0.5px; line-height: 1.2; }

  /* An organisation slug is a machine identifier, validated ^[a-z][a-z0-9_]{1,30}$.
     Mono against a wash is what separates the key from the prose beside it, and it
     matches how the desktop panel renders the same value. */
  .slug {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 13px; color: var(--text-2);
    background-color: var(--token-wash);
    border-radius: 5px; padding: 2px 7px;
  }
  .sub { color: var(--text-2); font-size: 15px; margin-bottom: 26px; max-width: 68ch; }

  /* ---- tabs ------------------------------------------------------------ */

  .tabs { display: flex; gap: 6px; margin-bottom: 22px; flex-wrap: wrap; }
  .tabs a {
    color: var(--text-2); text-decoration: none; font-size: 14px; padding: 7px 15px;
    border: 1px solid var(--line); border-radius: 7px; background-color: var(--raised);
  }
  .tabs a:hover { color: var(--text); border-color: var(--line-strong); }
  /* Filled, so white sits on the signal rather than the signal sitting on ink. */
  .tabs a.active {
    color: var(--on-signal); background-color: var(--signal);
    border-color: var(--signal); font-weight: 600;
  }
  .tabs a.active:hover { color: var(--on-signal); background-color: var(--signal-dim); }

  /* ---- cards ----------------------------------------------------------- */

  section.card {
    background-color: var(--raised); border: 1px solid var(--line); border-radius: 10px;
    padding: 22px; margin-bottom: 16px; box-shadow: var(--shadow);
    /* Tables scroll inside their own card rather than widening the page. The old
       rule hid every column past the third below 620px, which did not make the
       table fit - it deleted "Joined" and "Permissions" from the reader's view. */
    overflow-x: auto;
  }
  /* A real heading, not the uppercase micro-label the desktop panel uses. The
     admin page has six substantial sections on a 960px canvas; an eyebrow is the
     right weight for a 300px sidebar and too quiet to structure a page. */
  section.card > h2 {
    font-size: 16px; font-weight: 650; margin-bottom: 5px;
    letter-spacing: -0.2px; color: var(--text);
  }
  section.card > p.hint { color: var(--text-2); font-size: 13px; margin-bottom: 18px; max-width: 68ch; }

  /* ---- tables ---------------------------------------------------------- */

  table { width: 100%; border-collapse: collapse; font-size: 14px; }
  th {
    text-align: left; color: var(--text-2); font-weight: 600; font-size: 11px;
    text-transform: uppercase; letter-spacing: 0.6px; white-space: nowrap;
    padding: 0 14px 8px 0; border-bottom: 1px solid var(--line-strong);
  }
  td { padding: 11px 14px 11px 0; border-bottom: 1px solid var(--line); vertical-align: middle; }
  tr:last-child td { border-bottom: none; }
  tbody tr:hover td { background-color: var(--token-wash); }

  /* ---- pills and identifiers ------------------------------------------- */

  .pill {
    display: inline-block; font-size: 11px; padding: 2px 8px; border-radius: 5px;
    border: 1px solid var(--line-strong); color: var(--text-2);
    margin: 1px 4px 1px 0; white-space: nowrap;
  }
  /* A wash behind readable text, never the accent AS text. */
  .pill.admin {
    border-color: rgba(15, 91, 255, 0.45); background-color: var(--signal-wash);
    color: var(--signal-on-wash); font-weight: 600;
  }
  .pill.ok { border-color: rgba(47, 217, 138, 0.45); color: var(--ok-text); }
  .pill.warn { border-color: rgba(240, 180, 41, 0.45); color: var(--warn-text); }
  .mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
  }
  /* A mono pill is an identifier, not a status, so it drops the outline and
     takes a neutral wash - a row of outlined boxes shouts louder than the words. */
  .pill.mono { border-color: transparent; background-color: var(--token-wash); color: var(--text-2); }

  /* ---- forms ----------------------------------------------------------- */

  label { display: block; font-size: 13px; color: var(--text-2); margin-bottom: 6px; }
  input[type=text], input[type=number], select, textarea {
    width: 100%; background-color: var(--ink); color: var(--text);
    border: 1px solid var(--line-strong); border-radius: 7px; padding: 9px 11px;
    font-size: 14px; font-family: inherit;
  }
  input::placeholder, textarea::placeholder { color: var(--text-2); }
  input:hover, select:hover, textarea:hover { border-color: var(--signal-text); }
  /* focus-visible, so a mouse click does not paint a ring the keyboard user needs. */
  input:focus-visible, select:focus-visible, textarea:focus-visible,
  button:focus-visible, a:focus-visible {
    outline: 2px solid var(--signal); outline-offset: 2px;
  }
  /* Inside a table cell a control sizes to its content. The page-level width:100%
     made the role select fill the cell, which pushed its own Assign button onto a
     second line and left Remove floating beside the pair at a different height. */
  td select, td input[type=text], td input[type=number] { width: auto; min-width: 150px; }
  input[type=checkbox] { accent-color: var(--signal); width: 15px; height: 15px; }
  .row { display: flex; gap: 14px; flex-wrap: wrap; margin-bottom: 16px; }
  .row > div { flex: 1 1 200px; }
  .checkline { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
  .checkline label { margin: 0; }

  /* ---- buttons --------------------------------------------------------- */

  button {
    background-color: var(--signal); color: var(--on-signal); border: 1px solid var(--signal);
    border-radius: 7px; padding: 8px 16px; font-size: 14px; font-weight: 600;
    cursor: pointer; font-family: inherit;
  }
  button:hover { background-color: var(--signal-dim); border-color: var(--signal-dim); }
  button:active { transform: translateY(1px); }
  button.secondary {
    background-color: transparent; color: var(--text-2); border-color: var(--line-strong);
    font-weight: 500;
  }
  button.secondary:hover { background-color: var(--token-wash); color: var(--text); border-color: var(--line-strong); }
  button.danger {
    background-color: transparent; color: var(--alert-text);
    border-color: rgba(255, 93, 93, 0.45); font-weight: 500;
  }
  button.danger:hover { background-color: rgba(255, 93, 93, 0.12); border-color: var(--alert); }
  /* inline-flex, not inline: a form holding a select AND a button needs the two to
     stay on one line and share a gap. Two adjacent forms in one cell (approve and
     reject) get the margin, since flex gap does not reach across siblings. */
  form.inline { display: inline-flex; align-items: center; gap: 8px; vertical-align: middle; }
  form.inline + form.inline { margin-left: 8px; }

  /* ---- banners --------------------------------------------------------- */

  .banner {
    border-radius: 8px; padding: 12px 15px; margin-bottom: 18px; font-size: 14px;
    border: 1px solid transparent;
  }
  .banner.error { background-color: rgba(255, 93, 93, 0.10); border-color: rgba(255, 93, 93, 0.45); color: var(--alert-text); }
  .banner.ok { background-color: rgba(47, 217, 138, 0.10); border-color: rgba(47, 217, 138, 0.45); color: var(--ok-text); }

  /* ---- stats ----------------------------------------------------------- */

  /* inline-block with a bottom margin, so a row of five wraps onto a second line
     instead of running past the card edge. There are up to five, and "joining"
     carries values like "request to join", so the overflow was reachable rather
     than theoretical. Top-aligned, or a wrapped row hangs off the tallest value. */
  .stat { display: inline-block; margin: 0 30px 8px 0; vertical-align: top; }
  .stat b {
    display: block; font-size: 21px; font-weight: 650; letter-spacing: -0.3px;
    font-variant-numeric: tabular-nums;
  }
  .stat.phrase b { font-size: 15px; font-weight: 600; letter-spacing: 0; line-height: 1.75; }
  .stat span {
    color: var(--text-2); font-size: 11px; text-transform: uppercase; letter-spacing: 0.6px;
  }

  /* ---- misc ------------------------------------------------------------ */

  .empty { color: var(--text-2); font-size: 14px; padding: 10px 0; }
  footer.page {
    color: var(--text-2); font-size: 12px; margin-top: 34px; padding-top: 16px;
    border-top: 1px solid var(--line); text-align: center;
  }
  a { color: var(--signal-text); }

  /* Utility classes, because every inline style= attribute is DROPPED by our own
     CSP: style-src is nonce-only, CSP3 falls style-src-attr back to it, and a
     nonce cannot apply to an attribute. Inline styles rendered nothing and logged
     a violation on every page. */
  .spaced { margin-top: 16px; }
  .tight { margin-top: 12px; }
  .highlight { border-color: var(--signal); }
  .linkish { color: var(--signal-text); }

  @media (max-width: 620px) {
    body { padding: 18px 14px 14px; }
    header.page h1 { font-size: 23px; }
    section.card { padding: 16px; }
    .stat { margin-right: 22px; }
  }

  @media (prefers-reduced-motion: reduce) {
    * { animation: none !important; transition: none !important; }
    button:active { transform: none; }
  }
`

export type BannerKind = "error" | "ok"

export interface LayoutOptions {
  title: string
  nonce: string
  body: string
  /** Rendered above the body when present. */
  banner?: { kind: BannerKind; message: string } | null
}

/**
 * Wrap page content in the document shell.
 *
 * `body` is expected to be already-escaped markup built by the caller. The
 * banner message is escaped HERE, because it is the one string that routinely
 * originates from an RPC error and would otherwise be interpolated raw.
 */
export function layout({ title, nonce, body, banner }: LayoutOptions): string {
  const bannerHtml = banner
    ? `<div class="banner ${
      banner.kind === "ok" ? "ok" : "error"
    }" role="status">${esc(banner.message)}</div>`
    : ""

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta name="referrer" content="no-referrer">
<meta name="color-scheme" content="dark light">
<title>${esc(title)}</title>
<style nonce="${esc(nonce)}">${STYLES}</style>
</head>
<body>
<div class="wrap">
${bannerHtml}
${body}
<footer class="page">BOSS Organisation</footer>
</div>
</body>
</html>`
}

/**
 * The tab strip.
 *
 * `current` selects the active tab. The admin tab is only emitted when the
 * caller says the viewer is an admin, and that flag always comes from a live
 * probe -- hiding it is cosmetic, the route re-checks regardless.
 */
export function tabs(
  basePath: string,
  slug: string,
  current: "overview" | "admin",
  isAdmin: boolean,
): string {
  const overviewHref = `${basePath}/o/${encodeURIComponent(slug)}`
  const items = [
    `<a href="${esc(overviewHref)}"${
      current === "overview" ? ' class="active" aria-current="page"' : ""
    }>Overview</a>`,
  ]
  if (isAdmin) {
    items.push(
      `<a href="${esc(overviewHref)}/admin"${
        current === "admin" ? ' class="active" aria-current="page"' : ""
      }>Configuration</a>`,
    )
  }
  return `<nav class="tabs" aria-label="Organisation sections">${items.join("")}</nav>`
}

/** A hidden CSRF field. Every mutating form must include one. */
export function csrfField(fieldName: string, token: string): string {
  return `<input type="hidden" name="${esc(fieldName)}" value="${esc(token)}">`
}
