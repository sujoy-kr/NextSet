package com.sujoy.nextset.domain

import java.time.*
import kotlin.math.*

object ReminderPolicy {
    const val DEFAULT_MINUTE = 18 * 60

    /** Weighted circular medoid: recent starts matter more; one outlier cannot pull a mean. */
    fun learnedMinute(starts: List<Long>, now: Instant, zone: ZoneId): Int {
        val samples =
            starts
                .distinct()
                .filter {
                    it <= now.toEpochMilli() && it >= now.minus(Duration.ofDays(60)).toEpochMilli()
                }
                .sortedDescending()
                .take(30)
        if (samples.size < 3) return DEFAULT_MINUTE
        val weighted =
            samples.map { at ->
                val time = Instant.ofEpochMilli(at).atZone(zone)
                (time.hour * 60 + time.minute) to
                    (1.0 +
                        exp(
                            -Duration.between(Instant.ofEpochMilli(at), now).toMillis().toDouble() /
                                Duration.ofDays(14).toMillis()
                        ))
            }
        return weighted
            .minBy { candidate ->
                weighted.sumOf { (minute, weight) ->
                    val distance = abs(candidate.first - minute)
                    min(distance, 1440 - distance) * weight
                }
            }
            .first
    }

    fun completedToday(sessions: List<WorkoutSession>, id: String, date: LocalDate, zone: ZoneId) =
        sessions.any {
            it.snapshot.id == id &&
                it.status == SessionStatus.COMPLETED &&
                it.endedAt?.let { end ->
                    Instant.ofEpochMilli(end).atZone(zone).toLocalDate() == date
                } == true
        }

    fun shouldDeliver(
        date: LocalDate,
        templateId: String,
        now: ZonedDateTime,
        assignments: Map<DayOfWeek, String>,
        minute: Int,
        sessions: List<WorkoutSession>,
        delivered: Set<LocalDate>,
        workoutOn: (LocalDate) -> String? = { assignments[it.dayOfWeek] },
    ): Boolean {
        val due = date.atTime(minute / 60, minute % 60).atZone(now.zone)
        return date == now.toLocalDate() &&
            !now.isBefore(due) &&
            workoutOn(date) == templateId &&
            date !in delivered &&
            sessions.none { it.status == SessionStatus.ACTIVE } &&
            !completedToday(sessions, templateId, date, now.zone)
    }

    fun next(
        now: ZonedDateTime,
        assignments: Map<DayOfWeek, String>,
        minute: Int,
        sessions: List<WorkoutSession>,
        delivered: Set<LocalDate>,
        lookAheadDays: Int = 7,
        notBefore: LocalDate = now.toLocalDate(),
        workoutOn: (LocalDate) -> String? = { assignments[it.dayOfWeek] },
    ): Pair<ZonedDateTime, String>? {
        require(minute in 0..1439)
        require(lookAheadDays >= 0)
        val firstDate = maxOf(now.toLocalDate(), notBefore)
        return (0L..lookAheadDays.toLong()).firstNotNullOfOrNull { offset ->
            val date = firstDate.plusDays(offset)
            val id = workoutOn(date)
            // atZone resolves DST gaps forward and chooses the first occurrence in overlaps.
            val time = date.atTime(minute / 60, minute % 60).atZone(now.zone)
            if (
                id != null &&
                    date !in delivered &&
                    time.isAfter(now) &&
                    !completedToday(sessions, id, date, now.zone)
            )
                time to id
            else null
        }
    }
}
