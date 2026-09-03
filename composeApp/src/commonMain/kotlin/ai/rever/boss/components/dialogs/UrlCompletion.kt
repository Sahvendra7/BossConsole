package ai.rever.boss.components.dialogs

import ai.rever.boss.plugin.browser.canonicalUrlKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

/**
 * An inline completion: what the field SHOWS, and where accepting it actually goes.
 *
 * The two are not the same string and that is the point. [display] is canonical - no scheme,
 * no `www.`, no trailing slash - because that is the spelling the user is typing towards.
 * [target] is the address as history recorded it. Collapsing them into one string meant a
 * stored `http://192.168.4.20:8123/lovelace` was completed to `192.168.4.20:8123` and then
 * re-derived as `https://…` by `processUrlInput`, which fails the handshake; a stored
 * `https://www.example.com/x` became `example.com/x` and opened a host the certificate does
 * not cover.
 */
internal data class UrlCompletion(
    val display: String,
    val target: String,
)

/** The scheme of a stored URL, or null when it has no `scheme://` prefix. */
private fun schemeOf(url: String): String? = url.substringBefore("://", "").takeIf { it.isNotEmpty() }

/** The authority of a stored URL exactly as recorded, `www.` and port included. */
private fun storedAuthority(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?')

/** The authority of a [canonicalUrlKey]-shaped address: everything before the path or query. */
private fun hostOf(canonical: String): String = canonical.substringBefore('/').substringBefore('?')

/**
 * Text that must never reach the field through a completion.
 *
 * A visited page controls its own URL, so a stored path or query can carry a bidirectional
 * override or a zero-width character. Splicing one into the field would let the address the
 * user READS differ from the address Enter opens, which is the one thing inline completion
 * has to be trusted not to do. This does NOT cover script homoglyphs (a Cyrillic `а` in a
 * lookalike domain); that needs a punycode/confusables policy, not a character class.
 */
private fun String.hasInvisibleCharacters(): Boolean = any { it.isISOControl() || it.category == CharCategory.FORMAT }

/**
 * Whether this stored address is unfit to be offered as a completion at all.
 *
 * Empty: nothing to key on. Invisible characters: see [hasInvisibleCharacters]. A query
 * string: a stored OAuth URL is hundreds of characters of dead `state=` parameters, so its
 * tail would be longer than the field it is drawn in - and opening it replays an expired
 * request.
 */
private fun String.isUnofferable(): Boolean = isEmpty() || hasInvisibleCharacters() || contains('?')

/**
 * Whether [candidate] is a completion of [typed] - a strict extension of it, with the host
 * matched case-insensitively and the path matched case-SENSITIVELY.
 *
 * The split casing is not fussiness. [canonicalUrlKey] lowercases the authority and leaves
 * the path alone, so matching a path case-insensitively and then splicing the user's own
 * casing onto the stored tail produced an address that existed in neither place: typing
 * `github.com/risa-labs-inc/boss` against a stored `…/BossConsole/pulls` offered
 * `…/bossConsole/pulls`, a 404 on any case-sensitive server.
 */
private fun extendsTyped(
    candidate: String,
    typed: String,
): Boolean {
    if (candidate.length <= typed.length) return false
    val pathStart = typed.indexOf('/')
    return if (pathStart < 0) {
        candidate.startsWith(typed, ignoreCase = true)
    } else {
        candidate.regionMatches(0, typed, 0, pathStart, ignoreCase = true) &&
            candidate.startsWith(typed.substring(pathStart), pathStart)
    }
}

/**
 * The completion the URL field should offer for [typed], or null.
 *
 * This is Chrome's inline completion, and the rules below are what make it usable rather
 * than infuriating - or dangerous:
 *  - the typed text must be a PREFIX of the candidate, matched against the canonical
 *    spelling so typing "git" reaches an entry stored as `https://github.com/`.
 *  - a HOST completes before a path does. The best-ranked entry for "git" is whichever
 *    github page was visited most, so completing to the full URL would fill in
 *    "github.com/risa-labs-inc/BossConsole/pulls" in exchange for three characters. Chrome
 *    offers the host and leaves the deep pages to the list underneath.
 *  - **the host is only ever completed while the typed text names no host at all - no `.`
 *    and no `:` in it. After that, a completion may only add a PATH under the host that was
 *    typed.** This is a security rule, not a taste one. History is attacker-influenceable:
 *    one drive-by visit to `paypal.com-login.evil.example` puts it in the suggestion list,
 *    and a bare prefix extension would then turn a typed `paypal.com` - or `paypal.c`, or
 *    `paypal.` - into somebody else's domain, which Enter would take. Guarding only a host
 *    that "looks finished" left every prefix on the way to it open, and left `192.168.4`
 *    free to complete to `192.168.4.20:8123`, a different machine. The cost is no ghost
 *    while a dotted host is half-typed; the dropdown still lists the match.
 *  - a candidate carrying a query string is not offered: a stored OAuth URL is 500-2000
 *    characters of dead `state=` parameters, and it makes the ghost longer than the field.
 *  - text with whitespace in it is a search, never an address, and the "Search Google for
 *    …" row is never a completion candidate.
 *
 * Candidate order follows the suggestion list, which is already ranked.
 *
 * Pure, and separate from the composable, so these rules are pinned by tests rather than by
 * typing into the dialog.
 */
internal fun inlineUrlCompletion(
    typed: String,
    suggestions: List<UrlSuggestion>,
): UrlCompletion? {
    if (typed.isBlank() || typed.any { it.isWhitespace() } || typed.hasInvisibleCharacters()) return null

    val entries =
        suggestions
            .filterNot { it.isSearchSuggestion }
            .mapNotNull { suggestion ->
                val canonical = canonicalUrlKey(suggestion.url)
                val scheme = schemeOf(suggestion.url)
                if (scheme == null || canonical.isUnofferable()) {
                    null
                } else {
                    Triple(canonical, suggestion.url, scheme)
                }
            }

    val typedHost = hostOf(typed)
    // No dot and no colon means no host has been named yet, so the host is still the thing
    // being completed. Anything else and the user has committed to a host.
    val hostStillOpen = typed.none { it == '.' || it == ':' }

    val candidates =
        if (hostStillOpen) {
            // Hosts first, then the full addresses, as a Sequence so a hit on the first host
            // does not pay for the rest.
            entries.asSequence().map { (canonical, url, scheme) ->
                UrlCompletion(display = hostOf(canonical), target = "$scheme://${storedAuthority(url)}")
            } + entries.asSequence().map { (canonical, url, _) -> UrlCompletion(canonical, url) }
        } else {
            entries
                .asSequence()
                .filter { (canonical, _, _) -> hostOf(canonical).equals(typedHost, ignoreCase = true) }
                .map { (canonical, url, _) -> UrlCompletion(canonical, url) }
        }

    return candidates
        .firstOrNull { extendsTyped(it.display, typed) }
        // The user's own casing survives in the host, which is case-insensitive anyway; the
        // path comes through verbatim because `extendsTyped` matched it exactly.
        ?.let { it.copy(display = typed + it.display.substring(typed.length)) }
}

/**
 * Draws [completion]'s tail after the typed text in [color], without putting it in the
 * field's value.
 *
 * Gray ghost text rather than a highlighted selection, because that is what the browser's
 * own address bar shows for the same gesture (the `autocompleteSuggestion` path in the
 * fluck-browser plugin's `FluckBrowserTabComponent`) and the two URL inputs in the app
 * should not disagree about what a completion looks like.
 *
 * Being a VISUAL transformation is the load-bearing part: the completion is never part of
 * the value, so Backspace deletes a character the user typed instead of first having to
 * clear a completion they never asked to be there, and every existing reader of the field's
 * text still sees exactly what was typed.
 *
 * The prefix guard here is the same one [urlCompletionTarget] applies before navigating, so
 * what is drawn and where Enter goes cannot come apart.
 */
internal fun ghostTextTransformation(
    completion: UrlCompletion?,
    color: Color,
): VisualTransformation =
    VisualTransformation { text ->
        val tail = completion?.display?.takeIf { it.extends(text.text) }?.substring(text.length)
        if (tail == null) {
            TransformedText(text, OffsetMapping.Identity)
        } else {
            TransformedText(
                buildAnnotatedString {
                    append(text)
                    withStyle(SpanStyle(color = color)) { append(tail) }
                },
                // The ghost sits entirely past the end of the value, so an offset in the
                // value maps to itself, and an offset inside the ghost belongs to the end
                // of the value - which is where the cursor has to stay.
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int) = offset

                    override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(text.length)
                },
            )
        }
    }

/**
 * The address a completion is still offering for [typed], or null.
 *
 * Shared by the renderer and by every path that navigates, so a completion left over from
 * the previous keystroke can neither be drawn nor opened: the suggestion lookup is
 * debounced, so there is a window where [typed] has already moved on from the completion it
 * produced, and in that window the field correctly shows no ghost while a commit would have
 * taken the stale address.
 */
internal fun urlCompletionTarget(
    completion: UrlCompletion?,
    typed: String,
): UrlCompletion? = completion?.takeIf { it.display.extends(typed) }

/** A strict, case-insensitive extension of [typed] - the one rule both of the above share. */
private fun String.extends(typed: String): Boolean = length > typed.length && startsWith(typed, true)
