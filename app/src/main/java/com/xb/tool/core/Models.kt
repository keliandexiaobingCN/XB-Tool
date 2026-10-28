package com.xb.tool.core

import com.xb.tool.scan.ScanMode

enum class LogLevel { INFO, OK, WARN, ERROR }

data class LogEntry(
    val id: Long,
    val time: String,
    val level: LogLevel,
    val message: String
)

enum class Severity(val label: String) {
    HIGH("高危"),
    MEDIUM("中危"),
    LOW("低危")
}

data class Threat(
    val id: String,
    val name: String,
    val path: String,
    val sha256: String? = null,
    val severity: Severity = Severity.MEDIUM,
    val description: String = "",
    val packageName: String? = null,
    val source: String = "文件扫描"
)

data class ScanUiState(
    val running: Boolean = false,
    val mode: ScanMode = ScanMode.NORMAL,
    val scanned: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val threats: List<Threat> = emptyList(),
    val finished: Boolean = false,
    val startedAt: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
)

data class InterceptRecord(
    val id: Long,
    val time: Long,
    val name: String,
    val path: String,
    val action: String,
    val signature: String
)

data class CheckItem(
    val title: String,
    val ok: Boolean,
    val detail: String
)

data class AppCacheEntry(
    val packageName: String,
    val label: String,
    val size: Long
)

data class RunningAppEntry(
    val packageName: String,
    val label: String,
    val pid: Int,
    val rssKb: Long,
    val isSystem: Boolean
)

data class RootInfo(
    val rootGranted: Boolean = false,
    val managerName: String = "未检测到",
    val managerVersion: String = "—",
    val managerPackage: String? = null,
    val managerPath: String? = null,
    val suPath: String? = null,
    val kernel: String = "",
    val kernelFull: String = "",
    val selinux: String = "未知",
    val androidVersion: String = "",
    val sdk: Int = 0,
    val abi: String = "",
    val securityPatch: String = "",
    val fingerprint: String = "",
    val deviceModel: String = "",
    val checks: List<CheckItem> = emptyList(),
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val updatedAt: Long = 0L
) {
    val healthy: Boolean
        get() = rootGranted && checks.isNotEmpty() && checks.all { it.ok }
}

data class ThemeConfig(
    val seed: String = "teal",
    val darkMode: String = "dark",
    val fontScale: Float = 1f,
    val fontFamily: String = "default",
    val backgroundPath: String? = null,
    val backgroundVideoPath: String? = null,
    val backgroundBlur: Float = 0f,
    val backgroundAlpha: Float = 1f,
    val uiAnimation: Boolean = true,
    val glassEffect: Boolean = true,
    val glassAlpha: Float = 0.42f,
    val backgroundColor: String? = null,
    val navStyle: String = "slide"
)

sealed interface Screen {
    val title: String

    data object Home : Screen { override val title = "XB工具" }
    data object Tools : Screen { override val title = "其他功能" }
    data object Settings : Screen { override val title = "设置" }
    data object Root : Screen { override val title = "ROOT 环境检测" }
    data object Kernel : Screen { override val title = "ROOT 内核版本" }
    data object Update : Screen { override val title = "更新检测" }
    data object Announcements : Screen { override val title = "公告" }
    data object Whitelist : Screen { override val title = "白名单" }
    data object About : Screen { override val title = "关于" }
    data object License : Screen { override val title = "开源许可" }
    data object Author : Screen { override val title = "作者与反馈" }
    data object Search : Screen { override val title = "搜索" }
    data object Intercepts : Screen { override val title = "拦截记录" }
    data object Quarantine : Screen { override val title = "隔离区" }
    data object Audio : Screen { override val title = "音频转换器" }
    data object Cloud : Screen { override val title = "云盘直链下载" }
    data object Pdf : Screen { override val title = "PDF 转换" }
    data object Memory : Screen { override val title = "内存清理" }
    data object Ram : Screen { override val title = "运存清理" }
    data object Freeze : Screen { override val title = "应用冻结" }
    data object Theme : Screen { override val title = "自定义个性化" }
    data object Cache : Screen { override val title = "缓存清理" }
    data object Notification : Screen { override val title = "通知管理" }
    data object Extreme : Screen { override val title = "极致后台保活" }
    data object Emergency : Screen { override val title = "紧急救机" }
    data object AdBlock : Screen { override val title = "防广告" }
    data object QrCode : Screen { override val title = "二维码生成器" }
    data object Browser : Screen { override val title = "浏览器" }
    data object Network : Screen { override val title = "网络防护" }
    data object Realtime : Screen { override val title = "实时防护模块" }
    data object AdbPair : Screen { override val title = "无线调试配对" }
    data object PrivilegeService : Screen { override val title = "ADB 增强服务" }
    data object PrivilegeApps : Screen { override val title = "授权应用" }
    data object SystemMonitor : Screen { override val title = "系统资源检测" }
    data class Scan(val mode: ScanMode) : Screen {
        override val title: String get() = mode.title
    }

    val isTabRoot: Boolean
        get() = this is Home || this is Tools || this is Settings
}
