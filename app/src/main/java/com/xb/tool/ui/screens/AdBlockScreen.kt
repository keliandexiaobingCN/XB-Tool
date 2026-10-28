package com.xb.tool.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xb.tool.adblock.AdBlockRuntime
import com.xb.tool.adblock.AdBlockStats
import com.xb.tool.adblock.AdBlockStore
import com.xb.tool.adblock.AdBlockUpdater
import com.xb.tool.adblock.AdBlockVpnService
import com.xb.tool.adblock.RuleStore
import com.xb.tool.core.AppState
import com.xb.tool.ui.InfoRow
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBListRow
import com.xb.tool.util.FileUtils
import com.xb.tool.util.Intents
import kotlinx.coroutines.launch
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun AdBlockScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtimeStats by AdBlockRuntime.stats.collectAsState()
    val persisted = remember { AdBlockStore.stats(context) }
    val stats: AdBlockStats = if (runtimeStats.running || runtimeStats.totalQueries > 0) runtimeStats else persisted
    var starting by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var safeSearch by remember { mutableStateOf(AdBlockStore.safeSearch(context)) }
    var autoReconnect by remember { mutableStateOf(AdBlockStore.autoReconnect(context)) }
    var ruleInput by remember { mutableStateOf("") }
    var whiteInput by remember { mutableStateOf("") }
    var filterText by remember { mutableStateOf(AdBlockStore.filterUrls(context).joinToString("\n")) }
    var message by remember { mutableStateOf<String?>(null) }
    var customRules by remember { mutableStateOf(AdBlockStore.customRules(context)) }
    var whitelist by remember { mutableStateOf(AdBlockStore.whitelist(context)) }
    var rulesCount by remember { mutableStateOf(RuleStore.rules(context).size) }

    LaunchedEffect(Unit) {
        AdBlockStore.reloadRules(context)
        rulesCount = RuleStore.rules(context).size
    }

    fun startVpn() {
        starting = true
        message = null
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START)
            )
            AdBlockStore.setEnabled(context, true)
            message = "防广告服务已启动"
        }.onFailure { message = "启动失败：${it.message}" }
        starting = false
    }

    fun stopVpn() {
        runCatching {
            context.startService(
                Intent(context, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP)
            )
            AdBlockStore.setEnabled(context, false)
            AdBlockRuntime.setRunning(false)
            message = "防广告服务已停止"
        }.onFailure { message = "停止失败：${it.message}" }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpn()
        } else {
            message = "未授予 VPN 权限，无法启用防广告"
        }
    }

    fun toggle(enable: Boolean) {
        if (!enable) {
            stopVpn()
            return
        }
        if (!state.notificationsGranted) {
            state.requestNotificationPermission()
            message = "请先授权通知权限，然后再次开启防广告"
            return
        }
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            vpnLauncher.launch(prepare)
        } else {
            startVpn()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("防广告", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (stats.running) "本地 VPN 正在过滤广告与追踪域名"
                                else "开启后建立本地 DNS 过滤，不改动其他流量",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = stats.running || starting,
                            onCheckedChange = { toggle(it) },
                            enabled = !starting
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(if (stats.running) "运行中" else "未开启", active = stats.running)
                        StatusPill("规则 ${stats.rulesCount}", active = stats.rulesCount > 0)
                        StatusPill("安全搜索", active = safeSearch)
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("拦截率", "${String.format("%.1f", stats.rate)}%", "已拦截 ${stats.blockedQueries} / 总解析 ${stats.totalQueries}", Modifier.weight(1f))
                StatCard("已拦截", "${stats.blockedQueries}", "节省约 ${FileUtils.formatBytes(stats.savedBytes)}", Modifier.weight(1f))
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
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow("总 DNS 请求", "${stats.totalQueries}", Icons.Default.Wifi)
                    InfoRow("已拦截请求", "${stats.blockedQueries}", Icons.Default.Block)
                    InfoRow("估算节省流量", FileUtils.formatBytes(stats.savedBytes), Icons.Default.Speed)
                    InfoRow("规则数量", "$rulesCount", Icons.Default.Security)
                    InfoRow("最近拦截", stats.topBlocked.firstOrNull()?.first ?: "暂无", Icons.Default.Security)
                }
            }
        }

        item { SectionHeader("过滤统计") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (stats.topBlocked.isEmpty()) {
                        Text(
                            "运行后这里会显示被拦截最多的域名。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        stats.topBlocked.take(6).forEachIndexed { index, item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text("${index + 1}. ${item.first}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text("${item.second} 次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    AdBlockStore.lastUpdate(context).takeIf { it > 0 }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "过滤器上次更新：${FileUtils.formatTimeFull(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        item { SectionHeader("过滤设置") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    XBListRow(
                        icon = Icons.Default.Security,
                        title = "安全搜索",
                        subtitle = "过滤成人站点并强制 Google / Bing 安全搜索",
                        onClick = { },
                        trailing = {
                            Switch(
                                checked = safeSearch,
                                onCheckedChange = {
                                    safeSearch = it
                                    AdBlockStore.setSafeSearch(context, it)
                                    AdBlockStore.reloadRules(context)
                                }
                            )
                        }
                    )
                    XBListRow(
                        icon = Icons.Default.Wifi,
                        title = "自动重连",
                        subtitle = "网络变化后自动重建本地 DNS 隧道",
                        onClick = { },
                        trailing = {
                            Switch(
                                checked = autoReconnect,
                                onCheckedChange = {
                                    autoReconnect = it
                                    AdBlockStore.setAutoReconnect(context, it)
                                }
                            )
                        }
                    )
                    XBListRow(
                        icon = Icons.Default.Block,
                        title = "应用白名单",
                        subtitle = "管理不参与安全扫描的应用",
                        onClick = { state.navigate(com.xb.tool.core.Screen.Whitelist) }
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
                    Text("自定义过滤器", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "支持域名、hosts 行或 ||domain^；保存后立即生效。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = ruleInput,
                            onValueChange = { ruleInput = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("域名 / 过滤器规则") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (AdBlockStore.addCustomRule(context, ruleInput)) {
                                    customRules = AdBlockStore.customRules(context)
                                    rulesCount = RuleStore.rules(context).size
                                    ruleInput = ""
                                }
                            })
                        )
                        IconButton(onClick = {
                            if (AdBlockStore.addCustomRule(context, ruleInput)) {
                                customRules = AdBlockStore.customRules(context)
                                rulesCount = RuleStore.rules(context).size
                                ruleInput = ""
                            }
                        }) { Icon(Icons.Default.Add, contentDescription = "添加规则") }
                    }
                    if (customRules.isEmpty()) {
                        Text("暂无自定义规则", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    } else {
                        customRules.takeLast(8).forEach { rule ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(rule, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    AdBlockStore.removeCustomRule(context, rule)
                                    customRules = AdBlockStore.customRules(context)
                                    rulesCount = RuleStore.rules(context).size
                                }) { Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(17.dp)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("域名白名单", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = whiteInput,
                            onValueChange = { whiteInput = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("example.com") },
                            singleLine = true
                        )
                        IconButton(onClick = {
                            if (AdBlockStore.addWhitelist(context, whiteInput)) {
                                whitelist = AdBlockStore.whitelist(context)
                                whiteInput = ""
                            }
                        }) { Icon(Icons.Default.Add, contentDescription = "添加白名单") }
                    }
                    whitelist.toList().takeLast(8).forEach { domain ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(domain, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                AdBlockStore.removeWhitelist(context, domain)
                                whitelist = AdBlockStore.whitelist(context)
                            }) { Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(17.dp)) }
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
                    Text("过滤器订阅源", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("每行一个 hosts / 过滤器 URL") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val urls = filterText.lineSequence().map { it.trim() }
                                .filter { it.startsWith("http") }.toList()
                            AdBlockStore.setFilterUrls(context, urls)
                            message = "已保存 ${urls.size} 个订阅源"
                        }) { Text("保存订阅源") }
                        Button(
                            enabled = !updating,
                            onClick = {
                                updating = true
                                message = null
                                scope.launch {
                                    val urls = filterText.lineSequence().map { it.trim() }
                                        .filter { it.startsWith("http") }.toList()
                                    AdBlockStore.setFilterUrls(context, urls)
                                    val result = AdBlockUpdater.update(context, urls)
                                    updating = false
                                    rulesCount = RuleStore.rules(context).size
                                    message = result.fold(
                                        onSuccess = { "已更新过滤器，共 $it 条规则" },
                                        onFailure = { "更新失败：${it.message}" }
                                    )
                                }
                            }
                        ) {
                            if (updating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(if (updating) "更新中…" else "更新过滤器")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "参考 blockads.pwhs.app 的本地 VPN / Root 双路由思路与 youlong-blockads 规则结构；DNS 过滤不修改其他代理设置。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { Intents.openVpnSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("打开系统 VPN 设置") }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
