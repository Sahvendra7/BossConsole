package ai.rever.boss.plugin.sandbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the WIRING, which the registry's own tests cannot see.
 *
 * `PluginUiMountRegistryTest` proves the registry counts and waits correctly. That is worth
 * nothing if the boundary never calls it - the registry would stay empty, every wait would return
 * instantly, and the whole fix would be inert while its unit tests stayed green. This mounts a real
 * boundary and watches the count.
 *
 * It also pins the ordering the fix rests on: **the count must drop AFTER the plugin content's own
 * onDispose has run.** If it dropped first, the unload would be told "disposed" while the very
 * lambdas it is waiting for were still to execute - the original bug, restored, with nothing else
 * in the suite noticing. Compose dispatches remember observers inner-first, so the boundary's
 * effect (outermost) runs last; this test fails if that ever inverts.
 */
@OptIn(ExperimentalTestApi::class)
class PluginUiMountWiringTest {
    @BeforeTest
    fun setUp() = PluginUiMountRegistry.reset()

    @AfterTest
    fun tearDown() = PluginUiMountRegistry.reset()

    @Test
    fun `a mounted boundary is counted, and stops being counted when it goes`() =
        runComposeUiTest {
            var mounted by mutableStateOf(true)
            setContent {
                Box(Modifier) {
                    if (mounted) {
                        BoundaryUnderTest { }
                    }
                }
            }

            waitForIdle()
            assertTrue(PluginUiMountRegistry.isMounted(PLUGIN_ID), "the boundary did not register")

            mounted = false
            waitForIdle()
            assertFalse(PluginUiMountRegistry.isMounted(PLUGIN_ID), "the boundary did not unregister")
        }

    @Test
    fun `two boundaries for one plugin are counted separately`() =
        runComposeUiTest {
            // A tab and a sidebar panel, or a tab in each of two windows. The first to go must not
            // report the plugin as gone - that is exactly when the loader would close under the
            // second.
            var second by mutableStateOf(true)
            setContent {
                Box(Modifier) {
                    BoundaryUnderTest { }
                    if (second) {
                        BoundaryUnderTest { }
                    }
                }
            }

            waitForIdle()
            assertEquals(2, PluginUiMountRegistry.mounted.value[PLUGIN_ID])

            second = false
            waitForIdle()
            assertTrue(PluginUiMountRegistry.isMounted(PLUGIN_ID), "one surface is still up")
            assertEquals(1, PluginUiMountRegistry.mounted.value[PLUGIN_ID])
        }

    @Test
    fun `the count drops only after the plugin's own onDispose has run`() =
        runComposeUiTest {
            // The ordering the whole fix depends on. The content's onDispose stands in for a
            // plugin's - the lambda that, in the real bug, could no longer resolve its own class.
            var mounted by mutableStateOf(true)
            var countWhenContentDisposed: Int? = null

            setContent {
                Box(Modifier) {
                    if (mounted) {
                        BoundaryUnderTest {
                            DisposableEffect(Unit) {
                                onDispose {
                                    countWhenContentDisposed = PluginUiMountRegistry.mounted.value[PLUGIN_ID]
                                }
                            }
                        }
                    }
                }
            }

            waitForIdle()
            mounted = false
            waitForIdle()

            assertEquals(
                1,
                countWhenContentDisposed,
                "the registry said 'disposed' while the plugin's own onDispose was still to run - " +
                    "an unload waiting on it would close the classloader too early",
            )
            assertFalse(PluginUiMountRegistry.isMounted(PLUGIN_ID), "and it must be zero afterwards")
        }
}

/**
 * The registering half of `PluginErrorBoundary`, mounted on its own.
 *
 * The real boundary needs a `PluginSandbox` and a crash interceptor; this is the DisposableEffect
 * under test and nothing else, in the same position - outermost, around the content.
 */
@Composable
private fun BoundaryUnderTest(content: @Composable () -> Unit) {
    DisposableEffect(PLUGIN_ID) {
        PluginUiMountRegistry.onMounted(PLUGIN_ID)
        onDispose { PluginUiMountRegistry.onDisposed(PLUGIN_ID) }
    }
    content()
}

private const val PLUGIN_ID = "ai.rever.boss.plugin.dynamic.terminaltab"
