package com.klaypeter.halt.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.klaypeter.halt.data.DayUsage
import com.klaypeter.halt.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object NeedPermission : UiState
    data object Loading : UiState
    data class Ready(val usage: DayUsage) : UiState
}

class UsageViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = UsageRepository(app.applicationContext)

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * 每次回到前台都调用一次：用户可能刚在系统设置里授权完回来。
     */
    fun refresh() {
        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) { repository.hasUsageAccess() }
            if (!granted) {
                _state.value = UiState.NeedPermission
                return@launch
            }
            // 已有数据时不要闪成 loading，直接原地替换
            if (_state.value !is UiState.Ready) {
                _state.value = UiState.Loading
            }
            val usage = withContext(Dispatchers.IO) { repository.loadToday() }
            _state.value = UiState.Ready(usage)
        }
    }

    fun openPermissionSettings() = repository.openPermissionSettings()
}
