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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.xb.tool.core.InterceptRecord
import com.xb.tool.core.InterceptStore
import com.xb.tool.core.Screen
import com.xb.tool.core.Severity
import com.xb.tool.core.Threat
import com.xb.tool.scan.QuarantineStore
import com.xb.tool.ui.EmptyState
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBConfirmDialog
import com.xb.tool.ui.XBInfoDialog
import com.xb.tool.util.FileUtils
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun InterceptScreen(state: AppState) {
    val items by InterceptStore.items.collectAsState()
    var showClear by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<InterceptRecord?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GppBad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "已拦截 ${items.size} 项",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "实时检测发现的风险文件会记录在这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showClear = true }, enabled = items.isNotEmpty()) {
                        Text("清空")
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.Security,
                    "暂无拦截记录",
                    "开启实时病毒检测后，新下载或安装的可疑文件会被拦截"
                )
            }
        } else {
            items(items, key = { it.id }) { record ->
                Surface(
                    onClick = { detail = record },
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.GppBad,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                record.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            StatusPill(record.action, active = true)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            record.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            FileUtils.formatTimeFull(record.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                val entry = QuarantineStore.record(
                                    state.context,
                                    Threat(
                                        id = record.id.toString(),
                                        name = record.name,
                                        path = record.path,
                                        severity = Severity.MEDIUM,
                                        description = record.signature,
                                        source = "实时拦截"
                                    ),
                                    moveFile = true
                                )
                                state.showMessage(
                                    if (entry.moved) "已隔离 ${entry.name}"
                                    else "已登记到隔离区（无权限移动文件）"
                                )
                            }) { Text("隔离") }
                            TextButton(onClick = { state.navigate(Screen.Quarantine) }) {
                                Text("查看隔离区")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClear) {
        XBConfirmDialog(
            title = "清空拦截记录",
            text = "仅清除记录，不会恢复已拦截或删除的文件。",
            confirmLabel = "清空",
            onConfirm = { InterceptStore.clear(state.context) },
            onDismiss = { showClear = false }
        )
    }

    detail?.let { record ->
        XBInfoDialog(
            title = record.name,
            text = "处理：${record.action}\n时间：${FileUtils.formatTimeFull(record.time)}\n\n路径：\n${record.path}\n\n特征：${record.signature}",
            onDismiss = { detail = null }
        )
    }
}
