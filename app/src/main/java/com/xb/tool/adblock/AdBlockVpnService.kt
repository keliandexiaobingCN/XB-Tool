package com.xb.tool.adblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.xb.tool.MainActivity
import com.xb.tool.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class AdBlockVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.xb.tool.action.ADBLOCK_START"
        const val ACTION_STOP = "com.xb.tool.action.ADBLOCK_STOP"
        const val ACTION_RESET = "com.xb.tool.action.ADBLOCK_RESET"
        const val CHANNEL = "xb_adblock"
        const val NOTIFICATION_ID = 1101
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val DNS_ADDRESS = "10.111.222.2"
        private const val UPSTREAM_DNS = "223.5.5.5"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnel: ParcelFileDescriptor? = null
    private var readJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var upstream: DatagramSocket? = null
    private val total = AtomicLong(0)
    private val blocked = AtomicLong(0)
    private val saved = AtomicLong(0)
    private var top = linkedMapOf<String, Long>()
    private var lastPersist = 0L
    private var lastNotify = 0L
    private var startedAt = 0L
    private var lastDomain = ""

    override fun onCreate() {
        super.onCreate()
        AdBlockStore.reloadRules(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESET) {
            total.set(0)
            blocked.set(0)
            saved.set(0)
            top.clear()
            AdBlockStore.resetStats(this)
            publish(true)
            return START_STICKY
        }
        startForegroundSafely()
        if (running) {
            registerNetwork()
            return START_STICKY
        }
        running = true
        startedAt = System.currentTimeMillis()
        AdBlockRuntime.setRunning(true)
        AdBlockStore.setEnabled(this, true)
        startTunnel()
        registerNetwork()
        return START_STICKY
    }

    private var running = false

    private fun startForegroundSafely() {
        runCatching {
            val notification = buildNotification(0, 0)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    @Synchronized
    private fun startTunnel() {
        if (tunnel != null) return
        runCatching {
            val builder = Builder()
                .setSession("XB工具 · 防广告 DNS")
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32)
                .setBlocking(true)
                .setMtu(1500)
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)
            runCatching { builder.addDisallowedApplication(packageName) }
            tunnel = builder.establish()
            upstream = DatagramSocket().apply {
                soTimeout = 3000
                runCatching { protect(this) }
            }
        }.onFailure {
            running = false
            AdBlockRuntime.setRunning(false)
            stopSelf()
            return
        }
        val fd = tunnel ?: run {
            running = false
            AdBlockRuntime.setRunning(false)
            stopSelf()
            return
        }
        readJob?.cancel()
        readJob = scope.launch {
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            val buffer = ByteArray(32767)
            try {
                while (isActive && running) {
                    val length = try {
                        input.read(buffer)
                    } catch (t: java.io.IOException) {
                        if (running) throw t else break
                    }
                    if (length <= 0) break
                    runCatching { handlePacket(buffer, length, output) }
                }
            } catch (t: Throwable) {
                if (running) android.util.Log.w("AdBlockVpnService", "DNS reader stopped: ${t.message}")
            } finally {
                runCatching { input.close() }
                runCatching { output.close() }
            }
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 28 || (packet[0].toInt() shr 4) != 4) return
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (length < ihl + 8) return
        if (packet[9].toInt() and 0xFF != 17) return
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return
        val dnsOffset = ihl + 8
        if (length < dnsOffset + 12) return
        val dnsLength = length - dnsOffset
        val question = parseQuestion(packet, dnsOffset, length) ?: return
        val domain = question.first
        val questionEnd = question.second
        total.incrementAndGet()
        lastDomain = domain
        val safe = AdBlockStore.safeSearch(this)
        val safeIp = if (safe) AdBlockStore.forceSafeSearchIp(domain) else null
        val isBlocked = safeIp == null && RuleStore.isBlocked(this, domain)
        if (safeIp != null) {
            val response = buildAResponse(packet, dnsOffset, questionEnd, safeIp)
            writeUdpResponse(output, srcIp, dstIp, srcPort, dstPort, response)
            publish(false)
        } else if (isBlocked) {
            val response = buildNxdomain(packet, dnsOffset, questionEnd)
            writeUdpResponse(output, srcIp, dstIp, srcPort, dstPort, response)
            countBlocked(domain)
        } else {
            val forwarded = forwardDns(packet, dnsOffset, dnsLength)
            if (forwarded != null) {
                writeUdpResponse(output, srcIp, dstIp, srcPort, dstPort, forwarded)
            }
            publish(false)
        }
    }

    private fun parseQuestion(packet: ByteArray, offset: Int, length: Int): Pair<String, Int>? {
        var pos = offset + 12
        val labels = mutableListOf<String>()
        while (pos < length) {
            val labelLength = packet[pos].toInt() and 0xFF
            pos++
            if (labelLength == 0) break
            if (labelLength > 63 || pos + labelLength > length) return null
            labels += String(packet, pos, labelLength, Charsets.UTF_8)
            pos += labelLength
        }
        if (pos + 4 > length) return null
        return labels.joinToString(".") to (pos + 4)
    }

    private fun buildNxdomain(query: ByteArray, dnsOffset: Int, questionEnd: Int): ByteArray {
        val questionLength = (questionEnd - dnsOffset).coerceAtLeast(12)
        val out = ByteArray(questionLength)
        query.copyInto(out, 0, dnsOffset, questionEnd)
        out[2] = 0x81.toByte()
        out[3] = 0x83.toByte()
        for (i in 6 until 12) out[i] = 0
        return out
    }

    private fun buildAResponse(query: ByteArray, dnsOffset: Int, questionEnd: Int, ip: ByteArray): ByteArray {
        val questionLength = questionEnd - dnsOffset
        val out = ByteArray(questionLength + 16)
        query.copyInto(out, 0, dnsOffset, questionEnd)
        out[2] = 0x81.toByte()
        out[3] = 0x80.toByte()
        out[6] = 0
        out[7] = 1
        out[8] = 0
        out[9] = 0
        out[10] = 0
        out[11] = 0
        var p = questionLength
        out[p++] = 0xC0.toByte()
        out[p++] = 0x0C
        out[p++] = 0
        out[p++] = 1
        out[p++] = 0
        out[p++] = 1
        out[p++] = 0
        out[p++] = 0
        out[p++] = 0
        out[p++] = 60
        out[p++] = 0
        out[p++] = 4
        ip.copyInto(out, p, 0, 4)
        return out
    }

    private fun forwardDns(packet: ByteArray, dnsOffset: Int, dnsLength: Int): ByteArray? {
        val socket = upstream ?: return null
        return runCatching {
            val payload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
            val upstreamAddress = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(payload, payload.size, upstreamAddress, 53))
            val response = ByteArray(4096)
            val received = DatagramPacket(response, response.size)
            socket.receive(received)
            response.copyOf(received.length)
        }.getOrNull()
    }

    private fun writeUdpResponse(
        output: FileOutputStream,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dns: ByteArray
    ) {
        val udpLength = 8 + dns.size
        val totalLength = 20 + udpLength
        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        packet[1] = 0
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = totalLength.toByte()
        packet[4] = 0
        packet[5] = 0
        packet[6] = 0x40
        packet[7] = 0
        packet[8] = 64
        packet[9] = 17
        packet[10] = 0
        packet[11] = 0
        dstIp.copyInto(packet, 12, 0, 4)
        srcIp.copyInto(packet, 16, 0, 4)
        val checksum = checksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = checksum.toByte()
        packet[20] = (53 shr 8).toByte()
        packet[21] = 53.toByte()
        packet[22] = (srcPort shr 8).toByte()
        packet[23] = srcPort.toByte()
        packet[24] = (udpLength shr 8).toByte()
        packet[25] = udpLength.toByte()
        packet[26] = 0
        packet[27] = 0
        dns.copyInto(packet, 28)
        synchronized(output) {
            output.write(packet)
            output.flush()
        }
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun countBlocked(domain: String) {
        val count = blocked.incrementAndGet()
        saved.addAndGet(30_000L)
        top[domain] = (top[domain] ?: 0L) + 1
        publish(true)
        val now = System.currentTimeMillis()
        if (now - lastNotify > 5_000) {
            lastNotify = now
            runCatching {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(total.get(), count))
            }
        }
    }

    private fun publish(forcePersist: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastPersist < 500 && !forcePersist) return
        lastPersist = now
        AdBlockRuntime.publish(
            AdBlockStats(
                running = true,
                totalQueries = total.get(),
                blockedQueries = blocked.get(),
                savedBytes = saved.get(),
                rulesCount = RuleStore.rules(this).size,
                startedAt = startedAt,
                lastDomain = lastDomain,
                topBlocked = top.entries.sortedByDescending { it.value }.take(8).map { it.toPair() }
            )
        )
        AdBlockStore.saveStats(this, total.get(), blocked.get(), saved.get(), top)
    }

    private fun registerNetwork() {
        if (networkCallback != null) return
        if (!AdBlockStore.autoReconnect(this)) return
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (running && tunnel == null) startTunnel()
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun stopSelfSafely() {
        running = false
        AdBlockStore.setEnabled(this, false)
        AdBlockRuntime.setRunning(false)
        readJob?.cancel()
        runCatching { tunnel?.close() }
        tunnel = null
        runCatching { upstream?.close() }
        upstream = null
        networkCallback?.let { callback ->
            runCatching {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
            }
        }
        networkCallback = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopSelfSafely()
    }

    override fun onDestroy() {
        running = false
        AdBlockRuntime.setRunning(false)
        readJob?.cancel()
        runCatching { tunnel?.close() }
        runCatching { upstream?.close() }
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "防广告拦截", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "DNS 广告过滤运行状态"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(total: Long, blocked: Long): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            12,
            Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle("XB工具 · 防广告拦截运行中")
            .setContentText("已拦截 $blocked 次 / 共 $total 次解析")
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "停止拦截", stop)
            .build()
    }
}
