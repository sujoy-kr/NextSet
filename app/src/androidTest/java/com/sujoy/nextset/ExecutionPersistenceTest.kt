package com.sujoy.nextset

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sujoy.nextset.data.*
import com.sujoy.nextset.domain.*
import java.time.DayOfWeek
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionPersistenceTest {
    private lateinit var db: NextSetDatabase
    private lateinit var store: ExecutionStore
    private lateinit var workouts: LocalWorkoutRepository
    private val now = ClockReading(1_000, 1_000_000, 1)

    private fun clock(delta: Long) =
        now.copy(elapsed = now.elapsed + delta, wall = now.wall + delta)

    private fun template(name: String = "Push") =
        WorkoutTemplate(
            name = name,
            blocks =
                listOf(
                    WorkoutBlock(
                        exercises = listOf(Exercise(name = "Press", rests = listOf(10, 0)))
                    )
                ),
        )

    @Before
    fun open() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    NextSetDatabase::class.java,
                )
                .build()
        store = ExecutionStore(db)
        workouts = LocalWorkoutRepository(db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun staleAndConcurrentHoldsNeverDuplicateOrSkipSets() = runBlocking {
        val template = template()
        workouts.save(template)
        val id = store.start(template.id, now)
        coroutineScope { repeat(10) { launch { store.command(id, 0, Command.HOLD, now) } } }
        val state = store.read(id)!!
        assertEquals(Phase.REST, state.phase)
        assertEquals(1, state.index)
        assertEquals(1, LocalSessionRepository(db).sessions.first().single().completedSets.size)
        store.command(id, 0, Command.HOLD, clock(20_000)) // stale gesture after timer expiry
        assertEquals(1, store.read(id)!!.index)
        store.command(id, state.revision, Command.REFRESH, clock(20_000))
        val done = store.read(id)!!
        assertEquals(Phase.REST_DONE, done.phase)
        assertNotNull(store.claimAlert(id))
        assertNull(store.claimAlert(id))
        store.command(id, done.revision, Command.HOLD, clock(21_000))
        val second = store.read(id)!!
        store.command(id, done.revision, Command.HOLD, clock(21_001))
        assertEquals(second, store.read(id))
        store.command(id, second.revision, Command.HOLD, clock(22_000))
        val history = LocalSessionRepository(db).sessions.first().single()
        assertEquals(SessionStatus.COMPLETED, history.status)
        assertEquals(2, history.completedSets.size)
        assertEquals(22_000L, history.elapsedMillis)
    }

    @Test
    fun undoDeletesCompletedRecordAndReplayCreatesExactlyOne() = runBlocking {
        val template = template()
        workouts.save(template)
        val id = store.start(template.id, now)
        val rest = store.command(id, 0, Command.HOLD, now)
        val ready = store.command(id, rest.revision, Command.UNDO, clock(1_000))
        assertTrue(LocalSessionRepository(db).sessions.first().single().completedSets.isEmpty())
        store.command(id, ready.revision, Command.HOLD, clock(2_000))
        assertEquals(1, LocalSessionRepository(db).sessions.first().single().completedSets.size)
    }

    @Test
    fun activeSessionOwnsSnapshotAndMultipleStartAttemptsResumeIt() = runBlocking {
        val original = template()
        val other = template("Pull")
        workouts.save(original)
        workouts.save(other)
        val id = store.start(original.id, now)
        assertEquals(id, store.start(other.id, clock(1_000)))
        workouts.save(original.copy(name = "Renamed"))
        workouts.delete(original.id)
        assertEquals(original, store.observe(id).first()!!.session.snapshot)
        store.command(id, 0, Command.END, clock(2_000))
        val next = store.start(other.id, clock(3_000))
        assertNotEquals(id, next)
        assertEquals(
            SessionStatus.ABANDONED,
            LocalSessionRepository(db).sessions.first().first { it.id == id }.status,
        )
    }

    @Test
    fun manualMissedWorkoutDoesNotRewriteSchedule() = runBlocking {
        val scheduled = template("Scheduled")
        val actual = template("Missed workout")
        workouts.save(scheduled)
        workouts.save(actual)
        workouts.assign(DayOfWeek.MONDAY, scheduled.id)
        val id = store.start(actual.id, now)
        store.command(id, 0, Command.END, clock(1_000))
        assertEquals(actual.id, LocalSessionRepository(db).sessions.first().single().snapshot.id)
        assertEquals(scheduled.id, workouts.schedule.first()[DayOfWeek.MONDAY])
    }

    @Test
    fun processRestartRetainsDeadlinePauseAndAlertClaim() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "execution-recovery-test.db"
        context.deleteDatabase(name)
        fun database() =
            Room.databaseBuilder(context, NextSetDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        val first = database()
        val template = template()
        val id: String
        try {
            LocalWorkoutRepository(first).save(template)
            val engine = ExecutionStore(first)
            id = engine.start(template.id, now)
            engine.command(id, 0, Command.HOLD, now)
        } finally {
            first.close()
        }
        val second = database()
        try {
            val engine = ExecutionStore(second)
            val recovered = engine.command(id, null, Command.REFRESH, clock(20_000))
            assertEquals(Phase.REST_DONE, recovered.phase)
            assertEquals(0L, recovered.remaining)
            assertNotNull(engine.claimAlert(id))
            assertNull(engine.claimAlert(id))
            assertEquals(
                recovered.revision,
                ExecutionCodec.decode(ExecutionCodec.encode(recovered)).revision,
            )
            val paused = engine.command(id, recovered.revision, Command.PAUSE, clock(21_000))
            assertEquals(Phase.PAUSED, paused.phase)
        } finally {
            second.close()
        }
        val third = database()
        try {
            val engine = ExecutionStore(third)
            val recovered =
                engine.command(id, null, Command.REFRESH, ClockReading(30, now.wall + 60_000, 2))
            assertEquals(Phase.PAUSED, recovered.phase)
            assertTrue(recovered.alerted)
            assertEquals(
                Phase.REST_DONE,
                engine
                    .command(
                        id,
                        recovered.revision,
                        Command.RESUME,
                        ClockReading(40, now.wall + 60_010, 2),
                    )
                    .phase,
            )
        } finally {
            third.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun completedStateAndCompletedSetsAreObservedAtomically() = runBlocking {
        val t =
            WorkoutTemplate(
                name = "Quick",
                blocks =
                    listOf(
                        WorkoutBlock(exercises = listOf(Exercise(name = "A", rests = listOf(0))))
                    ),
            )
        workouts.save(t)
        val id = store.start(t.id, now)
        val observations = mutableListOf<RunningWorkout>()
        val ready = CompletableDeferred<Unit>()
        val collector = launch {
            store.observe(id).collect {
                it?.let(observations::add)
                ready.complete(Unit)
            }
        }
        ready.await()
        store.command(id, 0, Command.HOLD, clock(1_000))
        withTimeout(5_000) {
            while (observations.none { it.execution.phase == Phase.COMPLETED }) delay(10)
        }
        collector.cancelAndJoin()
        val terminal = store.observe(id).first()!!
        assertEquals(1, terminal.session.completedSets.size)
        assertTrue(
            observations.none {
                it.execution.phase == Phase.COMPLETED && it.session.completedSets.size != 1
            }
        )
    }

    @Test
    fun legacyActiveSessionRetainsElapsedTimeWhenCheckpointIsCreated() = runBlocking {
        val template = template()
        workouts.save(template)
        val sessions = LocalSessionRepository(db)
        val id = sessions.begin(template.id, now.wall)
        sessions.recordSet(id, 0, clock(1_000).wall)

        val recovered = store.active(clock(60_000))!!
        assertEquals(Phase.PAUSED, recovered.phase)
        assertEquals(2, recovered.index)
        assertEquals(60_000L, recovered.elapsed)

        store.command(id, recovered.revision, Command.END, clock(65_000))
        assertEquals(65_000L, sessions.sessions.first().single().elapsedMillis)
    }

    @Test
    fun legacyEndedSessionsStayTerminalAndTheirSetsRemainImmutable() = runBlocking {
        val template = template()
        workouts.save(template)
        val sessions = LocalSessionRepository(db)
        for (completed in listOf(false, true)) {
            val id = sessions.begin(template.id, now.wall)
            sessions.recordSet(id, 0, clock(1_000).wall)
            if (completed) sessions.recordSet(id, 2, clock(2_000).wall)
            sessions.finish(id, completed, clock(3_000).wall)
            val original = sessions.sessions.first().first { it.id == id }

            val recovered = store.command(id, null, Command.RESUME, clock(60_000))
            assertEquals(if (completed) Phase.COMPLETED else Phase.ABANDONED, recovered.phase)
            assertEquals(3_000L, recovered.elapsed)
            for (command in listOf(Command.UNDO, Command.HOLD, Command.END)) {
                assertEquals(recovered, store.command(id, null, command, clock(65_000)))
            }
            assertEquals(original, sessions.sessions.first().first { it.id == id })
        }
    }

    @Test
    fun legacyRepositoryCannotDesynchronizeExecutionCheckpoint() = runBlocking {
        val t = template()
        workouts.save(t)
        val id = store.start(t.id, now)
        try {
            LocalSessionRepository(db).recordSet(id, 0, now.wall)
            fail("Must use engine")
        } catch (_: IllegalArgumentException) {}
        try {
            LocalSessionRepository(db).finish(id, false, now.wall)
            fail("Must use engine")
        } catch (_: IllegalArgumentException) {}
        assertEquals(Phase.EXERCISE, store.read(id)!!.phase)
        assertTrue(store.observe(id).first()!!.session.completedSets.isEmpty())
    }
}
