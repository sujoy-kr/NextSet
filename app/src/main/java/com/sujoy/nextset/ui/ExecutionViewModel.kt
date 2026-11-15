package com.sujoy.nextset.ui

import androidx.lifecycle.*
import com.sujoy.nextset.data.RunningWorkout
import com.sujoy.nextset.domain.*
import com.sujoy.nextset.notifications.SessionRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class ExecutionUi(val workout: RunningWorkout? = null, val error: String? = null)

class ExecutionViewModel(private val runtime: SessionRuntime, private val id: String) :
    ViewModel() {
    private val retry = MutableStateFlow(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state =
        retry
            .flatMapLatest {
                runtime.store
                    .observe(id)
                    .map {
                        if (it == null)
                            ExecutionUi(
                                error = "Session not found. Return Home to choose a workout."
                            )
                        else ExecutionUi(it)
                    }
                    .catch {
                        emit(
                            ExecutionUi(
                                error =
                                    "Couldn't read this session. Retry to reconnect to local storage."
                            )
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExecutionUi())
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()

    fun retry() {
        retry.value++
        refresh()
    }

    fun refresh() = action(null, Command.REFRESH)

    fun action(revision: Long?, command: Command) {
        viewModelScope.launch {
            try {
                runtime.command(id, revision, command)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                events.send(e.message ?: "Couldn't save your progress. Please try again.")
            }
        }
    }
}
