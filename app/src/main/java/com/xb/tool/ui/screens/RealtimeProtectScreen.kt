package com.xb.tool.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.guard.CommandGuard
import com.xb.tool.guard.GuardService
import com.xb.tool.scan.ScanMode
import com.xb.tool.ui.InfoRow
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBListRow
import com.xb.tool.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xb.tool.ui.glassSurfaceColor
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton

@Composable
fun RealtimeProtectScreen(state: AppState) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(state.realtime) }
    val prefs = remember { com.xb.tool.core.Prefs(context) }
    var tick by remember { mutableStateOf(0) }
    // 用 Compose 状态保存强度，点击后立即重组，不必重进页面
    var level by remember { mutableStateOf(prefs.realtimeLevel) }
    var commandGuard by remember { mutableStateOf(prefs.commandGuardEnabled) }
    var popupEnabled by remember { mutableStateOf(prefs.commandAlertPopup) }
    var alerts by remember { mutableStateOf(CommandGuard.loadAlerts(context)) }
    val privilegeReady = state.rootInfo.rootGranted || state.shizukuGranted || state.adbConnected
    val privilegeChannel = when {
        state.rootInfo.rootGranted -> "Root"
        state.shizukuGranted -> "Shizuku"
        state.adbConnected -> "无线调试"
        else -> "需要特权"
    }
    val lastAt = prefs.realtimeLastAt
    val scanned = prefs.realtimeScanned
    val blocked = prefs.realtimeBlocked

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("实时防护模块", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "监控下载目录与新安装应用；进程常驻由保活机制负责",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = {
                            if (it && !state.notificationsGranted) {
                                state.requestNotificationPermission()
                                state.showMessage("请先授权通知权限")
                            } else {
                                state.applyRealtime(it)
                                enabled = it
                            }
                        })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(if (enabled) "防护中" else "已关闭", enabled)
                        StatusPill(if (state.guardAlive) "守护在线" else "守护离线", state.guardAlive)
                    }
                }
            }
        }

        if (!state.shizukuGranted) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Shizuku 授权",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            StatusPill(if (state.shizukuRunning) "已安装未授权" else "未安装", false)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "严格模式与 Shell 模式都要靠 Shizuku 拿到系统级权限：严格模式用它巡检已安装应用与受保护目录；" +
                                "Shell 模式在此基础上再用它读取进程命令行并弹窗阻断高危命令。没有 Shizuku 时这两种模式不可用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                state.requestShizukuAccess { ok ->
                                    state.showMessage(if (ok) "Shizuku 已授权" else "Shizuku 授权未完成")
                                }
                            }) { Text("授权 Shizuku") }
                            OutlinedButton(onClick = { state.navigate(Screen.Root) }) { Text("Root / Shizuku 页面") }
                        }
                    }
                }
            }
        }

        item { SectionHeader("防护强度") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    listOf(
                        Triple("low", "低模式", "只监控 APK / DEX / SO 等可执行文件，开销最小"),
                        Triple("normal", "标准模式", "监控安装包、压缩包、脚本与办公文档，推荐"),
                        Triple("strict", "严格模式", "监控所有文件 + 特权巡检已安装应用与受保护目录，需要 Shizuku"),
                        Triple("shell", "Shell 模式", "严格模式的全部能力 + 高危命令监控与弹窗阻断，需要 Shizuku 级权限")
                    ).forEach { (id, label, desc) ->
                        val selected = level == id
                        val needsShizuku = id == "strict"
                        val needsPrivilege = needsShizuku || id == "shell"
                        val allowed = if (needsShizuku) state.shizukuGranted else privilegeReady
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    if (!allowed) {
                                        state.showMessage(
                                            if (needsShizuku) "「$label」必须基于 Shizuku 权限，请先在本页上方授权 Shizuku"
                                            else "「$label」需要 Shizuku / Root / 无线调试 特权通道，请先授权"
                                        )
                                        return@clickable
                                    }
                                    prefs.realtimeLevel = id
                                    level = id
                                    state.showMessage("防护强度已设为「$label」，正在重启守护模块")
                                    runCatching {
                                        context.startService(
                                            android.content.Intent(context, GuardService::class.java)
                                                .setAction(GuardService.ACTION_REFRESH_GUARD)
                                        )
                                    }
                                }
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        if (needsPrivilege) {
                                            Spacer(Modifier.width(6.dp))
                                            StatusPill(
                                                when {
                                                    needsShizuku && state.shizukuGranted -> "Shizuku"
                                                    needsShizuku -> "需要 Shizuku"
                                                    privilegeReady -> privilegeChannel
                                                    else -> "需要特权"
                                                },
                                                allowed
                                            )
                                        }
                                    }
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader("检测状态") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow(
                        "最近检测时间",
                        if (lastAt <= 0) "还没有拦截记录" else
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(lastAt)),
                        Icons.Default.History
                    )
                    InfoRow("已检测文件", "$scanned 个", Icons.Default.Security)
                    InfoRow("已拦截威胁", "$blocked 个", Icons.Default.Bolt)
                    InfoRow(
                        "监控范围",
                        when (level) {
                            "shell" -> "所有文件 · 已安装应用 · 受保护目录 · 应用执行的 shell 命令"
                            "strict" -> "所有文件 · 已安装应用 · 受保护目录（特权巡检）"
                            else -> "下载目录 · 新安装 APK · 安装包"
                        },
                        Icons.Default.Shield
                    )
                    if (level == "strict" || level == "shell") {
                        val sweepAt = prefs.strictSweepAt
                        InfoRow(
                            "上次特权巡检",
                            if (sweepAt <= 0) "尚未执行（守护启动后自动开始）"
                            else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(sweepAt)) +
                                " · 应用 ${prefs.strictSweepApps} 个 · 命中 ${prefs.strictSweepThreats} 条",
                            Icons.Default.History
                        )
                        InfoRow("特权通道", privilegeChannel, Icons.Default.Shield)
                    }
                }
            }
        }

        if (level == "shell") {
            item { SectionHeader("高危命令防护") }
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("命令监控", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "特权通道：$privilegeChannel · 规则 ${CommandGuard.ruleCount()} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = commandGuard,
                                onCheckedChange = {
                                    prefs.commandGuardEnabled = it
                                    commandGuard = it
                                    runCatching {
                                        context.startService(
                                            android.content.Intent(context, GuardService::class.java)
                                                .setAction(GuardService.ACTION_REFRESH_GUARD)
                                        )
                                    }
                                    state.showMessage(if (it) "命令监控已开启" else "命令监控已关闭")
                                }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("命中时弹窗阻断", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "显示应用名、风险等级与命令，可一键跳转卸载",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = popupEnabled,
                                onCheckedChange = {
                                    prefs.commandAlertPopup = it
                                    popupEnabled = it
                                }
                            )
                        }
                        val poll = prefs.lastCommandPoll
                        Text(
                            if (poll <= 0) "尚未完成首次进程采样"
                            else "最近采样：" + SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(poll)) +
                                " · 命中 ${alerts.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (alerts.isNotEmpty()) {
                            alerts.take(5).forEach { alert ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                alert.appLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            StatusPill(
                                                alert.risk.label,
                                                alert.risk == CommandGuard.Risk.CRITICAL
                                            )
                                        }
                                        Text(
                                            alert.command,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    alerts = CommandGuard.loadAlerts(context)
                                    state.showMessage("已刷新命令告警")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("刷新") }
                            OutlinedButton(
                                onClick = {
                                    CommandGuard.clearAlerts(context)
                                    alerts = emptyList()
                                    state.showMessage("已清空命令告警记录")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("清空") }
                        }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Shell 模式通过特权通道周期性读取系统进程表，把 shell 命令归因到发起它的应用。它能发现静默卸载、清除数据、篡改系统设置、提权、下载即执行等行为；受 Android 权限模型限制，无法在内核层真正“阻止”命令执行，因此这里的处理方式是立即告警并引导卸载。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { SectionHeader("操作") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    XBListRow(
                        icon = Icons.Default.History,
                        title = "查看拦截记录",
                        subtitle = "查看实时检测命中的威胁与处理动作",
                        onClick = { state.navigate(Screen.Intercepts) }
                    )
                    XBListRow(
                        icon = Icons.Default.Bolt,
                        title = "重启守护模块",
                        subtitle = "守护进程异常时手动重启前台服务",
                        onClick = {
                            runCatching {
                                context.startService(
                                    android.content.Intent(context, GuardService::class.java)
                                        .setAction(GuardService.ACTION_REFRESH_SHAKE)
                                )
                                state.refreshServiceStatus()
                                state.showMessage("已请求重启守护模块")
                            }
                        }
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "说明：实时防护和后台保活是两个独立模块。实时防护负责“看到新文件就扫描”，保活负责“让进程不被系统杀掉”。只开保活不会自动扫描，只开实时防护在进程被清理后也会停止。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    val res = FileUtils.formatBytes(0)
                    state.showMessage("统计已刷新 $res")
                    tick++
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("刷新状态") }
        }
    }
}
