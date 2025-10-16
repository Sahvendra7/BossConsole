package ai.rever.boss.services.passkey

import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * macOS Keychain operations for passkey management
 * Handles storing, retrieving, and deleting passkeys from macOS Keychain
 */
object MacOSKeychainManager {

    /**
     * Delete a passkey from macOS Keychain
     */
    suspend fun deletePasskey(credentialId: String): Result<Boolean> = suspendCoroutine { continuation ->
        GlobalScope.launch(Dispatchers.IO) {
            try {
                println("MacOSKeychainManager: Deleting passkey with ID: $credentialId")
                
                val result = SwiftScriptExecutor.executeSwiftFile("DeletePasskey.swift", credentialId)
                
                when {
                    result.contains("SUCCESS") -> {
                        println("MacOSKeychainManager: Passkey deletion successful")
                        continuation.resume(Result.success(true))
                    }
                    result.contains("FAILED") -> {
                        println("MacOSKeychainManager: Passkey deletion failed")
                        continuation.resume(Result.success(false))
                    }
                    result.contains("WARNING") -> {
                        println("MacOSKeychainManager: Passkey not found (already deleted)")
                        continuation.resume(Result.success(true))
                    }
                    else -> {
                        println("MacOSKeychainManager: Unknown deletion result: $result")
                        continuation.resume(Result.failure(Exception("Unknown deletion result: $result")))
                    }
                }

            } catch (e: Exception) {
                println("MacOSKeychainManager: Exception during passkey deletion: ${e.message}")
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * List all BOSS passkeys stored in keychain
     */
    suspend fun listPasskeys(): Result<List<String>> = suspendCoroutine { continuation ->
        GlobalScope.launch(Dispatchers.IO) {
            try {
                println("MacOSKeychainManager: Listing passkeys from keychain...")
                
                val result = SwiftScriptExecutor.executeSwiftFile("ListPasskeys.swift")
                
                if (result.contains("EMPTY")) {
                    println("MacOSKeychainManager: No passkeys found")
                    continuation.resume(Result.success(emptyList()))
                } else {
                    val passkeys = result.lines()
                        .filter { it.startsWith("PASSKEY:") }
                        .map { it.substringAfter("PASSKEY:") }
                    
                    println("MacOSKeychainManager: Found ${passkeys.size} passkeys")
                    continuation.resume(Result.success(passkeys))
                }

            } catch (e: Exception) {
                println("MacOSKeychainManager: Exception during passkey listing: ${e.message}")
                continuation.resume(Result.failure(e))
            }
        }
    }
}
