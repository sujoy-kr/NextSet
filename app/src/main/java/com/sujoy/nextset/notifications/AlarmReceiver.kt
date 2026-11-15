package com.sujoy.nextset.notifications

import android.content.*
import android.util.Log
import com.sujoy.nextset.NextSetApplication
import com.sujoy.nextset.domain.Command
import kotlinx.coroutines.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as NextSetApplication
        app.applicationScope.launch {
            try {
                withTimeout(8_000) {
                    when (intent.action) {
                        AlarmScheduler.REST ->
                            intent.getStringExtra("id")?.let {
                                app.runtime.command(
                                    it,
                                    intent.getLongExtra("revision", -1),
                                    Command.REFRESH,
                                )
                            }
                        AlarmScheduler.DAILY ->
                            app.reminders.deliver(
                                intent.getStringExtra("date").orEmpty(),
                                intent.getStringExtra("template").orEmpty(),
                            )
                        else -> {
                            app.runtime.recover()
                            app.reminders.refresh()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                Log.e("NextSet", "Could not restore scheduled workout alert", e)
            } finally {
                pending.finish()
            }
        }
    }
}
