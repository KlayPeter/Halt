package com.klaypeter.halt.util

/** 把毫秒变成「2小时13分」这种人看的字。 */
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分"
        totalMinutes > 0 -> "${minutes}分钟"
        ms > 0 -> "不到1分钟"
        else -> "0分钟"
    }
}

/** 把毫秒变成 12:34 这种倒计时写法。 */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
