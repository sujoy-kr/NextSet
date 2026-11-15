package com.sujoy.nextset

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.sujoy.nextset.data.*
import com.sujoy.nextset.notifications.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class NextSetApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val requestedHome = MutableStateFlow(false)
    val requestedSession = MutableStateFlow<String?>(null)
    val database by lazy {
        Room.databaseBuilder(this, NextSetDatabase::class.java, "nextset.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
    val workouts by lazy { LocalWorkoutRepository(database) }
    val sessions by lazy { LocalSessionRepository(database) }
    val settings by lazy { SettingsRepository(this) }
    val execution by lazy { ExecutionStore(database) }
    val alarms by lazy { AlarmScheduler(this) }
    val notifications by lazy { WorkoutNotifications(this) }
    val runtime by lazy { SessionRuntime(execution, alarms, notifications, AndroidClock(this)) }
    val reminders by lazy {
        ReminderScheduler(database, workouts, sessions, settings, alarms, notifications)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // Retry transient storage failures without replacing or discarding local data.
            while (isActive) {
                try {
                    runtime.recover()
                    combine(
                            workouts.templates,
                            workouts.schedule,
                            database.sessions().observeReminderChanges(),
                            settings.settings,
                        ) { _, _, _, _ ->
                            Unit
                        }
                        .collect { reminders.refresh() }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("NextSet", "Could not refresh local reminders", e)
                    delay(30_000)
                }
            }
        }
    }
}
