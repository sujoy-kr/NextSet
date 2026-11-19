package com.sujoy.nextset.domain

/**
 * How long one step of a finished or partial session actually took. [millis] is null when it was
 * not reached or was never recorded. [approximate] marks a set whose time also contains a rest
 * that older sessions did not record separately. Paused time is part of the step it happened in.
 */
data class StepTiming(
    val index: Int,
    val step: WorkoutStep,
    val millis: Long?,
    val approximate: Boolean = false,
)

fun WorkoutSession.timeline(): List<StepTiming> {
    val sets = completedSets.associate { it.stepIndex to it.completedAt }
    val rests = completedRests.associate { it.stepIndex to it.endedAt }
    var cursor = startedAt
    var skippedRest = false
    return snapshot.sequence().mapIndexed { index, step ->
        val end =
            when (step) {
                is WorkoutStep.Set -> sets[index]
                is WorkoutStep.Rest -> rests[index]
            }
        if (end == null) {
            // A rest without a record (older session) leaves its time inside the next set.
            if (step is WorkoutStep.Rest && sets.keys.any { it > index }) skippedRest = true
            StepTiming(index, step, null)
        } else {
            val timing =
                StepTiming(
                    index,
                    step,
                    (end - cursor).coerceAtLeast(0),
                    approximate = step is WorkoutStep.Set && skippedRest,
                )
            cursor = end
            if (step is WorkoutStep.Set) skippedRest = false
            timing
        }
    }
}
