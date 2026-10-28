package com.xb.tool.guard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.xb.tool.core.LogBus
import com.xb.tool.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs in a separate process (:watchdog) for extreme keep-alive.
 * It heartbeats itself and restarts GuardService when its heartbeat is stale.
 */
class WatchdogService : Service() {
    companion object {
        const val ACTION_STOP_EXTREME = "com.xb.tool.action.STOP_EXTREME"
        private const val HEARTBEAT_INTERVAL = 15_000L
        private const val STALE_AFTER = 50_000L

        fun start(context: Context) {
            val intent = Intent(context, WatchdogService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 先完成前台化，避免系统 5 秒超时杀服务
        runCatching {
            startForeground(Notifications.ID_WATCHDOG, Notifications.watchdogNotification(this))
        }.onFailure {
            LogBus.warn("互拉守护前台服务启动失败：${it.message}")
        }
        if (intent?.action == ACTION_STOP_EXTREME) {
            prefs.extremeKeepAlive = false
            LogBus.info("已通过通知关闭极致保活")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!prefs.keepAlive && !prefs.realtimeEnabled && !prefs.extremeKeepAlive) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch {
                while (isActive) {
                    Heartbeat.beat(this@WatchdogService, Heartbeat.WATCHDOG)
                    if (prefs.extremeKeepAlive) {
                        val guardAge = Heartbeat.age(this@WatchdogService, Heartbeat.GUARD)
                        if (guardAge > STALE_AFTER) {
                            LogBus.warn("互拉守护：检测到守护进程失联，正在重启")
                            runCatching { GuardService.start(this@WatchdogService) }
                                .onFailure { LogBus.warn("互拉守护拉起失败：${it.message}") }
                        }
                    }
                    delay(HEARTBEAT_INTERVAL)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Heartbeat.clear(this, Heartbeat.WATCHDOG)
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
