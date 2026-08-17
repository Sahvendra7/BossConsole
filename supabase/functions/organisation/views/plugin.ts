/**
 * One plugin's page.
 *
 * Reuses the org pages' layout, so the palette, contrast work and light/dark handling are the
 * ones tests/contrast.test.ts already asserts. Nothing here introduces a colour.
 */

import { esc, scrollable } from "../utils/html.ts"
import { renderMarkdown } from "../services/markdown.ts"
import { csrfField, layout } from "./layout.ts"
import { CSRF_FIELD } from "../utils/csrf.ts"
import type { PluginDetail } from "../services/plugin.ts"

export interface PluginPageOptions {
  nonce: string
  basePath: string
  orgSlug: string
  csrf: string
  plugin: PluginDetail
  /** README text, already fetched. Null when there is none to show. */
  readme: string | null
  /** Whether to render the visibility control. The RPC re-checks regardless. */
  canEdit: boolean
  banner?: { kind: "ok" | "error"; message: string } | null
}

/** What each visibility value means, in the reader's terms rather than the column's. */
const VISIBILITY_COPY: Record<string, { label: string; detail: string }> = {
  public: {
    label: "Public",
    detail: "Anyone can find and install it, including people outside this organisation.",
  },
  org: {
    label: "Organisation only",
    detail: "Only members of this organisation can find and install it.",
  },
  unlisted: {
    label: "Unlisted",
    detail: "Hidden from the store. Only this organisation's administrators can see it.",
  },
}

export function pluginPage(options: PluginPageOptions): string {
  const { nonce, basePath, orgSlug, csrf, plugin, readme, canEdit, banner } = options
  const action = `${basePath}/o/${encodeURIComponent(orgSlug)}/plugins/${
    encodeURIComponent(plugin.plugin_id)
  }/visibility`

  return layout({
    title: `${plugin.display_name} - BOSS`,
    nonce,
    banner: banner ?? null,
    body: `
<header class="page">
  <h1>${esc(plugin.display_name)}</h1>
  <span class="slug">@${esc(orgSlug)}</span>
</header>
<p class="sub">${esc(plugin.plugin_id)}</p>

${identityCard(plugin)}
${canEdit ? visibilityCard(action, csrf, plugin) : visibilityReadOnly(plugin)}
${readmeCard(plugin, readme)}`,
  })
}

/**
 * Icon, description and the facts worth knowing before installing.
 *
 * The icon is rendered from `icon_url`, which is publisher-supplied. `img-src` in the CSP is
 * `'self' data:` - it does NOT include remote hosts - so a remote icon simply does not load and
 * the alt text stands in. That is the intended outcome rather than a gap: allowing arbitrary
 * remote images here would let a publisher log every reader of their plugin's page by URL.
 */
function identityCard(plugin: PluginDetail): string {
  const facts: Array<[string, string]> = []
  if (plugin.latest_version) facts.push(["Latest version", plugin.latest_version])
  if (plugin.type) facts.push(["Type", plugin.type])
  if (plugin.api_version) facts.push(["Plugin API", plugin.api_version])
  if (plugin.author_name) facts.push(["Published by", plugin.author_name])
  if (typeof plugin.download_count === "number") {
    facts.push(["Installs", String(plugin.download_count)])
  }

  return `
<section class="card">
  <div class="row">
    <div>
      ${
    plugin.icon_url
      ? `<img src="${esc(plugin.icon_url)}" alt="${
        esc(plugin.display_name)
      } icon" width="48" height="48">`
      : ""
  }
      <h2>${esc(plugin.display_name)}</h2>
      ${
    plugin.description
      ? `<p class="hint">${esc(plugin.description)}</p>`
      : '<p class="hint">No description.</p>'
  }
      ${
    plugin.verified ? '<span class="pill ok">verified</span>' : ""
  }${plugin.published ? "" : ' <span class="pill warn">unpublished</span>'}
    </div>
  </div>
  ${
    facts.length === 0 ? "" : scrollable(
      "Plugin details",
      `<table>
    <tbody>${
        facts.map(([k, v]) => `<tr><td>${esc(k)}</td><td class="mono">${esc(v)}</td></tr>`).join("")
      }</tbody>
  </table>`,
    )
  }
  ${
    plugin.homepage_url
      ? `<p class="hint">Source: <span class="mono">${esc(plugin.homepage_url)}</span></p>`
      : ""
  }
</section>`
}

/**
 * The visibility control, for an administrator of the owning organisation.
 *
 * Radios rather than a select, because there are exactly three and each needs a sentence: the
 * consequence of `org` and `unlisted` is not guessable from the word. It is a POST form with a
 * CSRF field like every other mutation here.
 *
 * The warning under it is the thing most worth saying: the Toolbox reads its catalogue as `anon`,
 * so anything other than `public` disappears from the store listing for EVERY reader, including
 * members of the owning organisation. That is a property of how the client reads today, not of
 * what the visibility means, and somebody flipping this switch should know it before they do.
 */
function visibilityCard(action: string, csrf: string, plugin: PluginDetail): string {
  const options = Object.entries(VISIBILITY_COPY).map(([value, copy]) => `
    <div class="checkline">
      <input type="radio" id="vis_${esc(value)}" name="visibility" value="${esc(value)}"${
    plugin.visibility === value ? " checked" : ""
  }>
      <label for="vis_${esc(value)}"><strong>${esc(copy.label)}</strong> - ${esc(copy.detail)}</label>
    </div>`).join("")

  return `
<section class="card">
  <h2>Visibility</h2>
  <p class="hint">Who can find and install this plugin.</p>
  <form method="post" action="${esc(action)}">
    ${csrfField(CSRF_FIELD, csrf)}
    ${options}
    <button type="submit">Save visibility</button>
  </form>
  <p class="hint">Anything other than Public also removes it from the Toolbox's store list, for everyone. The Toolbox reads that list anonymously, so it cannot see a plugin restricted to an organisation even for that organisation's own members.</p>
</section>`
}

/** The same fact, for somebody who may not change it. */
function visibilityReadOnly(plugin: PluginDetail): string {
  const copy = VISIBILITY_COPY[plugin.visibility] ??
    { label: plugin.visibility, detail: "" }
  return `
<section class="card">
  <h2>Visibility</h2>
  <p class="hint"><strong>${esc(copy.label)}</strong>${
    copy.detail ? ` - ${esc(copy.detail)}` : ""
  }</p>
  <p class="hint">Only this organisation's administrators can change it.</p>
</section>`
}

/**
 * The README, rendered.
 *
 * Rendered by services/markdown.ts, which escapes the source BEFORE applying any formatting, so
 * every tag below is one we wrote and nothing the README supplies can become markup. That is why
 * there is no library involved: a general Markdown renderer passes raw HTML through by design,
 * which would make the CSP the only thing between somebody else's repository and the admin control
 * on this page.
 */
function readmeCard(plugin: PluginDetail, readme: string | null): string {
  if (!readme) {
    return `
<section class="card">
  <h2>About</h2>
  <p class="hint">${
      plugin.homepage_url
        ? "No README could be read from this plugin's repository."
        : "This plugin has no repository to read a README from."
    }</p>
</section>`
  }

  return `
<section class="card">
  <h2>About</h2>
  <p class="hint">From the plugin's README.</p>
  <div class="md">${renderMarkdown(readme)}</div>
</section>`
}
