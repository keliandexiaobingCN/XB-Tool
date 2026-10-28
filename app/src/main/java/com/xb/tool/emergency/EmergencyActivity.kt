package com.xb.tool.emergency

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.xb.tool.ui.screens.EmergencyRescueScreen

class EmergencyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        getSystemService(android.app.NotificationManager::class.java)
            .cancel(EmergencyNotifier.NOTIFICATION_ID)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF6B6B),
                    onPrimary = Color.White,
                    secondary = Color(0xFFFFB4B4),
                    background = Color(0xFF120A0B),
                    surface = Color(0xFF1E1214),
                    onSurface = Color(0xFFF4E7E8),
                    onSurfaceVariant = Color(0xFFCDB9BB),
                    outline = Color(0xFF6E5658),
                    error = Color(0xFFFF6B6B)
                )
            ) {
                // 摇一摇 / 按键触发后展示的是「救援界面」，不是紧急救机设置页
                EmergencyRescueScreen(
                    context = this,
                    onClose = { finish() },
                    onToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}
