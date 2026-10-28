package com.xb.tool.guard

import android.content.Context
import java.io.File

/**
 * 跨进程心跳。
 *
 * SharedPreferences 在每个进程里各有一份内存缓存，`:watchdog` 进程写入的心跳
 * 主进程读不到（会一直拿到旧值）。这里改用文件时间戳，两个进程都能立即看到。
 */
object Heartbeat {
    const val GUARD = "guard"
    const val WATCHDOG = "watchdog"

    private fun file(context: Context, name: String) = File(context.filesDir, "hb_$name")

    fun beat(context: Context, name: String) {
        runCatching { file(context, name).writeText(System.currentTimeMillis().toString()) }
    }

    /** 距离上次心跳的毫秒数；从未写过返回 Long.MAX_VALUE。 */
    fun age(context: Context, name: String): Long {
        val f = file(context, name)
        if (!f.exists()) return Long.MAX_VALUE
        val at = f.lastModified()
        if (at <= 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - at
    }

    fun clear(context: Context, name: String) {
        runCatching { file(context, name).delete() }
    }
}
