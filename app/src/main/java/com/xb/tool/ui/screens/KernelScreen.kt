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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.ui.MonoBlock
import com.xb.tool.ui.StatusPill
import com.xb.tool.util.FileUtils
import kotlinx.coroutines.launch
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun KernelScreen(state: AppState) {
    val info = state.rootInfo
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var modules by remember { mutableStateOf<List<String>>(emptyList()) }
    var moduleError by remember { mutableStateOf<String?>(null) }
    var loadingModules by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadModules() {
        if (loadingModules) return
        loadingModules = true
        scope.launch {
            val result = state.privilegedExec("ls -1 /data/adb/modules 2>/dev/null")
            loadingModules = false
            if (result.success) {
                modules = result.out.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
                moduleError = null
            } else {
                modules = emptyList()
                moduleError = result.text.ifBlank { "需要 Root / Shizuku 权限" }
            }
        }
    }

    LaunchedEffect(Unit) { loadModules() }

    val allText = buildString {
        appendLine("XB工具 · 内核与 Root 信息")
        appendLine("设备：${info.deviceModel}")
        appendLine("系统：Android ${info.androidVersion} (SDK ${info.sdk})")
        appendLine("内核：${info.kernel}")
        appendLine("/proc/version：${info.kernelFull}")
        appendLine("架构：${info.abi}")
        appendLine("安全补丁：${info.securityPatch}")
        appendLine("Root：${if (info.rootGranted) "已授权" else "未授权"}")
        appendLine("管理器：${info.managerName} ${info.managerVersion}")
        appendLine("管理器包名：${info.managerPackage ?: "—"}")
        appendLine("管理器路径：${info.managerPath ?: "—"}")
        appendLine("su 路径：${info.suPath ?: "—"}")
        appendLine("SELinux：${info.selinux}")
        appendLine("Shizuku：${if (state.shizukuGranted) "已授权" else if (state.shizukuRunning) "运行中未授权" else "未运行"}")
        appendLine("指纹：${info.fingerprint}")
        if (modules.isNotEmpty()) {
            appendLine("模块：${modules.joinToString(", ")}")
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
                            listOf(scheme.primaryContainer.copy(alpha = 0.85f), scheme.surface)
                        )
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(scheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(27.dp),
                                    tint = scheme.primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("内核版本", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                Text(
                                    info.kernel.ifBlank { "正在读取…" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            StatusPill(if (info.rootGranted) "ROOT" else "普通", active = info.rootGranted)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("Android ${info.androidVersion}", active = false)
                            StatusPill(info.abi, active = false)
                        }
                    }
                }
            }
        }

        item {
            InfoCard("内核与系统") {
                InfoLine("os.version", info.kernel)
                InfoLine("架构", info.abi)
                InfoLine("Android", "${info.androidVersion} · SDK ${info.sdk}")
                InfoLine("安全补丁", info.securityPatch)
                InfoLine("设备", info.deviceModel)
                InfoLine("构建指纹", info.fingerprint)
            }
        }

        item {
            InfoCard("/proc/version") {
                MonoBlock(info.kernelFull.ifBlank { "无法读取" })
            }
        }

        item {
            InfoCard("Root 管理器") {
                InfoLine("管理器", info.managerName)
                InfoLine("管理器版本", info.managerVersion)
                InfoLine("包名", info.managerPackage ?: "—")
                InfoLine("安装路径", info.managerPath ?: "—")
                InfoLine("su 二进制", info.suPath ?: "未找到")
                InfoLine("SELinux", info.selinux)
                InfoLine(
                    "Shizuku",
                    if (state.shizukuGranted) "已授权" else if (state.shizukuRunning) "运行中" else "未运行"
                )
            }
        }

        item {
            InfoCard("已安装模块", trailing = {
                if (loadingModules) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { loadModules() }) { Text("刷新") }
                }
            }) {
                when {
                    moduleError != null -> Text(
                        moduleError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    modules.isEmpty() -> Text(
                        if (state.hasDeepAccess()) "未发现模块或目录为空" else "授予 Root / Shizuku 后可查看模块",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        modules.forEach { module ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(module, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { state.refreshRoot(true) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.rootChecking
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重新检测")
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(allText))
                        state.showMessage("信息已复制到剪贴板")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制信息")
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "内核与模块信息来自系统文件与特权 Shell，仅供参考。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
