package com.xb.tool.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.system.SystemResources
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.glassSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统资源检测页。
 *
 * 实时刷新：每 2 秒采一次样。数值不可得时显示「不可用」而不是 0，
 * 因为不同 SoC 的 sysfs 差异很大，编造数字会误导用户。
 */
@Composable
fun SystemMonitorScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<SystemResources.Snapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var autoRefresh by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { SystemResources.sample(context) }
            snapshot = result
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(autoRefresh) {
        if (!autoRefresh) return@LaunchedEffect
        while (isActive) {
            delay(2_000)
            val result = withContext(Dispatchers.IO) { SystemResources.sample(context) }
            snapshot = result
            loading = false
        }
    }

    val data = snapshot

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "系统资源检测",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            if (data?.privileged == true) "特权 ${data.channel}" else "普通权限",
                            data?.privileged == true
                        )
                    }
                    Text(
                        "每 2 秒自动刷新。部分传感器（GPU 频率、功率、部分温度）需要 Root / Shizuku 才能读取，" +
                            "读不到时会显示「不可用」而不是猜一个数字。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { refresh() }, enabled = !loading) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (loading) "读取中…" else "立即刷新")
                        }
                        OutlinedButton(onClick = { autoRefresh = !autoRefresh }) {
                            Text(if (autoRefresh) "暂停刷新" else "继续刷新")
                        }
                    }
                }
            }
        }

        item {
            val privileged = data?.privileged == true
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (privileged) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    (if (privileged) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error).copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (privileged) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (privileged) "特权通道已就绪：${data?.channel ?: "特权"}"
                            else "当前只有普通权限，部分数据读不到",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(if (privileged) "已授权" else "需 Root/Shizuku", privileged)
                    }
                    if (!privileged) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "这个页面不需要任何权限也能打开：CPU 型号、核心频率、内存、电池温度都是公开的。" +
                                    "只有标了「需特权」的字段（GPU 频率/占用、部分温度）才需要 Root、Shizuku 或无线调试通道。" +
                                    "也就是说，开不开权限的差别只体现在那些字段上。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Text(
                        if (privileged) {
                            "GPU 频率、GPU 占用、部分温度传感器走特权通道读取；读不到的字段仍然显示「不可用」，不会编造数字。"
                        } else {
                            "下面标了「需特权」的字段读不到时显示「不可用」，不会编造数字。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!privileged) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { state.navigate(Screen.PrivilegeService) }) {
                                Text("ADB 增强服务")
                            }
                            OutlinedButton(onClick = { state.navigate(Screen.Root) }) {
                                Text("Root / Shizuku")
                            }
                        }
                    }
                }
            }
        }

        if (data == null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在读取系统信息…", style = MaterialTheme.typography.bodySmall)
                }
            }
            return@LazyColumn
        }

        item { SectionHeader("处理器") }
        item {
            InfoCard {
                DataRow("CPU 型号", data.cpuModel)
                DataRow("架构", data.cpuAbi)
                DataRow("核心数", "${data.cpuCores} 核")
                DataRow("总体占用", percentText(data.cpuUsagePercent))
                if (data.cpuUsagePercent != null) {
                    Bar(data.cpuUsagePercent / 100f)
                }
                DataRow("CPU 温度", tempText(data.cpuTempC, data.privileged))
            }
        }

        item { SectionHeader("核心频率") }
        item {
            InfoCard {
                data.cores.forEach { core ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CPU${core.index}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            core.curFreqMhz?.let { "$it MHz" } ?: "不可用",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            core.maxFreqMhz?.let { "最高 $it MHz" } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (core.curFreqMhz != null && core.maxFreqMhz != null && core.maxFreqMhz > 0) {
                        Bar(core.curFreqMhz.toFloat() / core.maxFreqMhz)
                    }
                }
            }
        }

        item { SectionHeader("图形处理器") }
        item {
            InfoCard {
                DataRow("GPU 型号", data.gpuModel)
                DataRow("当前频率", data.gpuFreqMhz?.let { "$it MHz" } ?: missingText(data.privileged))
                DataRow("最高频率", data.gpuMaxFreqMhz?.let { "$it MHz" } ?: missingText(data.privileged))
                DataRow("占用率", percentText(data.gpuLoadPercent, data.privileged))
                if (data.gpuLoadPercent != null) Bar(data.gpuLoadPercent / 100f)
                DataRow("GPU 温度", tempText(data.gpuTempC, data.privileged))
            }
        }

        item { SectionHeader("内存") }
        item {
            InfoCard {
                DataRow("总内存", "${data.memTotalMb} MB")
                DataRow("已用", "${data.memUsedMb} MB")
                DataRow("可用", "${data.memAvailableMb} MB")
                DataRow("占用率", String.format("%.1f%%", data.memPercent))
                Bar(data.memPercent / 100f)
                if (data.swapTotalMb > 0) {
                    DataRow("交换分区", "${data.swapUsedMb} / ${data.swapTotalMb} MB")
                }
                DataRow("系统运行", data.uptimeText)
            }
        }

        item { SectionHeader("电池与功耗") }
        item {
            InfoCard {
                DataRow("电池温度", tempText(data.batteryTempC))
                DataRow("实时功率", data.batteryPowerW?.let { String.format("%.2f W", it) } ?: "不可用")
            }
        }

        item {
            Text(
                "说明：功率为电池电压 × 电流的估算值，不是 SoC 的精确功耗；" +
                    "不同厂商对电流符号与采样频率的实现不同，读数仅供参考。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun DataRow(title: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Bar(fraction: Float) {
    val safe = fraction.coerceIn(0f, 1f)
    // 数值每 2 秒跳一次，直接给滑块会让「百分比文字」和「条的末梢」看起来不同步，
    // 补一段 500ms 的缓动，两者视觉上就对齐了。
    val animated by animateFloatAsState(
        targetValue = safe,
        animationSpec = tween(durationMillis = 500),
        label = "bar"
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = Modifier.fillMaxWidth().height(4.dp),
        color = when {
            safe > 0.85f -> Color(0xFFFF6B6B)
            safe > 0.6f -> Color(0xFFFFB74D)
            else -> MaterialTheme.colorScheme.primary
        }
    )
}

private fun percentText(value: Float?, privileged: Boolean = true): String =
    value?.let { String.format("%.1f%%", it) } ?: missingText(privileged)

private fun tempText(value: Float?, privileged: Boolean = true): String =
    value?.let { String.format("%.1f °C", it) } ?: missingText(privileged)

/** 读不到时区分「这条通道就是读不到」和「需要特权才能读」。 */
private fun missingText(privileged: Boolean): String =
    if (privileged) "不可用" else "不可用 · 需特权"
