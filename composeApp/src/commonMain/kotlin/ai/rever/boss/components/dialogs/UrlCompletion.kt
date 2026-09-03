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
 * Text that must never reach the field through a completion, and that suppresses one when
 * the user types it.
 *
 * A visited page controls its own URL, so a stored path or query can carry a bidirectional
 * override, a zero-width joiner, or a non-breaking space. Splicing one into the field would
 * let the address the user READS differ from the address Enter opens, which is the one thing
 * inline completion has to be trusted not to do.
 *
 * Whitespace is in here rather than in a clause of its own, and it earns both roles: typed,
 * it means the field holds a search rather than an address; stored, a U+00A0 in a path is as
 * invisible as a zero-width joiner. Splitting the two apart is what let them disagree - the
 * typed side used `Char.isWhitespace`, which counts U+00A0, while the candidate side checked
 * only controls and `FORMAT`, so the very character that suppressed a completion when typed
 * could be spliced in from a stored path.
 *
 * This does NOT cover script homoglyphs (a Cyrillic `а` in a lookalike domain); that needs a
 * punycode/confusables policy, not a character class.
 */
private fun String.hasInvisibleCharacters(): Boolean = any { it.isWhitespace() || it.category in HIDDEN }

/**
 * The Unicode categories a completion must never splice, as categories rather than as
 * `isISOControl()` so the whole test fits one readable line: `CONTROL` is exactly the C0 and
 * C1 range that call covered, and `FORMAT` is where the bidi overrides and zero-width joiners
 * live.
 */
private val HIDDEN = setOf(CharCategory.CONTROL, CharCategory.FORMAT)

/**
 * Whether this stored address is unfit to be offered as a completion at all, host included.
 *
 * Empty: nothing to key on. Invisible characters: see [hasInvisibleCharacters]. Userinfo -
 * a `user@` before the authority ends - because [canonicalUrlKey] keeps it while
 * `java.net.URL` reads the host as whatever follows the `@`. A stored
 * `https://github.com@evil.example/` canonicalises to `github.com@evil.example`, which
 * extends a typed `git` while the host is still open, so one Tab would hand the field a
 * host the user never named and Enter would open `evil.example`. Chrome strips userinfo
 * out of the omnibox for the same reason.
 */
private fun String.isUnofferable(): Boolean = isEmpty() || hasInvisibleCharacters() || hostOf(this).contains('@')

/**
 * Whether the full stored ADDRESS is unfit to be offered even though its host is fine.
 *
 * A stored OAuth URL is hundreds of characters of dead `state=` parameters, so its tail
 * would be longer than the field it is drawn in - and opening it replays an expired
 * request. Both reasons are about the address, which is why this is not folded into
 * [isUnofferable]: an entry whose only visit carries a query still knows what host it is
 * on, and if the one `accounts.google.com` visit is an OAuth URL then typing "acc" should
 * still complete to the host.
 */
private fun String.isUnofferableAddress(): Boolean = contains('?')

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
internal fun extendsTyped(
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
 *  - a candidate ADDRESS carrying a query string is not offered: a stored OAuth URL is
 *    500-2000 characters of dead `state=` parameters, and it makes the ghost longer than
 *    the field. Its host is still offered - see [isUnofferableAddress].
 *  - text with whitespace in it is a search, never an address, and the "Search Google for
 *    …" row is never a completion candidate.
 *  - a typed `scheme://` is normalized away before any of the above, the same way
 *    `rankMatches` normalizes a pasted query, because the entries are matched in their
 *    canonical spelling. Without it a typed `https://git` carried a `:`, which read as a
 *    host already named and left the ghost blank on the one input where the dropdown and
 *    the field are easiest to compare side by side.
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
    // Matched in the entries' own spelling, so a typed scheme (and the `www.` behind it)
    // does not have to appear in every stored address for the ghost to appear. The typed
    // text itself is still what the ghost is drawn after, so the scheme stays on screen.
    val matchable = if (typed.contains("://")) canonicalUrlKey(typed) else typed
    // Whitespace is part of `hasInvisibleCharacters`, so text with a space in it is a search
    // and completing it would eat what is still being typed. A blank field needs no clause of
    // its own either: an empty one carries no `://`, so it arrives here as an empty
    // `matchable`, and an all-whitespace one is caught by the first clause.
    if (typed.hasInvisibleCharacters() || matchable.isEmpty()) return null

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

    val typedHost = hostOf(matchable)
    // No dot and no colon means no host has been named yet, so the host is still the thing
    // being completed. Anything else and the user has committed to a host.
    val hostStillOpen = matchable.none { it == '.' || it == ':' }

    val addresses =
        entries
            .asSequence()
            .filterNot { (canonical, _, _) -> canonical.isUnofferableAddress() }
            .map { (canonical, url, _) -> UrlCompletion(canonical, url) }

    val candidates =
        if (hostStillOpen) {
            // Hosts first, then the full addresses, as a Sequence so a hit on the first host
            // does not pay for the rest.
            entries.asSequence().map { (canonical, url, scheme) ->
                UrlCompletion(display = hostOf(canonical), target = "$scheme://${storedAuthority(url)}")
            } + addresses
        } else {
            addresses.filter { hostOf(it.display).equals(typedHost, ignoreCase = true) }
        }

    return candidates
        .firstOrNull { extendsTyped(it.display, matchable) }
        // The user's own casing survives in the host, which is case-insensitive anyway; the
        // path comes through verbatim because `extendsTyped` matched it exactly.
        ?.let { it.copy(display = typed + it.display.substring(matchable.length)) }
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
 * Its cost, and the address bar pays the same one: Compose reports the TRANSFORMED text as
 * the node's editable text, so a screen reader announces the ghost as though the field
 * contained it. That is also why the tests assert on the URL that OPENS rather than on the
 * field's rendered text - the semantics value cannot tell a ghost apart from an accepted
 * completion.
 *
 * The prefix guard here is the same one [urlCompletionTarget] applies before navigating, so
 * what is drawn and where Enter goes cannot come apart.
 */
internal fun ghostTextTransformation(
    completion: UrlCompletion?,
    color: Color,
): VisualTransformation =
    VisualTransformation { text ->
        val tail = completion?.display?.takeIf { extendsTyped(it, text.text) }?.substring(text.length)
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
 *
 * The guard is [extendsTyped] - the same rule that BUILT the display, case-sensitive in the
 * path and not just in the host. A looser test here would have let this function certify a
 * string the completion rules would not have produced, which is the one thing it exists to
 * prevent.
 */
internal fun urlCompletionTarget(
    completion: UrlCompletion?,
    typed: String,
): UrlCompletion? = completion?.takeIf { extendsTyped(it.display, typed) }
