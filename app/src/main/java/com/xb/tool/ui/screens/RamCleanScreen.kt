package com.xb.tool.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.ui.InfoRow
import com.xb.tool.ui.PermissionReminderDialog
import com.xb.tool.util.FileUtils
import com.xb.tool.util.MemoryTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun RamCleanScreen(state: AppState) {
    val context = state.context
    val scope = rememberCoroutineScope()
    var mem by remember { mutableStateOf(MemoryTools.read(context)) }
    var cleaning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<MemoryTools.CleanResult?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPrivilege by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mem = withContext(Dispatchers.IO) { MemoryTools.read(context) }
        if (state.hasDeepAccess()) state.refreshRunningApps()
    }
    LaunchedEffect(state.runningApps) {
        if (selected.isEmpty()) selected = state.runningApps.map { it.packageName }.toSet()
    }

    val ratio = (mem.percent / 100f).coerceIn(0f, 1f)
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(150.dp)) {
                            val stroke = 12.dp.toPx()
                            drawArc(
                                color = scheme.surfaceVariant,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = if (mem.percent > 80) scheme.error else scheme.primary,
                                startAngle = -90f,
                                sweepAngle = 360f * ratio,
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${mem.percent}%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("运存占用", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "可用 ${FileUtils.formatBytes(mem.available)} / 总共 ${FileUtils.formatBytes(mem.total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                cleaning = true
                                val result = withContext(Dispatchers.IO) { MemoryTools.clean(context) }
                                mem = withContext(Dispatchers.IO) { MemoryTools.read(context) }
                                lastResult = result
                                cleaning = false
                                state.showMessage("运存清理完成，释放 ${FileUtils.formatBytes(result.freed)}")
                            }
                        },
                        enabled = !cleaning
                    ) {
                        if (cleaning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = scheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (cleaning) "正在清理…" else "一键清理运存")
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow("物理内存", FileUtils.formatBytes(mem.total), Icons.Default.Storage)
                    InfoRow("已使用", FileUtils.formatBytes(mem.used), Icons.Default.Storage)
                    InfoRow("可用", FileUtils.formatBytes(mem.available), Icons.Default.Storage)
                    InfoRow("低内存状态", if (mem.lowMemory) "是" else "否", Icons.Default.Speed)
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("可清理后台应用", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                if (state.hasDeepAccess()) state.refreshRunningApps() else showPrivilege = true
                            },
                            enabled = !state.runningAppsScanning
                        ) {
                            if (state.runningAppsScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            } else {
                                Text("扫描")
                            }
                        }
                    }
                    Text(
                        "通过 Root / Shizuku 读取后台进程；普通模式只能清理系统允许释放的后台。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.hasDeepAccess()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "未获得特权，点击「扫描」申请 Root / Shizuku 后可查看具体应用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (state.runningAppsScanning) {
                        Spacer(Modifier.height(8.dp))
                        Text("正在扫描进程…", style = MaterialTheme.typography.labelSmall)
                    } else if (state.runningApps.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "未检测到可清理的第三方后台应用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        state.runningApps.take(12).forEach { app ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = app.packageName in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + app.packageName
                                        else selected - app.packageName
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Text(
                                        "PID ${app.pid} · ${FileUtils.formatBytes(app.rssKb * 1024)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    selected = if (selected.size == state.runningApps.size) emptySet()
                                    else state.runningApps.map { it.packageName }.toSet()
                                }
                            ) { Text(if (selected.size == state.runningApps.size) "全不选" else "全选") }
                            Button(
                                enabled = selected.isNotEmpty() && !state.runningAppsCleaning,
                                onClick = {
                                    state.cleanRunningApps(selected) { ok, message ->
                                        state.showMessage(message)
                                        if (ok) selected = emptySet()
                                        scope.launch {
                                            mem = withContext(Dispatchers.IO) { MemoryTools.read(context) }
                                        }
                                    }
                                }
                            ) {
                                if (state.runningAppsCleaning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("清理选中（${selected.size}）")
                            }
                        }
                    }
                }
            }
        }

        lastResult?.let { result ->
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("清理结果", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "释放 ${FileUtils.formatBytes(result.freed)} 运存 · 处理 ${result.killed} 个后台应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        mem = withContext(Dispatchers.IO) { MemoryTools.read(context) }
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新")
                }
            }
        }

        item {
            Text(
                "白名单中的 ${state.whitelist.size} 个应用不会被清理。清理后台进程可能使应用重新启动，系统进程不会受影响。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (showPrivilege) {
        PermissionReminderDialog(
            permissionName = "运存清理 · Root / Shizuku",
            message = "读取其他应用的后台进程并执行 am kill 需要 Root 或 Shizuku 权限。确认后将依次申请可用权限。",
            onDismiss = { showPrivilege = false },
            onConfirm = {
                showPrivilege = false
                state.ensureDeepPrivilege { ok ->
                    if (ok) state.refreshRunningApps()
                    else state.showMessage("未获得 Root / Shizuku 权限")
                }
            }
        )
    }
}
