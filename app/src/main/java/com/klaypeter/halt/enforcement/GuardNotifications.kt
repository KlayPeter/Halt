package com.klaypeter.halt.enforcement

/** 通知渠道和 ID 集中放这里，前台服务和普通通知要共用同一条「守护中」通知。 */
object GuardNotifications {
    const val CHANNEL_ALERT = "guard"
    const val CHANNEL_PROGRESS = "guard_progress"
    const val ID_PROGRESS = 1
}
