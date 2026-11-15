package com.sujoy.nextset.domain

import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    val templates: Flow<List<WorkoutTemplate>>
    val schedule: Flow<Map<DayOfWeek, String>>

    suspend fun save(template: WorkoutTemplate)

    suspend fun delete(id: String)

    suspend fun duplicate(id: String)

    suspend fun reorder(ids: List<String>)

    suspend fun assign(day: DayOfWeek, templateId: String?)
}

/**
 * Sessions own an immutable snapshot. Template edits and schedule changes cannot rewrite history.
 */
interface SessionRepository {
    val sessions: Flow<List<WorkoutSession>>

    suspend fun begin(templateId: String, at: Long = System.currentTimeMillis()): String

    suspend fun recordSet(sessionId: String, stepIndex: Int, at: Long = System.currentTimeMillis())

    suspend fun finish(sessionId: String, completed: Boolean, at: Long = System.currentTimeMillis())
}
