package com.xb.tool.network

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DbMirror(
    val name: String,
    val url: String,
    val sha256: String = "",
    val format: String = "json"
)

data class VirusDbUpdateInfo(
    val version: String,
    val url: String,
    val sha256: String,
    val notes: String,
    val mirrors: List<DbMirror> = emptyList()
) {
    fun allSources(): List<DbMirror> {
        val list = mutableListOf<DbMirror>()
        if (url.isNotBlank()) {
            list += DbMirror("清单主源", url, sha256)
        }
        list += mirrors.filter { it.url.isNotBlank() }
        return list.distinctBy { it.url }
    }
}

data class ChangelogEntry(
    val version: String,
    val date: String,
    val notes: List<String>,
    val tag: String = ""
)

data class VirusDbHistoryEntry(
    val version: String,
    val source: String,
    val signatures: Int,
    val time: Long,
    val mirror: String
) {
    fun timeText(): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))
}