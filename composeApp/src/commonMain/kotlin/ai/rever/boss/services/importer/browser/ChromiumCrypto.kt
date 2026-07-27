package ai.rever.boss.services.importer.browser

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.GeneralSecurityException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts the `password_value` blobs Chromium stores in `Login Data`.
 *
 * Chromium does not store passwords in the clear, but it also does not protect
 * them from the logged-in user — the key is held by the OS keychain and handed
 * back on request. Each platform does this differently:
 *
 * - **macOS** — a random key is kept in the login keychain as the generic
 *   password "Chrome Safe Storage". Values are `v10` + AES-128-CBC, key derived
 *   PBKDF2-HMAC-SHA1(password, salt="saltysalt", 1003 iterations, 16 bytes),
 *   IV = sixteen spaces.
 * - **Windows** — `Local State` holds an `encrypted_key`, itself DPAPI-wrapped;
 *   values are `v10` + AES-256-GCM. Unwrapping needs DPAPI, which the JDK has no
 *   binding for, so this is not supported here.
 * - **Linux** — key comes from the desktop keyring, or the fixed password
 *   "peanuts" when no keyring is present.
 *
 * Reading the macOS keychain entry prompts the user for permission the first
 * time. That prompt is the intended consent step: it is the OS asking whether
 * BOSS may read Chrome's passwords.
 */
internal object ChromiumCrypto {
    private val logger = BossLogger.forComponent("ChromiumCrypto")

    private const val SALT = "saltysalt"
    private const val ITERATIONS_MAC = 1003
    private const val ITERATIONS_LINUX = 1
    private const val KEY_LENGTH_BITS = 128
    private const val SECURITY_TIMEOUT_SECONDS = 60L

    /** Chromium's marker for "encrypted with the v10 scheme". */
    private val V10 = "v10".toByteArray(Charsets.US_ASCII)
    private val V11 = "v11".toByteArray(Charsets.US_ASCII)

    /** Why a profile's passwords cannot be decrypted on this machine. */
    class UnsupportedPlatformException(
        message: String,
    ) : Exception(message)

    /** Raised when the user declines the keychain prompt, or it is unavailable. */
    class KeyUnavailableException(
        message: String,
    ) : Exception(message)

    /**
     * Derive the AES key for [browserName]'s store.
     *
     * @param browserName used to pick the right keychain entry ("Chrome Safe
     *   Storage", "Brave Safe Storage", …)
     */
    fun deriveKey(browserName: String): SecretKeySpec =
        when {
            BrowserDetector.isMac -> {
                macKey(browserName)
            }

            BrowserDetector.isWindows -> {
                throw UnsupportedPlatformException(
                    "Importing saved passwords from Chromium browsers isn't supported on Windows yet — " +
                        "the key is sealed with DPAPI. Export a CSV from the browser instead.",
                )
            }

            else -> {
                linuxKey()
            }
        }

    /**
     * Keychain service name per browser.
     *
     * Not derivable from the display name: Chrome's entry is "Chrome Safe
     * Storage", never "Google Chrome Safe Storage". Verified against a real
     * keychain — guessing here silently yields "no saved passwords".
     */
    private val KEYCHAIN_SERVICES =
        mapOf(
            "Google Chrome" to "Chrome Safe Storage",
            "Google Chrome Beta" to "Chrome Beta Safe Storage",
            "Google Chrome Canary" to "Chrome Canary Safe Storage",
            "Microsoft Edge" to "Microsoft Edge Safe Storage",
            "Brave" to "Brave Safe Storage",
            "Vivaldi" to "Vivaldi Safe Storage",
            "Opera" to "Opera Safe Storage",
            "Arc" to "Arc Safe Storage",
            "Chromium" to "Chromium Safe Storage",
        )

    /** Read the per-browser secret out of the macOS login keychain. */
    private fun macKey(browserName: String): SecretKeySpec {
        val service = KEYCHAIN_SERVICES[browserName] ?: "$browserName Safe Storage"
        val process =
            ProcessBuilder("security", "find-generic-password", "-w", "-s", service)
                .redirectErrorStream(false)
                .start()

        val secret =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        val error = process.errorStream.bufferedReader().readText()

        if (!process.waitFor(SECURITY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw KeyUnavailableException("The keychain prompt timed out.")
        }

        if (process.exitValue() != 0 || secret.isEmpty()) {
            logger.warn(
                LogCategory.AUTH,
                "Could not read the browser's keychain key",
                mapOf("service" to service, "exit" to process.exitValue()),
            )
            throw KeyUnavailableException(
                if (error.contains("User canceled", ignoreCase = true)) {
                    "Keychain access was denied, so $browserName's passwords can't be read."
                } else {
                    "$browserName's encryption key isn't in the keychain — it may never have saved a password."
                },
            )
        }
        return pbkdf2(secret, ITERATIONS_MAC)
    }

    /**
     * Linux: without a keyring binding, only the documented fallback password
     * works. Profiles sealed by a real keyring will fail to decrypt, which the
     * caller reports per entry rather than aborting.
     */
    private fun linuxKey(): SecretKeySpec = pbkdf2("peanuts", ITERATIONS_LINUX)

    private fun pbkdf2(
        password: String,
        iterations: Int,
    ): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), SALT.toByteArray(Charsets.UTF_8), iterations, KEY_LENGTH_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec)
        // Clear the char[] the spec copied; the derived key is what we keep.
        spec.clearPassword()
        return SecretKeySpec(key.encoded, "AES")
    }

    /**
     * Decrypt one `password_value`, or null when the blob cannot be read.
     *
     * Returns null rather than throwing so one unreadable row does not abandon
     * the whole import.
     */
    fun decrypt(
        blob: ByteArray,
        key: SecretKeySpec,
    ): String? {
        if (blob.isEmpty()) return null

        return try {
            when {
                blob.startsWithPrefix(V10) || blob.startsWithPrefix(V11) -> {
                    // macOS/Linux v10 is CBC with a fixed all-spaces IV.
                    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                    val iv = IvParameterSpec(ByteArray(16) { ' '.code.toByte() })
                    cipher.init(Cipher.DECRYPT_MODE, key, iv)
                    val plain = cipher.doFinal(blob.copyOfRange(V10.size, blob.size))
                    String(stripPkcs7(plain), Charsets.UTF_8)
                }

                else -> {
                    // Older profiles stored the value unencrypted.
                    String(blob, Charsets.UTF_8)
                }
            }
        } catch (e: GeneralSecurityException) {
            logger.debug(
                LogCategory.AUTH,
                "Skipping an undecryptable password entry",
                mapOf("reason" to (e::class.simpleName ?: "unknown")),
            )
            null
        }
    }

    /**
     * Windows v10 blobs are AES-GCM with a 12-byte nonce. Present for
     * completeness; unreachable until DPAPI key unwrapping exists.
     */
    fun decryptGcm(
        blob: ByteArray,
        key: SecretKeySpec,
    ): String? =
        try {
            val nonce = blob.copyOfRange(V10.size, V10.size + 12)
            val payload = blob.copyOfRange(V10.size + 12, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            logger.debug(LogCategory.AUTH, "GCM decrypt failed", mapOf("reason" to (e::class.simpleName ?: "?")))
            null
        }

    /** Read `os_crypt.encrypted_key` from a Chromium `Local State` file. */
    fun readWindowsEncryptedKey(userDataDir: File): ByteArray? =
        runCatching {
            val localState = File(userDataDir, "Local State")
            if (!localState.isFile) return null
            val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(localState.readText()).jsonObject
            val encoded =
                root["os_crypt"]
                    ?.jsonObject
                    ?.get("encrypted_key")
                    ?.jsonPrimitive
                    ?.content ?: return null
            Base64.getDecoder().decode(encoded)
        }.getOrNull()

    private fun ByteArray.startsWithPrefix(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    /** CBC/NoPadding leaves PKCS#7 padding in place; strip it if well-formed. */
    private fun stripPkcs7(data: ByteArray): ByteArray {
        val pad = data.lastOrNull()?.toInt() ?: 0
        // Only strip when the length is plausible AND every padding byte
        // matches; otherwise those bytes were real data.
        val strippable =
            pad in 1..16 &&
                pad <= data.size &&
                (data.size - pad until data.size).all { data[it].toInt() == pad }
        return if (strippable) data.copyOfRange(0, data.size - pad) else data
    }
}
