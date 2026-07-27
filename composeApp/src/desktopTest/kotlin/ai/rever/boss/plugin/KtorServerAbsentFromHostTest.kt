package ai.rever.boss.plugin

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * The host must not ship a ktor SERVER stack.
 *
 * BossConsole declares ktor client only; ktor-server-cio and ktor-server-core
 * used to arrive transitively through supabase auth-kt, whose only consumer of
 * them is the localhost OAuth/SSO HTTP callback BOSS never takes. They are
 * excluded in composeApp/build.gradle.kts, because 529 host-owned
 * io.ktor.server.* classes are exactly the material a plugin classloader's
 * parent fallback turns into a loader-constraint LinkageError — the failure
 * mode a terminal-tab hot-reload hit on io/ktor/util/AttributeKey.
 *
 * This is a classpath assertion, not a behaviour test: a supabase (or other)
 * dependency bump that reaches io.ktor.server.* from a path BOSS does take
 * would otherwise reappear silently and only fail in a packaged build.
 */
class KtorServerAbsentFromHostTest {
    @Test
    fun `no ktor server classes on the host classpath`() {
        for (name in listOf(
            "io.ktor.server.cio.CIOApplicationEngine",
            "io.ktor.server.application.Application",
            "io.ktor.server.http.HttpRequestLifecycleKt",
            "io.ktor.server.engine.DefaultEnginePipelineKt",
        )) {
            assertFailsWith<ClassNotFoundException>("$name must not be on the host classpath") {
                Class.forName(name)
            }
        }
    }

    @Test
    fun `the ktor client stack the app actually uses is still present`() {
        // The other half of the assertion: the exclusion must not have taken
        // the client engine (supabase, the plugin store and the updater all
        // ride on it) with it.
        for (name in listOf(
            "io.ktor.client.HttpClient",
            "io.ktor.client.engine.cio.CIOEngine",
            "io.ktor.util.AttributeKey",
        )) {
            assertNotNull(Class.forName(name), "$name must stay on the host classpath")
        }
    }
}
