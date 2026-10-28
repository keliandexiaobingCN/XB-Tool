package com.xb.tool.util

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ResolvedCloudFile(
    val name: String,
    val directUrl: String,
    val size: String = "",
    val platform: String = "",
    val referer: String? = null,
    val cookie: String? = null,
    val userAgent: String? = null,
    val requiresBrowser: Boolean = false
)

class CloudResolveException(message: String) : Exception(message)

object CloudResolver {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val lanzouHosts = listOf(
        "lanzou", "lanzoui", "lanzoux", "lanzouw", "lanzouy", "lanzoup",
        "lanzn", "lanrar", "lanzou.com", "lanzoui.com", "lanzoux.com"
    )
    private val pan123Hosts = listOf("123pan.com", "123684.com", "123865.com", "123912.com")
    private val unsupportedHosts = listOf(
        "pan.baidu.com", "yun.baidu.com", "aliyundrive.com", "alipan.com",
        "cloud.189.cn", "drive.uc.cn", "fast.uc.cn", "quark.cn",
        "115.com", "115cdn.com", "weiyun.com"
    )

    fun isLanzou(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lanzouHosts.any { lower.contains(it) }
    }

    fun is123Pan(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return pan123Hosts.any { lower.contains(it) }
    }

    fun isUnsupported(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return unsupportedHosts.any { lower.contains(it) }
    }

    fun platformName(url: String): String = when {
        isLanzou(url) -> "蓝奏云"
        is123Pan(url) -> "123云盘"
        isUnsupported(url) -> "需要登录的云盘"
        else -> "直链"
    }

    suspend fun resolve(rawText: String, password: String = ""): List<ResolvedCloudFile> =
        withContext(Dispatchers.IO) {
            val url = extractUrl(rawText)
                ?: throw CloudResolveException("未找到有效的 http(s) 分享链接")
            when {
                isLanzou(url) -> resolveLanzou(url, password)
                is123Pan(url) -> resolve123Pan(url, password)
                isUnsupported(url) -> throw CloudResolveException(
                    "${platformName(url)} 的分享页需要登录 / 专用接口，当前版本暂不支持自动解析；请使用直链地址。"
                )
                else -> listOf(
                    ResolvedCloudFile(
                        name = guessFilename(url),
                        directUrl = url,
                        platform = "直链",
                        referer = url.substringBefore("/", "").let { scheme ->
                            runCatching {
                                val p = Uri.parse(url)
                                "${p.scheme}://${p.host}/"
                            }.getOrDefault("")
                        }
                    )
                )
            }
        }

    fun extractUrl(text: String): String? {
        val match = Regex("""https?://[^\s<>"']+""").find(text) ?: return null
        return match.value.trimEnd('。', '，', '、', '；', '）', ')', ']', '》', '"')
    }

    private fun resolveLanzou(url: String, password: String): List<ResolvedCloudFile> {
        val pageRequest = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        client.newCall(pageRequest).execute().use { response ->
            if (!response.isSuccessful) throw CloudResolveException("蓝奏云页面返回 HTTP ${response.code}")
            var html = response.body?.string().orEmpty()
            var referer = response.request.url.toString()
            val sharePageUrl = referer
            val outerHtml = html
            val pageTitle = Regex("<title>(.*?)</title>").find(outerHtml)?.groupValues?.get(1)
                ?.substringBefore(" - ")?.trim().orEmpty()
            val pageSize = Regex("文件大小：</span>([^<]+)").find(outerHtml)?.groupValues?.get(1)
                ?.trim().orEmpty()
            if (html.contains("var arg1=") && html.contains("eval")) {
                throw CloudResolveException("蓝奏云下发了 JS 反爬挑战，当前内置 HTTP 解析无法通过；请改用直链或稍后重试。")
            }
            if (html.contains("请输入密码") || html.contains("文件受密码保护")) {
                if (password.isBlank()) throw CloudResolveException("该蓝奏云分享需要提取码，请填写密码")
            }
            val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""")
                .find(html)?.groupValues?.get(1)
            if (!iframeSrc.isNullOrBlank()) {
                val baseUrl = Regex("""^(https?://[^/]+)""").find(referer)?.groupValues?.get(1)
                val iframeUrl = if (iframeSrc.startsWith("http")) iframeSrc
                else (baseUrl ?: "") + "/" + iframeSrc.trimStart('/')
                runCatching {
                    val iframeRequest = Request.Builder()
                        .url(iframeUrl)
                        .header("User-Agent", UA)
                        .header("Referer", referer)
                        .build()
                    client.newCall(iframeRequest).execute().use { iframeResponse ->
                        if (iframeResponse.isSuccessful) {
                            html = iframeResponse.body?.string().orEmpty()
                            referer = iframeUrl
                        }
                    }
                }
            }
            val wpSign = Regex("""var\s+wp_sign\s*=\s*['"]([^'"]+)['"]""")
                .find(html)?.groupValues?.get(1)
            val ajaxData = Regex("""var\s+ajaxdata\s*=\s*['"]([^'"]*)['"]""")
                .find(html)?.groupValues?.get(1).orEmpty()
            val domain1 = Regex("""var\s+domain1\s*=\s*['"]([^'"]+)['"]""")
                .find(html)?.groupValues?.get(1)
            if (!wpSign.isNullOrBlank() && !domain1.isNullOrBlank()) {
                val form = FormBody.Builder()
                    .add("action", "downprocess")
                    .add("websignkey", ajaxData)
                    .add("signs", ajaxData)
                    .add("sign", wpSign)
                    .add("websign", "")
                    .add("kd", "1")
                    .add("ves", "1")
                    .apply { if (password.isNotBlank()) add("p", password) }
                    .build()
                val ajaxRequest = Request.Builder()
                    .url(domain1)
                    .post(form)
                    .header("User-Agent", UA)
                    .header("Referer", sharePageUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
                client.newCall(ajaxRequest).execute().use { ajaxResponse ->
                    val body = ajaxResponse.body?.string().orEmpty()
                    val json = runCatching { JSONObject(body) }.getOrNull()
                        ?: throw CloudResolveException("蓝奏云返回内容不是 JSON：${body.take(120)}")
                    val success = json.optInt("zt", json.optInt("code", 0)) == 1 ||
                        json.optInt("code", 0) == 200
                    if (!success) {
                        throw CloudResolveException("蓝奏云解析失败：${json.optString("inf").ifBlank { "分享失效或提取码错误" }}")
                    }
                    val dom = json.optString("dom")
                    val path = json.optString("url")
                    if (path.isBlank()) throw CloudResolveException("蓝奏云未返回下载地址")
                    val direct = if (path.startsWith("http")) path else dom.trimEnd('/') + "/file/" + path
                    val cookie = response.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") }
                    return listOf(
                        ResolvedCloudFile(
                            name = pageTitle.ifBlank { json.optString("inf").ifBlank { guessFilename(url) } },
                            directUrl = direct,
                            size = pageSize.ifBlank { json.optString("size") },
                            platform = "蓝奏云",
                            referer = sharePageUrl,
                            cookie = cookie.ifBlank { null },
                            requiresBrowser = true
                        )
                    )
                }
            }
            if (html.contains("请输入密码") || html.contains("文件受密码保护")) {
                if (password.isBlank()) throw CloudResolveException("该蓝奏云分享需要提取码，请填写密码")
            }
            val sign = Regex("""var\s+(?:vm)?sign\s*=\s*['"]([^'"]+)['"]""")
                .find(html)?.groupValues?.get(1)
                ?: Regex("""["']sign["']\s*:\s*["']([^"']+)["']""")
                    .find(html)?.groupValues?.get(1)
            if (sign.isNullOrBlank()) {
                if (html.contains("文件受密码保护") || html.contains("请输入密码")) {
                    throw CloudResolveException("蓝奏云提取码错误，或页面结构已变化")
                }
                throw CloudResolveException("蓝奏云页面结构已变化：未找到 sign，可能被反爬拦截")
            }
            val fid = Regex("""ajaxm\.php\?file=([^"'&<>\s]+)""")
                .find(html)?.groupValues?.get(1)
                ?: Regex("""var\s+file_id\s*=\s*['"]?([^'";]+)""")
                    .find(html)?.groupValues?.get(1)
            val ajaxPath = Regex("""url\s*:\s*['"]([^'"]*ajaxm\.php[^'"]*)['"]""")
                .find(html)?.groupValues?.get(1)
                ?: if (fid != null) "/ajaxm.php?file=$fid" else "/ajaxm.php"
            val base = Regex("""^(https?://[^/]+)""").find(referer)?.groupValues?.get(1)
                ?: throw CloudResolveException("蓝奏云地址解析失败")
            val ajaxUrl = if (ajaxPath.startsWith("http")) ajaxPath else base + "/" + ajaxPath.trimStart('/')
            val form = FormBody.Builder()
                .add("action", "downprocess")
                .add("sign", sign)
                .add("ves", "1")
                .apply { if (password.isNotBlank()) add("p", password) }
                .build()
            val ajaxRequest = Request.Builder()
                .url(ajaxUrl)
                .post(form)
                .header("User-Agent", UA)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", base)
                .build()
            client.newCall(ajaxRequest).execute().use { ajaxResponse ->
                val body = ajaxResponse.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: throw CloudResolveException("蓝奏云返回内容不是 JSON：${body.take(120)}")
                val success = json.optInt("zt", json.optInt("code", 0)) == 1 ||
                    json.optInt("code", 0) == 200
                if (!success) {
                    val message = json.optString("inf").ifBlank { json.optString("msg") }
                    throw CloudResolveException("蓝奏云解析失败：${message.ifBlank { "提取码错误或分享失效" }}")
                }
                val dom = json.optString("dom")
                val path = json.optString("url")
                if (path.isBlank()) throw CloudResolveException("蓝奏云未返回下载地址")
                val direct = if (path.startsWith("http")) path else dom.trimEnd('/') + "/file/" + path
                val cookie = response.headers("Set-Cookie").joinToString("; ") { it.substringBefore(";") }
                return listOf(
                    ResolvedCloudFile(
                        name = json.optString("inf").ifBlank { guessFilename(url) },
                        directUrl = direct,
                        size = json.optString("size"),
                        platform = "蓝奏云",
                        referer = base + "/",
                        cookie = cookie.ifBlank { null }
                    )
                )
            }
        }
    }

    private fun resolve123Pan(url: String, password: String): List<ResolvedCloudFile> {
        val shareKey = Regex("""/s/([A-Za-z0-9_-]+)""").find(url)?.groupValues?.get(1)
            ?: throw CloudResolveException("123 云盘分享链接缺少 /s/<shareKey>")
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        val candidates = listOfNotNull(
            host?.let { "https://$it" },
            host?.replace("123pan", "123684")?.let { "https://$it" },
            "https://www.123684.com",
            "https://www.123865.com",
            "https://www.123912.com"
        ).distinct()

        var apiBase: String? = null
        var rootItems: List<JSONObject> = emptyList()
        var lastError: Throwable? = null
        for (base in candidates) {
            try {
                rootItems = fetch123List(base, shareKey, password, 0, 1)
                apiBase = base
                break
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (apiBase == null) {
            throw CloudResolveException(
                "123 云盘接口不可用：${lastError?.message ?: "分享不存在或已失效"}"
            )
        }
        if (rootItems.isEmpty()) throw CloudResolveException("123 云盘分享为空或已失效")

        val files = mutableListOf<JSONObject>()
        rootItems.forEach { item ->
            if (item.optInt("Type", 0) == 1) {
                runCatching {
                    files += fetch123List(
                        apiBase!!, shareKey, password,
                        item.optLong("FileId"), 4
                    )
                }
            } else {
                files += item
            }
        }
        if (files.isEmpty()) throw CloudResolveException("123 云盘分享中没有可下载文件")

        val result = mutableListOf<ResolvedCloudFile>()
        files.take(50).forEach { item ->
            val name = item.optString("FileName").ifBlank { "file" }
            val size = item.optLong("Size", 0L)
            val direct = runCatching { fetch123Download(apiBase!!, item, shareKey, password) }
                .getOrNull()
            if (direct.isNullOrBlank()) {
                result += ResolvedCloudFile(
                    name = name,
                    directUrl = url,
                    size = FileUtils.formatBytes(size),
                    platform = "123云盘（需登录）",
                    referer = "$apiBase/",
                    requiresBrowser = true
                )
            } else {
                result += ResolvedCloudFile(
                    name = name,
                    directUrl = direct,
                    size = FileUtils.formatBytes(size),
                    platform = "123云盘",
                    referer = "$apiBase/"
                )
            }
        }
        return result
    }

    private fun fetch123List(
        base: String,
        shareKey: String,
        password: String,
        parentId: Long,
        operateType: Int
    ): List<JSONObject> {
        val url = "$base/b/api/share/get?limit=100&next=-1&orderBy=file_name&orderDirection=asc" +
            "&SharePwd=${encode(password)}&ParentFileId=$parentId&Page=1" +
            "&event=homeListFile&operateType=$operateType" +
            "&shareKey=${encode(shareKey)}&r=${System.nanoTime()}"
        val request = Request.Builder()
            .url(url)
            .header("user-agent", "123pan/v2.4.0(Android_7.1.2;Xiaomi)")
            .header("platform", "android")
            .header("app-version", "61")
            .header("x-app-version", "2.4.0")
            .header("osversion", "Android_7.1.2")
            .header("devicetype", "M2101K9C")
            .header("x-channel", "1004")
            .header("devicename", "Xiaomi")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "$base/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw CloudResolveException("123 云盘接口 HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw CloudResolveException("123 云盘接口非 JSON 响应")
            }
            when (json.optInt("code", -1)) {
                0 -> Unit
                5103 -> throw CloudResolveException("分享不存在、已失效或提取码错误")
                else -> throw CloudResolveException(
                    "123 云盘接口：${json.optString("message").ifBlank { json.optString("code") }}"
                )
            }
            val data = json.optJSONObject("data") ?: throw CloudResolveException("123 云盘数据为空")
            val list = data.optJSONArray("InfoList") ?: return emptyList()
            return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        }
    }

    private fun fetch123Download(
        base: String,
        item: JSONObject,
        shareKey: String,
        password: String
    ): String? {
        val body = JSONObject().apply {
            put("driveId", 0)
            put("etag", item.optString("Etag"))
            put("fileId", item.optLong("FileId"))
            put("s3keyFlag", item.optString("S3KeyFlag"))
            put("type", item.optInt("Type", 0))
            put("fileName", item.optString("FileName"))
            put("size", item.optLong("Size", 0L))
        }
        val request = Request.Builder()
            .url("$base/a/api/file/download_info")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("user-agent", "123pan/v2.4.0(Android_7.1.2;Xiaomi)")
            .header("platform", "android")
            .header("app-version", "61")
            .header("x-app-version", "2.4.0")
            .header("osversion", "Android_7.1.2")
            .header("devicetype", "M2101K9C")
            .header("x-channel", "1004")
            .header("devicename", "Xiaomi")
            .header("Content-Type", "application/json")
            .header("Referer", "$base/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                ?: return null
            if (json.optInt("code", -1) != 0) return null
            val downloadUrl = json.optJSONObject("data")?.optString("DownloadUrl").orEmpty()
            if (downloadUrl.isBlank()) return null
            return decode123DownloadUrl(downloadUrl)
        }
    }

    private fun decode123DownloadUrl(url: String): String {
        if (!url.contains("params=")) return url
        val params = Uri.parse(url).getQueryParameter("params") ?: return url
        val decoded = runCatching {
            String(Base64.decode(params, Base64.URL_SAFE or Base64.NO_WRAP))
        }.getOrNull().orEmpty()
        return if (decoded.startsWith("http")) decoded else url
    }

    private fun guessFilename(url: String): String {
        val name = runCatching {
            URLDecoder.decode(Uri.parse(url).lastPathSegment.orEmpty(), "UTF-8")
        }.getOrDefault("")
        return name.ifBlank { "xb_share_${System.currentTimeMillis()}" }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
