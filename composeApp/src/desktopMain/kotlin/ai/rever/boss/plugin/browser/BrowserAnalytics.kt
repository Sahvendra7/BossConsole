package ai.rever.boss.plugin.browser

import ai.rever.boss.components.plugin.providers.publishSystemEvent
import ai.rever.boss.plugin.api.ApplicationEvent
import ai.rever.boss.plugin.api.BrowserEvent
import ai.rever.boss.plugin.api.BrowserEventType
import ai.rever.boss.plugin.api.BrowserInteractionEvent
import ai.rever.boss.plugin.api.BrowserInteractionType
import ai.rever.boss.plugin.api.BrowserNavigationType

/**
 * Publishes browser activity onto the application event bus so analytics consumers can
 * see which sites BOSS is used with and how they are used.
 *
 * **This is the privacy boundary for browser telemetry.** A full URL is passed *in* and
 * only a registrable domain goes *out* — the path, query string, fragment, and page title
 * are discarded here and never reach [BrowserEvent], the event bus, or any plugin.
 * Reducing at the source is deliberate: it means no downstream consumer can leak page-level
 * detail even by accident, because the detail was never handed to it.
 *
 * In-page interactions ([interaction]) get the same treatment one layer further in. The
 * injected collector is written to read only structural attributes, and everything it
 * sends is re-validated here against [sanitizeToken] / [sanitizeFieldName] / [sanitizePath]
 * before an event exists. Two independent passes, because the page is hostile territory:
 * a site controls its own DOM and can name an input whatever it likes.
 *
 * BOSS is used in healthcare contexts. Widening this to emit a URL, path, title, element
 * text, or input value is a privacy decision, not a refactor — see the analytics plugin's
 * `CLAUDE.md`.
 */
internal object BrowserAnalytics {
    /**
     * Host-side kill switch for browser telemetry, read once at startup.
     *
     * Consent for *sending* analytics lives in the analytics plugin, which gates egress for
     * every event source alike; this is a separate, blunter control for deployments that want
     * no browser telemetry produced at all, whatever a plugin later decides to do with it.
     *
     * It is enforced **here**, at the single point where every browser event is published,
     * rather than at the call sites. An operator who sets a variable named
     * `BOSS_BROWSER_TELEMETRY_DISABLED` means all of it — not just the in-page collector —
     * and gating each producer separately is how a later one gets added without the guard.
     * The page-side script is *additionally* not injected at all when this is off, so a
     * disabled deployment also runs no telemetry JavaScript in pages.
     *
     * `1`, `yes` and `on` disable it as surely as `true`, and a system property works as well
     * as an environment variable. Matching only the exact string `true` would hand an operator
     * who wrote `=1` a silent full-telemetry deployment, and silence is the wrong direction
     * for a privacy control to fail in. Same vocabulary as [FluckEngine.isTruthyFlag].
     *
     * A `var` purely so [BrowserAnalyticsTest] can assert the gate actually gates; nothing in
     * the app writes it, and the resolved value is what a deployment gets.
     */
    @Volatile
    internal var telemetryEnabled: Boolean =
        telemetryEnabledFrom(
            env = System.getenv(TELEMETRY_DISABLED_KEY),
            property = System.getProperty(TELEMETRY_DISABLED_PROPERTY),
        )

    /**
     * The kill switch's resolution rule, as a function of its two raw inputs so it can be
     * tested. Reading `System.getenv` inline left every documented behaviour here uncovered,
     * including a bug already hit once (see `isNotBlank` below).
     *
     * Read straight from the environment and system properties rather than through
     * `ConfigLoader`, deliberately: this is an operator-level control that must hold before
     * any settings exist, and it is never surfaced as a Settings row. (Note the asymmetry
     * `FluckEngine` warns about - `getenv` cannot see a system property - is handled here by
     * consulting both.)
     */
    internal fun telemetryEnabledFrom(
        env: String?,
        property: String?,
    ): Boolean {
        // `isNotBlank`, because an env var set to the empty string is still non-null: without
        // it, `BOSS_BROWSER_TELEMETRY_DISABLED=` (a common way to "unset" one in a launcher
        // script) silently shadowed `-Dboss.browser.telemetry.disabled=true`.
        val raw = env?.takeIf { it.isNotBlank() } ?: property
        return !FluckEngine.isTruthyFlag(raw)
    }

    private const val TELEMETRY_DISABLED_KEY = "BOSS_BROWSER_TELEMETRY_DISABLED"
    private const val TELEMETRY_DISABLED_PROPERTY = "boss.browser.telemetry.disabled"

    /** The one place a browser event reaches the bus, so the kill switch cannot be bypassed. */
    private fun publish(event: ApplicationEvent) {
        if (!telemetryEnabled) return
        // NOTE: a no-op until some plugin first touches PluginContext.applicationEventBus,
        // which is what lazily creates the bus (DefaultPlugin.applicationEventBus). Tabs
        // restored at launch therefore report their TAB_OPENED and first PAGE_VIEWED into
        // nothing, so opens undercount against closes for one session. Buffering pre-bus
        // events would fix it; until then a consumer must not read tab counts as exact.
        publishSystemEvent(event)
    }

    /**
     * Record a successfully-loaded page view for [authority] (a host, optionally with a
     * port, as produced by `suggestableHost`).
     *
     * Silently does nothing for hosts that aren't meaningful sites (loopback, bare IPs,
     * single-label intranet names) — see [registrableDomain].
     */
    fun pageViewed(
        authority: String,
        navigationType: BrowserNavigationType? = null,
        pageIndexInVisit: Int? = null,
        windowId: String? = null,
    ) {
        val domain = registrableDomain(authority) ?: return
        publish(
            BrowserEvent(
                browserEventType = BrowserEventType.PAGE_VIEWED,
                domain = domain,
                windowId = windowId,
                navigationType = navigationType,
                pageIndexInVisit = pageIndexInVisit,
            ),
        )
    }

    /**
     * Record the end of a page visit: [dwellMs] wall-clock, of which [activeMs] was spent
     * focused. Negative or absurd durations are dropped rather than reported — a clock
     * change or a resume-from-sleep can produce either, and a bogus multi-day dwell would
     * quietly poison every engagement average built on top of it.
     */
    fun pageLeft(
        authority: String,
        dwellMs: Long,
        activeMs: Long,
        windowId: String? = null,
    ) {
        val domain = registrableDomain(authority) ?: return
        if (dwellMs < 0 || activeMs < 0 || dwellMs > MAX_REPORTABLE_DWELL_MS) return
        publish(
            BrowserEvent(
                browserEventType = BrowserEventType.PAGE_LEFT,
                domain = domain,
                windowId = windowId,
                dwellMs = dwellMs,
                // Active time cannot exceed wall-clock time; clamp rather than drop, since
                // a small overshoot is just accounting drift between the two counters.
                activeMs = minOf(activeMs, dwellMs),
            ),
        )
    }

    /**
     * Record a browser tab lifecycle change. [authority] may be null for a new empty tab.
     *
     * A tab on a host we refuse to report (loopback, a bare IP, an intranet name) is *not*
     * the same thing as a tab with nothing loaded, and reporting both as [BLANK_TAB_DOMAIN]
     * would make "new tabs opened" read high by however much a developer used a dev server.
     * [UNREPORTABLE_TAB_DOMAIN] keeps them countable and separate, at no privacy cost -
     * neither sentinel says anything about where the tab actually was.
     */
    fun tabEvent(
        type: BrowserEventType,
        authority: String?,
        windowId: String? = null,
    ) {
        val domain =
            when {
                authority.isNullOrBlank() -> BLANK_TAB_DOMAIN
                else -> registrableDomain(authority) ?: UNREPORTABLE_TAB_DOMAIN
            }
        publish(BrowserEvent(browserEventType = type, domain = domain, windowId = windowId))
    }

    /**
     * Record an in-page interaction. Every caller-supplied field is sanitized here; a field
     * that fails validation is dropped to null rather than rejecting the whole event, so
     * one odd attribute cannot cost the interaction signal.
     */
    @Suppress("LongParameterList")
    fun interaction(
        type: BrowserInteractionType,
        authority: String,
        elementTag: String? = null,
        elementRole: String? = null,
        inputType: String? = null,
        fieldName: String? = null,
        elementPath: String? = null,
        scrollDepthPercent: Int? = null,
        repeatCount: Int? = null,
        windowId: String? = null,
    ) {
        val domain = registrableDomain(authority) ?: return
        publish(
            BrowserInteractionEvent(
                interactionType = type,
                domain = domain,
                elementTag = sanitizeToken(elementTag, MAX_TAG_LENGTH),
                elementRole = sanitizeToken(elementRole, MAX_TAG_LENGTH),
                inputType = sanitizeToken(inputType, MAX_TAG_LENGTH),
                fieldName = sanitizeFieldName(fieldName),
                elementPath = sanitizePath(elementPath),
                scrollDepthPercent = scrollDepthPercent?.takeIf { it in 0..100 },
                repeatCount = repeatCount?.takeIf { it in 1..MAX_REPEAT_COUNT },
                windowId = windowId,
            ),
        )
    }

    /**
     * A structural token (tag, ARIA role, input type). These come from a fixed HTML
     * vocabulary, so anything outside `[a-z0-9-]` is a page doing something unexpected and
     * is refused outright rather than trimmed — a value that needed cleaning was not a tag
     * name, and guessing what it *was* is how content leaks through.
     *
     * The range checks are **explicitly ASCII, not `Char.isLowerCase()`/`isDigit()`**. Those
     * delegate to `Character.*`, which is Unicode-aware: `isLowerCase` is true for Cyrillic,
     * Greek and Arabic-script letters, and `isDigit` covers the whole `Nd` category. Written
     * that way, this function called itself a structural-vocabulary check while accepting a
     * 32-character run of any script — free text in every locale but English.
     */
    internal fun sanitizeToken(
        raw: String?,
        maxLength: Int,
    ): String? =
        raw
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= maxLength }
            ?.takeIf { value -> value.all { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' } }

    /**
     * A form field's `name` attribute. Unlike a tag this is developer-chosen free text, so
     * it is cleaned rather than refused: unexpected characters are dropped and short digit
     * runs redacted, on the theory that a name is `patientMrn` (a schema label, safe) but
     * could be `mrn-4417882` (an identifier baked into a generated form, not safe).
     *
     * ASCII-only for the same reason as [sanitizeToken], and here the mismatch was worse:
     * filtering with the Unicode-aware `isLetterOrDigit()` while redacting with `\d`, which
     * is ASCII-only in Java unless `UNICODE_CHARACTER_CLASS` is set, let `mrn٤٤١٧٨٨٢` pass
     * the filter *and* the redactor untouched. Both halves must agree on an alphabet.
     *
     * **An interior space refuses the whole value rather than being filtered out of it.**
     * Filtering is what made `"John Smith"` come out as the plausible-looking field name
     * `JohnSmith`, and the digit redaction does nothing for alphabetic PHI — so the one
     * shape most likely to be a person's name was also the one this let through intact.
     * A real `name=` attribute essentially never contains whitespace (it is a form-encoding
     * key), so refusing costs nothing real. Leading and trailing whitespace is still just
     * trimmed: that is markup formatting, not content. The collector also only reads `name`
     * off actual form controls, so this function is not asked about a `div`'s author-defined
     * `name` property in the first place.
     *
     * **What remains, stated rather than implied.** This still cleans instead of refusing,
     * so single-token alphabetic content survives: `patient_johnsmith` and `mrn_smith_j` come
     * through intact, and the digit redaction does nothing for either. Whitespace was the
     * shape that made free text *look* like a field name; a developer writing a name that
     * embeds a person is a narrower case that only refusing unknown names outright would
     * catch, and that would cost the signal on every legitimate form. Tracked privately in
     * boss-plugin-analytics#7.
     */
    internal fun sanitizeFieldName(raw: String?): String? =
        raw
            ?.trim()
            ?.takeIf { value -> value.none { it.isWhitespace() } }
            ?.filter { c -> c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in FIELD_NAME_PUNCTUATION }
            ?.takeIf { it.isNotEmpty() }
            ?.let { DIGIT_RUN.replace(it, "#") }
            // Truncated LAST, so the cap applies to what is emitted rather than to the raw
            // input. Cutting first also meant a digit run straddling the boundary could leave
            // a one- or two-digit tail that the redactor no longer recognised as a run.
            ?.take(MAX_FIELD_NAME_LENGTH)

    /**
     * A structural element path: tag names and sibling positions only, e.g.
     * `form>div:2>button:1`. Rejected wholesale if it contains anything else, because the
     * only way to build a path with a `#`, `.`, or quote in it is to have included an id,
     * class, or attribute selector — which is exactly what must not be here.
     */
    internal fun sanitizePath(raw: String?): String? =
        raw
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= MAX_PATH_LENGTH }
            ?.takeIf { PATH_SHAPE.matches(it) }

    /** Longest visit we are willing to call real; beyond this the clock is suspect. */
    private const val MAX_REPORTABLE_DWELL_MS = 12L * 60 * 60 * 1000
    private const val MAX_TAG_LENGTH = 32
    private const val MAX_FIELD_NAME_LENGTH = 64
    private const val MAX_PATH_LENGTH = 120
    private const val MAX_REPEAT_COUNT = 100

    /** Stand-in domain for a tab with no site loaded, so tab counts still balance. */
    internal const val BLANK_TAB_DOMAIN = "about:blank"

    /** Stand-in for a tab on a host [registrableDomain] refuses; distinct from an empty tab. */
    internal const val UNREPORTABLE_TAB_DOMAIN = "unreportable"

    /** Punctuation a form field name may keep — array/object syntax and word separators. */
    private val FIELD_NAME_PUNCTUATION = setOf('_', '-', '.', '[', ']')

    /**
     * Three digits, not five.
     *
     * Five only catches long generated ids. A record number in a form field is routinely
     * four (`select_patient_4417`), and `BrowserInteractionScript`'s own KDoc names exactly
     * that shape as what must not escape — so the threshold and the stated intent disagreed.
     * Three costs almost nothing: `address_line[2]`, `line1`, and `col2` are one or two
     * digits and survive intact.
     */
    private val DIGIT_RUN = Regex("""\d{3,}""")
    private val PATH_SHAPE = Regex("""[a-z0-9-]+(:\d+)?(>[a-z0-9-]+(:\d+)?)*""")

    /**
     * Reduce an authority to its registrable domain (eTLD+1), or null when there is
     * nothing worth reporting.
     *
     * `portal.availity.com:443` → `availity.com`, `bbc.co.uk` → `bbc.co.uk`.
     *
     * Collapsing subdomains is the point: a subdomain is often more identifying than the
     * site itself (`patient-portal.smallclinic.com` names a workflow, not just a vendor).
     *
     * This uses a small table of common multi-label suffixes rather than the full Public
     * Suffix List — pulling in a PSL dependency for telemetry isn't worth it. The failure
     * mode is conservative in the wrong direction for exotic suffixes (`example.pvt.k12.ma.us`
     * reduces to `ma.us`), which over-collapses rather than over-reports.
     */
    // Each `return null` is a distinct category of thing we refuse to report. Collapsing them
    // into one exit would obscure exactly the list a reader of a privacy boundary comes for.
    @Suppress("ReturnCount")
    internal fun registrableDomain(authority: String): String? {
        var trimmed = authority.trim().lowercase()

        // Callers are expected to pass an authority, but this function is the privacy
        // boundary — it must not depend on that. Drop any scheme and cut at the first
        // path/query/fragment delimiter, so handing it a whole URL can never smuggle a
        // path or query string out through the last label.
        trimmed = trimmed.substringAfter("://")
        trimmed = trimmed.takeWhile { it != '/' && it != '?' && it != '#' }
        // Credentials in an authority ("user:pw@host") are never reportable.
        trimmed = trimmed.substringAfterLast('@')

        // IPv6 literals arrive bracketed ("[::1]:3000"); never report an address.
        if (trimmed.startsWith("[")) return null

        val host = trimmed.substringBefore(':').removeSuffix(".")
        if (host.isEmpty()) return null
        if (host == "localhost" || host.endsWith(".localhost")) return null

        // Internationalised names reach a browser URL already punycoded (`xn--…`), so a host
        // with a non-ASCII character is not a name the browser resolved. Refuse it rather
        // than reason about it — and note this must come BEFORE the IPv4 check to be safe,
        // not after. Making that check ASCII-only for consistency with the sanitizers would
        // invert its meaning: `١٢٧.٠.٠.١` would stop being recognised as an address and be
        // reported as the "site" `٠.١`. Here, unlike in the sanitizers, the Unicode-aware
        // test is the one that refuses more, so the guard belongs upstream of it.
        if (host.any { it.code > 127 }) return null

        val labels = host.split('.').filter { it.isNotEmpty() }
        // Single-label hosts are intranet machine names, not sites.
        if (labels.size < 2) return null
        // An address is not a site. Any all-numeric host, not only a four-label one: Chromium
        // canonicalises `127.1` to `127.0.0.1` before this sees it, but this function is
        // documented as holding under misuse, and a two-label check answered `127.1` with
        // "the site 127.1" and `10.0.1` with "the site 0.1".
        if (labels.all { l -> l.all { c -> c in '0'..'9' } }) return null

        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (labels.size >= 3 && lastTwo in MULTI_LABEL_SUFFIXES) {
            labels.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    /**
     * Two-label public suffixes common enough to matter. Without these, `bbc.co.uk` would
     * reduce to the meaningless `co.uk` and every UK site would collapse together.
     */
    private val MULTI_LABEL_SUFFIXES =
        setOf(
            "co.uk",
            "org.uk",
            "ac.uk",
            "gov.uk",
            "net.uk",
            "me.uk",
            "com.au",
            "net.au",
            "org.au",
            "edu.au",
            "gov.au",
            "co.jp",
            "or.jp",
            "ne.jp",
            "ac.jp",
            "go.jp",
            "co.nz",
            "org.nz",
            "govt.nz",
            "co.za",
            "org.za",
            "co.in",
            "net.in",
            "org.in",
            "com.br",
            "com.mx",
            "com.ar",
            "com.sg",
            "com.tr",
            "com.cn",
            "com.hk",
            "com.tw",
            "com.my",
            "com.ph",
            "com.pk",
            "co.kr",
            "or.kr",
        )
}
