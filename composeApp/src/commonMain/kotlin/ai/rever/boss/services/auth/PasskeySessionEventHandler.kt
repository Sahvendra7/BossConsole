package ai.rever.boss.services.auth

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * PasskeySessionEventHandler - Handles passkey session completion events from deep links
 *
 * This service coordinates cross-device passkey flows by:
 * - Tracking active passkey sessions by sessionId
 * - Notifying listeners when passkey operations complete via deep links
 * - Triggering appropriate UI updates and authentication completion
 */
object PasskeySessionEventHandler {

    /**
     * Passkey session event types
     */
    sealed class PasskeySessionEvent {
        data class RegistrationCompleted(val sessionId: String) : PasskeySessionEvent()
        data class AuthenticationCompleted(val sessionId: String) : PasskeySessionEvent()
    }

    /**
     * Flow of passkey session events
     */
    private val _sessionEvents = MutableStateFlow<PasskeySessionEvent?>(null)

    /**
     * Map of active sessions being tracked
     * Key: sessionId, Value: session metadata
     */
    private val activeSessions = mutableMapOf<String, SessionMetadata>()

    data class SessionMetadata(
        val sessionId: String,
        val email: String,
        val type: SessionType,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class SessionType {
    }

    /**
     * Handle passkey registration completion from deep link
     */
    fun handleRegistrationCompleted(sessionId: String) {
        println("PasskeySessionEventHandler: Registration completed for session: $sessionId")

        val metadata = activeSessions[sessionId]
        if (metadata != null) {
            _sessionEvents.value = PasskeySessionEvent.RegistrationCompleted(sessionId)
            println("PasskeySessionEventHandler: Notified listeners of registration completion")
        } else {
            println("PasskeySessionEventHandler: Warning - No active session found for: $sessionId")
        }
    }

    /**
     * Handle passkey authentication completion from deep link
     */
    fun handleAuthenticationCompleted(sessionId: String) {
        println("PasskeySessionEventHandler: Authentication completed for session: $sessionId")

        val metadata = activeSessions[sessionId]
        if (metadata != null) {
            _sessionEvents.value = PasskeySessionEvent.AuthenticationCompleted(sessionId)
            println("PasskeySessionEventHandler: Notified listeners of authentication completion")
        } else {
            println("PasskeySessionEventHandler: Warning - No active session found for: $sessionId")
        }
    }

    /**
     * Get metadata for an active session
     */
    fun getSessionMetadata(sessionId: String): SessionMetadata? {
        return activeSessions[sessionId]
    }

}
