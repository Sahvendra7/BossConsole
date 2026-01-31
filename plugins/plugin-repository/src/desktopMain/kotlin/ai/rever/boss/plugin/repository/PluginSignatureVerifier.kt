package ai.rever.boss.plugin.repository

import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64
import java.util.jar.JarFile

/**
 * Result of plugin signature verification.
 */
sealed class SignatureVerificationResult {
    /**
     * Plugin is signed and verified.
     */
    data class Verified(
        val publisher: String,
        val certificate: String
    ) : SignatureVerificationResult()

    /**
     * Plugin is not signed.
     */
    data object Unsigned : SignatureVerificationResult()

    /**
     * Plugin signature verification failed.
     */
    data class Failed(
        val reason: String,
        val error: Throwable? = null
    ) : SignatureVerificationResult()

    val isVerified: Boolean get() = this is Verified
}

/**
 * Verifies plugin JAR signatures and checksums.
 *
 * Supports:
 * - SHA-256 checksum verification
 * - Standard JAR signing (uses java.util.jar)
 * - Custom signature files (.sig alongside .jar)
 */
class PluginSignatureVerifier {
    private val logger = BossLogger.forComponent("PluginSignatureVerifier")

    /**
     * Trusted public keys by publisher name.
     */
    private val trustedKeys = mutableMapOf<String, PublicKey>()

    /**
     * Add a trusted public key for signature verification.
     *
     * @param publisher Publisher name
     * @param publicKeyPem Public key in PEM format
     */
    fun addTrustedKey(publisher: String, publicKeyPem: String) {
        try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val pemContent = publicKeyPem
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s".toRegex(), "")

            val decoded = Base64.getDecoder().decode(pemContent)
            val certificate = certificateFactory.generateCertificate(decoded.inputStream())
            trustedKeys[publisher] = certificate.publicKey

            logger.info(LogCategory.SYSTEM, "Added trusted key", mapOf(
                "publisher" to publisher
            ))
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to add trusted key", mapOf(
                "publisher" to publisher
            ), e)
        }
    }

    /**
     * Remove a trusted public key.
     *
     * @param publisher Publisher name
     */
    fun removeTrustedKey(publisher: String) {
        trustedKeys.remove(publisher)
    }

    /**
     * Verify a plugin JAR's signature.
     *
     * @param jarPath Path to the plugin JAR
     * @return Verification result
     */
    fun verifySignature(jarPath: String): SignatureVerificationResult {
        val jarFile = File(jarPath)
        if (!jarFile.exists()) {
            return SignatureVerificationResult.Failed("JAR file not found: $jarPath")
        }

        return try {
            // Check for standard JAR signing first
            val standardResult = verifyJarSigning(jarFile)
            if (standardResult.isVerified) {
                return standardResult
            }

            // Check for custom .sig file
            val sigFile = File("$jarPath.sig")
            if (sigFile.exists()) {
                return verifyDetachedSignature(jarFile, sigFile)
            }

            SignatureVerificationResult.Unsigned
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Signature verification failed", mapOf(
                "jarPath" to jarPath
            ), e)
            SignatureVerificationResult.Failed("Verification error: ${e.message}", e)
        }
    }

    /**
     * Verify SHA-256 checksum of a JAR.
     *
     * @param jarPath Path to the JAR file
     * @param expectedSha256 Expected SHA-256 hash (hex string)
     * @return True if checksum matches
     */
    fun verifyChecksum(jarPath: String, expectedSha256: String): Boolean {
        return try {
            val jarFile = File(jarPath)
            if (!jarFile.exists()) {
                return false
            }

            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(jarFile).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val matches = actualHash.equals(expectedSha256, ignoreCase = true)

            if (!matches) {
                logger.warn(LogCategory.SYSTEM, "Checksum mismatch", mapOf(
                    "jarPath" to jarPath,
                    "expected" to expectedSha256,
                    "actual" to actualHash
                ))
            }

            matches
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Checksum verification failed", mapOf(
                "jarPath" to jarPath
            ), e)
            false
        }
    }

    /**
     * Calculate SHA-256 checksum of a file.
     *
     * @param filePath Path to the file
     * @return SHA-256 hash as hex string
     */
    fun calculateChecksum(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                return null
            }

            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Failed to calculate checksum", mapOf(
                "filePath" to filePath
            ), e)
            null
        }
    }

    /**
     * Verify standard JAR signing.
     */
    private fun verifyJarSigning(jarFile: File): SignatureVerificationResult {
        return try {
            JarFile(jarFile, true).use { jar ->
                // Read all entries to trigger verification
                val entries = jar.entries()
                var hasSignedEntries = false

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory) {
                        // Reading the entry triggers verification
                        jar.getInputStream(entry).use { input ->
                            val buffer = ByteArray(8192)
                            while (input.read(buffer) != -1) {
                                // Just read to trigger verification
                            }
                        }

                        // Check if this entry is signed
                        val certs = entry.codeSigners
                        if (certs != null && certs.isNotEmpty()) {
                            hasSignedEntries = true
                        }
                    }
                }

                if (hasSignedEntries) {
                    // Get signer info from the manifest
                    val manifest = jar.manifest
                    val signerInfo = manifest?.mainAttributes?.getValue("Created-By") ?: "Unknown"

                    SignatureVerificationResult.Verified(
                        publisher = signerInfo,
                        certificate = "JAR Signed"
                    )
                } else {
                    SignatureVerificationResult.Unsigned
                }
            }
        } catch (e: SecurityException) {
            SignatureVerificationResult.Failed("JAR signature invalid: ${e.message}", e)
        }
    }

    /**
     * Verify a detached signature file.
     */
    private fun verifyDetachedSignature(jarFile: File, sigFile: File): SignatureVerificationResult {
        return try {
            val signatureBytes = sigFile.readBytes()

            // Try each trusted key
            for ((publisher, publicKey) in trustedKeys) {
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initVerify(publicKey)

                FileInputStream(jarFile).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        signature.update(buffer, 0, bytesRead)
                    }
                }

                if (signature.verify(signatureBytes)) {
                    return SignatureVerificationResult.Verified(
                        publisher = publisher,
                        certificate = "Detached signature"
                    )
                }
            }

            SignatureVerificationResult.Failed("No trusted key verified the signature")
        } catch (e: Exception) {
            SignatureVerificationResult.Failed("Signature verification error: ${e.message}", e)
        }
    }
}
