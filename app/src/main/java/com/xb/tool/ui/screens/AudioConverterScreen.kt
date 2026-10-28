package com.xb.tool.ui.screens

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xb.tool.core.AppState
import com.xb.tool.ui.SectionHeader
import com.xb.tool.util.AudioConverter
import com.xb.tool.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun AudioConverterScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var displayName by remember { mutableStateOf("") }
    var mediaInfo by remember { mutableStateOf<AudioConverter.MediaInfo?>(null) }
    var target by remember { mutableStateOf(AudioConverter.OutputFormat.MP3) }
    var bitrate by remember { mutableStateOf(192) }
    var converting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<File?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            inputUri = uri
            result = null
            error = null
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "audio_${System.currentTimeMillis()}"
            displayName = name
            scope.launch {
                mediaInfo = withContext(Dispatchers.IO) { AudioConverter.inspect(context, uri) }
            }
        }
    }

    fun startConvert() {
        val uri = inputUri ?: return
        error = null
        result = null
        converting = true
        progress = 0f
        status = "准备转换…"
        job = scope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    AudioConverter.convert(
                        context = context,
                        uri = uri,
                        displayName = displayName,
                        target = target,
                        bitrateKbps = bitrate,
                        onProgress = { value, text ->
                            progress = value
                            status = text
                        },
                        isCancelled = { job?.isCancelled == true }
                    )
                }
                result = output
                status = "转换完成"
                state.showMessage("已保存 ${output.name}")
            } catch (t: Throwable) {
                error = t.message ?: "转换失败"
                status = "转换失败"
            } finally {
                converting = false
                job = null
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("音频转换器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "支持解码 MP3 / AAC / FLAC / WAV 等格式，输出 MP3 / WAV / AAC (M4A) / FLAC。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { SectionHeader("输入文件") }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (inputUri == null) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("尚未选择音频", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildString {
                                        mediaInfo?.let {
                                            append("时长 ${FileUtils.formatDuration(it.durationMs)}")
                                            if (it.sampleRate > 0) append(" · ${it.sampleRate} Hz")
                                            if (it.mime.isNotBlank()) append(" · ${it.mime}")
                                        } ?: append("正在读取音频信息…")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Button(
                        onClick = { pickAudio.launch(arrayOf("audio/*")) },
                        enabled = !converting
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (inputUri == null) "选择音频文件" else "更换音频文件")
                    }
                }
            }
        }

        item { SectionHeader("输出格式") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AudioConverter.OutputFormat.MP3,
                        AudioConverter.OutputFormat.WAV
                    ).forEach { format ->
                        FilterChip(
                            selected = target == format,
                            onClick = { target = format },
                            label = { Text(format.label) },
                            enabled = !converting
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        AudioConverter.OutputFormat.AAC,
                        AudioConverter.OutputFormat.FLAC
                    ).forEach { format ->
                        FilterChip(
                            selected = target == format,
                            onClick = { target = format },
                            label = { Text(format.label) },
                            enabled = !converting
                        )
                    }
                }
                if (target == AudioConverter.OutputFormat.AAC || target == AudioConverter.OutputFormat.MP3) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (target == AudioConverter.OutputFormat.MP3) "MP3 码率" else "AAC 码率",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val values = if (target == AudioConverter.OutputFormat.MP3) {
                            listOf(128, 192, 320)
                        } else {
                            listOf(128, 192, 256)
                        }
                        values.forEach { value ->
                            FilterChip(
                                selected = bitrate == value,
                                onClick = { bitrate = value },
                                label = { Text("$value kbps") },
                                enabled = !converting
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader("转换") }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = glassSurfaceColor(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (converting) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = {
                            job?.cancel()
                            job = null
                            converting = false
                            status = "已取消"
                        }) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("取消")
                        }
                    } else {
                        Button(
                            onClick = { startConvert() },
                            enabled = inputUri != null
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("开始转换")
                        }
                        if (status.isNotBlank() && result == null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        result?.let { file ->
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("转换完成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${FileUtils.formatBytes(file.length())} · ${file.parent}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { FileUtils.openFile(context, file, "audio/*") }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("打开")
                            }
                            TextButton(onClick = { FileUtils.shareFile(context, file, "audio/*") }) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("分享")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "说明：输出文件保存在应用专属 Music/XB工具 目录；MP3 使用 LAME 编码，FLAC 使用纯 Java 无损编码，全部在本机完成。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
