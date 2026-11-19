package com.sujoy.nextset.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

// Storage uses Int seconds (about 68 years); this is a representation limit, not a workout limit.
const val MAX_REST_SECONDS = Int.MAX_VALUE

data class Exercise(
    val id: String = newId(),
    val name: String = "",
    // null uses the block default; the last entry is rest after the final set.
    val rests: List<Int?> = List(3) { null },
) {
    val sets: Int
        get() = rests.size
}

data class WorkoutBlock(
    val id: String = newId(),
    val exercises: List<Exercise> = listOf(Exercise()),
    val superset: Boolean = false,
    val betweenSets: Int = 90,
    val betweenMovements: Int = 0,
    val betweenRounds: Int = 90,
    val afterBlock: Int = 120,
)

data class WorkoutTemplate(
    val id: String = newId(),
    val name: String = "",
    val blocks: List<WorkoutBlock> = emptyList(),
) {
    val totalSets: Int
        get() = blocks.sumOf { b -> b.exercises.sumOf { it.sets } }

    val exerciseCount: Int
        get() = blocks.sumOf { it.exercises.size }

    fun validationError(): String? =
        when {
            id.isBlank() ||
                blocks.any { it.id.isBlank() || it.exercises.any { e -> e.id.isBlank() } } ->
                "Workout identifiers cannot be empty."
            blocks.map { it.id }.distinct().size != blocks.size ||
                blocks
                    .flatMap { it.exercises }
                    .let { exercises ->
                        exercises.map { it.id }.distinct().size != exercises.size
                    } -> "Each block and exercise must have a unique identifier."
            name.isBlank() -> "Give your workout a name."
            name.length > 100 -> "Keep workout names within 100 characters."
            blocks.isEmpty() -> "Add at least one exercise."
            blocks.any {
                it.exercises.isEmpty() ||
                    (it.superset && it.exercises.size < 2) ||
                    (!it.superset && it.exercises.size != 1)
            } -> "A superset needs at least two movements."
            blocks.flatMap { it.exercises }.any { it.name.isBlank() || it.name.length > 100 } ->
                "Name every exercise (up to 100 characters)."
            blocks.flatMap { it.exercises }.any { it.sets !in 1..100 } ->
                "Use between 1 and 100 sets per exercise."
            blocks.any { b ->
                listOf(b.betweenSets, b.betweenMovements, b.betweenRounds, b.afterBlock).any {
                    it !in 0..MAX_REST_SECONDS
                } ||
                    b.exercises.any { e ->
                        e.rests.any { it != null && it !in 0..MAX_REST_SECONDS }
                    }
            } -> "Rest must be a non-negative duration."
            else -> null
        }
}

sealed interface WorkoutStep {
    data class Set(
        val exerciseId: String,
        val exerciseName: String,
        val number: Int,
        val blockId: String,
    ) : WorkoutStep

    data class Rest(val seconds: Int) : WorkoutStep
}

/**
 * Pure execution plan. Zero rests are omitted; no timer or exercise-specific behavior lives here.
 */
fun WorkoutTemplate.sequence(): List<WorkoutStep> {
    require(validationError() == null) { validationError().orEmpty() }
    return buildList {
        blocks.forEach { block ->
            val sets =
                if (block.superset) {
                    (0 until block.exercises.maxOf { it.sets }).flatMap { round ->
                        block.exercises.filter { round < it.sets }.map { it to round }
                    }
                } else block.exercises.flatMap { e -> e.rests.indices.map { e to it } }
            sets.forEachIndexed { index, (exercise, round) ->
                add(WorkoutStep.Set(exercise.id, exercise.name, round + 1, block.id))
                val next = sets.getOrNull(index + 1)
                val defaultRest =
                    when {
                        next == null -> block.afterBlock
                        !block.superset -> block.betweenSets
                        next.second == round -> block.betweenMovements
                        else -> block.betweenRounds
                    }
                val seconds = exercise.rests[round] ?: defaultRest
                if (seconds > 0) add(WorkoutStep.Rest(seconds))
            }
        }
    }
}

fun assignedToday(assignments: Map<DayOfWeek, String>, date: LocalDate = LocalDate.now()): String? =
    assignments[date.dayOfWeek]

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED,
}

data class CompletedSet(val stepIndex: Int, val completedAt: Long)

/** When the rest at [stepIndex] was left, whether by acknowledging the alert or skipping. */
data class CompletedRest(val stepIndex: Int, val endedAt: Long)

data class WorkoutSession(
    val id: String,
    val snapshot: WorkoutTemplate,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus,
    val completedSets: List<CompletedSet>,
    val recordedElapsedMillis: Long? = null,
    val completedRests: List<CompletedRest> = emptyList(),
) {
    val elapsedMillis: Long
        get() =
            recordedElapsedMillis
                ?: ((endedAt ?: System.currentTimeMillis()) - startedAt).coerceAtLeast(0)
}
