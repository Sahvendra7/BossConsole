package ai.rever.boss.components.plugin.tab_types.fluck

import org.cef.browser.CefBrowser
import java.awt.*
import java.awt.event.*
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

class JCEFPanel(private val browser: CefBrowser) : JPanel() {
    
    private val browserComponent = browser.uiComponent
    private var renderTimer: Timer? = null
    
    init {
        println("JCEFPanel init - browser ID: ${browser.identifier}, component: ${browserComponent.javaClass.name}")
        
        // Use BorderLayout for proper sizing
        layout = BorderLayout()
        
        // Add the browser component
        add(browserComponent, BorderLayout.CENTER)
        
        // Ensure panel and browser are visible
        isVisible = true
        browserComponent.isVisible = true
        
        // Add component listener to handle resize
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                SwingUtilities.invokeLater {
                    browserComponent.size = size
                    browserComponent.validate()
                    browserComponent.repaint()
                }
            }
            
            override fun componentShown(e: ComponentEvent?) {
                println("JCEFPanel shown - size: $size")
                SwingUtilities.invokeLater {
                    browserComponent.isVisible = true
                    browserComponent.requestFocusInWindow()
                }
            }
        })
        
        // Add hierarchy listener to detect when added to window
        addHierarchyListener { e ->
            if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
                if (isShowing) {
                    println("JCEFPanel now showing in hierarchy")
                    println("Browser ID when showing: ${browser.identifier}")
                    SwingUtilities.invokeLater {
                        revalidate()
                        repaint()
                        browserComponent.requestFocusInWindow()
                        
                        // Try loading URL again if browser is ready
                        if (browser.identifier != -1 && browser.url.isNullOrEmpty()) {
                            println("Browser has valid ID, attempting to load default URL")
                            browser.loadURL("https://www.google.com")
                        }
                        
                        // Start periodic repainting to force rendering
                        if (renderTimer == null) {
                            renderTimer = Timer(100) { 
                                if (isShowing) {
                                    browserComponent.repaint()
                                }
                            }
                            renderTimer?.start()
                        }
                    }
                } else {
                    // Stop timer when not showing
                    renderTimer?.stop()
                    renderTimer = null
                }
            }
        }
        
        // Forward focus to browser
        isFocusable = true
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                browserComponent.requestFocusInWindow()
            }
        })
        
        // Forward mouse events to browser
        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                browserComponent.dispatchEvent(e)
            }
            
            override fun mouseReleased(e: MouseEvent) {
                browserComponent.dispatchEvent(e)
            }
            
            override fun mouseClicked(e: MouseEvent) {
                browserComponent.dispatchEvent(e)
            }
            
            override fun mouseMoved(e: MouseEvent) {
                browserComponent.dispatchEvent(e)
            }
            
            override fun mouseDragged(e: MouseEvent) {
                browserComponent.dispatchEvent(e)
            }
        }
        
        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
        
        // Forward key events
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                browserComponent.dispatchEvent(e)
            }
            
            override fun keyReleased(e: KeyEvent) {
                browserComponent.dispatchEvent(e)
            }
            
            override fun keyTyped(e: KeyEvent) {
                browserComponent.dispatchEvent(e)
            }
        })
    }
    
    override fun paint(g: Graphics) {
        super.paint(g)
        // Ensure browser component has correct bounds
        if (browserComponent.size != size) {
            browserComponent.setSize(width, height)
            browserComponent.validate()
        }
    }
    
    override fun doLayout() {
        super.doLayout()
        // Ensure browser fills the panel
        browserComponent.setBounds(0, 0, width, height)
    }
    
    fun forceRefresh() {
        SwingUtilities.invokeLater {
            invalidate()
            revalidate()
            repaint()
            browserComponent.invalidate()
            browserComponent.revalidate()
            browserComponent.repaint()
        }
    }
}