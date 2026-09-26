package com.kantu.phone

import java.util.Calendar
import java.util.TimeZone
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

/** Display stays numeric; relative Chinese dates are reserved for speech. */
internal fun formatCallDate(timestamp: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
    if (timestamp <= 0) "时间未知" else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).apply {
        this.timeZone = timeZone
    }.format(Date(timestamp))

internal fun describeCallTime(
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val callCalendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }
    val nowCalendar = Calendar.getInstance(timeZone).apply { timeInMillis = now }
    val callDay = callCalendar.civilDayNumber()
    val today = nowCalendar.civilDayNumber()
    val daysAgo = (today - callDay).toInt().coerceAtLeast(0)
    val dayText = when (daysAgo) {
        0 -> "今天"
        1 -> "昨天"
        2 -> "前天"
        3 -> "大前天"
        else -> "${chineseNumber(daysAgo)}天前"
    }

    val hour24 = callCalendar.get(Calendar.HOUR_OF_DAY)
    val minute = callCalendar.get(Calendar.MINUTE)
    val period = when (hour24) {
        in 0..5 -> "凌晨"
        in 6..11 -> "上午"
        in 12..17 -> "下午"
        else -> "晚上"
    }
    val hour12 = when (val value = hour24 % 12) {
        0 -> 12
        else -> value
    }
    val minuteText = when (minute) {
        0 -> ""
        in 1..9 -> "零${chineseNumber(minute)}分"
        else -> "${chineseNumber(minute)}分"
    }
    return "$dayText，$period${chineseNumber(hour12)}点$minuteText"
}

private val spokenDigits = mapOf(
    '0' to "零", '1' to "幺", '2' to "二", '3' to "三", '4' to "四",
    '5' to "五", '6' to "六", '7' to "七", '8' to "八", '9' to "九",
)

internal fun spokenDigit(digit: Char): String = spokenDigits[digit] ?: digit.toString()

/**
 * Reads a number the way people repeat it aloud: 11-digit numbers (mobiles, 010 landlines)
 * as 3-4-4 groups, others in groups of four, with pauses so the listener can follow along.
 */
internal fun spokenNumber(number: String): String {
    val digits = number.filter(Char::isDigit)
    if (digits.isEmpty()) return number
    val groups = if (digits.length == 11) {
        listOf(digits.substring(0, 3), digits.substring(3, 7), digits.substring(7))
    } else {
        digits.chunked(4)
    }
    return groups.joinToString("，") { group -> group.map(::spokenDigit).joinToString("") }
}

private fun Calendar.civilDayNumber(): Long {
    var year = get(Calendar.YEAR)
    val month = get(Calendar.MONTH) + 1
    val day = get(Calendar.DAY_OF_MONTH)
    if (month <= 2) year--
    // Avoid Math.floorDiv (API 24+) because the app supports Android 5.
    val era = if (year >= 0) year / 400 else (year - 399) / 400
    val yearOfEra = year - era * 400
    val shiftedMonth = month + if (month > 2) -3 else 9
    val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era.toLong() * 146_097L + dayOfEra
}

private fun chineseNumber(value: Int): String {
    val digits = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    return when (value) {
        in 0..9 -> digits[value]
        10 -> "十"
        in 11..19 -> "十${digits[value % 10]}"
        in 20..59 -> "${digits[value / 10]}十${if (value % 10 == 0) "" else digits[value % 10]}"
        else -> value.toString()
    }
}
