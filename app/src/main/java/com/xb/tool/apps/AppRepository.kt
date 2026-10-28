package com.xb.tool.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val enabled: Boolean
)

object AppRepository {
    fun load(context: Context, includeSystem: Boolean = false): List<InstalledApp> {
        val pm = context.packageManager
        val apps = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        return apps.asSequence()
            .filter { app ->
                if (app.packageName == context.packageName) return@filter false
                val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                includeSystem || !system
            }
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    label = runCatching { pm.getApplicationLabel(app).toString() }
                        .getOrDefault(app.packageName),
                    isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    enabled = runCatching {
                        pm.getApplicationEnabledSetting(app.packageName) !=
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    }.getOrDefault(true)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun isFrozen(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getApplicationEnabledSetting(packageName) ==
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        }.getOrDefault(false)
    }

    fun labelOf(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}
