package ai.rever.boss.crash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/**
 * Layout guarantees for [CrashReportDialog] at the crash window's *minimum* size.
 *
 * The dialog is hosted in a fixed JFrame (`CrashHandler.showCrashDialogWindow`) whose minimum
 * size is 450x500. A crash report is a lot of content for that box, and expanding "Technical
 * Details" adds ~250dp more — so a body laid out without a scroll region pushes "Report Issue"
 * and "Don't Send" below the bottom edge, where they cannot be clicked and the crash cannot be
 * reported or dismissed. These tests pin the two properties that prevent that: the footer is
 * pinned, and the body scrolls.
 *
 * The window size is reproduced with `clipToBounds()`, because that is what makes off-window
 * content actually undisplayed — without clipping, overflow is merely painted outside the frame
 * and `assertIsDisplayed` would pass on a broken layout.
 */
class CrashReportDialogLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    /** The JFrame minimum from `CrashHandler.showCrashDialogWindow` — the tightest real case. */
    private val windowWidth = 450.dp
    private val windowHeight = 500.dp

    /** Deep enough that the stack trace pane reaches its 200dp cap. */
    private fun deepCrashReport(): CrashReport {
        val frames =
            (1..60).joinToString("\n") { i ->
                "\tat ai.rever.boss.example.Frame$i.doWork(Frame$i.kt:$i)"
            }
        return CrashReport(
            signature = "test-signature",
            exceptionType = "java.lang.IllegalStateException",
            exceptionMessage = "Something went badly wrong while doing the thing",
            stackTrace = "java.lang.IllegalStateException: Something went badly wrong\n$frames",
            systemInfo =
                SystemInfo(
                    osName = "Mac OS X",
                    osVersion = "15.0",
                    osArch = "aarch64",
                    javaVersion = "21",
                    javaVendor = "Test",
                    heapUsedMB = 256,
                    heapMaxMB = 4096,
                    nonHeapUsedMB = 128,
                    availableProcessors = 8,
                ),
            appInfo = AppInfo(version = "9.9.9", platform = "macos", isDebug = true),
            timestamp = 0L,
        )
    }

    @Composable
    private fun Dialog() {
        CrashReportDialog(
            crashReport = deepCrashReport(),
            onDismiss = {},
            onSubmit = { _, _ -> },
            onCleanAndRestart = {},
        )
    }

    private fun setDialogAtMinimumWindowSize() {
        rule.setContent {
            Box(modifier = Modifier.size(windowWidth, windowHeight).clipToBounds()) {
                Dialog()
            }
        }
    }

    @Test
    fun actionButtonsStayVisibleWhenTechnicalDetailsAreExpanded() {
        setDialogAtMinimumWindowSize()

        rule.onNodeWithText("Report Issue").assertIsDisplayed()

        rule.onNodeWithText("Technical Details").performClick()
        rule.waitForIdle()

        // The expanded stack trace must not push the footer off the window.
        rule.onNodeWithText("Report Issue").assertIsDisplayed()
        rule.onNodeWithText("Don't Send").assertIsDisplayed()
        rule.onNodeWithText("Clean Data & Restart").assertIsDisplayed()
    }

    @Test
    fun bodyContentBelowTheFoldIsReachableByScrolling() {
        setDialogAtMinimumWindowSize()

        rule.onNodeWithText("Technical Details").performClick()
        rule.waitForIdle()

        // Fails without a scrollable ancestor: the checkbox sits below the fold once the
        // details are expanded, so it is only reachable if the body actually scrolls.
        rule.onNodeWithText("Include recent activity logs").performScrollTo().assertIsDisplayed()

        // Scrolling the body must not have carried the footer away with it.
        rule.onNodeWithText("Report Issue").assertIsDisplayed()
    }
}
