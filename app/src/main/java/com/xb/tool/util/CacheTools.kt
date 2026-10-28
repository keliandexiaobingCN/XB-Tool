package com.xb.tool.util

import android.content.Context
import java.io.File

object CacheTools {
    data class CacheInfo(
        val total: Long = 0L,
        val cache: Long = 0L,
        val codeCache: Long = 0L,
        val externalCache: Long = 0L
    )

    fun measure(context: Context): CacheInfo {
        val cache = FileUtils.dirSize(context.cacheDir)
        val code = FileUtils.dirSize(context.codeCacheDir)
        val external = context.externalCacheDir?.let { FileUtils.dirSize(it) } ?: 0L
        return CacheInfo(cache + code + external, cache, code, external)
    }

    fun clear(context: Context): Long {
        var freed = FileUtils.deleteContents(context.cacheDir)
        freed += FileUtils.deleteContents(context.codeCacheDir)
        context.externalCacheDir?.let { freed += FileUtils.deleteContents(it) }
        return freed
    }
}
