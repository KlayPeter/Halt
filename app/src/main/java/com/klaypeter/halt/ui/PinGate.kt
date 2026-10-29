package com.klaypeter.halt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * 重置 / 删除额度前要过的密码框。
 *
 * 只在「要改额度」时出现 —— 进 App 看数据是自由的。
 * 第一次用会先让你设置一个 4 位密码。
 */
@Composable
fun PinPromptDialog(
    pinSet: Boolean,
    verify: (String) -> Boolean,
    onSet: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableStateOf(0) }
    var coolingDown by remember { mutableStateOf(false) }

    LaunchedEffect(coolingDown) {
        if (coolingDown) {
            delay(30_000)
            coolingDown = false
            failures = 0
            message = null
        }
    }

    fun submit(pin: String) {
        if (!pinSet) {
            when {
                firstPin == null -> {
                    firstPin = pin
                    input = ""
                    message = "再输一次确认"
                }

                firstPin == pin -> {
                    onSet(pin)
                    onConfirmed()
                }

                else -> {
                    firstPin = null
                    input = ""
                    message = "两次不一致，重来"
                }
            }
        } else {
            if (verify(pin)) {
                onConfirmed()
            } else {
                input = ""
                failures += 1
                message = if (failures >= 5) {
                    coolingDown = true
                    "错误次数过多，30 秒后再试"
                } else {
                    "密码不对，还剩 ${5 - failures} 次"
                }
            }
        }
    }

    fun onDigit(digit: String) {
        if (coolingDown || input.length >= PIN_LENGTH) return
        val next = input + digit
        input = next
        if (next.length == PIN_LENGTH) submit(next)
    }

    val title = when {
        pinSet -> "输入密码"
        firstPin == null -> "设置一个密码"
        else -> "确认密码"
    }
    val subtitle = when {
        pinSet -> "改额度前先确认是你本人"
        firstPin == null -> "4 位数字，防止随手把额度重置掉"
        else -> "再输一遍"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(PIN_LENGTH) { index ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < input.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    message ?: " ",
                    fontSize = 11.sp,
                    color = if (message == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )

                Spacer(Modifier.height(14.dp))
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "⌫")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(Modifier.size(58.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            when (key) {
                                                "⌫" -> if (input.isNotEmpty()) input = input.dropLast(1)
                                                else -> onDigit(key)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        key,
                                        fontSize = if (key == "⌫") 16.sp else 19.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(2.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("取消", fontSize = 13.sp)
                }
            }
        }
    }
}

private const val PIN_LENGTH = 4
