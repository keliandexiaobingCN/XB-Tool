package com.xb.tool.util

import com.xb.tool.network.VirusDbHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {
    fun encodeDbHistory(list: List<VirusDbHistoryEntry>): String {
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("version", item.version)
                    put("source", item.source)
                    put("signatures", item.signatures)
                    put("time", item.time)
                    put("mirror", item.mirror)
                }
            )
        }
        return array.toString()
    }

    fun decodeDbHistory(raw: String?): List<VirusDbHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                VirusDbHistoryEntry(
                    version = item.optString("version"),
                    source = item.optString("source"),
                    signatures = item.optInt("signatures"),
                    time = item.optLong("time"),
                    mirror = item.optString("mirror")
                )
            }
        }.getOrDefault(emptyList())
    }
}
