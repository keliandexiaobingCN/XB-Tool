package com.xb.tool.privilege

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 授权应用（Shizuku 兼容应用列表）。
 *
 * 说明（不夸大）：Shizuku 的授权状态保存在 **Shizuku 管理器自己** 的存储里，
 * 普通应用既读不到也改不了，只有管理器本身能写入。所以这里做的是：
 *  1. 扫描所有声明了 `moe.shizuku.manager.permission.API_V23` 的应用；
 *  2. 展示包名、版本、是否声明 API、当前是否已安装 Shizuku；
 *  3. 提供「打开 Shizuku」「打开应用」两个动作，把用户送到真正能授权的地方。
 *
 * 本应用自己的特权服务（Root / 无线调试 / Shizuku 通道）不走这套授权，
 * 它是「本应用自己拿到的通道」，用于深度扫描、冻结等，不会替别的应用代理。
 */
object PrivilegeApps {

    const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    data class Entry(
        val packageName: String,
        val label: String,
        val versionName: String,
        val declared: Boolean,
        val installedShizuku: Boolean,
        val isSelf: Boolean
    )

    /** 扫描声明了 Shizuku API 的应用（含本应用）。 */
    fun list(context: Context): List<Entry> {
        val pm = context.packageManager
        val result = mutableListOf<Entry>()
        val installedShizuku = isInstalled(context, SHIZUKU_PACKAGE)

        val packages = runCatching {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            val declared = info.requestedPermissions?.any { it == SHIZUKU_PERMISSION } == true
            // 只列出真的声明了权限的应用；Shizuku 管理器自己也算一个入口
            if (!declared && info.packageName != SHIZUKU_PACKAGE) continue
            val label = runCatching {
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(info.packageName)
            result += Entry(
                packageName = info.packageName,
                label = label,
                versionName = info.versionName ?: "—",
                declared = declared,
                installedShizuku = installedShizuku,
                isSelf = info.packageName == context.packageName
            )
        }
        return result.sortedWith(
            compareByDescending<Entry> { it.isSelf }
                .thenByDescending { it.packageName == SHIZUKU_PACKAGE }
                .thenBy { it.label }
        )
    }

    fun isInstalled(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    /** 打开 Shizuku 管理器（若已安装）。 */
    fun openShizuku(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: return false
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 打开某个应用。 */
    fun openApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** 应用信息页（可卸载/看权限）。 */
    fun openAppInfo(context: Context, packageName: String) {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
