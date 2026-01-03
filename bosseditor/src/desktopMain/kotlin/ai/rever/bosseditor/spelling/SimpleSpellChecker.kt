package ai.rever.bosseditor.spelling

import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Simple dictionary-based spell checker implementation.
 *
 * This is a basic implementation for development/testing.
 * For production, consider using Hunspell or LanguageTool.
 *
 * Features:
 * - Loads word lists from resources or files
 * - Supports custom user dictionary
 * - Provides basic suggestions using edit distance
 * - Thread-safe lazy initialization
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
class SimpleSpellChecker(
    private val dictionaryPath: String? = null,
    private val customDictionaryPath: String = System.getProperty("user.home") + "/.boss/spelling/custom.txt"
) : SpellChecker {

    // Thread-safe initialization state using CountDownLatch for proper synchronization
    private val initLatch = CountDownLatch(1)
    @Volatile
    private var dictionary: Set<String> = emptySet()
    @Volatile
    private var prefixMap: Map<String, Set<String>> = emptyMap()
    private val customDictionary: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
    @Volatile
    private var currentLanguage: String = "en_US"
    @Volatile
    private var isInitialized: Boolean = false

    // Common programming terms to always accept
    private val programmingTerms = setOf(
        // Common keywords
        "kotlin", "java", "python", "javascript", "typescript",
        "func", "impl", "struct", "enum", "async", "await",
        "nullable", "nonnull", "lateinit", "inline", "reified",
        // Common abbreviations
        "args", "params", "config", "impl", "init", "ctx",
        "msg", "btn", "img", "src", "dst", "idx", "len",
        // Camel case parts
        "todo", "fixme", "xxx", "hack", "note", "bug",
        // API terms
        "api", "url", "uri", "http", "https", "json", "xml",
        "jwt", "oauth", "cors", "csrf", "xss", "sql",
        // Common tech terms
        "localhost", "webhook", "callback", "frontend", "backend"
    )

    /**
     * Ensures the spell checker is initialized.
     * Uses CountDownLatch for proper thread synchronization without busy-wait.
     * Safe to call from any thread.
     */
    private fun ensureInitialized() {
        if (isInitialized) return

        // Fast path: check if already initialized
        if (initLatch.count == 0L) return

        // Try to be the initializer thread
        synchronized(this) {
            if (isInitialized) return
            if (initLatch.count == 0L) return

            try {
                // Load main dictionary
                val words = loadMainDictionary()
                dictionary = words

                // Build prefix map for efficient suggestions (O(1) lookup by prefix)
                prefixMap = buildPrefixMap(words)

                // Load custom dictionary
                loadCustomDictionary()

                isInitialized = true
            } catch (e: Exception) {
                println("[SimpleSpellChecker] Failed to initialize: ${e.message}")
                // Fall back to empty dictionary
                dictionary = emptySet()
                prefixMap = emptyMap()
                isInitialized = true
            } finally {
                // Signal all waiting threads that initialization is complete
                initLatch.countDown()
            }
        }

        // Wait for initialization to complete (with timeout to prevent deadlock)
        if (!isInitialized) {
            try {
                initLatch.await(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                println("[SimpleSpellChecker] Initialization interrupted")
            }
        }
    }

    /**
     * Builds a prefix map for efficient suggestion lookups.
     * Groups words by their first 2 characters.
     */
    private fun buildPrefixMap(words: Set<String>): Map<String, Set<String>> {
        return words.groupBy { word ->
            if (word.length >= 2) word.substring(0, 2) else word
        }.mapValues { it.value.toSet() }
    }

    private fun loadMainDictionary(): Set<String> {
        val words = mutableSetOf<String>()

        // Add programming terms
        words.addAll(programmingTerms)

        // Try to load from file if provided
        if (dictionaryPath != null) {
            try {
                val file = File(dictionaryPath)
                if (file.exists()) {
                    file.useLines { lines ->
                        lines.forEach { line ->
                            val word = line.trim().lowercase(Locale.US)
                            if (word.isNotEmpty() && !word.startsWith("#")) {
                                words.add(word)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println("[SimpleSpellChecker] Failed to load dictionary: ${e.message}")
            }
        }

        // Load built-in common English words (basic set for demo)
        words.addAll(loadBuiltInWords())

        return words
    }

    private fun loadBuiltInWords(): Set<String> {
        // Expanded dictionary of common English words for spell checking
        // TODO: In production, load from a comprehensive dictionary resource file (10k+ words)
        return setOf(
            // Articles, prepositions, conjunctions, pronouns
            "a", "an", "the", "and", "or", "but", "if", "then", "else",
            "in", "on", "at", "to", "for", "of", "with", "by", "from",
            "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can",
            "i", "me", "my", "mine", "you", "your", "yours", "he", "him", "his",
            "she", "her", "hers", "it", "its", "we", "us", "our", "ours",
            "they", "them", "their", "theirs", "who", "whom", "whose",
            "about", "above", "across", "after", "against", "along", "among",
            "around", "before", "behind", "below", "beneath", "beside", "between",
            "beyond", "during", "except", "inside", "into", "near", "off",
            "onto", "out", "outside", "over", "past", "through", "toward",
            "under", "until", "upon", "within", "without",

            // Common verbs (expanded)
            "accept", "achieve", "act", "add", "adjust", "admit", "affect", "afford",
            "agree", "aim", "allow", "answer", "appear", "apply", "approach", "argue",
            "arrange", "arrive", "ask", "assume", "attach", "attempt", "attend", "avoid",
            "base", "become", "begin", "believe", "belong", "break", "bring", "build",
            "buy", "call", "care", "carry", "catch", "cause", "change", "check",
            "choose", "claim", "clean", "clear", "climb", "close", "collect", "come",
            "commit", "compare", "complete", "concern", "confirm", "connect", "consider",
            "contain", "continue", "control", "convert", "copy", "correct", "cost",
            "count", "cover", "create", "cross", "cut", "deal", "decide", "declare",
            "define", "deliver", "demand", "demonstrate", "deny", "depend", "describe",
            "design", "destroy", "detect", "determine", "develop", "die", "differ",
            "discover", "discuss", "display", "distribute", "divide", "document", "draw",
            "drive", "drop", "eat", "edit", "enable", "encourage", "end", "enjoy",
            "ensure", "enter", "escape", "establish", "evaluate", "examine", "execute",
            "exist", "expand", "expect", "experience", "explain", "explore", "export",
            "express", "extend", "extract", "face", "fail", "fall", "feed", "feel",
            "fetch", "fight", "fill", "filter", "find", "finish", "fire", "fit",
            "fix", "flow", "fly", "focus", "follow", "force", "forget", "form",
            "format", "forward", "free", "gain", "gather", "generate", "get", "give",
            "go", "grant", "grow", "guess", "guide", "handle", "hang", "happen",
            "hate", "hear", "help", "hide", "hit", "hold", "hope", "identify",
            "ignore", "imagine", "implement", "import", "impose", "improve", "include",
            "increase", "indicate", "inform", "initialize", "insert", "install", "intend",
            "introduce", "investigate", "invite", "involve", "issue", "join", "jump",
            "justify", "keep", "kill", "knock", "know", "lack", "land", "last",
            "launch", "lay", "lead", "learn", "leave", "lend", "let", "lift",
            "like", "limit", "link", "listen", "live", "load", "locate", "lock",
            "log", "look", "lose", "love", "maintain", "make", "manage", "mark",
            "match", "matter", "mean", "measure", "meet", "mention", "merge", "miss",
            "modify", "monitor", "mount", "move", "multiply", "name", "need", "notice",
            "notify", "obtain", "occur", "offer", "open", "operate", "optimize", "order",
            "organize", "output", "overcome", "override", "own", "parse", "pass", "pause",
            "pay", "perform", "permit", "pick", "place", "plan", "play", "please",
            "point", "pop", "populate", "position", "post", "practice", "prefer", "prepare",
            "present", "preserve", "press", "prevent", "print", "process", "produce",
            "program", "project", "promise", "promote", "propose", "protect", "prove",
            "provide", "publish", "pull", "push", "put", "query", "queue", "quit",
            "raise", "reach", "react", "read", "realize", "rebuild", "receive", "recognize",
            "recommend", "record", "recover", "reduce", "refer", "reflect", "refresh",
            "refuse", "register", "reject", "relate", "release", "rely", "remain",
            "remember", "remove", "rename", "render", "repeat", "replace", "reply",
            "report", "represent", "request", "require", "research", "reset", "resize",
            "resolve", "respond", "restart", "restore", "restrict", "result", "retain",
            "retrieve", "return", "reveal", "reverse", "review", "rewrite", "ride",
            "rise", "risk", "roll", "run", "satisfy", "save", "say", "scan",
            "schedule", "scroll", "search", "secure", "see", "seek", "seem", "select",
            "sell", "send", "separate", "serve", "set", "settle", "setup", "share",
            "shift", "ship", "shoot", "show", "shut", "sign", "simplify", "simulate",
            "sit", "skip", "sleep", "slice", "slide", "slow", "solve", "sort",
            "sound", "speak", "specify", "speed", "spend", "split", "spread", "stand",
            "start", "state", "stay", "step", "stick", "stop", "store", "stream",
            "stretch", "strike", "strip", "structure", "study", "submit", "subscribe",
            "succeed", "suffer", "suggest", "suit", "summarize", "supply", "support",
            "suppose", "surprise", "surround", "survive", "suspend", "swap", "switch",
            "synchronize", "take", "talk", "target", "teach", "tell", "tend", "terminate",
            "test", "think", "throw", "tie", "touch", "trace", "track", "trade",
            "train", "transfer", "transform", "translate", "transmit", "travel", "treat",
            "trigger", "trim", "trust", "try", "turn", "type", "understand", "undo",
            "unlock", "unpack", "update", "upgrade", "upload", "use", "validate", "vary",
            "verify", "view", "visit", "wait", "wake", "walk", "want", "warn",
            "waste", "watch", "win", "wish", "wonder", "work", "worry", "wrap",
            "write", "yield",

            // Common nouns (expanded)
            "ability", "access", "account", "accuracy", "action", "activity", "address",
            "advantage", "advice", "age", "agent", "agreement", "algorithm", "amount",
            "analysis", "answer", "api", "app", "application", "approach", "archive",
            "area", "argument", "array", "article", "aspect", "asset", "assignment",
            "attempt", "attention", "attribute", "author", "authority", "background",
            "backup", "balance", "bank", "base", "basis", "batch", "behavior", "benefit",
            "bit", "block", "board", "body", "book", "border", "bottom", "box",
            "branch", "brand", "break", "browser", "budget", "buffer", "bug", "build",
            "builder", "building", "bundle", "bus", "business", "button", "byte", "cache",
            "call", "callback", "camera", "campaign", "capability", "capacity", "capital",
            "card", "care", "career", "case", "cash", "category", "cause", "cell",
            "center", "century", "chain", "challenge", "chance", "change", "channel",
            "chapter", "character", "charge", "chart", "check", "child", "choice",
            "chunk", "circle", "city", "claim", "class", "clause", "client", "clock",
            "cloud", "cluster", "code", "collection", "color", "column", "combination",
            "command", "comment", "commission", "commitment", "committee", "community",
            "company", "comparison", "competition", "compiler", "complaint", "complexity",
            "component", "computer", "concept", "concern", "conclusion", "condition",
            "conference", "confidence", "configuration", "conflict", "connection", "consequence",
            "consideration", "console", "constant", "constraint", "consumer", "contact",
            "container", "content", "context", "contract", "contribution", "control",
            "controller", "convention", "conversation", "conversion", "cookie", "copy",
            "core", "corner", "corporation", "cost", "count", "counter", "country",
            "couple", "course", "court", "cover", "coverage", "creation", "creator",
            "credit", "crisis", "criteria", "crowd", "culture", "currency", "current",
            "cursor", "customer", "cycle", "daemon", "damage", "data", "database",
            "date", "day", "deadline", "deal", "death", "debate", "debt", "decade",
            "decision", "declaration", "decline", "decrease", "default", "defense",
            "definition", "degree", "delay", "delegate", "delivery", "demand", "demo",
            "department", "dependency", "deployment", "depth", "description", "design",
            "designer", "desktop", "destination", "detail", "detection", "determination",
            "developer", "development", "device", "dialog", "dictionary", "difference",
            "difficulty", "dimension", "direction", "director", "directory", "disadvantage",
            "discovery", "discussion", "disk", "display", "distance", "distribution",
            "district", "diversity", "division", "document", "documentation", "dollar",
            "domain", "door", "doubt", "download", "draft", "driver", "drop", "duration",
            "duty", "economy", "edge", "edition", "editor", "education", "effect",
            "efficiency", "effort", "election", "element", "email", "emergency", "emission",
            "emotion", "emphasis", "employee", "employer", "employment", "encoding", "end",
            "endpoint", "enemy", "energy", "engine", "engineer", "engineering", "enterprise",
            "entertainment", "entity", "entry", "environment", "episode", "equality", "equation",
            "equipment", "era", "error", "escape", "essay", "essence", "establishment",
            "estimate", "evaluation", "event", "evidence", "evolution", "examination", "example",
            "exception", "exchange", "excitement", "execution", "executive", "exercise",
            "existence", "exit", "expansion", "expectation", "expense", "experience",
            "experiment", "expert", "explanation", "exploration", "export", "exposure",
            "expression", "extension", "extent", "extra", "face", "facility", "fact",
            "factor", "factory", "faculty", "failure", "faith", "fall", "family", "fan",
            "farm", "fashion", "fate", "father", "fault", "favor", "favorite", "fear",
            "feature", "fee", "feed", "feedback", "feeling", "festival", "field", "figure",
            "file", "film", "filter", "finance", "finding", "finger", "fire", "firm",
            "fitness", "fix", "flag", "flavor", "flexibility", "flight", "float", "floor",
            "flow", "focus", "folder", "font", "food", "foot", "football", "force",
            "forecast", "forest", "form", "format", "formula", "forum", "foundation",
            "frame", "framework", "freedom", "frequency", "friend", "front", "fruit",
            "fuel", "fun", "function", "fund", "future", "gain", "game", "gap",
            "garden", "gas", "gate", "gateway", "generation", "generator", "genre",
            "gift", "girl", "glass", "global", "goal", "god", "gold", "good",
            "government", "governor", "grade", "grain", "grammar", "graph", "graphic",
            "grass", "gravity", "green", "grid", "ground", "group", "growth", "guarantee",
            "guard", "guess", "guest", "guidance", "guide", "guideline", "gun", "guy",
            "habit", "hair", "half", "hall", "hand", "handle", "handler", "happiness",
            "hardware", "harm", "hat", "head", "header", "headline", "health", "heart",
            "heat", "heaven", "height", "hell", "help", "helper", "hero", "hierarchy",
            "highlight", "hint", "history", "hit", "hole", "holiday", "home", "hook",
            "hope", "horizon", "horse", "hospital", "host", "hotel", "hour", "house",
            "housing", "human", "humor", "hundred", "husband", "icon", "idea", "ideal",
            "identification", "identity", "image", "imagination", "impact", "implementation",
            "implication", "importance", "impression", "improvement", "incentive", "inch",
            "incident", "income", "increase", "independence", "index", "indication", "indicator",
            "individual", "industry", "inflation", "influence", "info", "information",
            "infrastructure", "ingredient", "initiative", "injection", "injury", "innovation",
            "input", "inquiry", "insert", "inside", "insight", "inspection", "inspector",
            "inspiration", "installation", "instance", "institution", "instruction", "instrument",
            "insurance", "integer", "integration", "integrity", "intelligence", "intention",
            "interaction", "interest", "interface", "interior", "internal", "internet",
            "interpretation", "interval", "intervention", "interview", "introduction", "invasion",
            "inventory", "investigation", "investment", "investor", "invitation", "involvement",
            "iron", "island", "isolation", "issue", "item", "iteration", "iterator", "jacket",
            "jail", "java", "javascript", "job", "join", "joint", "joke", "journal",
            "journey", "joy", "judge", "judgment", "juice", "jump", "junction", "junior",
            "jury", "justice", "justification", "kernel", "key", "keyboard", "kid", "kill",
            "kind", "king", "kitchen", "knee", "knife", "knowledge", "label", "labor",
            "laboratory", "lack", "ladder", "lady", "lake", "land", "landscape", "language",
            "laptop", "launch", "law", "lawsuit", "lawyer", "layer", "layout", "lead",
            "leader", "leadership", "league", "learning", "lease", "leather", "leave",
            "lecture", "leg", "legacy", "legend", "legislation", "length", "lesson", "letter",
            "level", "leverage", "liability", "library", "license", "lie", "life", "lifestyle",
            "lifetime", "light", "likelihood", "limit", "limitation", "line", "link", "lion",
            "lip", "liquid", "list", "listener", "listing", "literature", "living", "load",
            "loan", "lobby", "local", "location", "lock", "log", "logger", "logging",
            "logic", "login", "logo", "look", "lookup", "loop", "loss", "lot",
            "love", "luck", "lunch", "machine", "magazine", "magic", "mail", "main",
            "mainstream", "maintenance", "major", "majority", "maker", "makeup", "male",
            "mall", "man", "management", "manager", "manner", "manufacturer", "manufacturing",
            "map", "mapping", "margin", "mark", "marker", "market", "marketing", "marriage",
            "mass", "master", "match", "mate", "material", "math", "mathematics", "matrix",
            "matter", "maximum", "meal", "meaning", "means", "measure", "measurement", "meat",
            "mechanism", "media", "medicine", "medium", "meeting", "member", "membership",
            "memory", "mention", "menu", "merchant", "merge", "merit", "mesh", "message",
            "messenger", "metadata", "metal", "method", "methodology", "metric", "microservice",
            "middle", "midnight", "migration", "mile", "military", "milk", "mill", "million",
            "mind", "mine", "minimum", "minister", "ministry", "minor", "minority", "minute",
            "miracle", "mirror", "mission", "mistake", "mix", "mixture", "mobile", "mock",
            "mode", "model", "modem", "modification", "module", "moment", "momentum", "money",
            "monitor", "monkey", "month", "mood", "moon", "morning", "mortgage", "mother",
            "motion", "motivation", "motor", "mount", "mountain", "mouse", "mouth", "move",
            "movement", "movie", "mud", "multiple", "murder", "muscle", "museum", "music",
            "mystery", "myth", "nail", "name", "namespace", "narrative", "nation", "native",
            "nature", "navigation", "navy", "necessity", "neck", "need", "needle", "negative",
            "negotiation", "neighbor", "neighborhood", "nerve", "nest", "net", "network",
            "news", "newspaper", "night", "nightmare", "node", "noise", "nomination", "none",
            "norm", "normal", "north", "nose", "notation", "note", "notebook", "nothing",
            "notice", "notification", "notion", "noun", "novel", "number", "nurse", "nut",
            "object", "objective", "obligation", "observation", "observer", "obstacle", "occasion",
            "occupation", "occurrence", "ocean", "odd", "odds", "offense", "offer", "office",
            "officer", "official", "offset", "oil", "onion", "online", "opening", "opera",
            "operation", "operator", "opinion", "opponent", "opportunity", "opposition", "option",
            "orange", "orbit", "order", "organization", "orientation", "origin", "original",
            "other", "outcome", "outdoor", "outer", "outlet", "outline", "outlook", "output",
            "outside", "outsider", "oven", "overall", "overflow", "overhead", "overlap", "overnight",
            "override", "overview", "owner", "ownership", "pace", "pack", "package", "packet",
            "pad", "padding", "page", "pain", "paint", "painter", "painting", "pair",
            "palace", "palm", "pan", "panel", "panic", "paper", "parade", "paragraph",
            "parallel", "parameter", "parent", "park", "parking", "parliament", "part",
            "participant", "participation", "particle", "particular", "partner", "partnership",
            "party", "pass", "passage", "passenger", "passion", "passport", "password", "past",
            "paste", "patch", "path", "patience", "patient", "pattern", "pause", "pay",
            "payload", "payment", "peace", "peak", "pen", "penalty", "pension", "people",
            "pepper", "percent", "percentage", "perception", "perfect", "performance", "period",
            "permission", "permit", "person", "personality", "personnel", "perspective", "petition",
            "phase", "phenomenon", "philosophy", "phone", "photo", "photograph", "photographer",
            "phrase", "physical", "physics", "piano", "picture", "pie", "piece", "pig",
            "pile", "pilot", "pin", "pink", "pipe", "pipeline", "pit", "pitch",
            "pizza", "place", "placement", "plain", "plaintiff", "plan", "plane", "planet",
            "planning", "plant", "plastic", "plate", "platform", "play", "player", "playground",
            "plea", "pleasure", "pledge", "plenty", "plot", "plug", "plugin", "plus",
            "pocket", "poem", "poet", "poetry", "point", "pointer", "poison", "poker",
            "pole", "police", "policy", "politics", "poll", "pollution", "pond", "pool",
            "poor", "pop", "population", "port", "portal", "portfolio", "portion", "portrait",
            "pose", "position", "positive", "possession", "possibility", "post", "pot", "potato",
            "potential", "pound", "poverty", "powder", "power", "practice", "praise", "prayer",
            "precedent", "precision", "prediction", "preference", "prefix", "pregnancy", "prejudice",
            "preliminary", "premise", "premium", "preparation", "presence", "present", "presentation",
            "preservation", "presidency", "president", "press", "pressure", "preview", "price",
            "pride", "priest", "primary", "prime", "prince", "princess", "principal", "principle",
            "print", "printer", "printing", "prior", "priority", "prison", "prisoner", "privacy",
            "private", "privilege", "prize", "probability", "probe", "problem", "procedure",
            "proceed", "process", "processing", "processor", "producer", "product", "production",
            "profession", "professional", "professor", "profile", "profit", "program", "programmer",
            "programming", "progress", "progression", "project", "projection", "promise", "promotion",
            "prompt", "proof", "propaganda", "proper", "property", "proportion", "proposal",
            "proposition", "prosecution", "prospect", "protection", "protein", "protest", "protocol",
            "proud", "provider", "province", "provision", "proxy", "psychology", "public",
            "publication", "publicity", "publisher", "publishing", "pull", "pulse", "pump",
            "punishment", "pupil", "purchase", "purple", "purpose", "pursuit", "push", "puzzle",
            "qualification", "quality", "quantity", "quarter", "queen", "query", "quest",
            "question", "queue", "quick", "quiet", "quit", "quiz", "quota", "quotation",
            "quote", "race", "racing", "radar", "radiation", "radical", "radio", "radius",
            "rage", "raid", "rail", "railroad", "railway", "rain", "rainbow", "raise",
            "rally", "ram", "ranch", "random", "range", "rank", "ranking", "rapid",
            "rat", "rate", "ratio", "raw", "ray", "reach", "reaction", "read",
            "reader", "reading", "ready", "real", "reality", "realization", "realm", "rear",
            "reason", "reasoning", "rebel", "rebellion", "receipt", "receiver", "reception",
            "recession", "recipe", "recipient", "recognition", "recommendation", "reconstruction",
            "record", "recorder", "recording", "recovery", "recreation", "recruit", "recruitment",
            "red", "reduction", "reef", "reference", "referendum", "reflection", "reform",
            "refugee", "refusal", "regard", "regime", "region", "regional", "register",
            "registration", "regret", "regular", "regulation", "regulator", "rehabilitation",
            "reign", "rejection", "relation", "relationship", "relative", "relaxation", "relay",
            "release", "relevance", "reliability", "relief", "religion", "reluctance", "remainder",
            "remains", "remark", "remedy", "reminder", "remote", "removal", "renaissance",
            "render", "rendering", "renewal", "rent", "rental", "repair", "repeat", "repetition",
            "replacement", "replica", "reply", "report", "reporter", "reporting", "repository",
            "representation", "representative", "reproduction", "republic", "republican", "reputation",
            "request", "requirement", "rescue", "research", "researcher", "reservation", "reserve",
            "reservoir", "residence", "resident", "residential", "resignation", "resistance", "resolution",
            "resolve", "resort", "resource", "respect", "response", "responsibility", "rest",
            "restaurant", "restoration", "restriction", "restructuring", "result", "resume", "retail",
            "retailer", "retention", "retirement", "retreat", "return", "revelation", "revenue",
            "reverse", "review", "reviewer", "revision", "revival", "revolution", "reward",
            "rhetoric", "rhythm", "rice", "rich", "ride", "rider", "ridge", "rifle",
            "right", "ring", "riot", "rise", "risk", "ritual", "rival", "river",
            "road", "robot", "rock", "rocket", "rod", "role", "roll", "romance",
            "roof", "room", "root", "rope", "rose", "rotation", "rough", "round",
            "route", "router", "routine", "row", "royal", "rubber", "ruin", "rule",
            "ruling", "rumor", "run", "runner", "running", "runtime", "rural", "rush",
            "rust", "sacrifice", "sad", "sadness", "safe", "safety", "saint", "sake",
            "salad", "salary", "sale", "salmon", "salon", "salt", "salvation", "sample",
            "sanction", "sanctuary", "sand", "sandwich", "satellite", "satisfaction", "sauce",
            "saving", "scale", "scandal", "scanner", "scenario", "scene", "schedule", "schema",
            "scheme", "scholar", "scholarship", "school", "science", "scientist", "scope", "score",
            "scratch", "screen", "screening", "screenplay", "script", "sculpture", "sea", "seal",
            "search", "searching", "season", "seat", "second", "secret", "secretary", "section",
            "sector", "secure", "security", "seed", "segment", "segregation", "seizure", "selection",
            "selector", "self", "seller", "seminar", "senate", "senator", "sender", "senior",
            "sensation", "sense", "sensitivity", "sensor", "sentence", "sentiment", "separation",
            "sequence", "serial", "series", "sermon", "servant", "server", "service", "session",
            "setting", "settlement", "setup", "shade", "shadow", "shaft", "shake", "shame",
            "shape", "share", "shareholder", "sharing", "shark", "sheep", "sheet", "shelf",
            "shell", "shelter", "sheriff", "shield", "shift", "shine", "ship", "shipment",
            "shipping", "shirt", "shock", "shoe", "shoot", "shooting", "shop", "shopping",
            "shore", "short", "shortage", "shortcut", "shot", "shoulder", "shout", "show",
            "shower", "shut", "shutdown", "sibling", "sick", "side", "sidebar", "siege",
            "sight", "sign", "signal", "signature", "significance", "silence", "silicon", "silk",
            "silver", "similarity", "simple", "simplicity", "simulation", "sin", "singer", "singing",
            "single", "sink", "sir", "sister", "sit", "site", "situation", "size",
            "skeleton", "sketch", "ski", "skiing", "skill", "skin", "skirt", "skull",
            "sky", "slave", "slavery", "sleep", "slice", "slide", "slider", "slip",
            "slope", "slot", "slow", "small", "smart", "smell", "smile", "smoke",
            "smoking", "smooth", "snake", "snap", "snapshot", "snow", "soap", "soccer",
            "social", "socialism", "socialist", "society", "socket", "soft", "software", "soil",
            "solar", "soldier", "sole", "solid", "solidarity", "solution", "son", "song",
            "sophistication", "sort", "sorting", "soul", "sound", "soup", "source", "south",
            "southern", "sovereignty", "space", "spam", "span", "spare", "spark", "speaker",
            "speaking", "spec", "special", "specialist", "species", "specification", "specimen",
            "spectacle", "spectrum", "speculation", "speech", "speed", "spell", "spelling",
            "spending", "sphere", "spider", "spin", "spine", "spirit", "spiritual", "spite",
            "split", "spokesman", "sponsor", "sponsorship", "spoon", "sport", "spot", "spotlight",
            "spouse", "spread", "spreadsheet", "spring", "spy", "squad", "square", "squeeze",
            "stability", "stable", "stack", "stadium", "staff", "stage", "stake", "stamp",
            "stance", "stand", "standard", "standing", "star", "start", "startup", "state",
            "statement", "station", "statistic", "statistics", "statue", "status", "statute",
            "stay", "steady", "steal", "steam", "steel", "steep", "stem", "step",
            "stereotype", "stick", "sticker", "still", "stimulus", "stock", "stomach", "stone",
            "stop", "storage", "store", "storm", "story", "strain", "strand", "stranger",
            "strategic", "strategy", "straw", "stream", "streaming", "street", "strength", "stress",
            "stretch", "strike", "string", "strip", "stripe", "stroke", "structure", "struggle",
            "stub", "student", "studio", "study", "stuff", "stupid", "style", "subject",
            "submission", "subscriber", "subscription", "subsidy", "substance", "substitute", "substitution",
            "subtle", "suburb", "success", "succession", "successor", "suffering", "sugar", "suggestion",
            "suicide", "suit", "suite", "sum", "summary", "summer", "summit", "sun",
            "sunday", "sunshine", "super", "supermarket", "supervision", "supervisor", "supplement",
            "supplier", "supply", "support", "supporter", "suppose", "supreme", "surface", "surge",
            "surgeon", "surgery", "surplus", "surprise", "surrender", "surveillance", "survey",
            "survival", "survivor", "suspect", "suspension", "suspicion", "sustainability", "swap",
            "sweat", "sweep", "sweet", "swim", "swimming", "swing", "switch", "sword",
            "symbol", "sympathy", "symptom", "sync", "synchronization", "syndrome", "syntax",
            "synthesis", "system", "tab", "table", "tablet", "tactic", "tag", "tail",
            "tale", "talent", "talk", "tall", "tank", "tap", "tape", "target",
            "tariff", "task", "taste", "tax", "taxi", "tea", "teacher", "teaching",
            "team", "teammate", "tear", "tech", "technique", "technology", "teen", "teenager",
            "telephone", "television", "tell", "temperature", "template", "temple", "tempo", "temporary",
            "tenant", "tendency", "tender", "tennis", "tension", "tent", "tenure", "term",
            "terminal", "termination", "terminology", "terms", "terrain", "territory", "terror",
            "terrorism", "terrorist", "test", "testament", "testimony", "testing", "text",
            "texture", "thank", "thanks", "thanksgiving", "theater", "theft", "theme", "theology",
            "theorem", "theoretical", "theory", "therapy", "thesis", "thickness", "thief", "thing",
            "thinking", "third", "thought", "thousand", "thread", "threat", "threshold", "throat",
            "throne", "throw", "thrust", "thumb", "thunder", "ticket", "tide", "tie",
            "tiger", "tight", "tile", "timber", "time", "timeline", "timeout", "timer",
            "timestamp", "timing", "tin", "tip", "tire", "tissue", "title", "toast",
            "tobacco", "today", "toe", "toilet", "token", "tolerance", "toll", "tomato",
            "tomorrow", "ton", "tone", "tongue", "tonight", "tool", "toolbar", "toolkit",
            "tooth", "top", "topic", "torch", "tornado", "torture", "total", "touch",
            "tough", "tour", "tourism", "tourist", "tournament", "tower", "town", "toy",
            "trace", "track", "tracker", "tracking", "tract", "trade", "trademark", "trader",
            "trading", "tradition", "traffic", "tragedy", "trail", "trailer", "train", "trainer",
            "training", "trait", "transaction", "transcript", "transfer", "transformation", "transformer",
            "transit", "transition", "translation", "translator", "transmission", "transparency", "transport",
            "transportation", "trap", "trash", "travel", "traveler", "tray", "treasure", "treasury",
            "treat", "treatment", "treaty", "tree", "tremendous", "trend", "trial", "triangle",
            "tribe", "tribunal", "tribute", "trick", "trigger", "trillion", "trim", "trip",
            "triumph", "troop", "trophy", "trouble", "truck", "true", "truly", "trump",
            "trunk", "trust", "trustee", "truth", "try", "tube", "tuition", "tumor",
            "tune", "tunnel", "turkey", "turn", "turnout", "turnover", "tutor", "tutorial",
            "twin", "twist", "type", "uncle", "understanding", "unemployment", "uniform", "union",
            "unique", "unit", "unity", "universal", "universe", "university", "unknown", "update",
            "upgrade", "upload", "upper", "upset", "upstairs", "urban", "urge", "urgency",
            "url", "usage", "use", "user", "username", "utility", "utilization", "vacation",
            "vaccine", "vacuum", "validation", "validity", "valley", "valuable", "valuation", "value",
            "van", "variable", "variance", "variation", "variety", "vast", "vector", "vegetable",
            "vegetation", "vehicle", "vein", "velocity", "vendor", "venue", "verb", "verdict",
            "verification", "version", "vessel", "veteran", "via", "victim", "victory", "video",
            "view", "viewer", "viewpoint", "village", "villain", "violation", "violence", "violin",
            "virgin", "virtual", "virtue", "virus", "visa", "visibility", "visible", "vision",
            "visit", "visitor", "visual", "vitamin", "vocabulary", "voice", "void", "volatile",
            "volcano", "voltage", "volume", "volunteer", "vote", "voter", "voting", "vulnerability",
            "wage", "wagon", "wait", "wake", "walk", "walker", "walking", "wall",
            "wallet", "want", "war", "ward", "warehouse", "warfare", "warm", "warmth",
            "warning", "warrant", "warranty", "warrior", "wash", "waste", "watch", "watcher",
            "water", "wave", "wavelength", "way", "weakness", "wealth", "weapon", "wear",
            "weather", "web", "webinar", "webpage", "website", "wedding", "wednesday", "weed",
            "week", "weekend", "weekly", "weight", "weird", "welcome", "welfare", "well",
            "west", "western", "wet", "whale", "wheat", "wheel", "whenever", "wherever",
            "while", "whisper", "white", "whole", "wholesale", "why", "wide", "widow",
            "width", "wife", "wild", "wilderness", "wildlife", "will", "willingness", "win",
            "wind", "window", "wine", "wing", "winner", "winning", "winter", "wire",
            "wireless", "wisdom", "wise", "wish", "witch", "withdrawal", "witness", "wizard",
            "woman", "wonder", "wood", "woods", "wool", "word", "work", "worker",
            "workflow", "workforce", "working", "workload", "workout", "workplace", "workshop",
            "workspace", "workstation", "world", "worldwide", "worm", "worried", "worry", "worse",
            "worship", "worst", "worth", "wound", "wrap", "wrapper", "wrapping", "wrist",
            "write", "writer", "writing", "wrong", "yard", "year", "yellow", "yesterday",
            "yield", "yoga", "young", "youngster", "yourself", "youth", "zone", "zoo",

            // Common adjectives and adverbs (expanded)
            "able", "absolute", "abstract", "acceptable", "accessible", "accurate", "active",
            "actual", "additional", "adequate", "adjacent", "administrative", "advanced", "adverse",
            "aesthetic", "affordable", "aggressive", "alive", "alone", "alternative", "amazing",
            "ambitious", "ancient", "angry", "annual", "anonymous", "another", "anxious", "apparent",
            "applicable", "appropriate", "approximate", "arbitrary", "architectural", "artificial",
            "artistic", "asynchronous", "atomic", "attractive", "automatic", "autonomous", "available",
            "average", "aware", "awful", "awkward", "backward", "bad", "bare", "basic",
            "beautiful", "beneficial", "best", "better", "big", "binary", "biological", "bitter",
            "bizarre", "blank", "blind", "bloody", "blue", "bold", "boolean", "boring",
            "born", "both", "bottom", "brave", "brief", "bright", "brilliant", "broad",
            "broken", "brown", "brutal", "built", "bulk", "busy", "calm", "capable",
            "capital", "careful", "casual", "central", "certain", "challenging", "cheap", "chemical",
            "chief", "chronic", "circular", "civil", "classic", "classical", "clean", "clear",
            "clever", "clinical", "close", "closed", "closest", "cognitive", "cold", "collaborative",
            "collective", "colonial", "colorful", "comfortable", "commercial", "common", "communist",
            "compact", "comparable", "comparative", "compatible", "compelling", "competitive", "complete",
            "complex", "complicated", "comprehensive", "computational", "computational", "concentrated",
            "conceptual", "concerned", "concrete", "concurrent", "conditional", "confident", "confidential",
            "confused", "connected", "conscious", "consecutive", "conservative", "considerable", "consistent",
            "constant", "constitutional", "constructive", "contemporary", "content", "contextual", "continental",
            "continuous", "contrary", "convenient", "conventional", "cool", "cooperative", "corporate",
            "correct", "corrupt", "cosmic", "costly", "countless", "coupled", "cozy", "crazy",
            "creative", "criminal", "critical", "crucial", "crude", "cruel", "cultural", "cumulative",
            "curious", "current", "custom", "cute", "daily", "dangerous", "dark", "dead",
            "dear", "decent", "decisive", "dedicated", "deep", "definite", "deliberate", "delicate",
            "democratic", "dense", "dependent", "depressed", "descriptive", "designed", "desirable",
            "desperate", "detailed", "determined", "different", "difficult", "digital", "diplomatic",
            "direct", "dirty", "disabled", "disappointed", "discrete", "distant", "distinct", "distinctive",
            "distributed", "diverse", "divine", "documentary", "domestic", "dominant", "double", "dramatic",
            "driven", "dry", "dual", "dumb", "durable", "dynamic", "eager", "early",
            "easier", "east", "eastern", "easy", "economic", "educational", "effective", "efficient",
            "eighth", "elaborate", "elderly", "electoral", "electric", "electrical", "electronic", "elegant",
            "elementary", "elevated", "eligible", "elite", "else", "embarrassed", "embedded", "emerging",
            "emotional", "empirical", "empty", "enclosed", "endless", "energetic", "engaging", "enhanced",
            "enormous", "enough", "entire", "environmental", "equal", "equivalent", "essential", "established",
            "ethical", "ethnic", "european", "eventual", "every", "everyday", "evident", "evil",
            "evolutionary", "exact", "excellent", "exceptional", "excess", "excessive", "excited", "exciting",
            "exclusive", "executable", "exhausted", "existing", "exotic", "expanded", "expected", "expensive",
            "experienced", "experimental", "expert", "explicit", "explosive", "exposed", "express", "extended",
            "extensive", "external", "extra", "extraordinary", "extreme", "facial", "factual", "failed",
            "fair", "fake", "false", "familiar", "famous", "fancy", "fantastic", "far",
            "fascinating", "fashionable", "fast", "fat", "fatal", "favorable", "favorite", "feasible",
            "federal", "fellow", "female", "feminist", "few", "fewer", "fierce", "fifth",
            "final", "financial", "fine", "finished", "finite", "firm", "fiscal", "fit",
            "fixed", "flat", "flexible", "floating", "fluent", "fluid", "focused", "following",
            "foolish", "forced", "foreign", "foremost", "formal", "former", "formidable", "forth",
            "forward", "founded", "fourth", "fragile", "free", "frequent", "fresh", "friendly",
            "frightened", "front", "frozen", "fruitful", "frustrated", "frustrating", "fulfilled", "full",
            "fully", "fun", "functional", "fundamental", "funded", "funny", "furious", "further",
            "future", "fuzzy", "general", "generic", "generous", "genetic", "gentle", "genuine",
            "geographical", "giant", "gifted", "given", "glad", "global", "golden", "gone",
            "good", "gorgeous", "governmental", "graceful", "gradual", "grand", "graphic", "grateful",
            "grave", "gray", "great", "greater", "greatest", "greek", "green", "grey",
            "gross", "grounded", "growing", "guaranteed", "guilty", "handsome", "handy", "happy",
            "hard", "harmful", "harsh", "headed", "healthy", "heard", "heavy", "helpful",
            "hidden", "hierarchical", "high", "higher", "highest", "historic", "historical", "holistic",
            "hollow", "holy", "homeless", "honest", "horizontal", "horrible", "hostile", "hot",
            "hourly", "huge", "human", "humble", "hungry", "hybrid", "hypothetical", "ideal",
            "identical", "ideological", "idle", "ignorant", "ill", "illegal", "illustrative", "immediate",
            "immense", "imminent", "immune", "imperial", "implicit", "important", "impossible", "impressed",
            "impressive", "improved", "inadequate", "inappropriate", "inclined", "included", "inclusive", "incomplete",
            "inconsistent", "incorporated", "incorrect", "incredible", "incremental", "indefinite", "independent",
            "indicative", "indirect", "individual", "indoor", "industrial", "inevitable", "infamous", "infinite",
            "influential", "informal", "inherent", "initial", "injured", "inland", "inner", "innocent",
            "innovative", "insane", "instant", "institutional", "instructional", "instrumental", "insufficient",
            "integrated", "intellectual", "intelligent", "intense", "intensive", "intentional", "interactive",
            "interested", "interesting", "interim", "interior", "intermediate", "internal", "international",
            "intimate", "intriguing", "intrinsic", "invalid", "invaluable", "inverse", "invisible", "involved",
            "irrational", "irregular", "irrelevant", "isolated", "iterative", "joint", "judicial", "junior",
            "just", "keen", "key", "kind", "known", "labeled", "lacking", "lame",
            "landed", "large", "larger", "largest", "last", "lasting", "late", "later",
            "latest", "latter", "lazy", "lead", "leading", "lean", "learned", "least",
            "left", "leftist", "legal", "legendary", "legislative", "legitimate", "lengthy", "less",
            "lesser", "lethal", "level", "liable", "liberal", "licensed", "light", "lightweight",
            "likely", "limited", "linear", "linguistic", "linked", "liquid", "literary", "little",
            "live", "lively", "living", "loaded", "local", "located", "locked", "logical",
            "lone", "lonely", "long", "longer", "longest", "loose", "lost", "loud",
            "lovely", "low", "lower", "lowest", "loyal", "lucky", "luxurious", "macro",
            "mad", "magic", "magical", "magnetic", "magnificent", "main", "mainstream", "major",
            "male", "malicious", "managed", "mandatory", "manifest", "manual", "marginal", "marine",
            "marked", "married", "masculine", "massive", "master", "matched", "material", "mathematical",
            "mature", "maximum", "meaningful", "mechanical", "medical", "medieval", "medium", "mega",
            "mental", "mere", "micro", "middle", "mild", "military", "million", "mind",
            "mindful", "minimal", "minimum", "minor", "minute", "miraculous", "miserable", "missing",
            "mixed", "mobile", "mock", "modal", "moderate", "modern", "modest", "modified",
            "modular", "molecular", "monetary", "monthly", "moral", "more", "moreover", "most",
            "motivated", "moving", "much", "multi", "multicultural", "multimedia", "multinational", "multiple",
            "municipal", "mutual", "mysterious", "naive", "naked", "narrow", "nasty", "national",
            "native", "natural", "naval", "near", "nearby", "nearest", "neat", "necessary",
            "negative", "neighboring", "nervous", "nested", "net", "neutral", "never", "nevertheless",
            "new", "newer", "newest", "next", "nice", "ninth", "noble", "nominal",
            "none", "nonprofit", "normal", "north", "northern", "notable", "nothing", "noticeable",
            "notorious", "novel", "nuclear", "null", "numerous", "objective", "obligated", "observable",
            "obvious", "occasional", "occupied", "odd", "offensive", "official", "okay", "old",
            "older", "oldest", "once", "ongoing", "online", "only", "open", "opened",
            "operational", "opposite", "optical", "optimal", "optimistic", "optional", "oral", "orange",
            "ordinary", "organic", "organizational", "organized", "oriented", "original", "orthodox", "other",
            "otherwise", "outdoor", "outer", "outstanding", "overall", "overhead", "overseas", "overwhelming",
            "own", "owned", "pacific", "packed", "painful", "pale", "pandemic", "parallel",
            "paramount", "parental", "parliamentary", "partial", "particular", "passionate", "passive", "past",
            "patient", "peculiar", "peer", "pending", "perceived", "perfect", "periodic", "peripheral",
            "permanent", "permitted", "persistent", "personal", "persuasive", "petty", "philosophical", "physical",
            "pink", "pioneer", "plain", "planned", "plastic", "pleasant", "pleased", "plenty",
            "plural", "plus", "pointed", "polar", "polite", "political", "poor", "pop",
            "popular", "portable", "positive", "possible", "postal", "posted", "potential", "powerful",
            "practical", "precious", "precise", "predictable", "predicted", "predominant", "preferential", "preferred",
            "pregnant", "preliminary", "premier", "premium", "prepared", "present", "presidential", "pressing",
            "prestigious", "pretty", "prevalent", "preventive", "previous", "priced", "priceless", "primarily",
            "primary", "prime", "primitive", "principal", "printed", "prior", "private", "privileged",
            "proactive", "probable", "problematic", "procedural", "productive", "professional", "profitable", "profound",
            "progressive", "projected", "prolonged", "prominent", "promising", "promotional", "prompt", "prone",
            "pronounced", "proper", "proposed", "proprietary", "prospective", "protective", "protestant", "proud",
            "proven", "provincial", "provocative", "proximate", "proxy", "psychiatric", "psychological", "public",
            "published", "pure", "purple", "pursuant", "qualified", "qualitative", "quantitative", "quarterly",
            "quasi", "quick", "quiet", "quite", "racial", "radical", "random", "rapid",
            "rare", "rated", "rational", "raw", "ready", "real", "realistic", "reasonable",
            "rebel", "recent", "reciprocal", "reckless", "recognized", "recommended", "recorded", "recovered",
            "recreational", "rectangular", "recurrent", "recursive", "red", "reduced", "redundant", "reference",
            "refined", "reflected", "reformed", "regional", "registered", "regular", "regulated", "regulatory",
            "reinforced", "related", "relative", "relaxed", "released", "relevant", "reliable", "relieved",
            "religious", "reluctant", "remaining", "remarkable", "remote", "removable", "removed", "renewable",
            "renowned", "rental", "repeated", "repetitive", "replaced", "reported", "representative", "reproduced",
            "republican", "reputable", "requested", "required", "reserved", "resident", "residential", "residual",
            "resistant", "resolved", "respective", "responsible", "responsive", "restricted", "restrictive", "resulting",
            "retail", "retained", "retired", "reverse", "revised", "revolutionary", "rich", "ridiculous",
            "right", "rigid", "rigorous", "risky", "rival", "robust", "rolled", "romantic",
            "root", "rotational", "rough", "round", "routine", "royal", "rubber", "rude",
            "rugged", "running", "rural", "rushed", "russian", "sacred", "sad", "safe",
            "safer", "safest", "said", "same", "satisfied", "satisfying", "savage", "saved",
            "scalable", "scattered", "scenic", "scheduled", "scientific", "seasonal", "seated", "second",
            "secondary", "secret", "secular", "secure", "selected", "selective", "semantic", "semi",
            "senior", "sensational", "sensible", "sensitive", "sent", "sentimental", "separate", "separated",
            "sequential", "serial", "serious", "served", "set", "settled", "seventh", "several",
            "severe", "shallow", "shaped", "shared", "sharp", "sheer", "shifted", "shocking",
            "short", "shorter", "shortest", "shy", "sick", "sided", "significant", "silent",
            "silly", "silver", "similar", "simple", "simpler", "simplest", "simplified", "simulated",
            "simultaneous", "since", "sincere", "single", "singular", "situated", "sixth", "sized",
            "skeptical", "skilled", "slight", "slim", "slippery", "slow", "slower", "slowest",
            "small", "smaller", "smallest", "smart", "smarter", "smartest", "smooth", "sneaky",
            "so", "sober", "social", "socialist", "societal", "soft", "softer", "softest",
            "solar", "sold", "sole", "solid", "solo", "solved", "some", "somebody",
            "someone", "something", "sometime", "sometimes", "somewhat", "soon", "sooner", "soonest",
            "sophisticated", "sorry", "sorted", "sought", "sound", "sour", "south", "southeast",
            "southern", "southwest", "sovereign", "soviet", "spare", "sparse", "spatial", "special",
            "specialized", "specific", "specified", "spectacular", "speculative", "spherical", "spiritual", "splendid",
            "split", "spoken", "spontaneous", "sporting", "spotted", "spread", "square", "squeezed",
            "stable", "stacked", "staffed", "staged", "stale", "standalone", "standard", "standardized",
            "standing", "stark", "starting", "stated", "static", "statistical", "statutory", "steady",
            "steep", "stellar", "stepped", "sticky", "stiff", "still", "stimulating", "stock",
            "stored", "straight", "straightforward", "strange", "stranger", "strategic", "streamlined", "street",
            "strict", "stricter", "strictest", "striking", "stringent", "stripped", "strong", "stronger",
            "strongest", "struck", "structural", "structured", "stubborn", "stuck", "studied", "stunning",
            "stupid", "stylish", "sub", "subject", "subjective", "sublime", "subordinate", "subsequent",
            "substantial", "substantive", "subtle", "suburban", "successful", "successive", "sudden", "sufficient",
            "suitable", "suited", "summary", "sunny", "super", "superb", "superficial", "superior",
            "supplemental", "supplementary", "supplied", "supported", "supposed", "supreme", "sure", "surface",
            "surgical", "surplus", "surprised", "surprising", "surrounded", "surrounding", "suspected", "suspended",
            "suspicious", "sustainable", "sweet", "swift", "symbolic", "sympathetic", "synchronized", "synthetic",
            "systematic", "tactical", "talented", "tall", "taller", "tallest", "tangible", "targeted",
            "technical", "technological", "tedious", "teenage", "temporary", "tender", "tense", "tenth",
            "terminal", "terrible", "terrific", "territorial", "tested", "textual", "thankful", "theatrical",
            "thematic", "then", "theoretical", "therapeutic", "thereby", "thermal", "thick", "thicker",
            "thickest", "thin", "thinner", "thinnest", "third", "thorough", "thoughtful", "threatened",
            "thrilled", "thriving", "tidy", "tied", "tight", "tighter", "tightest", "timeless",
            "timely", "tiny", "tired", "titled", "together", "tolerable", "tolerant", "too",
            "top", "topical", "torn", "total", "totalitarian", "touched", "tough", "tougher",
            "toughest", "toxic", "tracked", "traditional", "tragic", "trained", "tranquil", "transactional",
            "transcendent", "transferable", "transformed", "transient", "transitional", "translated", "transparent", "trapped",
            "tremendous", "trendy", "tribal", "tricky", "triggered", "triple", "trivial", "tropical",
            "troubled", "true", "truer", "truest", "truly", "trusted", "truthful", "turned",
            "twisted", "typical", "ugly", "ultimate", "ultra", "unable", "unanimous", "unaware",
            "uncertain", "unchanged", "unclear", "uncomfortable", "uncommon", "unconditional", "unconscious", "undefined",
            "underground", "underlying", "understood", "underwater", "unemployed", "unequal", "unexpected", "unfair",
            "unfamiliar", "unfortunate", "unhappy", "unified", "uniform", "unilateral", "unintended", "unique",
            "united", "universal", "unknown", "unlawful", "unlike", "unlikely", "unlimited", "unnecessary",
            "unofficial", "unprecedented", "unpredictable", "unrelated", "unsafe", "unsuccessful", "unusual", "unwanted",
            "unwilling", "upcoming", "updated", "upper", "upscale", "upset", "upward", "urban",
            "urgent", "useful", "useless", "usual", "utmost", "utter", "vacant", "vague",
            "valid", "valuable", "variable", "varied", "various", "varying", "vast", "vegetarian",
            "verbal", "versatile", "vertical", "very", "viable", "vibrant", "vicious", "video",
            "vigorous", "violent", "viral", "virgin", "virtual", "virtually", "visible", "visual",
            "vital", "vivid", "vocal", "void", "volatile", "voluntary", "vulnerable", "waiting",
            "wandering", "warm", "warmer", "warmest", "wary", "wasted", "watchful", "weak",
            "weaker", "weakest", "wealthy", "wearing", "weary", "weird", "welcome", "well",
            "west", "western", "wet", "wetter", "wettest", "whatever", "whenever", "where",
            "whereas", "whereby", "wherever", "whether", "which", "while", "white", "whole",
            "wholesale", "whose", "wicked", "wide", "wider", "widest", "widespread", "wild",
            "wilder", "wildest", "willing", "winning", "wise", "wiser", "wisest", "withdrawn",
            "wonderful", "wooden", "working", "worldwide", "worried", "worrying", "worse", "worst",
            "worthwhile", "worthy", "wounded", "wrapped", "written", "wrong", "yearly", "yellow",
            "young", "younger", "youngest"
        )
    }

    private fun loadCustomDictionary() {
        try {
            val file = File(customDictionaryPath)
            if (file.exists()) {
                file.useLines { lines ->
                    lines.forEach { line ->
                        val word = line.trim().lowercase(Locale.US)
                        if (word.isNotEmpty()) {
                            customDictionary.add(word)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[SimpleSpellChecker] Failed to load custom dictionary: ${e.message}")
        }
    }

    private fun saveCustomDictionary() {
        try {
            val file = File(customDictionaryPath)
            file.parentFile?.mkdirs()
            file.writeText(customDictionary.sorted().joinToString("\n"))
        } catch (e: Exception) {
            println("[SimpleSpellChecker] Failed to save custom dictionary: ${e.message}")
        }
    }

    override fun check(word: String): Boolean {
        if (word.isBlank()) return true

        // Ensure dictionary is loaded (lazy initialization)
        ensureInitialized()

        val normalized = word.lowercase(Locale.US)

        // Skip checking for:
        // - Single characters
        // - Numbers
        // - Words with digits
        // - CamelCase parts (will be split by caller)
        if (normalized.length <= 1) return true
        if (normalized.all { it.isDigit() }) return true
        if (normalized.any { it.isDigit() }) return true

        // Check custom dictionary first (user added words)
        if (customDictionary.contains(normalized)) return true

        // Check main dictionary
        if (dictionary.contains(normalized)) return true

        // Check with common suffixes removed
        for (suffix in listOf("s", "es", "ed", "ing", "er", "est", "ly", "ness", "ment", "tion", "able", "ible")) {
            if (normalized.endsWith(suffix)) {
                val base = normalized.dropLast(suffix.length)
                if (base.length >= 2 && dictionary.contains(base)) return true
            }
        }

        return false
    }

    override fun suggest(word: String): List<String> {
        if (word.isBlank()) return emptyList()

        // Ensure dictionary is loaded (lazy initialization)
        ensureInitialized()

        val normalized = word.lowercase(Locale.US)
        val suggestions = mutableListOf<Pair<String, Int>>()

        // Use prefix map for efficient candidate lookup instead of O(n) scan
        // Check words with same prefix and similar prefixes (off-by-one first char)
        val candidatePrefixes = mutableSetOf<String>()
        if (normalized.length >= 2) {
            candidatePrefixes.add(normalized.substring(0, 2))
            // Add adjacent prefixes for typos in first two characters
            val firstChar = normalized[0]
            val secondChar = normalized[1]
            for (c in listOf(firstChar - 1, firstChar + 1)) {
                if (c.isLetter()) {
                    candidatePrefixes.add("$c$secondChar")
                }
            }
            for (c in listOf(secondChar - 1, secondChar + 1)) {
                if (c.isLetter()) {
                    candidatePrefixes.add("$firstChar$c")
                }
            }
        }

        // Get candidate words from prefix map (much smaller set than full dictionary)
        val candidates = candidatePrefixes.flatMap { prefix ->
            prefixMap[prefix] ?: emptySet()
        }.toSet() + customDictionary

        // Find words with small edit distance from candidates only
        for (dictWord in candidates) {
            if (dictWord.length in (normalized.length - 2)..(normalized.length + 2)) {
                val distance = levenshteinDistance(normalized, dictWord)
                if (distance <= 2) {
                    suggestions.add(dictWord to distance)
                }
            }
        }

        // Sort by edit distance and return top suggestions
        return suggestions
            .sortedBy { it.second }
            .take(5)
            .map { it.first }
    }

    /**
     * Computes Levenshtein edit distance using single-row optimization.
     * Space complexity: O(min(m,n)) instead of O(m*n).
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        if (m == 0) return n
        if (n == 0) return m

        // Ensure s2 is the shorter string for space efficiency
        val (shorter, longer) = if (m < n) s1 to s2 else s2 to s1
        val shortLen = shorter.length
        val longLen = longer.length

        // Single row DP - only need current and previous row values
        var prevRow = IntArray(shortLen + 1) { it }
        var currRow = IntArray(shortLen + 1)

        for (i in 1..longLen) {
            currRow[0] = i
            for (j in 1..shortLen) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                currRow[j] = minOf(
                    prevRow[j] + 1,        // deletion
                    currRow[j - 1] + 1,    // insertion
                    prevRow[j - 1] + cost  // substitution
                )
            }
            // Swap rows
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }

        return prevRow[shortLen]
    }

    override fun addToDictionary(word: String) {
        val normalized = word.lowercase(Locale.US)
        if (normalized.isNotBlank()) {
            customDictionary.add(normalized)
            saveCustomDictionary()
        }
    }

    override fun isInCustomDictionary(word: String): Boolean {
        return customDictionary.contains(word.lowercase(Locale.US))
    }

    override fun getCustomDictionaryWords(): Set<String> = customDictionary.toSet()

    override fun removeFromDictionary(word: String) {
        val normalized = word.lowercase(Locale.US)
        if (customDictionary.remove(normalized)) {
            saveCustomDictionary()
        }
    }

    override fun isReady(): Boolean = isInitialized

    override fun getLanguage(): String = currentLanguage

    override fun setLanguage(languageCode: String): Boolean {
        // Simple implementation only supports English
        return if (languageCode.startsWith("en")) {
            currentLanguage = languageCode
            true
        } else {
            false
        }
    }

    override fun getAvailableLanguages(): List<String> = listOf("en_US", "en_GB")
}
