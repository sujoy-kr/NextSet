package com.sujoy.nextset

import com.sujoy.nextset.domain.*
import com.sujoy.nextset.ui.elapsedLabel
import org.junit.Assert.*
import org.junit.Test

class ReleaseQualityTest {
    @Test
    fun duplicateIdentifiersAreRejectedBeforePersistenceOrExecution() {
        val exercise = Exercise(name = "Press")
        val t =
            WorkoutTemplate(
                name = "Push",
                blocks =
                    listOf(WorkoutBlock(exercises = listOf(exercise, exercise), superset = true)),
            )
        assertNotNull(t.validationError())
        assertThrows(IllegalArgumentException::class.java) { t.sequence() }
        val b = WorkoutBlock(exercises = listOf(exercise))
        assertNotNull(
            t.copy(blocks = listOf(b, b.copy(exercises = listOf(exercise.copy(id = newId())))))
                .validationError()
        )
        assertNotNull(t.copy(id = "", blocks = listOf(b)).validationError())
    }

    @Test
    fun historyDurationRetainsUsefulPrecision() {
        assertEquals("0s", elapsedLabel(-10))
        assertEquals("45s", elapsedLabel(45_900))
        assertEquals("1m 1s", elapsedLabel(61_000))
        assertEquals("2h 3m", elapsedLabel((2 * 3600L + 3 * 60 + 25) * 1000))
    }

    @Test
    fun completeUnequalSupersetThroughTheReducerRecordsEverySetInOrder() {
        val t =
            WorkoutTemplate(
                name = "Circuit",
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises =
                                listOf(
                                    Exercise(name = "A", rests = listOf(0, 1, 0)),
                                    Exercise(name = "B", rests = listOf(2)),
                                ),
                            superset = true,
                            betweenMovements = 0,
                            betweenRounds = 0,
                            afterBlock = 0,
                        )
                    ),
            )
        val plan = t.sequence()
        var now = ClockReading(1000, 1_000_000, 1)
        var state = ExecutionMachine.start("s", now)
        val sets = mutableListOf<String>()
        repeat(plan.size * 3) {
            if (state.terminal) return@repeat
            val command =
                when (state.phase) {
                    Phase.REST -> {
                        now = now.copy(elapsed = state.deadline, wall = state.wallDeadline)
                        Command.REFRESH
                    }
                    else -> Command.HOLD
                }
            val change = ExecutionMachine.apply(state, plan, command, now)
            change.completeSet?.let { sets.add((plan[it] as WorkoutStep.Set).exerciseName) }
            state = change.state
        }
        assertEquals(listOf("A", "B", "A", "A"), sets)
        assertEquals(Phase.COMPLETED, state.phase)
    }
}
