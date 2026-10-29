package com.klaypeter.halt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klaypeter.halt.data.Allowance
import com.klaypeter.halt.data.InstalledApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    initialTab: Int = 0,
    usageState: UiState,
    allowances: List<Allowance>,
    perm: PermState,
    apps: List<InstalledApp>,
    onRefreshUsage: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onLoadApps: () -> Unit,
    onSaveAllowance: (String, String, Long) -> Unit,
    onResetAllowance: (String) -> Unit,
    onDeleteAllowance: (String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRecheckPermissions: () -> Unit
) {
    var tab by remember { mutableStateOf(initialTab) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Header(tab = tab, onRefresh = onRefreshUsage)
            SegmentedTabs(selected = tab, onSelect = { tab = it })

            Box(modifier = Modifier.weight(1f)) {
                if (tab == 0) {
                    TodayTab(state = usageState, onRequestPermission = onRequestUsagePermission)
                } else {
                    QuotaTab(
                        allowances = allowances,
                        perm = perm,
                        apps = apps,
                        onLoadApps = onLoadApps,
                        onSave = onSaveAllowance,
                        onReset = onResetAllowance,
                        onDelete = onDeleteAllowance,
                        onOpenAccessibility = onOpenAccessibility,
                        onOpenOverlay = onOpenOverlay,
                        onOpenNotifications = onOpenNotifications,
                        onRecheckPermissions = onRecheckPermissions
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(tab: Int, onRefresh: () -> Unit) {
    val date = remember {
        runCatching { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()) }.getOrDefault("")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (tab == 0) "今天" else "额度",
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (tab == 0) date else "自己定的规矩，自己守",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (tab == 0) {
            TextButton(onClick = onRefresh) {
                Text("刷新", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentItem("今日", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        SegmentItem("额度", selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun SegmentItem(
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (active) {
                    Modifier.shadow(elevation = 3.dp, shape = shape)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(
                if (active) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
