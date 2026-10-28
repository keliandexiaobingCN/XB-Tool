package com.xb.tool.guard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.xb.tool.core.InterceptRecord
import com.xb.tool.core.InterceptStore
import com.xb.tool.core.LogBus
import com.xb.tool.core.Threat
import com.xb.tool.privilege.PrivilegeExec
import com.xb.tool.scan.QuarantineStore
import com.xb.tool.util.FileUtils
import java.io.File

/**
 * 威胁处置动作：卸载、隔离、管控（冻结）。
 *
 * 这三种动作对权限的要求不同，这里统一封装成「尽力而为 + 明确告知结果」：
 *  - 有 Root / Shizuku / 无线调试时走特权通道静默执行；
 *  - 没有特权时退回系统界面（应用详情页），让用户手动确认。
 *
 * Android 本身不允许普通应用静默卸载或静默冻结其他应用，所以界面文案必须
 * 如实说明「需要手动确认」，不能假装一键搞定。
 */
object ThreatActions {

    data class Result(val ok: Boolean, val message: String)

    /** 卸载应用：优先静默卸载，失败则打开系统应用详情页。 */
    fun uninstall(context: Context, packageName: String, label: String): Result {
        if (packageName.isBlank()) return Result(false, "没有关联包名，无法卸载")
        if (PrivilegeExec.hasPrivilege()) {
            val r = runCatching {
                PrivilegeExec.exec(context, "pm uninstall --user 0 $packageName", 60_000L)
            }.getOrNull()
            val text = r?.text.orEmpty()
            val ok = r != null && r.success && !text.contains("Failure", true) &&
                !text.contains("Exception", true)
            if (ok) {
                LogBus.ok("已卸载 $label（$packageName）")
                record(context, "已卸载", packageName, label)
                return Result(true, "已卸载 $label")
            }
            LogBus.warn("静默卸载失败：${text.ifBlank { "未知原因" }}")
        }
        openAppDetails(context, packageName)
        return Result(false, "已打开系统应用详情页，请手动确认卸载")
    }

    /** 冻结应用（pm disable-user），可用于「管控」有风险但暂不想卸载的应用。 */
    fun freeze(context: Context, packageName: String, label: String): Result {
        if (packageName.isBlank()) return Result(false, "没有关联包名，无法管控")
        if (!PrivilegeExec.hasPrivilege()) {
            return Result(false, "管控需要 Root / Shizuku / 无线调试权限")
        }
        val r = runCatching {
            PrivilegeExec.exec(context, "pm disable-user --user 0 $packageName", 40_000L)
        }.getOrNull()
        val text = r?.text.orEmpty()
        val ok = r != null && r.success && !text.contains("Exception", true) &&
            !text.contains("Error", true)
        if (ok) {
            LogBus.ok("已管控（冻结）$label（$packageName）")
            record(context, "已管控", packageName, label)
            return Result(true, "已管控 $label，可在应用冻结页解冻")
        }
        LogBus.error("管控失败：${text.ifBlank { "无权限" }}")
        return Result(false, "管控失败：${text.ifBlank { "请确认特权通道可用" }}")
    }

    /**
     * 隔离文件：优先直接搬移，失败再走特权通道复制后删除。
     * 隔离成功后文件被移到应用私有目录，其他应用无法再执行它。
     */
    fun quarantine(context: Context, threat: Threat): Result {
        val source = File(threat.path)
        val dir = File(context.filesDir, "quarantine").apply { mkdirs() }
        val dest = File(dir, "${System.currentTimeMillis()}_${source.name}")

        var ok = runCatching {
            source.copyTo(dest, overwrite = true)
            source.delete()
            dest.isFile && dest.length() > 0 && !source.exists()
        }.getOrDefault(false)

        if (!ok) {
            val cmd = "cp ${FileUtils.shellQuote(source.absolutePath)} ${FileUtils.shellQuote(dest.absolutePath)} && " +
                "chmod 644 ${FileUtils.shellQuote(dest.absolutePath)} && rm -f ${FileUtils.shellQuote(source.absolutePath)}"
            val r = runCatching { PrivilegeExec.exec(context, cmd, 60_000L) }.getOrNull()
            ok = r != null && dest.isFile && dest.length() > 0 && !source.exists()
        }

        return if (ok) {
            runCatching { QuarantineStore.recordMoved(context, threat, dest.absolutePath) }
            record(context, "已隔离", threat.packageName ?: "", threat.name)
            LogBus.ok("已隔离：${threat.name}")
            Result(true, "已隔离 ${threat.name}")
        } else {
            LogBus.error("隔离失败：${threat.name}")
            Result(false, "隔离失败，需要 Root / Shizuku 权限才能移动该文件")
        }
    }

    /** 直接删除威胁文件。 */
    fun delete(context: Context, threat: Threat): Result {
        val file = File(threat.path)
        var ok = runCatching { file.delete() }.getOrDefault(false)
        if (!ok) {
            val r = runCatching {
                PrivilegeExec.exec(context, "rm -f ${FileUtils.shellQuote(threat.path)}", 40_000L)
            }.getOrNull()
            ok = r != null && !file.exists()
        }
        return if (ok) {
            record(context, "已删除", threat.packageName ?: "", threat.name)
            LogBus.ok("已删除：${threat.name}")
            Result(true, "已删除 ${threat.name}")
        } else {
            Result(false, "删除失败，需要 Root / Shizuku 权限")
        }
    }

    fun openAppDetails(context: Context, packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { LogBus.warn("打开应用详情页失败：${it.message}") }
    }

    private fun record(context: Context, action: String, packageName: String, name: String) {
        runCatching {
            InterceptStore.add(
                context,
                InterceptRecord(
                    id = System.currentTimeMillis(),
                    time = System.currentTimeMillis(),
                    name = name,
                    path = packageName.ifBlank { name },
                    action = action,
                    signature = action
                )
            )
        }
    }
}
