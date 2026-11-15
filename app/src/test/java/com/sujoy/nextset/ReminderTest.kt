package com.sujoy.nextset

import com.sujoy.nextset.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReminderTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val now = ZonedDateTime.of(2026, 11, 15, 10, 0, 0, 0, zone)

    private fun start(daysAgo: Long, hour: Int, minute: Int = 0) =
        now.minusDays(daysAgo).withHour(hour).withMinute(minute).toInstant().toEpochMilli()

    @Test
    fun insufficientHistoryUsesTransparentDefault() {
        assertEquals(
            1080,
            ReminderPolicy.learnedMinute(listOf(start(1, 7), start(2, 7)), now.toInstant(), zone),
        )
    }

    @Test
    fun circularLearningHandlesMidnightAndSingleUnusualStart() {
        val learned =
            ReminderPolicy.learnedMinute(
                listOf(start(1, 23, 55), start(2, 0, 5), start(3, 23, 58), start(4, 12)),
                now.toInstant(),
                zone,
            )
        assertTrue(learned >= 23 * 60 || learned <= 10)
    }

    @Test
    fun oneRecentOutlierDoesNotOverrideTwoOlderSimilarStarts() {
        val learned =
            ReminderPolicy.learnedMinute(
                listOf(start(1, 12), start(50, 18), start(51, 18, 5)),
                now.toInstant(),
                zone,
            )
        assertTrue(learned in 1080..1085)
    }

    @Test
    fun oldAndFutureHistoryIsExcluded() {
        assertEquals(
            1080,
            ReminderPolicy.learnedMinute(
                listOf(start(90, 7), start(80, 7), start(70, 7), start(-1, 7)),
                now.toInstant(),
                zone,
            ),
        )
    }

    @Test
    fun restDaysAreSkippedAndManualTimeIsUsedExactly() {
        val next =
            ReminderPolicy.next(
                now,
                mapOf(DayOfWeek.TUESDAY to "push"),
                7 * 60 + 15,
                emptyList(),
                emptySet(),
            )!!
        assertEquals(DayOfWeek.TUESDAY, next.first.dayOfWeek)
        assertEquals(7, next.first.hour)
        assertEquals(15, next.first.minute)
    }

    @Test
    fun completedScheduledWorkoutSuppressesTodayButManualWorkoutDoesNotChangeSchedule() {
        val template = WorkoutTemplate(id = "push", name = "Push")
        val session =
            WorkoutSession(
                "s",
                template,
                start(0, 8),
                start(0, 9),
                SessionStatus.COMPLETED,
                emptyList(),
            )
        val schedule = mapOf(DayOfWeek.SUNDAY to "push")
        assertEquals(
            now.toLocalDate().plusDays(7),
            ReminderPolicy.next(now, schedule, 1080, listOf(session), emptySet())!!
                .first
                .toLocalDate(),
        )
        val different = session.copy(snapshot = template.copy(id = "pull"))
        assertEquals(
            now.toLocalDate(),
            ReminderPolicy.next(now, schedule, 1080, listOf(different), emptySet())!!
                .first
                .toLocalDate(),
        )
        assertEquals("push", schedule[DayOfWeek.SUNDAY])
    }

    @Test
    fun deliveryLogSuppressesDuplicateReminder() {
        assertEquals(
            now.toLocalDate().plusDays(7),
            ReminderPolicy.next(
                    now,
                    mapOf(DayOfWeek.SUNDAY to "push"),
                    1080,
                    emptyList(),
                    setOf(now.toLocalDate()),
                )!!
                .first
                .toLocalDate(),
        )
    }

    @Test
    fun pastReminderIsNotInventedAfterRestartAndDstGapIsResolved() {
        assertEquals(
            now.toLocalDate().plusDays(7),
            ReminderPolicy.next(
                    now,
                    mapOf(DayOfWeek.SUNDAY to "push"),
                    9 * 60,
                    emptyList(),
                    emptySet(),
                )!!
                .first
                .toLocalDate(),
        )
        val dst = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ZoneId.of("America/New_York"))
        val next =
            ReminderPolicy.next(
                dst,
                mapOf(DayOfWeek.SUNDAY to "push"),
                150,
                emptyList(),
                emptySet(),
            )!!
        assertEquals(3, next.first.hour)
        assertEquals(30, next.first.minute)
    }

    @Test
    fun deliveryRechecksChangedTimeAssignmentRestDayAndDuplicates() {
        val date = now.toLocalDate()
        val atSix = now.withHour(18)
        val assignment = mapOf(DayOfWeek.SUNDAY to "push")
        assertTrue(
            ReminderPolicy.shouldDeliver(
                date,
                "push",
                atSix,
                assignment,
                1080,
                emptyList(),
                emptySet(),
            )
        )
        assertFalse(
            ReminderPolicy.shouldDeliver(
                date,
                "push",
                atSix,
                assignment,
                1200,
                emptyList(),
                emptySet(),
            )
        )
        assertFalse(
            ReminderPolicy.shouldDeliver(
                date,
                "pull",
                atSix,
                assignment,
                1080,
                emptyList(),
                emptySet(),
            )
        )
        assertFalse(
            ReminderPolicy.shouldDeliver(
                date,
                "push",
                atSix,
                emptyMap(),
                1080,
                emptyList(),
                emptySet(),
            )
        )
        assertFalse(
            ReminderPolicy.shouldDeliver(
                date,
                "push",
                atSix,
                assignment,
                1080,
                emptyList(),
                setOf(date),
            )
        )
        assertFalse(
            ReminderPolicy.shouldDeliver(
                date.minusDays(1),
                "push",
                atSix,
                assignment,
                1080,
                emptyList(),
                emptySet(),
            )
        )
    }
}
