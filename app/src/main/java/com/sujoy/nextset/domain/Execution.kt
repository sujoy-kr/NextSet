package com.sujoy.nextset.domain

/** Wall time is only a fallback across boots; normal operation ignores wall-clock changes. */
data class ClockReading(val elapsed: Long, val wall: Long, val boot: Int)

enum class Phase {
    NOT_STARTED,
    EXERCISE,
    REST,
    REST_DONE,
    PAUSED,
    COMPLETED,
    ABANDONED,
}

enum class Command {
    HOLD,
    PAUSE,
    RESUME,
    SKIP_REST,
    UNDO,
    END,
    REFRESH,
}

data class ExecutionState(
    val sessionId: String,
    val index: Int = 0,
    val phase: Phase = Phase.EXERCISE,
    val pausedFrom: Phase = Phase.EXERCISE,
    val remaining: Long = 0,
    val deadline: Long = 0,
    val wallDeadline: Long = 0,
    val boot: Int,
    val elapsed: Long = 0,
    val anchor: Long,
    val wallAnchor: Long,
    val revision: Long = 0,
    // True means this particular REST_DONE transition has already claimed its one alert.
    val alerted: Boolean = false,
) {
    val terminal
        get() = phase == Phase.COMPLETED || phase == Phase.ABANDONED

    fun remainingAt(now: ClockReading): Long =
        when {
            phase == Phase.PAUSED -> remaining
            phase != Phase.REST -> 0
            boot == now.boot && now.elapsed >= anchor -> (deadline - now.elapsed).coerceAtLeast(0)
            else -> (wallDeadline - now.wall).coerceIn(0, remaining)
        }
}

data class ExecutionChange(
    val state: ExecutionState,
    val completeSet: Int? = null,
    val undoFrom: Int? = null,
)

/** Deterministic reducer. Callers must compare revisions and persist set changes atomically. */
object ExecutionMachine {
    fun start(id: String, now: ClockReading) =
        ExecutionState(sessionId = id, boot = now.boot, anchor = now.elapsed, wallAnchor = now.wall)

    fun recover(state: ExecutionState, now: ClockReading): ExecutionState {
        if (state.terminal) return state
        val delta =
            if (state.boot == now.boot && now.elapsed >= state.anchor) now.elapsed - state.anchor
            else (now.wall - state.wallAnchor).coerceAtLeast(0)
        val left = state.remainingAt(now)
        return state.copy(
            elapsed = state.elapsed + delta,
            anchor = now.elapsed,
            wallAnchor = now.wall,
            boot = now.boot,
            phase = if (state.phase == Phase.REST && left == 0L) Phase.REST_DONE else state.phase,
            remaining = if (state.phase == Phase.REST) left else state.remaining,
            deadline = if (state.phase == Phase.REST) now.elapsed + left else state.deadline,
            wallDeadline = if (state.phase == Phase.REST) now.wall + left else state.wallDeadline,
        )
    }

    fun apply(
        original: ExecutionState,
        plan: List<WorkoutStep>,
        command: Command,
        now: ClockReading,
    ): ExecutionChange {
        require(plan.isNotEmpty()) { "A workout needs at least one set." }
        val state = recover(original, now)
        if (state.terminal) return ExecutionChange(state)
        var completed: Int? = null
        var undone: Int? = null
        fun advance(): ExecutionState {
            val next = state.index + 1
            val step = plan.getOrNull(next)
            return when (step) {
                null -> state.copy(index = plan.size, phase = Phase.COMPLETED, remaining = 0)
                is WorkoutStep.Set ->
                    state.copy(index = next, phase = Phase.EXERCISE, remaining = 0, alerted = false)
                is WorkoutStep.Rest -> {
                    val duration = step.seconds.toLong() * 1000
                    state.copy(
                        index = next,
                        phase = Phase.REST,
                        remaining = duration,
                        deadline = now.elapsed + duration,
                        wallDeadline = now.wall + duration,
                        alerted = false,
                    )
                }
            }
        }
        val result =
            when (command) {
                Command.HOLD ->
                    when (state.phase) {
                        Phase.EXERCISE -> {
                            require(plan[state.index] is WorkoutStep.Set)
                            completed = state.index
                            advance()
                        }
                        Phase.REST_DONE -> advance()
                        else -> state
                    }
                Command.PAUSE ->
                    if (state.phase in listOf(Phase.EXERCISE, Phase.REST, Phase.REST_DONE))
                        state.copy(phase = Phase.PAUSED, pausedFrom = state.phase)
                    else state
                Command.RESUME ->
                    if (state.phase == Phase.PAUSED)
                        state.copy(
                            phase = state.pausedFrom,
                            deadline = now.elapsed + state.remaining,
                            wallDeadline = now.wall + state.remaining,
                        )
                    else state
                Command.SKIP_REST ->
                    if (state.phase == Phase.REST || state.phase == Phase.REST_DONE) advance()
                    else state
                Command.UNDO -> {
                    val lastSet = (0 until state.index).lastOrNull { plan[it] is WorkoutStep.Set }
                    if (lastSet != null) {
                        undone = lastSet
                        state.copy(
                            index = lastSet,
                            phase = Phase.EXERCISE,
                            remaining = 0,
                            alerted = false,
                        )
                    } else state
                }
                Command.END -> state.copy(phase = Phase.ABANDONED, remaining = 0)
                Command.REFRESH -> state
            }
        // REFRESH only increments revision for a visible state change, not every display tick.
        val changed =
            result.phase != original.phase || command != Command.REFRESH && result != state
        return ExecutionChange(
            result.copy(revision = original.revision + if (changed) 1 else 0),
            completed,
            undone,
        )
    }
}
