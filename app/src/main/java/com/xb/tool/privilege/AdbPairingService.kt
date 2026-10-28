package com.xb.tool.privilege

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.xb.tool.MainActivity
import com.xb.tool.R

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 「通知内配对」服务。
 *
 * 为什么需要它：
 *  Android 的「使用配对码配对设备」弹窗一旦离开设置界面就会被销毁，配对端口随之关闭。
 *  也就是说，用户**不可能**边看配对码边切到本应用里输入——切过去端口就没了。
 *  唯一可行的做法是把输入框放进通知：下拉通知栏时弹窗仍在（已实测端口存活），
 *  用户在通知里直接输入 6 位码，本服务在后台完成 mDNS 发现 + SPAKE2 配对 + TLS 连接。
 *
 * 这也是 Shizuku / Stellar 采用的同一套交互。
 */
class AdbPairingService : Service() {

    companion object {
        const val ACTION_START = "com.xb.tool.action.ADB_PAIR_START"
        const val ACTION_PAIR = "com.xb.tool.action.ADB_PAIR_DO"
        const val ACTION_STOP = "com.xb.tool.action.ADB_PAIR_STOP"

        const val KEY_CODE = "xb_pair_code"
        private const val CHANNEL = "xb_adb_pair"
        private const val NOTIF_ID = 1101

        fun start(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AdbPairingService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var discoveryJob: Job? = null

    @Volatile private var foundHost: String = ""
    @Volatile private var foundPort: Int = 0
    @Volatile private var foundName: String = ""
    @Volatile private var busy = false
    /** 通知栏里已经输入、但配对服务还没被发现时先暂存，发现后立即配对。 */
    @Volatile private var pendingCode: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAIR -> {
                val fromRemote = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_CODE)?.toString()
                // 兜底：部分 ROM 的通知回复不会带回 RemoteInput 结果，允许直接带 extra。
                val raw = fromRemote ?: intent.getStringExtra(KEY_CODE) ?: ""
                val code = raw.trim().filter { it.isDigit() }
                if (code.length != 6) {
                    startForegroundSafe(buildNotification("配对码不对", "配对码应为 6 位数字，请重新输入", showInput = true))
                    notifyResult(false, "配对码应为 6 位数字")
                    return START_NOT_STICKY
                }
                // 关键：用户是在系统设置的配对弹窗里看到码就直接填进来的，
                // 此时 mDNS 可能还没扫到配对服务。先把码存下来，扫到立刻配对，
                // 不能让用户「填了码却提示未发现服务，只好再回应用里」。
                if (foundHost.isBlank() || foundPort == 0) {
                    pendingCode = code
                    startForegroundSafe(
                        buildNotification(
                            "已收到配对码",
                            "正在查找配对服务，找到后会自动配对（$code）",
                            showInput = true
                        )
                    )
                    if (discoveryJob?.isActive != true) startDiscovery()
                    return START_STICKY
                }
                startForegroundSafe(buildNotification("正在配对…", "配对码 $code", showInput = false))
                doPair(code)
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                startForegroundSafe(
                    buildNotification(
                        "等待配对码",
                        "打开系统「使用配对码配对设备」弹窗，点下面输入那 6 位码，配对会自动完成",
                        showInput = true
                    )
                )
                startDiscovery()
                return START_STICKY
            }
        }
    }

    // ------------------------------------------------------------ 发现

    private fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        PairLog.info(this, "开始扫描无线调试配对服务（mDNS）")
        discoveryJob = scope.launch {
            var round = 0
            while (isActive) {
                round++
                val found = runCatching { WirelessAdb.discoverPairing(this@AdbPairingService, 4_000L) }
                    .onFailure { PairLog.warn(this@AdbPairingService, "第 $round 轮扫描异常：${it.message}") }
                    .getOrDefault(emptyList())
                if (found.isNotEmpty()) {
                    val target = found.first()
                    val changed = target.host != foundHost || target.port != foundPort
                    foundHost = target.host
                    foundPort = target.port
                    foundName = target.name
                    if (changed) {
                        PairLog.ok(
                            this@AdbPairingService,
                            "发现配对服务 ${target.name} · ${target.host}:${target.port}"
                        )
                    }
                    val queued = pendingCode
                    if (queued.length == 6 && !busy) {
                        pendingCode = ""
                        updateNotification(
                            "已发现配对服务，正在配对…",
                            "${target.host}:${target.port} · 配对码 $queued",
                            showInput = false
                        )
                        doPair(queued)
                        delay(6_000L)
                        continue
                    }
                    updateNotification(
                        "已发现配对服务",
                        "${target.host}:${target.port} · 点下面输入 6 位配对码",
                        showInput = true
                    )
                    // 端口可能变化，继续低频刷新
                    delay(6_000L)
                    continue
                }
                if (round == 1 || round % 10 == 0) {
                    PairLog.info(this@AdbPairingService, "第 $round 轮：未发现配对服务（弹窗是否仍然打开？）")
                }
                updateNotification(
                    if (pendingCode.isNotBlank()) "已收到配对码，正在查找服务…" else "等待配对服务…",
                    "请在系统设置 → 无线调试 → 「使用配对码配对设备」打开弹窗（第 $round 次扫描）",
                    showInput = true
                )
                delay(2_000L)
            }
        }
    }

    // ------------------------------------------------------------ 配对

    private fun doPair(code: String) {
        if (busy) return
        busy = true
        val host = foundHost
        val port = foundPort
        if (host.isBlank() || port == 0) {
            busy = false
            notifyResult(false, "尚未发现配对服务，请确认配对弹窗仍然打开")
            return
        }
        PairLog.info(this, "开始配对：$host:$port（配对码 ${code.length} 位）")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                WirelessAdb.pair(this@AdbPairingService, host, port, code)
            }
            if (!result.ok) {
                busy = false
                notifyResult(false, result.message)
                return@launch
            }
            PairLog.ok(this@AdbPairingService, "SPAKE2 配对成功，正在连接无线调试端口")
            updateNotification("配对成功", "正在连接无线调试端口…", showInput = false)
            // 配对端口 ≠ 连接端口：逐候选尝试（mDNS → 系统属性 → 常见端口）
            val candidates = WirelessAdb.connectCandidates(this@AdbPairingService, host, port)
            PairLog.info(
                this@AdbPairingService,
                "连接候选 ${candidates.joinToString(" / ") { "${it.first}:${it.second}" }.ifBlank { "无" }}"
            )
            var ok = false
            var usedHost = host
            var usedPort = port
            for ((h, p) in candidates) {
                PairLog.info(this@AdbPairingService, "尝试连接 $h:$p")
                val connected = withContext(Dispatchers.IO) {
                    AdbShell.connect(this@AdbPairingService, h, p)
                }
                if (connected) {
                    ok = true
                    usedHost = h
                    usedPort = p
                    break
                }
                PairLog.warn(
                    this@AdbPairingService,
                    "连接 $h:$p 失败：${AdbShell.lastError().ifBlank { "未知原因" }}"
                )
            }
            val endpoint = usedHost to usedPort
            busy = false
            if (ok) {
                PairLog.ok(this@AdbPairingService, "无线调试已连接 · 特权通道就绪 ${endpoint.first}:${endpoint.second}")
                notifyResult(true, "已连接 · ${endpoint.first}:${endpoint.second}")
                // 与杀毒/实时检测联动：特权通道刚就绪，通知守护模块立刻接管
                PrivilegeExec.invalidate()
                runCatching {
                    startService(
                        Intent(this@AdbPairingService, com.xb.tool.guard.GuardService::class.java)
                            .setAction(com.xb.tool.guard.GuardService.ACTION_REFRESH_GUARD)
                    )
                }
                PairLog.info(this@AdbPairingService, "已通知守护模块接管特权通道")
            } else {
                notifyResult(false, AdbShell.lastError().ifBlank { "连接失败" })
            }
        }
    }

    // ------------------------------------------------------------ 通知

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "无线调试配对", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "在通知栏内输入配对码，完成免 Root 特权通道配对"
                setShowBadge(false)
                enableVibration(true)
            }
        )
    }

    private fun buildNotification(title: String, text: String, showInput: Boolean) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openAppIntent())
            .apply {
                if (showInput) {
                    addAction(replyAction())
                }
                addAction(
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_manage,
                        "打开系统配对页",
                        openWirelessDebuggingIntent()
                    ).build()
                )
                addAction(
                    NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "取消",
                        PendingIntent.getService(
                            this@AdbPairingService, 2,
                            Intent(this@AdbPairingService, AdbPairingService::class.java)
                                .setAction(ACTION_STOP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    ).build()
                )
            }
            .build()

    private fun replyAction(): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_CODE)
            .setLabel("6 位配对码")
            .setAllowFreeFormInput(true)
            .build()
        val intent = Intent(this, AdbPairingService::class.java).setAction(ACTION_PAIR)
        val pending = PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(android.R.drawable.ic_input_add, "输入配对码并配对", pending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    /**
     * 直接跳到系统的「无线调试」页面：用户需要在这里点开「使用配对码配对设备」，
     * 那 6 位码只在这个弹窗里显示。少一步自己找菜单。
     */
    private fun openWirelessDebuggingIntent(): PendingIntent {
        val candidates = listOf(
            Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
            Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        )
        val intent = candidates.firstOrNull { it.resolveActivity(packageManager) != null }
            ?: candidates.last()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, 4, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra("screen", "adb")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundSafe(notification: android.app.Notification) {
        runCatching { startForeground(NOTIF_ID, notification) }
            .onFailure { PairLog.warn(this, "配对服务前台通知失败：${it.message}") }
    }

    private fun updateNotification(title: String, text: String, showInput: Boolean) {
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NOTIF_ID, buildNotification(title, text, showInput))
        }
    }

    private fun notifyResult(ok: Boolean, message: String) {
        if (ok) {
            // 配对成功后立刻撤掉常驻的「输入配对码」通知，并退出前台服务：
            // 通知栏里不应该再留下任何配对相关的条目。
            PairLog.ok(this, "配对完成：$message")
            runCatching { NotificationManagerCompat.from(this).cancel(NOTIF_ID) }
            runCatching { NotificationManagerCompat.from(this).cancel(NOTIF_ID + 1) }
            runCatching {
                if (Build.VERSION.SDK_INT >= 24) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
            stopSelf()
            return
        }
        val text = "配对失败：$message"
        PairLog.error(this, text)
        runCatching {
            NotificationManagerCompat.from(this).notify(
                NOTIF_ID + 1,
                NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_xb)
                    .setContentTitle("无线调试配对失败")
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .build()
            )
        }
        // 失败后继续等待下一次输入
        updateNotification("配对失败", "$message · 请重新输入配对码", showInput = true)
    }

    override fun onDestroy() {
        discoveryJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
