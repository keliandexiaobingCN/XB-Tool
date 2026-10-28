package com.xb.tool.util

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class CloudDownloader {
    companion object {
        private val cloudHosts = listOf(
            "pan.baidu.com", "yun.baidu.com", "aliyundrive.com", "alipan.com",
            "123pan.com", "123684.com", "cloud.189.cn", "drive.uc.cn", "fast.uc.cn",
            "quark.cn", "lanzou", "weiyun.com", "115.com", "115cdn.com"
        )

        fun extractUrl(text: String): String? {
            val match = Regex("""https?://[^\s<>"']+""").find(text) ?: return null
            return match.value.trimEnd('。', '，', '、', '；', '）', ')', ']', '》', '"')
        }

        fun isCloudShare(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return cloudHosts.any { lower.contains(it) }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var currentCall: okhttp3.Call? = null

    fun cancel() {
        runCatching { currentCall?.cancel() }
        currentCall = null
    }

    fun download(
        context: Context,
        url: String,
        referer: String? = null,
        cookie: String? = null,
        userAgent: String? = null,
        onProgress: (Float, Long, Long, String) -> Unit
    ): File {
        val builder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                userAgent ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
        if (!referer.isNullOrBlank()) builder.header("Referer", referer)
        if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
        val request = builder.build()
        val call = client.newCall(request)
        currentCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("服务器返回 HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("响应内容为空")
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.startsWith("text/html", ignoreCase = true)) {
                    throw IllegalStateException("该链接返回的是网页而不是文件，可能需要浏览器完成校验或登录")
                }
                val total = body.contentLength()
                val fileName = resolveFileName(response.header("Content-Disposition"), url)
                val dir = FileUtils.externalDownloadDir(context)
                var output = File(dir, fileName)
                var counter = 1
                while (output.exists()) {
                    val base = fileName.substringBeforeLast('.', fileName)
                    val ext = fileName.substringAfterLast('.', "")
                    val suffix = if (ext.isBlank()) "" else ".$ext"
                    output = File(dir, "${base}_$counter$suffix")
                    counter++
                }
                onProgress(0f, 0L, total, "开始下载")
                body.byteStream().use { input ->
                    output.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            if (call.isCanceled()) throw IllegalStateException("已取消下载")
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            onProgress(progress, downloaded, total, "下载中")
                        }
                    }
                }
                onProgress(1f, output.length(), output.length(), "下载完成")
                return output
            }
        } finally {
            if (currentCall === call) currentCall = null
        }
    }

    private fun resolveFileName(contentDisposition: String?, url: String): String {
        contentDisposition?.let { header ->
            val utf8 = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(header)
            if (utf8 != null) {
                val decoded = runCatching {
                    java.net.URLDecoder.decode(utf8.groupValues[1], "UTF-8")
                }.getOrDefault(utf8.groupValues[1])
                return FileUtils.sanitizeFileName(decoded)
            }
            val normal = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)
            if (normal != null) return FileUtils.sanitizeFileName(normal.groupValues[1])
        }
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        val name = path.substringAfterLast('/')
        if (name.isNotBlank()) return FileUtils.sanitizeFileName(name)
        return "xb_download_${System.currentTimeMillis()}.bin"
    }
}
