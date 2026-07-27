package ai.rever.boss.services.importer.browser

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager

/**
 * Opens a browser's SQLite database safely.
 *
 * A running browser holds its databases open, and on some platforms locks them
 * outright, so reading in place either fails or blocks. Everything here works on
 * a private copy: the browser stays untouched and can keep writing while the
 * import reads.
 *
 * The copy includes the `-wal` and `-shm` sidecars when present — without them
 * a database in WAL mode reads back as an older snapshot, silently missing
 * whatever the browser wrote most recently.
 */
internal object SqliteSnapshot {
    /**
     * Copy [source] to a temp file, run [block] against it, then delete the copy.
     *
     * Opened read-only so a corrupt or unexpected schema can never write back.
     */
    fun <T> read(
        source: File,
        block: (Connection) -> T,
    ): T {
        val temp = Files.createTempFile("boss-import-", ".sqlite")
        val sidecars = listOf("-wal", "-shm")

        try {
            Files.copy(source.toPath(), temp, StandardCopyOption.REPLACE_EXISTING)
            sidecars.forEach { suffix ->
                val extra = File(source.absolutePath + suffix)
                if (extra.isFile) {
                    Files.copy(
                        extra.toPath(),
                        File(temp.toString() + suffix).toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            }

            // Explicit driver load: the host shades dependencies, so relying on
            // ServiceLoader discovery alone is fragile.
            Class.forName("org.sqlite.JDBC")
            return DriverManager
                .getConnection("jdbc:sqlite:file:$temp?mode=ro")
                .use(block)
        } finally {
            Files.deleteIfExists(temp)
            sidecars.forEach { Files.deleteIfExists(File(temp.toString() + it).toPath()) }
        }
    }
}
