package com.sujoy.nextset

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sujoy.nextset.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun clean() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as NextSetApplication
        app.workouts.templates.first().forEach { app.workouts.delete(it.id) }
    }

    @Test
    fun settingsReturnsToHomeThroughNavigationBar() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Your next set.").assertIsDisplayed()
    }

    @Test
    fun createAssignAndPreparePersistsAcrossRecreation() {
        val historyBefore = runBlocking {
            (compose.activity.application as NextSetApplication).sessions.sessions.first().size
        }
        compose.onNodeWithText("Create your first workout").performClick()
        compose.onNodeWithText("Workout name").performTextInput("Push A")
        compose.onNodeWithText("Add exercise").performClick()
        compose.onNodeWithText("Exercise name").performTextInput("Bench press")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Start workout").fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText("Week", useUnmergedTree = true).performClick()
        val app = compose.activity.application as NextSetApplication
        val today = java.time.LocalDate.now().dayOfWeek
        val dayName =
            today.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        compose.onNodeWithText("$dayName · Today").performScrollTo().performClick()
        compose.onNode(hasText("Push A") and hasClickAction()).performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Start workout").performClick()
        compose.onNode(hasText("Push A") and hasClickAction()).performClick()
        compose.onNodeWithText("Workout ready").assertIsDisplayed()
        compose.onAllNodesWithText("Bench press").onFirst().assertExists()
        runBlocking {
            assertEquals("Push A", app.workouts.templates.first().single().name)
            assertEquals(
                app.workouts.templates.first().single().id,
                app.workouts.schedule.first()[today],
            )
            assertEquals(historyBefore, app.sessions.sessions.first().size)
        }
    }

    @Test
    fun reminderIntentReturnsToHomeAndIsConsumedAcrossRotation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // ActivityScenario tracks filter-equivalent intents; retain MAIN/LAUNCHER identity.
        val incoming =
            android.content.Intent(compose.activity.intent).apply {
                flags =
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_home", true)
            }
        context.startActivity(incoming)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Your next set.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }
}
