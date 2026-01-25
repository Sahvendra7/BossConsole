package ai.rever.boss.services.network

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for monitoring network connectivity with auto-retry
 */
object NetworkMonitorService {
    private val logger = BossLogger.forComponent("NetworkMonitorService")
    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Checking)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _isAutoRetrying = MutableStateFlow(false)
    val isAutoRetrying: StateFlow<Boolean> = _isAutoRetrying.asStateFlow()

    private val _nextRetryCountdown = MutableStateFlow(0)
    val nextRetryCountdown: StateFlow<Int> = _nextRetryCountdown.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var autoRetryJob: Job? = null

    private const val CONNECTIVITY_CHECK_URL = "https://api.risaboss.com/health"
    private const val FALLBACK_CHECK_URL = "https://www.google.com"
    private const val INITIAL_CONNECTION_TIMEOUT_MS = 10000  // 10s for initial check (slow networks)
    private const val RETRY_CONNECTION_TIMEOUT_MS = 3000     // 3s for auto-retry (more responsive)
    private const val AUTO_RETRY_INTERVAL_SECONDS = 5

    // Track if this is an auto-retry check (uses shorter timeout)
    private var isAutoRetryCheck = false

    /**
     * Check network connectivity
     */
    suspend fun checkConnectivity(): Boolean = withContext(Dispatchers.IO) {
        _networkState.value = NetworkState.Checking

        val isConnected = try {
            performConnectivityCheck(CONNECTIVITY_CHECK_URL) ||
                performConnectivityCheck(FALLBACK_CHECK_URL)
        } catch (e: Exception) {
            logger.warn(LogCategory.NETWORK, "Connectivity check failed", error = e)
            false
        }

        val currentState = _networkState.value
        val retryAttempt = if (currentState is NetworkState.Disconnected) {
            currentState.retryAttempt + 1
        } else {
            0
        }

        _networkState.value = if (isConnected) {
            NetworkState.Connected
        } else {
            NetworkState.Disconnected(
                lastCheckTime = System.currentTimeMillis(),
                retryAttempt = retryAttempt
            )
        }

        isConnected
    }

    private fun performConnectivityCheck(urlString: String): Boolean {
        val timeoutMs = if (isAutoRetryCheck) RETRY_CONNECTION_TIMEOUT_MS else INITIAL_CONNECTION_TIMEOUT_MS
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..399
        } catch (e: Exception) {
            logger.debug(LogCategory.NETWORK, "Connectivity check failed", mapOf("url" to urlString))
            false
        }
    }

    /**
     * Start auto-retry loop (every 5 seconds)
     */
    fun startAutoRetry(onConnected: suspend () -> Unit) {
        if (autoRetryJob?.isActive == true) return

        _isAutoRetrying.value = true
        isAutoRetryCheck = true  // Use shorter timeout for retry checks
        autoRetryJob = scope.launch {
            while (isActive && _networkState.value !is NetworkState.Connected) {
                for (i in AUTO_RETRY_INTERVAL_SECONDS downTo 1) {
                    _nextRetryCountdown.value = i
                    delay(1000)
                }
                _nextRetryCountdown.value = 0

                val connected = checkConnectivity()
                if (connected) {
                    _isAutoRetrying.value = false
                    isAutoRetryCheck = false
                    onConnected()
                    break
                }
            }
        }
    }

    /**
     * Stop auto-retry loop
     */
    fun stopAutoRetry() {
        autoRetryJob?.cancel()
        autoRetryJob = null
        _isAutoRetrying.value = false
        _nextRetryCountdown.value = 0
        isAutoRetryCheck = false
    }

    /**
     * Trigger manual retry
     */
    suspend fun manualRetry(): Boolean {
        stopAutoRetry()
        return checkConnectivity()
    }

    /**
     * Reset service state
     */
    fun reset() {
        stopAutoRetry()
        _networkState.value = NetworkState.Checking
    }

    /**
     * Cleanup resources - call on application shutdown
     */
    fun cleanup() {
        stopAutoRetry()
        scope.coroutineContext[Job]?.cancel()
    }
}
