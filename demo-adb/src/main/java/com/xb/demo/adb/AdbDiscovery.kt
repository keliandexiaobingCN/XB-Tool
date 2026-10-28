package com.xb.demo.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 无线调试（Android 11+）配对服务发现。
 *
 * 只做「发现」这一层：通过 mDNS 找到系统广播的
 * _adb-tls-pairing._tcp 服务，拿到主机与端口。
 *
 * 配对本身需要 SPAKE2（BoringSSL 私有实现），
 * demo 阶段先验证发现链路是否可用。
 */
object AdbDiscovery {

    private const val TAG = "AdbDiscovery"
    private const val PAIRING = "_adb-tls-pairing._tcp"
    private const val CONNECT = "_adb-tls-connect._tcp"

    data class Service(val name: String, val host: String, val port: Int)

    data class Result(
        val pairing: List<Service>,
        val connect: List<Service>,
        val tlsPort: Int,
        val log: List<String>
    )

    fun scan(context: Context, timeoutMs: Long = 6000L): Result {
        val logs = mutableListOf<String>()
        val pairing = discover(context, PAIRING, logs, timeoutMs)
        val connect = discover(context, CONNECT, logs, timeoutMs)
        val tlsPort = readProp("service.adb.tls.port")
        logs += "service.adb.tls.port = $tlsPort"
        logs += if (tlsPort > 0) "无线调试状态：已开启" else "无线调试状态：未开启"
        return Result(pairing, connect, tlsPort, logs)
    }

    private fun discover(
        context: Context,
        type: String,
        logs: MutableList<String>,
        timeoutMs: Long
    ): List<Service> {
        val found = mutableListOf<Service>()
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsd == null) {
            logs += "NsdManager 不可用"
            return emptyList()
        }
        val latch = CountDownLatch(1)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                logs += "启动发现失败：$type code=$errorCode"
                latch.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                logs += "停止发现失败：$type code=$errorCode"
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                logs += "开始发现：$serviceType"
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                logs += "发现已停止：$serviceType"
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                logs += "服务消失：${serviceInfo?.serviceName}"
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val info = serviceInfo ?: return
                logs += "发现服务：${info.serviceName} (${info.serviceType})"
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i: NsdServiceInfo?, errorCode: Int) {
                            logs += "解析失败：${i?.serviceName} code=$errorCode"
                        }

                        override fun onServiceResolved(i: NsdServiceInfo?) {
                            val host = i?.host?.hostAddress ?: return
                            logs += "解析成功：${i.serviceName} -> $host:${i.port}"
                            synchronized(found) {
                                found += Service(i.serviceName, host, i.port)
                            }
                            latch.countDown()
                        }
                    })
                }
            }
        }
        runCatching {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }.onFailure { logs += "发现异常：${it.message}" }
        runCatching { nsd.stopServiceDiscovery(listener) }
        return found.distinctBy { "${it.host}:${it.port}" }
    }

    /** 读取系统属性（不需要 root）。 */
    fun readProp(name: String): Int =
        runCatching {
            val p = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            text.toIntOrNull() ?: 0
        }.getOrDefault(0)

    /** 是否有可用的 app_process（拉起特权进程的前提）。 */
    fun findAppProcess(): String? =
        listOf("/system/bin/app_process64", "/system/bin/app_process32", "/system/bin/app_process")
            .firstOrNull { runCatching { java.io.File(it).exists() }.getOrDefault(false) }

    /** 系统里是否存在 adb 可执行文件（用于 adb pair 兜底）。 */
    fun findAdb(): String? =
        listOf("/system/bin/adb", "/system/xbin/adb", "/vendor/bin/adb")
            .firstOrNull { runCatching { java.io.File(it).canExecute() }.getOrDefault(false) }
}
