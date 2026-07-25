package ai.rever.boss.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards deep-link dispatch: hosts match exactly (so a longer host is never
 * swallowed by a shorter one) and every host that acts on a window is routed
 * through the single window-resolution point.
 */
class DeepLinkRoutingTest {
    @Test
    fun `each known host routes to its own handler`() {
        assertEquals(DeepLinkHost.URL, routedDeepLinkHost("boss://url?url=https%3A%2F%2Fexample.com"))
        assertEquals(DeepLinkHost.WORKSPACE, routedDeepLinkHost("boss://workspace?path=/tmp/ws.json"))
        assertEquals(DeepLinkHost.FILE, routedDeepLinkHost("boss://file?path=/tmp/a.kt"))
        assertEquals(DeepLinkHost.TERMINAL, routedDeepLinkHost("boss://terminal"))
        assertEquals(DeepLinkHost.FOLDER, routedDeepLinkHost("boss://folder?path=/tmp/project"))
        assertEquals(DeepLinkHost.PLUGIN, routedDeepLinkHost("boss://plugin?id=bookmarks"))
        assertEquals(DeepLinkHost.SPLIT, routedDeepLinkHost("boss://split?orientation=horizontal"))
    }

    @Test
    fun `a longer host is never swallowed by a shorter one`() {
        // Prefix dispatch used to route these into plugin, file, url, terminal,
        // folder, workspace and split respectively, mis-parsing them instead of
        // leaving them to the default flow.
        assertNull(routedDeepLinkHost("boss://plugins"))
        assertNull(routedDeepLinkHost("boss://plugins?id=bookmarks"))
        assertNull(routedDeepLinkHost("boss://filesystem/list?path=/tmp"))
        assertNull(routedDeepLinkHost("boss://urlencode?url=x"))
        assertNull(routedDeepLinkHost("boss://terminals"))
        assertNull(routedDeepLinkHost("boss://folders?path=/tmp"))
        assertNull(routedDeepLinkHost("boss://workspaces"))
        assertNull(routedDeepLinkHost("boss://splitscreen"))
    }

    @Test
    fun `unrouted links fall through to the default flow`() {
        assertNull(routedDeepLinkHost("boss://auth/verify#access_token=abc"))
        assertNull(routedDeepLinkHost("boss://"))
        assertNull(routedDeepLinkHost("boss://?path=/tmp"))
        assertNull(routedDeepLinkHost("https://example.com/plugin"))
        assertNull(routedDeepLinkHost("bossx://plugin?id=bookmarks"))
        assertNull(routedDeepLinkHost(""))
    }

    @Test
    fun `host parsing stops at the first delimiter and ignores case`() {
        assertEquals("plugin", deepLinkHostOf("boss://plugin?id=bookmarks"))
        assertEquals("plugin", deepLinkHostOf("boss://plugin/open?id=bookmarks"))
        assertEquals("plugin", deepLinkHostOf("boss://plugin#fragment"))
        assertEquals("plugin", deepLinkHostOf("boss://plugin"))
        assertEquals("plugin", deepLinkHostOf("BOSS://Plugin?id=bookmarks"))
        assertEquals(DeepLinkHost.PLUGIN, routedDeepLinkHost("BOSS://PLUGIN?id=bookmarks"))
        assertNull(deepLinkHostOf("boss:/plugin"))
    }

    @Test
    fun `window targeting hosts are exactly the ones a caller can trigger without OS focus`() {
        // boss://plugin and boss://folder used to read focusedWindowFlow directly
        // and drop the link for MCP/CLI callers, while boss://split resolved a
        // usable window. All three now share one resolution point, which this
        // classification selects.
        assertTrue(DeepLinkHost.FOLDER.requiresTargetWindow)
        assertTrue(DeepLinkHost.PLUGIN.requiresTargetWindow)
        assertTrue(DeepLinkHost.SPLIT.requiresTargetWindow)

        assertFalse(DeepLinkHost.URL.requiresTargetWindow)
        assertFalse(DeepLinkHost.WORKSPACE.requiresTargetWindow)
        assertFalse(DeepLinkHost.FILE.requiresTargetWindow)
        assertFalse(DeepLinkHost.TERMINAL.requiresTargetWindow)
    }

    @Test
    fun `host names are unique and lower case so exact matching stays total`() {
        val hosts = DeepLinkHost.entries.map { it.host }
        assertEquals(hosts.toSet().size, hosts.size)
        assertEquals(hosts, hosts.map { it.lowercase() })
        hosts.forEach { host ->
            assertEquals(DeepLinkHost.entries.first { it.host == host }, routedDeepLinkHost("boss://$host"))
        }
    }
}
