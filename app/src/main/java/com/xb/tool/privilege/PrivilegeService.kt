package com.xb.tool.privilege

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.xb.tool.root.ShizukuUtils
import com.xb.tool.root.Shell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 特权服务：把 Root / Shizuku / 无线调试 三条通道统一成一个「服务」概念，
 * 对外暴露：怎么跑的、能不能重启、当前 PID、启动耗时、启动步骤与日志。
 *
 * 说明（不夸大）：
 *  - Root 通道：直接 fork `su -c`，UID 0。
 *  - Shizuku 通道：走已安装的 Shizuku 用户服务，UID 2000（shell）。
 *  - 无线调试通道：本应用自己维护一条 ADB TLS 长连接，等价于 adb shell，UID 2000。
 * 三条通道的能力不同，界面必须如实标注，不能假装成 root。
 */
object PrivilegeService {

    private const val SP = "xb_privilege_service"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_MODE = "mode"
    private const val KEY_LAST_ERROR = "last_error"

    enum class Mode(val label: String) {
        WIRELESS("无线调试"),
        WIRED("有线 ADB"),
        ROOT("Root")
    }

    enum class StepState { PENDING, RUNNING, OK, FAIL }

    data class Step(
        val title: String,
        val state: StepState = StepState.PENDING,
        val detail: String = ""
    )

    data class Status(
        val running: Boolean,
        val channel: String,
        val uid: String,
        val startedAt: Long,
        val mode: String,
        val pid: Int,
        val lastError: String
    )

    /** 一条「ADB 服务能不能完整跑起来」的检查结果。 */
    data class Limit(
        val name: String,
        val ok: Boolean,
        val detail: String
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    /** 当前特权服务状态。 */
    fun status(context: Context): Status {
        val sp = prefs(context)
        val running = PrivilegeExec.hasPrivilege()
        val channel = PrivilegeExec.channel()
        val uid = when (channel) {
            "Root" -> "0 (root)"
            "Shizuku", "无线调试" -> "2000 (shell)"
            else -> "—"
        }
        return Status(
            running = running,
            channel = channel,
            uid = uid,
            startedAt = sp.getLong(KEY_STARTED_AT, 0L),
            mode = sp.getString(KEY_MODE, "") ?: "",
            pid = android.os.Process.myPid(),
            lastError = sp.getString(KEY_LAST_ERROR, "") ?: ""
        )
    }

    fun rememberStarted(context: Context, mode: Mode) {
        prefs(context).edit()
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .putString(KEY_MODE, mode.label)
            .putString(KEY_LAST_ERROR, "")
            .apply()
    }

    fun rememberError(context: Context, message: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message).apply()
    }

    /** 停止：断开无线调试长连接并让 Shizuku 用户服务解绑。 */
    fun stop(context: Context) {
        runCatching { AdbShell.disconnect() }
        runCatching { ShizukuUtils.reset() }
        prefs(context).edit().remove(KEY_STARTED_AT).apply()
        PairLog.info(context, "ADB 增强服务已停止")
    }

    /** 重启：停掉再按上次的方式重新启动。 */
    suspend fun restart(context: Context, report: (List<Step>) -> Unit): Boolean {
        val last = prefs(context).getString(KEY_MODE, "") ?: ""
        val mode = Mode.entries.firstOrNull { it.label == last } ?: Mode.WIRELESS
        stop(context)
        return start(context, mode, report)
    }

    /**
     * 启动特权服务。逐步回调步骤状态，界面据此渲染时间线。
     *
     * 步骤顺序（对应用户要求的启动流程）：
     *   准备启动环境 → 检测开发者选项 → 检测所选通道 → 建立/验证 ADB 服务
     *   → 检测系统限制 → 启动特权 → 启动完成
     *
     * @return true 表示至少有一条通道可用。
     */
    suspend fun start(context: Context, mode: Mode, report: (List<Step>) -> Unit): Boolean {
        val label = mode.label
        val steps = mutableListOf(
            Step("准备启动环境"),
            Step("检测开发者选项"),
            Step("检测$label"),
            Step("建立 / 验证 ADB 服务"),
            Step("检测系统限制"),
            Step("启动 ADB 增强服务"),
            Step("启动完成")
        )
        fun publish() = report(steps.map { it.copy() })
        fun set(index: Int, state: StepState, detail: String = "") {
            steps[index] = steps[index].copy(state = state, detail = detail)
            publish()
        }

        PairLog.info(context, "=== ADB 增强服务启动 ===")
        PairLog.info(
            context,
            "启动方式：$label · 机型 ${Build.MANUFACTURER} ${Build.MODEL} · " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
        publish()
        kotlinx.coroutines.delay(120)

        // 1 准备启动环境
        set(0, StepState.RUNNING)
        kotlinx.coroutines.delay(180)
        set(0, StepState.OK, "Android ${Build.VERSION.SDK_INT} · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")

        // 2 检测开发者选项（Root 通道不依赖它，但仍然如实展示）
        set(1, StepState.RUNNING)
        kotlinx.coroutines.delay(180)
        val devOk = developerOptionsEnabled(context)
        if (devOk) {
            set(1, StepState.OK, "开发者选项已开启")
            PairLog.ok(context, "开发者选项已开启")
        } else if (mode == Mode.ROOT) {
            set(1, StepState.OK, "未开启，但 Root 通道不需要它")
            PairLog.warn(context, "开发者选项未开启（Root 通道不受影响）")
        } else {
            val msg = "开发者选项未开启：点上方「前往开发者选项」打开后再回来重启"
            set(1, StepState.FAIL, msg)
            PairLog.error(context, msg)
            rememberError(context, msg)
            return false
        }

        // 3 检测所选通道是否具备条件
        set(2, StepState.RUNNING)
        kotlinx.coroutines.delay(180)
        when (mode) {
            Mode.WIRELESS -> {
                val port = WirelessAdb.tlsPort()
                val connected = AdbShell.isConnected()
                if (connected) {
                    set(2, StepState.OK, "无线调试通道已连接")
                    PairLog.ok(context, "无线调试通道已连接")
                } else if (port <= 0) {
                    val msg = "无线调试未开启：请到系统设置的开发者选项里打开「无线调试」"
                    set(2, StepState.FAIL, msg)
                    PairLog.error(context, msg)
                    rememberError(context, msg)
                    return false
                } else {
                    set(2, StepState.OK, "已开启 · TLS 端口 $port")
                    PairLog.info(context, "无线调试已开启 · TLS 端口 $port")
                }
            }

            Mode.WIRED -> {
                set(2, StepState.OK, "检测本机 5555 端口")
                PairLog.info(context, "有线模式：准备连接本机 5555（需先在电脑执行 adb tcpip 5555）")
            }

            Mode.ROOT -> {
                val rooted = runCatching { Shell.rootAvailable() }.getOrDefault(false)
                if (rooted) {
                    set(2, StepState.OK, "su 可用")
                    PairLog.ok(context, "su 可用")
                } else {
                    val msg = "未获得 Root：请先在 Root 管理器里给本应用授权"
                    set(2, StepState.FAIL, msg)
                    PairLog.error(context, msg)
                    rememberError(context, msg)
                    return false
                }
            }
        }

        // 4 建立 / 验证 ADB 服务
        set(3, StepState.RUNNING)
        val ok = when (mode) {
            Mode.ROOT -> true
            Mode.WIRED -> {
                if (AdbShell.isConnected()) {
                    PairLog.ok(context, "已有可用 ADB 通道")
                    set(3, StepState.OK, "复用已连接的通道")
                    true
                } else {
                    PairLog.info(context, "尝试连接本机 5555（需要先在电脑执行 adb tcpip 5555）")
                    val connected = AdbShell.connect(context, "127.0.0.1", 5555)
                    if (connected) {
                        PairLog.ok(context, "有线（tcpip）通道已连接 127.0.0.1:5555")
                        set(3, StepState.OK, "127.0.0.1:5555")
                    } else {
                        val msg = "连不上本机 5555：请先在电脑上执行 adb tcpip 5555，" +
                            "并确保手机与电脑已用 USB 连接过一次"
                        PairLog.error(context, msg)
                        set(3, StepState.FAIL, msg)
                    }
                    connected
                }
            }

            Mode.WIRELESS -> {
                if (AdbShell.isConnected()) {
                    PairLog.ok(context, "无线调试通道已连接")
                    set(3, StepState.OK, "复用已连接的 ADB 通道")
                    true
                } else {
                    val host = AdbShell.savedHost(context)
                    val port = AdbShell.savedPort(context)
                    if (host.isBlank() || port == 0) {
                        val msg = "还没有配对：直接在这一步下面填 6 位配对码完成配对"
                        PairLog.error(context, msg)
                        set(3, StepState.FAIL, msg)
                        false
                    } else {
                        PairLog.info(context, "连接已保存的地址 $host:$port")
                        val connected = AdbShell.connect(context, host, port)
                        if (connected) {
                            PairLog.ok(context, "无线调试通道已连接 $host:$port")
                            set(3, StepState.OK, "$host:$port")
                        } else {
                            val msg = AdbShell.lastError().ifBlank { "连接失败" }
                            PairLog.error(context, "连接失败：$msg")
                            set(3, StepState.FAIL, msg)
                        }
                        connected
                    }
                }
            }
        }

        if (!ok) {
            rememberError(context, steps[3].detail)
            return false
        }

        // 身份验证：确认这条通道真的能执行命令
        val identity = runCatching { PrivilegeExec.exec(context, "id", 6_000L) }.getOrNull()
        val idText = identity?.out?.trim()?.lineSequence()?.firstOrNull().orEmpty()
        if (identity == null || !identity.success || idText.isBlank()) {
            val msg = "通道建立了，但执行 id 失败"
            PairLog.warn(context, msg)
            set(3, StepState.FAIL, msg)
            rememberError(context, msg)
            return false
        }
        PairLog.ok(context, "身份验证：$idText")
        set(3, StepState.OK, "${steps[3].detail.ifBlank { "已建立" }} · $idText")

        // 5 检测系统限制（ADB 能不能完整跑）
        set(4, StepState.RUNNING)
        kotlinx.coroutines.delay(200)
        val limits = adbCapabilities(context)
        val blocking = limits.filter { !it.ok }
        if (blocking.isEmpty()) {
            PairLog.ok(context, "系统限制检查通过：ADB 能力完整")
            set(4, StepState.OK, "无受限项")
        } else {
            val text = blocking.joinToString("、") { it.name }
            PairLog.warn(context, "系统限制：$text 受限")
            set(4, StepState.OK, "受限：$text")
        }

        // 6 启动特权
        set(5, StepState.RUNNING)
        kotlinx.coroutines.delay(200)
        rememberStarted(context, mode)
        PrivilegeExec.invalidate()
        val pid = android.os.Process.myPid()
        val channel = PrivilegeExec.channel()
        PairLog.ok(context, "ADB 增强服务就绪 · 通道 $channel · 进程 $pid")
        set(5, StepState.OK, "通道 $channel · 进程 $pid")

        // 7 启动完成
        set(6, StepState.RUNNING)
        kotlinx.coroutines.delay(160)
        set(6, StepState.OK, "ADB 增强服务已就绪")
        return true
    }

    /** 开发者选项是否已开启。 */
    fun developerOptionsEnabled(context: Context): Boolean = runCatching {
        android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    }.getOrDefault(false)

    fun openDeveloperSettings(context: Context) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * 打开系统的「无线调试」页面。
     *
     * Android 没有公开的 Action，只能按各 ROM 的组件名逐个试，最后退回开发者选项。
     */
    fun openWirelessDebugging(context: Context): Boolean {
        val components = listOf(
            "com.android.settings/.development.WirelessDebuggingActivity",
            "com.android.settings/.Settings\$WirelessDebuggingActivity",
            "com.android.settings/.development.AdbWirelessActivity",
            "com.android.settings/.subsettings.DevelopmentSettingsDashboardActivity"
        )
        for (raw in components) {
            val parts = raw.split('/')
            if (parts.size != 2) continue
            val intent = android.content.Intent().apply {
                component = android.content.ComponentName(parts[0], parts[1])
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return true
        }
        openDeveloperSettings(context)
        return false
    }

    /**
     * 检测「ADB 服务能否完整运行」。
     *
     * 不同 ROM 的「权限监控」会拦掉 shell 的部分能力（小米的 USB 调试(安全设置)、
     * ColorOS 的权限监控）。这里用几条真实的读写命令做探针，能做到什么就报什么，
     * 不做没有依据的猜测。
     */
    fun adbCapabilities(context: Context): List<Limit> {
        val list = mutableListOf<Limit>()

        fun run(command: String, timeout: Long = 6_000L): Shell.Result =
            runCatching { PrivilegeExec.exec(context, command, timeout) }
                .getOrDefault(Shell.Result(-1, "", "执行异常"))

        val id = run("id")
        list += Limit("终端命令", id.success && id.out.contains("uid="), id.out.trim().ifBlank { id.err })

        val pkg = run("pm list packages -3 2>&1 | head -n 1")
        list += Limit(
            "包管理查询",
            pkg.success && pkg.out.startsWith("package:"),
            pkg.out.trim().ifBlank { pkg.err }.take(60)
        )

        val probeKey = "xb_probe_${System.currentTimeMillis() % 100000}"
        val write = run("settings put global $probeKey 1 2>&1")
        val read = run("settings get global $probeKey 2>&1")
        run("settings delete global $probeKey 2>&1")
        val writeOk = write.success && read.out.trim() == "1"
        list += Limit(
            "安全设置写入",
            writeOk,
            if (writeOk) "可以写入系统设置" else (write.err.ifBlank { read.err }.ifBlank { "被系统权限监控拦截" })
        )

        val appops = run("appops get ${context.packageName} android:run_in_background 2>&1")
        val appopsOk = appops.success && !appops.out.contains("Error", true) &&
            !appops.err.contains("denied", true)
        list += Limit(
            "AppOps 访问",
            appopsOk,
            appops.out.trim().ifBlank { appops.err.trim() }.take(60)
        )
        return list
    }

    /** 针对当前 ROM 的额外提示（不做能力判断，只是告诉用户去哪里关掉限制）。 */
    fun brandHints(context: Context): List<String> {
        val brand = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
        val hints = mutableListOf<String>()
        when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ->
                hints += "小米 / Redmi：开发者选项里要打开「USB 调试（安全设置）」，" +
                    "并在「设置 → 应用设置 → 应用管理 → 权限 → 权限监控」里关掉对系统操作的拦截"
            brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") ->
                hints += "OPPO / 一加 / 真我：设置 → 权限与隐私 → 权限管理 → 权限监控，关掉后再试"
            brand.contains("vivo") || brand.contains("iqoo") ->
                hints += "vivo / iQOO：i 管家 → 应用管理 → 权限管理 → 权限监控，关掉后再试"
            brand.contains("huawei") || brand.contains("honor") ->
                hints += "华为 / 荣耀：「手机管家 → 应用启动管理」里关掉自动管理，避免服务被杀"
            else -> Unit
        }
        hints += "系统限制只会拦掉部分能力（如安全设置写入、权限授予），终端命令与包管理通常不受影响。"
        return hints
    }

    /** 生成游龙 Stellar 风格的启动日志文本。 */
    fun buildLog(context: Context, mode: Mode, command: String, output: String): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        return buildString {
            appendLine("=== XB ADB 增强服务启动日志 ===")
            appendLine("设备信息:")
            appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("应用版本: ${appVersion(context)}")
            appendLine("时间: $time")
            appendLine("执行命令:")
            appendLine(command)
            appendLine("命令输出:")
            append(output.trim())
        }
    }

    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("未知")
}
