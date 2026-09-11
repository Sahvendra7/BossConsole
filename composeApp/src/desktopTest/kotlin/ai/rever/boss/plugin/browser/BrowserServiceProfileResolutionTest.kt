package ai.rever.boss.plugin.browser

import ai.rever.boss.components.plugin.getBrowserServiceInstance
import ai.rever.boss.window.WindowManager
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals

class BrowserServiceProfileResolutionTest {
    @Test
    fun `browser service correctly resolves window profile id`() = runBlocking {
        val w1 = WindowManager.createNewWindow(browserProfileId = "work-profile")
        val w2 = WindowManager.createNewWindow(browserProfileId = "personal-profile")
        val w3 = WindowManager.createNewWindow() // default
        
        try {
            val service1 = getBrowserServiceInstance(w1.id)
            val service2 = getBrowserServiceInstance(w2.id)
            val service3 = getBrowserServiceInstance(w3.id)
            
            println("Service 1: $service1")
            assertNotNull(service1, "Service 1 should not be null")
            assertNotNull(service2, "Service 2 should not be null")
            assertNotNull(service3, "Service 3 should not be null")
            
            assertEquals("work-profile", w1.browserProfileId)
            assertEquals("personal-profile", w2.browserProfileId)
            assertEquals("browser-profile", w3.browserProfileId) // default
            
        } finally {
            WindowManager.closeWindow(w1.id)
            WindowManager.closeWindow(w2.id)
            WindowManager.closeWindow(w3.id)
        }
    }
}
