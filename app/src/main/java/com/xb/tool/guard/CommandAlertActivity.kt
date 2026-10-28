package com.xb.tool.guard

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xb.tool.R
import com.xb.tool.core.LogBus

/**
 * 高危命令告警弹窗。
 *
 * 独立的透明主题 Activity：不依赖主界面是否在前台，命令被检测到就能弹出来。
 * 用户可以选择「强力卸载」直接移除应用，或「仅本次忽略」。
 */
class CommandAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_LABEL = "label"
        const val EXTRA_RISK = "risk"
        const val EXTRA_COMMAND = "cmd"
        const val EXTRA_ACTION = "action"
        const val EXTRA_SOURCE = "source"
        private const val CHANNEL = "xb_command_alert"
        private const val NOTIF_ID = 1301

        fun show(context: Context, alert: CommandGuard.Alert) {
            val intent = Intent(context, CommandAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_PACKAGE, alert.packageName)
                putExtra(EXTRA_LABEL, alert.appLabel)
                putExtra(EXTRA_RISK, alert.risk.name)
                putExtra(EXTRA_COMMAND, alert.command)
                putExtra(EXTRA_ACTION, alert.action)
                putExtra(EXTRA_SOURCE, alert.source)
            }
            // 后台启动 Activity 在 Android 10+ 受限，先用全屏通知兜底再尝试直接打开。
            notify(context, intent, alert)
            runCatching { context.startActivity(intent) }
                .onFailure { LogBus.warn("命令告警弹窗被系统拦截：${it.message}") }
        }

        private fun notify(context: Context, intent: Intent, alert: CommandGuard.Alert) {
            if (!Notifications.areEnabled(context)) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "高危命令告警", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "检测到应用执行高危命令时的强制提醒"
                        enableVibration(true)
                    }
                )
            }
            val pending = PendingIntent.getActivity(
                context, 31, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val fromSuspicious = alert.source.contains("可疑")
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_xb)
                .setContentTitle(
                    if (fromSuspicious) "${alert.appLabel} 可疑应用"
                    else "${alert.appLabel} 正在执行高危命令"
                )
                .setContentText("${alert.risk.label} · ${alert.action}")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${alert.risk.label} · ${alert.action}\n${alert.command}"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pending, true)
                .setAutoCancel(true)
                .setTimeoutAfter(120_000L)
                .setContentIntent(pending)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val label = intent.getStringExtra(EXTRA_LABEL) ?: packageName
        val riskName = intent.getStringExtra(EXTRA_RISK) ?: CommandGuard.Risk.HIGH.name
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: ""
        val action = intent.getStringExtra(EXTRA_ACTION) ?: ""
        val risk = runCatching { CommandGuard.Risk.valueOf(riskName) }
            .getOrDefault(CommandGuard.Risk.HIGH)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF6B6B),
                    onPrimary = Color.White,
                    background = Color(0xFF120A0B),
                    surface = Color(0xFF1E1214),
                    onSurface = Color(0xFFF4E7E8),
                    onSurfaceVariant = Color(0xFFCDB9BB),
                    outline = Color(0xFF6E5658),
                    error = Color(0xFFFF6B6B)
                )
            ) {
                AlertContent(
                    label = label,
                    packageName = packageName,
                    risk = risk,
                    action = action,
                    command = command,
                    source = source,
                    onUninstall = { uninstall(packageName, label) },
                    onIgnore = { finish() }
                )
            }
        }
    }

    private fun uninstall(packageName: String, label: String) {
        if (packageName.isBlank()) {
            finish()
            return
        }
        LogBus.warn("用户选择强力卸载：$label（$packageName）")
        val ok = runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.isSuccess
        if (!ok) {
            Toast.makeText(this, "无法打开应用详情页", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

@Composable
private fun AlertContent(
    label: String,
    packageName: String,
    risk: CommandGuard.Risk,
    action: String,
    command: String,
    source: String,
    onUninstall: () -> Unit,
    onIgnore: () -> Unit
) {
    val riskColor = when (risk) {
        CommandGuard.Risk.CRITICAL -> Color(0xFFFF4D4D)
        CommandGuard.Risk.HIGH -> Color(0xFFFF8A3D)
        CommandGuard.Risk.MEDIUM -> Color(0xFFFFC53D)
    }
    val fromSuspicious = source.contains("可疑")
    Surface(Modifier.fillMaxSize(), color = Color(0xFF120A0B)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (fromSuspicious) "检测到可疑应用" else "高危命令已拦截",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = riskColor
            )
            Text(
                if (fromSuspicious) "这个应用具备高风险能力或行为特征，建议立即卸载。"
                else "有应用正在通过 shell 执行可能破坏系统或窃取数据的命令。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF241619),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLine("应用", label)
                    InfoLine("包名", packageName)
                    InfoLine("风险等级", risk.label, riskColor)
                    InfoLine("行为", action)
                }
            }

            Text(
                if (fromSuspicious) "判定依据" else "执行的命令",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0B0708),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    command.ifBlank { if (fromSuspicious) "（无附加信息）" else "（未捕获到完整命令）" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFB4B4),
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onUninstall,
                    colors = ButtonDefaults.buttonColors(containerColor = riskColor),
                    modifier = Modifier.weight(1f)
                ) { Text("强力卸载") }
                OutlinedButton(onClick = onIgnore, modifier = Modifier.weight(1f)) { Text("仅本次忽略") }
            }
            Text(
                "强力卸载会打开该应用的系统详情页，需要你手动确认卸载（Android 不允许静默卸载其他应用）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun InfoLine(title: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}
