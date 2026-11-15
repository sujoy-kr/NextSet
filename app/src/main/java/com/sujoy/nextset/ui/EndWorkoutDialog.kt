package com.sujoy.nextset.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sujoy.nextset.R

/** Scroll the whole confirmation so large titles cannot squeeze its explanation out of view. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EndWorkoutDialog(onDismiss: () -> Unit, onEnd: () -> Unit, onHome: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.stage2_end_this_workout),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Your completed sets will be saved as an abandoned session. To leave it running, return Home instead.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onHome) { Text(stringResource(R.string.home)) }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.stage2_keep_training))
                    }
                    TextButton(onClick = onEnd) {
                        Text(
                            stringResource(R.string.stage2_end_workout),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
