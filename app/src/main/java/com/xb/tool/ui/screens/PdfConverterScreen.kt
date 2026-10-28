package com.xb.tool.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.xb.tool.util.FileUtils
import com.xb.tool.util.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.xb.tool.ui.glassSurfaceColor

@Composable
fun PdfConverterScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var toPdf by remember { mutableStateOf(true) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resultPdf by remember { mutableStateOf<File?>(null) }
    var resultImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(40)
    ) { uris ->
        imageUris = uris
        resultPdf = null
        error = null
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pdfUri = uri
            pdfName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: "document.pdf"
            resultImages = emptyList()
            error = null
        }
    }

    fun convertImages() {
        if (imageUris.isEmpty()) return
        working = true
        progress = 0f
        error = null
        resultPdf = null
        val output = File(FileUtils.externalPdfDir(context), "XB_${System.currentTimeMillis()}.pdf")
        job = scope.launch {
            try {
                val pdf = withContext(Dispatchers.IO) {
                    PdfTools.imagesToPdf(context, imageUris, output) { page, count ->
                        progress = page.toFloat() / count.coerceAtLeast(1)
                        status = "正在合成第 $page / $count 页"
                    }
                }
                resultPdf = pdf.file
                status = "已生成 ${pdf.pages} 页 PDF"
                state.showMessage("PDF 已保存")
            } catch (t: Throwable) {
                error = t.message ?: "生成 PDF 失败"
            } finally {
                working = false
                job = null
            }
        }
    }

    fun convertPdf() {
        val uri = pdfUri ?: return
        working = true
        progress = 0f
        error = null
        resultImages = emptyList()
        val dir = File(FileUtils.externalPdfDir(context), "PDF_${System.currentTimeMillis()}")
        job = scope.launch {
            try {
                val images = withContext(Dispatchers.IO) {
                    PdfTools.pdfToImages(context, uri, dir) { page, count ->
                        progress = page.toFloat() / count.coerceAtLeast(1)
                        status = "正在导出第 $page / $count 页"
                    }
                }
                resultImages = images
                status = "已导出 ${images.size} 张图片"
                state.showMessage("PDF 已转换为图片")
            } catch (t: Throwable) {
                error = t.message ?: "PDF 转换失败"
            } finally {
                working = false
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
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("PDF 转换", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "图片合成 PDF 或把 PDF 页面导出为 PNG 图片，全部在本机完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = toPdf,
                    onClick = { toPdf = true },
                    label = { Text("图片 → PDF") },
                    enabled = !working
                )
                FilterChip(
                    selected = !toPdf,
                    onClick = { toPdf = false },
                    label = { Text("PDF → 图片") },
                    enabled = !working
                )
            }
        }

        if (toPdf) {
            item { SectionHeader("选择图片", trailing = if (imageUris.isNotEmpty()) "已选 ${imageUris.size} 张" else null) }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (imageUris.isEmpty()) {
                            Text(
                                "选择多张图片，将按选择顺序合成 PDF。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    pickImages.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !working
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (imageUris.isEmpty()) "选择图片" else "重新选择")
                            }
                            if (imageUris.isNotEmpty()) {
                                TextButton(onClick = { imageUris = emptyList() }) { Text("清空") }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { convertImages() },
                    enabled = imageUris.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (working) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("开始合成 PDF")
                }
            }
        } else {
            item { SectionHeader("选择 PDF") }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = glassSurfaceColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (pdfUri == null) {
                            Text(
                                "选择一个 PDF 文件，逐页导出为 PNG 图片。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text(
                                pdfName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Button(
                            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                            enabled = !working
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pdfUri == null) "选择 PDF 文件" else "更换 PDF")
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { convertPdf() },
                    enabled = pdfUri != null && !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (working) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("开始导出图片")
                }
            }
        }

        if (working || status.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        if (working) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                        }
                    }
                }
            }
        }

        error?.let {
            item {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        resultPdf?.let { file ->
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
                            Text("PDF 已生成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            FileUtils.formatBytes(file.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { FileUtils.openFile(context, file, "application/pdf") }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("打开 PDF")
                        }
                    }
                }
            }
        }

        if (resultImages.isNotEmpty()) {
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
                            Text(
                                "已导出 ${resultImages.size} 张图片",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "目录：${resultImages.first().parent}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { FileUtils.openFile(context, resultImages.first(), "image/png") }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("打开第一张")
                            }
                            TextButton(onClick = { FileUtils.shareFile(context, resultImages.first(), "image/png") }) {
                                Text("分享")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "输出文件保存在应用专属 Documents/XB工具 目录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
