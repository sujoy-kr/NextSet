package com.sujoy.nextset.notifications

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.sujoy.nextset.data.*
import com.sujoy.nextset.domain.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidClock(private val context: Context) {
    private val boot =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    fun now() = ClockReading(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot)
}

/** Serializes DB transitions and OS side effects, including competing UI and alarm callbacks. */
class SessionRuntime(
    val store: ExecutionStore,
    val alarms: AlarmScheduler,
    val notifications: WorkoutNotifications,
    val clock: AndroidClock,
) {
    private val mutex = Mutex()

    suspend fun start(templateId: String): String =
        mutex.withLock {
            withContext(NonCancellable) {
                store.start(templateId, clock.now()).also { id ->
                    sync(store.command(id, null, Command.REFRESH, clock.now()))
                }
            }
        }

    suspend fun command(id: String, revision: Long?, command: Command): ExecutionState =
        mutex.withLock {
            // Once accepted, finish the DB/OS transition even if its screen is removed.
            withContext(NonCancellable) {
                val before = store.read(id)
                val state = store.command(id, revision, command, clock.now())
                if (
                    before?.phase == Phase.REST_DONE &&
                        (state.phase != Phase.REST_DONE || state.revision != before.revision)
                )
                    notifications.clearRest(id, before.revision)
                sync(state)
                state
            }
        }

    private suspend fun sync(state: ExecutionState) {
        // Terminal sessions must not cancel a different session's countdown.
        val active = store.active(clock.now())
        if (active?.sessionId == state.sessionId) alarms.timer(state)
        else if (active == null) alarms.cancelTimer()
        store.claimAlert(state.sessionId)?.let { notifications.rest(it.sessionId, it.revision) }
    }

    suspend fun recover() =
        mutex.withLock {
            withContext(NonCancellable) {
                store.active(clock.now())?.let {
                    sync(store.command(it.sessionId, null, Command.REFRESH, clock.now()))
                } ?: alarms.cancelTimer()
            }
        }
}
