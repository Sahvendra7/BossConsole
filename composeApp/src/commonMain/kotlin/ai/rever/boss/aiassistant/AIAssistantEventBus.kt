package ai.rever.boss.aiassistant

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for AI Assistant launch requests from keyboard shortcuts.
 * Allows commonMain code (BossActionHandler) to trigger AI assistant launches
 * that are handled in desktopMain (terminal integration).
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
object AIAssistantEventBus {

    /**
     * Event data for launching an AI assistant.
     */
    data class LaunchRequest(
        val assistant: AIAssistant,
        val workingDirectory: String? = null
    )

    private val _launchRequests = MutableSharedFlow<LaunchRequest>(extraBufferCapacity = 1)

    /**
     * Flow of launch requests.
     * Terminal content should collect this and handle launching.
     */
    val launchRequests: SharedFlow<LaunchRequest> = _launchRequests.asSharedFlow()

    /**
     * Request to launch an AI assistant.
     * Called from keyboard shortcut handlers.
     */
    suspend fun requestLaunch(assistant: AIAssistant, workingDirectory: String? = null) {
        _launchRequests.emit(LaunchRequest(assistant, workingDirectory))
    }

    /**
     * Request to launch an AI assistant (non-suspending version).
     */
    fun tryRequestLaunch(assistant: AIAssistant, workingDirectory: String? = null): Boolean {
        return _launchRequests.tryEmit(LaunchRequest(assistant, workingDirectory))
    }
}
