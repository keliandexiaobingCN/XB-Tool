package com.xb.tool.ui.screens

import android.os.PowerManager
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.xb.tool.core.AppState
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBListRow
import com.xb.tool.util.Intents
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun ExtremeKeepAliveScreen(state: AppState) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(PowerManager::class.java)
    var batteryWhitelisted by remember {
        mutableStateOf(
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }
    // 刷新信号：点击“刷新状态”后自增，强制整页重组，让状态立即更新
    var refreshTick by remember { mutableStateOf(0) }

    fun refreshSystemState() {
        batteryWhitelisted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        state.syncBackgroundStateFromSystem()
        state.refreshServiceStatus()
    }

    LifecycleResumeEffect(Unit) {
        refreshSystemState()
        onPauseOrDispose { }
    }

    // 直接读 mutableState：refreshSystemState() 里赋值后立刻触发重组。
    // 之前用 remember(refreshTick) 缓存，进入页面时 refreshTick 没变，读到的是旧值。
    val batteryNow = batteryWhitelisted

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                modifier = Modifier.size(27.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "极致后台保活",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "标准保活 + 双进程互拉守护（含标准保活）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.extremeKeepAlive,
                            onCheckedChange = { checked ->
                                if (!checked) {
                                    state.applyExtremeKeepAlive(false)
                                } else {
                                    // 统一用 keepAliveGate 判断，避免与页面提示不一致
                                    val reasons = state.keepAliveGate(true)
                                    if (reasons.isEmpty()) {
                                        state.applyExtremeKeepAlive(true)
                                    } else {
                                        // 条件不足时停在本页，按下方「设置引导」逐项授权
                                        val reason = reasons.first()
                                        state.showMessage(reason)
                                        if (reason.contains("通知")) {
                                            state.requestNotificationPermission()
                                        }
                                    }
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            if (batteryNow) "电池白名单已加入" else "电池白名单未加入",
                            active = batteryNow
                        )
                        StatusPill(
                            if (state.backgroundUnrestricted) "后台无限制" else "后台受限",
                            active = state.backgroundUnrestricted
                        )
                        StatusPill(
                            if (state.autoStartEnabled) "自启动已开" else "自启动未开",
                            active = state.autoStartEnabled
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("保活状态自检", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    // 状态点与文字必须来自同一个事实：心跳是否在有效期内
                    StatusLine(
                        "守护进程",
                        state.guardAlive,
                        when {
                            state.guardAlive -> "运行中（心跳正常）"
                            state.keepAlive || state.extremeKeepAlive -> "心跳超时，可能已被系统清理"
                            else -> "未开启"
                        }
                    )
                    StatusLine(
                        "互拉守护",
                        state.watchdogAlive,
                        when {
                            state.watchdogAlive -> "运行中（心跳正常）"
                            state.extremeKeepAlive -> "心跳超时，可能已被系统清理"
                            else -> "未开启"
                        }
                    )
                    StatusLine("通知权限", state.notificationsGranted, if (state.notificationsGranted) "已授权" else "待授权")
                    if (DeviceCompat.isColorOsFamily()) {
                        StatusLine(
                            "完全后台行为",
                            batteryNow,
                            if (batteryNow) "已允许（电池无限制）" else "未允许"
                        )
                        StatusLine(
                            "电池优化",
                            batteryNow,
                            if (batteryNow) "无限制（已加入白名单）" else "受限制（未加入白名单）"
                        )
                    }
                    // 自启动：能查到的走真实渠道，查不到的如实说明并给跳转，
                    // 不再伪装成「已开启」（之前读自己的开机接收器，永远为真）。
                    StatusLine(
                        "应用自启动",
                        state.autoStartState == com.xb.tool.system.AutoStartDetector.State.ALLOWED,
                        when (state.autoStartState) {
                            com.xb.tool.system.AutoStartDetector.State.ALLOWED ->
                                "已允许（检测渠道：${state.autoStartChannel}）"
                            com.xb.tool.system.AutoStartDetector.State.DENIED ->
                                "未允许（检测渠道：${state.autoStartChannel}）"
                            else -> "本机查不到（${state.autoStartChannel}），需在系统设置里手动确认"
                        }
                    )
                    if (state.autoStartState != com.xb.tool.system.AutoStartDetector.State.ALLOWED) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                Intents.openAutoStartSettings(context)
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "去系统设置里打开「自启动 / 开机启动」",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            refreshSystemState()
                            refreshTick++
                            state.refreshServiceStatus()
                            // 条件都满足时不弹提示；只有确实缺东西才提示具体原因
                            val problems = buildList {
                                if (!state.notificationsGranted) add("通知权限未开启")
                                if (!state.guardAlive) add("守护进程未运行")
                                if (state.extremeKeepAlive && !state.watchdogAlive) add("互拉守护未运行")
                                if (DeviceCompat.isColorOsFamily() && state.extremeKeepAlive && !batteryNow) {
                                    add("电池优化未设为无限制")
                                }
                            }
                            if (problems.isNotEmpty()) {
                                state.showMessage("需要处理：" + problems.joinToString("；"))
                            }
                        }) {
                            Text("刷新状态")
                        }
                        Text(
                            "标准与极致只需开一个；极致已包含标准。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "极致保活开启后会常驻前台通知，并由两个进程互相拉起。若系统仍会清理，通常是厂商省电策略限制，请在上面的状态自检里确认各项状态。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (DeviceCompat.isColorOsFamily()) {
            item {
                XBListRow(
                    icon = Icons.Default.BatterySaver,
                    title = "电池优化",
                    subtitle = if (batteryNow) "已设为「不限制」，系统不会因省电清理本应用"
                    else "点击进入系统设置，把本应用设为「不限制 / 不优化」",
                    onClick = { Intents.openBatteryOptimizationSettings(context) },
                    trailing = {
                        StatusPill(
                            if (batteryNow) "已加入白名单" else "去设置",
                            batteryNow
                        )
                    }
                )
            }
        }

    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    CircleShape
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
