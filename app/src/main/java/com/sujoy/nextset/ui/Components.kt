package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.expectedDuration

@Composable
fun PageTitle(kicker: String, title: String, subtitle: String? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            kicker.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.headlineLarge)
        if (subtitle != null)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(icon, contentDescription = null, Modifier.padding(22.dp).size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun MoveButtons(index: Int, count: Int, label: String, onMove: (Int) -> Unit) {
    if (count <= 1) return
    Row {
        IconButton(onClick = { onMove(index - 1) }, enabled = index > 0) {
            Icon(Icons.Default.KeyboardArrowUp, "Move $label up")
        }
        IconButton(onClick = { onMove(index + 1) }, enabled = index < count - 1) {
            Icon(Icons.Default.KeyboardArrowDown, "Move $label down")
        }
    }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> =
    toMutableList().apply { add(to, removeAt(from)) }

@Composable
fun ConfirmDelete(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $name?") },
        text = {
            Text(
                stringResource(
                    R.string.ui_this_also_clears_its_weekly_assignments_your_workout_history_stay
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}

@Composable
fun DurationDialog(
    title: String,
    current: Int,
    allowDefault: Boolean = false,
    onDismiss: () -> Unit,
    onDefault: () -> Unit = {},
    minimumSeconds: Int = 0,
    onSave: (Int) -> Unit,
) {
    var minutes by rememberSaveable { mutableStateOf((current / 60).toString()) }
    var seconds by rememberSaveable { mutableStateOf((current % 60).toString()) }
    val min = minutes.toIntOrNull()
    val sec = seconds.toIntOrNull()
    val total =
        if (min != null && sec != null && min >= 0 && sec in 0..59) min.toLong() * 60 + sec else -1L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (minimumSeconds > 0) "Enter the estimated active time for one set."
                    else stringResource(R.string.rest_duration_help)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        minutes,
                        { if (it.length <= 8 && it.all(Char::isDigit)) minutes = it },
                        label = { Text(stringResource(R.string.ui_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        seconds,
                        { if (it.length <= 2 && it.all(Char::isDigit)) seconds = it },
                        label = { Text(stringResource(R.string.ui_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (total !in minimumSeconds.toLong()..Int.MAX_VALUE.toLong())
                    Text(
                        if (minimumSeconds > 0)
                            "Enter a duration of at least $minimumSeconds second, with seconds between 0 and 59."
                        else stringResource(R.string.rest_duration_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                if (allowDefault)
                    TextButton(onClick = onDefault) {
                        Text(stringResource(R.string.ui_use_block_default))
                    }
            }
        },
        confirmButton = {
            TextButton(
                enabled = total in minimumSeconds.toLong()..Int.MAX_VALUE.toLong(),
                onClick = { onSave(total.toInt()) },
            ) {
                Text(stringResource(R.string.ui_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}

@Composable
fun RestSetting(label: String, seconds: Int, minimumSeconds: Int = 0, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label · ${restLabel(seconds)}") }
    if (open)
        DurationDialog(
            label,
            seconds,
            onDismiss = { open = false },
            minimumSeconds = minimumSeconds,
            onSave = {
                onChange(it)
                open = false
            },
        )
}

@Composable
fun workoutSummary(
    workout: com.sujoy.nextset.domain.WorkoutTemplate,
    sessions: List<com.sujoy.nextset.domain.WorkoutSession> = emptyList(),
): String =
    androidx.compose.ui.res.pluralStringResource(
        R.plurals.exercise_count,
        workout.exerciseCount,
        workout.exerciseCount,
    ) +
        " · " +
        androidx.compose.ui.res.pluralStringResource(
            R.plurals.set_count,
            workout.totalSets,
            workout.totalSets,
        ) +
        // Drafts being edited may be invalid (e.g. unnamed), and sequence() rejects those.
        if (workout.validationError() != null) ""
        else
            workout.expectedDuration(sessions).let {
                " · ~${elapsedLabel(it.seconds * 1000)}" + if (it.fromHistory) " avg" else ""
            }
