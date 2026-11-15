package com.sujoy.nextset.notifications

import com.sujoy.nextset.data.*
import com.sujoy.nextset.domain.*
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ReminderScheduler(
    private val db: NextSetDatabase,
    private val workouts: WorkoutRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val alarms: AlarmScheduler,
    private val notifications: WorkoutNotifications,
) {
    private val mutex = Mutex()

    suspend fun refresh() = mutex.withLock { scheduleNext() }

    private suspend fun scheduleNext() {
        val prefs = settings.settings.first()
        if (!prefs.reminders) {
            alarms.daily(null)
            return
        }
        val now = ZonedDateTime.now()
        val history = sessions.sessions.first()
        val minute =
            prefs.manualReminderMinute
                ?: ReminderPolicy.learnedMinute(
                    history.map { it.startedAt },
                    now.toInstant(),
                    now.zone,
                )
        val delivered = db.execution().deliveries().map { LocalDate.parse(it.date) }.toSet()
        val assignments = workouts.schedule.first()
        val available = workouts.templates.first().map { it.id }.toSet()
        val next =
            ReminderPolicy.next(
                now,
                assignments,
                minute,
                history,
                delivered,
                lookAheadDays = maxOf(7, prefs.routine.entries.size),
                notBefore =
                    if (prefs.routine.mode == PlanMode.SEQUENCE) prefs.routine.startDate
                    else now.toLocalDate(),
            ) {
                prefs.routine.workoutOn(it, assignments)?.takeIf { id -> id in available }
            }
        alarms.daily(
            next?.first?.toInstant()?.toEpochMilli(),
            next?.first?.toLocalDate()?.toString().orEmpty(),
            next?.second.orEmpty(),
        )
        db.execution().prune(now.toLocalDate().minusDays(60).toString())
    }

    suspend fun deliver(dateValue: String, templateId: String) =
        mutex.withLock {
            val now = ZonedDateTime.now()
            val date = runCatching { LocalDate.parse(dateValue) }.getOrNull()
            val settingsNow = settings.settings.first()
            val assignments = workouts.schedule.first()
            val history = sessions.sessions.first()
            val minute =
                settingsNow.manualReminderMinute
                    ?: ReminderPolicy.learnedMinute(
                        history.map { it.startedAt },
                        now.toInstant(),
                        now.zone,
                    )
            val delivered = db.execution().deliveries().map { LocalDate.parse(it.date) }.toSet()
            if (
                settingsNow.reminders &&
                    date != null &&
                    notifications.canNotify() &&
                    ReminderPolicy.shouldDeliver(
                        date,
                        templateId,
                        now,
                        assignments,
                        minute,
                        history,
                        delivered,
                        { settingsNow.routine.workoutOn(it, assignments) },
                    )
            ) {
                val template = workouts.templates.first().find { it.id == templateId }
                if (
                    template != null &&
                        db.execution().delivered(ReminderDelivery(date.toString(), templateId)) !=
                            -1L
                )
                    notifications.reminder(template.name, date.toString())
            }
            scheduleNext()
        }
}
