package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabComponent
import ai.rever.boss.components.plugin.tab_types.fluck.LockedBrowser
import com.teamdev.jxbrowser.browser.Browser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

private val browserAccessorLogger = BossLogger.forComponent("DesktopBrowserAccessor")

/**
 * Desktop implementation of browser accessor using JxBrowser
 */
actual class BrowserAccessor {
    actual fun getActiveBrowserIntegration(): BrowserIntegration? {
        // Direct implementation - don't rely on ConnectToFluckBrowser being called
        val tabId = selectedTabId ?: return null
        
        // Get the browser directly from the stored reference if available
        if (currentBrowserIntegration != null && currentBrowserIntegration!!.isBrowserAvailable()) {
            return currentBrowserIntegration
        }
        
        // Try to find browser directly if we have access to split view state
        val splitViewState = lastKnownSplitViewState
        if (splitViewState != null) {
            val browser = findBrowserForTab(splitViewState, tabId)
            if (browser != null) {
                currentBrowserIntegration = DesktopBrowserIntegration(browser)
                return currentBrowserIntegration
            }
        }
        
        return null
    }
    
    actual companion object {
        var currentBrowserIntegration: BrowserIntegration? = null
        actual var selectedTabId: String? = null
        var lastKnownSplitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null
    }
}

/**
 * Desktop browser integration using JxBrowser with thread-safe LockedBrowser wrapper
 */
class DesktopBrowserIntegration(
    internal val browser: LockedBrowser
) : BrowserIntegration {

    override suspend fun executeJavaScript(script: String): Any? = withContext(Dispatchers.Main) {
        try {
            val mainFrame = browser.mainFrame().orElse(null)
            if (mainFrame != null) {
                mainFrame.executeJavaScript<Any>(script)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun isBrowserAvailable(): Boolean {
        return try {
            !browser.isClosed
        } catch (e: Exception) {
            // Browser was disposed or became inaccessible
            false
        }
    }

    override suspend fun getCurrentUrl(): String? = withContext(Dispatchers.Main) {
        try {
            browser.url()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Direct browser lookup function - returns thread-safe LockedBrowser wrapper
 */
private fun findBrowserForTab(
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState,
    tabId: String
): LockedBrowser? {
    return try {
        // Get all active Fluck tabs
        val activeFluckTabs = splitViewState.collectAllActiveFluckTabs()

        // Find the selected tab
        val selectedTab = activeFluckTabs.find { activeTab ->
            val tabInfo = activeTab.tabInfo
            when (tabInfo) {
                is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo -> tabInfo.id == tabId
                is FluckTabComponent -> tabInfo.config.id == tabId
                else -> false
            }
        }

        if (selectedTab != null) {
            val tabInfo = selectedTab.tabInfo

            // Get browser based on tab info type
            return when (tabInfo) {
                is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo -> {
                    val component = findFluckTabComponentById(splitViewState, tabInfo.id)
                    if (component != null) {
                        try {
                            val rawBrowser = component.browser as? Browser
                            if (rawBrowser != null && !rawBrowser.isClosed) {
                                LockedBrowser(rawBrowser, component.browserLock)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            // Component might be disposing
                            null
                        }
                    } else {
                        null
                    }
                }
                is FluckTabComponent -> {
                    try {
                        val rawBrowser = tabInfo.browser as? Browser
                        if (rawBrowser != null && !rawBrowser.isClosed) {
                            LockedBrowser(rawBrowser, tabInfo.browserLock)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        // Component might be disposing
                        null
                    }
                }
                else -> null
            }
        }

        null
    } catch (e: Exception) {
        // Handle any exceptions during browser lookup
        browserAccessorLogger.warn(LogCategory.BROWSER, "Error finding browser for tab", mapOf("tabId" to tabId), error = e)
        null
    }
}

/**
 * Helper function to find FluckTabComponent by ID in the SplitViewState
 */
private fun findFluckTabComponentById(
    splitViewState: ai.rever.boss.components.window_panel.SplitViewState, 
    tabId: String
): FluckTabComponent? {
    // Search through all panels
    val allPanels = splitViewState.getAllPanels()
    
    for (panel in allPanels) {
        val tabsComponent = panel.tabsComponent
        
        // Use the public API method to get the component
        val component = tabsComponent.getComponentById(tabId)
        
        if (component is FluckTabComponent) {
            return component
        }
    }
    
    return null
}

/**
 * Desktop implementation to store split view state
 */
actual fun storeSplitViewState(splitViewState: Any) {
    BrowserAccessor.lastKnownSplitViewState = splitViewState as? ai.rever.boss.components.window_panel.SplitViewState
}

/**
 * Desktop implementation to create FluckTabInfo from ActiveTab
 */
actual fun createFluckTabInfo(activeTab: Any): FluckTabInfo? {
    // ActiveTab is from TopOfMind package
    val activeTabTyped = activeTab as? ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.ActiveTab
        ?: return null
    
    val tabInfo = activeTabTyped.tabInfo
    
    // Check if this is a Fluck tab by checking the TabInfo type
    if (tabInfo is ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo) {
        val result = FluckTabInfo(
            id = tabInfo.id,
            title = tabInfo.title,
            url = tabInfo.currentUrl,
            panelId = activeTabTyped.panelId,
            tabComponent = tabInfo // Store the FluckTabInfo itself
        )
        return result
    }
    return null
}

/**
 * Desktop implementation of RPA Recorder Factory
 */
actual class RpaRecorderFactory {
    actual fun createComponent(ctx: com.arkivanov.decompose.ComponentContext, panelInfo: ai.rever.boss.components.registery.PanelInfo): RpaRecorderComponent {
        return DesktopRpaRecorderComponent(ctx, panelInfo)
    }
}

/**
 * Desktop RPA Recorder Component with file saving and video recording
 */
class DesktopRpaRecorderComponent(
    ctx: com.arkivanov.decompose.ComponentContext,
    panelInfo: ai.rever.boss.components.registery.PanelInfo
) : RpaRecorderComponent(ctx, panelInfo) {
    
    // Video recording temporarily disabled
    // private val videoRecorder = BrowserVideoRecorder()
    private var currentRecordingPath: String? = null
    private var recordingSessionId: String? = null
    
    override fun injectEventListeners() {
        super.injectEventListeners()
        
        // Start video recording when RPA recording starts
        val selectedTab = _selectedTab.value
        if (selectedTab != null) {
            // Generate session ID based on timestamp
            recordingSessionId = Clock.System.now().toEpochMilliseconds().toString()
            
            // Video recording temporarily disabled
            // Get browser from selected tab
            /*
            val browserIntegration = BrowserAccessor().getActiveBrowserIntegration()
            if (browserIntegration is DesktopBrowserIntegration) {
                val browser = browserIntegration.browser
                currentRecordingPath = videoRecorder.startRecording(browser, recordingSessionId!!)
                
                if (currentRecordingPath != null) {
                    _isVideoRecording.value = true
                    browserAccessorLogger.info(LogCategory.BROWSER, "Started video recording", mapOf("path" to (currentRecordingPath ?: "")))
                    showFeedback("Video recording started", FeedbackType.INFO)
                }
            }
            */
        }
    }
    
    override fun removeEventListeners() {
        super.removeEventListeners()
        
        // Video recording temporarily disabled
        /*
        // Stop video recording when RPA recording stops
        if (videoRecorder.isRecording()) {
            videoRecorder.stopRecording()
            _isVideoRecording.value = false
            
            if (currentRecordingPath != null) {
                browserAccessorLogger.info(LogCategory.BROWSER, "Stopped video recording", mapOf("path" to (currentRecordingPath ?: "")))
                showFeedback("Video recording saved", FeedbackType.SUCCESS)
            }
        }
        */
    }
    
    override fun saveAndOpenConfiguration(filename: String, content: String) {
        try {
            // Get user's downloads directory
            val userHome = System.getProperty("user.home")
            val downloadsDir = java.io.File(userHome, "Downloads")
            
            // Create rpa_config subdirectory
            val rpaConfigDir = java.io.File(downloadsDir, "rpa_config")
            if (!rpaConfigDir.exists()) {
                rpaConfigDir.mkdirs()
            }
            
            // Save file in rpa_config directory
            val file = java.io.File(rpaConfigDir, filename)
            file.writeText(content)
            
            // If we have a recording, create a session folder with both files
            if (currentRecordingPath != null && recordingSessionId != null) {
                val sessionDir = java.io.File(rpaConfigDir, "session_$recordingSessionId")
                if (!sessionDir.exists()) {
                    sessionDir.mkdirs()
                }
                
                // Move config file to session directory
                val sessionConfigFile = java.io.File(sessionDir, filename)
                file.renameTo(sessionConfigFile)
                
                // The video is already in the recordings subdirectory
                // Create a reference file pointing to the video
                val videoRefFile = java.io.File(sessionDir, "video_reference.txt")
                videoRefFile.writeText("Video recording location: $currentRecordingPath")
                
                // Video recording temporarily disabled
                /*
                // Take a screenshot for preview
                val browserIntegration = BrowserAccessor().getActiveBrowserIntegration()
                if (browserIntegration is DesktopBrowserIntegration) {
                    val screenshotPath = videoRecorder.takeScreenshot(
                        browserIntegration.browser, 
                        "session_${recordingSessionId}_final"
                    )
                    if (screenshotPath != null) {
                        val screenshotRefFile = java.io.File(sessionDir, "final_screenshot_reference.txt")
                        screenshotRefFile.writeText("Final screenshot: $screenshotPath")
                    }
                }
                */
                
                // Open the session directory
                val desktop = java.awt.Desktop.getDesktop()
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(sessionDir)
                }
                
                browserAccessorLogger.info(LogCategory.FILE, "RPA Configuration and video recording saved", mapOf("sessionDir" to sessionDir.absolutePath))
            } else {
                // No recording, just open the rpa_config directory
                val desktop = java.awt.Desktop.getDesktop()
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(rpaConfigDir)
                }
                
                browserAccessorLogger.info(LogCategory.FILE, "RPA Configuration saved", mapOf("path" to file.absolutePath))
            }
            
            // Clear recording references
            currentRecordingPath = null
            recordingSessionId = null
            
        } catch (e: Exception) {
            browserAccessorLogger.warn(LogCategory.FILE, "Error saving RPA configuration", error = e)
        }
    }
}

