package com.xb.tool.guard

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xb.tool.core.Prefs
import com.xb.tool.privilege.PrivilegeExec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 可疑应用识别（Shell 模式配套）。
 *
 * 判定原则（用户反馈后重做）：
 * 1. **只认真正的危险能力组合**，不再把「读取短信 + 联网」这种几乎人人都有的
 *    弱组合算作高风险——之前正是它把 OPPO 的 CTAutoRegist、钱包、Play 商店
 *    这些系统应用全部标红，属于典型误报。
 * 2. **系统应用一律不参与判定**。系统应用本就拥有大量权限，用权限组合去衡量它们
 *    只会产出噪音；真正需要防的是用户自己装进来的第三方应用。
 * 3. 判定依据分三类：能力组合（权限）、实际行为（以 root/特权身份运行）、
 *    已知远控/窃密类包名。每一项都要求「能直接造成破坏、窃取或远程控制」。
 */
object SuspiciousAppScanner {

    data class Suspicious(
        val packageName: String,
        val label: String,
        val risk: CommandGuard.Risk,
        val reason: String,
        val detail: String
    )

    /** 已提示过的包，避免每次轮询重复弹窗。 */
    private val prompted = mutableSetOf<String>()

    fun scan(context: Context): List<Suspicious> {
        val prefs = Prefs(context)
        val whitelist = prefs.commandWhitelist()
        val pm = context.packageManager
        val result = mutableListOf<Suspicious>()

        val rootPackages = rootPackages(context)
        val deviceAdmins = deviceAdminPackages(context)

        val installed = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        }.getOrDefault(emptyList())

        for (info in installed) {
            val pkg = info.packageName
            if (pkg == context.packageName) continue
            if (pkg in whitelist) continue
            // 系统 / 预装应用不参与判定：它们的权限是厂商给的，标红没有意义
            if (isSystemApp(info)) continue

            val reasons = mutableListOf<String>()
            var risk = CommandGuard.Risk.MEDIUM

            fun raise(level: CommandGuard.Risk) {
                if (risk.order < level.order) risk = level
            }

            // ---------- 1. 实际行为：以 root 身份运行 ----------
            if (pkg in rootPackages) {
                reasons += "以 root 身份运行进程"
                raise(CommandGuard.Risk.CRITICAL)
            }

            // ---------- 2. 已知远控 / 窃密类应用 ----------
            knownRemote(pkg)?.let {
                reasons += it
                raise(CommandGuard.Risk.HIGH)
            }

            // ---------- 3. 能力组合：必须能拼成一条完整的攻击链 ----------
            val caps = capabilities(pm, info)

            if (pkg in deviceAdmins) {
                if (caps.contains("无障碍")) {
                    reasons += "设备管理器 + 无障碍（可静默操控与本机管控）"
                    raise(CommandGuard.Risk.CRITICAL)
                } else if (caps.contains("读取短信")) {
                    reasons += "设备管理器 + 读取短信"
                    raise(CommandGuard.Risk.HIGH)
                } else {
                    reasons += "已激活设备管理器（可执行锁屏 / 擦除）"
                    raise(CommandGuard.Risk.HIGH)
                }
            }

            if (caps.contains("无障碍") && caps.contains("安装应用")) {
                reasons += "无障碍 + 静默安装（可自动装任意应用）"
                raise(CommandGuard.Risk.HIGH)
            }
            if (caps.contains("无障碍") && caps.contains("悬浮窗")) {
                reasons += "无障碍 + 悬浮窗（可覆盖界面并自动点击）"
                raise(CommandGuard.Risk.HIGH)
            }
            if (caps.contains("无障碍") && caps.contains("屏幕捕获")) {
                reasons += "无障碍 + 屏幕捕获（可实时读屏）"
                raise(CommandGuard.Risk.HIGH)
            }
            if (caps.contains("通知监听") && caps.contains("联网")) {
                reasons += "通知监听 + 联网（可截获验证码并外传）"
                raise(CommandGuard.Risk.HIGH)
            }
            // 短信类：必须叠加「外传」或「壳」能力才算危险，单读短信不算
            if (caps.contains("读取短信") &&
                (caps.contains("联网") && (caps.contains("无障碍") || caps.contains("悬浮窗") || caps.contains("安装应用")))
            ) {
                reasons += "读取短信 + 联网 + 操控能力（短信窃取链）"
                raise(CommandGuard.Risk.HIGH)
            }

            if (reasons.isEmpty()) continue

            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
            result += Suspicious(
                packageName = pkg,
                label = label,
                risk = risk,
                reason = reasons.first(),
                detail = reasons.joinToString("；") +
                    " · 权限：" + caps.joinToString("/").ifBlank { "无敏感权限" }
            )
        }
        return result.sortedByDescending { it.risk.order }
    }

    /** 是否系统 / 预装应用（含被更新过的系统应用）。 */
    private fun isSystemApp(info: ApplicationInfo): Boolean {
        val flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        if (info.flags and flags != 0) return true
        // 少数 ROM 的预装应用不带 FLAG_SYSTEM，用私有目录判断兜底
        val src = info.sourceDir ?: return false
        return src.startsWith("/system/") || src.startsWith("/product/") ||
            src.startsWith("/vendor/") || src.startsWith("/apex/") ||
            src.startsWith("/odm/") || src.startsWith("/system_ext/")
    }

    /**
     * 已知远控 / 屏幕共享 / 隐蔽监控类应用（按包名与关键词手动维护）。
     * 这些工具本身合法，但被滥用时就是远程控制通道，值得在可疑列表里提示。
     */
    private fun knownRemote(pkg: String): String? {
        val p = pkg.lowercase()
        KNOWN_REMOTE[p]?.let { return it }
        val keyword = REMOTE_KEYWORDS.firstOrNull { p.contains(it) } ?: return null
        return "远程控制类特征（含 $keyword）"
    }

    private val KNOWN_REMOTE = mapOf(
        "com.teamviewer.remotesupport" to "远程控制（TeamViewer QuickSupport）",
        "com.teamviewer.teamviewer.market.mobile" to "远程控制（TeamViewer）",
        "com.anydesk.anydeskandroid" to "远程控制（AnyDesk）",
        "com.oray.sunlogin" to "远程控制（向日葵）",
        "com.oray.sunlogin.remote" to "远程控制（向日葵）",
        "com.todesk.remote" to "远程控制（ToDesk）",
        "com.rustdesk.rustdesk" to "远程控制（RustDesk）",
        "com.sand.airdroid" to "远程管理与文件传输（AirDroid）",
        "com.sand.airmirror" to "远程镜像控制（AirMirror）"
    )

    private val REMOTE_KEYWORDS = listOf(
        "rustdesk", "anydesk", "teamviewer", "sunlogin", "airdroid", "airmirror", "todesk"
    )

    /** 把新发现的可疑应用转成告警记录（与命令告警同一套存储与弹窗）。 */
    fun toAlerts(items: List<Suspicious>): List<CommandGuard.Alert> {
        val now = System.currentTimeMillis()
        return items.mapIndexed { index, item ->
            CommandGuard.Alert(
                id = now + index,
                time = now,
                packageName = item.packageName,
                appLabel = item.label,
                risk = item.risk,
                command = item.detail,
                action = item.reason,
                source = "可疑应用"
            )
        }
    }

    /** 过滤出尚未提示过的应用，并登记。 */
    fun takeFresh(items: List<Suspicious>): List<Suspicious> {
        synchronized(prompted) {
            val fresh = items.filter { prompted.add(it.packageName) }
            if (prompted.size > 300) prompted.clear()
            return fresh
        }
    }

    fun resetPrompted() {
        synchronized(prompted) { prompted.clear() }
    }

    // ------------------------------------------------------------ 能力探测

    private fun capabilities(pm: PackageManager, info: ApplicationInfo): Set<String> {
        val caps = mutableSetOf<String>()
        val requested: Set<String> = runCatching {
            val flags = PackageManager.GET_PERMISSIONS
            val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(info.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(info.packageName, flags)
            }
            pkgInfo.requestedPermissions?.toSet().orEmpty()
        }.getOrDefault(emptySet())

        if (hasServiceWithPermission(pm, info.packageName, "android.permission.BIND_ACCESSIBILITY_SERVICE")) {
            caps += "无障碍"
        }
        if ("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" in requested ||
            hasServiceWithPermission(pm, info.packageName, "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        ) caps += "通知监听"
        if ("android.permission.SYSTEM_ALERT_WINDOW" in requested) caps += "悬浮窗"
        if ("android.permission.REQUEST_INSTALL_PACKAGES" in requested) caps += "安装应用"
        if ("android.permission.READ_SMS" in requested || "android.permission.RECEIVE_SMS" in requested) {
            caps += "读取短信"
        }
        if ("android.permission.INTERNET" in requested) caps += "联网"
        if ("android.permission.READ_CONTACTS" in requested) caps += "读取通讯录"
        if ("android.permission.RECORD_AUDIO" in requested) caps += "录音"
        if ("android.permission.CAMERA" in requested) caps += "相机"
        if ("android.permission.BIND_DEVICE_ADMIN" in requested) caps += "设备管理器"
        // 屏幕捕获：MediaProjection 需要前台服务类型 mediaProjection（Android 10+）
        val capturesScreen = runCatching {
            val pkgInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    info.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(info.packageName, PackageManager.GET_SERVICES)
            }
            val services = pkgInfo.services.orEmpty()
            fun serviceType(s: android.content.pm.ServiceInfo) =
                if (Build.VERSION.SDK_INT >= 29) s.foregroundServiceType else 0
            services.any { serviceType(it) and 0x20 != 0 }
        }.getOrDefault(false)
        if (capturesScreen) caps += "屏幕捕获"
        return caps
    }

    private fun hasServiceWithPermission(pm: PackageManager, pkg: String, permission: String): Boolean =
        runCatching {
            val services = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong())).services
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services
            }
            services?.any { it.permission == permission } == true
        }.getOrDefault(false)

    /** 以 uid 0 运行的非系统应用。 */
    private fun rootPackages(context: Context): Set<String> {
        if (!PrivilegeExec.hasPrivilege()) return emptySet()
        val result = runCatching {
            PrivilegeExec.exec(context, "ps -A -o UID,ARGS 2>/dev/null", 12_000L)
        }.getOrNull() ?: return emptySet()
        val packages = mutableSetOf<String>()
        val pm = context.packageManager
        for (line in result.out.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) continue
            if (parts[0] != "0") continue
            val cmd = parts[1]
            // 只有以应用私有目录路径启动的进程才算该应用在 root 下运行
            val match = Regex("""/data/(?:user/\d+/|data/)?([a-zA-Z0-9_.]+)/""").find(cmd) ?: continue
            val pkg = match.groupValues[1]
            if (pkg.count { it == '.' } >= 1 && runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess) {
                packages += pkg
            }
        }
        return packages
    }

    private fun deviceAdminPackages(context: Context): Set<String> {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return emptySet()
        return runCatching {
            dpm.activeAdmins?.mapNotNull { it.packageName }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
    }

    // ------------------------------------------------------------ 持久化

    fun loadPrompted(context: Context): Set<String> {
        val raw = Prefs(context).suspiciousPromptedJson ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun savePrompted(context: Context, packages: Set<String>) {
        val array = JSONArray()
        packages.take(300).forEach { array.put(it) }
        Prefs(context).suspiciousPromptedJson = array.toString()
    }

    fun describeItem(item: Suspicious): String =
        JSONObject().put("pkg", item.packageName).put("reason", item.reason).toString()
}
