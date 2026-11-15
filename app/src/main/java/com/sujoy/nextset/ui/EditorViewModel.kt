package com.sujoy.nextset.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sujoy.nextset.data.TemplateCodec
import com.sujoy.nextset.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditorState(
    val draft: WorkoutTemplate? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val dirty: Boolean = false,
)

class EditorViewModel(
    private val repository: WorkoutRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val mutable = MutableStateFlow(EditorState())
    val state = mutable.asStateFlow()
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()
    private var baseline: WorkoutTemplate? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val id = handle.get<String>("id") ?: "new"
                baseline =
                    if (id == "new") WorkoutTemplate()
                    else repository.templates.first().find { it.id == id }
                requireNotNull(baseline) { "This workout no longer exists." }
                val draft =
                    handle.get<String>("draft")?.let { TemplateCodec.decode(it, validate = false) }
                        ?: baseline
                mutable.value = EditorState(draft, dirty = draft != baseline)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = EditorState(error = e.message ?: "Couldn't load workout.")
            }
        }
    }

    fun update(draft: WorkoutTemplate) {
        if (state.value.saving) return
        handle["draft"] = TemplateCodec.encode(draft)
        mutable.value = state.value.copy(draft = draft, dirty = draft != baseline, error = null)
    }

    fun save() {
        val draft = state.value.draft ?: return
        if (state.value.saving) return
        val error = draft.validationError()
        if (error != null) {
            mutable.value = state.value.copy(error = error)
            return
        }
        mutable.value = state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                repository.save(draft)
                savedChannel.send(Unit)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value =
                    state.value.copy(
                        saving = false,
                        error = e.message ?: "Couldn't save workout. Try again.",
                    )
            }
        }
    }
}
