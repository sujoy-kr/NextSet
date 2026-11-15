package com.sujoy.nextset.data

import androidx.room.*
import com.sujoy.nextset.domain.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

@Entity(
    tableName = "execution",
    foreignKeys =
        [
            ForeignKey(
                entity = SessionRow::class,
                parentColumns = ["id"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class ExecutionRow(@PrimaryKey val sessionId: String, val payload: String)

/** One transactional observation keeps checkpoint, status and completed sets consistent. */
data class RunningRelation(
    @Embedded val execution: ExecutionRow,
    @Relation(entity = SessionRow::class, parentColumn = "sessionId", entityColumn = "id")
    val session: SessionRelation,
)

@Entity(tableName = "reminder_deliveries")
data class ReminderDelivery(@PrimaryKey val date: String, val templateId: String)

@Dao
interface ExecutionDao {
    @Transaction
    @Query("SELECT * FROM execution WHERE sessionId = :id")
    fun observeRunning(id: String): Flow<RunningRelation?>

    @Query("SELECT * FROM execution WHERE sessionId = :id")
    suspend fun get(id: String): ExecutionRow?

    @Upsert suspend fun save(row: ExecutionRow)

    @Query("SELECT * FROM reminder_deliveries") suspend fun deliveries(): List<ReminderDelivery>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun delivered(row: ReminderDelivery): Long

    @Query("DELETE FROM reminder_deliveries WHERE date < :date") suspend fun prune(date: String)
}

object ExecutionCodec {
    fun encode(s: ExecutionState) =
        JSONObject()
            .apply {
                put("v", 1)
                put("id", s.sessionId)
                put("index", s.index)
                put("phase", s.phase.name)
                put("paused", s.pausedFrom.name)
                put("remaining", s.remaining)
                put("deadline", s.deadline)
                put("wallDeadline", s.wallDeadline)
                put("boot", s.boot)
                put("elapsed", s.elapsed)
                put("anchor", s.anchor)
                put("wallAnchor", s.wallAnchor)
                put("revision", s.revision)
                put("alerted", s.alerted)
            }
            .toString()

    fun decode(value: String): ExecutionState {
        val o = JSONObject(value)
        require(o.getInt("v") == 1)
        return ExecutionState(
            o.getString("id"),
            o.getInt("index"),
            Phase.valueOf(o.getString("phase")),
            Phase.valueOf(o.getString("paused")),
            o.getLong("remaining"),
            o.getLong("deadline"),
            o.getLong("wallDeadline"),
            o.getInt("boot"),
            o.getLong("elapsed"),
            o.getLong("anchor"),
            o.getLong("wallAnchor"),
            o.getLong("revision"),
            o.getBoolean("alerted"),
        )
    }
}

data class RunningWorkout(val session: WorkoutSession, val execution: ExecutionState)

class ExecutionStore(private val db: NextSetDatabase) {
    fun observe(id: String): Flow<RunningWorkout?> =
        db.execution().observeRunning(id).map { relation ->
            relation?.let {
                RunningWorkout(it.session.domain(), ExecutionCodec.decode(it.execution.payload))
            }
        }

    suspend fun start(templateId: String, now: ClockReading): String =
        db.withTransaction {
            val active = db.sessions().active()
            if (active != null) {
                ensure(active, now)
                return@withTransaction active.id
            }
            val snapshot =
                requireNotNull(db.workouts().template(templateId)) { "Workout no longer exists." }
                    .domain()
            require(snapshot.validationError() == null) { snapshot.validationError().orEmpty() }
            val id = newId()
            db.sessions()
                .insert(
                    SessionRow(
                        id,
                        TemplateCodec.encode(snapshot),
                        now.wall,
                        null,
                        SessionStatus.ACTIVE.name,
                    )
                )
            save(ExecutionMachine.start(id, now))
            id
        }

    private suspend fun save(state: ExecutionState) {
        db.execution().save(ExecutionRow(state.sessionId, ExecutionCodec.encode(state)))
    }

    private suspend fun ensure(row: SessionRow, now: ClockReading): ExecutionState {
        db.execution().get(row.id)?.let {
            return ExecutionCodec.decode(it.payload)
        }
        val snapshot = TemplateCodec.decode(row.snapshot)
        val completed = db.sessions().session(row.id)!!.sets.map { it.stepIndex }.toSet()
        val plan = snapshot.sequence()
        val next = plan.indices.firstOrNull { plan[it] is WorkoutStep.Set && it !in completed }
        val status = SessionStatus.valueOf(row.status)
        val state =
            ExecutionMachine.start(row.id, now)
                .copy(
                    index = if (status == SessionStatus.ACTIVE) next ?: plan.lastIndex else plan.size,
                    phase =
                        when (status) {
                            SessionStatus.ACTIVE -> Phase.PAUSED
                            SessionStatus.COMPLETED -> Phase.COMPLETED
                            SessionStatus.ABANDONED -> Phase.ABANDONED
                        },
                    pausedFrom = if (next != null) Phase.EXERCISE else Phase.REST_DONE,
                    // Legacy sessions have no monotonic anchor; retain their wall-time duration.
                    elapsed =
                        row.elapsedMillis
                            ?: ((row.endedAt ?: now.wall) - row.startedAt).coerceAtLeast(0),
                )
        save(state)
        return state
    }

    suspend fun active(now: ClockReading): ExecutionState? =
        db.withTransaction { db.sessions().active()?.let { ensure(it, now) } }

    suspend fun read(id: String): ExecutionState? =
        db.execution().get(id)?.let { ExecutionCodec.decode(it.payload) }

    suspend fun command(
        id: String,
        expectedRevision: Long?,
        command: Command,
        now: ClockReading,
    ): ExecutionState =
        db.withTransaction {
            val row = requireNotNull(db.sessions().row(id)) { "Session not found." }
            val original = ensure(row, now)
            // A stale hold/alarm can never affect a newer set or a different countdown.
            if (expectedRevision != null && expectedRevision != original.revision)
                return@withTransaction original
            val change =
                ExecutionMachine.apply(
                    original,
                    TemplateCodec.decode(row.snapshot).sequence(),
                    command,
                    now,
                )
            change.completeSet?.let { db.sessions().insertSet(CompletedSetRow(id, it, now.wall)) }
            change.undoFrom?.let { db.sessions().undo(id, it) }
            save(change.state)
            if (change.state.terminal && row.status == SessionStatus.ACTIVE.name) {
                db.sessions()
                    .update(
                        row.copy(
                            endedAt = now.wall,
                            status =
                                if (change.state.phase == Phase.COMPLETED)
                                    SessionStatus.COMPLETED.name
                                else SessionStatus.ABANDONED.name,
                            elapsedMillis = change.state.elapsed,
                        )
                    )
            }
            change.state
        }

    suspend fun claimAlert(id: String): ExecutionState? =
        db.withTransaction {
            val state = read(id) ?: return@withTransaction null
            if (state.phase != Phase.REST_DONE || state.alerted) return@withTransaction null
            state.copy(alerted = true).also { save(it) }
        }
}
