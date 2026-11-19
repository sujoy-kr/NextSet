package com.sujoy.nextset

import android.app.NotificationManager
import android.media.RingtoneManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.sujoy.nextset.domain.*
import java.io.FileInputStream
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun sequenceCanBeBuiltAndSurvivesRecreation() {
        val app = compose.activity.application as NextSetApplication
        val previous = runBlocking { app.settings.settings.first().routine }
        val push =
            WorkoutTemplate(
                name = "Sequence Push",
                blocks = listOf(WorkoutBlock(exercises = listOf(Exercise(name = "Press")))),
            )
        runBlocking {
            app.workouts.save(push)
            app.settings.routine(RoutinePlan(PlanMode.SEQUENCE, emptyList(), LocalDate.now()))
        }
        try {
            compose.onNodeWithText("Week", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Add workout or rest day").performScrollTo().performClick()
            compose.onNode(hasText(push.name) and hasClickAction()).performClick()
            compose.onNodeWithText("Add workout or rest day").performScrollTo().performClick()
            compose.onNode(hasText("Rest day") and hasClickAction()).performClick()
            compose.waitUntil(5_000) {
                runBlocking { app.settings.settings.first().routine.entries.size } == 2
            }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Repeating sequence").assertIsDisplayed()
            assertEquals(
                listOf(push.id, null),
                runBlocking { app.settings.settings.first().routine.entries },
            )
            compose.onNodeWithText("History", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Weekly analysis").performScrollTo().assertIsDisplayed()
        } finally {
            runBlocking {
                app.settings.routine(previous)
                app.workouts.delete(push.id)
            }
        }
    }

    @Test
    fun assignmentAndSequencePickersSurviveRecreation() {
        val app = compose.activity.application as NextSetApplication
        val previous = runBlocking { app.settings.settings.first().routine }
        try {
            runBlocking { app.settings.routine(RoutinePlan()) }
            compose.onNodeWithText("Week", useUnmergedTree = true).performClick()
            val today =
                LocalDate.now()
                    .dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("$today · Today"))
            compose.onNodeWithText("$today · Today").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
            compose.onNodeWithText("Repeating sequence").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose
                    .onAllNodesWithText("Add workout or rest day")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Add workout or rest day").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Day 1").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            runBlocking {
                app.settings.routine(RoutinePlan(PlanMode.SEQUENCE, listOf(null), LocalDate.now()))
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Change start date").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Change start date").performScrollTo().performClick()
            val picker =
                androidx.test.espresso.Espresso.onView(
                    androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(
                        android.widget.DatePicker::class.java
                    )
                )
            picker.perform(
                object : androidx.test.espresso.ViewAction {
                    override fun getConstraints(): org.hamcrest.Matcher<android.view.View> =
                        androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(
                            android.widget.DatePicker::class.java
                        )

                    override fun getDescription() = "Choose an unsaved start date"

                    override fun perform(
                        controller: androidx.test.espresso.UiController,
                        view: android.view.View,
                    ) {
                        (view as android.widget.DatePicker).updateDate(2030, 1, 8)
                    }
                }
            )
            compose.activityRule.scenario.recreate()
            picker.check { view, error ->
                if (error != null) throw error
                val restored = view as android.widget.DatePicker
                assertEquals(2030, restored.year)
                assertEquals(1, restored.month)
                assertEquals(8, restored.dayOfMonth)
            }
            androidx.test.espresso.Espresso.onView(
                    androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button2)
                )
                .check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(
                        androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
                    )
                )
                .perform(androidx.test.espresso.action.ViewActions.click())
        } finally {
            runBlocking { app.settings.routine(previous) }
        }
    }

    @Test
    fun unavailableSoundFallsBackWithoutLosingTheAlert() = runBlocking {
        val app = compose.activity.application as NextSetApplication
        val previous = app.settings.settings.first()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation
                .executeShellCommand(
                    "pm grant com.sujoy.nextset android.permission.POST_NOTIFICATIONS"
                )
                .use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                }
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            app.settings.alertMode(com.sujoy.nextset.data.AlertMode.SOUND)
            app.settings.sound("content://media/internal/audio/media/2147483647")
            app.notifications.rest("missing-tone", 1)
            withTimeout(5_000) {
                while (manager.activeNotifications.none { it.tag == "rest:missing-tone:1" }) delay(
                    50
                )
            }
            if (Build.VERSION.SDK_INT >= 26) {
                val posted = manager.activeNotifications.single { it.tag == "rest:missing-tone:1" }
                assertEquals(
                    "android.resource://${app.packageName}/raw/rest_complete",
                    manager.getNotificationChannel(posted.notification.channelId).sound.toString(),
                )
            }
        } finally {
            app.notifications.clearRest("missing-tone", 1)
            app.settings.sound(previous.soundUri)
            app.settings.alertMode(previous.alertMode)
        }
    }

    @Test
    fun soundPreferencesPersistAndToneChangesCreateNewChannels() = runBlocking {
        val app = compose.activity.application as NextSetApplication
        val previous = app.settings.settings.first()
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            app.settings.sound(ringtone.toString())
            assertEquals(ringtone.toString(), app.settings.settings.first().soundUri)
            app.settings.alertMode(com.sujoy.nextset.data.AlertMode.SOUND)
            if (Build.VERSION.SDK_INT >= 33) {
                InstrumentationRegistry.getInstrumentation()
                    .uiAutomation
                    .executeShellCommand(
                        "pm grant com.sujoy.nextset android.permission.POST_NOTIFICATIONS"
                    )
                    .use { descriptor ->
                        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
                    }
            }
            assertTrue(app.notifications.canNotify())
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = app.getSystemService(NotificationManager::class.java)
                app.notifications.rest("tone-test", 1)
                withTimeout(5_000) {
                    while (manager.activeNotifications.none { it.tag == "rest:tone-test:1" }) delay(
                        50
                    )
                }
                val first =
                    manager.activeNotifications
                        .single { it.tag == "rest:tone-test:1" }
                        .notification
                        .channelId
                assertEquals(ringtone, manager.getNotificationChannel(first).sound)
                app.settings.sound(notification.toString())
                app.notifications.rest("tone-test", 2)
                withTimeout(5_000) {
                    while (manager.activeNotifications.none { it.tag == "rest:tone-test:2" }) delay(
                        50
                    )
                }
                val second =
                    manager.activeNotifications
                        .single { it.tag == "rest:tone-test:2" }
                        .notification
                        .channelId
                assertNotEquals(first, second)
                assertEquals(notification, manager.getNotificationChannel(second).sound)
                app.notifications.clearRest("tone-test", 1)
                app.notifications.clearRest("tone-test", 2)
            }
            compose.activityRule.scenario.recreate()
        } finally {
            app.settings.sound(previous.soundUri)
            app.settings.alertMode(previous.alertMode)
        }
    }
}
