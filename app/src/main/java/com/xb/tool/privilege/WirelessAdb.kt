package com.xb.tool.privilege

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.xb.tool.root.Shell
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalServices
import org.bouncycastle.x509.X509V1CertificateGenerator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/**
 * ADB 无线调试（Android 11+）配对与连接。
 *
 * 为什么需要它：
 *  Shizuku / Stellar 都要求先通过 ADB 获得一次特权授权。没有 Root 的设备上，
 *  唯一合法途径就是「无线调试」——用户在系统设置里打开它并出示 6 位配对码，
 *  本应用通过 SPAKE2 完成配对，之后就能像 adb 一样以 shell 身份执行命令。
 *
 * 实现要点：
 *  - mDNS 发现 `_adb-tls-pairing._tcp`（配对端口）与 `_adb-tls-connect._tcp`（连接端口）
 *  - 配对使用 libadb-android 的 PairingConnectionCtx（SPAKE2 + TLS keying material）
 *  - 连接使用 RSA 2048 密钥 + 自签证书，与桌面 adb 的密钥格式一致
 */
object WirelessAdb {

    private const val SP_NAME = "xb_wireless_adb"
    private const val KEY_PRIVATE = "private_key"
    private const val KEY_CERT = "certificate"
    private const val KEY_DEVICE_NAME = "device_name"

    data class Discovered(val host: String, val port: Int, val name: String)

    data class PairResult(
        val ok: Boolean,
        val message: String,
        val host: String = "",
        val port: Int = 0
    )

    data class ConnectResult(
        val ok: Boolean,
        val message: String,
        val shellOutput: String = ""
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- 密钥

    private var cachedPrivate: PrivateKey? = null
    private var cachedCert: X509Certificate? = null

    /** 取（或首次生成）本机 ADB 密钥对。与 adbkey/adbkey.pub 等价。 */
    internal fun loadKeyPair(context: Context): Pair<PrivateKey, X509Certificate> = loadOrCreateKey(context)

    private fun loadOrCreateKey(context: Context): Pair<PrivateKey, X509Certificate> {
        cachedPrivate?.let { priv -> cachedCert?.let { cert -> return priv to cert } }
        val sp = prefs(context)
        val privB64 = sp.getString(KEY_PRIVATE, null)
        val certB64 = sp.getString(KEY_CERT, null)
        if (!privB64.isNullOrBlank() && !certB64.isNullOrBlank()) {
            runCatching {
                val factory = KeyFactory.getInstance("RSA")
                val priv = factory.generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(privB64, Base64.NO_WRAP))
                )
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(Base64.decode(certB64, Base64.NO_WRAP).inputStream())
                    as X509Certificate
                cachedPrivate = priv
                cachedCert = cert
                return priv to cert
            }
        }
        val generated = generateKey()
        sp.edit()
            .putString(KEY_PRIVATE, Base64.encodeToString(generated.first.encoded, Base64.NO_WRAP))
            .putString(KEY_CERT, Base64.encodeToString(generated.second.encoded, Base64.NO_WRAP))
            .apply()
        cachedPrivate = generated.first
        cachedCert = generated.second
        return generated
    }

    /** 生成 RSA 2048 私钥 + 自签证书（ADB 只用到公钥，证书仅作承载）。 */
    private fun generateKey(): Pair<PrivateKey, X509Certificate> {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()

        val now = System.currentTimeMillis()
        val certGen = X509V1CertificateGenerator()
        certGen.setSerialNumber(BigInteger.valueOf(now))
        certGen.setIssuerDN(javax.security.auth.x500.X500Principal("CN=XB Tool"))
        certGen.setNotBefore(Date(now - 24L * 60 * 60 * 1000))
        certGen.setNotAfter(Date(now + 30L * 365 * 24 * 60 * 60 * 1000))
        certGen.setSubjectDN(javax.security.auth.x500.X500Principal("CN=XB Tool"))
        certGen.setPublicKey(pair.public)
        // 注意：不能用 "SHA256WithRSAEncryption" + BC。Android 平台自带的 BC 是裁剪版，
        // 不实现 Signature，会抛 "no such algorithm ... for provider BC"。
        // 这里交给默认 Provider（Conscrypt/AndroidOpenSSL）签名，算法名用标准写法。
        certGen.setSignatureAlgorithm("SHA256withRSA")
        val cert = certGen.generate(pair.private)
        return pair.private to cert
    }

    fun deviceName(context: Context): String {
        val sp = prefs(context)
        sp.getString(KEY_DEVICE_NAME, null)?.let { return it }
        val name = "XB-Tool@${android.os.Build.MODEL}"
        sp.edit().putString(KEY_DEVICE_NAME, name).apply()
        return name
    }

    // ------------------------------------------------------------ 配对

    /**
     * 用 6 位配对码与指定 host:port 完成配对。
     * 调用前应先用 [discoverPairing] 拿到系统当前广播的配对端口。
     */
    fun pair(
        context: Context,
        host: String,
        port: Int,
        code: String,
        timeoutMs: Long = 30_000L
    ): PairResult {
        if (code.length != 6 || code.any { !it.isDigit() }) {
            return PairResult(false, "配对码应为 6 位数字")
        }
        return runCatching {
            val (priv, cert) = loadOrCreateKey(context)
            val name = deviceName(context)
            // 配对尝试顺序：
            //  1. 自研实现 + 出示客户端证书（AOSP adb 的做法）
            //  2. 自研实现 + 不出示证书（少数 ROM 的 adbd 不索要客户端证书）
            //  3. libadb-android 官方实现（状态机写法不同，兜底）
            // 每一路都写日志，用户能看到到底哪一步失败。
            val attempts = listOf(true, false)
            var lastStage = ""
            var lastMessage = ""
            for (withCert in attempts) {
                val result = AdbPairer.pair(
                    context = context,
                    host = host,
                    port = port,
                    code = code,
                    privateKey = priv,
                    certificate = cert,
                    deviceName = name,
                    timeoutMs = timeoutMs.toInt(),
                    useClientCert = withCert,
                    log = { line -> PairLog.info(context, line) }
                )
                if (result.ok) {
                    rememberPaired(context, host, port)
                    return@runCatching PairResult(true, "配对成功", host, port)
                }
                lastStage = result.stage
                lastMessage = result.message
                PairLog.warn(
                    context,
                    "配对尝试 ${if (withCert) "A" else "B"} 失败（${result.stage}：${result.message}）"
                )
            }

            // 自研实现两种配置都失败，回退到 libadb-android 的官方实现。
            PairLog.warn(context, "自研实现失败，改用库实现重试")
            val fallback = runCatching {
                val ctx = io.github.muntashirakon.adb.PairingConnectionCtx(
                    host, port, code.toByteArray(Charsets.UTF_8), priv, cert, name
                )
                try {
                    ctx.start()
                    true
                } finally {
                    runCatching { ctx.close() }
                }
            }
            val ok = fallback.getOrElse {
                PairLog.error(context, "库实现同样失败：${it.message ?: it.javaClass.simpleName}")
                false
            }
            if (ok) {
                PairLog.ok(context, "库实现配对成功")
                rememberPaired(context, host, port)
                PairResult(true, "配对成功", host, port)
            } else {
                PairResult(false, "$lastStage：$lastMessage")
            }
        }.getOrElse { t ->
            PairResult(false, humanError(t))
        }
    }

    private fun rememberPaired(context: Context, host: String, port: Int) {
        prefs(context).edit()
            .putString("last_pair_host", host)
            .putInt("last_pair_port", port)
            .putLong("last_pair_at", System.currentTimeMillis())
            .apply()
    }

    // ------------------------------------------------------------ 连接

    /**
     * 连接已配对的无线调试端口并执行一条命令验证。
     */
    fun connectAndExec(
        context: Context,
        host: String,
        port: Int,
        command: String = "id",
        timeoutMs: Long = 20_000L
    ): ConnectResult {
        return runCatching {
            val (priv, cert) = loadOrCreateKey(context)
            val connection = AdbConnection.create(host, port, priv, cert, android.os.Build.VERSION.SDK_INT)
            try {
                val connected = connection.connect(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS, true)
                if (!connected) {
                    return@runCatching ConnectResult(false, "连接被拒绝（可能尚未配对）")
                }
                val stream = connection.open(LocalServices.SHELL, command)
                val out = readStream(stream)
                ConnectResult(true, "已连接并执行成功", out)
            } finally {
                runCatching { connection.close() }
            }
        }.getOrElse { t ->
            ConnectResult(false, humanError(t))
        }
    }

    /** 读空一个 shell 流。 */
    private fun readStream(stream: AdbStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        val input: InputStream = stream.openInputStream()
        try {
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                buffer.write(chunk, 0, read)
                if (buffer.size() > 256 * 1024) break
            }
        } catch (_: Throwable) {
            // 流关闭属正常结束
        }
        return buffer.toString("UTF-8").trim()
    }

    private fun humanError(t: Throwable): String {
        val raw = t.message ?: t.javaClass.simpleName
        return when {
            raw.contains("timed out", true) || raw.contains("Timeout", true) ->
                "超时：请确认已停留在「使用配对码配对设备」页面"
            raw.contains("Connection refused", true) ->
                "端口未开放：请重新打开无线调试页面后再试"
            raw.contains("Exchanging message", true) ->
                "配对码不正确或已过期"
            raw.contains("Could not exchange peer info", true) ->
                "配对握手失败，请重试"
            else -> raw
        }
    }

    // ------------------------------------------------------------ 发现

    /** 发现系统当前广播的配对服务（需要先打开「使用配对码配对设备」页面）。 */
    fun discoverPairing(context: Context, timeoutMs: Long = 8_000L): List<Discovered> =
        discover(context, "_adb-tls-pairing._tcp", timeoutMs)

    /** 发现已开启的无线调试连接端口。 */
    fun discoverConnect(context: Context, timeoutMs: Long = 8_000L): List<Discovered> =
        discover(context, "_adb-tls-connect._tcp", timeoutMs)

    private fun discover(context: Context, type: String, timeoutMs: Long): List<Discovered> {
        val nsd = context.getSystemService(Context.NSD_SERVICE)
            as? android.net.nsd.NsdManager ?: return emptyList()
        val found = mutableListOf<Discovered>()
        val latch = java.util.concurrent.CountDownLatch(1)
        val listener = object : android.net.nsd.NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                latch.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: android.net.nsd.NsdServiceInfo?) {}
            override fun onServiceFound(serviceInfo: android.net.nsd.NsdServiceInfo?) {
                val info = serviceInfo ?: return
                runCatching {
                    nsd.resolveService(info, object : android.net.nsd.NsdManager.ResolveListener {
                        override fun onResolveFailed(i: android.net.nsd.NsdServiceInfo?, errorCode: Int) {}
                        override fun onServiceResolved(i: android.net.nsd.NsdServiceInfo?) {
                            val resolved = i ?: return
                            val host = resolved.host?.hostAddress ?: return
                            synchronized(found) {
                                found += Discovered(host, resolved.port, resolved.serviceName)
                            }
                            latch.countDown()
                        }
                    })
                }
            }
        }
        runCatching {
            nsd.discoverServices(type, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
        runCatching { nsd.stopServiceDiscovery(listener) }
        return found.distinctBy { "${it.host}:${it.port}" }
    }

    // ------------------------------------------------------------ 状态

    fun lastPairHost(context: Context): String =
        prefs(context).getString("last_pair_host", "") ?: ""

    fun lastPairPort(context: Context): Int = prefs(context).getInt("last_pair_port", 0)

    fun lastPairAt(context: Context): Long = prefs(context).getLong("last_pair_at", 0L)

    /**
     * 当前无线调试连接端口。
     *
     * 注意：`service.adb.tls.port` 只在无线调试处于「已开启」状态时才有值，
     * 部分 ROM（尤其国产定制）压根不写这个属性，直接读会得到空/0。
     * 这里按可靠性从高到低多路探测，任何一路拿到正数就用。
     */
    fun tlsPort(): Int = tlsPortCandidates().firstOrNull() ?: 0

    /**
     * 无线调试「连接端口」的全部候选。
     *
     * 为什么是一个列表而不是一个值：
     *  连接端口与配对端口不同，不同 ROM 暴露它的方式也不一样。属性读不到时
     *  还可以从 adbd 的监听套接字里找，多给几个候选让上层逐个尝试，
     *  总比直接判定「端口为 0，配对失败」要好。
     */
    fun tlsPortCandidates(): List<Int> {
        val result = mutableListOf<Int>()
        val propKeys = listOf(
            "service.adb.tls.port",
            "persist.adb.tls_server.port",
            "service.adb.tcp.port",
            "persist.adb.tcp.port"
        )
        // 刚打开无线调试时属性可能还没写入，重试一次（间隔 400ms）
        repeat(2) { attempt ->
            for (key in propKeys) {
                readPropInt(key)?.let { if (it in 1..65535) result += it }
            }
            // adb_wifi_port 是 settings 里的值，部分 ROM 只写这里
            readSettingsInt("adb_wifi_port")?.let { if (it in 1..65535) result += it }
            if (result.isNotEmpty()) return result.distinct()
            if (attempt == 0) Thread.sleep(400)
        }
        // 属性全空：退一步看内核里 adbd 的监听端口
        result += scanAdbdListenPorts()
        return result.distinct()
    }

    /**
     * 直接读系统属性。
     *
     * 这里刻意不走 [Shell]：Shell 依赖 Root / Shizuku / 无线调试，而「读连接端口」
     * 恰恰是建立无线调试连接之前要做的事 —— 用 Shell 读会形成死锁（没通道 → 读不到
     * 端口 → 连不上 → 永远没通道），旧版本正是因此一直拿到 0 并报「尚未配对」。
     * getprop 是全局可执行文件，普通应用直接 ProcessBuilder 调用即可。
     */
    private fun readPropInt(key: String): Int? = runCatching {
        val process = ProcessBuilder("getprop", key).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        text.toIntOrNull()
    }.getOrNull()

    private fun readSettingsInt(key: String): Int? = runCatching {
        val process = ProcessBuilder("settings", "get", "global", key).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        text.toIntOrNull()
    }.getOrNull()

    /**
     * 从 /proc/net/tcp{,6} 里找监听中的高位端口。
     *
     * 部分 ROM 不写 `service.adb.tls.port`，但 adbd 确实在监听。这里只能拿到
     * 「某个进程在监听的高位端口」，无法 100% 确认是 adbd，所以只作为候选，
     * 由上层逐个尝试连接（连不上会自动跳到下一个，不会误判成功）。
     */
    private fun scanAdbdListenPorts(): List<Int> {
        // Android 10+ 普通应用读 /proc/net/tcp 会拿到空内容，这里只是最后兜底；
        // 真正有效的是 readPropInt / readSettingsInt 那两条路。
        val ports = mutableListOf<Int>()
        listOf("/proc/net/tcp", "/proc/net/tcp6").forEach { path ->
            runCatching {
                File(path).readLines().drop(1).forEach { line ->
                    val parts = line.trim().split(Regex("[ ]+"))
                    if (parts.size < 4) return@forEach
                    if (parts[3] != "0A") return@forEach
                    val port = parts[1].substringAfterLast(':').toIntOrNull(16) ?: return@forEach
                    if (port in 1025..65535 && port != 5037) ports += port
                }
            }
        }
        return ports.distinct()
    }

    /** 本机 WLAN IPv4 地址（用于免 mDNS 直连）。 */
    fun localWifiIp(): String = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull()?.hostAddress ?: ""
    }.getOrDefault("")

    /**
     * 兜底：直接用本机 IP + 系统属性里的 TLS 端口连接（跳过 mDNS）。
     * 部分设备（模拟器、部分定制 ROM）mDNS 广播不可见，这条路径更可靠。
     */
    fun directEndpoint(): Pair<String, Int>? {
        val ip = localWifiIp()
        val port = tlsPort()
        return if (ip.isNotBlank() && port > 0) ip to port else null
    }

    /**
     * 配对成功后的连接端点候选列表（按优先级）。
     *
     * 配对端口 ≠ 连接端口，所以不能直接拿配对地址去连。这里把所有可能来源都排上，
     * 由调用方逐个尝试，避免某一路（mDNS / 系统属性）在该机型上失效时整体失败。
     */
    fun connectCandidates(context: Context, pairHost: String, pairPort: Int): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()

        // 1) mDNS 广播的连接端口，最准确
        runCatching { discoverConnect(context, 5_000L) }
            .getOrDefault(emptyList())
            .forEach { result += it.host to it.port }

        // 2) 本机 WLAN IP + 系统属性端口
        val ip = localWifiIp().ifBlank { pairHost }
        val propPort = tlsPort()
        if (ip.isNotBlank() && propPort > 0) result += ip to propPort

        // 3) 配对地址所在主机的常见无线调试端口（部分 ROM 连接端口与配对端口相邻）
        if (pairHost.isNotBlank()) {
            if (propPort > 0) result += pairHost to propPort
            // adbd 无线调试常见端口段
            listOf(5555, 5037).forEach { result += pairHost to it }
        }

        return result
            .filter { it.first.isNotBlank() && it.second in 1..65535 }
            .distinctBy { "${it.first}:${it.second}" }
    }
}
