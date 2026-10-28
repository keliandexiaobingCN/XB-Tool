package com.xb.tool.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.scan.ScanMode
import com.xb.tool.ui.EmptyState
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.XBListRow

private data class SearchEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val screen: Screen,
    val keywords: String
)

private object SearchHistory {
    private const val FILE = "xb_search"
    private const val KEY = "history"

    fun read(context: Context): List<String> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
    }

    fun add(context: Context, query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val list = (listOf(q) + read(context)).distinct().take(10)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString("\n")).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

@Composable
fun SearchScreen(state: AppState) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(SearchHistory.read(context)) }
    val focusRequester = remember { FocusRequester() }

    val entries = remember {
        listOf(
            SearchEntry("普通杀毒", "扫描常用目录与已安装应用", Icons.Default.Security, Screen.Scan(ScanMode.NORMAL), "扫描 病毒 普通 杀毒"),
            SearchEntry("强力杀毒", "系统分区深度扫描，需要 Root / Shizuku", Icons.Default.Bolt, Screen.Scan(ScanMode.DEEP), "强力 深度 杀毒 root shizuku"),
            SearchEntry("紧急救机", "摇一摇 / 按钮触发全量扫描与置顶救援", Icons.Default.Warning, Screen.Emergency, "紧急 救机 锁机 防锁 摇一摇 全量 扫描"),
            SearchEntry("实时防护模块", "监控下载与新安装应用", Icons.Default.VerifiedUser, Screen.Realtime, "实时 防护 监控 下载 安装"),
            SearchEntry("拦截记录", "查看实时检测拦截的威胁", Icons.Default.GppMaybe, Screen.Intercepts, "拦截 记录 威胁 病毒"),
            SearchEntry("隔离区", "查看已拦截和手动隔离的样本，可恢复或删除", Icons.Default.Lock, Screen.Quarantine, "隔离 样本 恢复 删除 拦截"),
            SearchEntry("病毒库版本", "检测本地病毒库更新", Icons.Default.SystemUpdate, Screen.Update, "病毒库 更新 特征"),
            SearchEntry("防广告", "DNS 过滤、拦截率、节省流量与自定义过滤器", Icons.Default.Block, Screen.AdBlock, "防广告 adblock 拦截 dns 过滤 安全搜索"),
            SearchEntry("网络防护", "DDoS 连接监测、网页风险警告与 DNS 安全", Icons.Default.Security, Screen.Network, "网络 ddos 风险 警告 防护"),
            SearchEntry("浏览器", "无痕浏览、收藏、插件与搜索引擎", Icons.Default.Language, Screen.Browser, "浏览器 网页 无痕 收藏 插件 bing google 百度"),
            SearchEntry("二维码生成器", "文本或链接生成二维码", Icons.Default.QrCode2, Screen.QrCode, "二维码 qr 生成 分享"),
            SearchEntry("系统资源检测", "CPU / GPU 频率与占用、内存、温度、功率实时监控", Icons.Default.MonitorHeart, Screen.SystemMonitor, "系统 资源 cpu gpu 内存 温度 功率 频率 核心"),
            SearchEntry("公告", "版本动态、作者说明与更新公告", Icons.Default.Info, Screen.Announcements, "公告 更新 说明 作者"),
            SearchEntry("作者与反馈", "作者头像、反馈邮箱与意见反馈", Icons.Default.Person, Screen.Author, "作者 反馈 邮箱 意见"),
            SearchEntry("白名单", "管理不扫描、不清理、不冻结的应用", Icons.Default.ListAlt, Screen.Whitelist, "白名单 应用 排除"),
            SearchEntry("ROOT 环境检测", "Root、管理器、SELinux 检查", Icons.Default.VerifiedUser, Screen.Root, "root 环境 检测 magisk kernelsu"),
            SearchEntry("ROOT 内核版本", "内核与管理器版本详情", Icons.Default.Memory, Screen.Kernel, "root 内核 版本 管理器"),
            SearchEntry("音频转换器", "音频格式转换，输出 WAV / M4A", Icons.Default.MusicNote, Screen.Audio, "音频 转换 mp3 aac flac wav m4a"),
            SearchEntry("PDF 转换", "图片转 PDF、PDF 转图片", Icons.Default.PictureAsPdf, Screen.Pdf, "pdf 图片 文档"),
            SearchEntry("云盘直链下载", "解析链接并下载文件", Icons.Default.CloudDownload, Screen.Cloud, "云盘 下载 直链 链接"),
            SearchEntry("内存清理", "清理应用缓存与残留", Icons.Default.CleaningServices, Screen.Memory, "内存 缓存 清理 存储"),
            SearchEntry("运存清理", "结束后台进程释放运行内存", Icons.Default.Speed, Screen.Ram, "运存 ram 加速 内存"),
            SearchEntry("应用冻结", "冻结不常用应用，阻止后台运行", Icons.Default.AcUnit, Screen.Freeze, "冻结 应用 禁用"),
            SearchEntry("自定义个性化", "颜色、背景、字体、UI 动画与毛玻璃", Icons.Default.Palette, Screen.Theme, "主题 个性化 颜色 背景 字体 毛玻璃 动画"),
            SearchEntry("缓存清理", "查看并清理缓存占用", Icons.Default.DeleteSweep, Screen.Cache, "缓存 清理 空间"),
            SearchEntry("通知管理", "扫描结果、拦截与权限提醒", Icons.Default.Notifications, Screen.Notification, "通知 提醒 权限"),
            SearchEntry("极致后台保活", "电池白名单、后台行为与自启动", Icons.Default.BatteryChargingFull, Screen.Extreme, "保活 后台 电池 自启动"),
            SearchEntry("Shizuku 授权", "连接 Shizuku 用户服务", Icons.Default.Key, Screen.Settings, "shizuku 授权 权限"),
            SearchEntry("ADB 增强服务", "无线调试配对 / Root / 有线 ADB 统一特权通道", Icons.Default.Wifi, Screen.PrivilegeService, "无线调试 adb 配对 shell 免root wifi 授权 特权 服务"),
            SearchEntry("授权应用", "查看声明了 Shizuku API 的应用", Icons.Default.Key, Screen.PrivilegeApps, "授权 应用 shizuku 权限"),
            SearchEntry("关于", "版本、许可与隐私说明", Icons.Default.Info, Screen.About, "关于 版本 许可 隐私")
        )
    }

    val filtered = if (query.isBlank()) entries else entries.filter {
        it.title.contains(query, true) || it.subtitle.contains(query, true) ||
            it.keywords.contains(query, true)
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("搜索功能、设置与工具") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (query.isBlank() && history.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("搜索历史", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        SearchHistory.clear(context)
                        history = emptyList()
                    }) { Text("清空") }
                }
            }
            items(history, key = { "h-$it" }) { item ->
                XBListRow(
                    icon = Icons.Default.Search,
                    title = item,
                    subtitle = "点击重新搜索",
                    onClick = { query = item }
                )
            }
        }

        if (query.isNotBlank() && filtered.isNotEmpty()) {
            item { SectionHeader("包含「$query」的选项") }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.Search,
                    "没有找到结果",
                    "试试搜索「清理」「Root」「PDF」"
                )
            }
        } else {
            items(filtered, key = { it.title }) { entry ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInHorizontally { -it / 4 }
                ) {
                    XBListRow(
                        icon = entry.icon,
                        title = entry.title,
                        subtitle = entry.subtitle,
                        onClick = {
                            SearchHistory.add(context, query.ifBlank { entry.title })
                            history = SearchHistory.read(context)
                            state.navigate(entry.screen)
                        }
                    )
                }
            }
        }

        item {
            Text(
                if (query.isBlank()) "共 ${entries.size} 个功能" else "找到 ${filtered.size} 个结果",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
