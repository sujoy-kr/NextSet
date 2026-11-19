package com.sujoy.nextset.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class PlanMode {
    WEEKDAYS,
    SEQUENCE,
}

/** Each entry occupies one calendar day; null represents recovery. */
data class RoutinePlan(
    val mode: PlanMode = PlanMode.WEEKDAYS,
    val entries: List<String?> = emptyList(),
    val startDate: LocalDate = LocalDate.ofEpochDay(0),
) {
    fun indexOn(date: LocalDate): Int? =
        if (entries.isEmpty() || date.isBefore(startDate)) null
        else Math.floorMod(ChronoUnit.DAYS.between(startDate, date), entries.size.toLong()).toInt()

    fun workoutOn(date: LocalDate, weekdays: Map<DayOfWeek, String>): String? =
        if (mode == PlanMode.WEEKDAYS) weekdays[date.dayOfWeek]
        else indexOn(date)?.let { entries[it] }
}

/** Assumed active time per set; only used for the prediction before real sessions exist. */
const val DEFAULT_SECONDS_PER_SET = 45

/** Uses exactly the rests executed by the timer, including per-set overrides and final rest. */
fun WorkoutTemplate.estimatedSeconds(secondsPerSet: Int = DEFAULT_SECONDS_PER_SET): Long {
    require(secondsPerSet > 0)
    return sequence().sumOf {
        when (it) {
            is WorkoutStep.Set -> secondsPerSet.toLong()
            is WorkoutStep.Rest -> it.seconds.toLong()
        }
    }
}

/** A workout's expected duration and whether it comes from past sessions or the per-set estimate. */
data class DurationExpectation(val seconds: Long, val fromHistory: Boolean)

/**
 * Average elapsed time of this workout's completed sessions; with no history, the estimate from
 * [estimatedSeconds].
 */
fun WorkoutTemplate.expectedDuration(sessions: List<WorkoutSession>): DurationExpectation {
    val past =
        sessions.filter {
            it.status == SessionStatus.COMPLETED &&
                it.snapshot.id == id &&
                it.elapsedMillis > 0
        }
    return if (past.isEmpty()) DurationExpectation(estimatedSeconds(), false)
    else DurationExpectation(past.sumOf { it.elapsedMillis } / past.size / 1000, true)
}

data class ExerciseVolume(val name: String, val planned: Int, val completed: Int)

/** Completed sets are attributed to their completion date, including unfinished sessions. */
fun weeklyVolume(
    start: LocalDate,
    templates: List<WorkoutTemplate>,
    weekdays: Map<DayOfWeek, String>,
    plan: RoutinePlan,
    sessions: List<WorkoutSession>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ExerciseVolume> {
    val volumes = linkedMapOf<String, ExerciseVolume>()
    fun add(name: String, planned: Int = 0, completed: Int = 0) {
        val key = name.trim().lowercase(Locale.ROOT)
        val previous = volumes[key] ?: ExerciseVolume(name.trim(), 0, 0)
        volumes[key] =
            previous.copy(
                planned = previous.planned + planned,
                completed = previous.completed + completed,
            )
    }
    repeat(7) { offset ->
        val id = plan.workoutOn(start.plusDays(offset.toLong()), weekdays)
        templates
            .find { it.id == id }
            ?.blocks
            ?.forEach { block -> block.exercises.forEach { add(it.name, planned = it.sets) } }
    }
    sessions.forEach { session ->
        val steps = session.snapshot.sequence()
        session.completedSets
            .distinctBy { it.stepIndex }
            .forEach { set ->
                val date = Instant.ofEpochMilli(set.completedAt).atZone(zone).toLocalDate()
                if (!date.isBefore(start) && date.isBefore(start.plusDays(7))) {
                    (steps.getOrNull(set.stepIndex) as? WorkoutStep.Set)?.let {
                        add(it.exerciseName, completed = 1)
                    }
                }
            }
    }
    return volumes.values.sortedBy { it.name.lowercase(Locale.ROOT) }
}
