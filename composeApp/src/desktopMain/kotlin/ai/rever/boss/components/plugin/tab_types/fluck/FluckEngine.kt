package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions

// Singleton engine for all browser tabs
object FluckEngine {
    val engine: Engine by lazy {
        Engine.newInstance(
            EngineOptions.newBuilder(JxBrowserConfig.renderingMode)
                .licenseKey(JxBrowserConfig.licenseKey)
                .build()
        )
    }
} 