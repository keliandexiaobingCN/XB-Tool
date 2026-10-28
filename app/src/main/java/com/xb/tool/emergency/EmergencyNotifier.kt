package com.xb.tool.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.xb.tool.R

object EmergencyNotifier {
    const val CHANNEL = "xb_emergency"
    const val NOTIFICATION_ID = 1201
    private const val PREFS = "xb_emergency"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SHAKE = "shake_enabled"
    private const val KEY_BUTTON = "button_enabled"
    private const val KEY_PRESS_COUNT = "button_press_count"
    private const val KEY_SHAKE_LEVEL = "shake_level"
    private const val KEY_BUTTON_KEY = "button_key"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun isShakeEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_SHAKE, false)

    fun setShakeEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHAKE, value).apply()
    }

    fun isButtonEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BUTTON, true)

    fun setButtonEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_BUTTON, value).apply()
    }

    /** 连按次数 1..10，默认 3。 */
    fun pressCount(context: Context): Int =
        prefs(context).getInt(KEY_PRESS_COUNT, 3).coerceIn(1, 10)

    fun setPressCount(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_PRESS_COUNT, value.coerceIn(1, 10)).apply()
    }

    /** 摇一摇力度：standard / hard / violent。 */
    fun shakeLevel(context: Context): String =
        prefs(context).getString(KEY_SHAKE_LEVEL, "violent") ?: "violent"

    fun setShakeLevel(context: Context, value: String) {
        prefs(context).edit().putString(KEY_SHAKE_LEVEL, value).apply()
    }

    /**
     * 摇一摇阈值（单位 m/s²，指扣除重力后的加速度增量）。
     *
     * 为什么定这么高：
     *  手机放在口袋走路、从桌上拿起、放下的冲击大约在 8~15 m/s²；
     *  甩动手臂时峰值能到 30~45 m/s²，只有「刻意猛甩」才会稳定超过 90。
     *  历史版本依次用过 12/16/20 → 36/48/62，仍会被日常动作触发，
     *  所以这一版再整体上移到 52/70/95，并把判定次数提高到 6/7/8 次往复。
     *  宁可多摇两下，也不要让它在口袋里自己弹救援界面。
     */
    fun shakeThreshold(context: Context): Float = when (shakeLevel(context)) {
        // 猛摇：必须是「用力甩」的级别。日常拿起、放下、走路颠簸、口袋里晃动
        // 实测都在 25 m/s² 以内，把猛摇抬到 95 才能做到「不想触发就绝不会触发」。
        "violent" -> 95f
        // 用力：明确有意识的快速摇动
        "hard" -> 70f
        // 标准：仍需明显摇动，但不需要甩到极限
        else -> 52f
    }

    /** 需要在窗口内累计多少次「来回」冲击才算摇一摇。 */
    fun shakeCount(context: Context): Int = when (shakeLevel(context)) {
        "violent" -> 7
        "hard" -> 6
        else -> 5
    }

    /** 冲击统计窗口（毫秒）。窗口太短会把一次连续摇动拆成多段。 */
    fun shakeWindowMs(context: Context): Long = when (shakeLevel(context)) {
        "violent" -> 1_800L
        "hard" -> 2_100L
        else -> 2_400L
    }

    fun shakeLevelName(context: Context): String = when (shakeLevel(context)) {
        "violent" -> "猛摇"
        "hard" -> "用力"
        else -> "标准"
    }

    /** 触发按键：volume_up / volume_down / power。 */
    fun buttonKey(context: Context): String =
        prefs(context).getString(KEY_BUTTON_KEY, "volume_up") ?: "volume_up"

    fun setButtonKey(context: Context, value: String) {
        prefs(context).edit().putString(KEY_BUTTON_KEY, value).apply()
    }

    fun buttonKeyName(context: Context): String = when (buttonKey(context)) {
        "volume_down" -> "音量 −"
        "power" -> "电源键"
        else -> "音量 +"
    }

    /** 无障碍服务是否已连接（决定音量键能否在任意界面触发）。 */
    fun isAccessibilityConnected(context: Context): Boolean =
        prefs(context).getBoolean("accessibility_connected", false)

    fun setAccessibilityConnected(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("accessibility_connected", value).apply()
    }

    fun hasAnyTrigger(context: Context): Boolean =
        isShakeEnabled(context) || isButtonEnabled(context)

    fun trigger(context: Context, source: String) {
        if (!isEnabled(context)) return
        createChannel(context)
        val intent = Intent(context, EmergencyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("source", source)
        }
        val pending = PendingIntent.getActivity(
            context,
            21,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle("XB工具 · 紧急救机")
            .setContentText("检测到紧急触发（$source），正在打开救援界面")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pending, true)
            .setTimeoutAfter(60_000L)
            .setAutoCancel(true)
            .addAction(0, "打开救援", pending)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openDeviceAdminSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "紧急救机", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "防锁机紧急救援与全屏提醒"
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
            )
        }
    }
}
