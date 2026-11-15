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
class TemplateEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun clean() = runBlocking {
        val app = compose.activity.application as NextSetApplication
        app.execution.active(app.runtime.clock.now())?.let {
            app.runtime.command(it.sessionId, null, Command.END)
        }
        app.workouts.templates.first().forEach { app.workouts.delete(it.id) }
    }

    @Test
    fun createSupersetReorderMovementsAndOverrideIndividualFinalRest() {
        compose.waitUntil(5_000) {
            compose
                .onAllNodesWithText("Create your first workout")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Create your first workout").performClick()
        compose.onNodeWithText("Workout name").performTextInput("Superset audit")
        compose.onNodeWithText("Add superset").performScrollTo().performClick()
        compose.onNodeWithText("Add movement").performScrollTo()
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Name every exercise (up to 100 characters).").assertIsDisplayed()
        compose
            .onAllNodesWithText("Exercise name")
            .onFirst()
            .performScrollTo()
            .performTextInput("Raise")
        compose
            .onAllNodesWithText("Exercise name")
            .onLast()
            .performScrollTo()
            .performTextInput("Extension")
        compose.onNodeWithContentDescription("More sets for Raise").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Move movement 2 up").performScrollTo().performClick()
        // Raise is now the second movement, with four sets versus Extension's three.
        compose
            .onAllNodesWithText("Customize rest for each set")
            .onLast()
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Set 4 · Default").performScrollTo().performClick()
        compose.onNodeWithText("Minutes").performTextReplacement("0")
        compose.onNodeWithText("Seconds").performTextReplacement("0")
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Start workout").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            val app = compose.activity.application as NextSetApplication
            val saved = app.workouts.templates.first().single()
            val group = saved.blocks.single()
            assertTrue(group.superset)
            assertEquals(listOf("Extension", "Raise"), group.exercises.map { it.name })
            assertEquals(listOf(3, 4), group.exercises.map { it.sets })
            assertEquals(0, group.exercises.last().rests.last())
            assertTrue(saved.sequence().last() is WorkoutStep.Set)
        }
    }
}
