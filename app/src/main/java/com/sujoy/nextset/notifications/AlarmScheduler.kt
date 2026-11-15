package com.sujoy.nextset.notifications

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.net.toUri
import com.sujoy.nextset.domain.*

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    private fun timerIntent() = Intent(context, AlarmReceiver::class.java).setAction(REST)

    private fun timerPending(id: String = "", revision: Long = 0) =
        PendingIntent.getBroadcast(
            context,
            1,
            timerIntent().putExtra("id", id).putExtra("revision", revision),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun timer(state: ExecutionState) {
        val pending = timerPending(state.sessionId, state.revision)
        manager.cancel(pending)
        if (state.phase != Phase.REST) return
        // Exact access is optional and user controlled. No permission bypass or wake-lock loop.
        try {
            if (exactAllowed())
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    state.deadline,
                    pending,
                )
            else
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    state.deadline,
                    pending,
                )
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                state.deadline,
                pending,
            )
        }
    }

    fun cancelTimer() {
        manager.cancel(timerPending())
    }

    fun daily(at: Long?, date: String = "", templateId: String = "") {
        val pending =
            PendingIntent.getBroadcast(
                context,
                2,
                Intent(context, AlarmReceiver::class.java)
                    .setAction(DAILY)
                    .putExtra("date", date)
                    .putExtra("template", templateId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        manager.cancel(pending)
        if (at != null) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    fun requestExactIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 31)
            Intent(
                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                "package:${context.packageName}".toUri(),
            )
        else
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            )

    companion object {
        const val REST = "com.sujoy.nextset.REST"
        const val DAILY = "com.sujoy.nextset.DAILY"
    }
}
