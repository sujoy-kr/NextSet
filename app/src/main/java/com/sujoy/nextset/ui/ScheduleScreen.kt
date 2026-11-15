package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun ScheduleScreen(state: AppState, onAssign: (DayOfWeek, String?) -> Unit, onCreate: () -> Unit) {
    var editing by remember { mutableStateOf<DayOfWeek?>(null) }
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                "A steady rhythm",
                "Your week",
                "A repeating plan, with room to be human. Missing a day never changes your schedule.",
            )
        }
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
