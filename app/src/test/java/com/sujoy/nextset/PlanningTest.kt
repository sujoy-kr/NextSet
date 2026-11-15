package com.sujoy.nextset

import com.sujoy.nextset.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PlanningTest {
    private val saturday = LocalDate.of(2026, 11, 14)

    @Test
    fun sequenceContinuesAcrossWeekBoundariesAndIncludesRest() {
        val plan = RoutinePlan(PlanMode.SEQUENCE, listOf("push", "pull", null), saturday)
        assertEquals(
            listOf("push", "pull", null, "push", "pull", null, "push", "pull", null),
            (0L..8L).map { plan.workoutOn(saturday.plusDays(it), emptyMap()) },
        )
        assertNull(plan.workoutOn(saturday.minusDays(1), emptyMap()))
        assertNull(plan.workoutOn(saturday.minusDays(2), emptyMap()))
        assertNull(plan.copy(entries = emptyList()).workoutOn(saturday, emptyMap()))
        assertNull(plan.copy(entries = listOf(null)).workoutOn(saturday, emptyMap()))
    }

    @Test
    fun switchingModesPreservesWeekdayAssignments() {
        val assignments = mapOf(DayOfWeek.SATURDAY to "legs")
        val plan = RoutinePlan(PlanMode.SEQUENCE, listOf("push"), saturday)
        assertEquals("push", plan.workoutOn(saturday, assignments))
        assertEquals("legs", plan.copy(mode = PlanMode.WEEKDAYS).workoutOn(saturday, assignments))
    }

    @Test
    fun estimateUsesSupersetOrderOverridesAndAllRests() {
        val workout =
            WorkoutTemplate(
                name = "Circuit",
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises =
                                listOf(
                                    Exercise(name = "A", rests = listOf(null, 7)),
                                    Exercise(name = "B", rests = listOf(3)),
                                ),
                            superset = true,
                            betweenMovements = 5,
                            betweenRounds = 10,
                            afterBlock = 20,
                        )
                    ),
            )
        // A1, rest 5, B1, override 3, A2, override 7.
        assertEquals(3 * 45L + 5 + 3 + 7, workout.estimatedSeconds())
        assertEquals(3 * 60L + 5 + 3 + 7, workout.estimatedSeconds(60))
        assertThrows(IllegalArgumentException::class.java) { workout.estimatedSeconds(0) }
        val longRest =
            workout.copy(
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises =
                                listOf(
                                    Exercise(
                                        name = "A",
                                        rests = listOf(Int.MAX_VALUE, Int.MAX_VALUE),
                                    )
                                )
                        )
                    )
            )
        assertEquals(2L * Int.MAX_VALUE + 90, longRest.estimatedSeconds())
    }

    @Test
    fun weeklyAnalysisCountsActualSetDatesAndMergesExerciseNames() {
        val exercise = Exercise(name = "Bench press", rests = listOf(0, 0))
        val workout =
            WorkoutTemplate(
                id = "push",
                name = "Push",
                blocks = listOf(WorkoutBlock(exercises = listOf(exercise))),
            )
        val beforeWeek =
            saturday.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val withinWeek = saturday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val session =
            WorkoutSession(
                "s",
                workout.copy(
                    blocks =
                        listOf(
                            WorkoutBlock(exercises = listOf(exercise.copy(name = " BENCH PRESS ")))
                        )
                ),
                beforeWeek,
                null,
                SessionStatus.ACTIVE,
                listOf(
                    CompletedSet(0, withinWeek),
                    CompletedSet(1, beforeWeek),
                    CompletedSet(0, withinWeek),
                ),
            )
        val volumes =
            weeklyVolume(
                saturday,
                listOf(workout),
                emptyMap(),
                RoutinePlan(PlanMode.SEQUENCE, listOf("push", null), saturday),
                listOf(session),
                ZoneOffset.UTC,
            )
        assertEquals(1, volumes.size)
        assertEquals(8, volumes.single().planned)
        assertEquals(1, volumes.single().completed)
        assertEquals("Bench press", volumes.single().name)
        assertTrue(
            weeklyVolume(
                    saturday.plusWeeks(1),
                    emptyList(),
                    emptyMap(),
                    RoutinePlan(),
                    listOf(session),
                    ZoneOffset.UTC,
                )
                .isEmpty()
        )
    }

    @Test
    fun weeklyAnalysisSupportsWeekdaysAndPreservesDeletedWorkoutHistory() {
        val workout =
            WorkoutTemplate(
                id = "legs",
                name = "Legs",
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises = listOf(Exercise(name = "Squat", rests = listOf(0, 0)))
                        )
                    ),
            )
        val at = saturday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val history =
            listOf(
                WorkoutSession(
                    "s",
                    workout,
                    at,
                    at,
                    SessionStatus.COMPLETED,
                    listOf(CompletedSet(0, at), CompletedSet(1, at)),
                )
            )
        val assignments =
            mapOf(saturday.dayOfWeek to workout.id, saturday.plusDays(2).dayOfWeek to workout.id)
        val planned =
            weeklyVolume(
                    saturday,
                    listOf(workout),
                    assignments,
                    RoutinePlan(),
                    history,
                    ZoneOffset.UTC,
                )
                .single()
        assertEquals(4, planned.planned)
        assertEquals(2, planned.completed)
        val afterDelete =
            weeklyVolume(saturday, emptyList(), assignments, RoutinePlan(), history, ZoneOffset.UTC)
                .single()
        assertEquals(0, afterDelete.planned)
        assertEquals(2, afterDelete.completed)
    }

    @Test
    fun remindersCanBeScheduledForAFutureSequenceStart() {
        val start = saturday.plusMonths(1)
        val plan = RoutinePlan(PlanMode.SEQUENCE, listOf("push", null), start)
        val next =
            ReminderPolicy.next(
                saturday.atStartOfDay(ZoneOffset.UTC),
                emptyMap(),
                18 * 60,
                emptyList(),
                emptySet(),
                notBefore = start,
            ) {
                plan.workoutOn(it, emptyMap())
            }
        assertEquals(start, next?.first?.toLocalDate())
        assertEquals("push", next?.second)
    }

    @Test
    fun remindersFindTrainingAfterMoreThanAWeekOfRest() {
        val plan = RoutinePlan(PlanMode.SEQUENCE, List(10) { null } + "push", saturday)
        val next =
            ReminderPolicy.next(
                saturday.atStartOfDay(ZoneOffset.UTC),
                emptyMap(),
                18 * 60,
                emptyList(),
                emptySet(),
                lookAheadDays = plan.entries.size,
            ) {
                plan.workoutOn(it, emptyMap())
            }
        assertEquals(saturday.plusDays(10), next?.first?.toLocalDate())
        assertEquals("push", next?.second)
    }

    @Test
    fun remindersResolveCycleForDatesIncludingSameWeekdayNextWeek() {
        val plan = RoutinePlan(PlanMode.SEQUENCE, listOf("push", "pull", null), saturday)
        val now = saturday.atTime(19, 0).atZone(ZoneOffset.UTC)
        val delivered = (1L..6L).map { saturday.plusDays(it) }.toSet()
        val next =
            ReminderPolicy.next(now, emptyMap(), 18 * 60, emptyList(), delivered) {
                plan.workoutOn(it, emptyMap())
            }
        assertEquals(saturday.plusDays(7), next?.first?.toLocalDate())
        assertEquals("pull", next?.second)
        assertTrue(
            ReminderPolicy.shouldDeliver(
                saturday,
                "push",
                now,
                emptyMap(),
                18 * 60,
                emptyList(),
                emptySet(),
            ) {
                plan.workoutOn(it, emptyMap())
            }
        )
        assertFalse(
            ReminderPolicy.shouldDeliver(
                saturday.plusDays(2),
                "push",
                now.plusDays(2),
                emptyMap(),
                18 * 60,
                emptyList(),
                emptySet(),
            ) {
                plan.workoutOn(it, emptyMap())
            }
        )
    }
}
