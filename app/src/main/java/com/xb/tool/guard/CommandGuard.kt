package com.xb.tool.guard

import android.content.Context
import com.xb.tool.apps.AppRepository
import com.xb.tool.core.LogBus
import com.xb.tool.core.Prefs
import com.xb.tool.root.Shell
import org.json.JSONArray
import org.json.JSONObject

/**
 * 高危命令防护。
 *
 * 原理：Android 上应用执行 shell 命令时，`sh -c "..."` 子进程会继承调用方的 uid。
 * 因此只要能枚举系统进程表，就能把「某条命令」归因到「某个应用」。
 *
 * 普通应用受 hidepid 限制只能看到自己的进程，所以这里必须走特权通道
 * （Root / Shizuku / 无线调试），这也是「Shell 模式」依赖 Shizuku 级权限的原因。
 */
object CommandGuard {

    enum class Risk(val label: String, val order: Int) {
        CRITICAL("极高风险", 3),
        HIGH("高风险", 2),
        MEDIUM("中高风险", 1)
    }

    data class Rule(
        val id: String,
        val risk: Risk,
        val action: String,
        val pattern: Regex
    )

    data class Alert(
        val id: Long,
        val time: Long,
        val packageName: String,
        val appLabel: String,
        val risk: Risk,
        val command: String,
        val action: String,
        val source: String = "命令监控"
    )

    /**
     * 规则表。只匹配「明确具有破坏/窃取/绕过能力」的命令，
     * 避免把正常的 pm list、getprop 之类误判。
     */
    private val RULES: List<Rule> = listOf(
        Rule(
            "wipe", Risk.CRITICAL, "清除数据 / 恢复出厂",
            Regex("""\b(pm\s+clear|am\s+broadcast.*MASTER_CLEAR|recovery\s+--wipe)""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "uninstall", Risk.HIGH, "静默卸载应用",
            Regex("""\bpm\s+(uninstall|uninstall-system-updates)\b""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "disable", Risk.HIGH, "禁用系统组件",
            Regex("""\b(pm\s+disable|pm\s+disable-user|pm\s+suspend)\b""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "rm_rf", Risk.CRITICAL, "删除系统或数据目录",
            Regex("""\brm\s+(-[a-zA-Z]*\s+)*-?[rR][fF]?\s+/(system|data|vendor|product|sdcard|storage)\b""")
        ),
        Rule(
            "dd", Risk.CRITICAL, "直接写入块设备",
            Regex("""\bdd\s+.*\bof=/dev/block""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "mount_rw", Risk.HIGH, "重新挂载系统分区为可写",
            Regex("""\bmount\s+.*(-o\s+rw|remount)""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "settings", Risk.MEDIUM, "篡改系统设置",
            Regex("""\bsettings\s+put\s+(global|secure|system)\b""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "appops", Risk.MEDIUM, "修改应用权限策略",
            Regex("""\b(appops\s+set|cmd\s+appops\s+set)\b""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "input", Risk.MEDIUM, "模拟输入（可能用于静默点击）",
            Regex("""\binput\s+(tap|swipe|keyevent|text)\b""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "screen", Risk.HIGH, "截屏 / 录屏",
            Regex("""\b(screencap|screenrecord)\b""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "power", Risk.HIGH, "强制重启 / 关机",
            Regex("""\b(reboot|svc\s+power\s+(off|reboot))\b""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "su", Risk.CRITICAL, "尝试提权执行",
            Regex("""(^|[\s;|&])su(\s|$|-c)""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "pipe_shell", Risk.CRITICAL, "下载内容并直接执行",
            Regex("""(curl|wget)[^|]*\|\s*(sh|bash|toybox\s+sh)""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "base64_shell", Risk.HIGH, "解码后执行隐藏脚本",
            Regex("""base64\s+-d[^|]*\|\s*(sh|bash)""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "chmod_root", Risk.HIGH, "给系统目录提权",
            Regex("""\bchmod\s+(-R\s+)?[0-7]?777\s+/(system|data|vendor)""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "keylogger", Risk.HIGH, "读取输入设备（可能记录按键）",
            Regex("""\b(cat|dd)\s+/dev/input/event""", RegexOption.IGNORE_CASE)
        ),
        // ▼ 窃密类：短信 / 通讯录 / 通话记录被 content 命令成批导出
        Rule(
            "steal_sms", Risk.CRITICAL, "读取短信数据库（窃取验证码）",
            Regex(
                """content\s+(query|read)\s+.*content://(sms|mms|sms-sent|sms-inbox|sms-draft|mms-sms)""",
                RegexOption.IGNORE_CASE
            )
        ),
        Rule(
            "steal_contacts", Risk.HIGH, "读取通讯录 / 通话记录",
            Regex(
                """content\s+(query|read)\s+.*content://(contacts|com.android.contacts|call_log|com.android.calllog)""",
                RegexOption.IGNORE_CASE
            )
        ),
        Rule(
            "steal_clipboard", Risk.HIGH, "读取剪贴板内容",
            Regex("""\b(cmd\s+clipboard|service\s+call\s+clipboard)""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "steal_appdata", Risk.HIGH, "打包应用私有数据",
            Regex(
                """\b(tar|cp|rsync|dd)\b[^\n]*\s/data/(data|user_de|[a-z]+/)""",
                RegexOption.IGNORE_CASE
            )
        ),
        // ▼ 远控 / 绕过类：静默改权限、静默开无障碍、静默打开设备管理
        Rule(
            "accessibility", Risk.CRITICAL, "静默开启无障碍服务（远控常用手法）",
            Regex(
                """settings\s+put\s+secure\s+enabled_accessibility_services""",
                RegexOption.IGNORE_CASE
            )
        ),
        Rule(
            "pm_grant", Risk.HIGH, "静默授予危险权限",
            Regex("""\bpm\s+grant\s+\S+\s+android\.permission\.""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "device_admin", Risk.CRITICAL, "静默激活设备管理器 / 设备策略",
            Regex(
                """\b(dpm\s+set-active-admin|cmd\s+device_policy\s+set-active-admin)""",
                RegexOption.IGNORE_CASE
            )
        ),
        Rule(
            "default_assist", Risk.HIGH, "替换默认桌面 / 助手（可劫持界面）",
            Regex(
                """cmd\s+package\s+set-home-activity|settings\s+put\s+secure\s+(default_assist|assistant)""",
                RegexOption.IGNORE_CASE
            )
        ),
        Rule(
            "wifi_adb", Risk.HIGH, "开启无线调试 / ADB 端口",
            Regex(
                """(setprop\s+service\.adb\.tcp\.port|settings\s+put\s+global\s+adb_wifi_enabled|adbd\s+--?wifi)""",
                RegexOption.IGNORE_CASE
            )
        ),
        Rule(
            "overlay_app", Risk.HIGH, "安装 / 更新可悬浮应用包",
            Regex("""\bpm\s+(install|install-existing|install-multi-package)\b""", RegexOption.IGNORE_CASE)
        )
    )

    /** 系统自身与厂商进程不做归因，避免误报。 */
    private val IGNORED_PACKAGES = setOf(
        "android", "com.android.shell", "com.android.systemui"
    )

    /**
     * 采集一次进程表并返回命中规则的告警。
     *
     * @param exec 特权命令执行器（Root / Shizuku / 无线调试）
     */
    fun poll(context: Context, exec: (String) -> Shell.Result): List<Alert> {
        val result = exec("ps -A -o PID,UID,ARGS 2>/dev/null || ps -A -o PID,USER,ARGS")
        return pollWith(context, result)
    }

    /** 用已经取回的进程表做一次分析（GuardService 复用同一次采样）。 */
    fun pollWith(context: Context, result: Shell.Result): List<Alert> {
        val prefs = Prefs(context)
        val whitelist = prefs.commandWhitelist()
        if (result.out.isBlank()) return emptyList()

        val uidToPackage = uidIndex(context)
        val rows = parse(result.out)
        val alerts = mutableListOf<Alert>()
        val now = System.currentTimeMillis()

        for (row in rows) {
            val args = row.args
            if (args.isBlank()) continue
            // 只看真正在跑命令的进程，忽略内核线程（[xxx]）
            if (args.startsWith("[")) continue
            val rule = RULES.firstOrNull { it.pattern.containsMatchIn(args) } ?: continue

            val packageName = resolvePackage(row, uidToPackage) ?: continue
            if (packageName == context.packageName) continue
            if (packageName in IGNORED_PACKAGES) continue
            if (packageName in whitelist) continue

            val label = runCatching { AppRepository.labelOf(context, packageName) }
                .getOrDefault(packageName)
            alerts += Alert(
                id = now + alerts.size,
                time = now,
                packageName = packageName,
                appLabel = label,
                risk = rule.risk,
                command = args.take(400),
                action = rule.action
            )
            LogBus.warn("高危命令：${label}（${rule.risk.label}）· ${rule.action} · ${args.take(120)}")
        }
        return alerts
    }

    private data class Row(val pid: Int, val uid: Int, val args: String)

    private fun parse(text: String): List<Row> {
        val rows = mutableListOf<Row>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("PID")) continue
            val parts = trimmed.split(Regex("\\s+"), limit = 3)
            if (parts.size < 3) continue
            val pid = parts[0].toIntOrNull() ?: continue
            val uid = parts[1].toIntOrNull() ?: continue
            rows += Row(pid, uid, parts[2].trim())
        }
        return rows
    }

    /** uid → 包名（一个 uid 可能对应多个包，取第一个非系统包）。 */
    private fun uidIndex(context: Context): Map<Int, String> {
        val pm = context.packageManager
        val map = mutableMapOf<Int, String>()
        runCatching {
            pm.getInstalledApplications(0).forEach { info ->
                val uid = info.uid
                val existing = map[uid]
                if (existing == null || (info.packageName.length < existing.length)) {
                    map[uid] = info.packageName
                }
            }
        }
        return map
    }

    /**
     * 归因：进程自身 uid 属于某个应用时直接用；
     * 否则（例如 su / sh 由 root 或 shell 派生）尝试从命令行里的包名反查。
     */
    private fun resolvePackage(row: Row, uidToPackage: Map<Int, String>): String? {
        uidToPackage[row.uid]?.let { return it }
        // 命令行里常带包名（pm uninstall com.xxx / am force-stop com.xxx）
        val token = row.args.split(Regex("\\s+"))
            .lastOrNull { it.contains('.') && !it.startsWith("-") && !it.startsWith("/") }
        if (token != null && uidToPackage.containsValue(token)) return token
        return null
    }

    // ------------------------------------------------------------ 告警存储

    fun loadAlerts(context: Context): List<Alert> {
        val raw = Prefs(context).commandAlertsJson ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Alert(
                    id = o.optLong("id"),
                    time = o.optLong("time"),
                    packageName = o.optString("pkg"),
                    appLabel = o.optString("label"),
                    risk = runCatching { Risk.valueOf(o.optString("risk")) }.getOrDefault(Risk.MEDIUM),
                    command = o.optString("cmd"),
                    action = o.optString("action"),
                    source = o.optString("source").ifBlank { "命令监控" }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveAlerts(context: Context, alerts: List<Alert>) {
        val array = JSONArray()
        alerts.take(80).forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("time", it.time)
                    put("pkg", it.packageName)
                    put("label", it.appLabel)
                    put("risk", it.risk.name)
                    put("cmd", it.command)
                    put("action", it.action)
                    put("source", it.source)
                }
            )
        }
        Prefs(context).commandAlertsJson = array.toString()
    }

    fun appendAlerts(context: Context, fresh: List<Alert>): List<Alert> {
        if (fresh.isEmpty()) return loadAlerts(context)
        val existing = loadAlerts(context)
        val merged = (fresh + existing).distinctBy { "${it.packageName}|${it.command}" }.take(80)
        saveAlerts(context, merged)
        return merged
    }

    fun clearAlerts(context: Context) {
        Prefs(context).commandAlertsJson = "[]"
    }

    fun riskName(risk: Risk): String = risk.label

    fun ruleCount(): Int = RULES.size

    /** 规则说明，用于界面展示监控范围。 */
    fun describe(): String = RULES.joinToString("、") { it.action }
}
