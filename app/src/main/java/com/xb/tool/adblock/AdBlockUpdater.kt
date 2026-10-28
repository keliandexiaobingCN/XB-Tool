package com.xb.tool.adblock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object AdBlockUpdater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun update(context: Context, urls: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext Result.failure(IllegalArgumentException("没有过滤器地址"))
        val merged = linkedSetOf<String>()
        var success = 0
        var lastError: String? = null
        urls.forEach { url ->
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) XB-Tool")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val text = response.body?.string().orEmpty()
                    merged += AdBlockStore.parseFilterText(text)
                    success++
                }
            }.onFailure { lastError = it.message }
        }
        if (success == 0 || merged.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException(lastError ?: "过滤器为空或下载失败")
            )
        }
        runCatching {
            AdBlockStore.readFilterFile(context).writeText(merged.joinToString("\n"))
            AdBlockStore.saveLastUpdate(context)
            AdBlockStore.reloadRules(context)
            Result.success(merged.size)
        }.getOrElse { Result.failure(it) }
    }
}
