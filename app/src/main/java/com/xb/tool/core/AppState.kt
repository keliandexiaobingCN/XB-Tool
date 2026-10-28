package com.xb.tool.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xb.tool.network.Announcement
import com.xb.tool.network.AppUpdateInfo
import com.xb.tool.network.UpdateManifest
import com.xb.tool.network.UpdateRepository
import com.xb.tool.network.VirusDbUpdateInfo
import com.xb.tool.guard.GuardService
import com.xb.tool.guard.Notifications
import com.xb.tool.guard.WatchdogService
import com.xb.tool.apps.AppRepository
import com.xb.tool.root.RootDetector
import com.xb.tool.root.Shell
import com.xb.tool.root.ShizukuUtils
import com.xb.tool.scan.ScanEngine
import com.xb.tool.scan.ScanMode
import com.xb.tool.scan.VirusDatabase
import com.xb.tool.util.CacheTools
import com.xb.tool.util.FileUtils
import com.xb.tool.util.Intents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AppState(val context: Context) {
    val prefs = Prefs(context)
    var database by mutableStateOf(VirusDatabase.load(context))
        private set
    var engine by mutableStateOf(ScanEngine(context, database))
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Navigation
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    private val backStack = mutableListOf<Screen>()

    // Global UI state
    var busy by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    // Root / Shizuku
    var rootInfo by mutableStateOf(RootInfo())
        private set
    var shizukuRunning by mutableStateOf(false)
        private set
    var shizukuGranted by mutableStateOf(false)
        private set
    var rootChecking by mutableStateOf(false)
        private set
    private var pendingShizuku: ((Boolean) -> Unit)? = null

    // 无线调试（免 Root 特权通道）
    var adbConnected by mutableStateOf(false)
        private set
    var adbBusy by mutableStateOf(false)
        private set
    var adbStatus by mutableStateOf("")
        private set
    var adbPairingServices by mutableStateOf<List<com.xb.tool.privilege.WirelessAdb.Discovered>>(emptyList())
        private set
    var adbDiscovering by mutableStateOf(false)
        private set
    /** 手动填写的配对地址（mDNS 不可见时的兜底），格式 host:port。 */
    var adbManualPair by mutableStateOf("")
        private set
    /** 本机 IP 与当前 TLS 端口，界面上直接显示，便于手填。 */
    var adbLocalIp by mutableStateOf("")
        private set
    var adbTlsPort by mutableStateOf(0)
        private set

    // Scan
    var scanState by mutableStateOf(ScanUiState())
        private set
    private var scanJob: Job? = null
    private val scanCancel = AtomicBoolean(false)

    // Settings
    var keepAlive by mutableStateOf(prefs.keepAlive)
        private set
    var extremeKeepAlive by mutableStateOf(prefs.extremeKeepAlive)
        private set
    var realtime by mutableStateOf(prefs.realtimeEnabled)
        private set
    var notifyScan by mutableStateOf(prefs.notifyScan)
        private set
    var notifyIntercept by mutableStateOf(prefs.notifyIntercept)
        private set
    var notifyUpdate by mutableStateOf(prefs.notifyUpdate)
    var autoCheckUpdate by mutableStateOf(prefs.autoCheckUpdate)
    var autoCheckIntervalMin by mutableStateOf(prefs.autoCheckIntervalMin)
    var autoUpdateVirusDb by mutableStateOf(prefs.autoUpdateVirusDb)
    var showVirusDbPrompt by mutableStateOf(false)
    var agreementAccepted by mutableStateOf(prefs.agreementAccepted)
        private set
    var notifyPermission by mutableStateOf(prefs.notifyPermission)
        private set
    var rootRemind by mutableStateOf(prefs.rootRemind)
        private set
    var notificationsGranted by mutableStateOf(Notifications.areEnabled(context))
        private set
    var allFilesGranted by mutableStateOf(
        if (android.os.Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
    )
        private set
    var backgroundUnrestricted by mutableStateOf(prefs.backgroundUnrestricted)
        private set
    var autoStartEnabled by mutableStateOf(prefs.autoStartEnabled)
        private set
    // 自启动的真实检测结果：ALLOWED / DENIED / UNKNOWN，配套一条可读的检测渠道说明
    var autoStartState by mutableStateOf(com.xb.tool.system.AutoStartDetector.State.UNKNOWN)
        private set
    var autoStartChannel by mutableStateOf("未检测")
        private set

    // Theme
    var theme by mutableStateOf(
        ThemeConfig(
            seed = prefs.themeSeed,
            darkMode = prefs.darkMode,
            fontScale = prefs.fontScale,
            fontFamily = prefs.fontFamily,
            backgroundPath = prefs.backgroundPath,
            backgroundVideoPath = prefs.backgroundVideoPath,
            backgroundBlur = prefs.backgroundBlur,
            backgroundAlpha = prefs.backgroundAlpha,
            uiAnimation = prefs.uiAnimation,
            glassEffect = prefs.glassEffect,
            glassAlpha = prefs.glassAlpha,
            backgroundColor = prefs.backgroundColor,
            navStyle = prefs.navStyle
        )
    )
        private set

    // Whitelist / freeze
    var whitelist by mutableStateOf(prefs.whitelist().toSet())
        private set
    var frozenApps by mutableStateOf(prefs.frozenApps().toSet())
        private set

    // Cache
    var cacheInfo by mutableStateOf(CacheTools.CacheInfo())
        private set
    var cacheCalculating by mutableStateOf(false)
        private set
    var cacheCleaning by mutableStateOf(false)
        private set
    var appCaches by mutableStateOf<List<AppCacheEntry>>(emptyList())
        private set
    var appCachesScanning by mutableStateOf(false)
        private set
    var appCachesCleaning by mutableStateOf(false)
        private set
    var runningApps by mutableStateOf<List<RunningAppEntry>>(emptyList())
        private set
    var runningAppsScanning by mutableStateOf(false)
        private set
    var runningAppsCleaning by mutableStateOf(false)
        private set

    // Update checks
    var dbChecking by mutableStateOf(false)
        private set
    var dbCheckResult by mutableStateOf<String?>(null)
        private set
    var virusDbDirectUrl by mutableStateOf(prefs.virusDbDirectUrl)
        private set
    var updateChecking by mutableStateOf(false)
        private set
    var updateCheckResult by mutableStateOf<String?>(null)
        private set
    var announcements by mutableStateOf<List<Announcement>>(emptyList())
        private set
    var changelog by mutableStateOf<List<com.xb.tool.network.ChangelogEntry>>(emptyList())
        private set
    var dbHistory by mutableStateOf<List<com.xb.tool.network.VirusDbHistoryEntry>>(emptyList())
        private set
    var manifestSource by mutableStateOf("内置")
        private set
    var manifestError by mutableStateOf<String?>(null)
        private set
    var appUpdateInfo by mutableStateOf<AppUpdateInfo?>(null)
        private set
    var dbUpdateInfo by mutableStateOf<VirusDbUpdateInfo?>(null)
        private set
    var dbUpdating by mutableStateOf(false)
        private set
    var apkDownloading by mutableStateOf(false)
        private set
    var apkProgress by mutableStateOf(0f)
        private set
    var apkStatus by mutableStateOf("")
        private set
    var lastAnnouncementSeen by mutableStateOf(prefs.lastAnnouncementSeen)
        private set
    var updateManifestUrl by mutableStateOf(prefs.updateManifestUrl)
        private set
    var guardAlive by mutableStateOf(false)
        private set
    var watchdogAlive by mutableStateOf(false)
        private set

    var lastScanAt by mutableStateOf(prefs.lastScanAt)
        private set
    var lastScanThreats by mutableStateOf(prefs.lastScanThreats)
        private set
    var lastUpdateCheck by mutableStateOf(prefs.lastUpdateCheck)
        private set

    var notificationRequester: (() -> Unit)? = null

    fun requestNotificationPermission() {
        notificationRequester?.invoke()
    }

    private var bootstrapped = false

    private val binderReceivedListener = object : Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() {
            shizukuRunning = true
            LogBus.ok("Shizuku 服务已连接")
            refreshShizuku()
        }
    }

    private val binderDeadListener = object : Shizuku.OnBinderDeadListener {
        override fun onBinderDead() {
            shizukuRunning = false
            shizukuGranted = false
            ShizukuUtils.reset()
            LogBus.warn("Shizuku 服务已断开")
        }
    }

    private val permissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            shizukuGranted = granted
            if (granted) {
                LogBus.ok("Shizuku 授权成功")
                scope.launch {
                    ShizukuUtils.bind(context) { ok ->
                        scope.launch {
                            if (ok) LogBus.ok("Shizuku 用户服务已连接")
                            pendingShizuku?.let { it(ok) }
                            pendingShizuku = null
                        }
                    }
                }
            } else {
                LogBus.warn("Shizuku 授权被拒绝")
                pendingShizuku?.let { callback -> scope.launch { callback(false) } }
                pendingShizuku = null
            }
            refreshRoot(false)
        }
    }

    init {
        InterceptStore.init(context)
        loadDbHistory()
        // 以内置清单为准初始化；缓存仅在版本一致时使用，避免旧公告残留
        val local = UpdateRepository.localManifest(context)
        val cached = readCachedAnnouncements()
        val cacheVersion = prefs.announcementCacheVersion
        // 内置清单优先：它随 APK 一起更新，不会出现旧版本公告残留。
        // 只有内置清单为空（例如被裁剪）时才回退到缓存，且缓存版本号必须与当前版本一致。
        announcements = if (local.announcements.isNotEmpty()) local.announcements
        else if (cacheVersion == local.app?.versionName && cached.isNotEmpty()) cached
        else emptyList()
        changelog = local.changelog
        scope.launch(Dispatchers.IO) {
            com.xb.tool.util.TestSampleCleanup.cleanup(context)
        }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
    }

    fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true
        LogBus.info("XB工具 v1.3.7 已启动")
        refreshRoot(false)
        refreshShizuku()
        refreshCache()
        scope.launch {
            delay(500)
            LogBus.info("病毒库 v${database.version} 已加载 · ${database.signatureCount} 条特征")
            LogBus.info("在线更新与公告已接入：${prefs.updateManifestUrl}")
            notificationsGranted = Notifications.areEnabled(context)
            LogBus.info(
                if (notificationsGranted) "通知权限已开启"
                else "通知权限未开启，扫描与拦截提醒可能不可见"
            )
            // 极致保活本身就包含守护进程，启动时也必须一起拉起来，
            // 否则重启后开关是「开」但服务没在跑。
            if (keepAlive || extremeKeepAlive || realtime) {
                if (realtime || keepAliveGate(extremeKeepAlive).isEmpty()) {
                    startGuard()
                } else {
                    keepAlive = false
                    prefs.keepAlive = false
                    LogBus.warn("保活前置条件不满足，后台保活不会自动开启")
                }
            }
            prefs.lastSelfCheck = System.currentTimeMillis()
            if (!prefs.virusDbPromptShown && database.hashes.size <= 8) {
                showVirusDbPrompt = true
            }
            if (autoCheckUpdate) {
                delay(1500)
                maybeAutoCheck()
                while (true) {
                    delay(maxOf(1, autoCheckIntervalMin) * 60_000L)
                    maybeAutoCheck()
                }
            }
        }
    }

    /** 到间隔就检查更新；有病毒库更新且允许自动更新时直接下载。 */
    private suspend fun maybeAutoCheck() {
        if (!autoCheckUpdate) return
        val interval = maxOf(1, autoCheckIntervalMin) * 60_000L
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < interval) return
        checkUpdates { }
        if (autoUpdateVirusDb && dbUpdateInfo != null) {
            updateVirusDatabase { _, message -> LogBus.info("自动更新病毒库：$message") }
        }
    }

    fun acceptAgreement() {
        agreementAccepted = true
        prefs.agreementAccepted = true
        LogBus.ok("用户已同意用户协议与法律条款")
    }

    fun dismissVirusDbPrompt() {
        showVirusDbPrompt = false
        prefs.virusDbPromptShown = true
    }

    fun applyAutoCheckUpdate(value: Boolean) {
        autoCheckUpdate = value
        prefs.autoCheckUpdate = value
    }

    fun applyAutoCheckInterval(minutes: Int) {
        val safe = minutes.coerceIn(1, 1440)
        autoCheckIntervalMin = safe
        prefs.autoCheckIntervalMin = safe
    }

    fun applyAutoUpdateVirusDb(value: Boolean) {
        autoUpdateVirusDb = value
        prefs.autoUpdateVirusDb = value
    }

    // ------------------------------------------------------------------ nav
    fun navigate(target: Screen) {
        if (target == screen) return
        if (target.isTabRoot) {
            backStack.clear()
            screen = target
        } else {
            backStack += screen
            screen = target
        }
    }

    fun goBack(): Boolean {
        if (backStack.isEmpty()) return false
        screen = backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun switchTab(target: Screen) {
        backStack.clear()
        screen = target
    }

    fun showMessage(text: String) {
        message = text
    }

    fun consumeMessage() {
        message = null
    }

    // ----------------------------------------------------------------- root
    fun refreshRoot(showBusy: Boolean = true) {
        if (rootChecking) return
        rootChecking = true
        if (showBusy) busy = "正在检测 Root 环境…"
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                RootDetector.detect(context, probeRoot = prefs.rootVerified)
            }
            if (prefs.rootVerified && !info.rootGranted) prefs.rootVerified = false
            rootInfo = info
            shizukuRunning = info.shizukuRunning
            shizukuGranted = info.shizukuGranted
            rootChecking = false
            busy = null
            if (info.rootGranted) {
                LogBus.ok("Root 环境：${info.managerName} ${info.managerVersion} · SELinux ${info.selinux}")
                LogBus.info("内核版本：${info.kernel}")
            } else {
                LogBus.warn("未获取到 Root 权限 · ${info.managerName}")
            }
            if (info.shizukuGranted) {
                ShizukuUtils.bind(context) { }
            }
        }
    }

    fun requestRootAccess(onReady: (Boolean) -> Unit) {
        if (busy != null) {
            onReady(false)
            return
        }
        busy = "正在请求 Root 授权…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { Shell.exec("id", 25_000L, asRoot = true) }
            val granted = !result.timedOut && result.out.contains("uid=0")
            if (granted) {
                prefs.rootVerified = true
                val info = withContext(Dispatchers.IO) { RootDetector.detect(context, probeRoot = true) }
                rootInfo = info
                LogBus.ok("Root 授权成功：${info.managerName} ${info.managerVersion}")
            } else {
                LogBus.warn("Root 授权未通过：${result.err.ifBlank { result.out }.ifBlank { "请求超时或被拒绝" }}")
            }
            busy = null
            onReady(granted)
        }
    }

    fun requestShizukuAccess(onReady: (Boolean) -> Unit) {
        if (!shizukuRunning) {
            showMessage("Shizuku 服务未运行，请先启动 Shizuku")
            LogBus.warn("Shizuku 服务未运行，无法申请授权")
            onReady(false)
            return
        }
        if (shizukuGranted) {
            busy = "正在连接 Shizuku 服务…"
            ShizukuUtils.bind(context) { ok ->
                scope.launch {
                    busy = null
                    if (ok) LogBus.ok("Shizuku 服务就绪") else LogBus.warn("Shizuku 服务连接失败")
                    onReady(ok)
                }
            }
            return
        }
        pendingShizuku = { granted ->
            scope.launch { onReady(granted) }
        }
        if (!ShizukuUtils.requestPermission(1001)) {
            pendingShizuku = null
            onReady(false)
        }
    }

    fun refreshShizuku() {
        scope.launch {
            val running = withContext(Dispatchers.IO) { ShizukuUtils.isBinderAlive() }
            val granted = if (running) withContext(Dispatchers.IO) { ShizukuUtils.isGranted() } else false
            shizukuRunning = running
            shizukuGranted = granted
            if (granted) {
                ShizukuUtils.bind(context) { ok ->
                    if (ok) LogBus.ok("Shizuku 用户服务已连接") else LogBus.info("Shizuku 已授权，等待用户服务连接")
                }
            }
        }
    }

    fun hasDeepAccess(): Boolean = rootInfo.rootGranted || shizukuGranted || adbConnected

    // ------------------------------------------------------- 无线调试（免 Root）

    /** 刷新无线调试连接状态（不主动连接）。 */
    fun refreshAdb() {
        adbConnected = com.xb.tool.privilege.AdbShell.isConnected()
        adbLocalIp = com.xb.tool.privilege.WirelessAdb.localWifiIp()
        adbTlsPort = com.xb.tool.privilege.WirelessAdb.tlsPort()
        adbStatus = when {
            adbConnected -> "已连接 · 权限等同 adb shell"
            com.xb.tool.privilege.AdbShell.lastError().isNotBlank() ->
                com.xb.tool.privilege.AdbShell.lastError()
            else -> "未连接"
        }
    }

    fun saveAdbManualPair(text: String) {
        adbManualPair = text
    }

    /** 解析 host:port（也接受「IP 地址和端口」整行文本）。 */
    private fun parseEndpoint(raw: String): Pair<String, Int>? {
        val cleaned = raw.trim().replace("：", ":")
        if (cleaned.isBlank()) return null
        val parts = cleaned.split(":")
        if (parts.size < 2) return null
        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull() ?: return null
        if (host.isBlank() || port <= 0 || port > 65535) return null
        return host to port
    }

    /** 直接用本机 IP + 系统 TLS 端口连接（跳过 mDNS 与配对，适用于已配对过的情况）。 */
    fun connectAdbDirect() {
        if (adbBusy) return
        val endpoint = com.xb.tool.privilege.WirelessAdb.directEndpoint()
        if (endpoint == null) {
            adbStatus = "无法获取本机 WLAN IP 或无线调试端口，请确认已连接 WLAN 并开启无线调试"
            return
        }
        adbBusy = true
        adbStatus = "正在连接 ${endpoint.first}:${endpoint.second}…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                com.xb.tool.privilege.AdbShell.connect(context, endpoint.first, endpoint.second)
            }
            adbBusy = false
            adbConnected = ok
            adbStatus = if (ok) "已连接 · ${endpoint.first}:${endpoint.second}"
            else com.xb.tool.privilege.AdbShell.lastError()
            LogBus.info(if (ok) "无线调试直连成功" else "无线调试直连失败：$adbStatus")
        }
    }

    /** 发现系统当前广播的配对服务。 */
    fun discoverAdbPairing() {
        if (adbDiscovering) return
        adbDiscovering = true
        adbStatus = "正在查找配对服务…"
        com.xb.tool.privilege.PairLog.info(context, "手动扫描配对服务（6 秒）")
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                com.xb.tool.privilege.WirelessAdb.discoverPairing(context, 6_000L)
            }
            adbPairingServices = found
            adbDiscovering = false
            adbStatus = if (found.isEmpty())
                "未发现配对服务：请先在系统设置中打开「无线调试 → 使用配对码配对设备」"
            else "发现 ${found.size} 个配对服务"
            if (found.isEmpty()) {
                com.xb.tool.privilege.PairLog.warn(context, "手动扫描：未发现配对服务")
            } else {
                found.forEach {
                    com.xb.tool.privilege.PairLog.ok(context, "手动扫描：${it.name} · ${it.host}:${it.port}")
                }
            }
        }
    }

    /** 用 6 位配对码完成配对，随后立刻尝试连接。 */
    fun pairAdb(code: String, onDone: (Boolean, String) -> Unit) {
        if (adbBusy) return
        adbBusy = true
        adbStatus = "正在配对…"
        scope.launch {
            val target = adbPairingServices.firstOrNull()
            val manual = parseEndpoint(adbManualPair)
            val host: String
            val port: Int
            when {
                target != null -> {
                    host = target.host
                    port = target.port
                }
                manual != null -> {
                    host = manual.first
                    port = manual.second
                }
                else -> {
                    adbBusy = false
                    adbStatus = "未发现配对服务"
                    com.xb.tool.privilege.PairLog.error(context, "配对中止：未发现配对服务，也没有填写配对地址")
                    onDone(false, "未发现配对服务。请在下方手动填写系统显示的「IP 地址和端口」，或直接点「直连（已配对）」")
                    return@launch
                }
            }
            com.xb.tool.privilege.PairLog.info(context, "开始配对 $host:$port")
            val result = withContext(Dispatchers.IO) {
                com.xb.tool.privilege.WirelessAdb.pair(context, host, port, code)
            }
            if (!result.ok) {
                adbBusy = false
                adbStatus = result.message
                com.xb.tool.privilege.PairLog.error(context, "配对失败：${result.message}")
                onDone(false, result.message)
                return@launch
            }
            com.xb.tool.privilege.PairLog.ok(context, "配对成功，正在连接 TLS 端口")
            // 关键：配对端口与连接端口不同，连接必须用连接端口。
            // 不同 ROM 提供连接端口的方式不一样（mDNS / 系统属性 / 固定端口），
            // 这里逐个候选尝试，避免某一路失效就整体失败。
            val candidates = com.xb.tool.privilege.WirelessAdb
                .connectCandidates(context, result.host, result.port)
            com.xb.tool.privilege.PairLog.info(
                context,
                "连接候选 ${candidates.joinToString(" / ") { "${it.first}:${it.second}" }.ifBlank { "无" }}"
            )
            var connect = false
            var usedHost = result.host
            var usedPort = result.port
            for ((h, p) in candidates) {
                com.xb.tool.privilege.PairLog.info(context, "尝试连接 $h:$p")
                val ok = withContext(Dispatchers.IO) {
                    com.xb.tool.privilege.AdbShell.connect(context, h, p)
                }
                if (ok) {
                    connect = true
                    usedHost = h
                    usedPort = p
                    com.xb.tool.privilege.PairLog.ok(context, "连接成功 $h:$p")
                    break
                }
                com.xb.tool.privilege.PairLog.warn(
                    context,
                    "连接 $h:$p 失败：${com.xb.tool.privilege.AdbShell.lastError().ifBlank { "未知原因" }}"
                )
            }
            val endpoint = usedHost to usedPort
            adbBusy = false
            adbConnected = connect
            adbLocalIp = com.xb.tool.privilege.WirelessAdb.localWifiIp()
            adbTlsPort = com.xb.tool.privilege.WirelessAdb.tlsPort()
            if (connect) {
                com.xb.tool.privilege.PairLog.ok(context, "无线调试已连接 · 特权通道就绪")
            } else {
                com.xb.tool.privilege.PairLog.error(
                    context,
                    "连接失败：${com.xb.tool.privilege.AdbShell.lastError().ifBlank { "未知原因" }}"
                )
            }
            adbStatus = if (connect) "配对并连接成功 · ${endpoint.first}:${endpoint.second}"
            else com.xb.tool.privilege.AdbShell.lastError()
            if (connect) refreshGuardForShellMode()
            onDone(connect, adbStatus)
        }
    }

    /** 特权通道就绪后通知守护进程重新评估 Shell 模式。 */
    private fun refreshGuardForShellMode() {
        runCatching {
            context.startService(
                android.content.Intent(context, com.xb.tool.guard.GuardService::class.java)
                    .setAction(com.xb.tool.guard.GuardService.ACTION_REFRESH_GUARD)
            )
        }.onFailure { LogBus.warn("刷新守护模块失败：${it.message}") }
    }

    /** 直接用上次保存的地址重连。 */
    fun reconnectAdb() {
        if (adbBusy) return
        adbBusy = true
        adbStatus = "正在连接…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                com.xb.tool.privilege.AdbShell.connect(context)
            }
            adbBusy = false
            adbConnected = ok
            adbStatus = if (ok) "已连接" else com.xb.tool.privilege.AdbShell.lastError()
            LogBus.info(if (ok) "无线调试已连接" else "无线调试连接失败：$adbStatus")
        }
    }

    fun disconnectAdb() {
        com.xb.tool.privilege.AdbShell.disconnect()
        adbConnected = false
        adbStatus = "已断开"
        LogBus.info("无线调试已断开")
    }

    /** 在无线调试通道上执行一条命令（用于界面自检）。 */
    fun adbExec(command: String, onResult: (String) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                com.xb.tool.privilege.AdbShell.exec(command, 20_000L)
            }
            onResult(result.text.ifBlank { if (result.success) "（无输出）" else "执行失败" })
        }
    }

    fun ensureDeepPrivilege(onReady: (Boolean) -> Unit) {
        if (rootInfo.rootGranted) {
            onReady(true)
            return
        }
        if (shizukuGranted) {
            busy = "正在连接 Shizuku 服务…"
            ShizukuUtils.bind(context) { ok ->
                scope.launch {
                    busy = null
                    onReady(ok)
                }
            }
            return
        }
        if (com.xb.tool.privilege.AdbShell.isConnected()) {
            adbConnected = true
            onReady(true)
            return
        }
        busy = "正在申请 Root / Shizuku 权限…"
        scope.launch {
            val granted = withContext(Dispatchers.IO) {
                Shell.exec("id", 15_000L, asRoot = true).out.contains("uid=0")
            }
            if (granted) {
                prefs.rootVerified = true
                val info = withContext(Dispatchers.IO) { RootDetector.detect(context, probeRoot = true) }
                rootInfo = info
                busy = null
                LogBus.ok("Root 授权成功，强力杀毒已就绪")
                onReady(true)
                return@launch
            }
            if (shizukuRunning) {
                busy = null
                LogBus.info("Root 不可用，尝试通过 Shizuku 授权")
                requestShizukuAccess(onReady)
                return@launch
            }
            busy = null
            LogBus.warn("未获得 Root / Shizuku 权限，强力杀毒不可用")
            onReady(false)
        }
    }

    suspend fun privilegedExec(command: String): Shell.Result = withContext(Dispatchers.IO) {
        when {
            rootInfo.rootGranted -> Shell.exec(command, 30_000L, asRoot = true)
            shizukuGranted -> {
                if (!ShizukuUtils.isServiceReady()) {
                    val connected = suspendCoroutine<Boolean> { continuation ->
                        ShizukuUtils.bind(context) { continuation.resume(it) }
                    }
                    if (!connected) return@withContext Shell.Result(-1, "", "Shizuku 服务尚未连接")
                }
                ShizukuUtils.exec(command)
            }
            com.xb.tool.privilege.AdbShell.isConnected() ->
                com.xb.tool.privilege.AdbShell.exec(command, 30_000L)
            else -> Shell.Result(-1, "", "需要 Root、Shizuku 或无线调试权限")
        }
    }

    // ----------------------------------------------------------------- scan
    fun startScan(mode: ScanMode) {
        if (scanState.running) return
        scanCancel.set(false)
        scanState = ScanUiState(
            running = true,
            mode = mode,
            startedAt = System.currentTimeMillis()
        )
        lastScanAt = System.currentTimeMillis()
        LogBus.info("开始${mode.title} · ${if (mode == ScanMode.DEEP) "增强模式" else "标准模式"}")
        val started = System.currentTimeMillis()
        scanJob = scope.launch {
            var threats: List<Threat> = emptyList()
            try {
                threats = withContext(Dispatchers.IO) {
                    engine.scan(
                        mode = mode,
                        onProgress = { scanned, total, current, found ->
                            if (scanState.running) {
                                scanState = scanState.copy(
                                    scanned = scanned,
                                    total = total,
                                    current = current,
                                    threats = scanState.threats
                                )
                            }
                        },
                        onThreat = { threat ->
                            if (scanState.threats.none { it.path == threat.path }) {
                                scanState = scanState.copy(threats = scanState.threats + threat)
                                LogBus.warn("发现威胁：${threat.name} · ${threat.path}")
                            }
                        },
                        isCancelled = { scanCancel.get() || !isActive },
                        privilegedExec = if (mode == ScanMode.DEEP) {
                            { command -> privilegedExecBlocking(command) }
                        } else null
                    )
                }
            } catch (cancelled: CancellationException) {
                // handled below
            } catch (t: Throwable) {
                scanState = scanState.copy(error = t.message ?: "扫描异常")
                LogBus.error("扫描失败：${t.message}")
            }
            val duration = System.currentTimeMillis() - started
            scanState = scanState.copy(
                running = false,
                finished = true,
                durationMs = duration,
                current = "",
                threats = if (threats.isNotEmpty()) threats else scanState.threats
            )
            lastScanAt = System.currentTimeMillis()
            lastScanThreats = scanState.threats.size
            prefs.lastScanAt = lastScanAt
            prefs.lastScanThreats = lastScanThreats
            if (scanCancel.get()) {
                LogBus.warn("扫描已停止 · 已检查 ${scanState.scanned} 个文件")
            } else if (scanState.threats.isEmpty()) {
                LogBus.ok("扫描完成 · 未发现威胁 · 用时 ${FileUtils.formatDuration(duration)}")
            } else {
                LogBus.warn("扫描完成 · 发现 ${scanState.threats.size} 个威胁 · 用时 ${FileUtils.formatDuration(duration)}")
            }
            if (scanState.threats.isNotEmpty() && notifyScan) {
                Notifications.notifyScanResult(context, scanState.threats.size)
            }
            scanJob = null
        }
    }

    private fun privilegedExecBlocking(command: String): Shell.Result {
        val root = rootInfo.rootGranted
        return if (root) {
            Shell.exec(command, 45_000L, asRoot = true)
        } else if (ShizukuUtils.isServiceReady()) {
            ShizukuUtils.exec(command)
        } else if (com.xb.tool.privilege.AdbShell.isConnected()) {
            com.xb.tool.privilege.AdbShell.exec(command, 45_000L)
        } else {
            Shell.Result(-1, "", "无特权通道")
        }
    }

    fun stopScan() {
        scanCancel.set(true)
        scanJob?.cancel()
        scanJob = null
        if (scanState.running) {
            scanState = scanState.copy(
                running = false,
                finished = true,
                durationMs = System.currentTimeMillis() - scanState.startedAt,
                error = "已手动停止"
            )
            LogBus.warn("已停止扫描")
        }
    }

    fun quarantine(threat: Threat, onDone: (Boolean) -> Unit) {
        scope.launch {
            busy = "正在隔离 ${threat.name}…"
            val ok = withContext(Dispatchers.IO) {
                val source = File(threat.path)
                val dir = File(context.filesDir, "quarantine").apply { mkdirs() }
                val dest = File(dir, "${System.currentTimeMillis()}_${source.name}")
                var success = runCatching {
                    source.copyTo(dest, overwrite = true)
                    source.delete()
                }.getOrDefault(false)
                if (!success) {
                    val cmd = "cp ${FileUtils.shellQuote(source.absolutePath)} ${FileUtils.shellQuote(dest.absolutePath)} && " +
                        "chmod 644 ${FileUtils.shellQuote(dest.absolutePath)} && rm -f ${FileUtils.shellQuote(source.absolutePath)}"
                    val result = privilegedExecBlocking(cmd)
                    success = result.success && dest.exists()
                }
                if (success) {
                    runCatching {
                        com.xb.tool.scan.QuarantineStore.recordMoved(context, threat, dest.absolutePath)
                    }
                    InterceptStore.add(
                        context,
                        InterceptRecord(
                            id = System.currentTimeMillis(),
                            time = System.currentTimeMillis(),
                            name = threat.name,
                            path = threat.path,
                            action = "已隔离",
                            signature = threat.name
                        )
                    )
                }
                success
            }
            scanState = scanState.copy(threats = scanState.threats.filterNot { it.path == threat.path })
            busy = null
            if (ok) {
                LogBus.ok("已隔离：${threat.name}")
                showMessage("已隔离 ${threat.name}")
            } else {
                LogBus.error("隔离失败：${threat.name}")
                showMessage("隔离失败，请检查权限")
            }
            onDone(ok)
        }
    }

    fun deleteThreat(threat: Threat, onDone: (Boolean) -> Unit) {
        scope.launch {
            busy = "正在删除 ${threat.name}…"
            val ok = withContext(Dispatchers.IO) {
                val file = File(threat.path)
                var success = runCatching { file.delete() }.getOrDefault(false)
                if (!success) {
                    val result = privilegedExecBlocking("rm -f ${FileUtils.shellQuote(threat.path)}")
                    success = result.success && !file.exists()
                }
                if (success) {
                    InterceptStore.add(
                        context,
                        InterceptRecord(
                            id = System.currentTimeMillis(),
                            time = System.currentTimeMillis(),
                            name = threat.name,
                            path = threat.path,
                            action = "已删除",
                            signature = threat.name
                        )
                    )
                }
                success
            }
            scanState = scanState.copy(threats = scanState.threats.filterNot { it.path == threat.path })
            busy = null
            if (ok) {
                LogBus.ok("已删除：${threat.name}")
                showMessage("已删除威胁文件")
            } else {
                LogBus.error("删除失败：${threat.path}")
                showMessage("删除失败，可能需要 Root / Shizuku")
            }
            onDone(ok)
        }
    }

    // -------------------------------------------------------------- settings
    fun isBatteryWhitelisted(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 保活前置条件。顺序：通知 → 完全后台行为 → 自启动 → 电池优化白名单。
     * 只返回“第一个”缺失项，确保引导是逐步的，而不是一次弹一堆。
     */
    fun keepAliveGate(extreme: Boolean): List<String> {
        if (!Notifications.areEnabled(context)) {
            return listOf("通知权限未开启：前台守护需要常驻通知")
        }
        // 以下只在「系统确实提供该入口」时才作为门槛，
        // 否则会给没有该设置的机型造成永远开不了的错觉。
        val colorOs = com.xb.tool.ui.screens.DeviceCompat.isColorOsFamily()
        if (extreme && colorOs && !isBatteryWhitelisted()) {
            return listOf("电池优化未设为「无限制」：请在系统设置中改为不限制")
        }
        return emptyList()
    }

    fun applyKeepAlive(value: Boolean): Boolean {
        if (keepAlive == value) return true
        if (value) {
            val reasons = keepAliveGate(false)
            if (reasons.isNotEmpty()) {
                LogBus.warn("后台保活未开启：${reasons.joinToString("；")}")
                showMessage(reasons.first())
                // 缺通知权限时直接拉起系统授权，不再弹自定义对话框
                requestNotificationPermission()
                return false
            }
        }
        keepAlive = value
        prefs.keepAlive = value
        if (value) {
            // 标准保活与极致保活互斥
            extremeKeepAlive = false
            prefs.extremeKeepAlive = false
            WatchdogService.stop(context)
        } else {
            extremeKeepAlive = false
            prefs.extremeKeepAlive = false
            WatchdogService.stop(context)
        }
        if (value) LogBus.ok("后台保活机制已开启") else LogBus.info("后台保活机制已关闭")
        syncGuard()
        return true
    }

    fun applyExtremeKeepAlive(value: Boolean): Boolean {
        if (extremeKeepAlive == value) return true
        if (value) {
            val reasons = keepAliveGate(true)
            if (reasons.isNotEmpty()) {
                LogBus.warn("极致保活未开启：${reasons.joinToString("；")}")
                showMessage(reasons.first())
                // 条件不足时进入「极致后台保活」页，在页面里逐项授权
                navigate(Screen.Extreme)
                return false
            }
        }
        extremeKeepAlive = value
        prefs.extremeKeepAlive = value
        if (value) {
            // 极致保活包含标准保活，但 UI 上标准开关显示为灰色关闭
            keepAlive = false
            prefs.keepAlive = false
            LogBus.ok("极致后台保活已开启：双进程互拉守护")
        } else {
            // 关闭时立刻把守护进程停掉：只改标志位的话，syncGuard 会走 stopGuard，
            // 但 :watchdog 进程可能已经自己在 15 秒周期里重新拉起 guard，
            // 用户看到的就是「关了还在跑」。显式 stop 一次最稳。
            runCatching { WatchdogService.stop(context) }
            LogBus.info("极致后台保活已关闭")
        }
        syncGuard()
        return true
    }

    fun refreshServiceStatus() {
        // 心跳走文件时间戳：:watchdog 是独立进程，SharedPreferences 的跨进程缓存不可靠。
        // 只有超过 65 秒没有心跳才判定为被系统清理。
        guardAlive = com.xb.tool.guard.Heartbeat.age(context, com.xb.tool.guard.Heartbeat.GUARD) <= 65_000L
        watchdogAlive = com.xb.tool.guard.Heartbeat.age(context, com.xb.tool.guard.Heartbeat.WATCHDOG) <= 65_000L
    }

    fun applyRealtime(value: Boolean): Boolean {
        if (value && !Notifications.areEnabled(context)) {
            LogBus.warn("实时检测未开启：通知权限未授予")
            showMessage("通知权限未开启：实时检测需要常驻通知")
            requestNotificationPermission()
            return false
        }
        realtime = value
        prefs.realtimeEnabled = value
        if (value) {
            LogBus.ok("实时病毒检测已开启，正在监控下载与安装")
            syncGuard()
            Notifications.notifyGuard(context, true)
        } else {
            LogBus.info("实时病毒检测已关闭")
            syncGuard()
        }
        return true
    }

    fun applyRootRemind(value: Boolean) {
        rootRemind = value
        prefs.rootRemind = value
    }

    fun applyNotifyScan(value: Boolean) {
        notifyScan = value
        prefs.notifyScan = value
    }

    fun applyNotifyIntercept(value: Boolean) {
        notifyIntercept = value
        prefs.notifyIntercept = value
    }

    fun applyNotifyUpdate(value: Boolean) {
        notifyUpdate = value
        prefs.notifyUpdate = value
    }

    fun applyNotifyPermission(value: Boolean) {
        notifyPermission = value
        prefs.notifyPermission = value
    }

    fun applyBackgroundUnrestricted(value: Boolean) {
        backgroundUnrestricted = value
        prefs.backgroundUnrestricted = value
        LogBus.info(if (value) "已标记「完全后台行为」" else "已取消「完全后台行为」标记")
    }

    /**
     * 从系统实际状态同步“完全后台行为”和“自启动”。
     * Android 没有统一的 API 读取这两个厂商开关，所以用可验证的启发式：
     * - 完全后台行为：是否忽略电池优化 且 AppOps RUN_IN_BACKGROUND 允许
     * - 自启动：是否存在 BOOT_COMPLETED 接收器且启用（本应用固定启用）
     * 读不到时保留用户手动确认的结果，不强行覆盖。
     */
    /**
     * 从系统真实状态同步保活相关权限。
     *
     * - 完全后台行为：用「是否忽略电池优化」作为可验证依据（ColorOS 系即电池优化无限制）。
     * - 应用自启动：Android 没有公开 API 能查询厂商自启动开关，因此**不伪造状态**，
     *   统一标记为「需手动确认」，由用户自行在系统设置里核对。
     */
    fun syncBackgroundStateFromSystem() {
        // 完全后台行为：电池优化白名单 + AppOps RUN_IN_BACKGROUND 都要放行，
        // 只看其中一个会在 ColorOS 上给出错误结论。
        val ignoring = isBatteryWhitelisted()
        val appOpsAllowed = isBackgroundOpAllowed()
        val unrestricted = ignoring && appOpsAllowed
        if (unrestricted != backgroundUnrestricted) {
            backgroundUnrestricted = unrestricted
            prefs.backgroundUnrestricted = unrestricted
            LogBus.info(
                if (unrestricted) "系统检测：完全后台行为已允许" else "系统检测：完全后台行为未允许"
            )
        }
        // 自启动：走 AutoStartDetector 的多路检测（系统后台限制 → 厂商 auto_start op →
        // run_in_background op → 开机接收器是否被 pm disable）。
        // 查不到就如实标「无法查询」，不再默认当成「未开启」。
        val auto = com.xb.tool.system.AutoStartDetector.detect(context)
        if (auto.state != autoStartState || auto.channel != autoStartChannel) {
            autoStartState = auto.state
            autoStartChannel = auto.channel
            LogBus.info("系统检测：应用自启动 ${auto.detail}")
        }
        val bootEnabled = auto.state == com.xb.tool.system.AutoStartDetector.State.ALLOWED
        if (bootEnabled != autoStartEnabled) {
            autoStartEnabled = bootEnabled
            prefs.autoStartEnabled = bootEnabled
        }
    }

    /** AppOps 层面的后台运行许可，ColorOS 等厂商的「完全后台行为」最终落在这里。 */
    private fun isBackgroundOpAllowed(): Boolean {
        return runCatching {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                ?: return@runCatching true
            val mode = ops.checkOpNoThrow(
                "android:run_in_background",
                android.os.Process.myUid(),
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(true)
    }

    /** 读应用自身 BOOT_COMPLETED 接收器是否处于启用状态（被 pm disable 时能查出来）。 */
    private fun isBootReceiverEnabled(): Boolean {
        return runCatching {
            val pm = context.packageManager
            val info = pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_RECEIVERS
            )
            val receivers = info.receivers ?: return@runCatching false
            receivers.any { it.enabled && it.name?.isNotBlank() == true }
        }.getOrDefault(autoStartEnabled)
    }

    /** 自启动状态只能由用户手动确认（系统无查询 API）。 */
    fun autoStartNeedsManualCheck(): Boolean = !autoStartEnabled

    /** 用户手动确认后重新校验，返回是否真的满足（用于开关回弹）。 */
    fun confirmBackgroundPermission(which: String): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return when (which) {
            "background" -> {
                val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                if (ignoring) applyBackgroundUnrestricted(true)
                ignoring
            }
            "autostart" -> {
                applyAutoStartEnabled(true)
                true
            }
            else -> false
        }
    }

    fun applyAutoStartEnabled(value: Boolean) {
        autoStartEnabled = value
        prefs.autoStartEnabled = value
        LogBus.info(if (value) "已标记「应用自启动」" else "已取消「应用自启动」标记")
    }

    fun updateNotificationPermission(granted: Boolean) {
        notificationsGranted = granted
        if (granted) {
            LogBus.ok("通知权限已授予")
            if (keepAlive && keepAliveGate(false).isEmpty()) syncGuard()
        }
    }

    fun refreshRuntimeFlags() {
        keepAlive = prefs.keepAlive
        realtime = prefs.realtimeEnabled
        extremeKeepAlive = prefs.extremeKeepAlive
        notifyScan = prefs.notifyScan
        notifyIntercept = prefs.notifyIntercept
        notifyUpdate = prefs.notifyUpdate
        agreementAccepted = prefs.agreementAccepted
        autoCheckUpdate = prefs.autoCheckUpdate
        autoCheckIntervalMin = prefs.autoCheckIntervalMin
        autoUpdateVirusDb = prefs.autoUpdateVirusDb
        notifyPermission = prefs.notifyPermission
        rootRemind = prefs.rootRemind
        notificationsGranted = Notifications.areEnabled(context)
        backgroundUnrestricted = prefs.backgroundUnrestricted
        autoStartEnabled = prefs.autoStartEnabled
        // 极致与标准互斥：如果两个都为 true（旧版本数据），以极致为准
        if (extremeKeepAlive && keepAlive) {
            keepAlive = false
            prefs.keepAlive = false
        }
        // 只校验通知这类“必须”的硬条件；完全后台/自启动/电池是引导项，
        // 不因为用户暂时没设就强行关闭，否则会出现“刚开就被关”的困惑。
        if (extremeKeepAlive && !Notifications.areEnabled(context)) {
            extremeKeepAlive = false
            prefs.extremeKeepAlive = false
            WatchdogService.stop(context)
        }
        if (keepAlive && !Notifications.areEnabled(context)) {
            keepAlive = false
            prefs.keepAlive = false
            if (!realtime && !extremeKeepAlive) stopGuard()
        }
        refreshServiceStatus()
    }

    fun refreshAllFilesAccess() {
        allFilesGranted = if (android.os.Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else true
    }

    private fun startGuard() {
        runCatching {
            GuardService.start(context)
            if (extremeKeepAlive) WatchdogService.start(context)
        }.onFailure {
            LogBus.warn("前台守护启动失败：${it.message}")
        }
        refreshServiceStatus()
        // 服务启动到写入心跳有几百毫秒延迟，稍后再刷新一次状态
        scope.launch {
            delay(1200)
            refreshServiceStatus()
        }
    }

    private fun stopGuard() {
        runCatching { GuardService.stop(context) }
        runCatching { WatchdogService.stop(context) }
        // 心跳文件是「服务还活着」的唯一判据，服务停止后必须立刻清掉。
        // 否则 refreshServiceStatus() 仍会读到 65 秒内的旧心跳，
        // 用户看到的就变成「已经关掉了，互拉守护还显示运行中」。
        runCatching {
            com.xb.tool.guard.Heartbeat.clear(context, com.xb.tool.guard.Heartbeat.GUARD)
        }
        runCatching {
            com.xb.tool.guard.Heartbeat.clear(context, com.xb.tool.guard.Heartbeat.WATCHDOG)
        }
        refreshServiceStatus()
    }

    private fun syncGuard() {
        if (keepAlive || extremeKeepAlive || realtime) {
            startGuard()
        } else {
            stopGuard()
        }
    }

    // ----------------------------------------------------------------- theme
    fun setThemeSeed(seed: String) {
        prefs.themeSeed = seed
        theme = theme.copy(seed = seed)
    }

    fun setDarkMode(mode: String) {
        prefs.darkMode = mode
        theme = theme.copy(darkMode = mode)
    }

    fun setFontScale(scale: Float) {
        val safe = scale.coerceIn(0.85f, 1.35f)
        prefs.fontScale = safe
        theme = theme.copy(fontScale = safe)
    }

    fun setFontFamily(family: String) {
        prefs.fontFamily = family
        theme = theme.copy(fontFamily = family)
    }

    fun setBackground(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "theme").apply { mkdirs() }
                val target = File(dir, "background_${System.currentTimeMillis()}.jpg")
                val ok = FileUtils.copyUriToFile(context, uri, target)
                if (ok) {
                    theme.backgroundPath?.let { old -> runCatching { File(old).delete() } }
                    theme.backgroundVideoPath?.let { old -> runCatching { File(old).delete() } }
                    prefs.backgroundPath = target.absolutePath
                    prefs.backgroundVideoPath = null
                    theme = theme.copy(
                        backgroundPath = target.absolutePath,
                        backgroundVideoPath = null
                    )
                }
                ok
            }
            onDone(result)
            showMessage(if (result) "背景图片已更新" else "设置背景图片失败")
        }
    }

    fun setBackgroundVideo(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "theme").apply { mkdirs() }
                val target = File(dir, "background_video_${System.currentTimeMillis()}.mp4")
                val ok = FileUtils.copyUriToFile(context, uri, target)
                if (ok) {
                    theme.backgroundPath?.let { old -> runCatching { File(old).delete() } }
                    theme.backgroundVideoPath?.let { old -> runCatching { File(old).delete() } }
                    prefs.backgroundVideoPath = target.absolutePath
                    prefs.backgroundPath = null
                    theme = theme.copy(
                        backgroundVideoPath = target.absolutePath,
                        backgroundPath = null
                    )
                }
                ok
            }
            onDone(result)
            showMessage(if (result) "动态背景已更新" else "设置视频背景失败")
        }
    }

    fun setBackgroundBlur(value: Float) {
        val safe = value.coerceIn(0f, 24f)
        prefs.backgroundBlur = safe
        theme = theme.copy(backgroundBlur = safe)
    }

    fun setBackgroundAlpha(value: Float) {
        val safe = value.coerceIn(0.15f, 1f)
        prefs.backgroundAlpha = safe
        theme = theme.copy(backgroundAlpha = safe)
    }

    fun setUiAnimation(value: Boolean) {
        prefs.uiAnimation = value
        theme = theme.copy(uiAnimation = value)
    }

    fun setGlassEffect(value: Boolean) {
        prefs.glassEffect = value
        theme = theme.copy(glassEffect = value)
    }

    fun setNavStyle(value: String) {
        prefs.navStyle = value
        theme = theme.copy(navStyle = value)
    }

    fun setBackgroundColor(value: String?) {
        prefs.backgroundColor = value
        theme = theme.copy(backgroundColor = value)
    }

    fun setGlassAlpha(value: Float) {
        val safe = value.coerceIn(0.15f, 0.95f)
        prefs.glassAlpha = safe
        theme = theme.copy(glassAlpha = safe)
    }

    fun clearBackground() {
        theme.backgroundPath?.let { runCatching { File(it).delete() } }
        theme.backgroundVideoPath?.let { runCatching { File(it).delete() } }
        prefs.backgroundPath = null
        prefs.backgroundVideoPath = null
        theme = theme.copy(backgroundPath = null, backgroundVideoPath = null)
        showMessage("已恢复默认背景")
    }

    fun resetTheme() {
        setThemeSeed("teal")
        setDarkMode("dark")
        setFontScale(1f)
        setFontFamily("default")
        setBackgroundBlur(0f)
        setBackgroundAlpha(1f)
        clearBackground()
    }

    // ------------------------------------------------------------ whitelist
    fun addWhitelist(packageName: String) {
        val next = whitelist + packageName
        prefs.setWhitelist(next)
        whitelist = next
        LogBus.info("已加入白名单：$packageName")
    }

    fun removeWhitelist(packageName: String) {
        val next = whitelist - packageName
        prefs.setWhitelist(next)
        whitelist = next
        LogBus.info("已移出白名单：$packageName")
    }

    fun toggleWhitelist(packageName: String) {
        if (packageName in whitelist) removeWhitelist(packageName) else addWhitelist(packageName)
    }

    // --------------------------------------------------------------- freeze
    fun setFrozen(packageName: String, freeze: Boolean, onDone: (Boolean) -> Unit) {
        scope.launch {
            busy = if (freeze) "正在冻结应用…" else "正在解冻应用…"
            val command = if (freeze) {
                "pm disable-user --user 0 $packageName"
            } else {
                "pm enable $packageName"
            }
            val result = withContext(Dispatchers.IO) {
                when {
                    rootInfo.rootGranted -> Shell.exec(command, 30_000L, asRoot = true)
                    shizukuGranted -> {
                        if (!ShizukuUtils.isServiceReady()) {
                            val connected = suspendCoroutine<Boolean> { cont ->
                                ShizukuUtils.bind(context) { cont.resume(it) }
                            }
                            if (!connected) Shell.Result(-1, "", "Shizuku 服务未连接")
                            else ShizukuUtils.exec(command)
                        } else ShizukuUtils.exec(command)
                    }
                    else -> Shell.Result(-1, "", "需要 Root 或 Shizuku 权限")
                }
            }
            busy = null
            val failed = result.out.contains("Exception") || result.out.contains("Error") ||
                result.err.contains("Exception") || result.err.contains("Error")
            val ok = result.success && !failed
            if (ok) {
                val next = if (freeze) frozenApps + packageName else frozenApps - packageName
                prefs.setFrozenApps(next)
                frozenApps = next
                LogBus.ok(if (freeze) "已冻结 $packageName" else "已解冻 $packageName")
            } else {
                LogBus.error("操作失败：${result.text.ifBlank { "无权限" }}")
                showMessage("操作失败，请确认已授权 Root / Shizuku")
            }
            onDone(ok)
        }
    }

    // ---------------------------------------------------------------- cache
    fun refreshCache() {
        if (cacheCalculating) return
        cacheCalculating = true
        scope.launch {
            val info = withContext(Dispatchers.IO) { CacheTools.measure(context) }
            cacheInfo = info
            cacheCalculating = false
        }
    }

    fun clearCache(onDone: (Long) -> Unit) {
        if (cacheCleaning) return
        cacheCleaning = true
        scope.launch {
            val freed = withContext(Dispatchers.IO) { CacheTools.clear(context) }
            val info = withContext(Dispatchers.IO) { CacheTools.measure(context) }
            cacheInfo = info
            cacheCleaning = false
            LogBus.ok("缓存清理完成，释放 ${FileUtils.formatBytes(freed)}")
            showMessage("已释放 ${FileUtils.formatBytes(freed)}")
            onDone(freed)
        }
    }

    fun refreshPrivilegedCaches() {
        if (appCachesScanning) return
        appCachesScanning = true
        scope.launch {
            val result = privilegedExec("du -sk /data/user/0/*/cache /data/data/*/cache 2>/dev/null")
            if (!result.success || result.out.isBlank()) {
                appCaches = emptyList()
                appCachesScanning = false
                showMessage("无法读取其他应用缓存：需要 Root 权限")
                return@launch
            }
            val whitelist = prefs.whitelist()
            val parsed = linkedMapOf<String, Long>()
            val regex = Regex("""^(\d+)\s+/data/(?:user/0|data)/([^/]+)/cache/?$""")
            result.out.lineSequence().forEach { line ->
                val match = regex.find(line.trim()) ?: return@forEach
                val size = match.groupValues[1].toLongOrNull() ?: return@forEach
                val pkg = match.groupValues[2]
                if (pkg == context.packageName || pkg in whitelist) return@forEach
                parsed[pkg] = maxOf(parsed[pkg] ?: 0L, size * 1024L)
            }
            appCaches = withContext(Dispatchers.IO) {
                parsed.map { (pkg, size) ->
                    AppCacheEntry(pkg, AppRepository.labelOf(context, pkg), size)
                }.filter { it.size > 0 }.sortedByDescending { it.size }
            }
            appCachesScanning = false
            LogBus.info("深度缓存扫描：${appCaches.size} 个应用，共 ${FileUtils.formatBytes(appCaches.sumOf { it.size })}")
        }
    }

    fun clearPrivilegedCaches(onDone: (Boolean, String) -> Unit) {
        if (appCachesCleaning) return
        appCachesCleaning = true
        scope.launch {
            val command = if (rootInfo.rootGranted) {
                "rm -rf /data/user/0/*/cache/* 2>/dev/null; rm -rf /data/data/*/cache/* 2>/dev/null; echo xb_cache_done"
            } else {
                "pm trim-caches 999999M; echo xb_cache_done"
            }
            val result = privilegedExec(command)
            val ok = result.out.contains("xb_cache_done")
            val message = if (ok) {
                "深度缓存清理完成" + if (rootInfo.rootGranted) "（Root 模式）" else "（Shizuku 模式）"
            } else {
                "深度清理失败：${result.text.ifBlank { "需要 Root / Shizuku 权限" }}"
            }
            if (ok) LogBus.ok(message) else LogBus.error(message)
            refreshCache()
            refreshPrivilegedCaches()
            appCachesCleaning = false
            onDone(ok, message)
        }
    }

    fun refreshRunningApps() {
        if (runningAppsScanning) return
        runningAppsScanning = true
        scope.launch {
            val result = privilegedExec("ps -A -o PID,RSS,NAME 2>/dev/null")
            if (!result.success || result.out.isBlank()) {
                runningApps = emptyList()
                runningAppsScanning = false
                showMessage("无法读取进程列表：需要 Root / Shizuku 权限")
                return@launch
            }
            val whitelist = prefs.whitelist()
            val pm = context.packageManager
            val aggregate = linkedMapOf<String, RunningAppEntry>()
            result.out.lineSequence().drop(1).forEach { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 3)
                if (parts.size < 3) return@forEach
                val pid = parts[0].toIntOrNull() ?: return@forEach
                val rssKb = parts[1].toLongOrNull() ?: return@forEach
                val rawName = parts[2].substringBefore(' ').substringBefore(':')
                if (rawName.isBlank() || rawName.startsWith("[") || rawName.contains("/")) return@forEach
                val appInfo = runCatching { pm.getApplicationInfo(rawName, 0) }.getOrNull() ?: return@forEach
                if (rawName == context.packageName || rawName in whitelist) return@forEach
                val system = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (system) return@forEach
                val existing = aggregate[rawName]
                aggregate[rawName] = RunningAppEntry(
                    packageName = rawName,
                    label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(rawName),
                    pid = existing?.pid ?: pid,
                    rssKb = (existing?.rssKb ?: 0L) + rssKb,
                    isSystem = false
                )
            }
            runningApps = aggregate.values.sortedByDescending { it.rssKb }
            runningAppsScanning = false
            LogBus.info("进程扫描：${runningApps.size} 个可清理后台应用")
        }
    }

    fun cleanRunningApps(packages: Set<String>, onDone: (Boolean, String) -> Unit) {
        if (runningAppsCleaning) return
        if (packages.isEmpty()) {
            onDone(false, "没有选择可清理的应用")
            return
        }
        runningAppsCleaning = true
        scope.launch {
            var okCount = 0
            packages.forEach { pkg ->
                val result = privilegedExec("am kill ${FileUtils.shellQuote(pkg)}")
                if (!result.text.contains("Error") && !result.text.contains("Exception")) okCount++
            }
            val message = if (okCount > 0) {
                "已结束后台进程：$okCount 个应用"
            } else {
                "清理失败：需要 Root / Shizuku 权限"
            }
            LogBus.info(message)
            runningAppsCleaning = false
            refreshRunningApps()
            onDone(okCount > 0, message)
        }
    }

    // --------------------------------------------------------------- update
    fun currentVersionCode(): Int =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(270)

    fun currentVersionName(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.5.0"
        }.getOrDefault("1.2.1")

    fun saveUpdateManifestUrl(url: String) {
        prefs.updateManifestUrl = url
        updateManifestUrl = prefs.updateManifestUrl
        LogBus.info("更新源已保存：${prefs.updateManifestUrl}")
        showMessage("更新源已保存")
    }

    fun resetUpdateManifestUrl() {
        prefs.updateManifestUrl = Prefs.DEFAULT_UPDATE_URL
        updateManifestUrl = prefs.updateManifestUrl
        showMessage("已恢复默认更新源")
    }

    fun checkUpdates(onResult: (String) -> Unit) {
        if (updateChecking) return
        updateChecking = true
        updateCheckResult = null
        scope.launch {
            var error: String? = null
            val candidates = listOf(
                prefs.updateManifestUrl,
                Prefs.FALLBACK_UPDATE_URL
            ).filter { it.isNotBlank() }.distinct()
            var manifest: UpdateManifest? = null
            var lastError: String? = null
            for (url in candidates) {
                try {
                    manifest = UpdateRepository.fetchRemote(url)
                    break
                } catch (t: Throwable) {
                    lastError = t.message ?: "网络不可用"
                }
            }
            if (manifest == null) {
                error = lastError ?: "网络不可用"
                manifest = UpdateRepository.localManifest(context)
            }
            applyManifest(manifest, error)
            lastUpdateCheck = System.currentTimeMillis()
            prefs.lastUpdateCheck = lastUpdateCheck
            updateChecking = false
            val result = buildString {
                if (error != null) {
                    append("检查失败：").append(error).append("。当前使用本地公告。")
                } else {
                    when {
                        appUpdateInfo != null ->
                            append(
                                "发现新版本 v${appUpdateInfo?.versionName}（${appUpdateInfo?.versionCode}），" +
                                    if (appUpdateInfo?.url.isNullOrBlank()) "请到 Releases 手动下载"
                                    else "可下载更新"
                            )
                        dbUpdateInfo != null ->
                            append("发现新病毒库 v${dbUpdateInfo?.version}，可在本页更新")
                        else ->
                            append("已是最新版本 v${currentVersionName()}，病毒库 v${database.version}")
                    }
                }
            }
            updateCheckResult = result
            if (error != null) LogBus.warn("更新检查：$result") else LogBus.ok("更新检查：$result")
            onResult(result)
        }
    }

    fun checkDatabaseUpdate(onResult: (String) -> Unit) {
        if (dbChecking) return
        dbChecking = true
        dbCheckResult = null
        scope.launch {
            var error: String? = null
            val manifest = try {
                UpdateRepository.fetchRemote(prefs.updateManifestUrl)
            } catch (t: Throwable) {
                error = t.message ?: "网络不可用"
                UpdateRepository.localManifest(context)
            }
            applyManifest(manifest, error)
            prefs.lastDbCheck = System.currentTimeMillis()
            dbChecking = false
            val result = when {
                error != null ->
                    "无法检查病毒库：$error\n当前病毒库 v${database.version}（${database.signatureCount} 条特征）"
                dbUpdateInfo != null ->
                    "发现新病毒库 v${dbUpdateInfo?.version}，请在更新页点击更新"
                else ->
                    "已是最新病毒库 v${database.version}（${database.signatureCount} 条特征）"
            }
            dbCheckResult = result
            LogBus.ok("病毒库检查：$result")
            onResult(result)
        }
    }

    fun checkAppUpdate(onResult: (String) -> Unit) = checkUpdates(onResult)

    fun updateVirusDatabase(onDone: (Boolean, String) -> Unit) {
        val info = dbUpdateInfo
        if (info == null) {
            onDone(false, "没有可用的远程病毒库更新，请先检查更新")
            return
        }
        if (info.url.isBlank()) {
            onDone(false, "远程病毒库缺少下载地址")
            return
        }
        if (dbUpdating) return
        dbUpdating = true
        scope.launch {
            var message: String
            var success = false
            var usedSource = ""
            try {
                var raw = ""
                var lastError: Throwable? = null
                for (source in info.allSources()) {
                    try {
                        raw = UpdateRepository.fetchVirusDb(source.url, source.sha256)
                        usedSource = source.name
                        break
                    } catch (t: Throwable) {
                        lastError = t
                        LogBus.warn("病毒库源 ${source.name} 失败：${t.message}，尝试下一个")
                    }
                }
                if (raw.isBlank()) {
                    val cached = File(context.filesDir, VirusDatabase.REMOTE_FILE)
                    if (cached.isFile && cached.length() > 0) {
                        message = "所有在线源暂时不可用（${lastError?.message ?: "网络失败"}），已保留离线特征库 v${database.version}"
                        LogBus.warn(message)
                        dbUpdating = false
                        updateCheckResult = message
                        onDone(false, message)
                        return@launch
                    }
                    throw lastError ?: IllegalStateException("没有可用的病毒库下载源")
                }
                val parsed = VirusDatabase.merge(
                    VirusDatabase.builtIn(context),
                    VirusDatabase.parse(raw, "在线更新")
                        ?: throw IllegalStateException("病毒库格式校验失败")
                )
                File(context.filesDir, VirusDatabase.REMOTE_FILE).writeText(raw)
                File(context.filesDir, "virus_db_backup.json").writeText(raw)
                rememberDbHistory(parsed, usedSource.ifBlank { "在线更新" })
                database = parsed
                engine = ScanEngine(context, parsed)
                prefs.dbVersion = parsed.version
                prefs.lastDbCheck = System.currentTimeMillis()
                dbUpdateInfo = null
                restartGuardForReload()
                success = true
                message = "病毒库已更新到 v${parsed.version} · ${parsed.signatureCount} 条特征" +
                    if (usedSource.isNotBlank()) "（来源：$usedSource）" else ""
                LogBus.ok(message)
            } catch (t: Throwable) {
                message = "病毒库更新失败：${t.message}，原有离线特征库仍保留"
                LogBus.error(message)
            }
            dbUpdating = false
            updateCheckResult = message
            onDone(success, message)
        }
    }

    fun saveVirusDbDirectUrl(url: String) {
        virusDbDirectUrl = url.trim()
        prefs.virusDbDirectUrl = virusDbDirectUrl
    }

    fun updateVirusDatabaseFromUrl(url: String, onDone: (Boolean, String) -> Unit) {
        val target = url.trim()
        if (target.isBlank() || !target.startsWith("http")) {
            onDone(false, "请填写以 http(s) 开头的病毒库直链")
            return
        }
        if (dbUpdating) return
        dbUpdating = true
        dbCheckResult = null
        scope.launch {
            var message: String
            var success = false
            try {
                val raw = UpdateRepository.fetchVirusDb(target, "")
                val parsed = VirusDatabase.merge(
                    VirusDatabase.builtIn(context),
                    VirusDatabase.parse(raw, "直链更新")
                        ?: throw IllegalStateException("未识别到 JSON 或 SHA-256 哈希列表")
                )
                File(context.filesDir, VirusDatabase.REMOTE_FILE).writeText(raw)
                database = parsed
                engine = ScanEngine(context, parsed)
                prefs.dbVersion = parsed.version
                prefs.lastDbCheck = System.currentTimeMillis()
                prefs.virusDbDirectUrl = target
                virusDbDirectUrl = target
                dbUpdateInfo = null
                restartGuardForReload()
                success = true
                message = "病毒库已从直链更新：v${parsed.version} · ${parsed.signatureCount} 条特征"
                LogBus.ok(message)
            } catch (t: Throwable) {
                message = "直链病毒库更新失败：${t.message}"
                LogBus.error(message)
            }
            dbUpdating = false
            dbCheckResult = message
            updateCheckResult = message
            onDone(success, message)
        }
    }

    fun downloadAppUpdate(onDone: (Boolean, String) -> Unit) {        val info = appUpdateInfo
        if (info == null || info.url.isBlank()) {
            onDone(false, "没有可下载的新版本")
            return
        }
        if (apkDownloading) return
        apkDownloading = true
        apkProgress = 0f
        apkStatus = "准备下载…"
        scope.launch {
            try {
                val dir = File(context.externalCacheDir ?: context.cacheDir, "updates")
                val target = File(dir, "XB工具-${info.versionName}.apk")
                val file = UpdateRepository.downloadApk(
                    info.url,
                    info.sha256,
                    target
                ) { progress, status ->
                    apkProgress = progress
                    apkStatus = status
                }
                apkStatus = "下载完成，正在打开安装器…"
                FileUtils.installApk(context, file)
                LogBus.ok("安装包已下载：${file.name}")
                onDone(true, "安装包已下载并打开安装器")
            } catch (t: Throwable) {
                val message = "安装包下载失败：${t.message}"
                apkStatus = message
                LogBus.error(message)
                onDone(false, message)
            } finally {
                apkDownloading = false
            }
        }
    }

    fun markAnnouncementsSeen() {
        val latest = announcements.firstOrNull()?.id ?: return
        lastAnnouncementSeen = latest
        prefs.lastAnnouncementSeen = latest
    }

    fun hasUnreadAnnouncements(): Boolean {
        val latest = announcements.firstOrNull()?.id ?: return false
        return lastAnnouncementSeen != latest
    }

    private fun applyManifest(manifest: UpdateManifest, error: String?) {
        manifestSource = if (error == null) manifest.source else "本地回退"
        manifestError = error
        // 线上清单可能还没跟上当前版本（作者忘了推仓库是常态），
        // 这时把内置清单里「线上没有的那几条」补到最前面，
        // 保证当前版本的发布说明一定看得到，而不是整页旧公告。
        val localManifest = UpdateRepository.localManifest(context)
        val merged = mergeAnnouncements(manifest.announcements, localManifest.announcements)
        if (merged.isNotEmpty()) {
            announcements = merged
            cacheAnnouncements(merged)
        }
        val mergedLog = mergeChangelog(manifest.changelog, localManifest.changelog)
        if (mergedLog.isNotEmpty()) {
            changelog = mergedLog
        }
        val current = currentVersionCode()
        // 只要线上 versionCode 更大就提示有新版本；url 为空时提示手动下载，
        // 不再因为缺少下载地址而误报“已是最新”。
        appUpdateInfo = manifest.app?.takeIf { it.versionCode > current }
        dbUpdateInfo = manifest.virusDb?.takeIf {
            (it.url.isNotBlank() || it.mirrors.isNotEmpty()) && versionGreater(it.version, database.version)
        }
    }

    /** 线上公告 + 内置清单里线上缺失的条目（内置的排前面，重要的排最前）。 */
    private fun mergeAnnouncements(
        remote: List<Announcement>,
        local: List<Announcement>
    ): List<Announcement> {
        val seen = remote.map { it.id }.toHashSet()
        val extra = local.filter { it.id !in seen }
        return (extra + remote).sortedByDescending { it.important }
    }

    /** 版本更新内容同理：内置清单里线上没有的版本补上，再按版本号排序。 */
    private fun mergeChangelog(
        remote: List<com.xb.tool.network.ChangelogEntry>,
        local: List<com.xb.tool.network.ChangelogEntry>
    ): List<com.xb.tool.network.ChangelogEntry> {
        val seen = remote.map { it.version }.toHashSet()
        return local.filter { it.version !in seen } + remote
    }

    private fun rememberDbHistory(db: VirusDatabase, mirror: String) {
        val entry = com.xb.tool.network.VirusDbHistoryEntry(
            version = db.version,
            source = db.source,
            signatures = db.signatureCount,
            time = System.currentTimeMillis(),
            mirror = mirror
        )
        val list = (listOf(entry) + dbHistory).distinctBy { it.version }.take(8)
        dbHistory = list
        prefs.dbHistory = com.xb.tool.util.JsonCodec.encodeDbHistory(list)
    }

    private fun loadDbHistory() {
        dbHistory = com.xb.tool.util.JsonCodec.decodeDbHistory(prefs.dbHistory)
    }

    private fun versionGreater(candidate: String, current: String): Boolean {
        val a = candidate.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun restartGuardForReload() {
        if (keepAlive || realtime) {
            runCatching {
                GuardService.stop(context)
                GuardService.start(context)
            }
        }
    }

    private fun cacheAnnouncements(list: List<Announcement>) {
        prefs.announcementCacheVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("date", it.date)
                    put("tag", it.tag)
                    put("content", it.content)
                    put("important", it.important)
                }
            )
        }
        prefs.announcementsJson = array.toString()
    }

    private fun readCachedAnnouncements(): List<Announcement> {
        val raw = prefs.announcementsJson ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                Announcement(
                    id = item.optString("id", "a-$i"),
                    title = item.optString("title", "公告"),
                    date = item.optString("date", ""),
                    tag = item.optString("tag", "公告"),
                    content = item.optString("content", ""),
                    important = item.optBoolean("important", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------ privileges
    fun openShizukuApp() {
        Intents.openShizuku(context)
    }

    fun requiresReminder(): Boolean = rootRemind

    fun describePrivilege(): String = when {
        rootInfo.rootGranted -> "已授权 Root · ${rootInfo.managerName}"
        shizukuGranted -> "已授权 Shizuku"
        shizukuRunning -> "Shizuku 运行中 · 未授权"
        else -> "未授权"
    }
}
