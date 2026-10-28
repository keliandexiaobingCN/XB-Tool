package com.xb.tool.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 厂商定制 ROM 能力探测。
 * 只在“确认存在该入口”的机型上返回 true，避免给出无效按钮。
 */
object DeviceCompat {
    private const val OPPO_COMPONENT =
        "com.coloros.safecenter/com.coloros.safecenter.permission.PowerConsumptionOptimizeActivity"

    fun brand(): String = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()

    /**
     * 是否为 ColorOS 系（OPPO / 一加 / 真我）。
     * 电池优化、耗电异常优化这类入口目前只在 ColorOS 系上确认存在，
     * 其它系统（原生、MIUI、HyperOS 等）不显示相关选项。
     */
    fun isColorOsFamily(): Boolean {
        val b = brand()
        return b.contains("oppo") || b.contains("oneplus") || b.contains("realme") ||
            b.contains("oplus")
    }

    /** ColorOS 的「耗电异常优化」入口。 */
    fun hasOppoPowerSaver(context: Context): Boolean = isColorOsFamily()

    fun openOppoPowerSaver(context: Context) {
        val candidates = listOf(
            OPPO_COMPONENT,
            "com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
        )
        for (candidate in candidates) {
            val parts = candidate.split('/')
            if (parts.size != 2) continue
            val intent = Intent().apply {
                component = android.content.ComponentName(parts[0], parts[1])
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
        }
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /**
     * 是否显示「电池优化 / 完全后台行为」相关选项。
     * 按实际情况：目前确认只有 ColorOS 系有该入口，其它系统一律不显示。
     */
    fun hasBatteryOptimizationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        return isColorOsFamily()
    }
}
