package com.sujoy.nextset.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, defaultRest: Int, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    LaunchedEffect(state.error) {
        if (state.error != null && state.draft != null) listState.animateScrollToItem(0)
    }
    var discard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (state.dirty) discard = true else onBack() }
    BackHandler { if (!state.saving) leave() }
    LaunchedEffect(vm) { vm.saved.collect { onBack() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_workout_editor)) },
                navigationIcon = {
                    IconButton(onClick = leave, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            focus.clearFocus()
                            vm.save()
                        },
                        enabled = state.draft != null && !state.saving,
                    ) {
                        Text(if (state.saving) "Saving…" else "Save")
                    }
                },
            )
        }
    ) { padding ->
        val draft = state.draft
        if (draft == null) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                if (state.error != null) {
                    Text(state.error!!)
                    Button(onClick = vm::load) { Text(stringResource(R.string.ui_retry)) }
                } else CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.ui_make_it_yours),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        draft.name,
                        { vm.update(draft.copy(name = it.take(100))) },
                        label = { Text(stringResource(R.string.ui_workout_name)) },
                        placeholder = {
                            Text(stringResource(R.string.ui_e_g_push_pull_a_full_body))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.saving,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${workoutSummary(draft)}. Rest includes the final set of each block.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                itemsIndexed(draft.blocks, key = { _, b -> b.id }) { index, block ->
                    BlockEditor(
                        block,
                        index,
                        draft.blocks.size,
                        onChange = { updated ->
                            vm.update(
                                draft.copy(
                                    blocks =
                                        draft.blocks.map { if (it.id == block.id) updated else it }
                                )
                            )
                        },
                        onMove = { to ->
                            vm.update(draft.copy(blocks = draft.blocks.moved(index, to)))
                        },
                        onRemove = {
                            vm.update(
                                draft.copy(blocks = draft.blocks.filterNot { it.id == block.id })
                            )
                        },
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                vm.update(
                                    draft.copy(
                                        blocks =
                                            draft.blocks +
                                                WorkoutBlock(
                                                    betweenSets = defaultRest,
                                                    afterBlock = defaultRest,
                                                )
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving,
                        ) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.ui_add_exercise))
                        }
                        OutlinedButton(
                            onClick = {
                                vm.update(
                                    draft.copy(
                                        blocks =
                                            draft.blocks +
                                                WorkoutBlock(
                                                    exercises = listOf(Exercise(), Exercise()),
                                                    superset = true,
                                                    betweenRounds = defaultRest,
                                                    afterBlock = defaultRest,
                                                )
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.saving,
                        ) {
                            Icon(Icons.Default.Link, null)
                            Text(stringResource(R.string.ui_add_superset))
                        }
                        Text(
                            stringResource(
                                R.string
                                    .ui_supersets_alternate_movements_each_round_each_movement_can_have_i
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.ui_discard_changes)) },
            text = { Text(stringResource(R.string.ui_your_unsaved_workout_changes_will_be_lost)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.ui_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) {
                    Text(stringResource(R.string.ui_keep_editing))
                }
            },
        )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockEditor(
    block: WorkoutBlock,
    index: Int,
    count: Int,
    onChange: (WorkoutBlock) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var delete by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${index + 1}. ${if (block.superset) "Superset" else "Exercise"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                MoveButtons(index, count, "block ${index + 1}", onMove)
                IconButton(onClick = { delete = true }) {
                    Icon(Icons.Default.DeleteOutline, "Remove block ${index + 1}")
                }
            }
            block.exercises.forEachIndexed { movement, exercise ->
                key(exercise.id) {
                    ExerciseEditor(
                        exercise,
                        block,
                        movement,
                        onChange = { e ->
                            onChange(
                                block.copy(
                                    exercises = block.exercises.map { if (it.id == e.id) e else it }
                                )
                            )
                        },
                        onMove = { to ->
                            onChange(block.copy(exercises = block.exercises.moved(movement, to)))
                        },
                        onRemove =
                            if (block.superset)
                                ({
                                    val remaining =
                                        block.exercises.filterNot { it.id == exercise.id }
                                    onChange(
                                        block.copy(
                                            exercises = remaining,
                                            superset = remaining.size > 1,
                                        )
                                    )
                                })
                            else null,
                    )
                }
            }
            if (block.superset)
                TextButton(
                    onClick = { onChange(block.copy(exercises = block.exercises + Exercise())) }
                ) {
                    Text(stringResource(R.string.ui_add_movement))
                }
            Text(
                stringResource(R.string.ui_block_rest_defaults),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (block.superset) {
                    RestSetting("Between movements", block.betweenMovements) {
                        onChange(block.copy(betweenMovements = it))
                    }
                    RestSetting("Between rounds", block.betweenRounds) {
                        onChange(block.copy(betweenRounds = it))
                    }
                } else
                    RestSetting("Between sets", block.betweenSets) {
                        onChange(block.copy(betweenSets = it))
                    }
                RestSetting(
                    if (block.superset) "After superset" else "After exercise",
                    block.afterBlock,
                ) {
                    onChange(block.copy(afterBlock = it))
                }
            }
        }
    }
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Remove this ${if (block.superset) "superset" else "exercise"}?") },
            text = { Text(stringResource(R.string.ui_you_can_add_it_again_before_saving)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        delete = false
                        onRemove()
                    }
                ) {
                    Text(stringResource(R.string.ui_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { delete = false }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEditor(
    exercise: Exercise,
    block: WorkoutBlock,
    index: Int,
    onChange: (Exercise) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var restIndex by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (block.superset)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Movement ${index + 1}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                MoveButtons(index, block.exercises.size, "movement ${index + 1}", onMove)
                if (onRemove != null)
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, "Remove ${exercise.name.ifBlank { "movement" }}")
                    }
            }
        OutlinedTextField(
            exercise.name,
            { onChange(exercise.copy(name = it.take(100))) },
            label = { Text(stringResource(R.string.ui_exercise_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ui_sets),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = { onChange(exercise.copy(rests = exercise.rests.dropLast(1))) },
                enabled = exercise.sets > 1,
            ) {
                Icon(Icons.Default.Remove, "Fewer sets for ${exercise.name}")
            }
            Text("${exercise.sets}", style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { onChange(exercise.copy(rests = exercise.rests + listOf<Int?>(null))) },
                enabled = exercise.sets < 100,
            ) {
                Icon(Icons.Default.Add, "More sets for ${exercise.name}")
            }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide individual rest" else "Customize rest for each set")
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (expanded) {
            Text(
                stringResource(
                    R.string.ui_overrides_replace_the_block_rest_immediately_after_this_set_inclu
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.rests.forEachIndexed { i, seconds ->
                    OutlinedButton(onClick = { restIndex = i }) {
                        Text("Set ${i + 1} · ${seconds?.let { restLabel(it) } ?: "Default"}")
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }
    restIndex
        ?.takeIf { it in exercise.rests.indices }
        ?.let { i ->
            DurationDialog(
                "After set ${i + 1}",
                exercise.rests[i]
                    ?: (if (i == exercise.sets - 1) block.afterBlock
                    else if (block.superset) block.betweenMovements else block.betweenSets),
                allowDefault = true,
                onDismiss = { restIndex = null },
                onDefault = {
                    onChange(
                        exercise.copy(
                            rests =
                                exercise.rests.mapIndexed { k, old -> if (k == i) null else old }
                        )
                    )
                    restIndex = null
                },
                onSave = { value ->
                    onChange(
                        exercise.copy(
                            rests =
                                exercise.rests.mapIndexed { k, old -> if (k == i) value else old }
                        )
                    )
                    restIndex = null
                },
            )
        }
}
