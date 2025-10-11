package ai.rever.boss.services.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import ai.rever.boss.services.supabase.models.UserInfo

/**
 * Persistent storage for user data to survive app restarts
 *
 * WHY THIS EXISTS (Important - Not a Workaround!):
 * ================================================
 *
 * This is the CORRECT solution for custom authentication providers with Supabase.
 * It is NOT a hack or temporary workaround - it's the recommended pattern.
 *
 * Background:
 * -----------
 * Supabase Auth was designed for built-in authentication providers (OAuth, email/password, magic links).
 * When you use a built-in provider, Supabase generates JWT tokens that include full user information,
 * and the Supabase-KT client automatically populates session.user with this data.
 *
 * Custom Authentication Providers (like Passkeys):
 * ------------------------------------------------
 * For custom authentication providers (WebAuthn/passkeys), we implement the authentication
 * logic ourselves:
 *
 * 1. Client verifies passkey signature (Touch ID, Windows Hello, etc.)
 * 2. Edge Function generates Supabase-compatible JWT tokens
 * 3. Client imports session using auth.importSession()
 * 4. **Problem**: Supabase-KT intentionally does NOT populate session.user from custom JWTs
 *    - This is by design, not a bug
 *    - Custom JWTs don't include the user metadata that built-in providers include
 *    - The session.user property remains null
 *
 * 5. **Solution**: UserDataStorage persists user information separately
 *    - We store user data (id, email, createdAt) in local storage
 *    - This data persists across app restarts
 *    - SessionManager coordinates between Supabase auth (JWT tokens) and UserDataStorage (user info)
 *
 * Why Not Use Magic Links Instead?
 * --------------------------------
 * Magic links would populate session.user, but they:
 * - Break the passwordless/biometric UX flow
 * - Add unnecessary friction (email verification step)
 * - Defeat the purpose of passkey authentication
 * - Are less secure (email interception risk)
 *
 * The Correct Pattern:
 * -------------------
 * For custom authentication providers with Supabase:
 * 1. Implement authentication logic yourself (verify passkey, etc.)
 * 2. Generate Supabase-compatible JWT tokens on the backend
 * 3. Use importSession() to establish the Supabase session (for API access)
 * 4. Persist user data separately (UserDataStorage) for app state
 * 5. Use SessionManager to coordinate both
 *
 * This pattern is used by many Supabase applications that implement custom auth providers.
 *
 * Related Documentation:
 * ---------------------
 * - See SessionManager.kt for session orchestration logic
 * - See PasskeyAuthService.kt for passkey authentication implementation
 * - See CoreAuthService.kt for session initialization and restoration
 *
 * Storage Location:
 * ----------------
 * User data is stored in: ~/.boss/user_data.json
 * This file is automatically created and managed by this service.
 */
object UserDataStorage {
    private val storageFile = File(System.getProperty("user.home"), ".boss/user_data.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Serializable
    data class StoredUserData(
        val id: String,
        val email: String,
        val createdAt: String,
        val authenticatedVia: String? = null  // "passkey", "magic_link", "password", etc.
    )

    init {
        // Ensure directory exists
        storageFile.parentFile?.mkdirs()
    }

    /**
     * Save user data to persistent storage
     */
    fun saveUserData(user: UserInfo, authenticatedVia: String? = null) {
        try {
            val data = StoredUserData(
                id = user.id,
                email = user.email,
                createdAt = user.createdAt,
                authenticatedVia = authenticatedVia
            )
            val content = json.encodeToString(data)
            storageFile.writeText(content)
            println("UserDataStorage: Saved user data for ${user.email}")
        } catch (e: Exception) {
            println("UserDataStorage: Error saving user data: ${e.message}")
        }
    }

    /**
     * Load user data from persistent storage
     */
    fun loadUserData(): UserInfo? {
        return try {
            if (storageFile.exists()) {
                val content = storageFile.readText()
                val data = json.decodeFromString<StoredUserData>(content)
                println("UserDataStorage: Loaded user data for ${data.email} (authenticated via: ${data.authenticatedVia})")
                UserInfo(
                    id = data.id,
                    email = data.email,
                    createdAt = data.createdAt
                )
            } else {
                println("UserDataStorage: No stored user data found")
                null
            }
        } catch (e: Exception) {
            println("UserDataStorage: Error loading user data: ${e.message}")
            null
        }
    }

    /**
     * Clear stored user data (on logout)
     */
    fun clearUserData() {
        try {
            if (storageFile.exists()) {
                storageFile.delete()
                println("UserDataStorage: Cleared user data")
            }
        } catch (e: Exception) {
            println("UserDataStorage: Error clearing user data: ${e.message}")
        }
    }
}
