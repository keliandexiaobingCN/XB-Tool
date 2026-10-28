package com.xb.tool.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.LogBus
import com.xb.tool.core.Screen
import com.xb.tool.core.Severity
import com.xb.tool.core.Threat
import com.xb.tool.scan.ScanMode
import com.xb.tool.ui.PermissionReminderDialog
import com.xb.tool.ui.ScanningOrb
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.TerminalPanel
import com.xb.tool.ui.XBConfirmDialog
import com.xb.tool.ui.XBInfoDialog
import com.xb.tool.ui.severityColor
import com.xb.tool.ui.severityContainer
import com.xb.tool.util.FileUtils
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun ScanScreen(state: AppState, mode: ScanMode) {
    val context = LocalContext.current
    val logs by LogBus.entries.collectAsState()
    val scan = state.scanState
    var pendingDelete by remember { mutableStateOf<Threat?>(null) }
    var showReminder by remember { mutableStateOf(false) }
    var showGateInfo by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        if (state.scanState.running) return@LaunchedEffect
        if (state.scanState.finished && state.scanState.mode == mode) return@LaunchedEffect
        if (mode == ScanMode.DEEP && !state.hasDeepAccess()) return@LaunchedEffect
        state.startScan(mode)
    }

    val gateBlocked = mode == ScanMode.DEEP && !state.hasDeepAccess() && !scan.running
    val progress = if (scan.total > 0) (scan.scanned.toFloat() / scan.total).coerceIn(0f, 1f) else 0f

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            if (mode == ScanMode.DEEP) "强力杀毒" else "普通杀毒",
                            active = mode == ScanMode.DEEP
                        )
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (mode == ScanMode.DEEP) Icons.Default.Bolt else Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (mode == ScanMode.DEEP) "增强模式" else "标准模式",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ScanningOrb(
                        running = scan.running,
                        icon = if (scan.finished && scan.threats.isEmpty()) Icons.Default.CheckCircle
                        else if (mode == ScanMode.DEEP) Icons.Default.Bolt else Icons.Default.Shield
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            gateBlocked -> "需要特权权限"
                            scan.running -> "正在扫描…"
                            scan.finished && scan.threats.isNotEmpty() -> "发现 ${scan.threats.size} 个威胁"
                            scan.finished -> "扫描完成 · 未发现威胁"
                            else -> "准备扫描"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            gateBlocked -> "强力杀毒需要 Root 或 Shizuku 权限"
                            scan.running -> "已检查 ${scan.scanned} 个文件${if (scan.total > 0) " / ${scan.total}" else ""}"
                            scan.finished -> "用时 ${FileUtils.formatDuration(scan.durationMs)} · 共检查 ${scan.scanned} 个文件"
                            else -> "准备中"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (scan.running) {
                        Spacer(Modifier.height(14.dp))
                        if (scan.total > 0) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            scan.current,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (scan.running) {
                            Button(
                                onClick = { state.stopScan() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("停止扫描")
                            }
                        } else if (gateBlocked) {
                            Button(onClick = {
                                if (state.rootRemind) showReminder = true
                                else state.ensureDeepPrivilege { ok ->
                                    if (ok) state.startScan(mode) else showGateInfo = true
                                }
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("申请权限")
                            }
                        } else {
                            Button(onClick = { state.startScan(mode) }) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (scan.finished) "重新扫描" else "开始扫描")
                            }
                        }
                        OutlinedButton(onClick = { state.navigate(Screen.Home) }) {
                            Text("返回首页")
                        }
                    }
                    if (scan.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            scan.error!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (scan.running) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (mode == ScanMode.DEEP) "正在深度扫描受保护目录，请保持屏幕开启"
                            else "正在扫描已安装应用与下载目录，可随时停止",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (scan.finished && scan.threats.isEmpty() && !scan.running && !gateBlocked) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("设备状态良好", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "未发现已知病毒或风险文件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (scan.threats.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GppBad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "威胁列表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${scan.threats.size} 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            items(scan.threats, key = { it.id }) { threat ->
                ThreatCard(
                    threat = threat,
                    onQuarantine = {
                        state.quarantine(threat) { ok ->
                            if (ok) state.showMessage("已隔离 ${threat.name}")
                        }
                    },
                    onDelete = { pendingDelete = threat },
                    onUninstall = {
                        val pkg = threat.packageName.orEmpty()
                        val r = com.xb.tool.guard.ThreatActions.uninstall(context, pkg, threat.name)
                        state.showMessage(r.message)
                    },
                    onFreeze = {
                        val pkg = threat.packageName.orEmpty()
                        val r = com.xb.tool.guard.ThreatActions.freeze(context, pkg, threat.name)
                        state.showMessage(r.message)
                    }
                )
            }
        }

        item {
            TerminalPanel(
                logs = logs,
                title = "扫描日志",
                height = 170.dp,
                running = scan.running,
                onClear = { LogBus.clear() }
            )
        }
    }

    if (showReminder) {
        PermissionReminderDialog(
            permissionName = "强力杀毒 · Root / Shizuku",
            message = "深度扫描系统分区与受保护目录需要 Root 或 Shizuku 权限。确认后将依次尝试申请可用权限。",
            onDismiss = { showReminder = false },
            onConfirm = {
                showReminder = false
                state.ensureDeepPrivilege { ok ->
                    if (ok) state.startScan(mode) else showGateInfo = true
                }
            }
        )
    }

    if (showGateInfo) {
        XBInfoDialog(
            title = "未能获取特权",
            text = "请前往「设置 → Shizuku 授权 / Root 授权」完成授权，或在系统中启动 Shizuku 后重试。",
            onDismiss = {
                showGateInfo = false
                state.navigate(Screen.Settings)
            }
        )
    }

    pendingDelete?.let { threat ->
        XBConfirmDialog(
            title = "删除威胁文件",
            text = "确定删除 ${threat.name}？\n${threat.path}\n\n删除后无法恢复，受保护文件需要 Root / Shizuku 权限。",
            confirmLabel = "删除",
            onConfirm = {
                state.deleteThreat(threat) { ok ->
                    if (ok) state.showMessage("已删除威胁文件")
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ThreatCard(
    threat: Threat,
    onQuarantine: () -> Unit,
    onDelete: () -> Unit,
    onUninstall: () -> Unit,
    onFreeze: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, severityColor(threat.severity).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(severityContainer(threat.severity).copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.GppBad,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = severityColor(threat.severity)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        threat.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${threat.severity.label} · ${threat.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor(threat.severity)
                    )
                }
                StatusPill(threat.severity.label, active = true)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                threat.path,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (threat.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    threat.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (threat.sha256 != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "SHA-256: ${threat.sha256}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onQuarantine,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("隔离", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("删除", style = MaterialTheme.typography.labelMedium)
                }
            }
            // 关联到具体应用时，额外提供「卸载」与「管控（冻结）」两个处置动作。
            // 这两个动作在没有 Root / Shizuku 时会退化为打开系统应用详情页，界面会明确提示。
            if (!threat.packageName.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onUninstall,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("卸载应用", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onFreeze,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("管控", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
