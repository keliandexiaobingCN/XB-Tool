package com.xb.tool

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.xb.tool.core.AppState
import com.xb.tool.core.Screen
import com.xb.tool.core.XBTheme
import com.xb.tool.guard.Notifications
import com.xb.tool.ui.XBAppRoot

class MainActivity : ComponentActivity() {
    private var stateRef: AppState? = null
    private var notificationLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val state = AppState(applicationContext)
        stateRef = state
        notificationLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            state.updateNotificationPermission(granted)
            state.prefs.notificationAsked = true
            if (!granted) {
                state.showMessage("通知权限未开启，实时防护提醒将不可见")
            } else {
                state.showMessage("通知权限已开启")
            }
        }
        state.notificationRequester = { requestNotificationsIfNeeded() }
        state.bootstrap()

        setContent {
            XBTheme(state) {
                androidx.compose.runtime.CompositionLocalProvider(
                com.xb.tool.ui.LocalGlassEffect provides state.theme.glassEffect,
                com.xb.tool.ui.LocalUiAnimation provides state.theme.uiAnimation,
                com.xb.tool.ui.LocalGlassAlpha provides state.theme.glassAlpha
            ) {
                XBAppRoot(state)
            }
            }
        }
        if (BuildConfig.DEBUG) {
            navigateFromIntent(state, intent)
            if (intent?.getStringExtra("audio_test") != null) {
                Thread { com.xb.tool.util.AudioSelfTest.run(applicationContext) }.start()
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!state.goBack()) {
                finish()
            }
        }

        window.decorView.post { requestNotificationsIfNeeded() }
    }

    override fun onResume() {
        super.onResume()
        stateRef?.let { state ->
            state.updateNotificationPermission(Notifications.areEnabled(this))
            state.refreshAllFilesAccess()
            state.refreshRuntimeFlags()
        }
    }

    private fun requestNotificationsIfNeeded() {
        val state = stateRef ?: return
        if (Build.VERSION.SDK_INT < 33) {
            state.updateNotificationPermission(Notifications.areEnabled(this))
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching {
                notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            state.updateNotificationPermission(true)
        }
    }

    private fun navigateFromIntent(state: AppState, intent: android.content.Intent?) {
        if (intent?.getStringExtra("screen") == "scan_deep_force") {
            state.startScan(com.xb.tool.scan.ScanMode.DEEP)
            state.navigate(Screen.Scan(com.xb.tool.scan.ScanMode.DEEP))
            return
        }
        // 调试用：直接弹出「发现威胁」处置页，方便在没有真实样本时验证弹窗与
        // 卸载 / 隔离 / 管控 三个按钮的可用性。
        if (intent?.getStringExtra("screen") == "threat") {
            com.xb.tool.guard.ThreatAlertActivity.show(
                this,
                com.xb.tool.core.Threat(
                    id = "debug-sample",
                    name = "测试恶意应用",
                    path = "/sdcard/Download/xb_debug_sample.apk",
                    sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                    severity = com.xb.tool.core.Severity.HIGH,
                    description = "调试样本：命中内置公开恶意哈希 #1",
                    packageName = "com.example.bad",
                    source = "实时防护"
                )
            )
            return
        }
        val screen = when (intent?.getStringExtra("screen")) {
            "whitelist" -> Screen.Whitelist
            "about" -> Screen.About
            "author" -> Screen.Author
            "update" -> Screen.Update
            "announcements" -> Screen.Announcements
            "emergency" -> Screen.Emergency
            "adblock" -> Screen.AdBlock
            "qr" -> Screen.QrCode
            "browser" -> Screen.Browser
            "system", "sysmon" -> Screen.SystemMonitor
            "network" -> Screen.Network
            "realtime" -> Screen.Realtime
            "adb" -> Screen.AdbPair
            "privilege" -> Screen.PrivilegeService
            "privilege-apps" -> Screen.PrivilegeApps
            "root" -> Screen.Root
            "kernel" -> Screen.Kernel
            "theme" -> Screen.Theme
            "cache" -> Screen.Cache
            "notification" -> Screen.Notification
            "extreme" -> Screen.Extreme
            "intercepts" -> Screen.Intercepts
            "quarantine" -> Screen.Quarantine
            "scan_normal" -> Screen.Scan(com.xb.tool.scan.ScanMode.NORMAL)
            "scan_deep" -> Screen.Scan(com.xb.tool.scan.ScanMode.DEEP)
            "memory" -> Screen.Memory
            "ram" -> Screen.Ram
            "freeze" -> Screen.Freeze
            "audio" -> Screen.Audio
            "cloud" -> Screen.Cloud
            "pdf" -> Screen.Pdf
            "search" -> Screen.Search
            "tools" -> Screen.Tools
            "settings" -> Screen.Settings
            else -> null
        }
        if (screen != null) state.navigate(screen)
    }
}
