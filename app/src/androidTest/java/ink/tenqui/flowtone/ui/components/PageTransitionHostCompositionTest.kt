package ink.tenqui.flowtone.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 使用受控时钟验证生命周期；右滑用例仅在获准运行设备测试时执行。 */
@RunWith(AndroidJUnit4::class)
class PageTransitionHostCompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemBackReversesIncomingInsteadOfCallingTheRetainedOutgoingHandler() {
        val scenario = install()
        compose.runOnIdle { scenario.installOldPageBackHandler.value = true }
        start(scenario, "B")
        compose.mainClock.advanceTimeBy(160)
        var remaining = 0
        compose.runOnIdle {
            assertEquals("A", scenario.slots.outgoing)
            assertEquals("B", scenario.slots.incoming)
            remaining = pageTransitionDurationMillis(scenario.slots.progress, 0f)
            scenario.rootBackDispatcher.onBackPressed()
            assertEquals("A", scenario.target.value)
            assertEquals(0, scenario.oldPageBackCalls)
        }
        compose.mainClock.advanceTimeBy(remaining + FrameAllowanceMillis, ignoreFrameDuration = true)
        compose.runOnIdle {
            assertEquals("A", scenario.slots.current)
            assertEquals(1, scenario.pageBirths["A"])
        }
    }

    @Test fun fullScreenRightSwipeCanReverseAnIncomingPage() {
        val scenario = install()
        start(scenario, "B")
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertEquals("B", scenario.slots.incoming) }
        compose.onNodeWithTag("page-host").performTouchInput { swipeRight(durationMillis = 64) }
        var remaining = 0
        compose.runOnIdle {
            assertEquals("A", scenario.target.value)
            remaining = pageTransitionDurationMillis(scenario.slots.progress, 0f)
        }
        compose.mainClock.advanceTimeBy(remaining + FrameAllowanceMillis, ignoreFrameDuration = true)
        compose.runOnIdle {
            assertEquals("A", scenario.slots.current)
            assertEquals(1, scenario.pageBirths["A"])
        }
    }

    @Test fun reversingWithLiveLazyChildrenUsesOnlyTheRemainingDuration() {
        val scenario = install()
        start(scenario, "B")
        compose.mainClock.advanceTimeBy(160)
        var remaining = 0
        var childBirths = 0
        compose.runOnIdle {
            assertEquals("A", scenario.slots.outgoing)
            assertNull(scenario.pageDisposals["A"])
            assertNull(scenario.childDisposals["A"])
            childBirths = scenario.childBirths["A"] ?: 0
            remaining = pageTransitionDurationMillis(scenario.slots.progress, 0f)
            scenario.target.value = "A"
        }
        compose.mainClock.advanceTimeBy(remaining + FrameAllowanceMillis, ignoreFrameDuration = true)
        compose.runOnIdle {
            assertEquals("A", scenario.slots.current)
            assertEquals(1, scenario.pageBirths["A"])
            assertEquals(childBirths, scenario.childBirths["A"] ?: 0)
            assertNull(scenario.childDisposals["A"])
            assertEquals(1f, scenario.retainedProgress["A"]!!, 0f)
        }
    }

    @Test fun reversingAfterLazyChildrenDisposeDoesNotRecreateAOrReplayEntry() {
        val scenario = install()
        listOf(0.1f, 0.5f, 0.9f, 0.99f).forEach { fraction ->
            start(scenario, "B")
            compose.runOnIdle { scenario.showAChildren.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(
                (PageMotion.DurationMillis * fraction).toLong() - 16L,
                ignoreFrameDuration = true
            )
            var remaining = 0
            var childBirths = 0
            compose.runOnIdle {
                assertTrue(scenario.slots.progress > 0f && scenario.slots.progress < 1f)
                assertEquals(1, scenario.pageBirths["A"])
                assertTrue((scenario.childDisposals["A"] ?: 0) > 0)
                childBirths = scenario.childBirths["A"] ?: 0
                remaining = pageTransitionDurationMillis(scenario.slots.progress, 0f)
                scenario.target.value = "A"
            }
            // 最多三帧用于重组、启动新 effect 和提交，不追加元素动画时长。
            compose.mainClock.advanceTimeBy(remaining + FrameAllowanceMillis, ignoreFrameDuration = true)
            compose.runOnIdle {
                assertEquals("A", scenario.slots.current)
                assertNull(scenario.slots.outgoing)
                assertNull(scenario.slots.incoming)
                assertEquals(1, scenario.pageBirths["A"])
                assertEquals(childBirths, scenario.childBirths["A"] ?: 0)
                assertEquals(1f, scenario.retainedProgress["A"]!!, 0f)
                assertEquals(PageTransitionPhase.Current, scenario.nestedScopes["A"]!!.phase)
                assertEquals(1f, scenario.nestedScopes["A"]!!.progress, 0f)
            }
            // 下一次循环显式恢复数据；转场本身不能重新创建已合法移除的子内容。
            compose.runOnIdle { scenario.showAChildren.value = true }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    @Test fun completedNavigationDisposesAAndBackCreatesANewComposition() {
        val scenario = install()
        start(scenario, "B")
        finish(scenario, "B")
        compose.runOnIdle {
            assertEquals(1, scenario.pageDisposals["A"])
            assertEquals(1, scenario.pageBirths["A"])
        }
        start(scenario, "A")
        compose.runOnIdle {
            assertEquals("B", scenario.slots.outgoing)
            assertEquals("A", scenario.slots.incoming)
            assertEquals(2, scenario.pageBirths["A"])
        }
        finish(scenario, "A")
    }

    @Test fun rapidReversalAndThirdPageReplacementCannotCommitAnObsoletePair() {
        val scenario = install()
        start(scenario, "B")
        compose.mainClock.advanceTimeBy(160)
        start(scenario, "A")
        compose.mainClock.advanceTimeBy(32)
        start(scenario, "B")
        compose.mainClock.advanceTimeBy(32)
        start(scenario, "C")
        compose.runOnIdle {
            assertEquals("A", scenario.slots.outgoing)
            assertEquals("C", scenario.slots.incoming)
            assertEquals(1, scenario.pageBirths["A"])
            assertEquals(1, scenario.pageDisposals["B"])
            val parent = scenario.pageScopes["A"]!!
            val nested = scenario.nestedScopes["A"]!!
            assertEquals(parent.phase, nested.phase)
            assertEquals(parent.progress, nested.progress, 0f)
        }
        finish(scenario, "C")
        compose.mainClock.advanceTimeBy(PageMotion.DurationMillis.toLong())
        compose.runOnIdle {
            assertEquals("C", scenario.slots.current)
            assertEquals(1, scenario.pageBirths["C"])
            assertEquals(1, scenario.pageDisposals["A"])
            assertFalse(scenario.pageScopes.containsKey("A"))
            assertFalse(scenario.pageScopes.containsKey("B"))
        }
    }

    private fun install(): Scenario {
        compose.mainClock.autoAdvance = false
        return Scenario().also { scenario ->
            compose.setContent { scenario.Content() }
        }
    }

    private fun start(scenario: Scenario, target: String) {
        compose.runOnIdle { scenario.target.value = target }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun finish(scenario: Scenario, target: String) {
        compose.mainClock.advanceTimeBy(
            PageMotion.DurationMillis + FrameAllowanceMillis,
            ignoreFrameDuration = true
        )
        compose.runOnIdle {
            assertEquals(target, scenario.slots.current)
            assertNull(scenario.slots.outgoing)
            assertNull(scenario.slots.incoming)
        }
    }

    private class Scenario {
        val target = mutableStateOf("A")
        val showAChildren = mutableStateOf(true)
        val installOldPageBackHandler = mutableStateOf(false)
        var oldPageBackCalls = 0
        lateinit var rootBackDispatcher: OnBackPressedDispatcher
        var slots = PageTransitionSlots(current = "A")
        val pageBirths = mutableMapOf<String, Int>()
        val pageDisposals = mutableMapOf<String, Int>()
        val childBirths = mutableMapOf<String, Int>()
        val childDisposals = mutableMapOf<String, Int>()
        val pageScopes = mutableMapOf<String, PageTransitionPresentation>()
        val nestedScopes = mutableMapOf<String, PageTransitionPresentation>()
        val retainedProgress = mutableMapOf<String, Float>()

        @Composable fun Content() {
            val parentDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            SideEffect { rootBackDispatcher = parentDispatcher }
            BackHandler { target.value = "A" }
            PageTransitionHost(
                targetState = target.value,
                modifier = Modifier.fillMaxSize().testTag("page-host")
                    .rightSwipeBackGesture { target.value = "A" },
                onSlotsChanged = { slots = it }
            ) { page ->
                val pageScope = this
                if (page == "A" && installOldPageBackHandler.value) {
                    BackHandler { oldPageBackCalls += 1 }
                }
                DisposableEffect(Unit) {
                    pageBirths[page] = (pageBirths[page] ?: 0) + 1
                    onDispose {
                        pageDisposals[page] = (pageDisposals[page] ?: 0) + 1
                        pageScopes.remove(page)
                        nestedScopes.remove(page)
                    }
                }
                SideEffect {
                    pageScopes[page] = pageScope.presentation()
                    retainedProgress[page] = pageScope.retainedContentProgress
                }
                Box(Modifier.fillMaxSize()) {
                    if (page != "A" || showAChildren.value) {
                        if (page == "B") {
                            LazyVerticalGrid(columns = GridCells.Fixed(2)) {
                                items(count = 30, key = { "$page:$it" }) { index ->
                                    Child(page, pageScope, index)
                                }
                            }
                        } else {
                            LazyColumn {
                                items(count = 30, key = { "$page:$it" }) { index ->
                                    Child(page, pageScope, index)
                                }
                            }
                        }
                    }
                    PageTransitionHost(targetState = "inner", parentScope = pageScope) {
                        val nestedScope = this
                        SideEffect { nestedScopes[page] = nestedScope.presentation() }
                        Box(Modifier.then(nestedScope.elementModifier(0, 1)))
                    }
                }
            }
        }

        @Composable private fun Child(page: String, scope: PageTransitionScope, index: Int) {
            if (index == 0) {
                DisposableEffect(Unit) {
                    childBirths[page] = (childBirths[page] ?: 0) + 1
                    onDispose { childDisposals[page] = (childDisposals[page] ?: 0) + 1 }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(40.dp)
                    .then(scope.elementModifier(index, PageMotion.DefaultOrderCount))
            )
        }
    }

    private companion object {
        const val FrameAllowanceMillis = 48L
    }
}
