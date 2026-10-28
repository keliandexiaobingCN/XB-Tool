package com.xb.tool.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object PdfTools {
    data class PdfResult(val file: File, val pages: Int)

    fun imagesToPdf(
        context: Context,
        uris: List<Uri>,
        output: File,
        onProgress: (Int, Int) -> Unit
    ): PdfResult {
        val document = PdfDocument()
        var pageNumber = 1
        uris.forEach { uri ->
            val input = context.contentResolver.openInputStream(uri) ?: return@forEach
            val bitmap = input.use { BitmapFactory.decodeStream(it) } ?: return@forEach
            val maxSide = 2200f
            val scale = minOf(1f, maxSide / maxOf(bitmap.width, bitmap.height).toFloat())
            val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, width, height, true) else bitmap
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(scaled, 0f, 0f, null)
            document.finishPage(page)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            onProgress(pageNumber, uris.size)
            pageNumber++
        }
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
        return PdfResult(output, pageNumber - 1)
    }

    fun pdfToImages(
        context: Context,
        uri: Uri,
        outputDir: File,
        onProgress: (Int, Int) -> Unit
    ): List<File> {
        val results = mutableListOf<File>()
        val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("无法读取 PDF")
        PdfRenderer(descriptor).use { renderer ->
            outputDir.mkdirs()
            val count = renderer.pageCount
            for (index in 0 until count) {
                renderer.openPage(index).use { page ->
                    val scale = 2f
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val file = File(outputDir, "page_${index + 1}.png")
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    results += file
                    onProgress(index + 1, count)
                }
            }
        }
        return results
    }
}
