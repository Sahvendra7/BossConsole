package ai.rever.boss.filetypes

import ai.rever.boss.plugin.pathutils.BossDirectories
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the user has already been asked about default applications.
 *
 * @property promptShown true once the one-time offer has been put in front of
 *   them. The only thing that keeps this a one-time offer.
 * @property declinedCategories categories they said no to. Kept so a later "Set
 *   all" from Settings does not silently re-claim something they refused, and so
 *   the offer can be narrowed rather than repeated.
 */
@Serializable
internal data class DefaultAppsSettings(
    val promptShown: Boolean = false,
    val declinedCategories: Set<String> = emptySet(),
)

/**
 * Persists the default-applications decisions in `~/.boss/default-apps.json`.
 *
 * Same shape as `ScrollbarSettingsManager` and the other settings managers:
 * synchronous load at init, asynchronous save, defaults on any failure.
 *
 * **Persisted, unlike the missing-plugin declines.** Those are per-session
 * because "not now" is an answer about now and a persisted one would leave no way
 * to be asked again. This is the opposite: an offer to take over the user's file
 * associations must be made once and never again unless they go looking for it.
 * Being asked at every launch is precisely the behaviour that makes people
 * distrust a browser.
 */
internal object DefaultAppsSettingsManager {
    private val logger = BossLogger.forComponent("DefaultAppsSettingsManager")

    private val settingsFile = BossDirectories.resolve("default-apps.json")

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    private val _settings = MutableStateFlow(DefaultAppsSettings())
    val settings: StateFlow<DefaultAppsSettings> = _settings.asStateFlow()

    init {
        settingsFile.parentFile?.mkdirs()
        load()
    }

    private fun load() {
        try {
            if (!settingsFile.exists()) return
            _settings.value = json.decodeFromString<DefaultAppsSettings>(settingsFile.readText())
            logger.debug(
                LogCategory.SYSTEM,
                "Loaded default-apps settings",
                mapOf("promptShown" to _settings.value.promptShown),
            )
        } catch (e: Exception) {
            // Defaults, which means the prompt may be offered again. Better than
            // the alternative failure direction: a corrupt file that silently
            // suppressed the offer forever would leave no way to discover the
            // feature at all.
            logger.warn(LogCategory.SYSTEM, "Could not read default-apps settings", error = e)
            _settings.value = DefaultAppsSettings()
        }
    }

    /** True when the one-time offer has not been made yet. */
    fun shouldOfferPrompt(): Boolean = !_settings.value.promptShown

    suspend fun markPromptShown() {
        update { it.copy(promptShown = true) }
    }

    suspend fun markDeclined(categoryIds: Collection<String>) {
        update { it.copy(declinedCategories = it.declinedCategories + categoryIds) }
    }

    suspend fun clearDeclined(categoryIds: Collection<String>) {
        update { it.copy(declinedCategories = it.declinedCategories - categoryIds.toSet()) }
    }

    private suspend fun update(transform: (DefaultAppsSettings) -> DefaultAppsSettings) {
        _settings.value = transform(_settings.value)
        save()
    }

    private suspend fun save() =
        withContext(Dispatchers.IO) {
            try {
                settingsFile.parentFile?.mkdirs()
                settingsFile.writeText(json.encodeToString(DefaultAppsSettings.serializer(), _settings.value))
            } catch (e: Exception) {
                // Logged, not surfaced: the decision has already taken effect in
                // this session, and the only consequence of a failed write is
                // being asked again next launch.
                logger.warn(LogCategory.SYSTEM, "Could not save default-apps settings", error = e)
            }
        }
}
