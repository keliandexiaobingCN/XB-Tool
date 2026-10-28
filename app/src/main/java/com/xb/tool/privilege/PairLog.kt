package com.xb.tool.privilege

import android.content.Context
import com.xb.tool.core.LogBus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 无线调试配对日志。
 *
 * 配对发生在 [AdbPairingService] 里，界面上要能看到每一步到底卡在哪里，
 * 所以这里同时写内存日志（LogBus）和落盘日志（filesDir/adb_pair_log.txt），
 * 页面重新打开后仍然能看到上一次失败的原因。
 */
object PairLog {

    private const val FILE = "adb_pair_log.txt"
    private const val MAX_LINES = 300
    private val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    private fun file(context: Context) = File(context.filesDir, FILE)

    @Synchronized
    fun info(context: Context, message: String) {
        LogBus.info(message)
        append(context, "I", message)
    }

    @Synchronized
    fun ok(context: Context, message: String) {
        LogBus.ok(message)
        append(context, "✓", message)
    }

    @Synchronized
    fun warn(context: Context, message: String) {
        LogBus.warn(message)
        append(context, "!", message)
    }

    @Synchronized
    fun error(context: Context, message: String) {
        LogBus.error(message)
        append(context, "×", message)
    }

    private fun append(context: Context, level: String, message: String) {
        runCatching {
            val f = file(context)
            val existing = if (f.isFile) f.readLines() else emptyList()
            val line = "${format.format(Date())} $level $message"
            val next = (existing + line).takeLast(MAX_LINES)
            f.writeText(next.joinToString("\n"))
        }
    }

    fun read(context: Context): List<String> = runCatching {
        val f = file(context)
        if (!f.isFile) emptyList() else f.readLines().takeLast(120)
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        runCatching { file(context).delete() }
        LogBus.info("已清空无线调试配对日志")
    }
}
