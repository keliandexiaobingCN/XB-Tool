package com.xb.tool.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.ui.MonoBlock
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.glassSurfaceColor

/**
 * 无线调试授权页（免 Root 的特权通道）。
 *
 * 流程：系统设置打开「无线调试」→ 点「使用配对码配对设备」→ 回到本页扫描 →
 * 输入 6 位配对码 → 配对并连接 → 特权通道就绪。
 */
@Composable
fun AdbPairScreen(state: AppState) {
    var code by remember { mutableStateOf("") }
    var testOutput by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    var logLines by remember { mutableStateOf(com.xb.tool.privilege.PairLog.read(context)) }
    val nearbyPermission = remember {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            "android.permission.NEARBY_WIFI_DEVICES" else null
    }
    var pendingScan by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingScan) {
            pendingScan = false
            if (granted) state.discoverAdbPairing()
            else message = "未授予「附近的设备」权限，无法发现无线调试配对服务。"
        }
    }

    fun scanWithPermission() {
        message = null
        val perm = nearbyPermission
        if (perm == null) {
            state.discoverAdbPairing()
            return
        }
        if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
            state.discoverAdbPairing()
        } else {
            pendingScan = true
            permissionLauncher.launch(perm)
        }
    }

    LaunchedEffect(Unit) {
        state.refreshAdb()
        logLines = com.xb.tool.privilege.PairLog.read(context)
    }

    // 配对在后台服务里进行，这里定时拉一次日志，页面上能实时看到进度。
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2_000L)
            logLines = com.xb.tool.privilege.PairLog.read(context)
            if (!state.adbConnected && com.xb.tool.privilege.AdbShell.isConnected()) {
                state.refreshAdb()
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "无线调试授权",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "不 Root 也能获得 adb shell 级权限，用于深度杀毒、应用冻结等增强功能。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(if (state.adbConnected) "已连接" else "未连接", active = state.adbConnected)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        state.adbStatus.ifBlank { "尚未连接" },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.adbConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "本机 IP：${state.adbLocalIp.ifBlank { "未获取" }} · 无线调试端口：${if (state.adbTlsPort > 0) state.adbTlsPort.toString() else "未开启"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "ADB 增强服务",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "启动页面会显示：用什么方式在跑、UID 是多少、能不能重启、" +
                                    "系统有没有限制、每一步的启动时间线，以及完整的启动日志。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { state.navigate(Screen.PrivilegeService) }) {
                            Text("打开 ADB 增强服务")
                        }
                        OutlinedButton(onClick = { state.navigate(Screen.PrivilegeApps) }) {
                            Text("授权应用")
                        }
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
                Column(Modifier.padding(16.dp)) {
                    Text("操作步骤", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    StepRow("1", "进入系统设置 → 开发者选项 → 无线调试，打开开关")
                    StepRow("2", "点「使用配对码配对设备」，系统显示 6 位配对码")
                    StepRow("3", "保持该弹窗不要关闭，下拉通知栏找到「无线调试配对」通知")
                    StepRow("4", "在通知里直接输入 6 位码并发送，后台自动完成配对与连接")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "为什么要在通知栏输入：Android 的配对弹窗一离开设置界面就会被销毁，" +
                            "配对端口随之关闭，所以回到本应用输入时端口已经不存在了。下拉通知栏时弹窗仍然存活，这是唯一可行的路径。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "推荐：通知栏内配对",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点下面的按钮后会发一条常驻通知，并自动扫描配对服务。" +
                            "下拉通知栏 → 展开这条通知 → 输入 6 位配对码 → 发送，全程不用离开设置页面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            com.xb.tool.privilege.AdbPairingService.start(context)
                            message = "已开始后台扫描配对服务，请下拉通知栏查看"
                        }) { Text("开始通知栏配对") }
                        OutlinedButton(onClick = {
                            com.xb.tool.privilege.AdbPairingService.stop(context)
                        }) { Text("停止") }
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
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("配对服务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (state.adbDiscovering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = { scanWithPermission() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("扫描配对服务")
                            }
                        }
                    }
                    if (state.adbPairingServices.isEmpty()) {
                        Text(
                            "未发现配对服务。请先打开系统的「使用配对码配对设备」弹窗，再点「扫描配对服务」。" +
                                "也可以直接用下方「直连（已配对）」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.adbPairingServices.forEach { service ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${service.name} · ${service.host}:${service.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.adbManualPair,
                        onValueChange = { state.saveAdbManualPair(it) },
                        label = { Text("配对地址（IP:端口，系统弹窗里显示）") },
                        placeholder = { Text("例如 10.0.2.15:45397") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Uri
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.isDigit() }.take(6)
                        },
                        label = { Text("6 位配对码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                message = null
                                state.pairAdb(code) { ok, msg ->
                                    message = msg
                                    if (ok) {
                                        code = ""
                                        testOutput = null
                                    }
                                }
                            },
                            enabled = code.length == 6 && !state.adbBusy
                        ) {
                            if (state.adbBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (state.adbBusy) "处理中…" else "配对并连接")
                        }
                        OutlinedButton(
                            onClick = { state.connectAdbDirect() },
                            enabled = !state.adbBusy && !state.adbConnected
                        ) { Text("直连（已配对）") }
                        OutlinedButton(
                            onClick = { state.reconnectAdb() },
                            enabled = !state.adbBusy && !state.adbConnected
                        ) { Text("用上次地址重连") }
                        if (state.adbConnected) {
                            OutlinedButton(onClick = { state.disconnectAdb() }) { Text("断开") }
                        }
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.adbConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
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
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("通道自检", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                testOutput = "执行中…"
                                state.adbExec("id") { testOutput = it }
                            },
                            enabled = state.adbConnected
                        ) { Text("执行 id") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "无线调试通道以 shell（uid 2000）身份执行命令，能力与电脑上的 adb shell 相同：" +
                            "可读取应用列表、冻结应用、访问 /data/app 等；不能修改 /system。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    testOutput?.let {
                        Spacer(Modifier.height(8.dp))
                        MonoBlock(it)
                    }
                }
            }
        }

        item { SectionHeader("配对日志") }
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
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("配对日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            com.xb.tool.privilege.PairLog.clear(context)
                            logLines = com.xb.tool.privilege.PairLog.read(context)
                        }) { Text("清空") }
                        TextButton(onClick = {
                            logLines = com.xb.tool.privilege.PairLog.read(context)
                        }) { Text("刷新") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "每一步的扫描、配对、连接结果都会记录在这里；失败时可以直接看到原因。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (logLines.isEmpty()) {
                        Text(
                            "还没有日志。点上面的「开始通知栏配对」或「配对并连接」后就会记录。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        MonoBlock(logLines.takeLast(40).joinToString("\n"))
                    }
                }
            }
        }

        item { SectionHeader("常见问题") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    FaqRow(
                        "扫描不到配对服务？",
                        "配对服务只在「使用配对码配对设备」弹窗打开时广播，且弹窗离开设置界面就会关闭。" +
                            "所以请用「开始通知栏配对」，在下拉通知栏里输入配对码。"
                    )
                    FaqRow(
                        "提示配对码不正确？",
                        "配对码 6 位、有效期约 1 分钟。超时后关闭弹窗重新打开即可获得新码。"
                    )
                    FaqRow(
                        "重启后还要重新配对吗？",
                        "配对密钥已保存在本机。只要无线调试保持开启，重启后一般可直接「用上次地址重连」；" +
                            "若端口变化，重新扫描连接端口即可。"
                    )
                    FaqRow(
                        "和 Shizuku 有什么区别？",
                        "Shizuku 需要一个常驻的特权进程做代理；本通道由系统 adbd 直接提供，省电但每次开机" +
                            "需要无线调试处于开启状态。"
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "当前设备：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun StepRow(index: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(20.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    index,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FaqRow(question: String, answer: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(question, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(
            answer,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
