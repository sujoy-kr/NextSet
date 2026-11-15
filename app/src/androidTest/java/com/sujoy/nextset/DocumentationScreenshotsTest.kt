package com.sujoy.nextset

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.sujoy.nextset.data.*
import com.sujoy.nextset.domain.*
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in captures of the real UI. Run on a fresh, disposable emulator; never clears user data. */
class DocumentationScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(30_000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() ==
                instrumentation.targetContext.packageName
        }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val output =
            File(instrumentation.targetContext.filesDir, "documentation-screenshots/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }

    private fun tab(name: String) {
        compose.onNodeWithText(name, useUnmergedTree = true).performClick()
    }

    private fun scrollTo(text: String, substring: Boolean = false) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }

    @Test
    fun captureCurrentScreens() {
        assumeTrue(
            InstrumentationRegistry.getArguments().getString("documentationScreenshots") == "true"
        )
        val app = compose.activity.application as NextSetApplication
        assertTrue(
            "Use a fresh disposable emulator; existing workout data is never cleared.",
            runBlocking {
                app.workouts.templates.first().isEmpty() && app.sessions.sessions.first().isEmpty()
            },
        )
        val today = LocalDate.now()
        fun workout(name: String, vararg exercises: Pair<String, Int>) =
            WorkoutTemplate(
                name = name,
                blocks =
                    exercises.map { (exercise, sets) ->
                        WorkoutBlock(
                            exercises =
                                listOf(Exercise(name = exercise, rests = List(sets) { null }))
                        )
                    },
            )
        val push =
            workout(
                "Push",
                "Bench press" to 3,
                "Shoulder press" to 2,
                "Lateral raise" to 3,
                "Triceps extension" to 2,
            )
        val pull = workout("Pull", "Lat pulldown" to 3, "Seated row" to 3, "Biceps curl" to 2)
        val legs = workout("Legs", "Squat" to 3, "Romanian deadlift" to 3, "Calf raise" to 3)
        runBlocking {
            listOf(push, pull, legs).forEach { app.workouts.save(it) }
            app.settings.theme(ThemeMode.LIGHT)
            app.settings.alertMode(AlertMode.SOUND_AND_VIBRATION)
            app.settings.secondsPerSet(45)
            app.settings.sound(null)
            app.settings.routine(RoutinePlan())
            app.workouts.assign(DayOfWeek.MONDAY, push.id)
            app.workouts.assign(DayOfWeek.TUESDAY, pull.id)
            app.workouts.assign(DayOfWeek.THURSDAY, legs.id)
            app.workouts.assign(today.dayOfWeek, push.id)
            val at = today.atTime(8, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            app.database
                .sessions()
                .insert(
                    SessionRow(
                        "documentation-history",
                        TemplateCodec.encode(push),
                        at,
                        at + 1_200_000,
                        SessionStatus.COMPLETED.name,
                        1_200_000,
                    )
                )
            push.sequence().forEachIndexed { index, step ->
                if (step is WorkoutStep.Set)
                    app.database
                        .sessions()
                        .insertSet(
                            CompletedSetRow("documentation-history", index, at + index * 30_000)
                        )
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Push").fetchSemanticsNodes().isNotEmpty()
        }
        capture("home")
        tab("Workouts")
        compose.onNodeWithText("Your library", ignoreCase = true).assertExists()
        capture("workouts")
        tab("Week")
        capture("week")
        runBlocking {
            app.settings.routine(
                RoutinePlan(PlanMode.SEQUENCE, listOf(push.id, pull.id, null), today)
            )
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Day 1 · Today · Push").fetchSemanticsNodes().isNotEmpty()
        }
        capture("sequence")
        scrollTo("Next 7 days")
        capture("sequence-preview")
        tab("History")
        scrollTo("Weekly analysis")
        capture("weekly-analysis")
        compose.onNodeWithContentDescription("Settings").performClick()
        scrollTo("Choose notification tone")
        capture("sound-settings")
        scrollTo("Time per set", substring = true)
        capture("duration-settings")
        runBlocking { app.settings.theme(ThemeMode.DARK) }
        scrollTo("Appearance")
        capture("settings-dark")
        runBlocking { app.settings.theme(ThemeMode.LIGHT) }
        tab("Home")
        val quick =
            workout("Push · timer demo", "Bench press" to 2).let { template ->
                template.copy(
                    blocks =
                        template.blocks.map { block ->
                            block.copy(
                                exercises = block.exercises.map { it.copy(rests = listOf(1, 0)) }
                            )
                        }
                )
            }
        val id = runBlocking {
            app.workouts.save(quick)
            app.runtime.start(quick.id).also { app.requestedSession.value = it }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("workout_surface").fetchSemanticsNodes().isNotEmpty()
        }
        capture("exercise")
        runBlocking { app.runtime.command(id, null, Command.HOLD) }
        compose.waitUntil(30_000) {
            runBlocking { app.execution.read(id)?.phase == Phase.REST_DONE }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("REST COMPLETE").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("REST COMPLETE").assertIsDisplayed()
        runBlocking { app.execution.read(id)?.let { app.notifications.clearRest(id, it.revision) } }
        capture("rest-complete")
        runBlocking { app.runtime.command(id, null, Command.END) }
    }
}
