package com.xb.tool.scan

import android.content.Context
import com.xb.tool.core.Severity
import org.json.JSONObject
import java.io.File

data class VirusDatabase(
    val version: String,
    val released: String,
    val hashes: Map<String, Entry>,
    val patterns: List<Entry>,
    val names: List<NameRule>,
    val source: String = "内置"
) {
    data class Entry(
        val name: String,
        val severity: Severity,
        val description: String,
        val text: String = ""
    )

    data class NameRule(
        val regex: Regex,
        val name: String,
        val severity: Severity,
        val description: String
    )

    val signatureCount: Int get() = hashes.size + patterns.size + names.size

    companion object {
        const val LATEST_VERSION = "2024.11.02"
        const val REMOTE_FILE = "virus_db_remote.json"

        private fun severity(raw: String?): Severity = when (raw?.lowercase()) {
            "high" -> Severity.HIGH
            "low" -> Severity.LOW
            else -> Severity.MEDIUM
        }

        fun parse(raw: String, source: String = "更新"): VirusDatabase? {
            val trimmed = raw.trimStart()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("{")) {
                return parseHashList(raw, source)
            }
            return runCatching {
                val json = JSONObject(raw)
                val hashes = linkedMapOf<String, Entry>()
                json.optJSONArray("hashes")?.let { array ->
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val hash = item.optString("sha256").lowercase()
                        if (hash.length == 64) {
                            hashes[hash] = Entry(
                                name = item.optString("name", "Unknown"),
                                severity = severity(item.optString("severity")),
                                description = item.optString("desc")
                            )
                        }
                    }
                }
                val patterns = mutableListOf<Entry>()
                json.optJSONArray("patterns")?.let { array ->
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val text = item.optString("text")
                        if (text.isNotBlank()) {
                            patterns += Entry(
                                name = item.optString("name", "Unknown"),
                                severity = severity(item.optString("severity")),
                                description = item.optString("desc"),
                                text = text
                            )
                        }
                    }
                }
                val names = mutableListOf<NameRule>()
                json.optJSONArray("names")?.let { array ->
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val pattern = item.optString("pattern")
                        if (pattern.isNotBlank()) {
                            names += NameRule(
                                regex = Regex(pattern),
                                name = item.optString("name", "Heuristic"),
                                severity = severity(item.optString("severity")),
                                description = item.optString("desc")
                            )
                        }
                    }
                }
                if (hashes.isEmpty() && patterns.isEmpty() && names.isEmpty()) return null
                VirusDatabase(
                    version = json.optString("version", LATEST_VERSION),
                    released = json.optString("released", "—"),
                    hashes = hashes,
                    patterns = patterns,
                    names = names,
                    source = source
                )
            }.getOrNull()
        }

        private fun parseHashList(raw: String, source: String): VirusDatabase? {
            val hashes = linkedMapOf<String, Entry>()
            var index = 0
            raw.lineSequence().forEach { line ->
                val token = line.trim().substringBefore(',').substringBefore('\t').substringBefore(' ').lowercase()
                if (token.length == 64 && token.matches(Regex("^[a-f0-9]{64}$"))) {
                    index++
                    if (index <= 50_000) {
                        hashes[token] = Entry(
                            name = "公开恶意哈希 #$index",
                            severity = Severity.MEDIUM,
                            description = "来自公开恶意样本 SHA-256 哈希列表（$source）"
                        )
                    }
                }
            }
            if (hashes.isEmpty()) return null
            val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(java.util.Date())
            return VirusDatabase(
                version = "HASH-$date",
                released = date,
                hashes = hashes,
                patterns = emptyList(),
                names = emptyList(),
                source = source
            )
        }

        fun builtIn(context: Context): VirusDatabase {
            val raw = runCatching {
                context.assets.open("virus_db.json").bufferedReader().use { it.readText() }
            }.getOrDefault("")
            val base = parse(raw, "内置") ?: fallback()
            // 内置公开哈希特征：即使公共更新源不可用，安装后也自带一批恶意样本哈希。
            val builtinHashes = runCatching {
                context.assets.open(BUILTIN_HASH_FILE).bufferedReader().useLines { lines ->
                    val map = linkedMapOf<String, Entry>()
                    var index = 0
                    lines.forEach { line ->
                        val token = line.trim().lowercase()
                        if (token.length == 64 && token.matches(HEX64)) {
                            index++
                            map[token] = Entry(
                                name = "内置公开恶意哈希 #$index",
                                severity = Severity.MEDIUM,
                                description = "随应用内置的公开恶意样本 SHA-256 特征（离线可用）"
                            )
                        }
                    }
                    map
                }
            }.getOrDefault(emptyMap())
            if (builtinHashes.isEmpty()) return base
            return base.copy(
                hashes = builtinHashes + base.hashes,
                released = base.released,
                source = if (base.source == "内置") "内置 + 公开哈希" else base.source
            )
        }

        private val HEX64 = Regex("^[a-f0-9]{64}$")
        const val BUILTIN_HASH_FILE = "virus_hashes_builtin.txt"

        fun merge(base: VirusDatabase, overlay: VirusDatabase): VirusDatabase {
            return base.copy(
                version = overlay.version.ifBlank { base.version },
                released = overlay.released.ifBlank { base.released },
                hashes = base.hashes + overlay.hashes,
                patterns = (base.patterns + overlay.patterns).distinctBy { it.text },
                names = (base.names + overlay.names).distinctBy { it.regex.pattern },
                source = overlay.source
            )
        }

        fun load(context: Context): VirusDatabase {
            val base = builtIn(context)
            val remote = File(context.filesDir, REMOTE_FILE)
            if (remote.isFile) {
                parse(remote.readText(), "在线更新")?.let { return merge(base, it) }
            }
            return base
        }

        fun fallback(): VirusDatabase = VirusDatabase(
            version = LATEST_VERSION,
            released = "—",
            hashes = emptyMap(),
            patterns = emptyList(),
            names = emptyList(),
            source = "内置"
        )
    }
}
