package ai.rever.boss.updater

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The two things a user can do to an update from the banner, asserted against the rendered tree.
 *
 * The banner is the surface people actually look at - it is the topmost chrome in the window -
 * and until this it was the weaker of the two: the bottom bar's progress item opened a dialog
 * with Cancel and Install, while the banner showing the same transfer offered no way to stop it
 * and no way in. Both states are covered because they abandon different things:
 * `Downloading` cancels bytes arriving, `ReadyToInstall` deletes an artifact already on disk.
 *
 * A rendering test rather than a source one, unlike [UpdateHandleGuardTest]. What can regress
 * here is a button that stops being reachable - dropped from the layout, or placed where it does
 * not draw - and neither shows up in a signature. `assertIsDisplayed` is the part that matters:
 * existing in the semantics tree is not the same as being on screen.
 */
class UpdateBannerActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private val pressed = mutableListOf<String>()

    private fun setBanner(state: UpdateState) {
        rule.setContent {
            UpdateBanner(
                updateState = state,
                onInstallUpdate = { pressed += "install" },
                onCancelDownload = { pressed += "cancel" },
                onDiscardDownload = { pressed += "discard" },
            )
        }
    }

    @Test
    fun `a download in progress can be cancelled from the banner`() {
        setBanner(UpdateState.Downloading(progress = 0.4f))

        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()

        // cancelDownload, not discardDownload: nothing is on disk yet to throw away.
        assertEquals(listOf("cancel"), pressed)
    }

    @Test
    fun `a downloaded update offers both Install Now and Cancel`() {
        setBanner(UpdateState.ReadyToInstall(downloadPath = "/tmp/BOSS.dmg"))

        // Install Now was always here; Cancel is what the banner was missing, and without it
        // the only way to be rid of a staged update was the dialog behind the bottom bar.
        rule.onNodeWithText("Install Now").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()

        rule.onNodeWithText("Cancel").performClick()
        // discard, NOT cancel: this one deletes the staged artifact, and routing it to
        // cancelDownload would silently do nothing (that method refuses anything not Downloading).
        assertEquals(listOf("discard"), pressed)

        rule.onNodeWithText("Install Now").performClick()
        assertEquals(listOf("discard", "install"), pressed)
    }

    @Test
    fun `the downloading banner still shows its progress text`() {
        setBanner(UpdateState.Downloading(progress = 0.4f))

        // Adding a button to the row must not push the label out of the layout: the Row gives
        // the progress bar weight(1f), so a wider trailing action shrinks the bar, not the text.
        rule.onNodeWithText("Downloading... 40%").assertIsDisplayed()
    }
}
