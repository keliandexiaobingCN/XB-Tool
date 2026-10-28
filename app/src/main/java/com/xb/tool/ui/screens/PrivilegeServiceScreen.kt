package com.xb.tool.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.privilege.AdbPairingService
import com.xb.tool.privilege.AdbShell
import com.xb.tool.privilege.PairLog
import com.xb.tool.privilege.PrivilegeService
import com.xb.tool.privilege.PrivilegeService.StepState
import com.xb.tool.ui.MonoBlock
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.glassSurfaceColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ADB 增强服务（原来是「特权服务」+「无线调试授权」两页，现在合成一页）。
 *
 * 三条通道：
 *  - Root：本应用自己 fork `su`，UID 0，能力最完整；
 *  - 无线调试：本应用自己连系统的 adbd，UID 2000（shell），免 Root；
 *  - 有线 ADB：电脑上 `adb tcpip 5555` 后连本机端口，UID 2000。
 *
 * 说明：不是把 Shizuku 的二进制打包进来了 —— 那需要单独 APK 与系统级安装，
 * 普通应用做不到。本页做的是「本应用自己那条通道」的启动、配对、重启与诊断。
 */
@Composable
fun PrivilegeServiceScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(PrivilegeService.Mode.WIRELESS) }
    var steps by remember { mutableStateOf<List<PrivilegeService.Step>>(emptyList()) }
    var starting by remember { mutableStateOf(false) }
    var statusTick by remember { mutableStateOf(0) }
    var logLines by remember { mutableStateOf(PairLog.read(context)) }
    var showError by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }

    var code by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(state.adbManualPair) }
    var pairResult by remember { mutableStateOf<String?>(null) }

    val status = remember(statusTick, state.adbConnected, state.shizukuGranted, state.rootInfo.rootGranted) {
        PrivilegeService.status(context)
    }
    val caps = remember(statusTick, status.running) {
        if (status.running) PrivilegeService.adbCapabilities(context) else emptyList()
    }
    val devOptions = remember(statusTick) { PrivilegeService.developerOptionsEnabled(context) }

    LaunchedEffect(Unit) {
        logLines = PairLog.read(context)
        state.refreshAdb()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000L)
            statusTick++
            logLines = PairLog.read(context)
        }
    }


    fun launchStart(target: PrivilegeService.Mode) {
        if (starting) return
        if (!devOptions) {
            state.showMessage("开发者选项未开启，正在跳转到系统设置")
            PrivilegeService.openDeveloperSettings(context)
            return
        }
        // 无线调试还没配对时，先把「输入配对码」放到通知栏里。
        // 用户正在系统设置里看配对码，不该被迫切回应用再手输一遍。
        if (target == PrivilegeService.Mode.WIRELESS && !AdbShell.isConnected()) {
            AdbPairingService.start(context)
            state.showMessage("已把配对入口放到通知栏：下拉通知栏点「输入配对码并配对」，配对成功后会自动继续启动")
        }
        starting = true
        resultText = null
        steps = emptyList()
        scope.launch {
            val ok = PrivilegeService.start(context, target) { snapshot -> steps = snapshot }
            starting = false
            statusTick++
            resultText = if (ok) "ADB 增强服务已就绪" else "启动失败，请查看下方日志"
            state.refreshAdb()
        }
    }

    // 通知栏配对成功后，自动接着把服务启动起来，用户不用再点一次「启动服务」。
    var lastConnected by remember { mutableStateOf(state.adbConnected) }
    LaunchedEffect(state.adbConnected) {
        val now = state.adbConnected
        if (now && !lastConnected && mode == PrivilegeService.Mode.WIRELESS && !starting) {
            lastConnected = now
            state.showMessage("通知栏配对成功，正在自动启动服务…")
            launchStart(PrivilegeService.Mode.WIRELESS)
            return@LaunchedEffect
        }
        lastConnected = now
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------------- 状态总览 ----------------
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "ADB 增强服务",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "无线调试配对 + Root + 有线 ADB 统一成一条通道，供深度扫描、冻结等增强功能使用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(if (status.running) "运行中" else "未运行", status.running)
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoLine("运行方式", if (status.channel.isBlank() || status.channel == "无") "—" else status.channel)
                    InfoLine("身份 (UID)", status.uid)
                    InfoLine("进程", if (status.running) status.pid.toString() else "—")
                    InfoLine("启动时间", timeText(status.startedAt))
                    InfoLine("开发者选项", if (devOptions) "已开启" else "未开启")
                    if (status.lastError.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                status.lastError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { showError = true }) { Text("详情") }
                        }
                    }
                }
            }
        }

        // ---------------- 环境准备 ----------------
        item { SectionHeader("环境准备") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    EnvRow(
                        title = "开发者选项",
                        detail = if (devOptions) "已开启" else "未开启：无线调试与 ADB 都在这个开关下面",
                        ok = devOptions,
                        action = if (devOptions) null else "去开启",
                        onAction = { PrivilegeService.openDeveloperSettings(context) }
                    )
                    Spacer(Modifier.height(10.dp))
                    EnvRow(
                        title = "无线调试",
                        detail = if (state.adbTlsPort > 0) "已开启 · TLS 端口 ${state.adbTlsPort}" else "未开启",
                        ok = state.adbTlsPort > 0,
                        action = if (state.adbTlsPort > 0) null else "去开启",
                        onAction = {
                            val ok = PrivilegeService.openWirelessDebugging(context)
                            state.showMessage(
                                if (ok) "已打开无线调试页面，请打开开关后返回"
                                else "这台手机没有独立的无线调试页面，已跳到开发者选项"
                            )
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    EnvRow(
                        title = "配对状态",
                        detail = if (state.adbConnected) "已连接，可直接启动"
                        else "未连接：点下面「启动服务」，卡住的那一步会直接给出配对输入框",
                        ok = state.adbConnected,
                        action = null,
                        onAction = { }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "没有「无线调试」开关的机型（部分模拟器/定制 ROM）只能用 Root 或电脑 adb tcpip 5555 的方式。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // ---------------- 启动方式 ----------------
        item { SectionHeader("启动方式") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PrivilegeService.Mode.entries.forEach { item ->
                            FilterChip(
                                selected = mode == item,
                                onClick = { mode = item },
                                label = { Text(item.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (mode) {
                            PrivilegeService.Mode.ROOT ->
                                "Root：直接 fork su，UID 0。需要先在 Root 管理器里给本应用授权，能力最完整。"
                            PrivilegeService.Mode.WIRELESS ->
                                "无线调试：本应用自己连系统 adbd（UID 2000），免 Root。需要先打开无线调试并完成配对。"
                            PrivilegeService.Mode.WIRED ->
                                "有线 ADB：先在电脑执行 adb tcpip 5555，手机 USB 连过电脑一次后，本应用连本机 5555。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launchStart(mode) }, enabled = !starting) {
                            if (starting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (starting) "启动中…" else "启动服务")
                        }
                        OutlinedButton(
                            onClick = {
                                if (starting) return@OutlinedButton
                                starting = true
                                steps = emptyList()
                                scope.launch {
                                    val ok = PrivilegeService.restart(context) { snapshot -> steps = snapshot }
                                    starting = false
                                    statusTick++
                                    resultText = if (ok) "已重启" else "重启失败，请查看日志"
                                }
                            },
                            enabled = !starting
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重启")
                        }
                        OutlinedButton(
                            onClick = {
                                PrivilegeService.stop(context)
                                statusTick++
                                state.refreshAdb()
                                resultText = "已停止"
                            },
                            enabled = status.running
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("停止")
                        }
                    }
                    resultText?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (status.running) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // ---------------- 启动时间线 ----------------
        item { SectionHeader("启动过程") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (steps.isEmpty()) {
                        Text(
                            "点上面的「启动服务」后，这里会逐条显示每一步的结果；失败的那一步会标红并给出原因。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val done = steps.count { it.state == StepState.OK || it.state == StepState.FAIL }
                        val progress by animateFloatAsState(
                            targetValue = done.toFloat() / steps.size.toFloat(),
                            animationSpec = tween(320),
                            label = "startup-progress"
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        steps.forEachIndexed { index, step ->
                            TimelineRow(
                                index = index + 1,
                                title = step.title,
                                detail = step.detail,
                                state = step.state,
                                isLast = index == steps.lastIndex
                            )
                        }
                        // 卡在「还没配对」时，把配对输入直接放在失败那一行下面，
                        // 用户不用再去页面别处找输入框（对应「在卡住要求操作的地方加」）。
                        val pairBlocked = steps.any {
                            it.state == StepState.FAIL &&
                                (it.detail.contains("配对") || it.detail.contains("未连接") ||
                                    it.detail.contains("无线调试"))
                        }
                        if (pairBlocked) {
                            Spacer(Modifier.height(4.dp))
                            InlinePairPanel(
                                state = state,
                                manual = manual,
                                onManualChange = {
                                    manual = it
                                    state.saveAdbManualPair(it)
                                },
                                code = code,
                                onCodeChange = { input -> code = input.filter { it.isDigit() }.take(6) },
                                busy = state.adbBusy,
                                result = pairResult,
                                onPair = {
                                    if (!state.adbBusy) {
                                        pairResult = "正在配对…"
                                        state.pairAdb(code) { ok, msg ->
                                            pairResult = if (ok) "配对成功，已连接" else "配对失败：$msg"
                                            if (ok) {
                                                code = ""
                                                statusTick++
                                            }
                                        }
                                    }
                                },
                                onDiscover = { state.discoverAdbPairing() },
                                discovering = state.adbDiscovering,
                                services = state.adbPairingServices,
                                onOpenWireless = { PrivilegeService.openWirelessDebugging(context) }
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 运行环境检查 ----------------
        item { SectionHeader("运行环境检查") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (!status.running) {
                        Text(
                            "服务启动后，这里会用真实命令逐项测试这条通道能做到什么（终端命令、包管理、安全设置写入、AppOps）。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        caps.forEach { cap ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (cap.ok) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (cap.ok) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cap.name, style = MaterialTheme.typography.bodySmall)
                                    if (cap.detail.isNotBlank()) {
                                        Text(
                                            cap.detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                StatusPill(if (cap.ok) "可用" else "受限", cap.ok)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        PrivilegeService.brandHints(context).joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // ---------------- 无线调试配对 ----------------
        // ---------------- 授权应用入口 ----------------
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
                        Column(Modifier.weight(1f)) {
                            Text(
                                "授权应用",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "列出所有声明了 Shizuku API 的应用；授权动作需要在 Shizuku 管理器里完成。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { state.navigate(Screen.PrivilegeApps) }) {
                            Text("查看")
                        }
                    }
                }
            }
        }

        // ---------------- 启动日志 ----------------
        item { SectionHeader("启动日志") }
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
                        Text("日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { logLines = PairLog.read(context) }) { Text("刷新") }
                        TextButton(onClick = {
                            val text = if (logLines.isEmpty()) "还没有日志" else logLines.joinToString("\n")
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("XB 启动日志", text))
                            state.showMessage("日志已复制")
                        }) { Text("复制") }
                        TextButton(onClick = {
                            PairLog.clear(context)
                            logLines = PairLog.read(context)
                        }) { Text("清空") }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (logLines.isEmpty()) {
                        Text(
                            "暂无日志。启动或重启后这里会记录每一步。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        MonoBlock(logLines.takeLast(60).joinToString("\n"))
                    }
                }
            }
        }

        item {
            Text(
                "说明：本页是「本应用自己那条 ADB 通道」的启动器，不是把 Shizuku 打包进来。" +
                    "Shizuku 需要单独安装它的 APK 并通过无线调试/Root 启动它自己的服务进程，" +
                    "普通应用无法在自己的 APK 里 fork 出常驻 shell 进程 —— 这是 Android 的权限模型决定的。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("启动失败") },
            text = {
                Column {
                    Text(status.lastError, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "常见原因：\n· Root 模式：Root 管理器里没给本应用授权\n" +
                            "· 无线调试：还没配对，或无线调试开关被关掉\n" +
                            "· 有线 ADB：没在电脑执行 adb tcpip 5555\n" +
                            "· 开发者选项没开：部分 ROM 会连无线调试一起藏起来",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showError = false }) { Text("知道了") } }
        )
    }
}

@Composable
private fun EnvRow(
    title: String,
    detail: String,
    ok: Boolean,
    action: String?,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.Check else Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        } else {
            // 没有按钮时也要如实显示状态：之前一律写「就绪」，
            // 导致「配对状态：未连接」旁边挂着绿色的「就绪」，自相矛盾。
            StatusPill(if (ok) "就绪" else "待处理", ok)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TimelineRow(
    index: Int,
    title: String,
    detail: String,
    state: StepState,
    isLast: Boolean
) {
    val color = when (state) {
        StepState.OK -> MaterialTheme.colorScheme.primary
        StepState.FAIL -> MaterialTheme.colorScheme.error
        StepState.RUNNING -> MaterialTheme.colorScheme.tertiary
        StepState.PENDING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val dotScale by animateFloatAsState(
        targetValue = if (state == StepState.RUNNING) 1.12f else 1f,
        animationSpec = tween(260),
        label = "dot-$index"
    )
    val lineAlpha by animateFloatAsState(
        targetValue = if (state == StepState.PENDING) 0.25f else 0.75f,
        animationSpec = tween(260),
        label = "line-$index"
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.16f),
                modifier = Modifier.size(22.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (state) {
                        StepState.OK -> Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size((13 * dotScale).dp),
                            tint = color
                        )
                        StepState.FAIL -> Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size((13 * dotScale).dp),
                            tint = color
                        )
                        StepState.RUNNING -> CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.6.dp,
                            color = color
                        )
                        StepState.PENDING -> Text(
                            index.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (!isLast) {
                Surface(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = lineAlpha),
                    modifier = Modifier.width(1.5.dp).height(26.dp)
                ) {}
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (state == StepState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            AnimatedVisibility(
                visible = detail.isNotBlank(),
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(120))
            ) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state == StepState.FAIL) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun timeText(millis: Long): String {
    if (millis <= 0L) return "—"
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    return fmt.format(Date(millis))
}

/**
 * 内嵌在「启动过程」失败步骤下面的配对面板。
 *
 * 之前配对码输入藏在页面下方的独立卡片里，用户启动失败后还得往下翻去找；
 * 这里直接把「下一步该做什么」和输入框放在失败那一行下面。
 */
@Composable
private fun InlinePairPanel(
    state: AppState,
    manual: String,
    onManualChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    busy: Boolean,
    result: String?,
    onPair: () -> Unit,
    onDiscover: () -> Unit,
    discovering: Boolean,
    services: List<com.xb.tool.privilege.WirelessAdb.Discovered>,
    onOpenWireless: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "现在需要配对",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "手机设置 → 开发者选项 → 无线调试 → 「使用配对码配对设备」，" +
                    "把屏幕上显示的 IP 和 6 位配对码填到下面。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            // 主路径：配对码留在通知栏里输。用户正站在系统设置看那 6 位码，
            // 不用切回应用再抄一遍。
            Button(
                onClick = { AdbPairingService.start(state.context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("在通知栏输入配对码", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "打开系统的「使用配对码配对设备」弹窗后下拉通知栏，点「输入配对码并配对」，配对成功会自动继续启动。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDiscover, enabled = !discovering) {
                    Text(if (discovering) "搜索中…" else "自动搜索", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onOpenWireless) {
                    Text("打开无线调试", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (services.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                services.forEach { s ->
                    Text(
                        "· ${s.host}:${s.port}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manual,
                onValueChange = onManualChange,
                label = { Text("IP 地址和端口", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("192.168.1.5:37261", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                label = { Text("6 位配对码", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPair, enabled = !busy && code.length == 6) {
                    Text(if (busy) "配对中…" else "配对并连接", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = { state.connectAdbDirect() }, enabled = !busy) {
                    Text("直连（已配对）", style = MaterialTheme.typography.labelSmall)
                }
            }
            result?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it.startsWith("配对成功")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
