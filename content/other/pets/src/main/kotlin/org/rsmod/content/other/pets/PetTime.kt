package org.rsmod.content.other.pets

private const val TICKS_PER_MINUTE = 100

internal fun Int.ticksToMinutes(): Int = this / TICKS_PER_MINUTE

internal fun Int.minutesAsText(): String {
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours == 0 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
        else -> "$hours ${if (hours == 1) "hour" else "hours"} ${minutes.toString().padStart(2, '0')} minutes"
    }
}
