package com.sujoy.nextset.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sujoy.nextset.R
import com.sujoy.nextset.data.AlertMode
import com.sujoy.nextset.data.UserSettings
import com.sujoy.nextset.domain.*
import com.sujoy.nextset.notifications.AlarmScheduler
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettings(
    settings: UserSettings,
    sessions: List<WorkoutSession>,
    alarms: AlarmScheduler,
    onEnabled: (Boolean) -> Unit,
    onMinute: (Int?) -> Unit,
    onAlertMode: (AlertMode) -> Unit,
) {
    val context = LocalContext.current
    var notificationsAllowed by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    var settingsError by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    val learned =
        ReminderPolicy.learnedMinute(
            sessions.map { it.startedAt },
            Instant.now(),
            ZoneId.systemDefault(),
        )
    val minute = settings.manualReminderMinute ?: learned
    val label =
        LocalTime.of(minute / 60, minute % 60)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Workout reminders",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            Switch(
                modifier = Modifier.semantics { contentDescription = "Workout reminders" },
                checked = settings.reminders,
                onCheckedChange = { enabled ->
                    onEnabled(enabled)
                    if (
                        enabled &&
                            Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                    )
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }
        Text(
            "A gentle reminder on training days only. Completed scheduled workouts are skipped. Android may deliver reminders later to save battery.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.reminders) {
            Text(
                "${if (settings.manualReminderMinute == null) "Learned time" else "Manual time"} · $label",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Automatic time uses up to 30 starts in the last 60 days, favors recent sessions, and resists unusual starts. With fewer than 3 sessions, it uses 6 PM. Times follow your current timezone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { edit = true }) {
                    Text(stringResource(R.string.stage2_set_a_time))
                }
                if (settings.manualReminderMinute != null)
                    TextButton(onClick = { onMinute(null) }) {
                        Text(stringResource(R.string.stage2_use_learned_time))
                    }
            }
        }
        Text(
            stringResource(R.string.stage2_timer_alerts),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Choose how rest timers and workout reminders alert you. Sound uses alarm volume and plays even in vibrate mode. Follow device settings uses notification volume instead. Do Not Disturb and Android notification settings still apply.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        AlertMode.entries.forEach { mode ->
            val label =
                when (mode) {
                    AlertMode.SOUND_AND_VIBRATION -> "Sound and vibration (default)"
                    AlertMode.SOUND -> "Sound only"
                    AlertMode.VIBRATION -> "Vibration only"
                    AlertMode.SILENT -> "Silent"
                    AlertMode.DEVICE -> "Follow device settings"
                }
            Surface(
                onClick = { onAlertMode(mode) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.alertMode == mode,
                        onClick = null,
                        modifier = Modifier.padding(12.dp),
                    )
                    Text(label, Modifier.padding(vertical = 12.dp))
                }
            }
        }
        if (!notificationsAllowed)
            Text(
                "Notifications are currently blocked. Rest still turns red on screen; background sound and notifications require permission.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        TextButton(
            onClick = {
                try {
                    context.startActivity(
                        if (Build.VERSION.SDK_INT >= 26)
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        else
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${context.packageName}".toUri(),
                            )
                    )
                } catch (_: ActivityNotFoundException) {
                    settingsError = true
                } catch (_: SecurityException) {
                    settingsError = true
                }
            }
        ) {
            Text(stringResource(R.string.stage2_notification_permissions_sound))
        }
        if (settingsError)
            Text(
                "System settings could not be opened. Open NextSet’s App info in your device settings.",
                color = MaterialTheme.colorScheme.error,
            )
        TimerPermissionNotice(alarms)
    }
    if (edit) {
        val time =
            rememberTimePickerState(minute / 60, minute % 60, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text(stringResource(R.string.stage2_daily_reminder_time)) },
            text = { TimeInput(time) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMinute(time.hour * 60 + time.minute)
                        edit = false
                    }
                ) {
                    Text(stringResource(R.string.stage2_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { edit = false }) { Text(stringResource(R.string.ui_cancel)) }
            },
        )
    }
}
