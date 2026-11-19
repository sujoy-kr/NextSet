package com.sujoy.nextset.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sujoy.nextset.R
import com.sujoy.nextset.domain.*
import com.sujoy.nextset.notifications.AlarmScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreen(
    template: WorkoutTemplate?,
    alarms: AlarmScheduler,
    hasActive: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onStart: () -> Unit,
    sessions: List<com.sujoy.nextset.domain.WorkoutSession> = emptyList(),
) {
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onStart() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_workout_ready)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (template != null)
                Button(
                    enabled = !busy,
                    onClick = {
                        if (
                            Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                        )
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else onStart()
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(20.dp)
                            .heightIn(min = 54.dp),
                ) {
                    Text(
                        if (busy) "Opening…"
                        else if (hasActive) "Resume current workout" else "Start workout"
                    )
                }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (template == null)
                item {
                    Text(
                        stringResource(
                            R.string
                                .stage2_workout_unavailable_return_to_your_library_to_choose_another_w
                        )
                    )
                }
            else {
                item {
                    PageTitle(
                        "Your session plan",
                        template.name,
                        workoutSummary(template, sessions),
                    )
                    Text(
                        if (template.expectedDuration(sessions).fromHistory)
                            "Average of your completed sessions of this workout."
                        else "Estimate includes all saved rests and ${DEFAULT_SECONDS_PER_SET}s per set. After you complete this workout, this shows your average time."
                    )
                    Text(
                        "Complete each set and acknowledge the finished rest with a tap or a one-second hold, as chosen in Settings. Rest finishes with an alert and waits for your acknowledgment.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Sound and background alerts require notification permission. You can still train if you decline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasActive)
                        Text(
                            "An unfinished session is saved. Resume or end it before starting another workout.",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    TextButton(onClick = { onEdit(template.id) }) {
                        Text(stringResource(R.string.ui_edit_workout))
                    }
                }
                item { TimerPermissionNotice(alarms) }
                template.sequence().forEachIndexed { index, step ->
                    item(key = index) {
                        when (step) {
                            is WorkoutStep.Set ->
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(18.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            step.exerciseName,
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text("Set ${step.number}")
                                    }
                                }
                            is WorkoutStep.Rest ->
                                Text(
                                    "Rest · ${restLabel(step.seconds)}",
                                    Modifier.padding(start = 18.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimerPermissionNotice(alarms: AlarmScheduler) {
    val context = LocalContext.current
    var settingsError by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf(alarms.exactAllowed()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { exact = alarms.exactAllowed() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            exact = alarms.exactAllowed()
        }
    if (settingsError)
        Text(
            "System settings could not be opened. Check Alarms & reminders in your device settings.",
            color = MaterialTheme.colorScheme.error,
        )
    if (!exact) {
        Text(
            "For precise rest alerts while locked or in the background, allow Alarms & reminders. Without access, Android may delay background alerts. The on-screen countdown stays accurate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                runCatching { launcher.launch(alarms.requestExactIntent()) }
                    .onFailure {
                        settingsError = true
                        exact = alarms.exactAllowed()
                    }
            }
        ) {
            Text(stringResource(R.string.stage2_allow_precise_timer_alerts))
        }
    }
}
