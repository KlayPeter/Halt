package com.klaypeter.halt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klaypeter.halt.data.Allowance
import com.klaypeter.halt.data.InstalledApp
import com.klaypeter.halt.security.PinStore
import com.klaypeter.halt.util.formatDuration
import com.klaypeter.halt.util.loadAppIcon

@Composable
fun QuotaTab(
    allowances: List<Allowance>,
    todayUsage: Map<String, Long>,
    perm: PermState,
    apps: List<InstalledApp>,
    onLoadApps: () -> Unit,
    onSave: (String, String, Long) -> Unit,
    onGrant: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRecheckPermissions: () -> Unit,
    onOpenReleases: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // 「重置 / 删除」这些改额度的动作要过密码；进 App 看数据不用
    val context = LocalContext.current
    val pinStore = remember { PinStore(context) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 0=不显示，1=验证旧密码，2=设置新密码
    var changePinStep by remember { mutableStateOf(0) }

    pendingAction?.let { action ->
        PinPromptDialog(
            pinSet = pinStore.isSet(),
            verify = { pin -> pinStore.verify(pin) },
            onSet = { pin -> pinStore.set(pin) },
            onDismiss = { pendingAction = null },
            onConfirmed = {
                pendingAction = null
                action()
            }
        )
    }

    if (changePinStep == 1) {
        PinPromptDialog(
            pinSet = true,
            verify = { pin -> pinStore.verify(pin) },
            onSet = { pin -> pinStore.set(pin) },
            onDismiss = { changePinStep = 0 },
            onConfirmed = { changePinStep = 2 }
        )
    }

    if (changePinStep == 2) {
        PinPromptDialog(
            pinSet = false,
            verify = { pin -> pinStore.verify(pin) },
            onSet = { pin -> pinStore.set(pin) },
            onDismiss = { changePinStep = 0 },
            onConfirmed = { changePinStep = 0 }
        )
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenProject = {
                showAbout = false
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/KlayPeter/Halt")
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
    }

    if (showDialog) {
        AddQuotaDialog(
            apps = apps,
            onDismiss = { showDialog = false },
            onConfirm = { app, minutes ->
                onSave(app.packageName, app.label, minutes)
                showDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (pinStore.isSet()) "改额度需要密码" else "还没设密码",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { changePinStep = if (pinStore.isSet()) 1 else 2 }) {
                    Text(
                        if (pinStore.isSet()) "修改密码" else "设置密码",
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (!perm.allOk) {
            item {
                PermissionCard(
                    perm = perm,
                    onAccessibility = onOpenAccessibility,
                    onOverlay = onOpenOverlay,
                    onNotifications = onOpenNotifications,
                    onRecheck = onRecheckPermissions
                )
            }
        }

        item { SectionHeader("受控应用", if (allowances.isEmpty()) null else "额度用完就进不去了") }

        if (allowances.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                ) {
                    Text(
                        "还没有设置任何额度。\n加一个，然后自己填个时长试试。",
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 20.dp)
                    )
                }
            }
        }

        items(allowances, key = { it.packageName }) { allowance ->
            QuotaCard(
                allowance = allowance,
                usedMs = maxOf(allowance.usedMs, todayUsage[allowance.packageName] ?: 0L),
                allowedMs = allowance.allowedMs,
                onGrant = {
                    pendingAction = { onGrant(allowance.packageName) }
                },
                onDelete = {
                    pendingAction = { onDelete(allowance.packageName) }
                }
            )
        }

        item {
            Button(
                onClick = {
                    onLoadApps()
                    showDialog = true
                },
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp)
            ) {
                Text("＋ 添加额度", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = onOpenReleases) {
                    Text(
                        "v${com.klaypeter.halt.BuildConfig.VERSION_NAME} · 检查更新",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showAbout = true }) {
                    Text(
                        "关于",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaCard(
    allowance: Allowance,
    usedMs: Long,
    allowedMs: Long,
    onGrant: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val icon = remember(allowance.packageName) { loadAppIcon(context, allowance.packageName) }
    val exhausted = usedMs >= allowedMs
    val accent = if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val share = if (allowedMs > 0) {
        (usedMs.toFloat() / allowedMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val remainingFraction = 1f - share

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(icon, 46)
                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            allowance.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (exhausted) {
                            Spacer(Modifier.width(8.dp))
                            StatusChip("已用完")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ShareBar(share = share, height = 6, color = accent)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text(
                            "已用 ${formatDuration(usedMs)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "可用 ${formatDuration(allowedMs)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))
                RingProgress(
                    progress = remainingFraction,
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 5.dp,
                    color = accent
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            ringText((allowedMs - usedMs).coerceAtLeast(0L)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            "剩余",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onGrant) {
                    Text("＋15 分钟", fontSize = 13.sp)
                }
                TextButton(onClick = onDelete) {
                    Text(
                        "删除",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun ringText(remainingMs: Long): String {
    val minutes = remainingMs / 60_000
    return if (minutes >= 60) "${minutes / 60}h" else "$minutes"
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun PermissionCard(
    perm: PermState,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
    onRecheck: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "拦截功能还没开",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "无障碍只用来「到点把你踢出来」，和密码、看数据无关。不开的话额度只是个数字。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            PermissionLine("无障碍服务", perm.accessibility, onAccessibility)
            PermissionLine("显示在其他应用上层", perm.overlay, onOverlay)
            PermissionLine("通知", perm.notifications, onNotifications)
            TextButton(
                onClick = onRecheck,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开好了？点这里重新检测", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun PermissionLine(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        if (granted) {
            Text(
                "已开启",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            TextButton(onClick = onClick) {
                Text("去开启", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun AddQuotaDialog(
    apps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onConfirm: (InstalledApp, Long) -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var minutesText by remember { mutableStateOf("20") }
    var query by remember { mutableStateOf("") }

    val filtered = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    val minutes = minutesText.toLongOrNull() ?: 0L
    val canConfirm = selected != null && minutes > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("添加额度", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索应用", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                when {
                    apps.isEmpty() -> Text(
                        "正在读取应用列表…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    filtered.isEmpty() -> Text(
                        "没有匹配的应用",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            val icon = remember(app.packageName) {
                                loadAppIcon(context, app.packageName)
                            }
                            val isSelected = selected?.packageName == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { selected = app }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(icon, 34)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    app.label,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Text(
                                        "✓",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { input -> minutesText = input.filter { it.isDigit() }.take(4) },
                    label = { Text("额度", fontSize = 13.sp) },
                    suffix = { Text("分钟", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { selected?.let { onConfirm(it, minutes) } }
            ) {
                Text("确定", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
