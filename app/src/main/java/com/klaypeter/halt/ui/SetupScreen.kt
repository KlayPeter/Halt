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
 * 一进 App 就检查，缺什么引导什么。
 * 更新安装包后无障碍常被系统关掉，这个页面就是给那一刻准备的。
 */
@Composable
fun SetupScreen(
    perm: PermState,
    onOpenUsage: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBattery: () -> Unit,
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
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            title = "后台不被杀",
            desc = "省电策略设为「无限制」。不设的话守护会被系统悄悄杀掉",
            granted = perm.batteryUnrestricted,
            onOpen = onOpenBattery,
            badge = "建议"
        )

        Spacer(Modifier.height(18.dp))
        PrivacyCard()

        Spacer(Modifier.height(18.dp))
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
    highlight: Boolean = false,
    badge: String? = null
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
                    if (badge != null && !granted) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            badge,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 隐私说明：一个要「看屏幕」权限的 App，必须自己说清楚。 */
@Composable
fun PrivacyCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "它到底读了什么",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            PrivacyLine("只读「当前是哪个 App」，不读屏幕内容、键盘输入、通知内容")
            PrivacyLine("所有数据只存在本机，没有服务器，不上传")
            PrivacyLine("只有检查更新时才联网，可以在额度页底部手动触发")
        }
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("·", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/** 发现新版本。点「立即更新」直接应用内下载并调起安装器。 */
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

/** 下载进度。 */
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

/** 关于：隐私、数据、开源。 */
@Composable
fun AboutDialog(onDismiss: () -> Unit, onOpenProject: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        title = { Text("关于 Halt", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "v" + com.klaypeter.halt.BuildConfig.VERSION_NAME,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PrivacyLine("只读「当前是哪个 App」，不读屏幕内容、键盘输入、通知内容")
                PrivacyLine("额度、密码摘要、使用记录都存在本机，没有服务器")
                PrivacyLine("只有检查更新时才联网")
                Spacer(Modifier.height(10.dp))
                Text(
                    "MIT License · github.com/KlayPeter/Halt",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenProject) { Text("打开项目页") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}