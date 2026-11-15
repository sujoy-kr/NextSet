package com.sujoy.nextset

import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sujoy.nextset.domain.*
import java.io.FileInputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDeliveryTest {
    @Test
    fun osAlarmFinishesBackgroundRestAndPostsOnlyOnce() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as NextSetApplication
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
        if (Build.VERSION.SDK_INT >= 33)
            shell("pm grant com.sujoy.nextset android.permission.POST_NOTIFICATIONS")
        if (Build.VERSION.SDK_INT >= 31)
            shell("appops set com.sujoy.nextset SCHEDULE_EXACT_ALARM allow")
        app.execution.active(app.runtime.clock.now())?.let {
            app.runtime.command(it.sessionId, null, Command.END)
        }
        val template =
            WorkoutTemplate(
                name = "Background alarm test",
                blocks =
                    listOf(
                        WorkoutBlock(exercises = listOf(Exercise(name = "A", rests = listOf(2, 0))))
                    ),
            )
        app.workouts.save(template)
        val id = app.runtime.start(template.id)
        val rest = app.runtime.command(id, 0, Command.HOLD)
        try {
            assertTrue(app.alarms.exactAllowed())
            // No execution screen/ticker is running: only the OS alarm can advance this rest.
            withTimeout(15_000) {
                while (app.execution.read(id)?.phase != Phase.REST_DONE) delay(100)
            }
            val manager = app.getSystemService(NotificationManager::class.java)
            withTimeout(5_000) {
                while (
                    manager.activeNotifications.none { it.tag?.startsWith("rest:$id:") == true }
                ) delay(100)
            }
            val notification =
                manager.activeNotifications.single { it.tag?.startsWith("rest:$id:") == true }
            repeat(3) { app.runtime.command(id, rest.revision, Command.REFRESH) }
            assertEquals(
                notification.postTime,
                manager.activeNotifications
                    .single { it.tag?.startsWith("rest:$id:") == true }
                    .postTime,
            )
            val done = app.execution.read(id)!!
            app.runtime.command(id, done.revision, Command.HOLD)
            assertTrue(manager.activeNotifications.none { it.tag?.startsWith("rest:$id:") == true })
        } finally {
            app.runtime.command(id, null, Command.END)
        }
    }

    @Test
    fun acceptedTransitionSurvivesCallerCancellation() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as NextSetApplication
        app.execution.active(app.runtime.clock.now())?.let {
            app.runtime.command(it.sessionId, null, Command.END)
        }
        val template =
            WorkoutTemplate(
                name = "Cancellation audit",
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises = listOf(Exercise(name = "A", rests = listOf(90, 0)))
                        )
                    ),
            )
        app.workouts.save(template)
        val id = app.runtime.start(template.id)
        try {
            // Start synchronously until the first Room suspension, then remove the caller.
            val caller =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    app.runtime.command(id, 0, Command.HOLD)
                }
            caller.cancelAndJoin()
            val saved = app.execution.observe(id).first()!!
            assertEquals(Phase.REST, saved.execution.phase)
            assertEquals(1, saved.session.completedSets.size)
        } finally {
            app.runtime.command(id, null, Command.END)
        }
    }
}
