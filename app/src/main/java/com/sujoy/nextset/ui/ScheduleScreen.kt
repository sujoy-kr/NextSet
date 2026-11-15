package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.*
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun ScheduleScreen(
    state: AppState,
    onAssign: (DayOfWeek, String?) -> Unit,
    onCreate: () -> Unit,
    onRoutine: (RoutinePlan) -> Unit,
) {
    var editing by remember { mutableStateOf<DayOfWeek?>(null) }
    var sequenceIndex by remember { mutableStateOf<Int?>(null) }
    val plan = state.settings.routine
    val context = LocalContext.current
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                "A steady rhythm",
                "Your week",
                "Choose fixed weekdays or a daily sequence that repeats across week boundaries.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = plan.mode == PlanMode.WEEKDAYS,
                    onClick = { onRoutine(plan.copy(mode = PlanMode.WEEKDAYS)) },
                    label = { Text("Weekdays") },
                )
                FilterChip(
                    selected = plan.mode == PlanMode.SEQUENCE,
                    onClick = { onRoutine(plan.copy(mode = PlanMode.SEQUENCE)) },
                    label = { Text("Repeating sequence") },
                )
            }
        }
        if (plan.mode == PlanMode.SEQUENCE) {
            item {
                Text(
                    "One entry per day, including rest. The sequence repeats without resetting each week. Missed workouts do not shift it."
                )
                if (plan.entries.isNotEmpty()) {
                    Text(
                        "Day 1 starts ${plan.startDate}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = {
                            android.app
                                .DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        onRoutine(
                                            plan.copy(
                                                startDate =
                                                    java.time.LocalDate.of(year, month + 1, day)
                                            )
                                        )
                                    },
                                    plan.startDate.year,
                                    plan.startDate.monthValue - 1,
                                    plan.startDate.dayOfMonth,
                                )
                                .show()
                        }
                    ) {
                        Text("Change start date")
                    }
                    TextButton(onClick = { onRoutine(plan.copy(startDate = state.today)) }) {
                        Text("Start day 1 today")
                    }
                }
            }
            plan.entries.forEachIndexed { index, id ->
                item(key = "sequence:$index") {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            TextButton(onClick = { sequenceIndex = index }) {
                                Text(
                                    "Day ${index + 1}${if (plan.indexOn(state.today) == index) " · Today" else ""} · " +
                                        (state.templates.find { it.id == id }?.name
                                            ?: if (id == null) "Rest day"
                                            else "Workout deleted · choose another")
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MoveButtons(index, plan.entries.size, "Day ${index + 1}") { to ->
                                    onRoutine(plan.copy(entries = plan.entries.moved(index, to)))
                                }
                                IconButton(
                                    onClick = {
                                        onRoutine(
                                            plan.copy(
                                                entries =
                                                    plan.entries.filterIndexed { i, _ ->
                                                        i != index
                                                    }
                                            )
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, "Remove day ${index + 1}")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { sequenceIndex = plan.entries.size }) {
                    Text("Add workout or rest day")
                }
            }
            if (plan.entries.isNotEmpty())
                item {
                    Text("Next 7 days", style = MaterialTheme.typography.titleMedium)
                    repeat(7) { offset ->
                        val date = state.today.plusDays(offset.toLong())
                        val id = plan.workoutOn(date, state.schedule)
                        Text("$date · ${state.templates.find { it.id == id }?.name ?: "Rest day"}")
                    }
                }
        }
        if (plan.mode == PlanMode.WEEKDAYS)
            repeat(7) { offset ->
                val day = first.plus(offset.toLong())
                item(key = day.value) {
                    val today = day == state.today.dayOfWeek
                    val template = state.templates.find { it.id == state.schedule[day] }
                    OutlinedCard(
                        onClick = { editing = day },
                        colors =
                            CardDefaults.outlinedCardColors(
                                containerColor =
                                    if (today) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface
                            ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    day.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                        if (today) " · Today" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    template?.name ?: "Rest day",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (template == null) Icons.Default.Add else Icons.Default.Edit,
                                "Change assignment",
                            )
                        }
                    }
                }
            }
        if (state.templates.isEmpty())
            item {
                TextButton(onClick = onCreate) {
                    Text(stringResource(R.string.ui_create_a_workout_to_assign))
                }
            }
    }
    sequenceIndex?.let { index ->
        AlertDialog(
            onDismissRequest = { sequenceIndex = null },
            title = { Text("Day ${index + 1}") },
            text = {
                LazyColumn {
                    fun choose(id: String?) {
                        val entries = plan.entries.toMutableList()
                        if (index == entries.size) entries.add(id)
                        else if (index in entries.indices) entries[index] = id
                        onRoutine(
                            plan.copy(
                                entries = entries,
                                startDate =
                                    if (plan.entries.isEmpty()) state.today else plan.startDate,
                            )
                        )
                        sequenceIndex = null
                    }
                    item { TextButton(onClick = { choose(null) }) { Text("Rest day") } }
                    state.templates.forEach { template ->
                        item(key = template.id) {
                            TextButton(onClick = { choose(template.id) }) { Text(template.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sequenceIndex = null }) { Text("Cancel") } },
        )
    }
    editing?.let { day ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
            text = {
                LazyColumn {
                    item {
                        TextButton(
                            onClick = {
                                onAssign(day, null)
                                editing = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ui_rest_day))
                        }
                    }
                    state.templates.forEach { template ->
                        item(key = template.id) {
                            TextButton(
                                onClick = {
                                    onAssign(day, template.id)
                                    editing = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(template.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
        )
    }
}
