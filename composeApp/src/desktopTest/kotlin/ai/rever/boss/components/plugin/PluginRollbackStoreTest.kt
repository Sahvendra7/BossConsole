package ai.rever.boss.components.plugin

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one kept file that turns "the plugin is gone" into a button.
 *
 * Every install path promotes over the target and deletes what it rejects, so without a snapshot the
 * previous jar does not exist anywhere by the time anyone discovers the new one will not load. These
 * pin the properties that make the copy usable rather than merely present.
 */
class PluginRollbackStoreTest {
    private val dir = createTempDirectory("rollback").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** A jar that `PluginManifestReader` can read a version out of. */
    private fun writeJar(
        name: String,
        version: String,
    ): File {
        val jar = File(dir, name)
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/boss-plugin/plugin.json"))
            zip.write(
                """
                {
                  "manifestVersion": 1,
                  "pluginId": "com.example.rollback",
                  "displayName": "Test Plugin",
                  "version": "$version",
                  "apiVersion": "1.0.0",
                  "mainClass": "com.example.Main"
                }
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        return jar
    }

    private fun rollbackFile(jar: File) = File(jar.absolutePath + ".rollback")

    @Test
    fun `a snapshot keeps the version that was there`() {
        val jar = writeJar("p.jar", "1.2.21")
        PluginRollbackStore.snapshot(jar.absolutePath)
        assertTrue(rollbackFile(jar).isFile, "no rollback copy was kept")
        assertEquals("1.2.21", PluginRollbackStore.availableVersion(jar.absolutePath))
    }

    @Test
    fun `the copy survives the jar being replaced, which is the whole point`() {
        val jar = writeJar("p.jar", "1.2.21")
        PluginRollbackStore.snapshot(jar.absolutePath)
        // What an install does next: promote new bytes over the target.
        writeJar("p.jar", "1.2.22")
        assertEquals("1.2.22", readVersion(jar))
        assertEquals("1.2.21", PluginRollbackStore.availableVersion(jar.absolutePath))
    }

    @Test
    fun `restoring puts the previous version back`() {
        val jar = writeJar("p.jar", "1.2.21")
        PluginRollbackStore.snapshot(jar.absolutePath)
        writeJar("p.jar", "1.2.22")

        assertEquals("1.2.21", PluginRollbackStore.restore(jar.absolutePath))
        assertEquals("1.2.21", readVersion(jar))
    }

    @Test
    fun `restoring keeps the copy, so a failed load can be retried`() {
        // Moving instead of copying would consume the only good jar. A restore interrupted between
        // the write and the load would then leave nothing to try again with - the exact position
        // this whole mechanism exists to avoid.
        val jar = writeJar("p.jar", "1.2.21")
        PluginRollbackStore.snapshot(jar.absolutePath)
        writeJar("p.jar", "1.2.22")

        PluginRollbackStore.restore(jar.absolutePath)
        assertTrue(rollbackFile(jar).isFile, "the rollback copy was consumed by the restore")
        assertEquals("1.2.21", PluginRollbackStore.restore(jar.absolutePath), "not repeatable")
    }

    @Test
    fun `snapshotting a path with nothing there is not an error`() {
        // A first install has no previous jar. It must not fail, and must not leave a copy behind.
        val absent = File(dir, "never-installed.jar")
        PluginRollbackStore.snapshot(absent.absolutePath)
        assertFalse(rollbackFile(absent).exists())
        assertNull(PluginRollbackStore.availableVersion(absent.absolutePath))
    }

    @Test
    fun `nothing to restore answers null rather than throwing`() {
        val jar = writeJar("p.jar", "1.2.22")
        assertNull(PluginRollbackStore.restore(jar.absolutePath))
        assertEquals("1.2.22", readVersion(jar), "the jar was touched despite there being no copy")
    }

    @Test
    fun `the copy is not named like a jar, so the directory scan ignores it`() {
        // Same reasoning as StoreMissingDependencyInstaller's `.jar.part`: a plugin directory scan
        // picks up anything ending in .jar, and a stale copy loading as a second plugin would be a
        // far stranger failure than the one this fixes.
        val jar = writeJar("p.jar", "1.2.21")
        PluginRollbackStore.snapshot(jar.absolutePath)
        assertFalse(rollbackFile(jar).name.endsWith(".jar"), "the rollback copy is scannable")
        assertEquals(
            listOf("p.jar"),
            dir
                .listFiles()
                .orEmpty()
                .map { it.name }
                .filter { it.endsWith(".jar") },
        )
    }

    @Test
    fun `discard removes the copy`() {
        val jar = writeJar("p.jar", "1.2.21")
        PluginRollbackStore.snapshot(jar.absolutePath)
        PluginRollbackStore.discard(jar.absolutePath)
        assertFalse(rollbackFile(jar).exists())
        assertNull(PluginRollbackStore.availableVersion(jar.absolutePath))
    }

    private fun readVersion(jar: File): String? =
        runCatching {
            ai.rever.boss.plugin.loader.PluginManifestReader
                .readFromJar(jar.absolutePath)
                .version
        }.getOrNull()

    @Test
    fun `the kept version is recorded, not read back out of the copy`() {
        // The trap worth pinning, because the two requirements are in direct conflict:
        // PluginManifestReader.readFromJar throws "File is not a JAR" for any path not ending in
        // .jar, and the .rollback suffix exists precisely so this file is NOT scannable as one. So
        // the version has to be captured while the live jar is still there, and a naive
        // "read it back from the copy" answers null for every rollback that exists.
        val jar = writeJar("p.jar", "1.2.21")
        PluginRollbackStore.snapshot(jar.absolutePath)
        assertNull(
            readVersion(rollbackFile(jar)),
            "the copy is readable as a jar, so the suffix is not doing its job",
        )
        assertEquals("1.2.21", PluginRollbackStore.availableVersion(jar.absolutePath))
    }

    @Test
    fun `a copy whose version could not be recorded is not offered`() {
        // Offered blind, a rollback button cannot say what it will install - and a jar whose
        // manifest is unreadable is not something to restore on a guess.
        val jar = File(dir, "broken.jar")
        jar.writeText("not a zip")
        PluginRollbackStore.snapshot(jar.absolutePath)
        assertTrue(rollbackFile(jar).isFile, "the bytes were not kept")
        assertNull(PluginRollbackStore.availableVersion(jar.absolutePath))
    }
}
