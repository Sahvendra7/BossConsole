package ai.rever.boss.services.network

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
    private const val CONNECTION_TIMEOUT_MS = 5000
    private const val AUTO_RETRY_INTERVAL_SECONDS = 5

    /**
     * Check network connectivity
     */
    suspend fun checkConnectivity(): Boolean = withContext(Dispatchers.IO) {
        _networkState.value = NetworkState.Checking

        val isConnected = try {
            performConnectivityCheck(CONNECTIVITY_CHECK_URL) ||
                performConnectivityCheck(FALLBACK_CHECK_URL)
        } catch (e: Exception) {
            println("NetworkMonitorService: Connectivity check failed: ${e.message}")
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
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = CONNECTION_TIMEOUT_MS
            connection.useCaches = false
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..399
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Start auto-retry loop (every 5 seconds)
     */
    fun startAutoRetry(onConnected: suspend () -> Unit) {
        if (autoRetryJob?.isActive == true) return

        _isAutoRetrying.value = true
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
    }

    /**
     * Trigger manual retry
     */
    suspend fun manualRetry(): Boolean {
        stopAutoRetry()
        return checkConnectivity()
    }

    fun reset() {
        stopAutoRetry()
        _networkState.value = NetworkState.Checking
    }
}
