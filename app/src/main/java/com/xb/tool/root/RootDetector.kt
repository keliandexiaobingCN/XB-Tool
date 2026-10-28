package com.xb.tool.root

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.xb.tool.core.CheckItem
import com.xb.tool.core.RootInfo
import java.io.File

object RootDetector {
    private val suPaths = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su"
    )

    private val managerPackages = linkedMapOf(
        "com.topjohnwu.magisk" to "Magisk",
        "me.weishu.kernelsu" to "KernelSU",
        "com.rifsxd.ksunext" to "KernelSU Next",
        "me.bmax.apatch" to "APatch",
        "com.dergoogler.mmrl" to "MMRL"
    )

    fun detect(context: Context, probeRoot: Boolean = true): RootInfo {
        val pm = context.packageManager
        val suPath = findSuPath()
        val rootResult = if (probeRoot) Shell.exec("id", 6_000L, asRoot = true) else null
        val rootGranted = rootResult != null && !rootResult.timedOut && rootResult.out.contains("uid=0")

        var managerName = "未检测到"
        var managerVersion = "—"
        var managerPackage: String? = null
        managerPackages.forEach { (pkg, name) ->
            if (runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess) {
                managerName = name
                managerPackage = pkg
                managerVersion = runCatching {
                    pm.getPackageInfo(pkg, 0).versionName ?: "—"
                }.getOrDefault("—")
            }
        }

        var managerPath: String? = null
        var magiskVersion = ""
        var ksuVersion = ""
        var apatchVersion = ""
        var selinux = "未知"
        var kernel = System.getProperty("os.version") ?: ""
        var kernelFull = readText("/proc/version")
        var adbWritable = false

        if (rootGranted) {
            val script = """
                echo __SU__
                command -v su
                echo __MAGISK__
                magisk -v 2>/dev/null
                magisk -V 2>/dev/null
                echo __KSU__
                ksud -V 2>/dev/null
                echo __AP__
                apd -V 2>/dev/null
                echo __SELINUX__
                getenforce 2>/dev/null
                echo __KERNEL__
                uname -a
                echo __PATH__
                ls -d /data/adb/magisk /data/adb/ksu /data/adb/ap 2>/dev/null
                echo __WRITABLE__
                test -w /data/adb && echo yes || echo no
            """.trimIndent()
            val result = Shell.exec(script, 20_000L, asRoot = true)
            val sections = parseSections(result.out)
            magiskVersion = sections["MAGISK"]?.joinToString(" ") { it.trim() }?.trim().orEmpty()
            ksuVersion = sections["KSU"]?.joinToString(" ") { it.trim() }?.trim().orEmpty()
            apatchVersion = sections["AP"]?.joinToString(" ") { it.trim() }?.trim().orEmpty()
            sections["SELINUX"]?.firstOrNull { it.isNotBlank() }?.let { selinux = it.trim() }
            sections["KERNEL"]?.firstOrNull { it.isNotBlank() }?.let { kernelFull = it.trim() }
            sections["PATH"]?.firstOrNull { it.isNotBlank() }?.let { managerPath = it.trim() }
            adbWritable = sections["WRITABLE"]?.any { it.trim() == "yes" } == true
        } else {
            selinux = Shell.exec("getenforce", 3_000L).out.trim().ifBlank { "未知" }
            val uname = Shell.exec("uname -a", 3_000L).out.trim()
            if (uname.isNotBlank()) kernelFull = uname
        }

        if (managerName == "未检测到") {
            when {
                magiskVersion.isNotBlank() || File("/data/adb/magisk").exists() -> {
                    managerName = "Magisk"
                    managerPath = managerPath ?: "/data/adb/magisk"
                }
                ksuVersion.isNotBlank() || File("/data/adb/ksu").exists() -> {
                    managerName = "KernelSU"
                    managerPath = managerPath ?: "/data/adb/ksu"
                }
                apatchVersion.isNotBlank() || File("/data/adb/ap").exists() -> {
                    managerName = "APatch"
                    managerPath = managerPath ?: "/data/adb/ap"
                }
            }
        }

        if (managerVersion == "—" || managerVersion.isBlank()) {
            managerVersion = when (managerName) {
                "Magisk" -> magiskVersion.ifBlank { "—" }
                "KernelSU", "KernelSU Next" -> ksuVersion.ifBlank { "—" }
                "APatch" -> apatchVersion.ifBlank { "—" }
                else -> "—"
            }
        } else if (magiskVersion.isNotBlank() && managerName == "Magisk") {
            val code = magiskVersion.split("\\s+".toRegex()).lastOrNull { it.all(Char::isDigit) }
            if (code != null && !managerVersion.contains(code)) managerVersion = "$managerVersion ($code)"
        }

        if (kernel.isBlank()) kernel = kernelFull.substringBefore(" ").ifBlank { "未知" }
        if (kernelFull.isBlank()) kernelFull = kernel

        val suBinaryOk = suPath != null || managerPath != null
        val managerOk = managerName != "未检测到"

        val checks = listOf(
            CheckItem("su 二进制", suBinaryOk, suPath ?: "未找到 su"),
            CheckItem("Root Shell", rootGranted, when {
                rootGranted -> "uid=0"
                !probeRoot -> "未验证 · 需手动授权"
                else -> "无法获取 uid=0"
            }),
            CheckItem("Root 管理器", managerOk, "$managerName $managerVersion"),
            CheckItem("SELinux", selinux != "未知", selinux),
            CheckItem("/data/adb 可写", adbWritable, if (adbWritable) "可写" else "不可写"),
            CheckItem("内核信息", kernelFull.isNotBlank(), kernel)
        )

        val shizukuRunning = ShizukuUtils.isBinderAlive()
        val shizukuGranted = ShizukuUtils.isGranted()

        return RootInfo(
            rootGranted = rootGranted,
            managerName = managerName,
            managerVersion = managerVersion,
            managerPackage = managerPackage,
            managerPath = managerPath,
            suPath = suPath,
            kernel = kernel,
            kernelFull = kernelFull,
            selinux = selinux.ifBlank { "未知" },
            androidVersion = Build.VERSION.RELEASE ?: "未知",
            sdk = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "未知",
            securityPatch = if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH ?: "未知" else "未知",
            fingerprint = Build.FINGERPRINT ?: "未知",
            deviceModel = "${Build.BRAND} ${Build.MODEL}".trim(),
            checks = checks,
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun installedNonSystemAppCount(context: Context): Int {
        return runCatching {
            context.packageManager.getInstalledApplications(0).count {
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
        }.getOrDefault(0)
    }

    private fun findSuPath(): String? {
        suPaths.firstOrNull { runCatching { File(it).exists() }.getOrDefault(false) }?.let { return it }
        val which = Shell.exec("command -v su", 3_000L).out.trim()
        if (which.isNotBlank() && !which.contains("not found")) return which
        return null
    }

    private fun parseSections(text: String): Map<String, List<String>> {
        val sections = linkedMapOf<String, MutableList<String>>()
        var current = "_"
        sections[current] = mutableListOf()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("__") && trimmed.endsWith("__")) {
                current = trimmed.removeSurrounding("__")
                sections.getOrPut(current) { mutableListOf() }
            } else {
                sections.getOrPut(current) { mutableListOf() }.add(line)
            }
        }
        return sections
    }

    private fun readText(path: String): String = runCatching {
        File(path).takeIf { it.canRead() }?.readText()?.trim().orEmpty()
    }.getOrDefault("")
}
