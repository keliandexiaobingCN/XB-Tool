package com.xb.tool.core

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    companion object {
        const val DEFAULT_UPDATE_URL =
            "https://fastly.jsdelivr.net/gh/keliandexiaobingCN/XB-Tool@main/update.json"

        const val FALLBACK_UPDATE_URL =
            "https://raw.githubusercontent.com/keliandexiaobingCN/XB-Tool/main/update.json"
    }

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("xb_prefs", Context.MODE_PRIVATE)

    var agreementAccepted: Boolean
        get() = sp.getBoolean("agreement_accepted", false)
        set(value) = sp.edit().putBoolean("agreement_accepted", value).apply()

    var rootRemind: Boolean
        get() = sp.getBoolean("root_remind", true)
        set(value) = sp.edit().putBoolean("root_remind", value).apply()

    var keepAlive: Boolean
        get() = sp.getBoolean("keep_alive", true)
        set(value) = sp.edit().putBoolean("keep_alive", value).apply()

    var extremeKeepAlive: Boolean
        get() = sp.getBoolean("extreme_keep_alive", false)
        set(value) = sp.edit().putBoolean("extreme_keep_alive", value).apply()

    /** 实时防护模式：low / normal / strict / shell。 */
    var realtimeLevel: String
        get() = sp.getString("realtime_level", "normal") ?: "normal"
        set(value) = sp.edit().putString("realtime_level", value).apply()

    /** 高危命令防护：监控应用进程执行的 shell 命令。 */
    var commandGuardEnabled: Boolean
        get() = sp.getBoolean("command_guard_enabled", true)
        set(value) = sp.edit().putBoolean("command_guard_enabled", value).apply()

    /** 高危命令防护白名单（包名）。 */
    fun commandWhitelist(): MutableSet<String> =
        (sp.getStringSet("command_whitelist", emptySet()) ?: emptySet()).toMutableSet()

    fun setCommandWhitelist(value: Set<String>) =
        sp.edit().putStringSet("command_whitelist", value.toSet()).apply()

    var commandAlertsJson: String?
        get() = sp.getString("command_alerts", null)
        set(value) = sp.edit().putString("command_alerts", value).apply()

    /** 命中高危命令时是否弹出阻断窗口（关闭则只记录）。 */
    var commandAlertPopup: Boolean
        get() = sp.getBoolean("command_alert_popup", true)
        set(value) = sp.edit().putBoolean("command_alert_popup", value).apply()

    /** 已提示过的可疑应用（避免重复弹窗）。 */
    var suspiciousPromptedJson: String?
        get() = sp.getString("suspicious_prompted", null)
        set(value) = sp.edit().putString("suspicious_prompted", value).apply()

    var lastCommandPoll: Long
        get() = sp.getLong("last_command_poll", 0L)
        set(value) = sp.edit().putLong("last_command_poll", value).apply()

    var realtimeEnabled: Boolean
        get() = sp.getBoolean("realtime_enabled", false)
        set(value) = sp.edit().putBoolean("realtime_enabled", value).apply()

    var notifyScan: Boolean
        get() = sp.getBoolean("notify_scan", true)
        set(value) = sp.edit().putBoolean("notify_scan", value).apply()

    var notifyIntercept: Boolean
        get() = sp.getBoolean("notify_intercept", true)
        set(value) = sp.edit().putBoolean("notify_intercept", value).apply()

    /** 命中威胁时是否弹出全屏告警窗口（关闭则只发通知）。 */
    var threatAlertPopup: Boolean
        get() = sp.getBoolean("threat_alert_popup", true)
        set(value) = sp.edit().putBoolean("threat_alert_popup", value).apply()

    var autoCheckUpdate: Boolean
        get() = sp.getBoolean("auto_check_update", true)
        set(value) = sp.edit().putBoolean("auto_check_update", value).apply()

    var autoCheckIntervalMin: Int
        get() = sp.getInt("auto_check_interval_min", 60)
        set(value) = sp.edit().putInt("auto_check_interval_min", value.coerceIn(1, 1440)).apply()

    var autoUpdateVirusDb: Boolean
        get() = sp.getBoolean("auto_update_virus_db", true)
        set(value) = sp.edit().putBoolean("auto_update_virus_db", value).apply()

    var virusDbPromptShown: Boolean
        get() = sp.getBoolean("virus_db_prompt_shown", false)
        set(value) = sp.edit().putBoolean("virus_db_prompt_shown", value).apply()

    var notifyUpdate: Boolean
        get() = sp.getBoolean("notify_update", true)
        set(value) = sp.edit().putBoolean("notify_update", value).apply()

    var notifyPermission: Boolean
        get() = sp.getBoolean("notify_permission", true)
        set(value) = sp.edit().putBoolean("notify_permission", value).apply()

    var themeSeed: String
        get() = sp.getString("theme_seed", "teal") ?: "teal"
        set(value) = sp.edit().putString("theme_seed", value).apply()

    var darkMode: String
        get() = sp.getString("dark_mode", "dark") ?: "dark"
        set(value) = sp.edit().putString("dark_mode", value).apply()

    var fontScale: Float
        get() = sp.getFloat("font_scale", 1f)
        set(value) = sp.edit().putFloat("font_scale", value).apply()

    var fontFamily: String
        get() = sp.getString("font_family", "default") ?: "default"
        set(value) = sp.edit().putString("font_family", value).apply()

    var backgroundPath: String?
        get() = sp.getString("background_path", null)
        set(value) = sp.edit().putString("background_path", value).apply()

    var backgroundVideoPath: String?
        get() = sp.getString("background_video_path", null)
        set(value) = sp.edit().putString("background_video_path", value).apply()

    var backgroundBlur: Float
        get() = sp.getFloat("background_blur", 0f)
        set(value) = sp.edit().putFloat("background_blur", value.coerceIn(0f, 24f)).apply()

    var backgroundAlpha: Float
        get() = sp.getFloat("background_alpha", 1f)
        set(value) = sp.edit().putFloat("background_alpha", value.coerceIn(0.15f, 1f)).apply()

    var uiAnimation: Boolean
        get() = sp.getBoolean("ui_animation", true)
        set(value) = sp.edit().putBoolean("ui_animation", value).apply()

    var navStyle: String
        get() = sp.getString("nav_style", "slide") ?: "slide"
        set(value) = sp.edit().putString("nav_style", value).apply()

    var backgroundColor: String?
        get() = sp.getString("background_color", null)
        set(value) = sp.edit().putString("background_color", value).apply()

    var glassAlpha: Float
        get() = sp.getFloat("glass_alpha", 0.42f)
        set(value) = sp.edit().putFloat("glass_alpha", value.coerceIn(0.15f, 0.95f)).apply()

    var glassEffect: Boolean
        get() = sp.getBoolean("glass_effect", true)
        set(value) = sp.edit().putBoolean("glass_effect", value).apply()

    var updateManifestUrl: String
        get() = sp.getString("update_manifest_url", DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL
        set(value) = sp.edit().putString("update_manifest_url", value.trim()).apply()

    var announcementCacheVersion: String?
        get() = sp.getString("announcement_cache_version", null)
        set(value) = sp.edit().putString("announcement_cache_version", value).apply()

    var announcementsJson: String?
        get() = sp.getString("announcements_json", null)
        set(value) = sp.edit().putString("announcements_json", value).apply()

    var lastAnnouncementSeen: String?
        get() = sp.getString("last_announcement_seen", null)
        set(value) = sp.edit().putString("last_announcement_seen", value).apply()

    var virusDbDirectUrl: String
        get() = sp.getString("virus_db_direct_url", "") ?: ""
        set(value) = sp.edit().putString("virus_db_direct_url", value).apply()

    var guardHeartbeat: Long
        get() = sp.getLong("guard_heartbeat", 0L)
        set(value) = sp.edit().putLong("guard_heartbeat", value).apply()

    var watchdogHeartbeat: Long
        get() = sp.getLong("watchdog_heartbeat", 0L)
        set(value) = sp.edit().putLong("watchdog_heartbeat", value).apply()

    var dbVersion: String
        get() = sp.getString("db_version", "2024.11.02") ?: "2024.11.02"
        set(value) = sp.edit().putString("db_version", value).apply()

    var dbHistory: String
        get() = sp.getString("db_history", "[]") ?: "[]"
        set(value) = sp.edit().putString("db_history", value).apply()

    var realtimeLastAt: Long
        get() = sp.getLong("realtime_last_at", 0L)
        set(value) = sp.edit().putLong("realtime_last_at", value).apply()

    var realtimeScanned: Int
        get() = sp.getInt("realtime_scanned", 0)
        set(value) = sp.edit().putInt("realtime_scanned", value).apply()

    var realtimeBlocked: Int
        get() = sp.getInt("realtime_blocked", 0)
        set(value) = sp.edit().putInt("realtime_blocked", value).apply()

    /** 严格模式巡检：最近一次全盘扫描的时间、应用数与命中数。 */
    var strictSweepAt: Long
        get() = sp.getLong("strict_sweep_at", 0L)
        set(value) = sp.edit().putLong("strict_sweep_at", value).apply()

    var strictSweepApps: Int
        get() = sp.getInt("strict_sweep_apps", 0)
        set(value) = sp.edit().putInt("strict_sweep_apps", value).apply()

    var strictSweepThreats: Int
        get() = sp.getInt("strict_sweep_threats", 0)
        set(value) = sp.edit().putInt("strict_sweep_threats", value).apply()

    var lastScanAt: Long
        get() = sp.getLong("last_scan_at", 0L)
        set(value) = sp.edit().putLong("last_scan_at", value).apply()

    var lastScanThreats: Int
        get() = sp.getInt("last_scan_threats", 0)
        set(value) = sp.edit().putInt("last_scan_threats", value).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(value) = sp.edit().putLong("last_update_check", value).apply()

    var lastDbCheck: Long
        get() = sp.getLong("last_db_check", 0L)
        set(value) = sp.edit().putLong("last_db_check", value).apply()

    var notificationAsked: Boolean
        get() = sp.getBoolean("notification_asked", false)
        set(value) = sp.edit().putBoolean("notification_asked", value).apply()

    var allFilesAsked: Boolean
        get() = sp.getBoolean("all_files_asked", false)
        set(value) = sp.edit().putBoolean("all_files_asked", value).apply()

    var batteryOptimized: Boolean
        get() = sp.getBoolean("battery_optimized", false)
        set(value) = sp.edit().putBoolean("battery_optimized", value).apply()

    var backgroundUnrestricted: Boolean
        get() = sp.getBoolean("background_unrestricted", false)
        set(value) = sp.edit().putBoolean("background_unrestricted", value).apply()

    var autoStartEnabled: Boolean
        get() = sp.getBoolean("auto_start_enabled", false)
        set(value) = sp.edit().putBoolean("auto_start_enabled", value).apply()

    var deepPreferred: String
        get() = sp.getString("deep_preferred", "auto") ?: "auto"
        set(value) = sp.edit().putString("deep_preferred", value).apply()

    fun whitelist(): MutableSet<String> =
        (sp.getStringSet("whitelist", emptySet()) ?: emptySet()).toMutableSet()

    fun setWhitelist(value: Set<String>) =
        sp.edit().putStringSet("whitelist", value.toSet()).apply()

    fun frozenApps(): MutableSet<String> =
        (sp.getStringSet("frozen_apps", emptySet()) ?: emptySet()).toMutableSet()

    fun setFrozenApps(value: Set<String>) =
        sp.edit().putStringSet("frozen_apps", value.toSet()).apply()

    var rootVerified: Boolean
        get() = sp.getBoolean("root_verified", false)
        set(value) = sp.edit().putBoolean("root_verified", value).apply()

    var interceptJson: String?
        get() = sp.getString("intercepts", null)
        set(value) = sp.edit().putString("intercepts", value).apply()

    var lastSelfCheck: Long
        get() = sp.getLong("last_self_check", 0L)
        set(value) = sp.edit().putLong("last_self_check", value).apply()
}
