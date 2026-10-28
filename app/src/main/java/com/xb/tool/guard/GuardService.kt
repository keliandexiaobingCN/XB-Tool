package com.xb.tool.guard

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.xb.tool.core.InterceptRecord
import com.xb.tool.core.InterceptStore
import com.xb.tool.core.LogBus
import com.xb.tool.core.Prefs
import com.xb.tool.core.Threat
import com.xb.tool.emergency.EmergencyNotifier
import com.xb.tool.scan.ScanEngine
import com.xb.tool.scan.VirusDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

class GuardService : Service() {
    companion object {
        const val ACTION_STOP_REALTIME = "com.xb.tool.action.STOP_REALTIME"
        const val ACTION_REFRESH_SHAKE = "com.xb.tool.action.REFRESH_SHAKE"
        const val ACTION_REFRESH_GUARD = "com.xb.tool.action.REFRESH_GUARD"

        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observers = mutableListOf<FileObserver>()
    private val recentScans = Collections.synchronizedSet(mutableSetOf<String>())
    private lateinit var prefs: Prefs
    private lateinit var engine: ScanEngine
    private var receiver: BroadcastReceiver? = null
    private var heartbeatJob: Job? = null
    private val shakeTimes = mutableListOf<Long>()
    private var lastEmergencyTrigger = 0L
    private var shakeListener: SensorEventListener? = null
    private var commandJob: Job? = null
    private var suspiciousJob: Job? = null
    private var strictSweepJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        engine = ScanEngine(this, VirusDatabase.load(this))
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notifications.guardNotification(this, prefs.realtimeEnabled)
        runCatching { startForeground(Notifications.ID_GUARD, notification) }
            .onFailure { LogBus.warn("前台服务启动失败：${it.message}") }
        startHeartbeat()
        startShakeDetection()

        if (intent?.action == ACTION_REFRESH_SHAKE) {
            stopShakeDetection()
            startShakeDetection()
            return START_STICKY
        }

        if (intent?.action == ACTION_REFRESH_GUARD) {
            if (prefs.realtimeEnabled) startRealtime() else stopRealtime()
            stopShakeDetection()
            startShakeDetection()
            syncCommandGuard()
            syncStrictSweep()
            return START_STICKY
        }

        if (intent?.action == ACTION_STOP_REALTIME) {
            prefs.realtimeEnabled = false
            stopRealtime()
            if (!prefs.keepAlive) {
                stopSelf()
                return START_NOT_STICKY
            }
            Notifications.notifyGuard(this, false)
            LogBus.info("已通过通知停止实时检测")
            return START_STICKY
        }

        if (prefs.realtimeEnabled) {
            startRealtime()
            LogBus.info("实时检测服务已启动，监控下载与安装")
        } else {
            stopRealtime()
            LogBus.info("后台守护中（实时检测关闭）")
        }
        syncCommandGuard()
        syncStrictSweep()
        return START_STICKY
    }

    /**
     * 严格模式：周期性用特权通道巡检已安装应用与受保护目录。
     *
     * Shell 模式是严格模式的超集 —— 除了自己额外的高危命令监控，严格模式的全部
     * 巡检能力也要一并生效，否则切到 Shell 模式反而弱化了防护。
     */
    private fun syncStrictSweep() {
        val wanted = prefs.realtimeEnabled &&
            (prefs.realtimeLevel == "strict" || prefs.realtimeLevel == "shell")
        if (wanted) startStrictSweep() else stopStrictSweep()
    }

    private fun startStrictSweep() {
        if (strictSweepJob?.isActive == true) return
        LogBus.info("严格模式已启动：特权通道巡检已安装应用与受保护目录")
        strictSweepJob = scope.launch {
            var warned = false
            while (isActive) {
                if (!com.xb.tool.privilege.PrivilegeExec.hasPrivilege()) {
                    if (!warned) {
                        LogBus.warn("严格模式等待特权通道：请授权 Shizuku / Root / 无线调试")
                        warned = true
                    }
                    delay(30_000L)
                    continue
                }
                warned = false
                runCatching { strictSweepOnce() }
                    .onFailure { LogBus.warn("严格模式巡检异常：${it.message}") }
                delay(10 * 60_000L)
            }
        }
    }

    /** 一轮巡检：已安装应用（含系统应用）APK + 受保护目录哈希。 */
    private suspend fun strictSweepOnce() = withContext(Dispatchers.IO) {
        val apps = runCatching {
            packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        var scanned = 0
        var hit = 0
        apps.forEach { app ->
            if (app.packageName == packageName) return@forEach
            val apk = File(app.sourceDir ?: return@forEach)
            if (!apk.isFile) return@forEach
            scanned++
            runCatching { engine.scanFile(apk, app.packageName, "严格模式 · 应用巡检") }
                .getOrNull()?.let { threat ->
                    hit++
                    recordIntercept(threat)
                }
        }
        val protected = runCatching {
            engine.scanPrivilegedPaths { cmd ->
                com.xb.tool.privilege.PrivilegeExec.exec(this@GuardService, cmd, 45_000L)
            }
        }.getOrDefault(emptyList())
        protected.forEach { threat ->
            hit++
            recordIntercept(threat)
        }
        prefs.strictSweepAt = System.currentTimeMillis()
        prefs.strictSweepApps = apps.size
        prefs.strictSweepThreats = hit
        LogBus.info("严格模式巡检完成：应用 ${apps.size} 个 · 文件 $scanned 个 · 命中 $hit 条")
    }

    private fun recordIntercept(threat: Threat) {
        prefs.realtimeBlocked = prefs.realtimeBlocked + 1
        runCatching { com.xb.tool.scan.QuarantineStore.record(this, threat, moveFile = false) }
        InterceptStore.add(
            this,
            InterceptRecord(
                id = System.currentTimeMillis(),
                time = System.currentTimeMillis(),
                name = threat.name,
                path = threat.path,
                action = "已拦截",
                signature = threat.name
            )
        )
        if (prefs.notifyIntercept) Notifications.notifyIntercept(this, threat)
        if (prefs.threatAlertPopup) {
            runCatching { ThreatAlertActivity.show(this, threat) }
                .onFailure { LogBus.warn("威胁告警弹窗失败：${it.message}") }
        }
    }

    private fun stopStrictSweep() {
        strictSweepJob?.cancel()
        strictSweepJob = null
    }

    /** 按当前模式启停高危命令监控。 */
    private fun syncCommandGuard() {
        val wanted = prefs.realtimeEnabled && prefs.commandGuardEnabled &&
            prefs.realtimeLevel == "shell"
        if (wanted) startCommandGuard() else stopCommandGuard()
    }

    private fun startCommandGuard() {
        if (commandJob?.isActive == true) return
        LogBus.info("Shell 模式已启动：监控应用执行的高危命令")
        commandJob = scope.launch {
            var warned = false
            while (isActive) {
                if (!com.xb.tool.privilege.PrivilegeExec.hasPrivilege()) {
                    if (!warned) {
                        LogBus.warn("Shell 模式等待特权通道：请授权 Root / Shizuku / 无线调试")
                        warned = true
                    }
                    delay(30_000L)
                    continue
                }
                warned = false
                val result = runCatching {
                    com.xb.tool.privilege.PrivilegeExec.exec(
                        this@GuardService,
                        "ps -A -o PID,UID,ARGS 2>/dev/null"
                    )
                }.getOrNull()
                if (result != null && result.out.isNotBlank()) {
                    val fresh = runCatching {
                        CommandGuard.pollWith(this@GuardService, result)
                    }.getOrDefault(emptyList())
                    if (fresh.isNotEmpty()) {
                        val merged = CommandGuard.appendAlerts(this@GuardService, fresh)
                        val top = fresh.maxByOrNull { it.risk.order } ?: fresh.first()
                        if (prefs.commandAlertPopup) {
                            CommandAlertActivity.show(this@GuardService, top)
                        }
                        LogBus.warn("Shell 模式拦截 ${fresh.size} 条高危命令（累计 ${merged.size} 条）")
                    }
                }
                prefs.lastCommandPoll = System.currentTimeMillis()
                delay(4_000L)
            }
        }
        // 可疑应用扫描与命令监控共用 Shell 模式的开关
        if (prefs.commandAlertPopup) {
            suspiciousJob = scope.launch {
                delay(6_000L)
                while (isActive) {
                    val items = runCatching {
                        SuspiciousAppScanner.scan(this@GuardService)
                    }.getOrDefault(emptyList())
                    val fresh = SuspiciousAppScanner.takeFresh(items)
                    if (fresh.isNotEmpty()) {
                        CommandGuard.appendAlerts(this@GuardService, SuspiciousAppScanner.toAlerts(fresh))
                        val top = fresh.first()
                        LogBus.warn("发现可疑应用：${top.label} · ${top.reason}")
                        CommandAlertActivity.show(this@GuardService, SuspiciousAppScanner.toAlerts(listOf(top)).first())
                    }
                    delay(60_000L)
                }
            }
        }
    }

    private fun stopCommandGuard() {
        commandJob?.cancel()
        commandJob = null
        suspiciousJob?.cancel()
        suspiciousJob = null
    }

    private fun startRealtime() {
        stopRealtime()
        val dirs = mutableListOf<File>()
        val storage = android.os.Environment.getExternalStorageDirectory()
        listOf("Download", "DCIM", "DCIM/Camera", "Pictures", "Movies", "Documents", "Music",
            "Telegram", "WhatsApp", "Android/data").forEach { name ->
            dirs += File(storage, name)
        }
        getExternalFilesDir(null)?.let { dirs += it }
        dirs.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            @Suppress("DEPRECATION")
            val observer = object : FileObserver(dir.absolutePath, CREATE or CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path.isNullOrBlank()) return
                    val file = File(dir, path)
                    if (!file.isFile) return
                    scheduleScan(file)
                }
            }
            runCatching {
                observer.startWatching()
                observers += observer
            }
        }

        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
                val packageName = intent.data?.schemeSpecificPart ?: return
                if (packageName == context?.packageName) return
                scope.launch {
                    delay(600)
                    val appInfo = runCatching {
                        packageManager.getApplicationInfo(packageName, 0)
                    }.getOrNull() ?: return@launch
                    val apk = File(appInfo.sourceDir)
                    if (apk.isFile) {
                        scanAndIntercept(apk, packageName, "实时安装检测")
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiver = packageReceiver
        }
    }

    private fun stopRealtime() {
        observers.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
    }

    /** 按防护强度决定是否扫描该文件。 */
    private fun shouldScanByLevel(file: File): Boolean {
        val name = file.name.lowercase()
        val ext = name.substringAfterLast('.', "")
        return when (prefs.realtimeLevel) {
            // 低模式：只扫可执行与安装包，开销最小
            "low" -> ext in setOf("apk", "dex", "so", "jar")
            // 严格模式：所有文件都过一遍（含文档、压缩包）
            "strict" -> file.length() <= 64L * 1024 * 1024
            // Shell 模式：严格模式的全量文件扫描 + 额外的高危命令监控
            "shell" -> file.length() <= 64L * 1024 * 1024
            // 标准模式：常见可携带载荷的类型
            else -> ext in setOf(
                "apk", "dex", "so", "jar", "zip", "rar", "7z",
                "exe", "dll", "bat", "sh", "js", "vbs",
                "docm", "xlsm", "pdf"
            )
        }
    }

    private fun scheduleScan(file: File) {
        if (!shouldScanByLevel(file)) return
        val path = file.absolutePath
        if (recentScans.contains(path)) return
        recentScans.add(path)
        if (recentScans.size > 200) {
            synchronized(recentScans) {
                recentScans.take(80).forEach { recentScans.remove(it) }
            }
        }
        scope.launch {
            delay(900)
            val current = File(path)
            if (!current.exists() || !current.isFile) return@launch
            scanAndIntercept(current, null, "实时检测")
        }
    }

    private fun scanAndIntercept(file: File, packageName: String?, source: String) {
        prefs.realtimeLastAt = System.currentTimeMillis()
        prefs.realtimeScanned = prefs.realtimeScanned + 1
        val threat = runCatching { engine.scanFile(file, packageName, source) }.getOrNull() ?: return
        prefs.realtimeBlocked = prefs.realtimeBlocked + 1
        LogBus.warn("实时拦截：${threat.name} · ${threat.path}")
        runCatching { com.xb.tool.scan.QuarantineStore.record(this, threat, moveFile = false) }
        InterceptStore.add(
            this,
            InterceptRecord(
                id = System.currentTimeMillis(),
                time = System.currentTimeMillis(),
                name = threat.name,
                path = threat.path,
                action = "已拦截",
                signature = threat.name
            )
        )
        if (prefs.notifyIntercept) {
            Notifications.notifyIntercept(this, threat)
        }
        // 低 / 标准 / 严格 / Shell 模式统一：命中后弹窗告警，并直接给出处置动作
        if (prefs.threatAlertPopup) {
            runCatching { ThreatAlertActivity.show(this, threat) }
                .onFailure { LogBus.warn("威胁告警弹窗失败：${it.message}") }
        }
    }

    private fun startShakeDetection() {
        if (shakeListener != null) return
        if (!EmergencyNotifier.isEnabled(this) || !EmergencyNotifier.isShakeEnabled(this)) return
        runCatching {
            val manager = getSystemService(SensorManager::class.java)
            val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            val threshold = EmergencyNotifier.shakeThreshold(this)
            val needCount = EmergencyNotifier.shakeCount(this)
            val windowMs = EmergencyNotifier.shakeWindowMs(this)
            // 两次有效冲击之间的最小间隔：一次猛烈撞击会在相邻采样点连续超阈值，
            // 不加间隔的话一次撞击就能刷满计数，等于把「摇一摇」变成了「磕一下」。
            // 140ms 大约相当于人手每秒 7 次往复的上限，日常走动/放桌面不会达到。
            val minGapMs = 150L
            // 一次真实的「摇一摇」必须持续一段时间：单次磕碰、放下手机、跑步落地
            // 都只有 1~2 个峰值，撑不满这个跨度，因此不会被误判。
            val minSpanMs = 700L
            var lastPeakAt = 0L
            var firstPeakAt = 0L
            // 上一次冲击的加速度方向：真正的「摇」是来回往复，方向必然交替；
            // 走路、坐下、放桌上都只是单方向的冲击，方向不会来回翻转。
            var lastSign = 0
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // 用主轴（绝对值最大的轴）的符号判断方向，避免整体幅值互相抵消
                    val axis = when {
                        kotlin.math.abs(x) >= kotlin.math.abs(y) && kotlin.math.abs(x) >= kotlin.math.abs(z) -> x
                        kotlin.math.abs(y) >= kotlin.math.abs(z) -> y
                        else -> z
                    }
                    val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val force = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)
                    if (force < threshold) return
                    val now = System.currentTimeMillis()
                    if (now - lastPeakAt < minGapMs) return
                    val sign = if (axis >= 0f) 1 else -1
                    // 第一次冲击只记方向；之后必须与上一次方向相反才算一次有效往复
                    if (lastSign != 0 && sign == lastSign) return
                    lastSign = sign
                    lastPeakAt = now
                    synchronized(shakeTimes) {
                        shakeTimes += now
                        shakeTimes.removeAll { now - it > windowMs }
                        if (shakeTimes.size == 1) firstPeakAt = now
                        val spanOk = now - firstPeakAt >= minSpanMs
                        if (shakeTimes.size >= needCount && spanOk && now - lastEmergencyTrigger > 20_000L) {
                            lastEmergencyTrigger = now
                            shakeTimes.clear()
                            lastSign = 0
                            firstPeakAt = 0
                            LogBus.warn(
                                "检测到${EmergencyNotifier.shakeLevelName(this@GuardService)}，" +
                                    "触发紧急救机（${needCount} 次往复 / ${windowMs}ms）"
                            )
                            EmergencyNotifier.trigger(this@GuardService, "摇一摇")
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            shakeListener = listener
        }.onFailure { LogBus.warn("摇一摇监听启动失败：${it.message}") }
    }

    private fun stopShakeDetection() {
        runCatching {
            getSystemService(SensorManager::class.java).unregisterListener(shakeListener)
        }
        shakeListener = null
        shakeTimes.clear()
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        Heartbeat.beat(this, Heartbeat.GUARD)
        heartbeatJob = scope.launch {
            while (isActive) {
                Heartbeat.beat(this@GuardService, Heartbeat.GUARD)
                if (prefs.extremeKeepAlive) {
                    val watchdogAge = Heartbeat.age(this@GuardService, Heartbeat.WATCHDOG)
                    if (watchdogAge > 50_000L) {
                        LogBus.warn("守护进程：检测到互拉守护失联，正在拉起")
                        runCatching { WatchdogService.start(this@GuardService) }
                            .onFailure { LogBus.warn("拉起互拉守护失败：${it.message}") }
                    }
                }
                delay(15_000L)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Heartbeat.clear(this, Heartbeat.GUARD)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRealtime()
        stopHeartbeat()
        stopShakeDetection()
        stopCommandGuard()
        stopStrictSweep()
        scope.cancel()
        super.onDestroy()
    }
}
