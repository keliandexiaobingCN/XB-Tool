package com.xb.tool.adblock

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class AdBlockStats(
    val running: Boolean = false,
    val totalQueries: Long = 0,
    val blockedQueries: Long = 0,
    val savedBytes: Long = 0,
    val rulesCount: Int = 0,
    val startedAt: Long = 0,
    val lastDomain: String = "",
    val topBlocked: List<Pair<String, Long>> = emptyList()
) {
    val rate: Float
        get() = if (totalQueries <= 0) 0f else (blockedQueries.toFloat() / totalQueries * 100f)
}

object AdBlockRuntime {
    private val _stats = MutableStateFlow(AdBlockStats())
    val stats = _stats.asStateFlow()

    fun publish(stats: AdBlockStats) {
        _stats.value = stats
    }

    fun setRunning(running: Boolean) {
        _stats.value = _stats.value.copy(running = running)
    }
}

object AdBlockStore {
    private const val PREFS = "xb_adblock"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SAFE_SEARCH = "safe_search"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_FILTER_URLS = "filter_urls"
    private const val KEY_CUSTOM = "custom_rules"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_TOTAL = "total_queries"
    private const val KEY_BLOCKED = "blocked_queries"
    private const val KEY_SAVED = "saved_bytes"
    private const val KEY_LAST_UPDATE = "last_filter_update"
    private const val KEY_TOP = "top_blocked"

    val defaultFilterUrls = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://adaway.org/hosts.txt"
    )

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun safeSearch(context: Context): Boolean = prefs(context).getBoolean(KEY_SAFE_SEARCH, true)

    fun setSafeSearch(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAFE_SEARCH, value).apply()
    }

    fun autoReconnect(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_RECONNECT, true)

    fun setAutoReconnect(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()
    }

    fun filterUrls(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_FILTER_URLS, null)
        if (raw.isNullOrBlank()) return defaultFilterUrls
        return raw.lineSequence().map { it.trim() }.filter { it.startsWith("http") }.toList()
    }

    fun setFilterUrls(context: Context, urls: List<String>) {
        prefs(context).edit().putString(KEY_FILTER_URLS, urls.joinToString("\n")).apply()
    }

    fun customRules(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_CUSTOM, "") ?: ""
        return raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    }

    fun addCustomRule(context: Context, rule: String): Boolean {
        val clean = normalizeRule(rule) ?: return false
        val all = (customRules(context) + clean).distinct()
        prefs(context).edit().putString(KEY_CUSTOM, all.joinToString("\n")).apply()
        reloadRules(context)
        return true
    }

    fun removeCustomRule(context: Context, rule: String) {
        prefs(context).edit().putString(KEY_CUSTOM, (customRules(context) - rule).joinToString("\n")).apply()
        reloadRules(context)
    }

    fun whitelist(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY_WHITELIST, "") ?: ""
        return raw.lineSequence().map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.toSet()
    }

    fun addWhitelist(context: Context, domain: String): Boolean {
        val clean = normalizeDomain(domain) ?: return false
        prefs(context).edit().putString(KEY_WHITELIST, (whitelist(context) + clean).joinToString("\n")).apply()
        reloadRules(context)
        return true
    }

    fun removeWhitelist(context: Context, domain: String) {
        prefs(context).edit().putString(KEY_WHITELIST, (whitelist(context) - domain).joinToString("\n")).apply()
        reloadRules(context)
    }

    fun lastUpdate(context: Context): Long = prefs(context).getLong(KEY_LAST_UPDATE, 0L)

    fun stats(context: Context): AdBlockStats {
        val p = prefs(context)
        return AdBlockStats(
            running = AdBlockRuntime.stats.value.running,
            totalQueries = p.getLong(KEY_TOTAL, 0),
            blockedQueries = p.getLong(KEY_BLOCKED, 0),
            savedBytes = p.getLong(KEY_SAVED, 0),
            rulesCount = RuleStore.rules(context).size,
            startedAt = 0,
            lastDomain = "",
            topBlocked = readTop(context)
        )
    }

    fun saveStats(context: Context, total: Long, blocked: Long, saved: Long, top: Map<String, Long>) {
        val topJson = JSONObject()
        top.entries.sortedByDescending { it.value }.take(8).forEach { topJson.put(it.key, it.value) }
        prefs(context).edit()
            .putLong(KEY_TOTAL, total)
            .putLong(KEY_BLOCKED, blocked)
            .putLong(KEY_SAVED, saved)
            .putString(KEY_TOP, topJson.toString())
            .apply()
    }

    private fun readTop(context: Context): List<Pair<String, Long>> {
        val raw = prefs(context).getString(KEY_TOP, null) ?: return emptyList()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().map { it to json.optLong(it) }
                .sortedByDescending { it.second }.toList()
        }.getOrDefault(emptyList())
    }

    fun saveLastUpdate(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
    }

    fun resetStats(context: Context) {
        prefs(context).edit()
            .putLong(KEY_TOTAL, 0)
            .putLong(KEY_BLOCKED, 0)
            .putLong(KEY_SAVED, 0)
            .remove(KEY_TOP)
            .apply()
    }

    fun reloadRules(context: Context) {
        RuleStore.reload(context)
    }

    fun normalizeRule(raw: String): String? {
        var value = raw.trim()
        if (value.isBlank() || value.startsWith("!") || value.startsWith("#")) return null
        value = value.substringBefore('#').substringBefore(' ').trim()
        value = value.removePrefix("||").removeSuffix("^").removePrefix("*.")
        return normalizeDomain(value)
    }

    fun normalizeDomain(raw: String): String? {
        var value = raw.trim().lowercase(Locale.ROOT)
        value = value.removePrefix("http://").removePrefix("https://")
        value = value.substringBefore('/').substringBefore(':').trim('.')
        if (value.isBlank() || !value.contains('.') || value.length > 253) return null
        if (!value.matches(Regex("^[a-z0-9._-]+$"))) return null
        return value
    }

    fun parseFilterText(text: String): Set<String> {
        val result = linkedSetOf<String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val domain = when {
                line.startsWith("||") -> normalizeRule(line)
                line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1") ->
                    normalizeRule(line.split(Regex("\\s+")).getOrNull(1).orEmpty())
                line.startsWith("#") || line.startsWith("!") || line.isBlank() -> null
                line.contains(' ') || line.contains('\t') -> null
                else -> normalizeRule(line)
            }
            if (domain != null) result += domain
        }
        return result
    }

    private val safeDomains = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
        "xhamster.com", "spankbang.com", "onlyfans.com", "chaturbate.com",
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "applovin.com", "unityads.unity3d.com",
        "facebook.com/ads", "adcolony.com", "vungle.com", "chartboost.com"
    )

    fun isAdultDomain(domain: String, safeSearch: Boolean): Boolean {
        if (!safeSearch) return false
        val host = domain.lowercase(Locale.ROOT)
        return safeDomains.any { host == it || host.endsWith(".$it") }
    }

    fun forceSafeSearchIp(domain: String): ByteArray? {
        val host = domain.lowercase(Locale.ROOT)
        return when (host) {
            "google.com", "www.google.com", "www.google.com.hk" ->
                byteArrayOf(216.toByte(), 239.toByte(), 38, 120)
            "bing.com", "www.bing.com" ->
                byteArrayOf(204.toByte(), 79.toByte(), 197.toByte(), 220.toByte())
            else -> null
        }
    }

    fun readFilterFile(context: Context): File = File(context.filesDir, "ad_filters_remote.txt")

    fun loadBundled(context: Context): Set<String> = runCatching {
        context.assets.open("ad_filters.txt").bufferedReader().use { parseFilterText(it.readText()) }
    }.getOrDefault(emptySet())

    fun loadRemote(context: Context): Set<String> {
        val file = readFilterFile(context)
        if (!file.isFile) return emptySet()
        return runCatching { parseFilterText(file.readText()) }.getOrDefault(emptySet())
    }

    fun topBlockedJson(context: Context): List<Pair<String, Long>> = readTop(context)
}

/**
 * Rule matching uses suffix checks, so a rule "doubleclick.net" also blocks
 * "ad.doubleclick.net". A small whitelist short-circuits matches.
 */
object RuleStore {
    @Volatile private var cached: Set<String> = emptySet()
    @Volatile private var cachedWhitelist: Set<String> = emptySet()
    @Volatile private var cachedCustom: Set<String> = emptySet()

    fun rules(context: Context): Set<String> {
        if (cached.isEmpty()) reload(context)
        return cached
    }

    @Synchronized
    fun reload(context: Context) {
        val bundled = AdBlockStore.loadBundled(context)
        val remote = AdBlockStore.loadRemote(context)
        val custom = AdBlockStore.customRules(context).mapNotNull { AdBlockStore.normalizeRule(it) }.toSet()
        cached = (bundled + remote + custom)
        cachedWhitelist = AdBlockStore.whitelist(context)
        cachedCustom = custom
    }

    fun isBlocked(context: Context, domain: String): Boolean {
        if (cached.isEmpty()) reload(context)
        val host = domain.lowercase(Locale.ROOT).trim('.')
        if (host.isBlank()) return false
        if (cachedWhitelist.any { host == it || host.endsWith(".$it") }) return false
        if (AdBlockStore.isAdultDomain(host, AdBlockStore.safeSearch(context))) return true
        var probe = host
        while (true) {
            if (cached.contains(probe)) return true
            val index = probe.indexOf('.')
            if (index < 0) break
            probe = probe.substring(index + 1)
            if (!probe.contains('.')) break
        }
        return false
    }

    fun customCount(): Int = cachedCustom.size
}
