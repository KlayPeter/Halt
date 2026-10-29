package com.klaypeter.halt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 一进 App 就检查权限，缺什么引导什么。
 * 更新安装包后无障碍常被系统关掉，这个页面就是给那一刻准备的。
 */
@Composable
fun SetupScreen(
    perm: PermState,
    onOpenUsage: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRecheck: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "还差几步",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Halt 要「知道你在用哪个 App」才能到点把你踢出来。下面几项开完就能用。",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )

        PermissionRow(
            title = "使用情况访问",
            desc = "统计今天每个 App 用了多久",
            granted = perm.usageAccess,
            onOpen = onOpenUsage
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            title = "无障碍服务",
            desc = "实时知道前台是哪个 App，并把你踢出去",
            granted = perm.accessibility,
            onOpen = onOpenAccessibility,
            highlight = true
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            title = "显示在其他应用上层",
            desc = "额度用完时盖一层提示，挡住内容",
            granted = perm.overlay,
            onOpen = onOpenOverlay
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            title = "通知",
            desc = "剩余时间提醒和常驻状态",
            granted = perm.notifications,
            onOpen = onOpenNotifications
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRecheck,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("开好了，重新检测", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(
                "先跳过（没有权限就不会拦截）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean,
    onOpen: () -> Unit,
    highlight: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        onClick = { if (!granted) onOpen() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (highlight && !granted) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "最容易漏",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    desc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (granted) {
                Text(
                    "已开启",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "去开启",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 更新后提醒：系统可能把无障碍关掉了。 */
@Composable
fun PostUpdateDialog(
    version: String,
    onCheckPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text("已更新到 $version", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "系统有时会在更新后关掉无障碍服务，导致额度不生效。建议现在检查一遍权限。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            TextButton(onClick = onCheckPermissions) { Text("去检查") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

/** 发现新版本。点「去下载」跳到 Release 页面，走系统安装器。 */
@Composable
fun UpdateAvailableDialog(
    version: String,
    current: String,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onLater,
        shape = RoundedCornerShape(26.dp),
        title = { Text("发现新版本 $version", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "当前版本 $current。点「立即更新」会直接下载并调起安装，装完记得回来看一眼权限。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDownload) { Text("立即更新") }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("以后再说") }
        }
    )
}
/** 下载进度。下载的是 6MB 左右的 APK，通常几秒钟。 */
@Composable
fun UpdateProgressDialog(progress: Int) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { },
        shape = RoundedCornerShape(26.dp),
        title = { Text("正在下载更新", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (progress >= 0) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "$progress%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "正在下载…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = { }
    )
}