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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.xb.tool.apps.AppRepository
import com.xb.tool.apps.InstalledApp
import com.xb.tool.core.AppState
import com.xb.tool.ui.AppIcon
import com.xb.tool.ui.EmptyState
import com.xb.tool.ui.PermissionReminderDialog
import com.xb.tool.ui.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun AppFreezeScreen(state: AppState) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showReminder by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppRepository.load(context) }
        loading = false
    }

    fun tryFreeze(packageName: String, freeze: Boolean) {
        if (packageName in state.whitelist) {
            state.showMessage("白名单应用不可冻结")
            return
        }
        if (state.rootInfo.rootGranted || state.shizukuGranted) {
            state.setFrozen(packageName, freeze) { }
        } else if (state.rootRemind) {
            pending = packageName to freeze
            showReminder = true
        } else {
            state.ensureDeepPrivilege { ok ->
                if (ok) state.setFrozen(packageName, freeze) { }
                else state.showMessage("冻结需要 Root 或 Shizuku 权限")
            }
        }
    }

    val filtered = if (query.isBlank()) apps else apps.filter {
        it.label.contains(query, true) || it.packageName.contains(query, true)
    }
    val frozenCount = apps.count { AppRepository.isFrozen(context, it.packageName) || it.packageName in state.frozenApps }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AcUnit, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("应用冻结", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        StatusPill("已冻结 $frozenCount", active = frozenCount > 0)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "冻结不常用应用以阻止其后台运行。冻结不会卸载应用，但可能导致消息延迟，请谨慎选择。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "冻结需要 Root 或 Shizuku 权限",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索已安装应用") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (!state.rootInfo.rootGranted && !state.shizukuGranted) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "当前未授权 Root / Shizuku，点击开关时会先弹出权限提醒。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (loading) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在读取应用列表…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (filtered.isEmpty()) {
            item { EmptyState(Icons.Default.AcUnit, "没有匹配的应用", "换个关键词试试") }
        } else {
            items(filtered, key = { it.packageName }) { app ->
                val isWhitelisted = app.packageName in state.whitelist
                val frozen = app.packageName in state.frozenApps || AppRepository.isFrozen(context, app.packageName)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app.packageName, 42.dp, app.label)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                when {
                                    isWhitelisted -> "白名单应用 · 不可冻结"
                                    frozen -> "已冻结 · 后台已禁止运行"
                                    else -> app.packageName
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (frozen) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = frozen,
                            enabled = !isWhitelisted,
                            onCheckedChange = { checked -> tryFreeze(app.packageName, checked) }
                        )
                    }
                }
            }
        }
        item {
            Text(
                "解冻后应用会恢复正常运行。系统预装应用默认不显示，以避免误操作。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (showReminder) {
        PermissionReminderDialog(
            permissionName = "应用冻结 · Root / Shizuku",
            message = "冻结与解冻应用需要通过系统级权限执行 pm disable-user 命令。确认后将尝试申请 Root 或 Shizuku 权限。",
            onDismiss = {
                showReminder = false
                pending = null
            },
            onConfirm = {
                showReminder = false
                val target = pending
                state.ensureDeepPrivilege { ok ->
                    if (ok && target != null) {
                        state.setFrozen(target.first, target.second) { }
                    } else if (!ok) {
                        state.showMessage("未获得 Root / Shizuku 权限")
                    }
                    pending = null
                }
            }
        )
    }
}
