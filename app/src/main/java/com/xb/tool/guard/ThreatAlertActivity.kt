package com.xb.tool.guard

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
import com.xb.tool.core.Threat
import com.xb.tool.scan.QuarantineStore

/**
 * 病毒扫描告警弹窗。
 *
 * 为什么单独做一个 Activity：
 *  低 / 标准 / 严格模式下命中威胁时，用户往往不在应用里（实时监控在后台跑），
 *  只发通知容易被划掉。这里用全屏通知 + 独立 Activity 保证「一定会被看到」，
 *  并且直接在弹窗里提供 卸载 / 隔离 / 管控 三种处置动作。
 */
class ThreatAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_PATH = "path"
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_SEVERITY = "severity"
        const val EXTRA_DESC = "desc"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_SHA = "sha"
        private const val CHANNEL = "xb_threat_alert"
        private const val NOTIF_ID = 1302

        /** 构造跳转到告警处置页的 Intent（通知栏点击也复用它）。 */
        fun buildIntent(context: Context, threat: Threat): Intent =
            Intent(context, ThreatAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_NAME, threat.name)
                putExtra(EXTRA_PATH, threat.path)
                putExtra(EXTRA_PACKAGE, threat.packageName.orEmpty())
                putExtra(EXTRA_SEVERITY, threat.severity.name)
                putExtra(EXTRA_DESC, threat.description)
                putExtra(EXTRA_SOURCE, threat.source)
                putExtra(EXTRA_SHA, threat.sha256.orEmpty())
            }

        fun show(context: Context, threat: Threat) {
            val intent = buildIntent(context, threat)
            notify(context, intent, threat)
            runCatching { context.startActivity(intent) }
                .onFailure { LogBus.warn("威胁告警弹窗被系统拦截：${it.message}") }
        }

        private fun notify(context: Context, intent: Intent, threat: Threat) {
            if (!Notifications.areEnabled(context)) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "病毒告警", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "扫描到威胁时的强制提醒"
                        enableVibration(true)
                    }
                )
            }
            val pending = PendingIntent.getActivity(
                context, 32, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_xb)
                .setContentTitle("发现威胁：${threat.name}")
                .setContentText("${threat.severity.label} · ${threat.path}")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${threat.severity.label}\n${threat.path}\n${threat.description}"
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
        val name = intent.getStringExtra(EXTRA_NAME) ?: "未知威胁"
        val path = intent.getStringExtra(EXTRA_PATH) ?: ""
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: ""
        val severityName = intent.getStringExtra(EXTRA_SEVERITY) ?: "MEDIUM"
        val desc = intent.getStringExtra(EXTRA_DESC) ?: ""
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "实时检测"
        val sha = intent.getStringExtra(EXTRA_SHA) ?: ""
        val severity = runCatching { com.xb.tool.core.Severity.valueOf(severityName) }
            .getOrDefault(com.xb.tool.core.Severity.MEDIUM)

        val threat = Threat(
            id = sha.ifBlank { path },
            name = name,
            path = path,
            sha256 = sha.ifBlank { null },
            severity = severity,
            description = desc,
            packageName = packageName.ifBlank { null },
            source = source
        )

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
                ThreatAlertContent(
                    threat = threat,
                    onUninstall = {
                        val r = ThreatActions.uninstall(this, packageName, name)
                        Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                        if (r.ok) finish()
                    },
                    onQuarantine = {
                        val r = ThreatActions.quarantine(this, threat)
                        Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                        if (r.ok) finish()
                    },
                    onFreeze = {
                        val r = ThreatActions.freeze(this, packageName, name)
                        Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                        if (r.ok) finish()
                    },
                    onDelete = {
                        val r = ThreatActions.delete(this, threat)
                        Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
                        if (r.ok) finish()
                    },
                    onIgnore = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ThreatAlertContent(
    threat: Threat,
    onUninstall: () -> Unit,
    onQuarantine: () -> Unit,
    onFreeze: () -> Unit,
    onDelete: () -> Unit,
    onIgnore: () -> Unit
) {
    val severityColor = when (threat.severity) {
        com.xb.tool.core.Severity.HIGH -> Color(0xFFFF4D4D)
        com.xb.tool.core.Severity.MEDIUM -> Color(0xFFFF8A3D)
        else -> Color(0xFFFFC53D)
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF120A0B)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "发现威胁",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = severityColor
            )
            Text(
                "扫描到可能危害设备的内容，请立即处置。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF241619),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLine("名称", threat.name)
                    if (threat.packageName != null) InfoLine("包名", threat.packageName)
                    InfoLine("风险等级", threat.severity.label, severityColor)
                    InfoLine("来源", threat.source)
                }
            }

            Text("文件路径", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0B0708),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    threat.path.ifBlank { "（无路径信息）" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFB4B4),
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (threat.description.isNotBlank()) {
                Text("判定说明", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    threat.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(2.dp))

            // 第一优先：卸载（有包名时）/ 删除（纯文件威胁）
            if (threat.packageName != null) {
                Button(
                    onClick = onUninstall,
                    colors = ButtonDefaults.buttonColors(containerColor = severityColor),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("卸载该应用") }
            } else {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = severityColor),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("删除该文件") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onQuarantine, modifier = Modifier.weight(1f)) {
                    Text("隔离")
                }
                if (threat.packageName != null) {
                    OutlinedButton(onClick = onFreeze, modifier = Modifier.weight(1f)) {
                        Text("管控")
                    }
                }
            }

            OutlinedButton(onClick = onIgnore, modifier = Modifier.fillMaxWidth()) {
                Text("稍后处理")
            }

            Text(
                "隔离会把文件移入应用私有目录，其他应用无法再执行；" +
                    "卸载与管控在没有 Root / Shizuku 时会打开系统页面由你手动确认。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
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
