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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.CheckItem
import com.xb.tool.core.Screen
import com.xb.tool.root.IntegrityDetector
import com.xb.tool.root.IntegrityInfo
import com.xb.tool.ui.PermissionReminderDialog
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBConfirmDialog
import com.xb.tool.ui.XBInfoDialog
import com.xb.tool.util.FileUtils
import com.xb.tool.util.Intents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun RootScreen(state: AppState) {
    val context = LocalContext.current
    val info = state.rootInfo
    var integrity by remember { mutableStateOf<IntegrityInfo?>(null) }
    var integrityLoading by remember { mutableStateOf(true) }
    var showRootReminder by remember { mutableStateOf(false) }
    var reminderTarget by remember { mutableStateOf("root") }
    var showShizukuMissing by remember { mutableStateOf(false) }
    var showShizukuInfo by remember { mutableStateOf(false) }
    var showRootResult by remember { mutableStateOf<String?>(null) }

    fun startRootAuth() {
        state.requestRootAccess { ok ->
            showRootResult = if (ok) "Root 授权成功 · ${state.rootInfo.managerName} ${state.rootInfo.managerVersion}"
            else "Root 授权未通过，请检查管理器是否允许本应用"
        }
    }

    fun startShizukuAuth() {
        if (!state.shizukuRunning) {
            showShizukuMissing = true
        } else if (state.shizukuGranted) {
            showShizukuInfo = true
        } else if (state.rootRemind) {
            reminderTarget = "shizuku"
            showRootReminder = true
        } else {
            state.requestShizukuAccess { ok ->
                showRootResult = if (ok) "Shizuku 服务已就绪" else "Shizuku 授权未完成"
            }
        }
    }

    LaunchedEffect(Unit) {
        integrity = withContext(Dispatchers.IO) { IntegrityDetector.detect(context) }
        integrityLoading = false
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RootHero(state)
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("设备完整性与残留", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (integrityLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        } else {
                            StatusPill(
                                when (integrity?.level) {
                                    2 -> "有修改"
                                    1 -> "需确认"
                                    else -> "较纯净"
                                },
                                active = integrity?.level == 0
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        integrity?.verdict ?: "正在检查 Bootloader、构建签名、su、管理器与模块残留…",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = when (integrity?.level) {
                            2 -> MaterialTheme.colorScheme.error
                            1 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    integrity?.checks?.forEach { check ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (check.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (check.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(check.title, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    check.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    integrity?.leftovers?.takeIf { it.isNotEmpty() }?.let { leftovers ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "残留路径：${leftovers.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow("环境状态", when {
                        info.rootGranted && info.healthy -> "正常 · 未检测到异常"
                        info.rootGranted -> "已 Root · 部分检查异常"
                        else -> "未授权 Root"
                    }, Icons.Default.VerifiedUser)
                    DividerRow()
                    InfoRow("管理器", info.managerName, Icons.Default.AdminPanelSettings)
                    DividerRow()
                    InfoRow("管理器版本", info.managerVersion, Icons.Default.Tag)
                    DividerRow()
                    InfoRow(
                        "内核版本",
                        info.kernel,
                        Icons.Default.Memory,
                        onClick = { state.navigate(Screen.Kernel) },
                        chevron = true
                    )
                    DividerRow()
                    InfoRow("su 路径", info.suPath ?: "未找到", Icons.Default.Shield)
                    DividerRow()
                    InfoRow("SELinux", info.selinux, Icons.Default.Shield)
                    DividerRow()
                    InfoRow("系统", "Android ${info.androidVersion} · ${info.abi}", Icons.Default.Settings)
                    DividerRow()
                    InfoRow("安全补丁", info.securityPatch, Icons.Default.Tag)
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "环境检查",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (info.checks.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在检测…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        info.checks.forEach { check ->
                            CheckRow(check)
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("授权前弹出提醒", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "在授予 Root 或 Shizuku 权限前，先以双滑块确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.rootRemind,
                        onCheckedChange = { state.applyRootRemind(it) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { state.refreshRoot(true) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.rootChecking
                ) {
                    if (state.rootChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("重新检测")
                }
                OutlinedButton(
                    onClick = { Intents.openAppDetails(state.context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("系统设置")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (state.rootRemind) {
                            reminderTarget = "root"
                            showRootReminder = true
                        } else startRootAuth()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.rootInfo.rootGranted) "重新授权 Root" else "申请 Root")
                }
                OutlinedButton(
                    onClick = { startShizukuAuth() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.shizukuGranted) "Shizuku 已授权" else "申请 Shizuku")
                }
            }
        }

        item {
            Text(
                "检测内容包括 su 二进制、Root Shell、管理器、SELinux、/data/adb 可写状态与内核信息。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (showRootReminder) {
        PermissionReminderDialog(
            permissionName = "Root / Shizuku 授权",
            message = "授权后本应用将能够执行深度扫描、冻结应用等系统级操作。请确认设备已解锁并了解 Root 风险。",
            onDismiss = { showRootReminder = false },
            onConfirm = {
                showRootReminder = false
                if (reminderTarget == "shizuku") {
                    state.requestShizukuAccess { ok ->
                        showRootResult = if (ok) "Shizuku 服务已就绪" else "Shizuku 授权未完成"
                    }
                } else {
                    startRootAuth()
                }
            }
        )
    }

    if (showShizukuMissing) {
        XBConfirmDialog(
            title = "Shizuku 服务未运行",
            text = "请安装并启动 Shizuku，然后返回本应用申请授权。",
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
            title = "Shizuku 状态",
            text = "已授权\n服务版本：${com.xb.tool.root.ShizukuUtils.version()}\nUID：${com.xb.tool.root.ShizukuUtils.uid()}",
            onDismiss = { showShizukuInfo = false }
        )
    }

    showRootResult?.let { result ->
        XBInfoDialog(title = "授权结果", text = result, onDismiss = { showRootResult = null })
    }
}

@Composable
private fun RootHero(state: AppState) {
    val info = state.rootInfo
    val scheme = MaterialTheme.colorScheme
    val healthy = info.rootGranted && info.healthy
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(
                        if (info.rootGranted) scheme.primaryContainer else scheme.surfaceVariant,
                        scheme.surface
                    )
                )
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                if (info.rootGranted) scheme.primary.copy(alpha = 0.18f)
                                else scheme.onSurface.copy(alpha = 0.06f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (healthy) Icons.Default.VerifiedUser else Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = if (info.rootGranted) scheme.primary else scheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                healthy -> "环境正常 · 未检测到异常"
                                info.rootGranted -> "已获取 Root · 部分检查异常"
                                else -> "未检测到 Root 权限"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            info.deviceModel,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(if (info.rootGranted) "Root" else "未 Root", active = info.rootGranted)
                    StatusPill(
                        if (state.shizukuGranted) "Shizuku" else "无 Shizuku",
                        active = state.shizukuGranted
                    )
                    StatusPill(
                        if (info.managerName != "未检测到") info.managerName else "无管理器",
                        active = info.managerName != "未检测到"
                    )
                }
                if (info.updatedAt > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "最近检测 ${FileUtils.formatTimeFull(info.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Text("›", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DividerRow() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    )
}

@Composable
private fun CheckRow(check: CheckItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (check.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (check.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(10.dp))
        Text(check.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(100.dp))
        Text(
            check.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
