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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
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
import com.xb.tool.ui.XBConfirmDialog
import com.xb.tool.util.FileUtils
import kotlinx.coroutines.launch
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun MemoryCleanScreen(state: AppState) {
    var freed by remember { mutableStateOf<Long?>(null) }
    var showDeepConfirm by remember { mutableStateOf(false) }
    var showPrivilege by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        state.refreshCache()
        if (state.hasDeepAccess()) state.refreshPrivilegedCaches()
    }

    val info = state.cacheInfo
    val ratio = (info.total.toFloat() / (256f * 1024f * 1024f)).coerceIn(0f, 1f)
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
                                color = scheme.primary,
                                startAngle = -90f,
                                sweepAngle = 360f * ratio,
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                FileUtils.formatBytes(info.total),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("可清理缓存", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "清理应用缓存与临时转换文件，释放存储空间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { state.clearCache { amount -> freed = amount } },
                            enabled = !state.cacheCleaning && !state.cacheCalculating,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.cacheCleaning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = scheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("清理本应用缓存")
                        }
                        TextButton(onClick = { state.refreshCache() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    freed?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "已释放 ${FileUtils.formatBytes(it)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary
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
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow("普通缓存", FileUtils.formatBytes(info.cache), Icons.Default.Storage)
                    InfoRow("代码缓存", FileUtils.formatBytes(info.codeCache), Icons.Default.Storage)
                    InfoRow("外部缓存", FileUtils.formatBytes(info.externalCache), Icons.Default.Storage)
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("深度清理（应用缓存）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                if (state.hasDeepAccess()) state.refreshPrivilegedCaches() else showPrivilege = true
                            },
                            enabled = !state.appCachesScanning
                        ) {
                            if (state.appCachesScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            } else {
                                Text("扫描")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "通过 Root / Shizuku 读取系统应用缓存并清理，普通模式只能清理本应用缓存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.hasDeepAccess()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "当前未获得 Root / Shizuku 权限，无法检测其他应用缓存。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (state.appCachesScanning) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在扫描应用缓存…", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (state.appCaches.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "未发现可清理的应用缓存（或 Shizuku 无权读取，建议使用 Root）。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        state.appCaches.take(8).forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Text(
                                    FileUtils.formatBytes(entry.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        if (state.appCaches.size > 8) {
                            Text(
                                "共 ${state.appCaches.size} 个应用 · ${FileUtils.formatBytes(state.appCaches.sumOf { it.size })}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (state.hasDeepAccess()) showDeepConfirm = true else showPrivilege = true
                            },
                            enabled = !state.appCachesCleaning
                        ) {
                            if (state.appCachesCleaning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.appCachesCleaning) "正在清理…" else "深度清理应用缓存")
                        }
                    }
                }
            }
        }

        item {
            Text(
                "提示：清理操作不会删除应用数据、账号与已下载的文件。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (showDeepConfirm) {
        XBConfirmDialog(
            title = "确认深度清理",
            text = "将清理其他应用的缓存文件。此操作不可撤销，可能导致应用重新加载数据。",
            confirmLabel = "开始清理",
            onConfirm = {
                showDeepConfirm = false
                state.clearPrivilegedCaches { _, _ -> }
            },
            onDismiss = { showDeepConfirm = false }
        )
    }

    if (showPrivilege) {
        PermissionReminderDialog(
            permissionName = "深度缓存清理 · Root / Shizuku",
            message = "深度清理其他应用的缓存目录需要 Root 或 Shizuku 权限。请确认你了解系统级操作的风险。",
            onDismiss = { showPrivilege = false },
            onConfirm = {
                showPrivilege = false
                state.ensureDeepPrivilege { ok ->
                    if (ok) showDeepConfirm = true
                    else state.showMessage("未获得 Root / Shizuku 权限")
                }
            }
        )
    }
}
