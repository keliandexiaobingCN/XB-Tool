package com.xb.tool.ui.screens

import android.content.Context
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.adblock.AdBlockStore
import com.xb.tool.browser.BrowserPrefs
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.ui.InfoRow
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBListRow
import com.xb.tool.ui.glassSurfaceColor
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun NetworkScreen(state: AppState) {
    val context = LocalContext.current
    var ddosGuard by remember { mutableStateOf(NetworkPrefs.ddosGuard(context)) }
    var riskWarning by remember { mutableStateOf(NetworkPrefs.riskWarning(context)) }
    var dnsSafe by remember { mutableStateOf(NetworkPrefs.dnsSafe(context)) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("网络防护", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "本机连接异常监测、网页风险警告与 DNS 过滤联动",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(if (ddosGuard) "监测中" else "未开启", active = ddosGuard)
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
                        icon = Icons.Default.NetworkCheck,
                        title = "DDoS / 连接洪泛监测",
                        subtitle = "监测本机异常连接速率与端口扫描；受系统限制无法拦截运营商级攻击",
                        onClick = { },
                        trailing = {
                            Switch(checked = ddosGuard, onCheckedChange = {
                                ddosGuard = it
                                NetworkPrefs.setDdosGuard(context, it)
                            })
                        }
                    )
                    XBListRow(
                        icon = Icons.Default.Warning,
                        title = "网页风险警告",
                        subtitle = "浏览器访问 HTTP、IP 直连、可疑登录页时弹出警告",
                        onClick = { },
                        trailing = {
                            Switch(checked = riskWarning, onCheckedChange = {
                                riskWarning = it
                                NetworkPrefs.setRiskWarning(context, it)
                                BrowserPrefs.prefs(context).edit().putBoolean("warning", it).apply()
                            })
                        }
                    )
                    XBListRow(
                        icon = Icons.Default.Block,
                        title = "DNS 安全过滤",
                        subtitle = "联动防广告 DNS 过滤，拦截已知恶意/广告域名",
                        onClick = { state.navigate(Screen.AdBlock) },
                        trailing = {
                            Switch(checked = dnsSafe, onCheckedChange = {
                                dnsSafe = it
                                NetworkPrefs.setDnsSafe(context, it)
                            })
                        }
                    )
                }
            }
        }

        item { SectionHeader("当前防护状态") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    InfoRow("DDoS 监测", if (ddosGuard) "已开启（阈值 60 次/5 秒）" else "未开启", Icons.Default.NetworkCheck)
                    InfoRow("网页风险警告", if (riskWarning) "已开启" else "未开启", Icons.Default.Warning)
                    InfoRow("DNS 安全过滤", if (dnsSafe) "已开启" else "未开启", Icons.Default.Block)
                    InfoRow("当前广告规则", "${AdBlockStore.stats(context).rulesCount} 条", Icons.Default.Security)
                }
            }
        }

        item { SectionHeader("拦截记录") }
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    val stats = AdBlockStore.stats(context)
                    val top = stats.topBlocked
                    Text(
                        "已拦截 ${stats.blockedQueries} 次 / 总解析 ${stats.totalQueries} 次",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "估算节省 ${com.xb.tool.util.FileUtils.formatBytes(stats.savedBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (top.isEmpty()) {
                        Text(
                            "暂无拦截记录。开启防广告后，命中的广告 / 追踪 / 风险域名会显示在这里。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        top.take(8).forEach { (domain, count) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    domain,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "$count 次",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "说明：Android 应用无法在运营商侧清洗 DDoS 流量。这里提供本机层面的连接洪泛/端口扫描告警，并配合 DNS 过滤减少恶意域名访问；真正的 DDoS 缓解需要网络服务商或路由器完成。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "病毒库离线回退：更新成功后会把特征保存到本机（virus_db_remote.json 与 virus_db_backup.json），公共更新源不可用时仍使用本地特征。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

object NetworkPrefs {
    private const val FILE = "xb_network"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun ddosGuard(context: Context): Boolean = prefs(context).getBoolean("ddos", false)

    fun setDdosGuard(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("ddos", value).apply()
    }

    fun riskWarning(context: Context): Boolean = prefs(context).getBoolean("risk_warning", true)

    fun setRiskWarning(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("risk_warning", value).apply()
    }

    fun dnsSafe(context: Context): Boolean = prefs(context).getBoolean("dns_safe", false)

    fun setDnsSafe(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("dns_safe", value).apply()
    }
}
