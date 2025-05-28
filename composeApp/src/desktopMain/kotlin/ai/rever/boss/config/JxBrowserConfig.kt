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
            ?: "OK6AEKNYF3K41B5WB4FEKK1C3H7UH3C6ZI1UL63J6E5VJTT3RXZ711M87XU8PLPO0EXR4PNTJWDLDF7FSVO658N5GSB7ZAMNXZ66L8QR115B9B1INDPS5KWSA4RYSUHG1QLPHFPL108ZS9IHW" // TODO: Remove in production
    }
    
    // Other JxBrowser configuration options
    val defaultUrl: String = ConfigLoader.getConfig("jxbrowser.default.url",
        "https://www.rilslabs.ai") ?: "https://www.risalabs.ai"
    val renderingMode = com.teamdev.jxbrowser.engine.RenderingMode.OFF_SCREEN

} 