package ai.rever.boss.config

/**
 * Configuration for JxBrowser.
 * 
 * The license key can be provided through:
 * 1. Environment variable: JXBROWSER_LICENSE_KEY
 * 2. System property: jxbrowser.license.key
 * 3. local.properties file: jxbrowser.license.key=YOUR_KEY
 * 4. Fallback to development key (should be removed in production)
 */
object JxBrowserConfig {
    /**
     * JxBrowser license key loaded from secure sources.
     * In production, remove the default fallback value.
     */
    val licenseKey: String by lazy {
        ConfigLoader.getConfig("JXBROWSER_LICENSE_KEY")
            ?: ConfigLoader.getConfig("jxbrowser.license.key")
            ?: "58NM0Q9E2PKXH7EUHPUTJN668X5SS6QK0TN3QT1L8328W7PKKT7NWPR2R6ZG5CD4S9NS5OU0C7OWR7Z6UWTXDCIC1T28Z6EO38FS8NL8LS10WS7MSXK7DUZ7KDXTHVI12DP71SYEXFCFRKOMG9N6X7845LFEE5B9JB8U" // TODO: Remove in production
    }
    
    // Other JxBrowser configuration options
    val defaultUrl: String = ConfigLoader.getConfig("jxbrowser.default.url",
        "https://www.risalabs.ai") ?: "https://www.risalabs.ai"
    
    // Using OFF_SCREEN mode with custom context menu implementation
    val renderingMode = com.teamdev.jxbrowser.engine.RenderingMode.OFF_SCREEN

} 