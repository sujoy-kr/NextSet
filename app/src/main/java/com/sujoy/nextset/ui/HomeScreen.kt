package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HomeScreen(
    state: AppState,
    onCreate: () -> Unit,
    onSchedule: () -> Unit,
    onPrepare: (String) -> Unit,
    onResume: (String) -> Unit,
) {
    var choose by remember { mutableStateOf(false) }
    val today =
        state.templates.find {
            it.id == state.settings.routine.workoutOn(state.today, state.schedule)
        }
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PageTitle(
                state.today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                "Your next set.",
                "A little structure. A stronger routine.",
            )
        }
        state.sessions
            .firstOrNull { it.status == SessionStatus.ACTIVE }
            ?.let { active ->
                item {
                    OutlinedCard(
                        onClick = { onResume(active.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "Pick up where you left off",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${active.snapshot.name} · ${active.completedSets.size} sets completed"
                            )
                            Text(
                                "Resume workout",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        if (today == null) Icons.Default.Spa else Icons.Default.FitnessCenter,
                        null,
                        Modifier.size(32.dp),
                    )
                    Text(
                        if (today == null) "ROOM TO RECOVER" else "TODAY’S WORKOUT",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(today?.name ?: "Rest day", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        today?.let {
                            "${workoutSummary(it, state.sessions)}\nMove at your own pace."
                        }
                            ?: "Take a breath. Recovery is part of the work. You can still choose a workout whenever you’re ready.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = { if (state.templates.isEmpty()) onCreate() else choose = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(
                            if (state.templates.isEmpty()) Icons.Default.Add
                            else Icons.Default.PlayArrow,
                            null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.templates.isEmpty()) "Create your first workout"
                            else "Start workout"
                        )
                    }
                }
            }
        }
        if (state.templates.isEmpty())
            item {
                Text(
                    stringResource(R.string.ui_make_it_yours_34),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string
                            .ui_build_a_workout_with_your_exercises_sets_and_rest_then_give_it_a_
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${state.templates.size}", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.ui_saved_workouts),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column {
                    Text(
                        "${state.sessions.count { it.status == SessionStatus.COMPLETED }}",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        stringResource(R.string.ui_sessions_completed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            HorizontalDivider()
            TextButton(onClick = onSchedule, contentPadding = PaddingValues(vertical = 16.dp)) {
                Icon(Icons.Default.CalendarMonth, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (state.settings.routine.mode == PlanMode.SEQUENCE) "Edit repeating sequence"
                    else
                        "Shape your week · ${pluralStringResource(R.plurals.training_day_count,state.schedule.size,state.schedule.size)}"
                )
            }
            Text(
                stringResource(R.string.ui_offline_on_your_terms),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (choose)
        WorkoutPicker(
            state,
            today?.id,
            onDismiss = { choose = false },
            onChoose = {
                choose = false
                onPrepare(it)
            },
        )
}

@Composable
private fun WorkoutPicker(
    state: AppState,
    todayId: String?,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_choose_your_workout)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        stringResource(
                            R.string
                                .ui_train_today_s_plan_or_pick_any_saved_workout_your_weekly_schedule
                        )
                    )
                }
                state.templates
                    .sortedBy { if (it.id == todayId) 0 else 1 }
                    .forEach { template ->
                        item(key = template.id) {
                            OutlinedCard(
                                onClick = { onChoose(template.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    if (template.id == todayId)
                                        Text(
                                            stringResource(R.string.ui_today_s_plan),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    Text(
                                        template.name,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(workoutSummary(template, state.sessions))
                                }
                            }
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}
