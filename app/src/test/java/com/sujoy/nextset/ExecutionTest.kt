package com.sujoy.nextset

import com.sujoy.nextset.domain.*
import org.junit.Assert.*
import org.junit.Test

class ExecutionTest {
    private val now = ClockReading(1_000, 1_000_000, 1)

    private fun clock(delta: Long) =
        now.copy(elapsed = now.elapsed + delta, wall = now.wall + delta)

    private val template =
        WorkoutTemplate(
            name = "Push",
            blocks =
                listOf(
                    WorkoutBlock(
                        exercises = listOf(Exercise(name = "Press", rests = listOf(10, 0))),
                        afterBlock = 0,
                    )
                ),
        )
    private val plan = template.sequence()

    private fun command(state: ExecutionState, cmd: Command, delta: Long = 0) =
        ExecutionMachine.apply(state, plan, cmd, clock(delta))

    @Test
    fun ordinarySetsRequireRestAcknowledgment() {
        val start = ExecutionMachine.start("s", now)
        val rest = command(start, Command.HOLD)
        assertEquals(0, rest.completeSet)
        assertEquals(Phase.REST, rest.state.phase)
        val finished = command(rest.state, Command.REFRESH, 10_000).state
        assertEquals(Phase.REST_DONE, finished.phase)
        assertEquals(1, finished.index)
        assertEquals(finished.index, command(finished, Command.REFRESH, 50_000).state.index)
        val set2 = command(finished, Command.HOLD, 50_000).state
        assertEquals(2, set2.index)
        assertEquals(Phase.EXERCISE, set2.phase)
        val completed = command(set2, Command.HOLD, 51_000)
        assertEquals(2, completed.completeSet)
        assertEquals(Phase.COMPLETED, completed.state.phase)
        assertNull(command(completed.state, Command.HOLD, 52_000).completeSet)
    }

    @Test
    fun earlyHoldAndShortRestRefreshCannotSkipCountdown() {
        val rest = command(ExecutionMachine.start("s", now), Command.HOLD).state
        val ignored = command(rest, Command.HOLD, 1_000)
        assertEquals(Phase.REST, ignored.state.phase)
        assertNull(ignored.completeSet)
        assertEquals(9_000L, ignored.state.remainingAt(clock(1_000)))
    }

    @Test
    fun pauseFreezesRestAndResumeUsesRemainingNotOriginalDuration() {
        val rest = command(ExecutionMachine.start("s", now), Command.HOLD).state
        val paused = command(rest, Command.PAUSE, 4_000).state
        assertEquals(6_000L, paused.remaining)
        assertEquals(6_000L, paused.remainingAt(clock(100_000)))
        val resumed = command(paused, Command.RESUME, 100_000).state
        assertEquals(6_000L, resumed.remainingAt(clock(100_000)))
        assertEquals(Phase.REST_DONE, command(resumed, Command.REFRESH, 106_000).state.phase)
        assertEquals(106_000L, command(resumed, Command.REFRESH, 106_000).state.elapsed)
    }

    @Test
    fun pauseExerciseAndRestDoneNeverCompletesAnotherSet() {
        val start = ExecutionMachine.start("s", now)
        val pause = command(start, Command.PAUSE).state
        assertNull(command(pause, Command.HOLD).completeSet)
        assertEquals(Phase.EXERCISE, command(pause, Command.RESUME).state.phase)
        val expired = command(command(start, Command.HOLD).state, Command.REFRESH, 10_000).state
        val pausedDone = command(expired, Command.PAUSE, 11_000).state
        assertEquals(Phase.REST_DONE, command(pausedDone, Command.RESUME, 30_000).state.phase)
    }

    @Test
    fun undoRestAndFollowingExerciseReturnToLastCompletedSet() {
        val rest = command(ExecutionMachine.start("s", now), Command.HOLD).state
        val undo = command(rest, Command.UNDO, 1_000)
        assertEquals(0, undo.undoFrom)
        assertEquals(0, undo.state.index)
        assertEquals(Phase.EXERCISE, undo.state.phase)
        val next = command(rest, Command.SKIP_REST, 1_000).state
        assertEquals(0, command(next, Command.UNDO, 2_000).undoFrom)
        assertNull(command(ExecutionMachine.start("s", now), Command.UNDO).undoFrom)
    }

    @Test
    fun zeroRestImmediatelyAdvancesWithoutRestDone() {
        val zero =
            WorkoutTemplate(
                    name = "Flow",
                    blocks =
                        listOf(
                            WorkoutBlock(
                                exercises = listOf(Exercise(name = "A", rests = listOf(0, 0))),
                                afterBlock = 0,
                            )
                        ),
                )
                .sequence()
        val first =
            ExecutionMachine.apply(ExecutionMachine.start("s", now), zero, Command.HOLD, now).state
        assertEquals(Phase.EXERCISE, first.phase)
        assertEquals(1, first.index)
        assertEquals(
            Phase.COMPLETED,
            ExecutionMachine.apply(first, zero, Command.HOLD, now).state.phase,
        )
    }

    @Test
    fun deviceClockChangesDoNotChangeCountdownOrElapsedOnSameBoot() {
        val rest = command(ExecutionMachine.start("s", now), Command.HOLD).state
        val changed = now.copy(elapsed = 6_000, wall = now.wall - 3_600_000)
        val recovered = ExecutionMachine.recover(rest, changed)
        assertEquals(5_000L, recovered.remaining)
        assertEquals(5_000L, recovered.elapsed)
    }

    @Test
    fun rebootUsesWallDeadlineAndExpiredTimerDoesNotRestart() {
        val rest = command(ExecutionMachine.start("s", now), Command.HOLD).state
        val reboot = ClockReading(500, now.wall + 20_000, 2)
        val recovered = ExecutionMachine.recover(rest, reboot)
        assertEquals(Phase.REST_DONE, recovered.phase)
        assertEquals(0L, recovered.remaining)
        assertEquals(20_000L, recovered.elapsed)
    }

    @Test
    fun rebootAndWallClockRollbackNeverExtendBeyondSavedRemaining() {
        val rest = command(ExecutionMachine.start("s", now), Command.HOLD).state
        val reboot = ClockReading(500, now.wall - 10_000, 2)
        assertEquals(10_000L, ExecutionMachine.recover(rest, reboot).remaining)
    }

    @Test
    fun pausedTimerStaysFrozenAcrossReboot() {
        val rest = command(ExecutionMachine.start("s", now), Command.HOLD).state
        val paused = command(rest, Command.PAUSE, 3_000).state
        val recovered = ExecutionMachine.recover(paused, ClockReading(20, now.wall + 100_000, 2))
        assertEquals(Phase.PAUSED, recovered.phase)
        assertEquals(7_000L, recovered.remaining)
    }

    @Test
    fun restOver24HoursAndIntegerSecondLimitAreSafe() {
        val t =
            template.copy(
                blocks =
                    listOf(
                        WorkoutBlock(
                            exercises = listOf(Exercise(name = "A", rests = listOf(Int.MAX_VALUE)))
                        )
                    )
            )
        assertNull(t.validationError())
        val rest =
            ExecutionMachine.apply(
                    ExecutionMachine.start("s", now),
                    t.sequence(),
                    Command.HOLD,
                    now,
                )
                .state
        assertEquals(Int.MAX_VALUE.toLong() * 1000, rest.remaining)
    }

    @Test
    fun endIsTerminalAndDoesNotCreateCompletedSet() {
        val end = command(ExecutionMachine.start("s", now), Command.END, 500).state
        assertEquals(Phase.ABANDONED, end.phase)
        assertNull(command(end, Command.HOLD, 600).completeSet)
    }

    @Test
    fun skipRestAndAcknowledgmentAfterFinalTransitionCompleteOnlyOnce() {
        val p =
            WorkoutTemplate(
                    name = "Single",
                    blocks =
                        listOf(
                            WorkoutBlock(
                                exercises = listOf(Exercise(name = "A", rests = listOf(1)))
                            )
                        ),
                )
                .sequence()
        val rest =
            ExecutionMachine.apply(ExecutionMachine.start("s", now), p, Command.HOLD, now).state
        val done = ExecutionMachine.apply(rest, p, Command.REFRESH, clock(2_000)).state
        val final = ExecutionMachine.apply(done, p, Command.HOLD, clock(2_000)).state
        assertEquals(Phase.COMPLETED, final.phase)
        assertNull(ExecutionMachine.apply(final, p, Command.HOLD, clock(3_000)).completeSet)
    }
}
