package com.sujoy.nextset.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sujoy.nextset.data.*
import com.sujoy.nextset.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppState(
    val templates: List<WorkoutTemplate> = emptyList(),
    val schedule: Map<DayOfWeek, String> = emptyMap(),
    val sessions: List<WorkoutSession> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val error: String? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val workouts: WorkoutRepository,
    private val sessions: SessionRepository,
    private val preferences: SettingsRepository,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    private val date = flow {
        while (true) {
            emit(LocalDate.now())
            delay(15_000)
        }
    }
    val state =
        retry
            .flatMapLatest {
                combine(
                        workouts.templates,
                        workouts.schedule,
                        sessions.sessions,
                        preferences.settings,
                        date,
                    ) { templates, schedule, history, settings, today ->
                        AppState(templates, schedule, history, settings, today, loading = false)
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(
                            AppState(
                                loading = false,
                                error = "Couldn't read local data. Retry to reconnect to storage.",
                            )
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    fun retry() {
        retry.value++
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notices.send(e.message ?: "Couldn't save changes. Please try again.")
            }
        }
    }

    fun delete(id: String) = action { workouts.delete(id) }

    fun deleteSession(id: String) = action { sessions.delete(id) }

    fun duplicate(id: String) = action { workouts.duplicate(id) }

    fun reorder(ids: List<String>) = action { workouts.reorder(ids) }

    fun assign(day: DayOfWeek, id: String?) = action { workouts.assign(day, id) }

    fun theme(mode: ThemeMode) = action { preferences.theme(mode) }

    fun defaultRest(seconds: Int) = action { preferences.rest(seconds) }

    fun alertMode(mode: AlertMode) = action { preferences.alertMode(mode) }

    fun setAdvance(mode: AdvanceMode) = action { preferences.setAdvance(mode) }

    fun restAdvance(mode: AdvanceMode) = action { preferences.restAdvance(mode) }

    fun sound(uri: String?) = action { preferences.sound(uri) }

    fun routine(plan: RoutinePlan) = action { preferences.routine(plan) }

    fun reminders(enabled: Boolean) = action { preferences.reminders(enabled) }

    fun reminderMinute(minute: Int?) = action { preferences.reminderMinute(minute) }
}
