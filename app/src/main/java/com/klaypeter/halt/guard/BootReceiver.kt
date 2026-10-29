package com.klaypeter.halt.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klaypeter.halt.enforcement.GuardianService

/** 开机后把守护服务重新拉起来。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GuardianService.start(context)
        }
    }
}
