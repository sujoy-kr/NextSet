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
    fun estimateAndSoundPreferencesPersistAndToneChangesCreateNewChannels() = runBlocking {
        val app = compose.activity.application as NextSetApplication
        val previous = app.settings.settings.first()
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            app.settings.secondsPerSet(60)
            app.settings.sound(ringtone.toString())
            assertEquals(60, app.settings.settings.first().secondsPerSet)
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
            assertEquals(60, app.settings.settings.first().secondsPerSet)
        } finally {
            app.settings.secondsPerSet(previous.secondsPerSet)
            app.settings.sound(previous.soundUri)
            app.settings.alertMode(previous.alertMode)
        }
    }
}
