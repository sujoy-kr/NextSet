package com.sujoy.nextset.notifications

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.sujoy.nextset.MainActivity
import com.sujoy.nextset.R
import com.sujoy.nextset.data.AlertMode
import com.sujoy.nextset.data.SettingsRepository
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

class WorkoutNotifications(private val context: Context, private val settings: SettingsRepository) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val restSound = "android.resource://${context.packageName}/raw/rest_complete".toUri()

    // Channel sound/vibration cannot be changed after creation. Reuse a stable channel per
    // alert mode and selected tone so preference changes work while preserving Android overrides.
    private fun channel(rest: Boolean, mode: AlertMode, selectedSound: String?): String {
        val soundKey =
            selectedSound?.let { uri ->
                MessageDigest.getInstance("SHA-256")
                    .digest(uri.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            }
        val id =
            "${if (rest) REST_CHANNEL else DAILY_CHANNEL}_${mode.name.lowercase(java.util.Locale.ROOT)}" +
                (soundKey?.let { "_$it" } ?: "")
        if (Build.VERSION.SDK_INT >= 26) {
            val title = if (rest) "Rest complete" else "Workout reminders"
            val modeLabel =
                when (mode) {
                    AlertMode.SOUND_AND_VIBRATION -> "Sound and vibration"
                    AlertMode.SOUND -> "Sound only"
                    AlertMode.VIBRATION -> "Vibration only"
                    AlertMode.SILENT -> "Silent"
                    AlertMode.DEVICE -> "Device settings"
                }
            manager.createNotificationChannel(
                NotificationChannel(
                        id,
                        "$title · $modeLabel",
                        if (rest) NotificationManager.IMPORTANCE_HIGH
                        else NotificationManager.IMPORTANCE_DEFAULT,
                    )
                    .apply {
                        description =
                            if (rest) "Alerts when your workout rest timer finishes"
                            else "Reminders on scheduled training days"
                        setSound(
                            if (mode.sound) sound(rest, selectedSound) else null,
                            AudioAttributes.Builder()
                                .setUsage(
                                    if (mode == AlertMode.DEVICE) AudioAttributes.USAGE_NOTIFICATION
                                    else AudioAttributes.USAGE_ALARM
                                )
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        enableVibration(mode.vibrate)
                        if (mode.vibrate) vibrationPattern = VIBRATION_PATTERN
                        setBypassDnd(false)
                    }
            )
        }
        return id
    }

    private fun sound(rest: Boolean, selectedSound: String?) =
        selectedSound?.toUri() ?: if (rest) restSound else Settings.System.DEFAULT_NOTIFICATION_URI

    @Suppress("DEPRECATION")
    private fun NotificationCompat.Builder.alert(
        rest: Boolean,
        mode: AlertMode,
        selectedSound: String?,
    ) = apply {
        // Android 7 uses the notification itself rather than a channel.
        if (Build.VERSION.SDK_INT < 26) {
            setSound(
                if (mode.sound) sound(rest, selectedSound) else null,
                if (mode == AlertMode.DEVICE) AudioManager.STREAM_NOTIFICATION
                else AudioManager.STREAM_ALARM,
            )
            setVibrate(if (mode.vibrate) VIBRATION_PATTERN else longArrayOf(0))
        }
    }

    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun launch(sessionId: String? = null) =
        PendingIntent.getActivity(
            context,
            if (sessionId == null) 2 else 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (sessionId == null) putExtra("open_home", true)
                else putExtra("session", sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    suspend fun rest(id: String, revision: Long) {
        if (!canNotify()) return
        val prefs = settings.settings.first()
        val mode = prefs.alertMode
        val channel = channel(rest = true, mode, prefs.soundUri)
        try {
            manager.notify(
                "rest:$id:$revision",
                REST_ID,
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Rest complete")
                    .setContentText("Hold to acknowledge your next set in NextSet.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .alert(rest = true, mode, prefs.soundUri)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(launch(id))
                    .setAutoCancel(true)
                    .build(),
            )
        } catch (_: SecurityException) {
            /* Permission may be revoked between the check and notify. */
        }
    }

    fun clearRest(id: String, revision: Long) {
        manager.cancel("rest:$id:$revision", REST_ID)
    }

    suspend fun reminder(name: String, date: String) {
        if (!canNotify()) return
        val prefs = settings.settings.first()
        val mode = prefs.alertMode
        val channel = channel(rest = false, mode, prefs.soundUri)
        try {
            manager.notify(
                "day:$date",
                DAILY_ID,
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Time for $name")
                    .setContentText("Your plan is ready. Train when it works for you.")
                    .setContentIntent(launch())
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .alert(rest = false, mode, prefs.soundUri)
                    .build(),
            )
        } catch (_: SecurityException) {}
    }

    companion object {
        const val REST_CHANNEL = "rest_complete_v2"
        const val DAILY_CHANNEL = "daily_workout_v2"
        private val VIBRATION_PATTERN = longArrayOf(0, 180, 100, 300)
        private const val REST_ID = 10
        private const val DAILY_ID = 20
    }
}
