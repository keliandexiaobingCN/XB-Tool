package com.xb.tool.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import com.xb.tool.core.Prefs

object MemoryTools {
    data class MemInfo(
        val total: Long,
        val available: Long,
        val used: Long,
        val percent: Int,
        val lowMemory: Boolean
    )

    data class CleanResult(val freed: Long, val killed: Int, val before: Long, val after: Long)

    fun read(context: Context): MemInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val used = (info.totalMem - info.availMem).coerceAtLeast(0)
        val percent = if (info.totalMem > 0) ((used * 100) / info.totalMem).toInt() else 0
        return MemInfo(info.totalMem, info.availMem, used, percent, info.lowMemory)
    }

    fun clean(context: Context): CleanResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val prefs = Prefs(context)
        val before = read(context).available
        val whitelist = prefs.whitelist()
        var killed = 0
        runCatching {
            val pm = context.packageManager
            pm.getInstalledApplications(0).forEach { app ->
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isSystem && !isUpdatedSystem) return@forEach
                if (app.packageName == context.packageName) return@forEach
                if (app.packageName in whitelist) return@forEach
                runCatching {
                    am.killBackgroundProcesses(app.packageName)
                    killed++
                }
            }
        }
        System.gc()
        Thread.sleep(350)
        val after = read(context).available
        val freed = (after - before).coerceAtLeast(0)
        return CleanResult(freed, killed, before, after)
    }
}
