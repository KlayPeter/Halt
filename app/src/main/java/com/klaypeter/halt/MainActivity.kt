package com.klaypeter.halt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.klaypeter.halt.enforcement.GuardianService
import com.klaypeter.halt.guard.GuardHealthWorker
import com.klaypeter.halt.ui.GuardViewModel
import com.klaypeter.halt.ui.UiState
import com.klaypeter.halt.ui.MainScreen
import com.klaypeter.halt.ui.UsageViewModel
import com.klaypeter.halt.ui.theme.ScreenTimeTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "extra_tab"
        const val TAB_TODAY = 0
        const val TAB_QUOTA = 1
    }

    private val usageViewModel: UsageViewModel by viewModels()
    private val guardViewModel: GuardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleHealthCheck()
        GuardianService.start(this)

        val initialTab = readTab(intent)

        setContent {
            ScreenTimeTheme {
                val usageState by usageViewModel.state.collectAsStateWithLifecycle()
                val allowances by guardViewModel.allowances.collectAsStateWithLifecycle()
                val perm by guardViewModel.perm.collectAsStateWithLifecycle()
                val apps by guardViewModel.apps.collectAsStateWithLifecycle()

                // 「今日」页和「额度」页共用同一份系统统计
                val todayUsage = (usageState as? UiState.Ready)
                    ?.usage?.apps
                    ?.associate { it.packageName to it.totalMs }
                    ?: emptyMap()

                MainScreen(
                    initialTab = initialTab,
                    usageState = usageState,
                    allowances = allowances,
                    todayUsage = todayUsage,
                    perm = perm,
                    apps = apps,
                    onRefreshUsage = usageViewModel::refresh,
                    onRequestUsagePermission = usageViewModel::openPermissionSettings,
                    onLoadApps = guardViewModel::loadApps,
                    onSaveAllowance = guardViewModel::save,
                    onGrantAllowance = guardViewModel::grant,
                    onDeleteAllowance = guardViewModel::delete,
                    onOpenAccessibility = guardViewModel::openAccessibility,
                    onOpenOverlay = guardViewModel::openOverlay,
                    onOpenNotifications = guardViewModel::openNotifications,
                    onRecheckPermissions = guardViewModel::refreshPermissions
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readTab(intent) != TAB_TODAY) recreate()
    }

    private fun readTab(intent: Intent?): Int =
        intent?.getIntExtra(EXTRA_TAB, TAB_TODAY) ?: TAB_TODAY

    /** 每 15 分钟自检一次：无障碍被系统关掉了要能通知到用户。 */
    private fun scheduleHealthCheck() {
        val request = PeriodicWorkRequestBuilder<GuardHealthWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            GuardHealthWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    override fun onResume() {
        super.onResume()
        usageViewModel.refresh()
        guardViewModel.refreshPermissions()
    }
}
