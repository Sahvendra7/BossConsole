package ai.rever.boss.mcp

import ai.rever.boss.plugin.api.McpExecutionOutcome
import ai.rever.boss.plugin.api.McpExecutionRequest
import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolExecutionObserver
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [McpToolRegistryCore] — the testable core behind the
 * process-wide [McpToolRegistryImpl] singleton. Covers the pure logic that 19
 * downstream plugins depend on: RBAC gating ([McpToolRegistryImpl] KDoc
 * "Security posture"), first-wins dedup across providers, the happy path of
 * disabled-set persistence, argument scalar coercion, and
 * [McpToolRegistryCore.invoke]'s timeout/cancellation/rejection contract.
 *
 * The kill-switch's *failure* behaviour — what happens when the persisted set
 * cannot be read or written (BossConsole#85) — lives in
 * [McpKillSwitchPersistenceTest].
 */
class McpToolRegistryCoreTest {
    @Test
    fun `observer receives safely sanitized nested JSON result payload`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            val rawNested =
                "{\"user\": {\"profile\": {\"name\": \"alice\", \"token\": \"sk-secret-value\"} }, " +
                    "\"metadata\": {\"nested\": {\"enabled\": true} } }"
            core.registerProvider(provider("p1", echoTool("test_tool", handler = McpToolHandler { McpToolResult(rawNested) })))

            var capturedOutcome: McpExecutionOutcome? = null
            val observer =
                object : McpToolExecutionObserver {
                    override val observerId = "json-test"

                    override fun onExecutionStarted(request: McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: McpExecutionRequest,
                        outcome: McpExecutionOutcome,
                    ) {
                        capturedOutcome = outcome
                    }
                }
            core.registerExecutionObserver(observer)

            core.invoke("test_tool", "{}")

            val success = capturedOutcome as? McpExecutionOutcome.Success
            assertNotNull(success)

            val sanitizedText = success.result.text
            assertTrue(sanitizedText.contains("user"))
            assertTrue(sanitizedText.contains("alice"))
            assertTrue(sanitizedText.contains("[REDACTED]"))
            assertFalse(sanitizedText.contains("sk-secret-value"))
        }

    @Test
    fun `observer receives safely sanitized scalar and array JSON results`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            val rawArray =
                "[{\"name\": \"a\", \"token\": \"sk-secret-a\"}, " +
                    "{\"name\": \"b\", \"token\": \"sk-secret-b\"}, 123, true, null]"
            core.registerProvider(provider("p1", echoTool("test_tool", handler = McpToolHandler { McpToolResult(rawArray) })))

            var capturedOutcome: McpExecutionOutcome? = null
            val observer =
                object : McpToolExecutionObserver {
                    override val observerId = "json-array-test"

                    override fun onExecutionStarted(request: McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: McpExecutionRequest,
                        outcome: McpExecutionOutcome,
                    ) {
                        capturedOutcome = outcome
                    }
                }
            core.registerExecutionObserver(observer)

            core.invoke("test_tool", "{}")

            val success = capturedOutcome as? McpExecutionOutcome.Success
            assertNotNull(success)

            val sanitizedText = success.result.text
            assertTrue(sanitizedText.contains("name"))
            assertFalse(sanitizedText.contains("sk-secret-a"))
            assertFalse(sanitizedText.contains("sk-secret-b"))
            assertTrue(sanitizedText.contains("[REDACTED]"))
            assertTrue(sanitizedText.contains("123"))
            assertTrue(sanitizedText.contains("true"))
            assertTrue(sanitizedText.contains("null"))
        }

    @Test
    fun `observer gracefully handles malformed JSON results`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            val rawMalformed = "{\"user\":{\"token\":\"sk-secret"
            core.registerProvider(provider("p1", echoTool("test_tool", handler = McpToolHandler { McpToolResult(rawMalformed) })))

            var capturedOutcome: McpExecutionOutcome? = null
            val observer =
                object : McpToolExecutionObserver {
                    override val observerId = "json-malformed-test"

                    override fun onExecutionStarted(request: McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: McpExecutionRequest,
                        outcome: McpExecutionOutcome,
                    ) {
                        capturedOutcome = outcome
                    }
                }
            core.registerExecutionObserver(observer)

            val finalResult = core.invoke("test_tool", "{}")

            val success = capturedOutcome as? McpExecutionOutcome.Success
            assertNotNull(success)

            val sanitizedText = success.result.text
            assertFalse(sanitizedText.contains("sk-secret"))
            assertTrue(sanitizedText.contains("[REDACTED]"))

            assertTrue(finalResult.text.contains("sk-secret"))
        }

    @Test
    fun `observer receives capped result before sanitization for oversized JSON`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            val padding = "a".repeat(150_000)
            val rawOversized = "{\"pad\": \"$padding\", \"token\": \"sk-secret-value-at-end\"}"
            core.registerProvider(provider("p1", echoTool("test_tool", handler = McpToolHandler { McpToolResult(rawOversized) })))

            var capturedOutcome: McpExecutionOutcome? = null
            val observer =
                object : McpToolExecutionObserver {
                    override val observerId = "json-oversized-test"

                    override fun onExecutionStarted(request: McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: McpExecutionRequest,
                        outcome: McpExecutionOutcome,
                    ) {
                        capturedOutcome = outcome
                    }
                }
            core.registerExecutionObserver(observer)

            core.invoke("test_tool", "{}")

            val success = capturedOutcome as? McpExecutionOutcome.Success
            assertNotNull(success)

            val sanitizedText = success.result.text
            assertTrue(sanitizedText.contains("a".repeat(10_000)))
            assertFalse(sanitizedText.contains("sk-secret-value-at-end"))
            assertTrue(sanitizedText.contains("[BOSS host cap:"))
        }

    @Test
    fun `privacy regression test for arbitrary observer JSON payload`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            val rawJson =
                "{\n" +
                    "    \"user\": \"alice\",\n" +
                    "    \"nested\": {\n" +
                    "        \"secrets\": [\n" +
                    "            \"sk-test-secret\",\n" +
                    "            {\"password\": \"super-secret\"},\n" +
                    "            \"api_key=abc123\",\n" +
                    "            {\"token\": \"secret-token\"},\n" +
                    "            \"authorization=Bearer secret\"\n" +
                    "        ]\n" +
                    "    },\n" +
                    "    \"safe_value\": \"hello world\",\n" +
                    "    \"safe_boolean\": true,\n" +
                    "    \"safe_number\": 42\n" +
                    "}"

            core.registerProvider(provider("p1", echoTool("test_tool", handler = McpToolHandler { McpToolResult(rawJson) })))

            var capturedOutcome: McpExecutionOutcome? = null
            val observer =
                object : McpToolExecutionObserver {
                    override val observerId = "privacy-test"

                    override fun onExecutionStarted(request: McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: McpExecutionRequest,
                        outcome: McpExecutionOutcome,
                    ) {
                        capturedOutcome = outcome
                    }
                }
            core.registerExecutionObserver(observer)

            core.invoke("test_tool", "{}")

            val success = capturedOutcome as? McpExecutionOutcome.Success
            assertNotNull(success)

            val sanitizedText = success.result.text
            assertFalse(sanitizedText.contains("sk-test-secret"))
            assertFalse(sanitizedText.contains("super-secret"))
            assertFalse(sanitizedText.contains("abc123"))
            assertFalse(sanitizedText.contains("secret-token"))
            assertFalse(sanitizedText.contains("Bearer secret"))

            assertTrue(sanitizedText.contains("[REDACTED]"))

            assertTrue(sanitizedText.contains("alice"))
            assertTrue(sanitizedText.contains("hello world"))
            assertTrue(sanitizedText.contains("true"))
            assertTrue(sanitizedText.contains("42"))
        }

    private val tempFiles = mutableListOf<File>()

    /** A throwaway disabled-tools file under the OS temp dir, cleaned up after each test. */
    private fun tempDisabledFile(): File {
        val dir =
            kotlin.io.path
                .createTempDirectory("mcp-registry-test")
                .toFile()
        return File(dir, "mcp-disabled-tools.json").also { tempFiles.add(it) }
    }

    @AfterTest
    fun cleanup() {
        tempFiles.forEach { it.parentFile?.deleteRecursively() }
        tempFiles.clear()
    }

    private fun provider(
        id: String,
        vararg defs: McpToolDefinition,
    ) = object : McpToolProvider {
        override val providerId = id

        override fun tools() = defs.toList()
    }

    private fun echoTool(
        name: String,
        requiredPermissions: List<String> = emptyList(),
        requiresAdmin: Boolean = false,
        handler: McpToolHandler = McpToolHandler { McpToolResult("ok:$name") },
    ) = McpToolDefinition(name = name, description = "test tool $name", handler = handler)
        .apply {
            this.requiredPermissions = requiredPermissions
            this.requiresAdmin = requiresAdmin
        }

    // ---------------------------------------------------------------------
    // permitted() semantics — admin bypass, requiresAdmin gate, containsAll
    // ---------------------------------------------------------------------

    @Test
    fun `tool with no requirements is exposed to a logged-out user`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("open_tool")))

        // Default state before any updateAccess call: isAdmin=false, permissions=empty.
        assertTrue(core.tools.value.any { it.definition.name == "open_tool" })
    }

    @Test
    fun `tool requiring a permission is hidden until the user holds it`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("gated_tool", requiredPermissions = listOf("secret.read"))))

        assertFalse(core.tools.value.any { it.definition.name == "gated_tool" })

        core.updateAccess(isAdmin = false, permissions = setOf("secret.read"))
        assertTrue(core.tools.value.any { it.definition.name == "gated_tool" })
    }

    @Test
    fun `tool requiring ALL permissions is hidden when only some are held`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(
            provider("p1", echoTool("multi_gate", requiredPermissions = listOf("role.read", "role.assign"))),
        )

        core.updateAccess(isAdmin = false, permissions = setOf("role.read"))
        assertFalse(core.tools.value.any { it.definition.name == "multi_gate" })

        core.updateAccess(isAdmin = false, permissions = setOf("role.read", "role.assign"))
        assertTrue(core.tools.value.any { it.definition.name == "multi_gate" })
    }

    @Test
    fun `admin bypasses requiredPermissions`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("gated_tool", requiredPermissions = listOf("secret.read"))))

        core.updateAccess(isAdmin = true, permissions = emptySet())
        assertTrue(core.tools.value.any { it.definition.name == "gated_tool" })
    }

    @Test
    fun `admin bypasses requiresAdmin`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("admin_tool", requiresAdmin = true)))

        core.updateAccess(isAdmin = true, permissions = emptySet())
        assertTrue(core.tools.value.any { it.definition.name == "admin_tool" })
    }

    // ---------------------------------------------------------------------
    // permittedTools() — the third answer, and the one the global search reads
    // ---------------------------------------------------------------------

    /**
     * `permittedTools()` sits between `allTools` and `tools`, and the double-shift search is what
     * reads it. Getting it wrong in either direction is a real fault rather than a cosmetic one:
     * too permissive leaks the names and full descriptions of admin-only tools to anyone typing
     * into the search, and too strict hides the switched-off tool that someone is searching for
     * precisely because they switched it off.
     */
    @Test
    fun `permittedTools keeps a disabled tool but drops an unpermitted one`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(
            provider(
                "p1",
                echoTool("open_tool"),
                echoTool("gated_tool", requiredPermissions = listOf("secret.read")),
            ),
        )
        core.setToolEnabled("open_tool", enabled = false)

        val names = core.permittedTools().map { it.definition.name }

        assertTrue("open_tool" in names, "a switched-off tool is exactly what someone searches for")
        assertFalse("gated_tool" in names, "a tool this user may not run must not be enumerable")
        // And the distinction from the exposed set is the whole reason this method exists.
        assertFalse(core.tools.value.any { it.definition.name == "open_tool" })
    }

    @Test
    fun `permittedTools drops a requiresAdmin tool for a non-admin`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("admin_tool", requiresAdmin = true)))

        core.updateAccess(isAdmin = false, permissions = setOf("secret.read"))

        assertTrue(core.permittedTools().isEmpty(), "no permission set makes an admin-only tool visible")
    }

    @Test
    fun `permittedTools gives an admin everything, disabled included`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("admin_tool", requiresAdmin = true), echoTool("open_tool")))
        core.setToolEnabled("open_tool", enabled = false)

        core.updateAccess(isAdmin = true, permissions = emptySet())

        assertEquals(2, core.permittedTools().size)
    }

    @Test
    fun `permittedTools hides everything gated from a signed-out user`() {
        // The default posture: isAdmin false, permissions empty. This is the population the search
        // is open to before anyone signs in.
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(
            provider(
                "p1",
                echoTool("admin_tool", requiresAdmin = true),
                echoTool("gated_tool", requiredPermissions = listOf("secret.read")),
                echoTool("open_tool"),
            ),
        )

        assertEquals(listOf("open_tool"), core.permittedTools().map { it.definition.name })
    }

    /**
     * Admin bypasses every *permission* check, so for an admin operator the
     * kill-switch is the only access control left — the README's security section
     * says so explicitly. The two filters in `applyExposed` are independent
     * conjuncts today; folding them together (an early `if (isAdmin) return true`
     * over the whole filter) would keep every other test in this file green while
     * silently making a disabled tool reachable again for admins.
     *
     * Asserts both the listing and the call path: `invoke` resolves against the
     * exposed set, so a stale tool list on the agent's side must not get a call
     * through either.
     */
    @Test
    fun `admin does not bypass the disabled set`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(provider("p1", echoTool("toggle_me")))
            core.updateAccess(isAdmin = true, permissions = emptySet())
            assertTrue(core.tools.value.any { it.definition.name == "toggle_me" })

            core.setToolEnabled("toggle_me", enabled = false)

            assertFalse(
                core.tools.value.any { it.definition.name == "toggle_me" },
                "a disabled tool must stay hidden for an admin",
            )
            assertTrue(
                core.invoke("toggle_me", "{}").isError,
                "a disabled tool must not be invocable by an admin",
            )
        }

    @Test
    fun `non-admin with requiresAdmin is hidden even holding the listed permissions`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(
            provider("p1", echoTool("admin_only", requiredPermissions = listOf("role.read"), requiresAdmin = true)),
        )

        core.updateAccess(isAdmin = false, permissions = setOf("role.read"))
        assertFalse(core.tools.value.any { it.definition.name == "admin_only" })
    }

    @Test
    fun `revoking a permission live hides the tool without re-registration`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("gated_tool", requiredPermissions = listOf("secret.read"))))
        core.updateAccess(isAdmin = false, permissions = setOf("secret.read"))
        assertTrue(core.tools.value.any { it.definition.name == "gated_tool" })

        core.updateAccess(isAdmin = false, permissions = emptySet())
        assertFalse(core.tools.value.any { it.definition.name == "gated_tool" })
    }

    @Test
    fun `allTools is not permission-filtered but tools is (metadata-only disclosure posture)`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("admin_only", requiresAdmin = true)))

        // No admin, no permissions: the tool is invisible to invocation...
        assertFalse(core.tools.value.any { it.definition.name == "admin_only" })
        // ...but still listed in the full/management view.
        assertTrue(core.allTools.value.any { it.definition.name == "admin_only" })
    }

    // ---------------------------------------------------------------------
    // Dedup across providers
    // ---------------------------------------------------------------------

    @Test
    fun `duplicate tool name across providers - first registered wins`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("first", echoTool("shared_name")))
        core.registerProvider(provider("second", echoTool("shared_name")))

        val matches = core.allTools.value.filter { it.definition.name == "shared_name" }
        assertEquals(1, matches.size, "duplicate tool name must be deduped, not both kept")
        assertEquals("first", matches.single().providerId)
    }

    @Test
    fun `unregistering the winning provider lets the second provider's tool take over`() {
        // recompute() flattens ALL *currently registered* providers on every
        // change (it is not "first-wins forever" — only "first-wins within one
        // pass"), so once "first" is gone, "second" (still registered) claims
        // "shared_name" on the very next recompute. Pin this down since it's easy
        // to assume permanent exclusion instead of a live re-flatten.
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("first", echoTool("shared_name")))
        core.registerProvider(provider("second", echoTool("shared_name")))
        core.unregisterProvider("first")

        val matches = core.allTools.value.filter { it.definition.name == "shared_name" }
        assertEquals(1, matches.size)
        assertEquals("second", matches.single().providerId)
    }

    @Test
    fun `a provider whose tools() throws registers with no tools and does not affect siblings`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("healthy", echoTool("healthy_tool")))
        core.registerProvider(
            object : McpToolProvider {
                override val providerId = "broken"

                override fun tools(): List<McpToolDefinition> = error("plugin bug in tools()")
            },
        )
        core.registerProvider(provider("healthy2", echoTool("healthy_tool_2")))

        // The broken provider contributes nothing but doesn't take anyone down.
        assertTrue(core.allTools.value.any { it.definition.name == "healthy_tool" })
        assertTrue(core.allTools.value.any { it.definition.name == "healthy_tool_2" })
        assertTrue(core.allTools.value.none { it.providerId == "broken" })

        // And its teardown path stays consistent (id was tracked despite the throw).
        core.unregisterProvider("broken")
        assertTrue(core.allTools.value.any { it.definition.name == "healthy_tool" })
    }

    @Test
    fun `re-registering the same providerId replaces its tool set`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("v1_tool")))
        assertTrue(core.allTools.value.any { it.definition.name == "v1_tool" })

        core.registerProvider(provider("p1", echoTool("v2_tool")))
        assertFalse(core.allTools.value.any { it.definition.name == "v1_tool" })
        assertTrue(core.allTools.value.any { it.definition.name == "v2_tool" })
    }

    // ---------------------------------------------------------------------
    // disabledToolNames persistence
    // ---------------------------------------------------------------------

    @Test
    fun `setToolEnabled false removes the tool from tools but not allTools`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("toggle_me")))
        assertTrue(core.tools.value.any { it.definition.name == "toggle_me" })

        core.setToolEnabled("toggle_me", enabled = false)
        assertFalse(core.tools.value.any { it.definition.name == "toggle_me" })
        assertTrue(core.allTools.value.any { it.definition.name == "toggle_me" })
        assertTrue("toggle_me" in core.disabledToolNames.value)

        core.setToolEnabled("toggle_me", enabled = true)
        assertTrue(core.tools.value.any { it.definition.name == "toggle_me" })
    }

    @Test
    fun `disabled set persists across a fresh core instance reading the same file`() {
        val file = tempDisabledFile()
        val core1 = McpToolRegistryCore(disabledFile = file)
        core1.registerProvider(provider("p1", echoTool("persisted_tool")))
        core1.setToolEnabled("persisted_tool", enabled = false)
        assertTrue(file.exists(), "save should have written the file")

        val core2 = McpToolRegistryCore(disabledFile = file)
        assertTrue("persisted_tool" in core2.disabledToolNames.value)
    }

    @Test
    fun `save leaves no dangling tmp file (atomic rename completed)`() {
        val file = tempDisabledFile()
        val core = McpToolRegistryCore(disabledFile = file)
        core.registerProvider(provider("p1", echoTool("t")))
        core.setToolEnabled("t", enabled = false)

        val tmp = File(file.parentFile, file.name + ".tmp")
        assertFalse(tmp.exists(), "temp file must be renamed away, not left behind")
        assertTrue(file.exists())
    }

    @Test
    fun `null disabledFile skips persistence entirely (pure in-memory)`() {
        val core = McpToolRegistryCore(disabledFile = null)
        core.registerProvider(provider("p1", echoTool("t")))
        // Must not throw despite no file to write to.
        core.setToolEnabled("t", enabled = false)
        assertTrue("t" in core.disabledToolNames.value)
    }

    // ---------------------------------------------------------------------
    // invoke(): lookup, args parsing/coercion, timeout, cancellation
    // ---------------------------------------------------------------------

    @Test
    fun `invoke rejects unknown tool name`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            val result = core.invoke("does_not_exist", "{}")
            assertTrue(result.isError)
        }

    @Test
    fun `invoke rejects a disabled tool by name (unreachable even though still registered)`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(provider("p1", echoTool("disabled_tool")))
            core.setToolEnabled("disabled_tool", enabled = false)

            val result = core.invoke("disabled_tool", "{}")
            assertTrue(result.isError)
        }

    @Test
    fun `invoke rejects a permission-denied tool by name`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(provider("p1", echoTool("gated_tool", requiredPermissions = listOf("secret.read"))))
            // Never granted -> stays out of `tools`, so invoke can't reach it.

            val result = core.invoke("gated_tool", "{}")
            assertTrue(result.isError)
        }

    @Test
    fun `invoke parses string, boolean, integer, and double arguments`() =
        runBlocking {
            var captured: McpToolArgs? = null
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(
                provider(
                    "p1",
                    echoTool(
                        "args_tool",
                        handler =
                            McpToolHandler { args ->
                                captured = args
                                McpToolResult("ok")
                            },
                    ),
                ),
            )

            core.invoke(
                "args_tool",
                """{"name":"hello","enabled":true,"count":42,"ratio":3.5,"missing_key_untouched":null}""",
            )

            val args = requireNotNull(captured)
            assertEquals("hello", args.string("name"))
            assertEquals(true, args.boolean("enabled"))
            assertEquals(42, args.int("count"))
            assertEquals(3.5, args.double("ratio"))
            assertTrue(args.has("missing_key_untouched"))
            assertNull(args.string("missing_key_untouched"))
            assertFalse(args.has("truly_absent_key"))
        }

    @Test
    fun `invoke coerces a JSON integer through int() and double()`() =
        runBlocking {
            // scalarOf() stores whole numbers as Long; McpToolArgs getters must still
            // hand back a usable Int/Double rather than silently going null.
            var captured: McpToolArgs? = null
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(
                provider(
                    "p1",
                    echoTool(
                        "num_tool",
                        handler =
                            McpToolHandler { args ->
                                captured = args
                                McpToolResult("ok")
                            },
                    ),
                ),
            )

            core.invoke("num_tool", """{"n": 7}""")

            val args = requireNotNull(captured)
            assertEquals(7, args.int("n"))
            assertEquals(7.0, args.double("n"))
        }

    @Test
    fun `int() returns null for values outside Int range instead of silently wrapping`() =
        runBlocking {
            var captured: McpToolArgs? = null
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(
                provider(
                    "p1",
                    echoTool(
                        "big_tool",
                        handler =
                            McpToolHandler { args ->
                                captured = args
                                McpToolResult("ok")
                            },
                    ),
                ),
            )

            core.invoke(
                "big_tool",
                """{"too_big": 9999999999, "too_small": -9999999999, "big_double": 1.0E12, "fits": 2147483647}""",
            )

            val args = requireNotNull(captured)
            assertNull(args.int("too_big"), "Long above Int.MAX_VALUE must not wrap")
            assertNull(args.int("too_small"), "Long below Int.MIN_VALUE must not wrap")
            assertNull(args.int("big_double"), "Double outside Int range must not saturate")
            assertEquals(Int.MAX_VALUE, args.int("fits"))
            // The full value stays reachable through the wider getters.
            assertEquals(9_999_999_999.0, args.double("too_big"))
        }

    @Test
    fun `invoke with malformed JSON args runs the handler with an empty arg set instead of erroring`() =
        runBlocking {
            var captured: McpToolArgs? = null
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(
                provider(
                    "p1",
                    echoTool(
                        "bad_args_tool",
                        handler =
                            McpToolHandler { args ->
                                captured = args
                                McpToolResult("ok")
                            },
                    ),
                ),
            )

            val result = core.invoke("bad_args_tool", "{not valid json")

            assertFalse(result.isError)
            assertFalse(requireNotNull(captured).has("anything"))
        }

    @Test
    fun `invoke times out a handler that never completes`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null, invokeTimeoutMs = 50L)
            core.registerProvider(
                provider(
                    "p1",
                    echoTool(
                        "hangs",
                        handler =
                            McpToolHandler {
                                delay(5_000)
                                McpToolResult("should never get here")
                            },
                    ),
                ),
            )

            val result = core.invoke("hangs", "{}")
            assertTrue(result.isError)
            assertTrue(result.text.contains("timed out", ignoreCase = true))
        }

    @Test
    fun `invoke wraps a handler exception into an error result`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(
                provider("p1", echoTool("throws", handler = McpToolHandler { error("boom") })),
            )

            val result = core.invoke("throws", "{}")
            assertTrue(result.isError)
            assertTrue(result.text.contains("boom"))
        }

    @Test
    fun `invoke propagates caller cancellation rather than swallowing it into an error result`() =
        runBlocking {
            val core = McpToolRegistryCore(disabledFile = null)
            core.registerProvider(
                provider(
                    "p1",
                    echoTool(
                        "slow",
                        handler =
                            McpToolHandler {
                                delay(5_000)
                                McpToolResult("unreachable")
                            },
                    ),
                ),
            )

            var threw: Throwable? = null
            try {
                coroutineScope {
                    val deferred = async { core.invoke("slow", "{}") }
                    delay(20)
                    deferred.cancel()
                    deferred.await()
                }
            } catch (t: Throwable) {
                threw = t
            }
            assertTrue(threw is CancellationException, "expected CancellationException, got $threw")
        }

    // ---------------------------------------------------------------------
    // Concurrency: the mutation lock must keep allTools/tools consistent
    // under mutators arriving from multiple threads (register/unregister/
    // setToolEnabled/updateAccess all race in production across dispatchers).
    // ---------------------------------------------------------------------

    @Test
    fun `concurrent register, unregister, and updateAccess never leave a stale or torn snapshot`() {
        val core = McpToolRegistryCore(disabledFile = null)
        val threads = mutableListOf<Thread>()
        val iterations = 200

        repeat(8) { workerIndex ->
            threads +=
                Thread {
                    repeat(iterations) { i ->
                        val id = "worker$workerIndex"
                        core.registerProvider(provider(id, echoTool("tool_${workerIndex}_$i")))
                        core.updateAccess(isAdmin = i % 2 == 0, permissions = setOf("p$i"))
                        core.unregisterProvider(id)
                    }
                }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // After every worker has unregistered its last provider, nothing should
        // remain registered — if the mutation lock ever let a recompute overwrite
        // a later unregister with a stale snapshot, a provider's tools would
        // still be present here.
        assertTrue(core.allTools.value.isEmpty(), "no provider should remain registered: ${core.allTools.value}")
        assertTrue(core.tools.value.isEmpty())
        assertTrue(core.permittedTools().isEmpty(), "permittedTools reads the same snapshot")
    }

    @Test
    fun `observer registration ignores duplicates`() =
        kotlinx.coroutines.runBlocking {
            val registry = McpToolRegistryCore(disabledFile = null)
            val observer =
                object : ai.rever.boss.plugin.api.McpToolExecutionObserver {
                    override val observerId = "test.observer"

                    override fun onExecutionStarted(request: ai.rever.boss.plugin.api.McpExecutionRequest) {
                        // no-op for test
                    }

                    override fun onExecutionFinished(
                        request: ai.rever.boss.plugin.api.McpExecutionRequest,
                        outcome: ai.rever.boss.plugin.api.McpExecutionOutcome,
                    ) {
                        // no-op for test
                    }
                }

            registry.registerExecutionObserver(observer)
            registry.registerExecutionObserver(observer)

            registry.unregisterExecutionObserver("test.observer")

            registry.registerProvider(provider("p1", echoTool("tool1")))

            val result = registry.invoke("tool1", "{}")
            kotlin.test.assertTrue(result.text.contains("ok:tool1"))
        }

    @Test
    fun `observer exceptions do not break tool execution`() =
        kotlinx.coroutines.runBlocking {
            val registry = McpToolRegistryCore(disabledFile = null)

            var startedCalled = false
            var finishedCalled = false

            val observer =
                object : ai.rever.boss.plugin.api.McpToolExecutionObserver {
                    override val observerId = "faulty.observer"

                    override fun onExecutionStarted(request: ai.rever.boss.plugin.api.McpExecutionRequest) {
                        startedCalled = true
                        error("Observer crashed on start")
                    }

                    override fun onExecutionFinished(
                        request: ai.rever.boss.plugin.api.McpExecutionRequest,
                        outcome: ai.rever.boss.plugin.api.McpExecutionOutcome,
                    ) {
                        finishedCalled = true
                        error("Observer crashed on finish")
                    }
                }

            registry.registerExecutionObserver(observer)

            registry.registerProvider(provider("p1", echoTool("tool1")))

            val result = registry.invoke("tool1", "{}")

            kotlin.test.assertTrue(result.text.contains("ok:tool1"))
            kotlin.test.assertTrue(startedCalled)
            kotlin.test.assertTrue(finishedCalled)
        }

    // ---------------------------------------------------------------------
    // Governed Autonomy - Policy, Approval Gate, and Operation Ledger
    // ---------------------------------------------------------------------

    private fun payloadCore(
        text: String,
        cap: Int,
        isError: Boolean = false,
    ): McpToolRegistryCore =
        McpToolRegistryCore(disabledFile = null, maxResultChars = cap).also {
            it.registerProvider(
                provider("p1", echoTool("big", handler = McpToolHandler { McpToolResult(text, isError) })),
            )
        }

    @Test
    fun `a result under the cap comes back byte-identical`() =
        kotlinx.coroutines.runBlocking {
            val payload = "y".repeat(999) + "\n[504 older matching lines omitted...]"

            val result = payloadCore(payload, cap = 100_000).invoke("big", "{}")

            kotlin.test.assertEquals(payload, result.text)
            kotlin.test.assertFalse(result.isError)
        }

    @Test
    fun `a result of exactly the cap is untouched`() =
        kotlinx.coroutines.runBlocking {
            val cap = 1_000
            val payload = "z".repeat(cap)

            val result = payloadCore(payload, cap = cap).invoke("big", "{}")

            kotlin.test.assertEquals(payload, result.text)
            kotlin.test.assertEquals(cap, result.text.length)
        }

    @Test
    fun `a result over the cap is cut and carries a marker saying so`() =
        kotlinx.coroutines.runBlocking {
            val cap = 1_000
            val payload = "x".repeat(5_000) + "\n[504 older matching lines omitted...]"

            val result = payloadCore(payload, cap = cap).invoke("big", "{}")

            kotlin.test.assertTrue(result.text.length <= cap, "cap holds marker included, got ${result.text.length}")
            kotlin.test.assertTrue(result.text.startsWith("x".repeat(100)), "the head of the answer survives")
            kotlin.test.assertTrue(
                result.text.contains("BOSS host cap"),
                "a silent cut reads as a complete answer",
            )
        }

    @Test
    fun `an oversized error result is capped too and stays an error`() =
        kotlinx.coroutines.runBlocking {
            val cap = 1_000
            val result = payloadCore("e".repeat(20_000), cap = cap, isError = true).invoke("big", "{}")
            kotlin.test.assertTrue(result.isError)
            kotlin.test.assertTrue(result.text.length <= cap)
            kotlin.test.assertTrue(result.text.contains("BOSS host cap"))
        }

    @Test
    fun `cutting never leaves a lone surrogate, at either boundary parity`() {
        val text = "\uD83D\uDE00".repeat(1_000)
        for (cap in 600..620) {
            val out = capMcpResultText(text, cap)
            val kept = out.substringBefore("\n\n[BOSS host cap")
            kotlin.test.assertTrue(kept.isNotEmpty())
            kotlin.test.assertFalse(kept.last().isHighSurrogate())
        }
    }

    @Test
    fun `observer receives capped result and does not receive over-cap result`() =
        kotlinx.coroutines.runBlocking {
            val registry = McpToolRegistryCore(disabledFile = null, maxResultChars = 1_000)
            registry.registerProvider(
                provider("p1", echoTool("tool1", handler = McpToolHandler { McpToolResult("x".repeat(5_000)) })),
            )

            var observerResultText: String? = null

            val observer =
                object : ai.rever.boss.plugin.api.McpToolExecutionObserver {
                    override val observerId = "test.obs"

                    override fun onExecutionStarted(request: ai.rever.boss.plugin.api.McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: ai.rever.boss.plugin.api.McpExecutionRequest,
                        outcome: ai.rever.boss.plugin.api.McpExecutionOutcome,
                    ) {
                        if (outcome is ai.rever.boss.plugin.api.McpExecutionOutcome.Success) {
                            observerResultText = outcome.result.text
                        }
                    }
                }
            registry.registerExecutionObserver(observer)
            registry.invoke("tool1", "{}")

            val result = observerResultText
            kotlin.test.assertNotNull(result)
            kotlin.test.assertTrue(result!!.length <= 1_000)
            kotlin.test.assertTrue(result.contains("BOSS host cap"))
            kotlin.test.assertFalse(result.length > 1_000)
        }

    @Test
    fun `sensitive MCP arguments are sanitized before observer delivery`() =
        kotlinx.coroutines.runBlocking {
            val registry = McpToolRegistryCore(disabledFile = null)
            registry.registerProvider(
                provider("p1", echoTool("tool1")),
            )

            var observerArgsText: String? = null
            val observer =
                object : ai.rever.boss.plugin.api.McpToolExecutionObserver {
                    override val observerId = "test.obs"

                    override fun onExecutionStarted(request: ai.rever.boss.plugin.api.McpExecutionRequest) {
                        observerArgsText = request.arguments
                    }

                    override fun onExecutionFinished(
                        request: ai.rever.boss.plugin.api.McpExecutionRequest,
                        outcome: ai.rever.boss.plugin.api.McpExecutionOutcome,
                    ) {}
                }
            registry.registerExecutionObserver(observer)

            val rawArgs = "{\"api_key\": \"sk-1234567890\", \"nested\": {\"password\": \"foo\"}, \"safe\": \"bar\"}"
            registry.invoke("tool1", rawArgs)

            val argsStr = observerArgsText
            kotlin.test.assertNotNull(argsStr)
            kotlin.test.assertTrue(argsStr!!.contains("[REDACTED]"))
            kotlin.test.assertFalse(argsStr.contains("sk-1234567890"))
            kotlin.test.assertFalse(argsStr.contains("foo"))
            kotlin.test.assertTrue(argsStr.contains("bar"))
        }

    @Test
    fun `sensitive exception messages are not exposed raw`() =
        kotlinx.coroutines.runBlocking {
            val registry = McpToolRegistryCore(disabledFile = null)
            registry.registerProvider(
                provider("p1", echoTool("tool1", handler = McpToolHandler { error("Crashed with token sk-1234567890") })),
            )

            var outcomeErrorMsg: String? = null
            val observer =
                object : ai.rever.boss.plugin.api.McpToolExecutionObserver {
                    override val observerId = "test.obs"

                    override fun onExecutionStarted(request: ai.rever.boss.plugin.api.McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: ai.rever.boss.plugin.api.McpExecutionRequest,
                        outcome: ai.rever.boss.plugin.api.McpExecutionOutcome,
                    ) {
                        if (outcome is ai.rever.boss.plugin.api.McpExecutionOutcome.Failure) {
                            outcomeErrorMsg = outcome.error.message
                        }
                    }
                }
            registry.registerExecutionObserver(observer)
            registry.invoke("tool1", "{}")

            val msg = outcomeErrorMsg
            kotlin.test.assertNotNull(msg)
            println("MESSAGE: $msg")

            kotlin.test.assertFalse(msg.contains("sk-1234567890"))
        }

    @Test
    fun `observer receives correct timeout outcome`() =
        kotlinx.coroutines.runBlocking {
            val registry = McpToolRegistryCore(disabledFile = null, invokeTimeoutMs = 100)
            registry.registerProvider(
                provider(
                    "p1",
                    echoTool(
                        "tool1",
                        handler =
                            McpToolHandler {
                                kotlinx.coroutines.delay(500)
                                McpToolResult("done")
                            },
                    ),
                ),
            )

            var didTimeout = false
            val observer =
                object : ai.rever.boss.plugin.api.McpToolExecutionObserver {
                    override val observerId = "test.obs"

                    override fun onExecutionStarted(request: ai.rever.boss.plugin.api.McpExecutionRequest) {}

                    override fun onExecutionFinished(
                        request: ai.rever.boss.plugin.api.McpExecutionRequest,
                        outcome: ai.rever.boss.plugin.api.McpExecutionOutcome,
                    ) {
                        if (outcome is ai.rever.boss.plugin.api.McpExecutionOutcome.Timeout) {
                            didTimeout = true
                        }
                    }
                }
            registry.registerExecutionObserver(observer)
            registry.invoke("tool1", "{}")

            kotlin.test.assertTrue(didTimeout)
        }
}
