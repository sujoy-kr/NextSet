package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.domain.weeklyVolume
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun WeeklyAnalysis(state: AppState) {
    var offset by rememberSaveable { mutableStateOf(0L) }
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val start =
        state.today
            .minusDays(((state.today.dayOfWeek.value - first.value + 7) % 7).toLong())
            .plusWeeks(offset)
    val zone = state.zone
    val volumes =
        remember(
            start,
            state.templates,
            state.schedule,
            state.settings.routine,
            state.sessions,
            zone,
        ) {
            weeklyVolume(
                start,
                state.templates,
                state.schedule,
                state.settings.routine,
                state.sessions,
                zone,
            )
        }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Weekly analysis", style = MaterialTheme.typography.titleLarge)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { offset-- }) { Text("Previous") }
                TextButton(onClick = { offset = 0 }) { Text("This week") }
                TextButton(onClick = { offset++ }) { Text("Next") }
            }
            Text("$start – ${start.plusDays(6)}")
            Text(
                "${volumes.sumOf { it.completed }} completed · ${volumes.sumOf { it.planned }} planned sets"
            )
            Text(
                "Planned sets use your current plan. Completed sets include recorded sets from unfinished workouts.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth()) {
                Text("Exercise", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Done / Planned", style = MaterialTheme.typography.labelLarge)
            }
            if (volumes.isEmpty()) Text("No planned or recorded sets this week.")
            volumes.forEach { volume ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(volume.name, Modifier.weight(1f))
                    Text("${volume.completed} / ${volume.planned}")
                }
            }
        }
    }
}
