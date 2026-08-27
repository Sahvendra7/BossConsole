package ai.rever.boss.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Asserts that the Chromium branding workflow strips the engine bundle's URL and
 * document type declarations, and does it before the re-sign.
 *
 * A source-level assertion, for the same reason `WizardDependencyReportTest` is
 * one: the workflow only runs on a mac runner against a downloaded JxBrowser
 * archive, and what it produces is verified by Launch Services on a user's
 * machine weeks later. The failure it guards is invisible and expensive - the
 * engine re-registers as a second app called "BOSS" that claims http, https and
 * `public.html`, System Settings offers two entries nobody can tell apart, and
 * every link opens a bare Chromium with no BOSS window. That is exactly the bug
 * this change exists to fix, and nothing else would notice it coming back.
 */
class EngineBundlePlistStripTest {
    private val strippedKeys = listOf("CFBundleURLTypes", "CFBundleDocumentTypes")

    private fun workflow(): String {
        val root =
            assertNotNull(
                generateSequence(File("").absoluteFile) { it.parentFile }
                    .firstOrNull { File(it, "composeApp/build.gradle.kts").isFile },
                "could not locate the repository root",
            )
        val file = File(root, ".github/workflows/build-chromium-branding.yml")
        assertTrue(file.isFile, "build-chromium-branding.yml not found at ${file.absolutePath}")
        return file.readText()
    }

    @Test
    fun `both plist keys are deleted from the engine bundle`() {
        val text = workflow()
        strippedKeys.forEach { key ->
            assertTrue(
                text.contains("""PlistBuddy -c "Delete :$key" "${'$'}MAIN_PLIST""""),
                "the workflow must delete :$key from the engine's Info.plist",
            )
        }
    }

    @Test
    fun `the deletes happen for every macOS architecture`() {
        val text = workflow()
        // Two mac jobs, arm64 and x64, with the same steps. Patching only one
        // ships a clean engine on Apple Silicon and a colliding one on Intel.
        strippedKeys.forEach { key ->
            assertEquals(
                2,
                Regex("""PlistBuddy -c "Delete :$key"""").findAll(text).count(),
                "expected the :$key delete in both macOS build jobs",
            )
        }
    }

    @Test
    fun `the deletes precede the re-sign, or they break the signature`() {
        val text = workflow()
        val firstDelete = text.indexOf("""Delete :CFBundleURLTypes""")
        val firstResign = text.indexOf("""Re-signing with identity""")
        assertTrue(firstDelete > 0, "no CFBundleURLTypes delete found")
        assertTrue(firstResign > 0, "no re-sign step found")
        assertTrue(
            firstDelete < firstResign,
            "the plist edit must come BEFORE the re-sign; after it, the edit breaks the code signature " +
                "and the engine fails codesign --verify (or worse, ships mis-signed)",
        )
    }

    @Test
    fun `the end state is asserted rather than assumed`() {
        val text = workflow()
        // Every PlistBuddy delete ends in `|| true`, because an absent key is
        // fine. So without a check on the result, a delete that silently failed
        // would republish the collision - and that is the whole failure this step
        // exists to prevent.
        assertTrue(
            text.contains("""still present in the engine Info.plist"""),
            "the workflow must fail the job when either key survives the delete",
        )
    }
}
