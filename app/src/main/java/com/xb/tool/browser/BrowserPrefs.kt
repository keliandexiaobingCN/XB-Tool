package com.xb.tool.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Bookmark(val id: String, val title: String, val url: String)

object BrowserPrefs {
    private const val FILE = "xb_browser"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun engine(context: Context): String = prefs(context).getString("engine", "bing") ?: "bing"

    fun setEngine(context: Context, value: String) {
        prefs(context).edit().putString("engine", value).apply()
    }

    fun incognito(context: Context): Boolean = prefs(context).getBoolean("incognito", true)

    fun setIncognito(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("incognito", value).apply()
    }

    fun darkMode(context: Context): String = prefs(context).getString("dark", "system") ?: "system"

    fun setDarkMode(context: Context, value: String) {
        prefs(context).edit().putString("dark", value).apply()
    }

    fun adblock(context: Context): Boolean = prefs(context).getBoolean("plugin_adblock", true)

    fun setAdblock(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("plugin_adblock", value).apply()
    }

    fun desktop(context: Context): Boolean = prefs(context).getBoolean("plugin_desktop", false)

    fun setDesktop(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("plugin_desktop", value).apply()
    }

    fun bookmarks(context: Context): List<Bookmark> {
        val raw = prefs(context).getString("bookmarks", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                Bookmark(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", obj.optString("url")),
                    url = obj.optString("url")
                )
            }.filter { it.url.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun addBookmark(context: Context, title: String, url: String) {
        if (url.isBlank()) return
        val list = bookmarks(context).toMutableList()
        if (list.any { it.url == url }) return
        list.add(0, Bookmark(UUID.randomUUID().toString(), title.ifBlank { url }, url))
        write(context, list)
    }

    fun removeBookmark(context: Context, url: String) {
        write(context, bookmarks(context).filterNot { it.url == url })
    }

    fun clearBookmarks(context: Context) {
        write(context, emptyList())
    }

    private fun write(context: Context, list: List<Bookmark>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("url", it.url)
            })
        }
        prefs(context).edit().putString("bookmarks", array.toString()).apply()
    }

    fun searchUrl(engine: String, query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // 一律走移动端搜索入口：桌面版结果页在手机上要么宽到要左右拖，
        // 要么被站点判定为「非移动浏览器」而跳安全验证页。
        return when (engine) {
            "google" -> "https://www.google.com/search?q=$encoded&hl=zh-CN"
            "baidu" -> "https://m.baidu.com/s?word=$encoded&ie=utf-8"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            else -> "https://cn.bing.com/search?q=$encoded&mkt=zh-CN&ensearch=0"
        }
    }

    fun engineHome(engine: String): String = when (engine) {
        "google" -> "https://www.google.com/"
        "baidu" -> "https://m.baidu.com/"
        "duckduckgo" -> "https://duckduckgo.com/"
        else -> "https://cn.bing.com/"
    }

    fun engineName(engine: String): String = when (engine) {
        "google" -> "Google"
        "baidu" -> "百度"
        "duckduckgo" -> "DuckDuckGo"
        else -> "Bing"
    }
}
