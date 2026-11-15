package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun HistoryCalendar(
    month: YearMonth,
    selected: LocalDate?,
    counts: Map<LocalDate, Int>,
    onMonth: (YearMonth) -> Unit,
    onDate: (LocalDate) -> Unit,
    onAll: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy")),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
            }
        }
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        Row {
            repeat(7) { offset ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        first
                            .plus(offset.toLong())
                            .getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        val start = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
        val rows = (start + month.lengthOfMonth() + 6) / 7
        repeat(rows) { row ->
            Row {
                repeat(7) { column ->
                    val day = row * 7 + column - start + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        val count = counts[date] ?: 0
                        Surface(
                            onClick = { onDate(date) },
                            modifier =
                                Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                    contentDescription =
                                        date.format(
                                            DateTimeFormatter.ofLocalizedDate(
                                                java.time.format.FormatStyle.FULL
                                            )
                                        ) +
                                            ", " +
                                            if (count == 1) "1 workout" else "$count workouts"
                                    this.selected = selected == date
                                },
                            shape = MaterialTheme.shapes.medium,
                            color =
                                if (selected == date) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(day.toString())
                                if (count > 0)
                                    Text(
                                        "•",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                            }
                        }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
        TextButton(onClick = onAll) { Text(stringResource(R.string.stage2_all_history)) }
    }
}
