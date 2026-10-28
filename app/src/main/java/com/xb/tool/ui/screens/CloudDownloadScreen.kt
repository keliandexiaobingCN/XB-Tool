package com.xb.tool.ui.screens

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xb.tool.core.AppState
import com.xb.tool.ui.SectionHeader
import com.xb.tool.ui.StatusPill
import com.xb.tool.util.CloudDownloader
import com.xb.tool.util.CloudResolveException
import com.xb.tool.util.CloudResolver
import com.xb.tool.util.FileUtils
import com.xb.tool.util.ResolvedCloudFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun CloudDownloadScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember { CloudDownloader() }
    var input by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var resolved by remember { mutableStateOf<List<ResolvedCloudFile>>(emptyList()) }
    var platformLabel by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloaded by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<File>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var browserFile by remember { mutableStateOf<ResolvedCloudFile?>(null) }

    fun resolveLink() {
        if (resolving || downloading) return
        resolving = true
        error = null
        resolved = emptyList()
        scope.launch {
            try {
                val files = CloudResolver.resolve(input, password.trim())
                resolved = files
                platformLabel = files.firstOrNull()?.platform ?: ""
                state.showMessage("解析成功：${files.size} 个文件")
            } catch (t: CloudResolveException) {
                error = t.message
            } catch (t: Throwable) {
                error = "解析失败：${t.message}"
            } finally {
                resolving = false
            }
        }
    }

    fun downloadFile(file: ResolvedCloudFile) {
        if (downloading) return
        error = null
        downloading = true
        progress = 0f
        downloaded = 0
        total = 0
        status = "准备下载 ${file.name}"
        job = scope.launch {
            try {
                val output = withContext(Dispatchers.IO) {
                    downloader.download(
                        context = context,
                        url = file.directUrl,
                        referer = file.referer,
                        cookie = file.cookie,
                        userAgent = file.userAgent,
                        onProgress = { value, done, size, text ->
                            progress = value
                            downloaded = done
                            total = size
                            status = "${file.name} · $text"
                        }
                    )
                }
                results = listOf(output) + results
                status = "下载完成"
                state.showMessage("已保存 ${output.name}")
            } catch (t: Throwable) {
                error = t.message ?: "下载失败"
            } finally {
                downloading = false
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
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("云盘直链下载", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "尝试解析蓝奏云 / 123 云盘的公开分享链接，或直接下载 http(s) 直链。其他需要登录的网盘会给出提示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分享链接 / 直链") },
                placeholder = { Text("https://wwx.lanzou.com/xxxx 或 https://example.com/file.zip") },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
            )
        }

        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("提取码（可选）") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { resolveLink() },
                    enabled = !resolving && !downloading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (resolving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (resolving) "解析中…" else "解析分享链接")
                }
                OutlinedButton(
                    onClick = {
                        val file = resolved.firstOrNull()
                        if (file != null) {
                            if (file.requiresBrowser) {
                                browserFile = file
                            } else {
                                downloadFile(file)
                            }
                        } else {
                            val url = CloudResolver.extractUrl(input)
                            if (url == null) error = "未找到有效的 http(s) 链接"
                            else downloadFile(ResolvedCloudFile(FileUtils.sanitizeFileName(url.substringAfterLast('/')), url, platform = "直链"))
                        }
                    },
                    enabled = !resolving && !downloading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("直链下载")
                }
            }
        }

        if (platformLabel.isNotBlank()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(platformLabel, active = true)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "已解析 ${resolved.size} 个文件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (resolved.isNotEmpty()) {
            item { SectionHeader("解析结果") }
            resolved.forEach { file ->
                item(key = file.directUrl + file.name) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = glassSurfaceColor(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    append(file.platform)
                                    if (file.size.isNotBlank()) append(" · ").append(file.size)
                                    if (file.requiresBrowser) append(" · 需浏览器校验")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (file.requiresBrowser) browserFile = file else downloadFile(file)
                                    },
                                    enabled = !downloading
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (file.requiresBrowser) "校验下载" else "下载")
                                }
                                TextButton(onClick = { com.xb.tool.util.Intents.openUrl(context, file.directUrl) }) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("浏览器打开")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (downloading || (status.isNotBlank() && resolved.isNotEmpty())) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (downloading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        }
                        if (downloading) {
                            Spacer(Modifier.height(8.dp))
                            if (total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                buildString {
                                    append(FileUtils.formatBytes(downloaded))
                                    if (total > 0) append(" / ").append(FileUtils.formatBytes(total))
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                downloader.cancel()
                                job?.cancel()
                                downloading = false
                                status = "已取消"
                            }) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("取消")
                            }
                        }
                    }
                }
            }
        }

        error?.let {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            item { SectionHeader("本次下载") }
            results.forEach { file ->
                item(key = file.absolutePath) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = glassSurfaceColor(),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                FileUtils.formatBytes(file.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextButton(onClick = { FileUtils.openFile(context, file) }) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("打开")
                                }
                                TextButton(onClick = { FileUtils.shareFile(context, file) }) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("分享")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "支持尝试解析蓝奏云、123 云盘公开分享；百度 / 阿里 / 夸克 / UC / 移动云盘等需要登录或专用接口，暂不自动解析。文件保存在应用专属 Download/XB工具 目录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    browserFile?.let { file ->
        CloudBrowserDialog(
            url = file.directUrl,
            onDismiss = { browserFile = null },
            onDownload = { url, ua, cookie ->
                browserFile = null
                downloadFile(
                    file.copy(
                        directUrl = url,
                        userAgent = ua,
                        cookie = cookie ?: file.cookie
                    )
                )
            }
        )
    }
}

@Composable
private fun CloudBrowserDialog(
    url: String,
    onDismiss: () -> Unit,
    onDownload: (String, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setBackgroundColor(android.graphics.Color.BLACK)
            webViewClient = WebViewClient()
            setDownloadListener { downloadUrl, userAgent, _, _, _ ->
                val cookie = runCatching {
                    CookieManager.getInstance().getCookie(downloadUrl)
                }.getOrNull()
                onDownload(downloadUrl, userAgent, cookie)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }
    LaunchedEffect(url) {
        webView.loadUrl(url)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = glassSurfaceColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "完成蓝奏云校验",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Text(
                    "页面会自动执行 CDN 校验；出现下载按钮后点击它，应用会接管保存。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}
