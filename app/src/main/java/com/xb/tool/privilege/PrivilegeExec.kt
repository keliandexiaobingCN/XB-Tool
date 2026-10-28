package com.xb.tool.privilege

import android.content.Context
import com.xb.tool.root.ShizukuUtils
import com.xb.tool.root.Shell

/**
 * 统一特权通道：Root → Shizuku → 无线调试。
 *
 * 与 AppState 里的私有实现不同，这里可以被 Service / 后台线程直接调用，
 * 用来做周期性的进程表采集（高危命令监控）。
 *
 * 注意：探测 root 需要 fork `su`，代价很高。这里缓存探测结果，
 * 避免后台轮询每几秒就 fork 一次 su 进程。
 */
object PrivilegeExec {

    private const val PROBE_TTL = 60_000L
    private var rootCheckedAt = 0L
    private var rootAvailable = false

    /** 当前可用的最高通道名称，用于界面展示。 */
    fun channel(): String = when {
        hasRoot() -> "Root"
        ShizukuUtils.isServiceReady() -> "Shizuku"
        AdbShell.isConnected() -> "无线调试"
        else -> "无"
    }

    fun hasPrivilege(): Boolean =
        hasRoot() || ShizukuUtils.isServiceReady() || AdbShell.isConnected()

    private fun hasRoot(): Boolean {
        val now = System.currentTimeMillis()
        if (now - rootCheckedAt < PROBE_TTL) return rootAvailable
        val ok = runCatching {
            Shell.exec("id", 3_000L, asRoot = true).out.contains("uid=0")
        }.getOrDefault(false)
        rootCheckedAt = now
        rootAvailable = ok
        return ok
    }

    /** 特权通道变化后调用，强制下次重新探测。 */
    fun invalidate() {
        rootCheckedAt = 0L
        rootAvailable = false
    }

    /**
     * 执行命令。会依次尝试 Root、Shizuku、无线调试，返回第一个成功的结果。
     */
    fun exec(context: Context, command: String, timeoutMs: Long = 20_000L): Shell.Result {
        if (hasRoot()) {
            val root = runCatching { Shell.exec(command, timeoutMs, asRoot = true) }.getOrNull()
            if (root != null && root.success) return root
        }

        if (ShizukuUtils.isServiceReady()) {
            val shizuku = runCatching { ShizukuUtils.exec(command) }.getOrNull()
            if (shizuku != null && shizuku.success && shizuku.out.isNotBlank()) return shizuku
        } else if (ShizukuUtils.isGranted()) {
            // 服务未绑定则异步绑定，本次先走其他通道
            runCatching { ShizukuUtils.bind(context) {} }
        }

        if (AdbShell.isConnected()) {
            val adb = runCatching { AdbShell.exec(command, timeoutMs) }.getOrNull()
            if (adb != null && adb.success) return adb
        }
        return Shell.Result(-1, "", "无特权通道")
    }
}
