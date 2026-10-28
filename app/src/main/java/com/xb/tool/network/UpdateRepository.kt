package com.xb.tool.network

import android.content.Context
import com.xb.tool.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object UpdateRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun localManifest(context: Context): UpdateManifest {
        val raw = runCatching {
            context.assets.open("update_manifest.json").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        return parse(raw, "内置") ?: UpdateManifest(
            app = null,
            virusDb = null,
            announcements = emptyList(),
            source = "内置"
        )
    }

    suspend fun fetchRemote(url: String): UpdateManifest = withContext(Dispatchers.IO) {
        if (url.isBlank()) throw IllegalStateException("更新源地址为空")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "XB-Tool/1.3.7 (Android)")
            .header("Accept", "application/json,text/plain,*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("更新源返回 HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IllegalStateException("更新源内容为空")
            parse(body, "在线") ?: throw IllegalStateException("更新源 JSON 格式不正确")
        }
    }

    suspend fun fetchVirusDb(url: String, expectedSha256: String): String =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) throw IllegalStateException("病毒库下载地址为空")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "XB-Tool/1.3.7 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val hint = if (response.code == 405) {
                        "（源站不允许直接下载/已变更接口，请改用公开哈希库或自建 JSON）"
                    } else ""
                    throw IllegalStateException("病毒库下载失败：HTTP ${response.code}$hint")
                }
                val bytes = response.body?.bytes() ?: throw IllegalStateException("病毒库内容为空")
                if (bytes.size < 32) throw IllegalStateException("病毒库文件过小")
                val actual = sha256(bytes)
                if (expectedSha256.isNotBlank() && !actual.equals(expectedSha256, true)) {
                    throw IllegalStateException("病毒库 SHA-256 校验失败")
                }
                String(bytes, Charsets.UTF_8)
            }
        }

    suspend fun downloadApk(
        url: String,
        expectedSha256: String,
        destination: File,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (url.isBlank()) throw IllegalStateException("安装包下载地址为空")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "XB-Tool/1.3.7 (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("安装包下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("安装包内容为空")
            val total = body.contentLength()
            destination.parentFile?.mkdirs()
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (total > 0) downloaded.toFloat() / total else 0f
                        onProgress(progress, "下载中 ${FileUtils.formatBytes(downloaded)}")
                    }
                }
            }
            if (expectedSha256.isNotBlank()) {
                val actual = fileSha256(destination)
                if (!actual.equals(expectedSha256, true)) {
                    destination.delete()
                    throw IllegalStateException("安装包 SHA-256 校验失败")
                }
            }
            onProgress(1f, "下载完成")
            destination
        }
    }

    private fun parse(raw: String, source: String): UpdateManifest? {
        return runCatching {
            val json = JSONObject(raw)
            val appJson = json.optJSONObject("app")
            val app = appJson?.let {
                AppUpdateInfo(
                    versionCode = it.optInt("versionCode", 0),
                    versionName = it.optString("versionName", ""),
                    url = it.optString("url", ""),
                    sha256 = it.optString("sha256", ""),
                    notes = it.optString("notes", "")
                )
            }
            val dbJson = json.optJSONObject("virusDb")
            val virusDb = dbJson?.let {
                val mirrors = mutableListOf<DbMirror>()
                it.optJSONArray("mirrors")?.let { array ->
                    for (i in 0 until array.length()) {
                        val mirror = array.optJSONObject(i) ?: continue
                        val url = mirror.optString("url", "")
                        if (url.isBlank()) continue
                        mirrors += DbMirror(
                            name = mirror.optString("name", "镜像 ${i + 1}"),
                            url = url,
                            sha256 = mirror.optString("sha256", ""),
                            format = mirror.optString("format", "json")
                        )
                    }
                }
                VirusDbUpdateInfo(
                    version = it.optString("version", ""),
                    url = it.optString("url", ""),
                    sha256 = it.optString("sha256", ""),
                    notes = it.optString("notes", ""),
                    mirrors = mirrors
                )
            }
            val announcements = mutableListOf<Announcement>()
            json.optJSONArray("announcements")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optString("id").ifBlank { "a-$i" }
                    announcements += Announcement(
                        id = id,
                        title = item.optString("title", "公告"),
                        date = item.optString("date", ""),
                        tag = item.optString("tag", "公告"),
                        content = item.optString("content", ""),
                        important = item.optBoolean("important", false)
                    )
                }
            }
            val changelog = mutableListOf<ChangelogEntry>()
            json.optJSONArray("changelog")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val notes = mutableListOf<String>()
                    item.optJSONArray("notes")?.let { noteArray ->
                        for (j in 0 until noteArray.length()) {
                            val note = noteArray.optString(j, "").trim()
                            if (note.isNotBlank()) notes += note
                        }
                    }
                    changelog += ChangelogEntry(
                        version = item.optString("version", ""),
                        date = item.optString("date", ""),
                        notes = notes,
                        tag = item.optString("tag", "")
                    )
                }
            }
            UpdateManifest(
                app = app,
                virusDb = virusDb,
                announcements = announcements,
                changelog = changelog,
                source = source,
                fetchedAt = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
