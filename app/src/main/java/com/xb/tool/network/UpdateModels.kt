package com.xb.tool.network

data class Announcement(
    val id: String,
    val title: String,
    val date: String,
    val tag: String,
    val content: String,
    val important: Boolean = false
)

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val notes: String
)

data class UpdateManifest(
    val app: AppUpdateInfo?,
    val virusDb: VirusDbUpdateInfo?,
    val announcements: List<Announcement>,
    val changelog: List<ChangelogEntry> = emptyList(),
    val source: String = "内置",
    val fetchedAt: Long = 0L
)