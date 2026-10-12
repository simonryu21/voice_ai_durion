package com.lgcns.codeengine.util.extension

import java.text.SimpleDateFormat
import java.util.*

fun Date.plusYears(years: Int): Date {
    val cal = Calendar.getInstance()
    cal.time = this
    cal.add(Calendar.YEAR, years)
    return cal.time
}


fun Date.plusDays(days: Int): Date {
    val cal = Calendar.getInstance()
    cal.time = this
    cal.add(Calendar.DAY_OF_MONTH, days)
    return cal.time
}

fun Date.format(pattern: String, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat(pattern, locale).format(this)

fun Date.toCalendar(): Calendar = Calendar.getInstance().also { it.time = this }

// 다른날짜보다 미래이면 true, 같거나 과거이면 false (2000.01.02 > 2000.01.01 = true)
fun Date.isAfterDay(other: Date): Boolean {
    val cal = this.toCalendar()
    val otherCal = other.toCalendar()

    val year = cal.get(Calendar.YEAR)
    val otherYear = otherCal.get(Calendar.YEAR)
    return if (year != otherYear) {
        year > otherYear
    } else {
        cal.get(Calendar.DAY_OF_YEAR) > otherCal.get(Calendar.DAY_OF_YEAR)
    }
}

// 다른날짜보다 과거이면 true, 같거나 미래이면 false (2000.01.02 < 2000.01.01 = true)
fun Date.isBeforeDay(other: Date): Boolean {
    val cal = this.toCalendar()
    val otherCal = other.toCalendar()

    val year = cal.get(Calendar.YEAR)
    val otherYear = otherCal.get(Calendar.YEAR)
    return if (year != otherYear) {
        year < otherYear
    } else {
        cal.get(Calendar.DAY_OF_YEAR) < otherCal.get(Calendar.DAY_OF_YEAR)
    }
}