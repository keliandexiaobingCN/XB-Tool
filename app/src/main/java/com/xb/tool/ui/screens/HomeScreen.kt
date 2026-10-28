package com.xb.tool.ui.screens

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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xb.tool.MainActivity
import com.xb.tool.core.AppState
import com.xb.tool.core.InterceptStore
import com.xb.tool.core.LogBus
import com.xb.tool.core.Screen
import com.xb.tool.scan.ScanMode
import com.xb.tool.ui.PermissionReminderDialog
import com.xb.tool.ui.ScanActionCard
import com.xb.tool.ui.TerminalPanel
import com.xb.tool.ui.XBConfirmDialog
import com.xb.tool.ui.XBInfoDialog
import com.xb.tool.ui.XBListRow
import com.xb.tool.ui.StatusPill
import com.xb.tool.util.FileUtils
import com.xb.tool.util.Intents
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun HomeScreen(state: AppState) {
    val logs by LogBus.entries.collectAsState()
    val intercepts by InterceptStore.items.collectAsState()
    var dbDialog by remember { mutableStateOf<String?>(null) }
    var showDeepReminder by remember { mutableStateOf(false) }
    var showNoPrivilege by remember { mutableStateOf(false) }

    fun beginDeep() {
        if (state.hasDeepAccess()) {
            state.navigate(Screen.Scan(ScanMode.DEEP))
        } else if (state.rootRemind) {
            showDeepReminder = true
        } else {
            state.ensureDeepPrivilege { ok ->
                if (ok) state.navigate(Screen.Scan(ScanMode.DEEP)) else showNoPrivilege = true
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HomeHeroCard(state, intercepts.size) { state.navigate(Screen.Root) }
        }
        item {
            if (!state.allFilesGranted) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "未授予「所有文件访问」，部分目录无法扫描",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        TextButton(onClick = { Intents.openAllFilesSettings(state.context) }) {
                            Text("去授权", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        if (state.scanState.finished && state.scanState.threats.isNotEmpty()) {
            item {
                HomeThreatCard(
                    threats = state.scanState.threats,
                    onOpenDetails = { state.navigate(Screen.Scan(state.scanState.mode)) }
                )
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clickable { state.navigate(Screen.Emergency) }
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("紧急救机", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "摇一摇 / 按键触发 · 锁屏置顶救援",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill("救援", active = true)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScanActionCard(
                    title = "普通杀毒",
                    subtitle = "已安装应用与常用下载目录",
                    icon = Icons.Default.Security,
                    deep = false,
                    modifier = Modifier.weight(1f)
                ) {
                    state.navigate(Screen.Scan(ScanMode.NORMAL))
                }
                ScanActionCard(
                    title = "强力杀毒",
                    subtitle = "系统分区深度扫描 · Root / Shizuku",
                    icon = Icons.Default.Bolt,
                    deep = true,
                    badge = if (state.rootInfo.rootGranted || state.shizukuGranted || state.adbConnected) "已授权" else "需 Root",
                    badgeActive = state.rootInfo.rootGranted || state.shizukuGranted || state.adbConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    beginDeep()
                }
            }
        }
        if (state.rootInfo.rootGranted || state.shizukuGranted || state.adbConnected) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().clickable { state.navigate(Screen.Root) }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("内核级保护", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                "利用 Root / Shizuku / 无线调试 检查系统分区、内核与完整性，普通权限无法覆盖",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill("已就绪", active = true)
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { state.navigate(Screen.Realtime) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("实时防护模块", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            if (state.realtime) "监控下载/安装内容 · 保活机制单独负责进程常驻"
                            else "开启后监控新文件；后台存活由保活机制负责",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.realtime,
                        onCheckedChange = { checked ->
                            if (checked && !state.notificationsGranted) {
                                state.requestNotificationPermission()
                                state.showMessage("请授权通知权限后再次开启实时检测")
                            } else {
                                state.applyRealtime(checked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
        item {
            XBListRow(
                icon = Icons.Default.Shield,
                title = "病毒库版本",
                subtitle = "v${state.database.version} · ${state.database.signatureCount} 条特征 · 点击检测更新",
                onClick = {
                    state.checkDatabaseUpdate { result -> dbDialog = result }
                },
                trailing = {
                    if (state.dbChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { state.checkDatabaseUpdate { result -> dbDialog = result } },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "检测病毒库更新",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
        item {
            XBListRow(
                icon = Icons.Default.GppMaybe,
                title = "拦截记录",
                subtitle = if (intercepts.isEmpty()) "暂无拦截记录 · 实时检测发现威胁后会记录在这里"
                else "已拦截 ${intercepts.size} 项 · 点击查看详情",
                onClick = { state.navigate(Screen.Intercepts) },
                tint = MaterialTheme.colorScheme.error,
                trailing = {
                    if (intercepts.isNotEmpty()) {
                        StatusPill("${intercepts.size}", active = true)
                    }
                }
            )
        }
        item {
            TerminalPanel(
                logs = logs,
                running = state.realtime || state.scanState.running,
                onClear = { LogBus.clear() }
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "强力杀毒需要 Root 或 Shizuku 权限，未授权时会先弹出提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showDeepReminder) {
        PermissionReminderDialog(
            permissionName = "强力杀毒 · Root / Shizuku",
            message = "强力杀毒将深度扫描系统分区、受保护目录与已安装系统应用。" +
                "此操作需要 Root 或 Shizuku 权限，确认后将依次尝试申请。",
            onDismiss = { showDeepReminder = false },
            onConfirm = {
                showDeepReminder = false
                state.ensureDeepPrivilege { ok ->
                    if (ok) state.navigate(Screen.Scan(ScanMode.DEEP)) else showNoPrivilege = true
                }
            }
        )
    }

    if (showNoPrivilege) {
        XBConfirmDialog(
            title = "无法开始强力杀毒",
            text = "未检测到可用的 Root 或 Shizuku 权限。请先在设置中完成授权，或启动 Shizuku 后重试。",
            confirmLabel = "前往设置",
            onConfirm = {
                showNoPrivilege = false
                state.navigate(Screen.Settings)
            },
            onDismiss = { showNoPrivilege = false }
        )
    }

    dbDialog?.let { result ->
        XBInfoDialog(
            title = "病毒库检测",
            text = result,
            onDismiss = { dbDialog = null }
        )
    }
}

@Composable
private fun HomeHeroCard(state: AppState, interceptCount: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = glassSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.20f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(
                        scheme.primaryContainer.copy(alpha = 0.92f),
                        scheme.surface,
                        scheme.tertiaryContainer.copy(alpha = 0.42f)
                    )
                )
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = scheme.primary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "XB 安全中心",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (state.rootInfo.rootGranted) "增强防护已就绪"
                            else if (state.shizukuGranted) "Shizuku 增强模式"
                            else if (state.adbConnected) "无线调试增强模式"
                            else "标准防护模式",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    Text("详情 ›", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        if (state.rootInfo.rootGranted) "Root · ${state.rootInfo.managerName}"
                        else if (state.shizukuGranted) "Shizuku 已授权"
                        else if (state.adbConnected) "无线调试已授权"
                        else "未授权 Root/Shizuku",
                        active = state.rootInfo.rootGranted || state.shizukuGranted || state.adbConnected
                    )
                    StatusPill(
                        if (state.realtime) "实时防护开" else "实时防护关",
                        active = state.realtime
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroStat(
                        label = "病毒库",
                        value = state.database.version,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(scheme.outlineVariant.copy(alpha = 0.55f))
                    )
                    HeroStat(
                        label = "最近扫描",
                        value = if (state.lastScanAt > 0) FileUtils.formatTime(state.lastScanAt) else "尚未扫描",
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(scheme.outlineVariant.copy(alpha = 0.55f))
                    )
                    HeroStat(
                        label = "拦截",
                        value = "$interceptCount 项",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.scanState.finished && state.scanState.threats.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.errorContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.GppMaybe,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = scheme.onErrorContainer
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "上次扫描发现 ${state.scanState.threats.size} 个威胁，点击查看",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 风险类型：从威胁名称/描述推断，首页只显示最多 4 个标签，其余用 … 表示。 */
private fun threatTags(threat: com.xb.tool.core.Threat): List<String> {
    val text = (threat.name + " " + threat.description).lowercase()
    val tags = mutableListOf<String>()
    fun add(tag: String, vararg keys: String) {
        if (keys.any { text.contains(it) }) tags += tag
    }
    add("诈骗", "phish", "fraud", "scam", "钓鱼", "诈骗", "fake")
    add("勒索", "ransom", "勒索", "encrypt", "加密")
    add("广告", "adware", "广告", "advert")
    add("锁机", "lock", "锁机", "kiosk", "锁屏")
    add("木马", "trojan", "木马", "backdoor", "后门")
    add("间谍", "spyware", "间谍", "steal", "窃取")
    add("风险", "riskware", "hacktool", "risk", "风险")
    if (tags.isEmpty()) tags += "可疑"
    return tags.distinct()
}

@Composable
private fun HomeThreatCard(
    threats: List<com.xb.tool.core.Threat>,
    onOpenDetails: () -> Unit
) {
    val tags = remember(threats) { threats.flatMap { threatTags(it) }.distinct() }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable { onOpenDetails() }
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.GppMaybe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "发现 ${threats.size} 个风险应用",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text("详情 ›", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.take(4).forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (tags.size > 4) {
                    Text("…", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                threats.take(2).joinToString("、") { it.name } +
                    if (threats.size > 2) " 等 ${threats.size} 项" else "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

