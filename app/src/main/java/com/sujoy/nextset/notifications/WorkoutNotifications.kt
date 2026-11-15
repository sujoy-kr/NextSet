package com.sujoy.nextset.notifications

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.sujoy.nextset.MainActivity
import com.sujoy.nextset.R

class WorkoutNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val restSound = "android.resource://${context.packageName}/raw/rest_complete".toUri()

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                        REST_CHANNEL,
                        "Rest complete",
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                    .apply {
                        description = "Alerts when your workout rest timer finishes"
                        setSound(
                            restSound,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .build(),
                        )
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 180, 100, 300)
                        setBypassDnd(false)
                    }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    DAILY_CHANNEL,
                    "Workout reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
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

    fun rest(id: String, revision: Long) {
        if (!canNotify()) return
        try {
            manager.notify(
                "rest:$id:$revision",
                REST_ID,
                NotificationCompat.Builder(context, REST_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Rest complete")
                    .setContentText("Hold to acknowledge your next set in NextSet.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setDefaults(
                        NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS
                    )
                    .setSound(restSound)
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

    fun reminder(name: String, date: String) {
        if (!canNotify()) return
        try {
            manager.notify(
                "day:$date",
                DAILY_ID,
                NotificationCompat.Builder(context, DAILY_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Time for $name")
                    .setContentText("Your plan is ready. Train when it works for you.")
                    .setContentIntent(launch())
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .build(),
            )
        } catch (_: SecurityException) {}
    }

    companion object {
        const val REST_CHANNEL = "rest_complete_v1"
        const val DAILY_CHANNEL = "daily_workout_v1"
        private const val REST_ID = 10
        private const val DAILY_ID = 20
    }
}
