package com.xb.tool.root

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File

data class IntegrityCheck(
    val title: String,
    val ok: Boolean,
    val detail: String
)

data class IntegrityInfo(
    val verdict: String,
    val level: Int,
    val checks: List<IntegrityCheck>,
    val leftovers: List<String>
)

object IntegrityDetector {
    private val managerPackages = listOf(
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "com.rifsxd.ksunext",
        "me.bmax.apatch",
        "com.dergoogler.mmrl"
    )

    fun detect(context: Context): IntegrityInfo {
        val checks = mutableListOf<IntegrityCheck>()
        val leftovers = mutableListOf<String>()
        val props = readProps(
            "ro.boot.verifiedbootstate",
            "ro.boot.flash.locked",
            "ro.boot.veritymode",
            "ro.build.tags",
            "ro.build.type",
            "ro.debuggable",
            "ro.secure",
            "ro.product.manufacturer",
            "ro.product.model"
        )
        val bootState = props["ro.boot.verifiedbootstate"].orEmpty().ifBlank { "未知" }
        val bootLocked = props["ro.boot.flash.locked"].orEmpty()
        val tags = props["ro.build.tags"].orEmpty()
        val debuggable = props["ro.debuggable"].orEmpty()
        checks += IntegrityCheck(
            "Bootloader 验证状态",
            bootState.equals("green", true) && bootLocked != "0",
            "verifiedbootstate=$bootState，flash.locked=${bootLocked.ifBlank { "未知" }}"
        )
        checks += IntegrityCheck(
            "系统构建签名",
            tags.contains("release-keys"),
            "build.tags=${tags.ifBlank { "未知" }}，build.type=${props["ro.build.type"].orEmpty()}"
        )
        checks += IntegrityCheck(
            "调试属性",
            debuggable != "1",
            "ro.debuggable=${debuggable.ifBlank { "未知" }}，ro.secure=${props["ro.secure"].orEmpty()}"
        )
        val suPath = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/data/local/bin/su", "/data/local/xbin/su", "/system/sd/xbin/su",
            "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su"
        ).firstOrNull { runCatching { File(it).exists() }.getOrDefault(false) }
        checks += IntegrityCheck("su 可执行文件", suPath == null, suPath ?: "未发现")
        if (suPath != null) leftovers += suPath

        val managers = managerPackages.filter {
            runCatching { context.packageManager.getApplicationInfo(it, 0) }.isSuccess
        }
        checks += IntegrityCheck(
            "Root 管理器",
            managers.isEmpty(),
            if (managers.isEmpty()) "未安装常见 Root 管理器" else "检测到：${managers.joinToString()}"
        )
        leftovers += managers

        val residuePaths = listOf(
            "/data/adb/modules", "/data/adb/ksu", "/data/adb/magisk",
            "/data/adb/ap", "/system/etc/init.d", "/system/xbin/busybox",
            "/data/local/tmp/ksud", "/data/local/tmp/su"
        )
        residuePaths.forEach { path ->
            if (runCatching { File(path).exists() }.getOrDefault(false)) leftovers += path
        }
        checks += IntegrityCheck(
            "Root 残留路径",
            leftovers.isEmpty(),
            if (leftovers.isEmpty()) "未发现常见 su / 模块残留" else "发现 ${leftovers.size} 项残留"
        )
        val selinux = Shell.exec("getenforce", 4_000L).out.ifBlank { "未知" }
        checks += IntegrityCheck(
            "SELinux",
            selinux.contains("Enforcing", true),
            "getenforce=$selinux"
        )
        val adbEnabled = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        }.getOrDefault(false)
        checks += IntegrityCheck(
            "ADB 调试",
            !adbEnabled,
            if (adbEnabled) "ADB 调试已开启（开发/调试状态）" else "ADB 调试未开启"
        )
        val writable = Shell.exec("mount | grep ' /system '", 4_000L).out
        val systemWritable = writable.contains("rw,")
        checks += IntegrityCheck(
            "系统分区",
            !systemWritable,
            if (systemWritable) "系统分区可写" else "系统分区只读或未检查到"
        )
        val emulator = Build.FINGERPRINT.contains("generic", true) ||
            Build.MODEL.contains("Emulator", true) ||
            Build.HARDWARE.contains("goldfish", true) ||
            Build.HARDWARE.contains("ranchu", true)
        checks += IntegrityCheck(
            "运行环境",
            !emulator,
            if (emulator) "模拟器 / 开发环境，完整性结果仅供参考" else "${Build.MANUFACTURER} ${Build.MODEL}"
        )

        val failed = checks.count { !it.ok }
        val level = when {
            leftovers.isNotEmpty() || failed >= 3 -> 2
            failed > 0 || emulator -> 1
            else -> 0
        }
        val verdict = when (level) {
            2 -> "检测到 Root 痕迹或系统修改"
            1 -> "存在开发/不确定项，建议人工确认"
            else -> "未发现明显 Root 或系统修改"
        }
        return IntegrityInfo(verdict, level, checks, leftovers.distinct())
    }

    private fun readProps(vararg keys: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        keys.forEach { key ->
            val value = Shell.exec("getprop $key", 3_000L).out.trim()
            result[key] = value
        }
        return result
    }
}
