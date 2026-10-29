package com.klaypeter.halt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klaypeter.halt.data.Allowance
import com.klaypeter.halt.data.GuardRepository
import com.klaypeter.halt.data.InstalledApp
import com.klaypeter.halt.data.listLaunchableApps
import com.klaypeter.halt.guard.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermState(
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val usageAccess: Boolean = false
) {
    /** 四项全开，拦截才真正生效。 */
    val allOk: Boolean get() = accessibility && overlay && notifications && usageAccess
}

private const val GRANT_MS = 15 * 60_000L

class GuardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = GuardRepository(app)
    private val context = app.applicationContext

    val allowances: StateFlow<List<Allowance>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _perm = MutableStateFlow(PermState())
    val perm: StateFlow<PermState> = _perm.asStateFlow()

    /** 第一次检查完成之前不要拦着用户看启动页。 */
    private val _permReady = MutableStateFlow(false)
    val permReady: StateFlow<Boolean> = _permReady.asStateFlow()

    /** 每次回到前台刷新一次：用户可能刚在系统设置里授权完回来。 */
    fun refreshPermissions() {
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) {
                runCatching { com.klaypeter.halt.data.UsageRepository(context).hasUsageAccess() }
                    .getOrDefault(false)
            }
            _perm.value = PermState(
                accessibility = Permissions.accessibilityEnabled(context),
                overlay = Permissions.overlayAllowed(context),
                notifications = Permissions.notificationsAllowed(context),
                usageAccess = usage
            )
            _permReady.value = true
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { listLaunchableApps(context) }
        }
    }

    fun save(packageName: String, label: String, minutes: Long) {
        viewModelScope.launch {
            repository.upsert(Allowance(packageName, label, minutes * 60_000L, 0L))
        }
    }

    /** 输密码后加 15 分钟，等价于「解锁」。 */
    fun grant(packageName: String) {
        viewModelScope.launch { repository.grant(packageName, GRANT_MS) }
    }

    fun delete(packageName: String) {
        viewModelScope.launch { repository.delete(packageName) }
    }

    fun openAccessibility() = Permissions.openAccessibilitySettings(context)

    fun openOverlay() = Permissions.openOverlaySettings(context)

    fun openNotifications() = Permissions.openNotificationSettings(context)
}
