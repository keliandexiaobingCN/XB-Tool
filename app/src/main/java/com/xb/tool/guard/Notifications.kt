package com.xb.tool.guard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xb.tool.MainActivity
import com.xb.tool.R
import com.xb.tool.core.Threat
import com.xb.tool.util.FileUtils

object Notifications {
    const val CHANNEL_GUARD = "xb_guard"
    const val CHANNEL_SCAN = "xb_scan"
    const val CHANNEL_INTERCEPT = "xb_intercept"
    const val CHANNEL_WATCHDOG = "xb_watchdog"

    const val ID_GUARD = 1001
    const val ID_SCAN = 1002
    const val ID_INTERCEPT = 1003
    const val ID_WATCHDOG = 1004

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_GUARD, "后台守护", NotificationManager.IMPORTANCE_LOW).apply {
                description = "实时检测与后台保活常驻通知"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SCAN, "扫描结果", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "病毒扫描完成与威胁提醒"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INTERCEPT, "实时拦截", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "实时检测发现的威胁与拦截记录"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCHDOG, "互拉守护", NotificationManager.IMPORTANCE_LOW).apply {
                description = "极致后台保活的看门狗常驻通知"
                setShowBadge(false)
            }
        )
    }

    fun areEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun guardNotification(context: Context, realtime: Boolean): Notification {
        val stopIntent = Intent(context, GuardService::class.java).apply {
            action = GuardService.ACTION_STOP_REALTIME
        }
        val stopPending = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (realtime) "XB工具 · 实时防护运行中" else "XB工具 · 后台守护中"
        val text = if (realtime) {
            "正在监控下载目录与新安装应用"
        } else {
            "检测模块常驻，点击可打开"
        }
        return NotificationCompat.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "停止实时检测", stopPending)
            .build()
    }

    fun notifyGuard(context: Context, realtime: Boolean) {
        if (!areEnabled(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_GUARD, guardNotification(context, realtime))
        }
    }

    fun watchdogNotification(context: Context): Notification {
        val stopIntent = Intent(context, com.xb.tool.guard.WatchdogService::class.java).apply {
            action = com.xb.tool.guard.WatchdogService.ACTION_STOP_EXTREME
        }
        val stopPending = PendingIntent.getService(
            context,
            4,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_WATCHDOG)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle("XB工具 · 极致保活互拉守护")
            .setContentText("守护进程互相监测，后台被杀后自动尝试拉起")
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "关闭极致保活", stopPending)
            .build()
    }

    fun notifyScanResult(context: Context, count: Int) {
        if (!areEnabled(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle("扫描完成")
            .setContentText(if (count == 0) "未发现威胁，设备状态良好" else "发现 $count 个威胁，点击查看并处理")
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SCAN, notification) }
    }

    fun notifyIntercept(context: Context, threat: Threat) {
        if (!areEnabled(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_INTERCEPT)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle("已拦截可疑文件")
            .setContentText("${threat.name} · ${FileUtils.formatBytes(runCatching { java.io.File(threat.path).length() }.getOrDefault(0L))}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${threat.name}\n${threat.path}\n${threat.description}"
                )
            )
            // 点通知直接进入「发现威胁」处置页，可以就地卸载 / 隔离 / 管控，
            // 而不是只把用户丢回首页自己去找。
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    (threat.path.hashCode() and 0x7FFFFFFF) % 100000,
                    ThreatAlertActivity.buildIntent(context, threat),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_INTERCEPT, notification) }
    }

    fun notifyTest(context: Context) {
        if (!areEnabled(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_stat_xb)
            .setContentTitle("测试通知")
            .setContentText("通知通道工作正常，扫描结果与拦截提醒会出现在这里")
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1009, notification) }
    }
}
