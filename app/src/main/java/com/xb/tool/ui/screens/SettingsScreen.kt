package com.xb.tool.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.root.ShizukuUtils
import com.xb.tool.ui.PermissionReminderDialog
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBConfirmDialog
import com.xb.tool.ui.XBInfoDialog
import com.xb.tool.ui.XBListRow
import com.xb.tool.util.FileUtils
import com.xb.tool.util.Intents
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun SettingsScreen(state: AppState) {
    var reminderFor by remember { mutableStateOf<String?>(null) }
    var showShizukuMissing by remember { mutableStateOf(false) }
    var showShizukuInfo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { state.refreshCache() }

    fun doRootAuth() {
        state.requestRootAccess { ok ->
            state.showMessage(if (ok) "Root 授权成功" else "Root 授权未通过")
        }
    }

    fun doShizukuAuth() {
        state.requestShizukuAccess { ok ->
            state.showMessage(if (ok) "Shizuku 服务已就绪" else "Shizuku 授权未完成")
        }
    }

    fun onRootClick() {
        if (state.rootInfo.rootGranted) {
            state.navigate(Screen.Root)
        } else if (state.rootRemind) {
            reminderFor = "root"
        } else {
            doRootAuth()
        }
    }

    fun onShizukuClick() {
        if (!state.shizukuRunning) {
            showShizukuMissing = true
        } else if (state.shizukuGranted) {
            showShizukuInfo = true
        } else if (state.rootRemind) {
            reminderFor = "shizuku"
        } else {
            doShizukuAuth()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SettingsHeader(state) }

        item { SectionHeader("防护与保活") }
        item {
            XBListRow(
                icon = Icons.Default.Bolt,
                title = "后台保活机制",
                subtitle = when {
                    state.extremeKeepAlive -> "极致保活开启中，标准保活已自动关闭"
                    state.keepAlive -> "保持检测模块常驻，实时守护"
                    else -> "已关闭，退出应用后防护可能停止"
                },
                onClick = {
                    if (!state.extremeKeepAlive) {
                        state.applyKeepAlive(!state.keepAlive)
                    } else {
                        state.showMessage("极致保活开启中，请先关闭极致保活")
                    }
                },
                trailing = {
                    Switch(
                        checked = state.keepAlive,
                        enabled = !state.extremeKeepAlive,
                        onCheckedChange = {
                            if (!state.extremeKeepAlive) state.applyKeepAlive(it)
                            else state.showMessage("极致保活开启中，请先关闭极致保活")
                        }
                    )
                }
            )
        }
        item {
            val ready = state.keepAliveGate(true).isEmpty()
            XBListRow(
                icon = Icons.Default.BatteryChargingFull,
                title = "极致后台保活",
                subtitle = when {
                    state.extremeKeepAlive -> "已开启 · 双进程互拉守护"
                    state.keepAlive -> "标准保活开启中，需先关闭"
                    !ready -> "待授权 · 点击进入页面按步骤开启"
                    else -> "条件已满足，可直接开启"
                },
                // 未满足条件时不再弹窗，直接进入页面逐项授权
                onClick = { state.navigate(Screen.Extreme) },
                trailing = {
                    Switch(
                        checked = state.extremeKeepAlive,
                        onCheckedChange = { checked ->
                            when {
                                !checked -> state.applyExtremeKeepAlive(false)
                                state.keepAlive -> {
                                    state.showMessage("后台保活机制开启中，请先关闭标准保活")
                                    state.navigate(Screen.Extreme)
                                }
                                !ready -> state.navigate(Screen.Extreme)
                                else -> state.applyExtremeKeepAlive(true)
                            }
                        }
                    )
                }
            )
        }

        item { SectionHeader("隔离与记录") }
        item {
            XBListRow(
                icon = Icons.Default.Lock,
                title = "隔离区",
                subtitle = "查看已拦截和手动隔离的样本",
                onClick = { state.navigate(Screen.Quarantine) }
            )
        }

        item { SectionHeader("权限") }
        item {
            XBListRow(
                icon = Icons.Default.Wifi,
                title = "ADB 增强服务",
                subtitle = when {
                    state.adbConnected -> "已连接 · 免 Root 特权通道（adb shell）"
                    else -> "无线调试配对 · Root · 有线 ADB，三条通道合一"
                },
                onClick = { state.navigate(Screen.PrivilegeService) },
                tint = MaterialTheme.colorScheme.primary,
                trailing = {
                    StatusPill(
                        if (state.adbConnected) "已连接" else "未连接",
                        active = state.adbConnected
                    )
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.Key,
                title = "Shizuku 授权",
                subtitle = when {
                    !state.shizukuRunning -> "Shizuku 未运行 · 点击查看说明"
                    state.shizukuGranted -> "已授权 · UID ${ShizukuUtils.uid()} · 服务已连接"
                    else -> "未授权 · 点击申请（先弹出权限提醒）"
                },
                onClick = { onShizukuClick() },
                tint = MaterialTheme.colorScheme.tertiary,
                trailing = {
                    StatusPill(
                        if (state.shizukuGranted) "已授权" else "未授权",
                        active = state.shizukuGranted
                    )
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.Shield,
                title = "Root 授权",
                subtitle = when {
                    state.rootInfo.rootGranted ->
                        "已授权 · ${state.rootInfo.managerName} ${state.rootInfo.managerVersion}"
                    state.rootInfo.managerName != "未检测到" ->
                        "检测到 ${state.rootInfo.managerName} · 等待授权"
                    else -> "未授权 · 点击申请 Root 权限"
                },
                onClick = { onRootClick() },
                trailing = {
                    StatusPill(
                        if (state.rootInfo.rootGranted) "已授权" else "未授权",
                        active = state.rootInfo.rootGranted
                    )
                }
            )
        }

        item { SectionHeader("通知") }
        item {
            XBListRow(
                icon = Icons.Default.Notifications,
                title = "通知管理",
                subtitle = if (state.notificationsGranted) "已开启 · 扫描、拦截与更新提醒"
                else "通知未授权 · 点击前往开启",
                onClick = { state.navigate(Screen.Notification) },
                trailing = {
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
            )
        }

        item { SectionHeader("个性化") }
        item {
            XBListRow(
                icon = Icons.Default.Palette,
                title = "自定义个性化",
                subtitle = "颜色 · 背景图片/视频 · 字体大小 · UI 动画 · 毛玻璃",
                onClick = { state.navigate(Screen.Theme) }
            )
        }

        item { SectionHeader("存储") }
        item {
            XBListRow(
                icon = Icons.Default.DeleteSweep,
                title = "缓存清理",
                subtitle = if (state.cacheCalculating) "正在计算缓存占用…"
                else "已占用 ${FileUtils.formatBytes(state.cacheInfo.total)} · 点击清理",
                onClick = { state.navigate(Screen.Cache) },
                tint = MaterialTheme.colorScheme.error,
                trailing = {
                    Text(
                        FileUtils.formatBytes(state.cacheInfo.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                "XB工具 v1.3.7 · 所有数据仅保存在本机",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    reminderFor?.let { target ->
        val isRoot = target == "root"
        PermissionReminderDialog(
            permissionName = if (isRoot) "Root 授权" else "Shizuku 授权",
            message = if (isRoot) {
                "授予 Root 权限后，XB工具可以执行深度扫描、冻结应用与清理系统缓存。" +
                    "Root 权限具有较高风险，请确认你信任本应用并了解相关后果。"
            } else {
                "授予 Shizuku 权限后，本应用可以在不 Root 的情况下执行部分系统级操作。" +
                    "请确保 Shizuku 服务已启动，并且你了解将授予的权限范围。"
            },
            onDismiss = { reminderFor = null },
            onConfirm = {
                reminderFor = null
                if (isRoot) doRootAuth() else doShizukuAuth()
            }
        )
    }

    if (showShizukuMissing) {
        XBConfirmDialog(
            title = "Shizuku 服务未运行",
            text = "请先安装并启动 Shizuku（可通过无线调试或 Root 启动），返回本应用后再进行授权。",
            confirmLabel = "打开 Shizuku",
            onConfirm = {
                showShizukuMissing = false
                Intents.openShizuku(state.context)
            },
            onDismiss = { showShizukuMissing = false }
        )
    }

    if (showShizukuInfo) {
        XBInfoDialog(
            title = "Shizuku 已授权",
            text = "服务版本：${ShizukuUtils.version()}\nUID：${ShizukuUtils.uid()}\n" +
                (if (ShizukuUtils.uid() == 0) "当前以 Root 身份运行" else "当前以 Shell 身份运行") +
                "\n\n高级功能会自动使用 Shizuku 通道。",
            onDismiss = { showShizukuInfo = false }
        )
    }

}

@Composable
private fun SettingsHeader(state: AppState) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { state.navigate(Screen.Root) }
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(scheme.surface, scheme.primaryContainer.copy(alpha = 0.55f))
                )
            )
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(scheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = scheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("XB工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "v1.3.7 · 本机安全与系统工具",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        if (state.rootInfo.healthy) "环境正常" else "标准模式",
                        active = state.rootInfo.healthy
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        if (state.rootInfo.rootGranted) "Root 已授权" else "Root 未授权",
                        active = state.rootInfo.rootGranted,
                        icon = Icons.Default.VerifiedUser
                    )
                    StatusPill(
                        if (state.shizukuGranted) "Shizuku 已授权" else "Shizuku 未授权",
                        active = state.shizukuGranted
                    )
                    StatusPill(
                        if (state.realtime) "实时防护开" else "实时防护关",
                        active = state.realtime
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "白名单 ${state.whitelist.size} 个 · 已冻结 ${state.frozenApps.size} 个 · 缓存 ${FileUtils.formatBytes(state.cacheInfo.total)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "查看环境详情",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary
                    )
                    Icon(
                        Icons.Default.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = scheme.primary
                    )
                }
            }
        }
    }
}
