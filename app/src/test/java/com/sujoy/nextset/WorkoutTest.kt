package com.sujoy.nextset

import com.sujoy.nextset.domain.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WorkoutTest {
    @Test
    fun weeklyAssignmentsRepeatWithoutChangingForMissedWorkouts() {
        val schedule = mapOf(DayOfWeek.SATURDAY to "push", DayOfWeek.TUESDAY to "push")
        assertEquals("push", assignedToday(schedule, LocalDate.of(2026, 11, 14)))
        assertEquals("push", assignedToday(schedule, LocalDate.of(2026, 11, 21)))
        assertEquals("push", assignedToday(schedule, LocalDate.of(2026, 11, 17)))
        assertNull(assignedToday(schedule, LocalDate.of(2026, 11, 16)))
        assertEquals(2, schedule.size)
    }

    @Test
    fun assignmentUsesLocalDayAcrossTimezones() {
        val instant = Instant.parse("2026-11-15T23:30:00Z")
        val schedule = mapOf(DayOfWeek.MONDAY to "pull", DayOfWeek.SUNDAY to "push")
        assertEquals(
            "pull",
            assignedToday(schedule, instant.atZone(ZoneId.of("Asia/Dhaka")).toLocalDate()),
        )
        assertEquals(
            "push",
            assignedToday(schedule, instant.atZone(ZoneId.of("America/New_York")).toLocalDate()),
        )
    }

    @Test
    fun individualSetOverridesIncludeFinalRest() {
        val t =
            WorkoutTemplate(
                name = "Push",
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises =
                                listOf(Exercise(name = "Press", rests = listOf(null, 0, 15))),
                            betweenSets = 60,
                            afterBlock = 120,
                        )
                    ),
            )
        assertEquals(
            listOf(60, 15),
            t.sequence().filterIsInstance<WorkoutStep.Rest>().map { it.seconds },
        )
        assertEquals(
            listOf(1, 2, 3),
            t.sequence().filterIsInstance<WorkoutStep.Set>().map { it.number },
        )
    }

    @Test
    fun supersetsAlternateWithUnequalSetsAndThreeMovements() {
        val block =
            WorkoutBlock(
                superset = true,
                exercises =
                    listOf(
                        Exercise(name = "A", rests = List(3) { null }),
                        Exercise(name = "B", rests = List(2) { null }),
                        Exercise(name = "C", rests = List(1) { null }),
                    ),
                betweenMovements = 5,
                betweenRounds = 30,
                afterBlock = 90,
            )
        val plan = WorkoutTemplate(name = "Circuit", blocks = listOf(block)).sequence()
        assertEquals(
            listOf("A", "B", "C", "A", "B", "A"),
            plan.filterIsInstance<WorkoutStep.Set>().map { it.exerciseName },
        )
        assertEquals(
            listOf(5, 5, 30, 5, 30, 90),
            plan.filterIsInstance<WorkoutStep.Rest>().map { it.seconds },
        )
    }

    @Test
    fun zeroRestSupersetProducesOnlySets() {
        val t =
            WorkoutTemplate(
                name = "Flow",
                blocks =
                    listOf(
                        WorkoutBlock(
                            superset = true,
                            exercises = listOf(Exercise(name = "A"), Exercise(name = "B")),
                            betweenMovements = 0,
                            betweenRounds = 0,
                            afterBlock = 0,
                        )
                    ),
            )
        assertEquals(6, t.sequence().size)
        assertTrue(t.sequence().all { it is WorkoutStep.Set })
    }

    @Test
    fun supersetPerSetOverrideReplacesGroupRest() {
        val t =
            WorkoutTemplate(
                name = "Flow",
                blocks =
                    listOf(
                        WorkoutBlock(
                            superset = true,
                            exercises =
                                listOf(
                                    Exercise(name = "A", rests = listOf(0)),
                                    Exercise(name = "B", rests = listOf(7)),
                                ),
                            betweenMovements = 10,
                            afterBlock = 100,
                        )
                    ),
            )
        assertEquals(
            listOf(7),
            t.sequence().filterIsInstance<WorkoutStep.Rest>().map { it.seconds },
        )
    }

    @Test
    fun invalidWorkoutsCannotGeneratePlans() {
        assertNotNull(WorkoutTemplate().validationError())
        val one =
            WorkoutTemplate(
                name = "Invalid",
                blocks =
                    listOf(WorkoutBlock(exercises = listOf(Exercise(name = "A")), superset = true)),
            )
        assertNotNull(one.validationError())
        assertThrows(IllegalArgumentException::class.java) { one.sequence() }
        assertNotNull(
            one.copy(
                    blocks =
                        listOf(
                            WorkoutBlock(
                                exercises = listOf(Exercise(name = "A", rests = emptyList()))
                            )
                        )
                )
                .validationError()
        )
        assertNotNull(
            one.copy(
                    blocks =
                        listOf(
                            WorkoutBlock(
                                exercises = listOf(Exercise(name = "A", rests = listOf(-1)))
                            )
                        )
                )
                .validationError()
        )
    }
}
