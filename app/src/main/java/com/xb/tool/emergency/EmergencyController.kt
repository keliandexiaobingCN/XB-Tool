package com.xb.tool.emergency

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.xb.tool.core.Threat
import com.xb.tool.root.ShizukuUtils
import com.xb.tool.root.Shell
import com.xb.tool.scan.ScanEngine
import com.xb.tool.scan.ScanMode
import com.xb.tool.scan.VirusDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LockRisk(
    val packageName: String,
    val label: String,
    val reasons: List<String>,
    val severity: Int,
    val isSystem: Boolean
)

data class EmergencyAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

data class EmergencyResult(
    val threats: List<Threat>,
    val risks: List<LockRisk>,
    val apps: List<EmergencyAppInfo>,
    val scannedApps: Int,
    val elapsedMs: Long,
    val rooted: Boolean,
    val shizuku: Boolean
)

object LockRiskScanner {
    private val suspiciousWords = listOf(
        "lock", "kiosk", "lockscreen", "锁机", "锁屏", "一键锁", "deviceadmin"
    )

    fun scan(context: Context): List<LockRisk> {
        val pm = context.packageManager
        val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        val admins = runCatching { dpm?.activeAdmins.orEmpty() }.getOrDefault(emptyList())
        val adminPackages = admins.map { it.packageName }.toSet()
        val accessibilityRaw = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()
        val accessibilityPackages = accessibilityRaw.split(':').mapNotNull { raw ->
            val component = ComponentName.unflattenFromString(raw)
            component?.packageName
        }.toSet()
        val defaultHome = runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        val installed = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        val result = mutableListOf<LockRisk>()
        installed.forEach { info: ApplicationInfo ->
            val pkg = info.packageName
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            // 系统应用默认不进入风险列表，避免把 OPPO / Google 等正常组件误报为锁机风险。
            if (isSystem) return@forEach
            val reasons = mutableListOf<String>()
            if (pkg in adminPackages) reasons += "已激活设备管理器"
            if (pkg in accessibilityPackages) reasons += "已启用无障碍服务"
            if (pkg == defaultHome) reasons += "当前默认桌面"
            if (pkg !in adminPackages && pkg !in accessibilityPackages && pkg != defaultHome) {
                val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault("")
                val suspicious = suspiciousWords.any {
                    pkg.contains(it, ignoreCase = true) || label.contains(it, ignoreCase = true)
                }
                if (suspicious && hasOverlay(context, pkg)) reasons += "带悬浮窗的锁屏类应用"
            }
            if (reasons.isEmpty()) return@forEach
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
            val severity = when {
                pkg in adminPackages || pkg == defaultHome -> 3
                pkg in accessibilityPackages -> 2
                else -> 1
            }
            result += LockRisk(pkg, label, reasons, severity, false)
        }
        return result.sortedByDescending { it.severity }
    }

    private fun hasOverlay(context: Context, pkg: String): Boolean {
        return runCatching {
            val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                context.packageManager.getApplicationInfo(pkg, 0).uid,
                pkg
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}

object EmergencyController {
    suspend fun scan(
        context: Context,
        onProgress: (done: Int, total: Int, current: String) -> Unit
    ): EmergencyResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val engine = ScanEngine(context, VirusDatabase.load(context))
        val pm = context.packageManager
        val apps = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
            .sortedBy { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
        val threats = linkedMapOf<String, Threat>()
        val appList = mutableListOf<EmergencyAppInfo>()
        apps.forEachIndexed { index, info ->
            val file = File(info.sourceDir)
            onProgress(index + 1, apps.size, info.packageName)
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(info.packageName)
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            // 过滤纯类库/共享包：它们没有可结束的进程，列出来只会造成误导
            val isLibraryOnly = (info.flags and ApplicationInfo.FLAG_HAS_CODE) == 0 ||
                info.packageName.startsWith("android.ext.") ||
                info.packageName.contains(".cts.") ||
                info.packageName.endsWith(".apk") ||
                info.packageName == "android"
            if (!isLibraryOnly) {
                appList += EmergencyAppInfo(
                    packageName = info.packageName,
                    label = label,
                    isSystem = isSystem
                )
            }
            runCatching {
                engine.scanFile(file, info.packageName, "紧急全量扫描")?.let {
                    threats[it.path] = it
                }
            }
        }
        val risks = LockRiskScanner.scan(context)
        EmergencyResult(
            threats = threats.values.toList(),
            risks = risks,
            apps = appList.sortedBy { it.label.lowercase() },
            scannedApps = apps.size,
            elapsedMs = System.currentTimeMillis() - started,
            rooted = Shell.rootAvailable(),
            shizuku = ShizukuUtils.isGranted()
        )
    }

    suspend fun forceStop(context: Context, packageName: String): String = withContext(Dispatchers.IO) {
        if (!Shell.rootAvailable() && !ShizukuUtils.isGranted()) {
            return@withContext "需要 Root / Shizuku 才能强制结束应用"
        }
        val command = "am force-stop $packageName"
        val result = if (Shell.rootAvailable()) {
            Shell.exec(command, 10_000L, asRoot = true)
        } else {
            ShizukuUtils.exec(command)
        }
        if (result.success || result.text.isBlank()) "已结束 $packageName" else "结束失败：${result.text}"
    }

    suspend fun isolate(context: Context, risks: List<LockRisk>): String = withContext(Dispatchers.IO) {
        val targets = risks.filter { !it.isSystem }.map { it.packageName }.distinct()
        if (targets.isEmpty()) return@withContext "没有可隔离的非系统风险应用"
        if (!Shell.rootAvailable() && !ShizukuUtils.isGranted()) {
            return@withContext "需要 Root / Shizuku 才能强制结束或冻结风险应用；请先在安全中心授权"
        }
        if (!Shell.rootAvailable() && !ShizukuUtils.isServiceReady()) {
            return@withContext "Shizuku 服务尚未连接，请回到安全中心重新授权"
        }
        var count = 0
        targets.forEach { pkg ->
            val command = "am force-stop $pkg; pm disable-user --user 0 $pkg"
            val result = if (Shell.rootAvailable()) {
                Shell.exec(command, 15_000L, asRoot = true)
            } else {
                ShizukuUtils.exec(command)
            }
            if (result.success || result.text.isBlank()) count++
        }
        "已强制处理 $count 个风险应用"
    }
}
