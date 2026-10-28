package com.xb.tool.system

import android.app.AppOpsManager
import android.content.Context
import android.os.Build

/**
 * 自启动 / 后台限制的检测渠道。
 *
 * Android 没有公开 API 可以查询「厂商自启动开关」，但确实有几条**真实可查**的通道，
 * 之前一律显示「未开启」是错的。这里按可靠性从高到低逐条尝试：
 *
 * 1. `ActivityManager.isBackgroundRestricted()`（API 28+，公开 API）——
 *    用户在系统里给应用开了「限制后台」时为 true，可直接判定为未放行。
 * 2. AppOps `android:auto_start` —— ColorOS / MIUI 等厂商确实注册了这个 op，
 *    存在且为 MODE_ALLOWED 即可判定为已放行；不存在会抛异常，说明该 ROM 没这条通道。
 * 3. AppOps `android:run_in_background` —— 通用后台放行位。
 * 4. 本应用 BOOT_COMPLETED 接收器是否被 `pm disable` 禁用（能查出被系统禁用的情形）。
 *
 * 全部读不到时返回 [State.UNKNOWN]，由界面如实显示「需手动确认」并给跳转，
 * 不再伪造一个永远「未开启」的状态。
 */
object AutoStartDetector {

    enum class State { ALLOWED, DENIED, UNKNOWN }

    data class Result(
        val state: State,
        val channel: String,
        val detail: String
    )

    fun detect(context: Context): Result {
        val pkg = context.packageName
        val uid = android.os.Process.myUid()

        // 1. 系统级「限制后台」
        if (Build.VERSION.SDK_INT >= 28) {
            val restricted = runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as? android.app.ActivityManager
                am?.isBackgroundRestricted == true
            }.getOrDefault(false)
            if (restricted) {
                return Result(
                    State.DENIED,
                    "系统后台限制",
                    "系统里给本应用开了「限制后台」，开机不会自启"
                )
            }
        }

        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

        // 2. 厂商自启动 op（ColorOS / MIUI 等）
        opsOp(ops, "android:auto_start", uid, pkg)?.let { mode ->
            return when (mode) {
                AppOpsManager.MODE_ALLOWED -> Result(State.ALLOWED, "厂商自启动开关", "系统已允许开机自启动")
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED ->
                    Result(State.DENIED, "厂商自启动开关", "系统未允许开机自启动")
                else -> Result(State.UNKNOWN, "厂商自启动开关", "系统返回默认值，无法确定")
            }
        }

        // 3. 通用后台放行位
        opsOp(ops, "android:run_in_background", uid, pkg)?.let { mode ->
            return when (mode) {
                AppOpsManager.MODE_ALLOWED -> {
                    // 后台放行 + 接收器未被禁用，按「已放行」保守认定
                    if (bootReceiverEnabled(context)) {
                        Result(State.ALLOWED, "后台运行许可", "系统允许后台运行，开机接收器未被禁用")
                    } else {
                        Result(State.DENIED, "开机接收器", "开机接收器被系统禁用")
                    }
                }
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED ->
                    Result(State.DENIED, "后台运行许可", "系统禁止了后台运行")
                else -> Result(State.UNKNOWN, "后台运行许可", "系统返回默认值，无法确定")
            }
        }

        // 4. 兜底：只看开机接收器（能查出 pm disable 的情形）
        return if (bootReceiverEnabled(context)) {
            Result(State.UNKNOWN, "开机接收器", "开机接收器已启用，但厂商自启动开关无法查询")
        } else {
            Result(State.DENIED, "开机接收器", "开机接收器被系统禁用")
        }
    }

    /** 查询一个 AppOps 项；op 不存在或查询失败返回 null（不是「被拒绝」）。 */
    private fun opsOp(ops: AppOpsManager?, op: String, uid: Int, pkg: String): Int? {
        if (ops == null) return null
        return runCatching { ops.checkOpNoThrow(op, uid, pkg) }.getOrNull()
    }

    fun bootReceiverEnabled(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_RECEIVERS
        )
        // 只要有一个未被禁用的接收器，就认为开机广播仍能送达
        info.receivers.orEmpty().any { it.enabled && it.name?.isNotBlank() == true }
    }.getOrDefault(true)

    fun stateLabel(state: State): String = when (state) {
        State.ALLOWED -> "已允许"
        State.DENIED -> "未允许"
        State.UNKNOWN -> "无法查询"
    }
}
