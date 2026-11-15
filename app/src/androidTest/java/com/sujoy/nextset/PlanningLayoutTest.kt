package com.sujoy.nextset

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.domain.*
import com.sujoy.nextset.ui.*
import java.time.*
import java.time.temporal.WeekFields
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanningLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun planningChoicesWrapAndRemainInsideANarrowScreenWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                NextSetTheme(com.sujoy.nextset.data.ThemeMode.LIGHT) {
                    Box(Modifier.width(280.dp)) {
                        ScheduleScreen(AppState(loading = false), { _, _ -> }, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Repeating sequence").performScrollTo().assertIsDisplayed()
        val fixed = compose.onNodeWithText("Weekdays").fetchSemanticsNode().boundsInRoot
        val sequence =
            compose.onNodeWithText("Repeating sequence").fetchSemanticsNode().boundsInRoot
        assertTrue("Planning modes should wrap onto separate lines", sequence.top >= fixed.bottom)
        assertTrue(
            "The second choice must fit the screen",
            sequence.right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right,
        )
    }

    @Test
    fun weeklyAnalysisRecalculatesWhenTheTimezoneChanges() {
        val today = LocalDate.of(2026, 11, 16)
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val start = today.minusDays(((today.dayOfWeek.value - first.value + 7) % 7).toLong())
        val at = start.minusDays(1).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val template =
            WorkoutTemplate(
                name = "Push",
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises = listOf(Exercise(name = "Press", rests = listOf(0)))
                        )
                    ),
            )
        var state by
            mutableStateOf(
                AppState(
                    today = start,
                    zone = ZoneOffset.UTC,
                    sessions =
                        listOf(
                            WorkoutSession(
                                "s",
                                template,
                                at,
                                at,
                                SessionStatus.COMPLETED,
                                listOf(CompletedSet(0, at)),
                            )
                        ),
                )
            )
        compose.setContent {
            NextSetTheme(com.sujoy.nextset.data.ThemeMode.LIGHT) { WeeklyAnalysis(state) }
        }
        compose.onNodeWithText("0 completed · 0 planned sets").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(zone = ZoneId.of("Asia/Dhaka")) }
        compose.onNodeWithText("1 completed · 0 planned sets").assertIsDisplayed()
    }
}
