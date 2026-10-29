package com.klaypeter.halt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.klaypeter.halt.enforcement.GuardianService
import com.klaypeter.halt.guard.GuardHealthWorker
import com.klaypeter.halt.ui.GuardViewModel
import com.klaypeter.halt.ui.MainScreen
import com.klaypeter.halt.ui.PostUpdateDialog
import com.klaypeter.halt.ui.SetupScreen
import com.klaypeter.halt.ui.UiState
import com.klaypeter.halt.ui.UpdateAvailableDialog
import com.klaypeter.halt.ui.UsageViewModel
import com.klaypeter.halt.ui.theme.ScreenTimeTheme
import com.klaypeter.halt.updater.UpdateChecker
import com.klaypeter.halt.updater.UpdateInfo
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "extra_tab"
        const val TAB_TODAY = 0
        const val TAB_QUOTA = 1

        private const val PREFS = "app"
        private const val KEY_LAST_VERSION = "last_version_code"
        private const val KEY_SKIPPED_UPDATE = "skipped_update_version"
        private const val RELEASES_URL = "https://github.com/KlayPeter/Halt/releases/latest"
    }

    private val usageViewModel: UsageViewModel by viewModels()
    private val guardViewModel: GuardViewModel by viewModels()

    /** 用户在引导页点了「先跳过」。 */
    private var setupSkipped by mutableStateOf(false)

    private var updateInfo by mutableStateOf<UpdateInfo?>(null)
    private var showPostUpdate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleHealthCheck()
        GuardianService.start(this)
        detectVersionChange()

        val initialTab = readTab(intent)

        setContent {
            ScreenTimeTheme {
                val perm by guardViewModel.perm.collectAsStateWithLifecycle()
                val permReady by guardViewModel.permReady.collectAsStateWithLifecycle()

                if (permReady && !perm.allOk && !setupSkipped) {
                    // 一进 App 就检查，缺什么引导什么
                    SetupScreen(
                        perm = perm,
                        onOpenUsage = usageViewModel::openPermissionSettings,
                        onOpenAccessibility = guardViewModel::openAccessibility,
                        onOpenOverlay = guardViewModel::openOverlay,
                        onOpenNotifications = guardViewModel::openNotifications,
                        onRecheck = guardViewModel::refreshPermissions,
                        onSkip = { setupSkipped = true }
                    )
                } else {
                    val usageState by usageViewModel.state.collectAsStateWithLifecycle()
                    val allowances by guardViewModel.allowances.collectAsStateWithLifecycle()
                    val apps by guardViewModel.apps.collectAsStateWithLifecycle()

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
                        onRecheckPermissions = guardViewModel::refreshPermissions,
                        onOpenReleases = { openUrl(RELEASES_URL) }
                    )
                }

                updateInfo?.let { info ->
                    UpdateAvailableDialog(
                        version = info.version,
                        current = BuildConfig.VERSION_NAME,
                        onDownload = {
                            openUrl(info.downloadUrl)
                            updateInfo = null
                        },
                        onLater = {
                            skipVersion(info.version)
                            updateInfo = null
                        }
                    )
                }

                if (showPostUpdate) {
                    PostUpdateDialog(
                        version = BuildConfig.VERSION_NAME,
                        onCheckPermissions = {
                            showPostUpdate = false
                            setupSkipped = false
                            guardViewModel.refreshPermissions()
                        },
                        onDismiss = { showPostUpdate = false }
                    )
                }
            }
        }

        checkUpdates()
    }

    /** 安装包被替换过：系统很可能把无障碍关了，提醒用户复查。 */
    private fun detectVersionChange() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST_VERSION, 0)
        if (last != 0 && last != BuildConfig.VERSION_CODE) {
            showPostUpdate = true
        }
        prefs.edit().putInt(KEY_LAST_VERSION, BuildConfig.VERSION_CODE).apply()
    }

    private fun checkUpdates() {
        lifecycleScope.launch {
            val info = runCatching {
                UpdateChecker.check(this@MainActivity, BuildConfig.VERSION_NAME)
            }.getOrNull() ?: return@launch
            val skipped = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_SKIPPED_UPDATE, null)
            if (info.version != skipped) updateInfo = info
        }
    }

    private fun skipVersion(version: String) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_SKIPPED_UPDATE, version)
            .apply()
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (readTab(intent) != TAB_TODAY) recreate()
    }

    private fun readTab(intent: Intent?): Int =
        intent?.getIntExtra(EXTRA_TAB, TAB_TODAY) ?: TAB_TODAY

    /** 每 5 分钟自检：无障碍 / 使用情况权限被关掉时通知用户。 */
    private fun scheduleHealthCheck() {
        val request = PeriodicWorkRequestBuilder<GuardHealthWorker>(5, TimeUnit.MINUTES)
            .setInitialDelay(5, TimeUnit.MINUTES)
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