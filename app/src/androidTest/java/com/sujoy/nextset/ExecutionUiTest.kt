package com.sujoy.nextset

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sujoy.nextset.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: NextSetApplication

    @Before
    fun setup(): Unit = runBlocking {
        app = compose.activity.application as NextSetApplication
        app.execution.active(app.runtime.clock.now())?.let {
            app.runtime.command(it.sessionId, null, Command.END)
        }
    }

    private fun start(rest: Int): String = runBlocking {
        val template =
            WorkoutTemplate(
                name = "Execution UI",
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises =
                                listOf(Exercise(name = "Test press", rests = listOf(rest, 0)))
                        )
                    ),
            )
        app.workouts.save(template)
        app.runtime.start(template.id).also { app.requestedSession.value = it }
    }

    @After
    fun end() = runBlocking {
        app.execution.active(app.runtime.clock.now())?.let {
            app.runtime.command(it.sessionId, null, Command.END)
        }
        Unit
    }

    @Test
    fun shortTapsDoNothingAndOneSecondHoldCompletesOnlyOneSet() {
        val id = start(0)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("workout_surface").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("workout_surface").performTouchInput { click(center) }
        runBlocking {
            assertTrue(app.execution.observe(id).first()!!.session.completedSets.isEmpty())
        }
        // Keep the physical down event alive while advancing Compose's gesture timeout clock.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("workout_surface").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithTag("workout_surface").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) {
            runBlocking { app.execution.observe(id).first()!!.session.completedSets.size == 1 }
        }
        compose.onNodeWithText("SET 2 OF 2").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("SET 2 OF 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo last set").performClick()
        compose.onNodeWithText("SET 1 OF 2").assertIsDisplayed()
    }

    @Test
    fun expiredRestWaitsForAcknowledgmentAcrossRecreation() {
        val id = start(1)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("workout_surface").fetchSemanticsNodes().size == 1
        }
        val initial = runBlocking { app.execution.read(id)!! }
        runBlocking { app.runtime.command(id, initial.revision, Command.HOLD) }
        compose.waitUntil(10_000) {
            runBlocking { app.execution.read(id)?.phase == Phase.REST_DONE }
        }
        compose.onNodeWithText("00:00").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("REST COMPLETE").assertIsDisplayed()
        val state = runBlocking { app.execution.read(id)!! }
        assertEquals(Phase.REST_DONE, state.phase)
        runBlocking { app.runtime.command(id, state.revision, Command.HOLD) }
        compose.onNodeWithText("SET 2 OF 2").assertIsDisplayed()
    }

    private fun holdFor(millis: Long) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("workout_surface").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(millis)
        compose.onNodeWithTag("workout_surface").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun partialHoldsDoNotAccumulateAndFullWorkoutIsSavedToHistory() {
        val id = start(0)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("workout_surface").fetchSemanticsNodes().size == 1
        }
        repeat(2) { holdFor(500) }
        runBlocking {
            assertTrue(app.execution.observe(id).first()!!.session.completedSets.isEmpty())
        }
        holdFor(1_100)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("SET 2 OF 2").fetchSemanticsNodes().isNotEmpty()
        }
        holdFor(1_100)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Workout complete").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            val saved = app.execution.observe(id).first()!!.session
            assertEquals(SessionStatus.COMPLETED, saved.status)
            assertEquals(2, saved.completedSets.size)
        }
        compose.onNodeWithText("Back to Home").performClick()
        compose.onNodeWithText("History", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Completed · 2/2 sets").performScrollTo().assertIsDisplayed()
    }
}
