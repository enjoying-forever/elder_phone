package com.kantu.phone

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CallAnnouncementTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun displayUsesNumericDateAndTwentyFourHourTime() {
        assertEquals("2026-09-09 15:05", formatCallDate(at(2026, 9, 9, 15, 5), utc))
        assertEquals("时间未知", formatCallDate(0, utc))
    }

    @Test fun describesTodayWithNaturalChineseTime() {
        assertEquals(
            "今天，上午十点三十分",
            describeCallTime(at(2026, 9, 9, 10, 30), at(2026, 9, 9, 20, 0), utc),
        )
    }

    @Test fun describesRecentCalendarDays() {
        val now = at(2026, 9, 9, 12, 0)
        assertEquals("昨天，晚上十一点三十分", describeCallTime(at(2026, 9, 8, 23, 30), now, utc))
        assertEquals("前天，下午三点", describeCallTime(at(2026, 9, 7, 15, 0), now, utc))
        assertEquals("大前天，上午九点零五分", describeCallTime(at(2026, 9, 6, 9, 5), now, utc))
        assertEquals("五天前，凌晨十二点零一分", describeCallTime(at(2026, 9, 4, 0, 1), now, utc))
    }

    @Test fun crossingMidnightUsesCalendarDayInsteadOfTwentyFourHours() {
        assertEquals(
            "昨天，晚上十一点五十分",
            describeCallTime(at(2026, 9, 8, 23, 50), at(2026, 9, 9, 0, 10), utc),
        )
    }

    @Test fun daylightSavingChangeStillCountsNaturalDays() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        assertEquals(
            "昨天，晚上十一点三十分",
            describeCallTime(
                at(2025, 11, 1, 23, 30, newYork),
                at(2025, 11, 2, 1, 30, newYork),
                newYork,
            ),
        )
    }

    @Test fun mobileNumbersAreSpokenInThreeFourFourGroups() {
        assertEquals("幺三八，零零幺三，八零零幺", spokenNumber("138 0013 8001"))
    }

    @Test fun otherNumbersAreSpokenInGroupsOfFour() {
        assertEquals("零幺零，六二五五，幺二三四", spokenNumber("010-62551234"))
        assertEquals("零七五五，八六六二，幺二三四", spokenNumber("0755 8662 1234"))
        assertEquals("幺幺零", spokenNumber("110"))
        assertEquals("unknown", spokenNumber("unknown"))
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        zone: TimeZone = utc,
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis
}
