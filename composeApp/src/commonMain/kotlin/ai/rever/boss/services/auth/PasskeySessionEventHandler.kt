package ai.rever.boss.services.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    val sessionEvents: StateFlow<PasskeySessionEvent?> = _sessionEvents

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
        REGISTRATION,
        AUTHENTICATION
    }

    /**
     * Register a new active session for tracking
     */
    fun registerActiveSession(sessionId: String, email: String, type: SessionType) {
        activeSessions[sessionId] = SessionMetadata(
            sessionId = sessionId,
            email = email,
            type = type
        )
        println("PasskeySessionEventHandler: Registered $type session: $sessionId for $email")
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
     * Clear a session after it's been handled
     */
    fun clearSession(sessionId: String) {
        activeSessions.remove(sessionId)
        _sessionEvents.value = null
        println("PasskeySessionEventHandler: Cleared session: $sessionId")
    }

    /**
     * Get metadata for an active session
     */
    fun getSessionMetadata(sessionId: String): SessionMetadata? {
        return activeSessions[sessionId]
    }

    /**
     * Clear all sessions (cleanup)
     */
    fun clearAllSessions() {
        activeSessions.clear()
        _sessionEvents.value = null
        println("PasskeySessionEventHandler: Cleared all sessions")
    }
}
