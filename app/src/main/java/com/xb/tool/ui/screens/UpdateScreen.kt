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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.ui.MonoBlock
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBInfoDialog
import com.xb.tool.ui.XBListRow
import com.xb.tool.util.FileUtils
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun UpdateScreen(state: AppState) {
    var sourceText by remember(state.updateManifestUrl) { mutableStateOf(state.updateManifestUrl) }
    var directDbUrl by remember(state.virusDbDirectUrl) { mutableStateOf(state.virusDbDirectUrl) }
    var dialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            )
                        )
                    )
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        RoundedCornerShape(17.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("XB工具", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "当前版本 v${state.currentVersionName()} (${state.currentVersionCode()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(state.manifestSource, active = state.manifestSource == "在线")
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { state.checkUpdates { result -> dialog = result } },
                            enabled = !state.updateChecking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.updateChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.updateChecking) "正在检查…" else "检查应用与病毒库更新")
                        }
                        state.lastUpdateCheck.takeIf { it > 0 }?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "上次检查：${FileUtils.formatTimeFull(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
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
                    Text("更新源", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "填写 update.json 的地址；公告、应用更新和病毒库更新共用这个清单。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sourceText,
                        onValueChange = { sourceText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("更新清单 URL") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            state.saveUpdateManifestUrl(sourceText)
                            state.checkUpdates { result -> dialog = result }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("保存并检查")
                        }
                        TextButton(onClick = {
                            state.resetUpdateManifestUrl()
                            sourceText = state.updateManifestUrl
                        }) { Text("默认源") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "火绒 / 360 没有公开特征库 API，因此不会直接抓取它们的私有病毒库；可发布自己的 JSON 特征库后填入这里。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    state.manifestError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "上次在线检查失败：$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
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
                        Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("应用更新", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        StatusPill(
                            if (state.appUpdateInfo != null) "有更新" else "最新",
                            active = state.appUpdateInfo != null
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.appUpdateInfo != null) {
                        val info = state.appUpdateInfo!!
                        Text(
                            "新版本 v${info.versionName} (${info.versionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                info.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (state.apkDownloading) {
                            LinearProgressIndicator(
                                progress = { state.apkProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                state.apkStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (info.url.isBlank()) {
                            Text(
                                "更新清单未提供 APK 直链，请到项目 GitHub Releases 手动下载最新版。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = {
                                com.xb.tool.util.Intents.openUrl(
                                    state.context,
                                    "https://github.com/keliandexiaobingCN/XB-Tool/releases"
                                )
                            }) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("打开下载页")
                            }
                        } else {
                            Button(onClick = {
                                state.downloadAppUpdate { _, message ->
                                    dialog = message
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("下载并安装")
                            }
                        }
                    } else {
                        Text(
                            "当前已是最新版本。若要发布更新，在 update.json 的 app 节点填写更高 versionCode 与 APK 地址。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("病毒库", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        StatusPill("v${state.database.version}", active = true)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已加载 ${state.database.signatureCount} 条特征 · 来源：${state.database.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.dbUpdateInfo != null) {
                        val update = state.dbUpdateInfo!!
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "发现远程病毒库 v${update.version}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (update.notes.isNotBlank()) {
                            Text(
                                update.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { state.checkDatabaseUpdate { dialog = it } },
                            enabled = !state.dbChecking
                        ) {
                            if (state.dbChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("检查病毒库")
                        }
                        Button(
                            onClick = {
                                state.updateVirusDatabase { _, message -> dialog = message }
                            },
                            enabled = state.dbUpdateInfo != null && !state.dbUpdating
                        ) {
                            if (state.dbUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.dbUpdating) "更新中…" else "更新病毒库")
                        }
                    }
                    state.dbCheckResult?.let {
                        Spacer(Modifier.height(8.dp))
                        MonoBlock(it)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("病毒库直链（公开哈希列表 / JSON）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持公开 SHA-256 哈希列表（每行一个）或本应用 JSON；MalwareBazaar 的导出链接可能返回 405，遇到时请改用内置公开哈希库。公开哈希库是通用恶意样本哈希，不保证覆盖所有 Android 锁机样本，未知样本请用紧急救机的全部应用列表手动处理。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = directDbUrl,
                        onValueChange = { directDbUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("病毒库直链 URL") },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !state.dbUpdating,
                            onClick = {
                                state.saveVirusDbDirectUrl(directDbUrl)
                                state.updateVirusDatabaseFromUrl(directDbUrl) { _, message -> dialog = message }
                            }
                        ) {
                            if (state.dbUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.dbUpdating) "更新中…" else "从直链更新")
                        }
                        TextButton(onClick = {
                            directDbUrl = "https://fastly.jsdelivr.net/gh/sergelacombe/malware_sha256_hashes@main/malware_sha256_hashes.txt"
                        }) { Text("使用公开哈希库") }
                    }
                }
            }
        }

        if (state.dbHistory.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("本地病毒库历史（离线回退）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "更新成功的特征会保存在本机，公共更新源失效时继续使用最近一次的特征。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        state.dbHistory.take(5).forEach { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text("v${item.version}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
                                Text(
                                    "${item.signatures} 条 · ${item.mirror.ifBlank { item.source }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(item.timeText(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
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
                Column(Modifier.padding(vertical = 4.dp)) {
                    XBListRow(
                        icon = Icons.Default.SystemUpdate,
                        title = "自动检查更新",
                        subtitle = if (state.autoCheckUpdate) "每 ${state.autoCheckIntervalMin} 分钟检查一次，发现新病毒库自动提示" else "已关闭自动检查",
                        onClick = { },
                        trailing = {
                            Switch(
                                checked = state.autoCheckUpdate,
                                onCheckedChange = { state.applyAutoCheckUpdate(it) }
                            )
                        }
                    )
                    XBListRow(
                        icon = Icons.Default.SystemUpdate,
                        title = "自动更新病毒库",
                        subtitle = "发现新版本时自动下载并保存离线副本",
                        onClick = { },
                        trailing = {
                            Switch(
                                checked = state.autoUpdateVirusDb,
                                onCheckedChange = { state.applyAutoUpdateVirusDb(it) }
                            )
                        }
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("检查间隔", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        listOf(1, 15, 60, 360).forEach { minutes ->
                            TextButton(onClick = { state.applyAutoCheckInterval(minutes) }) {
                                Text(
                                    if (minutes < 60) "${minutes}分" else "${minutes / 60}时",
                                    fontWeight = if (state.autoCheckIntervalMin == minutes) FontWeight.Bold else FontWeight.Normal
                                )
                            }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("最新公告", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { state.navigate(Screen.Announcements) }) { Text("全部") }
                    }
                    if (state.announcements.isEmpty()) {
                        Text(
                            "暂无公告",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.announcements.take(2).forEach { item ->
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(item.tag, active = item.important)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                item.content,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "提示：更新清单可同时包含 app / virusDb / announcements。应用更新下载后会拉起系统安装器；病毒库更新需通过 JSON 格式校验后才会替换本地库。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    dialog?.let { text ->
        XBInfoDialog(
            title = "检测结果",
            text = text,
            onDismiss = { dialog = null }
        )
    }
}
