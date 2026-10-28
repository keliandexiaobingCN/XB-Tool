package com.xb.tool.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.guard.Notifications
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBListRow
import com.xb.tool.util.Intents
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun NotificationSettingsScreen(state: AppState) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (state.notificationsGranted) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.notificationsGranted) Icons.Default.NotificationsActive
                            else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = if (state.notificationsGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.notificationsGranted) "通知已开启" else "通知未授权",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "扫描结果、实时拦截与病毒库更新会通过通知提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            Modifier.clickable {
                                if (state.notificationsGranted) {
                                    Intents.openNotificationSettings(state.context)
                                } else {
                                    state.requestNotificationPermission()
                                }
                            }
                        ) {
                            StatusPill(
                                if (state.notificationsGranted) "关闭" else "开启",
                                active = state.notificationsGranted
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!state.notificationsGranted) {
                            Button(
                                onClick = { state.requestNotificationPermission() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("请求权限")
                            }
                        }
                        OutlinedButton(
                            onClick = { Intents.openNotificationSettings(state.context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("系统通知设置")
                        }
                    }
                }
            }
        }

        item { SectionHeader("提醒类型") }
        item {
            XBListRow(
                icon = Icons.Default.Notifications,
                title = "扫描结果通知",
                subtitle = "扫描完成或发现威胁时通知",
                onClick = { state.applyNotifyScan(!state.notifyScan) },
                trailing = {
                    Switch(checked = state.notifyScan, onCheckedChange = { state.applyNotifyScan(it) })
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.GppMaybe,
                title = "实时拦截通知",
                subtitle = "实时检测到可疑新文件时立即提醒",
                onClick = { state.applyNotifyIntercept(!state.notifyIntercept) },
                tint = MaterialTheme.colorScheme.error,
                trailing = {
                    Switch(checked = state.notifyIntercept, onCheckedChange = { state.applyNotifyIntercept(it) })
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.SystemUpdate,
                title = "病毒库更新通知",
                subtitle = "病毒库有新版本时提醒",
                onClick = { state.applyNotifyUpdate(!state.notifyUpdate) },
                trailing = {
                    Switch(checked = state.notifyUpdate, onCheckedChange = { state.applyNotifyUpdate(it) })
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.Warning,
                title = "权限提醒",
                subtitle = "在需要 Shizuku / Root 授权时先弹出双滑块提醒",
                onClick = { state.applyRootRemind(!state.rootRemind) },
                tint = MaterialTheme.colorScheme.tertiary,
                trailing = {
                    Switch(checked = state.rootRemind, onCheckedChange = { state.applyRootRemind(it) })
                }
            )
        }

        item { SectionHeader("通道") }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "后台守护通知会常驻状态栏，用于保持实时检测服务存活；可在系统通知设置中单独调整重要程度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { Notifications.notifyTest(state.context) }) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("发送测试通知")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Android 13 及以上需要通知权限。若系统不再弹出授权框，请前往系统设置手动开启。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
