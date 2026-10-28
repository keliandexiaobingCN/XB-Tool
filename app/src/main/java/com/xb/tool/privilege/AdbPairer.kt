package com.xb.tool.privilege

import android.content.Context
import android.os.Build
import io.github.muntashirakon.crypto.spake2.Spake2Context
import io.github.muntashirakon.crypto.spake2.Spake2Role
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.HKDFParameters
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * ADB 无线调试配对（AOSP `adb pair` 协议的客户端实现）。
 *
 * 为什么不用 libadb-android 自带的 `PairingConnectionCtx`：
 *  它把 TLS、SPAKE2、PeerInfo 三段全部包在一个 `start()` 里，任何一段失败都只抛出
 *  同一句 "Could not exchange peer info."，日志里看不出到底卡在哪；而且它选择
 *  Conscrypt 的方式依赖 `Class.forName`，在部分 ROM 上会拿到和 SSLContext 不一致的
 *  provider，导致导出的 keying material 与服务端不一致，最终就是「握手失败」。
 *
 * 这里按 AOSP `adb/pairing_connection/pairing_connection.cpp` 逐段实现，
 * 每一步都写日志，失败时能明确告诉用户是 TLS、配对码还是 PeerInfo 的问题。
 *
 * 协议要点：
 *  1. TCP 连接配对端口，TLS 1.3 握手（不校验证书，客户端不需要证书）
 *  2. exportKeyingMaterial(socket, "adb-label\0", null, 64)
 *  3. 口令 = 6 位配对码字节 + keying material（AOSP 的顺序：先 pswd 后 keying）
 *  4. SPAKE2（Alice，名字 "adb pair client\0" / "adb pair server\0"）交换消息
 *  5. HKDF-SHA256(salt=null, info="adb pairing_auth aes-128-gcm key") 得到 16 字节密钥
 *  6. AES-128-GCM 交换 PeerInfo（12 字节小端计数器 nonce，初始为 0）
 */
object AdbPairer {

    private const val EXPORT_KEY_SIZE = 64
    private const val PEER_INFO_SIZE = 8192
    private const val HEADER_SIZE = 6
    private const val TYPE_SPAKE2 = 0
    private const val TYPE_PEER_INFO = 1
    private const val TAG = "AdbPairer"

    private val CLIENT_NAME = "adb pair client\u0000".toByteArray(Charsets.UTF_8)
    private val SERVER_NAME = "adb pair server\u0000".toByteArray(Charsets.UTF_8)
    private val HKDF_INFO = "adb pairing_auth aes-128-gcm key".toByteArray(Charsets.UTF_8)

    data class Result(
        val ok: Boolean,
        val stage: String,
        val message: String
    )

    /**
     * 执行配对。
     *
     * @param useClientCert 是否在 TLS 握手中出示本机 adb 证书。
     *   AOSP 的 `SslUtils.getSslContext` 是带 KeyManager 的；但也有 ROM 的 adbd
     *   不索要客户端证书。两种都试一遍，由设备自己决定认哪一种。
     * @param log 每完成/失败一步都会回调，界面直接显示。
     */
    fun pair(
        context: Context,
        host: String,
        port: Int,
        code: String,
        privateKey: PrivateKey,
        certificate: X509Certificate,
        deviceName: String,
        timeoutMs: Int = 25_000,
        useClientCert: Boolean = true,
        log: (String) -> Unit = {}
    ): Result {
        if (code.length != 6 || code.any { !it.isDigit() }) {
            return Result(false, "配对码", "配对码应为 6 位数字")
        }

        var socket: Socket? = null
        var ssl: SSLSocket? = null
        try {
            log("1/5 连接配对端口 $host:$port")
            val raw = Socket()
            raw.tcpNoDelay = true
            raw.connect(InetSocketAddress(host, port), 8_000)
            socket = raw

            log("2/5 TLS 1.3 握手（${if (useClientCert) "带本机证书" else "不带证书"}）")
            val tls = buildSslContext(
                if (useClientCert) privateKey else null,
                if (useClientCert) certificate else null
            )
            log("     使用 ${tls.label}")
            val created = tls.context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            ssl = created
            created.sslParameters = created.sslParameters.apply {
                protocols = arrayOf("TLSv1.3")
            }
            created.startHandshake()
            log("     TLS 握手成功 · ${created.session.protocol}")

            val exported = exportKeyingMaterial(created, tls.exporter)
            val keying = exported.first
                ?: return Result(
                    false,
                    "密钥导出",
                    "无法导出 TLS keying material：${exported.second.ifBlank { "该设备的 TLS 实现不提供导出接口" }}"
                )
            log("3/5 已导出 keying material ${keying.size} 字节")

            val material = ByteArray(code.length + keying.size)
            System.arraycopy(code.toByteArray(Charsets.UTF_8), 0, material, 0, code.length)
            System.arraycopy(keying, 0, material, code.length, keying.size)

            val spake = Spake2Context(Spake2Role.Alice, CLIENT_NAME, SERVER_NAME)
            val myMsg = spake.generateMessage(material)
                ?: return Result(false, "SPAKE2", "SPAKE2 初始化失败")
            log("4/5 发送 SPAKE2 消息（${myMsg.size} 字节）")

            val input = DataInputStream(created.inputStream)
            val output = DataOutputStream(created.outputStream)

            writePacket(output, TYPE_SPAKE2, myMsg)
            val header = readHeader(input)
                ?: return Result(false, "SPAKE2", "服务端关闭了连接（没有返回数据）")
            if (header.second != TYPE_SPAKE2) {
                return Result(false, "SPAKE2", "服务端返回了意外的数据包类型 ${header.second}")
            }
            val theirMsg = ByteArray(header.third)
            input.readFully(theirMsg)
            val secret = spake.processMessage(theirMsg)
                ?: return Result(false, "SPAKE2", "配对码不正确（SPAKE2 校验失败）")
            log("     SPAKE2 校验通过")

            val key = hkdf(secret)
            val auth = AesGcm(key)

            val peerInfo = buildPeerInfo(privateKey, deviceName)
            val encrypted = auth.encrypt(peerInfo)
                ?: return Result(false, "PeerInfo", "本地 PeerInfo 加密失败")
            writePacket(output, TYPE_PEER_INFO, encrypted)
            log("5/5 已发送本机公钥，等待服务端确认")

            val replyHeader = readHeader(input)
                ?: return Result(false, "PeerInfo", "服务端关闭了连接（配对可能已被取消）")
            if (replyHeader.second != TYPE_PEER_INFO) {
                return Result(false, "PeerInfo", "服务端返回了意外的数据包类型 ${replyHeader.second}")
            }
            val replyPayload = ByteArray(replyHeader.third)
            input.readFully(replyPayload)
            val decrypted = auth.decrypt(replyPayload)
                ?: return Result(false, "PeerInfo", "无法解密服务端返回的公钥（密钥不一致）")
            if (decrypted.size != PEER_INFO_SIZE) {
                return Result(false, "PeerInfo", "服务端公钥长度异常（${decrypted.size} 字节，应为 $PEER_INFO_SIZE）")
            }
            log("     配对完成，服务端已接受本机公钥")
            return Result(true, "完成", "配对成功")
        } catch (t: Throwable) {
            val stage = when {
                ssl == null -> "TLS 握手"
                else -> "数据交换"
            }
            return Result(false, stage, describe(t))
        } finally {
            runCatching { ssl?.close() }
            runCatching { socket?.close() }
        }
    }

    // ---------------------------------------------------------------- TLS

    private data class TlsBundle(
        val context: SSLContext,
        val label: String,
        /** 从这个类上导出 keying material，必须与 context 的 provider 同源。 */
        val exporter: String
    )

    /**
     * 依次尝试「内置 Conscrypt」与「平台 TLS」，返回可用的 SSLContext。
     *
     * 顺序很重要：平台 Conscrypt 的 `com.android.org.conscrypt.Conscrypt` 在部分 ROM
     * 上不是公开 API（Android 11 之后被 @UnsupportedAppUsage 收口），反射取不到
     * `exportKeyingMaterial`，于是配对永远卡在「密钥导出」。
     * 内置 Conscrypt 2.5.2 自带 BoringSSL 的 `SSL_export_keying_material`，
     * 能力完整，所以优先用它。
     *
     * 必须带 KeyManager（客户端证书）：adbd 的配对服务在 TLS 1.3 里会发
     * CertificateRequest，客户端交空证书时设备端在收到 Finished 后会立刻关连接，
     * 而客户端此时已经认为握手成功 —— 表现就是「TLS 握手成功」之后第一次写
     * 立刻 Broken pipe。AOSP 的 `SslUtils.getSslContext` 也是传了 KeyManager 的。
     */
    private fun buildSslContext(
        privateKey: PrivateKey?,
        certificate: X509Certificate?
    ): TlsBundle {
        val keyManagers = if (privateKey != null && certificate != null) {
            arrayOf<KeyManager>(keyManager(privateKey, certificate))
        } else {
            null
        }
        return tryBuild {
            val cls = Class.forName("org.conscrypt.OpenSSLProvider")
            val provider = cls.getDeclaredConstructor().newInstance() as java.security.Provider
            val ctx = SSLContext.getInstance("TLSv1.3", provider)
            ctx.init(keyManagers, arrayOf(trustAll()), SecureRandom())
            TlsBundle(ctx, "内置 Conscrypt", "org.conscrypt.Conscrypt")
        } ?: tryBuild {
            if (Build.VERSION.SDK_INT < 29) return@tryBuild null
            val ctx = SSLContext.getInstance("TLSv1.3")
            ctx.init(keyManagers, arrayOf(trustAll()), SecureRandom())
            TlsBundle(ctx, "平台 TLS 1.3", "com.android.org.conscrypt.Conscrypt")
        } ?: TlsBundle(
            SSLContext.getInstance("TLS").apply {
                init(keyManagers, arrayOf(trustAll()), SecureRandom())
            },
            "平台 TLS（兜底，可能无法导出密钥）",
            "com.android.org.conscrypt.Conscrypt"
        )
    }

    private fun <T> tryBuild(block: () -> T?): T? = runCatching { block() }.getOrNull()

    private fun trustAll(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /**
     * 单证书 KeyManager：只认别名 "key"，只对 RSA 请求出示证书。
     * 与 libadb-android 的 `SslUtils$1` 行为一致。
     */
    private fun keyManager(
        privateKey: PrivateKey,
        certificate: X509Certificate
    ): X509ExtendedKeyManager = object : X509ExtendedKeyManager() {
        private val alias = "key"

        override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? =
            null

        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out java.security.Principal>?,
            socket: java.net.Socket?
        ): String? = if (keyType?.any { it.equals("RSA", true) } == true) alias else null

        override fun getServerAliases(
            keyType: String?,
            issuers: Array<out java.security.Principal>?
        ): Array<String>? = null

        override fun chooseServerAlias(
            keyType: String?,
            issuers: Array<out java.security.Principal>?,
            socket: java.net.Socket?
        ): String? = null

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            if (alias == this.alias) arrayOf(certificate) else null

        override fun getPrivateKey(alias: String?): PrivateKey? =
            if (alias == this.alias) privateKey else null
    }

    /**
     * 导出 TLS keying material，返回 (数据, 失败原因)。
     *
     * AOSP 用的是 BoringSSL 的 `SSL_export_keying_material`，label = "adb-label\0"，
     * context 为空，长度 64 字节。这里反射调用目标 provider 的静态方法。
     */
    private fun exportKeyingMaterial(socket: SSLSocket, preferred: String): Pair<ByteArray?, String> {
        val order = listOf(preferred, "org.conscrypt.Conscrypt", "com.android.org.conscrypt.Conscrypt")
            .distinct()
        val errors = mutableListOf<String>()
        for (name in order) {
            val cls = runCatching { Class.forName(name) }.getOrNull()
            if (cls == null) {
                errors += "$name 不存在"
                continue
            }
            val method = runCatching {
                cls.getMethod(
                    "exportKeyingMaterial",
                    SSLSocket::class.java,
                    String::class.java,
                    ByteArray::class.java,
                    Int::class.javaPrimitiveType
                )
            }.getOrNull()
            if (method == null) {
                errors += "${name.substringAfterLast('.')} 没有 exportKeyingMaterial"
                continue
            }
            val bytes = runCatching {
                method.invoke(null, socket, "adb-label\u0000", null, EXPORT_KEY_SIZE) as ByteArray
            }.getOrElse {
                errors += "${name.substringAfterLast('.')} 导出抛错：${it.cause?.message ?: it.message}"
                null
            }
            when {
                bytes == null -> Unit
                bytes.size != EXPORT_KEY_SIZE -> errors += "${name.substringAfterLast('.')} 只返回 ${bytes.size} 字节"
                else -> return bytes to ""
            }
        }
        return null to errors.joinToString("；")
    }

    // ---------------------------------------------------------------- 加密

    private fun hkdf(secret: ByteArray): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(secret, null, HKDF_INFO))
        val key = ByteArray(16)
        generator.generateBytes(key, 0, key.size)
        return key
    }

    /** AOSP 的 AES-128-GCM：12 字节 nonce，前 8 字节是小端计数器，后 4 字节为 0。 */
    private class AesGcm(key: ByteArray) {
        private val keySpec = SecretKeySpec(key, "AES")
        private var encIv = 0L
        private var decIv = 0L

        fun encrypt(plain: ByteArray): ByteArray? = runCatching {
            val nonce = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(encIv++)
                .putInt(0)
                .array()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            cipher.doFinal(plain)
        }.getOrNull()

        fun decrypt(payload: ByteArray): ByteArray? = runCatching {
            val nonce = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putLong(decIv++)
                .putInt(0)
                .array()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            cipher.doFinal(payload)
        }.getOrNull()
    }

    // ---------------------------------------------------------------- 数据包

    /** 写一个配对数据包：1 字节版本 + 1 字节类型 + 4 字节大端长度 + 负载。 */
    private fun writePacket(out: DataOutputStream, type: Int, payload: ByteArray) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
            .put(1)
            .put(type.toByte())
            .putInt(payload.size)
            .array()
        out.write(header)
        out.write(payload)
        out.flush()
    }

    /** 读包头，返回 (版本, 类型, 长度)；对端关闭返回 null。 */
    private fun readHeader(input: DataInputStream): Triple<Int, Int, Int>? {
        val raw = ByteArray(HEADER_SIZE)
        runCatching { input.readFully(raw) }.getOrElse { return null }
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
        val version = buffer.get().toInt() and 0xFF
        val type = buffer.get().toInt() and 0xFF
        val size = buffer.int
        if (version != 1) return null
        if (size < 0 || size > 1024 * 1024) return null
        return Triple(version, type, size)
    }

    // ---------------------------------------------------------------- PeerInfo

    /**
     * 构造 PeerInfo：1 字节类型（0 = ADB_RSA_PUB_KEY）+ 8191 字节数据，
     * 数据内容就是 adb 公钥行：`base64(RSAPublicKey 结构) + " " + 设备名 + "\0"`。
     *
     * 这里直接复用 libadb-android 里的 `AndroidPubkey.encodeWithName`（反射调用，
     * 它是包级私有类），避免自己手写 524 字节的 RSA 结构出错 —— 那个结构一旦
     * 字节序或 n0inv 算错，配对会「成功」但之后 adb 不认这把钥匙。
     */
    private fun buildPeerInfo(privateKey: PrivateKey, deviceName: String): ByteArray {
        val crt = privateKey as? java.security.interfaces.RSAPrivateCrtKey
            ?: throw IllegalStateException("私钥格式不支持，无法导出 ADB 公钥")
        val pub = java.security.KeyFactory.getInstance("RSA").generatePublic(
            java.security.spec.RSAPublicKeySpec(crt.modulus, crt.publicExponent)
        ) as java.security.interfaces.RSAPublicKey

        val publicKeyBytes = runCatching {
            val cls = Class.forName("io.github.muntashirakon.adb.AndroidPubkey")
            val method = cls.getDeclaredMethod(
                "encodeWithName",
                java.security.interfaces.RSAPublicKey::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, pub, deviceName) as ByteArray
        }.getOrElse {
            // 兜底：自己拼 base64 公钥 + 设备名
            val encoded = encodeAdbPublicKey(pub)
            val base64 = android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)
            "$base64 $deviceName\u0000".toByteArray(Charsets.UTF_8)
        }

        val data = ByteArray(PEER_INFO_SIZE - 1)
        System.arraycopy(publicKeyBytes, 0, data, 0, minOf(publicKeyBytes.size, data.size))

        val out = ByteArray(PEER_INFO_SIZE)
        out[0] = 0
        System.arraycopy(data, 0, out, 1, data.size)
        return out
    }

    /**
     * AOSP `RSAPublicKey` 结构（524 字节，全部小端）：
     *   uint32 modulus_size_words (64)
     *   uint32 n0inv
     *   uint8  modulus[256]
     *   uint8  rr[256]        (R^2 mod N)
     *   uint32 exponent
     */
    private fun encodeAdbPublicKey(key: java.security.interfaces.RSAPublicKey): ByteArray {
        val modulus = key.modulus
        val exponent = key.publicExponent
        val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(64)
        buffer.putInt(n0inv(modulus).toInt())

        val mod = modulus.toByteArray()
        val modFixed = ByteArray(256)
        val modTrim = if (mod.size > 256) mod.copyOfRange(mod.size - 256, mod.size) else mod
        System.arraycopy(modTrim, 0, modFixed, 256 - modTrim.size, modTrim.size)
        modFixed.reverse()
        buffer.put(modFixed)

        val r = BigInteger.ONE.shiftLeft(2048)
        val rr = r.multiply(r).mod(modulus).toByteArray()
        val rrFixed = ByteArray(256)
        val rrTrim = if (rr.size > 256) rr.copyOfRange(rr.size - 256, rr.size) else rr
        System.arraycopy(rrTrim, 0, rrFixed, 256 - rrTrim.size, rrTrim.size)
        rrFixed.reverse()
        buffer.put(rrFixed)

        buffer.putInt(exponent.toInt())
        return buffer.array()
    }

    /** n0inv = -1 / N[0] mod 2^32。 */
    private fun n0inv(modulus: BigInteger): Long {
        val n0 = modulus.toLong() and 0xFFFFFFFFL
        var inv = 1L
        // 牛顿迭代求模 2^32 的逆元
        for (i in 0 until 5) {
            inv = (inv * (2 - n0 * inv)) and 0xFFFFFFFFL
        }
        return (0L - inv) and 0xFFFFFFFFL
    }

    private fun describe(t: Throwable): String {
        val raw = t.message ?: t.javaClass.simpleName
        return when {
            raw.contains("Broken pipe", true) || raw.contains("EPIPE", true) ->
                "设备在 TLS 握手末尾主动断开了连接（Broken pipe）。" +
                    "通常是因为握手时没有出示客户端证书；本版会自动换配置重试，" +
                    "若仍然出现，请重新打开系统的「使用配对码配对设备」弹窗再试一次"
            raw.contains("Connection reset", true) ->
                "设备重置了连接：配对码可能已过期，或弹窗已被关闭"
            raw.contains("Connection refused", true) -> "端口未开放：配对弹窗可能已经关闭，请重新打开后再试"
            raw.contains("timed out", true) || raw.contains("Timeout", true) -> "超时：请停留在「使用配对码配对设备」页面"
            raw.contains("NoSuchAlgorithm", true) -> "该设备不支持 TLS 1.3，无法使用无线调试配对"
            raw.contains("certificate", true) || raw.contains("SSLHandshake", true) -> "TLS 握手失败：$raw"
            else -> raw
        }
    }
}
