package com.si13.forgetty

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskRepeatRuleTest {
    private val friday = LocalDate.of(2026, 7, 31)

    @Test
    fun standardRulesCalculateNextLocalDate() {
        assertNull(TaskRepeatRule.NONE.nextDate(friday))
        assertEquals(LocalDate.of(2026, 8, 1), TaskRepeatRule.DAILY.nextDate(friday))
        assertEquals(LocalDate.of(2026, 8, 3), TaskRepeatRule.WEEKDAYS.nextDate(friday))
        assertEquals(LocalDate.of(2026, 8, 7), TaskRepeatRule.WEEKLY.nextDate(friday))
        assertEquals(LocalDate.of(2026, 8, 31), TaskRepeatRule.MONTHLY.nextDate(friday))
        assertEquals(LocalDate.of(2027, 7, 31), TaskRepeatRule.YEARLY.nextDate(friday))
    }

    @Test
    fun monthlyRuleClampsToLastDayOfShortMonth() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            TaskRepeatRule.MONTHLY.nextDate(LocalDate.of(2026, 1, 31))
        )
    }

    @Test
    fun customRulesSupportIntervalsAndSelectedWeekdays() {
        assertEquals(
            LocalDate.of(2026, 8, 3),
            TaskRepeatRule.CUSTOM.nextDate(friday, 1, RepeatUnit.WEEK, listOf(1, 3))
        )
        assertEquals(
            LocalDate.of(2026, 10, 31),
            TaskRepeatRule.CUSTOM.nextDate(friday, 3, RepeatUnit.MONTH)
        )
    }
}
