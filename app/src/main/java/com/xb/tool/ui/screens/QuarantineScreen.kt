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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.scan.QuarantineEntry
import com.xb.tool.scan.QuarantineStore
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.XBConfirmDialog
import com.xb.tool.util.FileUtils
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun QuarantineScreen(state: AppState) {
    var entries by remember { mutableStateOf<List<QuarantineEntry>>(emptyList()) }
    var clearConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        entries = QuarantineStore.entries(state.context)
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("隔离区", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "拦截和手动隔离的样本会显示在这里；可恢复或彻底删除。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${entries.size} 项", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "无 Root / Shizuku 权限时，系统目录样本只能登记，无法移动文件。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        message?.let {
            item {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        if (entries.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("暂无隔离样本", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "在扫描结果或拦截记录中点击“隔离”，样本会在这里显示。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { reload() }) { Text("刷新") }
                    OutlinedButton(onClick = { clearConfirm = true }) { Text("清空隔离区") }
                }
            }
            items(entries, key = { it.id }) { entry ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                entry.threatName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (entry.moved) "已隔离" else "已登记",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.moved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            entry.originalPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            FileUtils.formatTimeFull(entry.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (entry.description.isNotBlank()) {
                            Text(
                                entry.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (entry.moved) {
                                TextButton(onClick = {
                                    val ok = QuarantineStore.restore(state.context, entry)
                                    message = if (ok) "已恢复 ${entry.name}" else "恢复失败：原路径不可写"
                                    reload()
                                }) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("恢复")
                                }
                            } else {
                                TextButton(onClick = {
                                    val source = java.io.File(entry.originalPath)
                                    if (!source.isFile) {
                                        message = "原文件已不存在，只能删除记录"
                                    } else {
                                        QuarantineStore.record(
                                            state.context,
                                            com.xb.tool.core.Threat(
                                                id = entry.id,
                                                name = entry.threatName,
                                                path = entry.originalPath,
                                                severity = com.xb.tool.core.Severity.MEDIUM,
                                                description = entry.description
                                            ),
                                            moveFile = true
                                        )
                                        message = "已移入隔离区"
                                        reload()
                                    }
                                }) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("移入隔离")
                                }
                            }
                            TextButton(onClick = {
                                QuarantineStore.delete(state.context, entry)
                                message = "已删除 ${entry.name}"
                                reload()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (entry.moved) "删除" else "删除记录")
                            }
                        }
                    }
                }
            }
        }
    }

    if (clearConfirm) {
        XBConfirmDialog(
            title = "清空隔离区",
            text = "会删除所有已隔离文件并清空记录，此操作不可撤销。",
            confirmLabel = "清空",
            onConfirm = {
                QuarantineStore.clear(state.context)
                clearConfirm = false
                message = "隔离区已清空"
                reload()
            },
            onDismiss = { clearConfirm = false }
        )
    }
}
