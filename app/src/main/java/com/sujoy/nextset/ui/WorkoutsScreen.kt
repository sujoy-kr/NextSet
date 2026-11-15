package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.WorkoutTemplate

@Composable
fun WorkoutsScreen(
    templates: List<WorkoutTemplate>,
    onEdit: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onPrepare: (String) -> Unit,
) {
    var deletion by remember { mutableStateOf<WorkoutTemplate?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PageTitle("Your library", "Workouts", "Built around the way you train.") }
        item {
            Button(onClick = { onEdit("new") }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_new_workout))
            }
        }
        if (templates.isEmpty())
            item {
                EmptyState(
                    Icons.Default.FitnessCenter,
                    "A fresh start",
                    "Your workouts will live here. Start with a few favorite exercises.",
                )
            }
        itemsIndexed(templates, key = { _, t -> t.id }) { index, template ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                workoutSummary(template),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoveButtons(index, templates.size, template.name) { to ->
                            onReorder(templates.moved(index, to).map { it.id })
                        }
                    }
                    Text(
                        template.blocks.joinToString(" · ") {
                            it.exercises.joinToString(" + ") { e -> e.name }
                        },
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPrepare(template.id) }) {
                            Text(stringResource(R.string.ui_start))
                        }
                        TextButton(onClick = { onEdit(template.id) }) {
                            Text(stringResource(R.string.ui_edit))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onDuplicate(template.id) }) {
                            Icon(Icons.Default.ContentCopy, "Duplicate ${template.name}")
                        }
                        IconButton(onClick = { deletion = template }) {
                            Icon(Icons.Default.DeleteOutline, "Delete ${template.name}")
                        }
                    }
                }
            }
        }
    }
    deletion?.let { template ->
        ConfirmDelete(template.name, { deletion = null }) {
            onDelete(template.id)
            deletion = null
        }
    }
}
