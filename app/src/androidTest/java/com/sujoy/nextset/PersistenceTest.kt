package com.sujoy.nextset

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sujoy.nextset.data.*
import com.sujoy.nextset.domain.*
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var db: NextSetDatabase
    private lateinit var workouts: LocalWorkoutRepository
    private lateinit var sessions: LocalSessionRepository

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    NextSetDatabase::class.java,
                )
                .build()
        workouts = LocalWorkoutRepository(db)
        sessions = LocalSessionRepository(db)
    }

    @After
    fun close() {
        db.close()
    }

    private fun template(name: String = "Push") =
        WorkoutTemplate(
            name = name,
            blocks =
                listOf(
                    WorkoutBlock(
                        exercises = listOf(Exercise(name = "Press", rests = listOf(null, 0, 35)))
                    )
                ),
        )

    @Test
    fun editingKeepsScheduleAndHistorySurvivesDeletion() = runBlocking {
        val original = template()
        workouts.save(original)
        workouts.assign(DayOfWeek.SATURDAY, original.id)
        val id = sessions.begin(original.id, 1_000)
        sessions.recordSet(id, 0, 2_000)
        sessions.recordSet(id, 0, 2_500) // idempotent retry
        workouts.save(
            original.copy(
                name = "Edited",
                blocks =
                    original.blocks.map { b ->
                        b.copy(exercises = b.exercises.map { it.copy(name = "Different") })
                    },
            )
        )
        assertEquals(original.id, workouts.schedule.first()[DayOfWeek.SATURDAY])
        sessions.finish(id, false, 61_000)
        workouts.delete(original.id)
        assertTrue(workouts.templates.first().isEmpty())
        assertTrue(workouts.schedule.first().isEmpty())
        val history = sessions.sessions.first().single()
        assertEquals(original, history.snapshot)
        assertEquals(1, history.completedSets.size)
        assertEquals(60_000L, history.elapsedMillis)
        assertEquals(SessionStatus.ABANDONED, history.status)
    }

    @Test
    fun duplicateHasIndependentIdsAndOrderPersists() = runBlocking {
        val a = template()
        val b = template("Pull")
        workouts.save(a)
        workouts.save(b)
        workouts.duplicate(a.id)
        val all = workouts.templates.first()
        val duplicate = all.last()
        assertEquals("Push (copy)", duplicate.name)
        assertNotEquals(a.blocks.first().id, duplicate.blocks.first().id)
        assertNotEquals(
            a.blocks.first().exercises.first().id,
            duplicate.blocks.first().exercises.first().id,
        )
        workouts.reorder(all.reversed().map { it.id })
        assertEquals(all.reversed(), workouts.templates.first())
        assertEquals(a, TemplateCodec.decode(TemplateCodec.encode(a)))
    }

    @Test
    fun completedSessionsRequireActualSetsAndValidateStepIndices() = runBlocking {
        val t = template()
        workouts.save(t)
        val id = sessions.begin(t.id, 100)
        expectInvalid { sessions.begin(t.id, 200) }
        expectInvalid { sessions.recordSet(id, 1, 200) } // rest step
        expectInvalid { sessions.recordSet(id, 0, 99) }
        expectInvalid { sessions.finish(id, true, 300) }
        t.sequence().forEachIndexed { index, step ->
            if (step is WorkoutStep.Set) sessions.recordSet(id, index, 200)
        }
        sessions.finish(id, true, 500)
        sessions.finish(id, false, 600) // retries cannot change a terminal status
        assertEquals(SessionStatus.COMPLETED, sessions.sessions.first().single().status)
        expectInvalid { sessions.recordSet(id, 0, 700) }
    }

    @Test
    fun failedSavesLeaveOriginalAndAssignmentsIntact() = runBlocking {
        val t = template()
        workouts.save(t)
        workouts.assign(DayOfWeek.MONDAY, t.id)
        expectInvalid { workouts.save(t.copy(name = "")) }
        expectInvalid { workouts.assign(DayOfWeek.MONDAY, "missing") }
        assertEquals(t, workouts.templates.first().single())
        assertEquals(t.id, workouts.schedule.first()[DayOfWeek.MONDAY])
    }

    @Test
    fun deletionThenAppendKeepsNewWorkoutAtEnd() = runBlocking {
        val a = template("A")
        val b = template("B")
        val c = template("C")
        val d = template("D")
        listOf(a, b, c).forEach { workouts.save(it) }
        workouts.delete(b.id)
        workouts.save(d)
        assertEquals(listOf(a.id, c.id, d.id), workouts.templates.first().map { it.id })
    }

    @Test
    fun diskDatabaseSurvivesCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        val original = template()
        val first =
            Room.databaseBuilder(context, NextSetDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
        try {
            LocalWorkoutRepository(first).save(original)
            LocalWorkoutRepository(first).assign(DayOfWeek.FRIDAY, original.id)
        } finally {
            first.close()
        }
        val reopened =
            Room.databaseBuilder(context, NextSetDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
        try {
            assertEquals(original, LocalWorkoutRepository(reopened).templates.first().single())
            assertEquals(
                original.id,
                LocalWorkoutRepository(reopened).schedule.first()[DayOfWeek.FRIDAY],
            )
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun expectInvalid(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected invalid operation to be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun collidingChildIdsRollbackWithoutDeletingAnotherTemplate() = runBlocking {
        val original = template()
        workouts.save(original)
        val clone = original.copy(id = newId(), name = "Bad clone")
        try {
            workouts.save(clone)
            fail("Expected ID collision")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {}
        assertEquals(listOf(original), workouts.templates.first())
    }
}
