package ai.rever.boss.service.settings

import ai.rever.boss.ipc.proto.services.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * gRPC implementation of SettingsService.
 * Phase 6 skeleton — in-memory settings store with reactive streaming.
 */
class SettingsServiceImpl : SettingsServiceGrpcKt.SettingsServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(SettingsServiceImpl::class.java)
    private val settings = ConcurrentHashMap<String, SettingValue>()
    private val changes = MutableSharedFlow<SettingValue>(extraBufferCapacity = 64)

    /** Composite key combining namespace and key for unique storage. */
    private fun storageKey(namespace: String, key: String): String =
        if (namespace.isBlank()) key else "$namespace/$key"

    override suspend fun getSetting(request: GetSettingRequest): SettingValue {
        val stored = settings[storageKey(request.namespace, request.key)]
        return stored ?: SettingValue.newBuilder()
            .setKey(request.key)
            .setNamespace(request.namespace)
            .setValue(request.defaultValue)
            .setFound(false)
            .build()
    }

    override suspend fun setSetting(request: SetSettingRequest): SettingValue {
        logger.debug("setSetting: namespace={}, key={}", request.namespace, request.key)
        val value = SettingValue.newBuilder()
            .setKey(request.key)
            .setValue(request.value)
            .setFound(true)
            .setNamespace(request.namespace)
            .setUpdatedAt(System.currentTimeMillis())
            .build()
        settings[storageKey(request.namespace, request.key)] = value
        changes.tryEmit(value)
        return value
    }

    override fun watchSetting(request: GetSettingRequest): Flow<SettingValue> = flow {
        // Emit current value first
        settings[storageKey(request.namespace, request.key)]?.let { emit(it) }
        // Then stream changes matching this key and namespace
        changes
            .filter { it.key == request.key && it.namespace == request.namespace }
            .collect { emit(it) }
    }

    override suspend fun listSettings(request: ListSettingsRequest): SettingsListResponse {
        val prefix = request.namespacePrefix
        val all = if (prefix.isBlank()) {
            settings.values.toList()
        } else {
            settings.values.filter { it.namespace.startsWith(prefix) }
        }
        val total = all.size
        val limit = if (request.limit > 0) request.limit else Int.MAX_VALUE
        val offset = if (request.offset > 0) request.offset else 0
        val page = all.drop(offset).take(limit)
        return SettingsListResponse.newBuilder()
            .addAllSettings(page)
            .setTotalCount(total)
            .build()
    }
}
