package com.xb.demo.adb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoApp() }
    }
}

@Composable
fun DemoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AdbDiscovery.Result?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun askNearby() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }

    fun startScan() {
        if (scanning) return
        scanning = true
        askNearby()
        scope.launch {
            val r = withContext(Dispatchers.IO) { AdbDiscovery.scan(context) }
            result = r
            scanning = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ADB 无线配对 Demo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "只验证发现与状态读取，不做 SPAKE2 配对",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startScan() }, enabled = !scanning, modifier = Modifier.weight(1f)) {
                Text(if (scanning) "扫描中…" else "开始扫描")
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("开发者选项") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent("android.settings.ADB_WIRELESS_SETTINGS"))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开无线调试设置") }

        Spacer(Modifier.height(12.dp))

        if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在通过 mDNS 查找配对服务…", style = MaterialTheme.typography.bodySmall)
            }
        }

        result?.let { r ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    InfoCard("环境检查") {
                        Line("无线调试 TLS 端口", if (r.tlsPort > 0) "${r.tlsPort}（已开启）" else "0（未开启）")
                        Line("app_process", AdbDiscovery.findAppProcess() ?: "未找到")
                        Line("系统 adb", AdbDiscovery.findAdb() ?: "未找到")
                    }
                }
                item {
                    InfoCard("配对服务（_adb-tls-pairing._tcp）") {
                        if (r.pairing.isEmpty()) {
                            Line("结果", "未发现")
                            Text(
                                "请确认已打开无线调试，并且屏幕停留在「使用配对码配对设备」页面。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            r.pairing.forEach { Line(it.name, "${it.host}:${it.port}") }
                            Text(
                                "这证明 mDNS 发现链路可用。后续 SPAKE2 配对需要 BoringSSL。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (r.connect.isNotEmpty()) {
                    item {
                        InfoCard("连接服务（_adb-tls-connect._tcp）") {
                            r.connect.forEach { Line(it.name, "${it.host}:${it.port}") }
                        }
                    }
                }
                item {
                    InfoCard("日志") {
                        r.log.forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(130.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
