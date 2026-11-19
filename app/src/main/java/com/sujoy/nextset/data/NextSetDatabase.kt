package com.sujoy.nextset.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "templates")
data class TemplateRow(@PrimaryKey val id: String, val name: String, val position: Int)

@Entity(
    tableName = "blocks",
    foreignKeys =
        [
            ForeignKey(
                entity = TemplateRow::class,
                parentColumns = ["id"],
                childColumns = ["templateId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("templateId")],
)
data class BlockRow(
    @PrimaryKey val id: String,
    val templateId: String,
    val position: Int,
    val superset: Boolean,
    val betweenSets: Int,
    val betweenMovements: Int,
    val betweenRounds: Int,
    val afterBlock: Int,
)

@Entity(
    tableName = "exercises",
    foreignKeys =
        [
            ForeignKey(
                entity = BlockRow::class,
                parentColumns = ["id"],
                childColumns = ["blockId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("blockId")],
)
data class ExerciseRow(
    @PrimaryKey val id: String,
    val blockId: String,
    val position: Int,
    val name: String,
    val rests: String,
)

@Entity(
    tableName = "schedule",
    foreignKeys =
        [
            ForeignKey(
                entity = TemplateRow::class,
                parentColumns = ["id"],
                childColumns = ["templateId"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices = [Index("templateId")],
)
data class ScheduleRow(@PrimaryKey val day: Int, val templateId: String?)

// Deliberately no foreign key to templates: history must survive template deletion.
@Entity(tableName = "sessions", indices = [Index("startedAt")])
data class SessionRow(
    @PrimaryKey val id: String,
    val snapshot: String,
    val startedAt: Long,
    val endedAt: Long?,
    val status: String,
    val elapsedMillis: Long? = null,
)

@Entity(
    tableName = "completed_sets",
    primaryKeys = ["sessionId", "stepIndex"],
    foreignKeys =
        [
            ForeignKey(
                entity = SessionRow::class,
                parentColumns = ["id"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("sessionId")],
)
data class CompletedSetRow(val sessionId: String, val stepIndex: Int, val completedAt: Long)

@Entity(
    tableName = "completed_rests",
    primaryKeys = ["sessionId", "stepIndex"],
    foreignKeys =
        [
            ForeignKey(
                entity = SessionRow::class,
                parentColumns = ["id"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("sessionId")],
)
data class CompletedRestRow(val sessionId: String, val stepIndex: Int, val endedAt: Long)

data class BlockRelation(
    @Embedded val block: BlockRow,
    @Relation(parentColumn = "id", entityColumn = "blockId") val exercises: List<ExerciseRow>,
)

data class TemplateRelation(
    @Embedded val template: TemplateRow,
    @Relation(entity = BlockRow::class, parentColumn = "id", entityColumn = "templateId")
    val blocks: List<BlockRelation>,
)

data class SessionRelation(
    @Embedded val session: SessionRow,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val sets: List<CompletedSetRow>,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val rests: List<CompletedRestRow>,
)

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM templates ORDER BY position, id")
    fun observeTemplates(): Flow<List<TemplateRelation>>

    @Transaction
    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun template(id: String): TemplateRelation?

    @Query("SELECT * FROM templates ORDER BY position, id")
    suspend fun templateRows(): List<TemplateRow>

    @Insert suspend fun insertBlocks(rows: List<BlockRow>)

    @Insert suspend fun insertExercises(rows: List<ExerciseRow>)

    @Upsert suspend fun upsertTemplate(row: TemplateRow)

    @Query("DELETE FROM blocks WHERE templateId = :id") suspend fun clearBlocks(id: String)

    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: String)

    @Query("SELECT * FROM schedule") fun observeSchedule(): Flow<List<ScheduleRow>>

    @Upsert suspend fun assign(row: ScheduleRow)
}

/** A lightweight reminder invalidation signal; individual set writes do not change it. */
data class SessionSignal(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val status: String,
)

@Dao
interface SessionDao {
    @Query("SELECT id, startedAt, endedAt, status FROM sessions ORDER BY startedAt DESC")
    fun observeReminderChanges(): Flow<List<SessionSignal>>

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<SessionRelation>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: String): SessionRelation?

    @Query("SELECT * FROM sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun active(): SessionRow?

    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun row(id: String): SessionRow?

    @Upsert suspend fun update(row: SessionRow)

    @Query("DELETE FROM completed_sets WHERE sessionId = :id AND stepIndex >= :index")
    suspend fun undo(id: String, index: Int)

    @Query("DELETE FROM completed_rests WHERE sessionId = :id AND stepIndex >= :index")
    suspend fun undoRests(id: String, index: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRest(row: CompletedRestRow)

    @Query("DELETE FROM sessions WHERE id = :id AND status != 'ACTIVE'")
    suspend fun deleteFinished(id: String): Int

    @Insert suspend fun insert(row: SessionRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSet(row: CompletedSetRow)

    @Query(
        "UPDATE sessions SET endedAt = :at, status = :status WHERE id = :id AND status = 'ACTIVE'"
    )
    suspend fun finish(id: String, status: String, at: Long)
}

@Database(
    entities =
        [
            TemplateRow::class,
            BlockRow::class,
            ExerciseRow::class,
            ScheduleRow::class,
            SessionRow::class,
            CompletedSetRow::class,
            CompletedRestRow::class,
            ExecutionRow::class,
            ReminderDelivery::class,
        ],
    version = 4,
    exportSchema = true,
)
abstract class NextSetDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao

    abstract fun sessions(): SessionDao

    abstract fun execution(): ExecutionDao
}

/** First upgrade adds a history index without altering or dropping any user records. */
val MIGRATION_1_2 =
    object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sessions_startedAt ON sessions (startedAt)"
            )
        }
    }

val MIGRATION_2_3 =
    object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN elapsedMillis INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS execution (sessionId TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS reminder_deliveries (date TEXT NOT NULL PRIMARY KEY, templateId TEXT NOT NULL)"
            )
        }
    }

/** Rest timings are new; existing sessions simply have none. */
val MIGRATION_3_4 =
    object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS completed_rests (sessionId TEXT NOT NULL, stepIndex INTEGER NOT NULL, endedAt INTEGER NOT NULL, PRIMARY KEY(sessionId, stepIndex), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_completed_rests_sessionId ON completed_rests (sessionId)"
            )
        }
    }
