package com.xb.tool.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.ui.glassSurfaceColor
import com.xb.tool.emergency.EmergencyAppInfo
import com.xb.tool.emergency.EmergencyController
import com.xb.tool.emergency.EmergencyResult
import kotlinx.coroutines.launch

/**
 * 紧急救援界面：摇一摇 / 按键触发后弹出的独立页面。
 * 与「紧急救机设置页」分开，这里只放救援动作：
 * 1. 扫描所有应用（含系统应用）
 * 2. 病毒检测结果
 * 3. 手动强制关闭（逐个应用，含系统应用，二次确认）
 */
@Composable
fun EmergencyRescueScreen(
    context: Context,
    onClose: () -> Unit,
    onToast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<EmergencyResult?>(null) }
    var progressText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var pendingKill by remember { mutableStateOf<EmergencyAppInfo?>(null) }
    var killing by remember { mutableStateOf(false) }
    var showSystemApps by remember { mutableStateOf(true) }

    fun runScan() {
        if (scanning) return
        scanning = true
        progressText = "准备扫描…"
        progress = 0f
        scope.launch {
            val r = EmergencyController.scan(context) { done, total, current ->
                progress = if (total > 0) done.toFloat() / total else 0f
                progressText = "正在扫描 $done/$total · $current"
            }
            result = r
            scanning = false
            progressText = ""
            onToast("扫描完成：${r.scannedApps} 个应用，发现 ${r.threats.size} 个威胁")
        }
    }

    LaunchedEffect(Unit) { runScan() }

    val apps = remember(result, showSystemApps) {
        val list = result?.apps.orEmpty()
        if (showSystemApps) list else list.filter { !it.isSystem }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("紧急救机", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "全量扫描应用并检测病毒，可手动强制关闭可疑应用",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(onClick = onClose) { Text("关闭") }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("扫描所有应用（含系统应用）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "系统应用也可能被病毒伪装，默认一并扫描，不做跳过。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (scanning) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(progressText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Spacer(Modifier.height(10.dp))
                        result?.let { r ->
                            Text(
                                "已扫描 ${r.scannedApps} 个应用 · 用时 ${r.elapsedMs / 1000} 秒 · " +
                                    "发现 ${r.threats.size} 个威胁",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { runScan() }, modifier = Modifier.weight(1f)) {
                                Text("重新扫描")
                            }
                            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                                Text("退出救援")
                            }
                        }
                    }
                }
            }
        }

        // 病毒检测结果
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GppBad, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("病毒检测", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${result?.threats?.size ?: 0} 项",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val threats = result?.threats.orEmpty()
                    if (threats.isEmpty()) {
                        Text(
                            if (scanning) "扫描中…" else "未发现已知病毒特征。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        threats.take(5).forEach { threat ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(threat.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(
                                    threat.packageName ?: threat.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (threats.size > 5) {
                            Text("… 共 ${threats.size} 项", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 手动强制关闭
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("手动强制关闭", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { showSystemApps = !showSystemApps }) {
                    Text(if (showSystemApps) "隐藏系统应用" else "显示系统应用")
                }
            }
        }
        item {
            Text(
                "点击应用即可强制关闭（含系统应用）。需要 Root 或 Shizuku 权限；误关系统应用可能影响手机功能，请确认目标后再操作。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (apps.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (scanning) CircularProgressIndicator(Modifier.size(22.dp))
                    else Text("暂无可操作应用", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            items(apps, key = { it.packageName }) { app ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pendingKill = app }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (app.isSystem) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "系统",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text("强制关闭", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    pendingKill?.let { app ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!killing) pendingKill = null },
            title = { Text("强制关闭 ${app.label}？") },
            text = {
                Column {
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (app.isSystem)
                            "这是系统应用。强制关闭可能导致系统功能异常，请确认它确实可疑。"
                        else "将强制停止该应用的所有进程。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !killing,
                    onClick = {
                        killing = true
                        scope.launch {
                            val msg = EmergencyController.forceStop(context, app.packageName)
                            onToast(msg)
                            killing = false
                            pendingKill = null
                        }
                    }
                ) { Text(if (killing) "处理中…" else "确认关闭") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingKill = null }, enabled = !killing) { Text("取消") }
            }
        )
    }
}
