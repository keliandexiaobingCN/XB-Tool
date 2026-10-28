package com.xb.tool.ui.screens

import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.privilege.PrivilegeApps
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.glassSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 授权应用页：列出所有声明了 Shizuku API 的应用。
 *
 * 不假装能改别人的授权状态——Shizuku 的授权开关在它自己的管理器里，
 * 普通应用最多只能把用户送过去。这里把「谁能被授权」列清楚，一点就跳转。
 */
@Composable
fun PrivilegeAppsScreen(state: AppState) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<PrivilegeApps.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var shizukuInstalled by remember { mutableStateOf(false) }

    fun reload() {
        loading = true
        apps = PrivilegeApps.list(context)
        shizukuInstalled = PrivilegeApps.isInstalled(context, PrivilegeApps.SHIZUKU_PACKAGE)
        loading = false
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { reload() }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "授权应用",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            if (state.shizukuGranted) "Shizuku 已授权本应用" else
                                if (shizukuInstalled) "Shizuku 已安装" else "Shizuku 未安装",
                            state.shizukuGranted
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "下面是所有声明了 Shizuku API 的应用。授权开关在 Shizuku 管理器里，" +
                            "本页只负责列出来并把你送过去——普通应用没有权限替别的应用改授权状态。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            if (!PrivilegeApps.openShizuku(context)) {
                                state.showMessage("没有找到 Shizuku 管理器，请先安装")
                            }
                        }) { Text("打开 Shizuku") }
                        OutlinedButton(onClick = { reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("刷新")
                        }
                        OutlinedButton(onClick = { state.navigate(Screen.PrivilegeService) }) {
                            Text("ADB 增强服务")
                        }
                    }
                }
            }
        }

        item { SectionHeader("声明了 Shizuku API 的应用") }

        if (loading) {
            item {
                Text(
                    "正在扫描已安装应用…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (apps.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "没有发现声明 Shizuku API 的应用。装一个 Shizuku 兼容应用后会出现在这里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(apps, key = { it.packageName }) { entry ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (entry.isSelf) Icons.Default.CheckCircle else Icons.Default.Android,
                            contentDescription = null,
                            tint = if (entry.isSelf) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    entry.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.isSelf) {
                                    Spacer(Modifier.width(6.dp))
                                    StatusPill("本应用", true)
                                }
                            }
                            Text(
                                "${entry.packageName} · v${entry.versionName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { PrivilegeApps.openAppInfo(context, entry.packageName) }) {
                            Text("信息")
                        }
                        if (entry.packageName != PrivilegeApps.SHIZUKU_PACKAGE) {
                            TextButton(onClick = {
                                if (!PrivilegeApps.openApp(context, entry.packageName)) {
                                    state.showMessage("打不开这个应用")
                                }
                            }) { Text("打开") }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Shizuku 的授权状态保存在 Shizuku 管理器自己的存储里，本应用读不到也改不了。" +
                    "这也是为什么本应用还提供了自己的 Root / 无线调试通道——不依赖 Shizuku 也能做深度功能。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}
