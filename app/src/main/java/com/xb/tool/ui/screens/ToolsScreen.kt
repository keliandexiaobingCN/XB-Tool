package com.xb.tool.ui.screens

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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.ui.XBListRow

private data class ToolEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: Screen,
    val group: String,
    val keywords: String = "",
    val badge: String? = null
)

@Composable
fun ToolsScreen(state: AppState) {
    var query by remember { mutableStateOf("") }
    val tools = remember {
        listOf(
            ToolEntry("音频转换器", "MP3 / AAC / FLAC 解码 · 输出 WAV / M4A", Icons.Default.MusicNote, Screen.Audio, "转换与下载", "音频 音乐 mp3 aac wav m4a flac 转换"),
            ToolEntry("PDF 转换", "图片转 PDF、PDF 转图片", Icons.Default.PictureAsPdf, Screen.Pdf, "转换与下载", "pdf 图片 文档 转换 合并"),
            ToolEntry("云盘直链下载", "解析分享链接，保存直链文件", Icons.Default.CloudDownload, Screen.Cloud, "转换与下载", "下载 云盘 直链 链接 网盘"),
            ToolEntry("二维码生成器", "文本 / 链接生成二维码，保存或分享", Icons.Default.QrCode2, Screen.QrCode, "转换与下载", "二维码 qr 生成 链接 分享"),
            ToolEntry("浏览器", "默认无痕 · 插件 · 收藏 · 搜索引擎 · 自定义背景", Icons.Default.Language, Screen.Browser, "转换与下载", "浏览器 web 网页 无痕 搜索 bing google 百度 收藏 插件"),
            ToolEntry("网络防护", "DDoS 连接防护、网页风险警告与 DNS 安全", Icons.Default.Security, Screen.Network, "安全防护", "网络 ddos 攻击 防护 网页 风险 警告 dns 连接"),
            ToolEntry("防广告", "DNS 过滤广告与追踪，统计拦截率与流量", Icons.Default.Block, Screen.AdBlock, "安全防护", "广告 拦截 adblock dns 过滤 追踪 规则"),
            ToolEntry("内存清理", "清理应用缓存与残留文件", Icons.Default.CleaningServices, Screen.Memory, "清理与优化", "存储 缓存 内存 清理 garbage"),
            ToolEntry("运存清理", "结束后台进程，释放运行内存", Icons.Default.Speed, Screen.Ram, "清理与优化", "运存 ram 内存 加速 清理"),
            ToolEntry("应用冻结", "冻结不常用应用，阻止后台运行", Icons.Default.AcUnit, Screen.Freeze, "清理与优化", "冻结 禁用 应用 后台", "Root/Shizuku"),
            ToolEntry("ROOT 环境检测", "检测 Root、管理器与内核版本", Icons.Default.VerifiedUser, Screen.Root, "Root 工具", "root magisk kernelsu apatch 环境 检测 内核", "需 Root"),
            ToolEntry("ROOT 内核版本", "内核、管理器与版本详细信息", Icons.Default.Memory, Screen.Kernel, "Root 工具", "root 内核 kernel 版本 管理器 magisk", "需 Root"),
            ToolEntry("ADB 增强服务", "无线调试配对 / Root / 有线 ADB 统一特权通道", Icons.Default.Wifi, Screen.PrivilegeService, "Root 工具", "无线调试 adb 配对 shell 免root 授权 wifi 特权 服务", "免 Root"),
            ToolEntry("系统资源检测", "CPU / GPU 频率与占用、内存、温度、功率实时监控", Icons.Default.MonitorHeart, Screen.SystemMonitor, "清理与优化", "系统 资源 检测 cpu gpu 内存 温度 功率 频率 核心 占用", "部分需 Root")
        )
    }
    val filtered = if (query.isBlank()) tools else tools.filter {
        it.title.contains(query, true) || it.subtitle.contains(query, true) ||
            it.keywords.contains(query, true) || it.group.contains(query, true)
    }
    val grouped = filtered.groupBy { it.group }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                RoundedCornerShape(15.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "工具与优化",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "文件转换 · 系统清理 · Root 增强",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    "没有匹配的功能",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        grouped.forEach { (group, entries) ->
            item(key = "header-$group") {
                SectionHeader(group)
            }
            entries.forEach { tool ->
                item(key = tool.title) {
                    XBListRow(
                        icon = tool.icon,
                        title = tool.title,
                        subtitle = tool.subtitle,
                        onClick = { state.navigate(tool.screen) },
                        trailing = {
                            if (tool.badge != null) {
                                StatusPill(tool.badge, active = true)
                            }
                        }
                    )
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "提示：所有转换与清理操作均在本机完成；Root 相关功能请确认已了解风险。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
