package ai.rever.boss

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Windows ARM64 compiles `desktopMain` with the microkernel sources removed — see the
 * `isWindowsArm64Build` branch in `composeApp/build.gradle.kts`, which drops the `kernel`
 * and `plugin/remote` directories plus `OutOfProcessPluginSpawnerImpl.kt` and
 * `PluginStateBridge.kt`, because boss-ipc's protoc ships no win-arm64 binary.
 *
 * So anything still in that build may not reference those sources statically. `main.kt`,
 * `PluginStoreSetup` and `PerformanceDataProviderImpl` all reach `KernelBootstrap` through
 * `Class.forName` for exactly this reason.
 *
 * A static import compiles everywhere *except* Windows ARM64, where it fails as an
 * unresolved reference — and no PR job builds that target, so it surfaces only during a
 * release, after the other four platforms have published. That is how the self-healing
 * settings shipped a release with no Windows ARM64 artifact: the settings UI imported three
 * types from `ai.rever.boss.kernel` which turned out not to need boss-ipc at all. They now
 * live in `ai.rever.boss.config`.
 *
 * This test runs on every platform, so the mistake fails a PR instead of a release.
 */
class WindowsArm64SourceIsolationTest {
    /** Path fragments the Windows ARM64 build removes from `desktopMain`. */
    private val excludedOnWindowsArm64 =
        listOf(
            "/kernel/",
            "/plugin/remote/",
            "/OutOfProcessPluginSpawnerImpl.kt",
            "/PluginStateBridge.kt",
        )

    /** Walks up from the working directory, which differs between Gradle and IDE runs. */
    private fun desktopMainRoot(): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "composeApp/src/desktopMain/kotlin") }
            .firstOrNull { it.isDirectory }

    @Test
    fun `source kept in the Windows ARM64 build does not import the microkernel`() {
        val root = desktopMainRoot()
        // Deliberately not a skip: a guard that silently passes when it can't find the tree
        // is how this kind of check rots into decoration.
        assertTrue(root != null, "could not locate composeApp/src/desktopMain/kotlin")

        val surviving =
            root!!
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { file ->
                    val path = file.path.replace(File.separatorChar, '/')
                    excludedOnWindowsArm64.any { path.contains(it) }
                }.toList()

        assertTrue(
            surviving.size > 100,
            "only ${surviving.size} files scanned — the walk is not seeing the source tree",
        )

        val offenders =
            surviving
                .filter { file ->
                    file.readLines().any { it.trimStart().startsWith("import ai.rever.boss.kernel.") }
                }.map { it.name }
                .sorted()

        assertTrue(
            offenders.isEmpty(),
            "These files stay in the Windows ARM64 build but import ai.rever.boss.kernel, which " +
                "is excluded there. Reach it via Class.forName, or move what they need out of " +
                "the kernel package: $offenders",
        )
    }
}
