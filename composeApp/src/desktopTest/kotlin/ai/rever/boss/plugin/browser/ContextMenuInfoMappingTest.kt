package ai.rever.boss.plugin.browser

import com.teamdev.jxbrowser.media.MediaType
import com.teamdev.jxbrowser.menu.ContextMenuContentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Truth table for [ContextMenuTarget.toContextMenuInfo] — the mapping from what Chromium reports about a
 * right-click onto the plugin-facing info. Pure, so none of this needs a live browser.
 *
 * Page identity (url, title) is the caller's to apply and is not asserted here.
 */
class ContextMenuInfoMappingTest {
    private fun info(
        contentTypes: List<ContextMenuContentType> = emptyList(),
        mediaType: MediaType = MediaType.NONE,
        srcUrl: String = "",
        linkUrl: String = "",
        selectedText: String = "",
        isMainFrame: Boolean = true,
    ) = ContextMenuTarget(
        contentTypes = contentTypes,
        mediaType = mediaType,
        srcUrl = srcUrl,
        linkUrl = linkUrl,
        selectedText = selectedText,
        isMainFrame = isMainFrame,
    ).toContextMenuInfo(pageUrl = "https://example.com/", pageTitle = "Example")

    @Test
    fun `blank strings become null rather than empty`() {
        val result = info(linkUrl = "", selectedText = "   ")

        assertNull(result.linkUrl)
        assertNull(result.selectedText)
    }

    @Test
    fun `a link target carries its url`() {
        assertEquals("https://example.com/next", info(linkUrl = "https://example.com/next").linkUrl)
    }

    @Test
    fun `an image is reported from either the media type or the content type`() {
        val byMediaType = info(mediaType = MediaType.IMAGE, srcUrl = "https://example.com/cat.png")
        val byContentType =
            info(
                contentTypes = listOf(ContextMenuContentType.MEDIA_IMAGE),
                srcUrl = "https://example.com/cat.png",
            )

        assertTrue(byMediaType.hasImage)
        assertEquals("https://example.com/cat.png", byMediaType.imageUrl)
        assertTrue(byContentType.hasImage)
        assertEquals("https://example.com/cat.png", byContentType.imageUrl)
    }

    @Test
    fun `an image with no source url is not reported as an image`() {
        // Chromium reports MEDIA_IMAGE for canvases and CSS backgrounds, which have no
        // address — every image action needs one, so hasImage must not promise otherwise.
        val result = info(contentTypes = listOf(ContextMenuContentType.MEDIA_IMAGE), srcUrl = "")

        assertFalse(result.hasImage)
        assertNull(result.imageUrl)
    }

    @Test
    fun `a video source url is not mistaken for an image url`() {
        val result = info(mediaType = MediaType.VIDEO, srcUrl = "https://example.com/clip.mp4")

        assertTrue(result.hasVideo)
        assertFalse(result.hasImage)
        assertNull(result.imageUrl)
    }

    @Test
    fun `an editable field in the main frame is editable`() {
        assertTrue(info(contentTypes = listOf(ContextMenuContentType.EDITABLE)).isEditable)
    }

    @Test
    fun `an editable field inside an iframe is not offered edit actions`() {
        // cut/copy/paste/selectAll and fillCredentials all act on browser.mainFrame(), so
        // offering them for a subframe field would act on the wrong frame — and in the
        // credential case could fill a password into an unrelated main-frame input.
        val result =
            info(
                contentTypes = listOf(ContextMenuContentType.EDITABLE),
                isMainFrame = false,
            )

        assertFalse(result.isEditable)
    }

    @Test
    fun `a non-editable subframe click still reports its link and selection`() {
        val result =
            info(
                linkUrl = "https://example.com/in-frame",
                selectedText = "picked",
                isMainFrame = false,
            )

        assertEquals("https://example.com/in-frame", result.linkUrl)
        assertEquals("picked", result.selectedText)
    }

    @Test
    fun `a plain page click reports no target of any kind`() {
        val result = info()

        assertNull(result.linkUrl)
        assertNull(result.selectedText)
        assertFalse(result.isEditable)
        assertFalse(result.hasImage)
        assertFalse(result.hasVideo)
    }

    @Test
    fun `a linked image reports both`() {
        val result =
            info(
                contentTypes = listOf(ContextMenuContentType.LINK, ContextMenuContentType.MEDIA_IMAGE),
                mediaType = MediaType.IMAGE,
                srcUrl = "https://example.com/cat.png",
                linkUrl = "https://example.com/next",
            )

        assertEquals("https://example.com/next", result.linkUrl)
        assertTrue(result.hasImage)
    }
}
