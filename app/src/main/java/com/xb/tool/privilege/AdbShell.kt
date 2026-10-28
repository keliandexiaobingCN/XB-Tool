package com.xb.tool.privilege

import android.content.Context
import com.xb.tool.root.Shell
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * 通过「无线调试」拿到的 ADB shell 通道。
 *
 * 权限级别与桌面 adb shell 相同（uid 2000 shell），能执行 pm / am / dumpsys / settings /
 * 读取 /data/app 等，但不等同于 Root。对免 Root 用户来说，这是本应用唯一合法的高权限通道。
 *
 * 与 [WirelessAdb] 的分工：
 *  - WirelessAdb 负责配对、发现、密钥；
 *  - AdbShell 负责保持一条长连接，并把命令结果包装成 [Shell.Result]。
 */
object AdbShell {

    private const val PREFS = "xb_wireless_adb"
    private const val KEY_HOST = "shell_host"
    private const val KEY_PORT = "shell_port"

    @Volatile private var connection: AdbConnection? = null
    @Volatile private var connectedAt: Long = 0L
    @Volatile private var lastError: String = ""

    private val lock = Any()

    fun isConnected(): Boolean = connection?.isConnected == true

    fun connectedAt(): Long = connectedAt

    fun lastError(): String = lastError

    fun savedHost(context: Context): String =
        prefs(context).getString(KEY_HOST, "") ?: ""

    fun savedPort(context: Context): Int = prefs(context).getInt(KEY_PORT, 0)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 建立（或复用）连接。host/port 为空时使用上次成功的地址。
     */
    fun connect(context: Context, host: String = "", port: Int = 0, timeoutMs: Long = 20_000L): Boolean =
        synchronized(lock) {
            if (isConnected()) return true
            val useHost = host.ifBlank { savedHost(context) }
            val usePort = if (port != 0) port else savedPort(context)
            if (useHost.isBlank() || usePort == 0) {
                lastError = "尚未配对，请先完成无线调试配对"
                return false
            }
            runCatching {
                val (priv, cert) = WirelessAdb.loadKeyPair(context)
                val conn = AdbConnection.create(useHost, usePort, priv, cert, android.os.Build.VERSION.SDK_INT)
                conn.setDeviceName(WirelessAdb.deviceName(context))
                val ok = conn.connect(timeoutMs, TimeUnit.MILLISECONDS, true)
                if (!ok) {
                    runCatching { conn.close() }
                    lastError = "连接超时（可能设备未授权本机密钥）"
                    return false
                }
                connection = conn
                connectedAt = System.currentTimeMillis()
                lastError = ""
                prefs(context).edit().putString(KEY_HOST, useHost).putInt(KEY_PORT, usePort).apply()
                true
            }.getOrElse { t ->
                lastError = t.message ?: t.javaClass.simpleName
                false
            }
        }

    fun disconnect() {
        synchronized(lock) {
            runCatching { connection?.close() }
            connection = null
            connectedAt = 0L
        }
    }

    /**
     * 执行一条 shell 命令。返回的 [Shell.Result] 与 root 通道保持同一形状，便于上层无感替换。
     */
    fun exec(command: String, timeoutMs: Long = 20_000L): Shell.Result {
        if (!isConnected()) {
            return Shell.Result(-1, "", lastError.ifBlank { "无线调试未连接" })
        }
        val conn = connection ?: return Shell.Result(-1, "", "无线调试未连接")
        return runCatching {
            // 注意：不要用 conn.open(LocalServices.SHELL, cmd)。libadb 的 getDestination 会在参数
            // 含空格时给它整体加双引号，adbd 再交给 sh -c 时整条命令会变成一个"文件名"而报
            // inaccessible or not found。直接拼 "shell:" 前缀可绕开这层转义。
            val wrapped = "${command.trim()}; echo __XB_EXIT:\$?"
            val stream = conn.open("shell:$wrapped")
            val raw = readAll(stream, timeoutMs)
            val exitLine = raw.lineSequence().lastOrNull { it.startsWith("__XB_EXIT:") }
            val code = exitLine?.removePrefix("__XB_EXIT:")?.trim()?.toIntOrNull() ?: 0
            val body = raw.lineSequence()
                .filterNot { it.startsWith("__XB_EXIT:") }
                .joinToString("\n")
                .trim()
            Shell.Result(code, body, "")
        }.getOrElse { t ->
            if (!isConnected()) connection = null
            Shell.Result(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * 读取一个 shell 流的全部输出。
     *
     * adbd 的 inline shell 流不保证在命令结束后立刻发 CLSE，直接阻塞 read 会一直挂住，
     * 所以这里用独立读线程 + 超时兜底：超时就关流，把已经读到的内容返回。
     */
    private fun readAll(stream: AdbStream, timeoutMs: Long): String {
        val buffer = ByteArrayOutputStream()
        val finished = java.util.concurrent.CountDownLatch(1)
        val reader = Thread {
            val chunk = ByteArray(8192)
            try {
                val input: InputStream = stream.openInputStream()
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    synchronized(buffer) { buffer.write(chunk, 0, read) }
                    if (buffer.size() > 1024 * 1024) break
                }
            } catch (_: Throwable) {
                // 流关闭属正常结束
            } finally {
                finished.countDown()
            }
        }
        reader.isDaemon = true
        reader.start()
        val done = finished.await(timeoutMs, TimeUnit.MILLISECONDS)
        runCatching { stream.close() }
        if (!done) reader.interrupt()
        val text = synchronized(buffer) { buffer.toString("UTF-8") }
        return if (!done && text.isBlank()) {
            throw java.io.IOException("读取超时（${timeoutMs}ms）")
        } else {
            text
        }
    }
}
