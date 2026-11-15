package com.sujoy.nextset.data

import androidx.room.withTransaction
import com.sujoy.nextset.domain.*
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray

internal fun TemplateRelation.domain() =
    WorkoutTemplate(
        template.id,
        template.name,
        blocks
            .sortedBy { it.block.position }
            .map { relation ->
                val b = relation.block
                WorkoutBlock(
                    b.id,
                    relation.exercises
                        .sortedBy { it.position }
                        .map { e ->
                            val values = JSONArray(e.rests)
                            Exercise(
                                e.id,
                                e.name,
                                (0 until values.length()).map {
                                    if (values.isNull(it)) null else values.getInt(it)
                                },
                            )
                        },
                    b.superset,
                    b.betweenSets,
                    b.betweenMovements,
                    b.betweenRounds,
                    b.afterBlock,
                )
            },
    )

class LocalWorkoutRepository(private val db: NextSetDatabase) : WorkoutRepository {
    private val dao = db.workouts()
    override val templates =
        dao.observeTemplates().map { rows -> rows.map { it.domain() } }.flowOn(Dispatchers.Default)
    override val schedule =
        dao.observeSchedule().map { rows ->
            rows.mapNotNull { r -> r.templateId?.let { DayOfWeek.of(r.day) to it } }.toMap()
        }

    override suspend fun save(template: WorkoutTemplate) {
        require(template.validationError() == null) { template.validationError().orEmpty() }
        db.withTransaction {
            val rows = dao.templateRows()
            dao.upsertTemplate(
                TemplateRow(
                    template.id,
                    template.name.trim(),
                    rows.find { it.id == template.id }?.position
                        ?: ((rows.maxOfOrNull { it.position } ?: -1) + 1),
                )
            )
            dao.clearBlocks(template.id)
            dao.insertBlocks(
                template.blocks.mapIndexed { i, b ->
                    BlockRow(
                        b.id,
                        template.id,
                        i,
                        b.superset,
                        b.betweenSets,
                        b.betweenMovements,
                        b.betweenRounds,
                        b.afterBlock,
                    )
                }
            )
            dao.insertExercises(
                template.blocks.flatMap { b ->
                    b.exercises.mapIndexed { i, e ->
                        ExerciseRow(
                            e.id,
                            b.id,
                            i,
                            e.name.trim(),
                            JSONArray()
                                .apply { e.rests.forEach { put(it ?: org.json.JSONObject.NULL) } }
                                .toString(),
                        )
                    }
                }
            )
        }
    }

    override suspend fun delete(id: String) = db.withTransaction { dao.deleteTemplate(id) }

    override suspend fun duplicate(id: String) =
        db.withTransaction {
            val source = requireNotNull(dao.template(id)).domain()
            save(
                source.copy(
                    id = newId(),
                    name = source.name.take(93) + " (copy)",
                    blocks =
                        source.blocks.map { b ->
                            b.copy(
                                id = newId(),
                                exercises = b.exercises.map { it.copy(id = newId()) },
                            )
                        },
                )
            )
        }

    override suspend fun reorder(ids: List<String>) =
        db.withTransaction {
            val rows = dao.templateRows()
            require(ids.size == rows.size && ids.toSet() == rows.map { it.id }.toSet()) {
                "Workout list changed; try again."
            }
            val byId = rows.associateBy { it.id }
            ids.forEachIndexed { i, id ->
                val row = byId.getValue(id)
                if (row.position != i) dao.upsertTemplate(row.copy(position = i))
            }
        }

    override suspend fun assign(day: DayOfWeek, templateId: String?) =
        db.withTransaction {
            require(templateId == null || dao.template(templateId) != null) {
                "Workout no longer exists."
            }
            dao.assign(ScheduleRow(day.value, templateId))
        }
}

internal fun SessionRelation.domain() =
    WorkoutSession(
        session.id,
        TemplateCodec.decode(session.snapshot),
        session.startedAt,
        session.endedAt,
        SessionStatus.valueOf(session.status),
        sets.sortedBy { it.stepIndex }.map { CompletedSet(it.stepIndex, it.completedAt) },
        session.elapsedMillis,
    )

class LocalSessionRepository(private val db: NextSetDatabase) : SessionRepository {
    private val dao = db.sessions()
    override val sessions =
        dao.observeSessions().map { rows -> rows.map { it.domain() } }.flowOn(Dispatchers.Default)

    override suspend fun begin(templateId: String, at: Long): String =
        db.withTransaction {
            require(dao.active() == null) {
                "Finish or abandon the active session before starting another."
            }
            val template =
                requireNotNull(db.workouts().template(templateId)) { "Workout no longer exists." }
                    .domain()
            newId().also {
                dao.insert(
                    SessionRow(
                        it,
                        TemplateCodec.encode(template),
                        at,
                        null,
                        SessionStatus.ACTIVE.name,
                    )
                )
            }
        }

    override suspend fun recordSet(sessionId: String, stepIndex: Int, at: Long) =
        db.withTransaction {
            require(db.execution().get(sessionId) == null) {
                "Use the execution engine to update a running workout."
            }
            val session = requireNotNull(dao.session(sessionId)) { "Session not found." }
            require(session.session.status == SessionStatus.ACTIVE.name) { "Session has ended." }
            require(at >= session.session.startedAt) { "Completion precedes session start." }
            val step =
                TemplateCodec.decode(session.session.snapshot).sequence().getOrNull(stepIndex)
            require(step is WorkoutStep.Set) { "Only workout sets can be completed." }
            dao.insertSet(CompletedSetRow(sessionId, stepIndex, at))
        }

    override suspend fun finish(sessionId: String, completed: Boolean, at: Long) =
        db.withTransaction {
            require(db.execution().get(sessionId) == null) {
                "Use the execution engine to finish a running workout."
            }
            val relation = requireNotNull(dao.session(sessionId)) { "Session not found." }
            if (relation.session.status != SessionStatus.ACTIVE.name) return@withTransaction
            require(
                at >= relation.session.startedAt && relation.sets.all { it.completedAt <= at }
            ) {
                "End precedes completed activity."
            }
            if (completed)
                require(
                    relation.sets.size == TemplateCodec.decode(relation.session.snapshot).totalSets
                ) {
                    "Complete every set before finishing."
                }
            dao.finish(
                sessionId,
                if (completed) SessionStatus.COMPLETED.name else SessionStatus.ABANDONED.name,
                at,
            )
        }
}
