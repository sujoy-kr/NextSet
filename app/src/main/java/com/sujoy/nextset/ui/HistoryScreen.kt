package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun timestamp(at: Long) =
    Instant.ofEpochMilli(at)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

private fun statusLabel(status: SessionStatus) =
    when (status) {
        SessionStatus.ACTIVE -> "In progress"
        SessionStatus.COMPLETED -> "Completed"
        SessionStatus.ABANDONED -> "Abandoned"
    }

@Composable
fun HistoryScreen(sessions: List<WorkoutSession>, state: AppState? = null) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var monthValue by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var dateValue by rememberSaveable { mutableStateOf<String?>(null) }
    var all by rememberSaveable { mutableStateOf(true) }
    val month = YearMonth.parse(monthValue)
    val date = dateValue?.let(LocalDate::parse)
    val zone = ZoneId.systemDefault()
    val grouped = sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
    val filtered =
        sessions.filter { session ->
            val local = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
            if (date != null) local == date else all || YearMonth.from(local) == month
        }
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageTitle(
                "The work you put in",
                "History",
                "Your actual training, independent of the plan.",
            )
        }
        if (state != null) item { WeeklyAnalysis(state) }
        if (sessions.isNotEmpty())
            item {
                HistoryCalendar(
                    month,
                    date,
                    grouped.mapValues { it.value.size },
                    {
                        monthValue = it.toString()
                        dateValue = null
                        all = false
                    },
                    {
                        dateValue = it.toString()
                        all = false
                    },
                    {
                        dateValue = null
                        all = true
                    },
                )
            }
        if (filtered.isEmpty())
            item {
                EmptyState(
                    Icons.Default.History,
                    if (sessions.isEmpty()) "Your story starts here" else "No workouts here",
                    if (sessions.isEmpty())
                        "Recorded sessions will appear here with the sets you completed and the time you spent."
                    else "Choose another date or view all history.",
                )
            }
        filtered.forEach { session ->
            item(key = session.id) {
                OutlinedCard(
                    onClick = { selected = session.id },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(session.snapshot.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            timestamp(session.startedAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${statusLabel(session.status)} · ${session.completedSets.size}/${session.snapshot.totalSets} sets"
                        )
                        if (session.endedAt != null)
                            Text(
                                "${elapsedLabel(session.elapsedMillis)} elapsed",
                                color = MaterialTheme.colorScheme.primary,
                            )
                    }
                }
            }
        }
    }
    sessions
        .find { it.id == selected }
        ?.let { session ->
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text(session.snapshot.name) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text(
                                statusLabel(session.status),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("Started ${timestamp(session.startedAt)}")
                            session.endedAt?.let { Text("Ended ${timestamp(it)}") }
                            Text(
                                "${session.completedSets.size}/${session.snapshot.totalSets} sets completed"
                            )
                            Text(
                                stringResource(
                                    R.string
                                        .ui_workout_details_are_preserved_as_they_were_when_this_session_bega
                                )
                            )
                        }
                        session.snapshot.sequence().forEachIndexed { index, step ->
                            if (step is WorkoutStep.Set)
                                item(key = index) {
                                    val completed =
                                        session.completedSets.any { it.stepIndex == index }
                                    Text(
                                        "${if (completed) "✓" else "○"} ${step.exerciseName} · Set ${step.number}"
                                    )
                                }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selected = null }) {
                        Text(stringResource(R.string.ui_done))
                    }
                },
            )
        }
}
