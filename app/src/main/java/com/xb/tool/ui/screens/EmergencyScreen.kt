package com.xb.tool.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.core.Threat
import com.xb.tool.emergency.EmergencyController
import com.xb.tool.emergency.EmergencyAppInfo
import com.xb.tool.emergency.EmergencyNotifier
import com.xb.tool.emergency.EmergencyResult
import com.xb.tool.emergency.LockRisk
import com.xb.tool.guard.GuardService
import kotlinx.coroutines.launch
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun EmergencyScreen(state: AppState) {
    EmergencyContent(
        context = state.context,
        onBack = { state.goBack() },
        onToast = { state.showMessage(it) },
        onOpenSecurity = { state.navigate(Screen.Home) }
    )
}

@Composable
fun EmergencyContent(
    context: Context,
    onBack: () -> Unit,
    onToast: (String) -> Unit,
    onOpenSecurity: () -> Unit = onBack
) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<EmergencyResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var isolating by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(EmergencyNotifier.isEnabled(context)) }
    var shakeEnabled by remember { mutableStateOf(EmergencyNotifier.isShakeEnabled(context)) }
    var buttonEnabled by remember { mutableStateOf(EmergencyNotifier.isButtonEnabled(context)) }
    var pressCountTick by remember { mutableStateOf(0) }
    var shakeLevelTick by remember { mutableStateOf(0) }
    var buttonKeyTick by remember { mutableStateOf(0) }
    var showAllApps by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }

    fun missingEmergencyPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) missing += "通知权限"
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) missing += "全屏通知权限"
        }
        return missing
    }

    fun requestMissingPermission(missing: List<String>) {
        when {
            missing.any { it.contains("通知") } -> {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onToast("请在系统设置中允许 XB工具 通知，然后再次开启紧急救机")
            }
            missing.any { it.contains("全屏") } && Build.VERSION.SDK_INT >= 34 -> {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onToast("请允许全屏通知权限，然后再次开启紧急救机")
            }
        }
    }

    fun refreshTrigger() {
        runCatching {
            context.startService(
                Intent(context, GuardService::class.java)
                    .setAction(GuardService.ACTION_REFRESH_SHAKE)
            )
        }
    }

    fun setEmergencyEnabled(next: Boolean) {
        if (next) {
            val missing = missingEmergencyPermissions()
            if (missing.isNotEmpty()) {
                requestMissingPermission(missing)
                return
            }
            EmergencyNotifier.setEnabled(context, true)
            // 开启总开关时默认只启用「摇一摇」，按键触发由用户自行开启
            EmergencyNotifier.setShakeEnabled(context, true)
            EmergencyNotifier.setButtonEnabled(context, false)
            EmergencyNotifier.setShakeLevel(context, "violent")
            enabled = true
            shakeEnabled = true
            buttonEnabled = false
            runCatching {
                context.startService(
                    Intent(context, GuardService::class.java)
                        .setAction(GuardService.ACTION_REFRESH_SHAKE)
                )
            }
            onToast("紧急救机已开启：默认摇一摇触发")
        } else {
            // 关闭只停用总开关，保留触发方式选择，下次开启不必重设
            EmergencyNotifier.setEnabled(context, false)
            enabled = false
            runCatching {
                context.startService(
                    Intent(context, GuardService::class.java)
                        .setAction(GuardService.ACTION_REFRESH_SHAKE)
                )
            }
            context.getSystemService(NotificationManager::class.java)
                .cancel(EmergencyNotifier.NOTIFICATION_ID)
            // 关闭后重置为只保留摇一摇，避免下次打开状态混乱
            EmergencyNotifier.setShakeEnabled(context, true)
            EmergencyNotifier.setButtonEnabled(context, false)
            shakeEnabled = true
            buttonEnabled = false
            onToast("紧急救机已关闭")
        }
    }


    fun startScan() {
        if (scanning) return
        scanning = true
        scope.launch {
            runCatching {
                EmergencyController.scan(context) { _, _, _ -> }
            }.onSuccess {
                result = it
                onToast("全量扫描完成：${it.scannedApps} 个应用")
            }.onFailure {
                onToast("紧急扫描失败：${it.message}")
            }
            scanning = false
        }
    }

    LaunchedEffect(Unit) { startScan() }

    val risks = result?.risks.orEmpty()
    val threats = result?.threats.orEmpty()
    val allApps = result?.apps.orEmpty()
    val riskPackages = remember(risks) { risks.map { it.packageName }.toSet() }
    val filteredApps = remember(allApps, appQuery) {
        val q = appQuery.trim().lowercase()
        if (q.isBlank()) allApps
        else allApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF3B1114),
                border = BorderStroke(1.dp, Color(0xFFFF6B6B).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(listOf(Color(0xFF3B1114), Color(0xFF190B0D)))
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF8A8A),
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "紧急救机",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "全量扫描所有应用 · 权限自检 · 置顶救援",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFB4B4)
                                )
                            }
                            TextButton(onClick = onBack) {
                                Text("返回", color = Color(0xFFFFB4B4))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(if (result?.rooted == true) "Root 可用" else "Root 未授权", result?.rooted == true)
                            Chip(if (result?.shizuku == true) "Shizuku 可用" else "Shizuku 未授权", result?.shizuku == true)
                            Chip("摇一摇 ${if (shakeEnabled) "开" else "关"}", shakeEnabled)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0x22000000), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("紧急救机总开关", color = Color.White, fontWeight = FontWeight.Medium)
                                Text(
                                    if (enabled) "已开启，摇一摇 / 按钮可触发锁屏救援"
                                    else "开启时需要通知和全屏通知权限",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFFB4B4)
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = enabled,
                                onCheckedChange = { setEmergencyEnabled(it) }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "全量扫描与强制关闭在救援界面中进行（摇一摇 / 按键触发后自动打开）。",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB4B4)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.45f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("触发方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("摇一摇", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "当前力度：${EmergencyNotifier.shakeLevelName(context)}" + if (shakeLevelTick >= 0) "" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = shakeEnabled, onCheckedChange = { next ->
                            if (next && !enabled) {
                                onToast("请先开启紧急救机总开关")
                                setEmergencyEnabled(true)
                            } else {
                                EmergencyNotifier.setShakeEnabled(context, next)
                                shakeEnabled = next
                                refreshTrigger()
                            }
                        })
                    }
                    if (shakeEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("standard" to "标准", "hard" to "用力", "violent" to "猛摇").forEach { (id, label) ->
                                FilterChip(
                                    selected = EmergencyNotifier.shakeLevel(context) == id,
                                    onClick = {
                                        EmergencyNotifier.setShakeLevel(context, id)
                                        shakeLevelTick++
                                        onToast("摇一摇力度已设为「$label」")
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("按键触发", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "在救援页点击救援按键，需连按 ${EmergencyNotifier.pressCount(context)} 次" + if (pressCountTick >= 0) "" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = buttonEnabled, onCheckedChange = { next ->
                            if (next) {
                                if (!EmergencyNotifier.isAccessibilityConnected(context)) {
                                    // 未开无障碍时，先引导去开启
                                    runCatching {
                                        context.startActivity(
                                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                    onToast("请开启「XB工具 紧急救机（音量键）」无障碍服务后再打开按键触发")
                                } else {
                                    EmergencyNotifier.setButtonEnabled(context, true)
                                    buttonEnabled = true
                                    refreshTrigger()
                                }
                            } else {
                                EmergencyNotifier.setButtonEnabled(context, false)
                                buttonEnabled = false
                                refreshTrigger()
                            }
                        })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("触发按键", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        listOf("volume_up" to "音量 +", "volume_down" to "音量 −").forEach { (id, label) ->
                            FilterChip(
                                selected = EmergencyNotifier.buttonKey(context) == id,
                                onClick = {
                                    EmergencyNotifier.setButtonKey(context, id)
                                    buttonKeyTick++
                                    onToast("触发按键已设为 ")
                                },
                                label = { Text(label) }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("连按次数", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                val next = (EmergencyNotifier.pressCount(context) - 1).coerceAtLeast(1)
                                EmergencyNotifier.setPressCount(context, next)
                                pressCountTick++
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text("−") }
                        Text(
                            "  ${EmergencyNotifier.pressCount(context)}  ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                val next = (EmergencyNotifier.pressCount(context) + 1).coerceAtMost(10)
                                EmergencyNotifier.setPressCount(context, next)
                                pressCountTick++
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) { Text("+") }
                    }
                }
            }
        }

        // 锁机风险 / 病毒威胁 / 全部应用 仅存在于救援界面，不在紧急救机设置页展示。

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "紧急救机在 Root / Shizuku 下可冻结风险应用并让救援界面覆盖锁屏；普通权限下只能扫描并引导到系统设备管理器处理。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenSecurity) { Text("返回安全中心") }
                        TextButton(onClick = { onToast("摇一摇触发已启用，可在后台持续检测") }) {
                            Text("后台触发说明")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) Color(0xFF1E5B45) else Color(0xFF5B2024)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color(0xFF9CF0C4) else Color(0xFFFFB4B4),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun RiskRow(risk: LockRisk) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (risk.severity >= 3) Icons.Default.Warning else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (risk.severity >= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(risk.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                risk.reasons.joinToString(" · ") + if (risk.isSystem) " · 系统应用" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThreatRow(threat: Threat) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(threat.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                threat.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Boolean?.orFalse(): Boolean = this == true

@Composable
private fun AppRow(
    context: Context,
    app: EmergencyAppInfo,
    risk: Boolean,
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var stopping by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (risk) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        app.packageName + if (app.isSystem) " · 系统应用" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (risk) {
                    Text(
                        "风险",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("信息") }
                if (!app.isSystem) {
                    TextButton(
                        enabled = !stopping,
                        onClick = {
                            stopping = true
                            scope.launch {
                                val message = EmergencyController.forceStop(context, app.packageName)
                                onMessage(message)
                                stopping = false
                            }
                        }
                    ) {
                        Text(if (stopping) "处理中…" else "强制结束")
                    }
                }
            }
        }
    }
}
