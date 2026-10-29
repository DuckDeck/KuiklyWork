package com.ss.kuiklywork.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** App-owned transport: never holds a page, Activity or render callback. */
class CosImageFileDownloader(context: Context, private val cookies: (String) -> String?) : CosTaskDownloader {
    private val app = context.applicationContext
    override fun download(task: CosDownloadRecord, index: Int, checkpoint: (String) -> Unit): String {
        val journal = File(app.filesDir, "cos_download_files/${task.taskId}/$index.done")
        val candidates = listOfNotNull(task.targets[index], journal.takeIf { it.exists() }?.readText()) + publishedCandidates(task, index)
        recoverCosPublishedTarget(candidates, ::isPublished, checkpoint) { remember(journal, it) }?.let { return it }
        val connection = openConnection(task.imageUrls[index], task.referer)
        try {
            if (connection.responseCode !in 200..299) throw IOException("下载失败：HTTP ${connection.responseCode}")
            val mime = connection.contentType?.substringBefore(';')?.lowercase().orEmpty()
            if (!mime.startsWith("image/")) throw IOException("图片内容不可用，请检查登录状态")
            val extension = when (mime) {
                "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; "image/avif" -> "avif"; else -> "jpg"
            }
            val name = "awp_${task.taskId}_$index.$extension"
            val target = if (Build.VERSION.SDK_INT >= 29) {
                val resolver = app.contentResolver
                val existing = task.targets[index]?.takeIf { it.startsWith("content://") }?.let(Uri::parse)
                    ?: resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                        "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
                        arrayOf(name, "${Environment.DIRECTORY_PICTURES}/爱壁纸/"), null)?.use { cursor ->
                        if (cursor.moveToFirst()) Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString()) else null
                    }
                val uri = existing ?: resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/爱壁纸/")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }) ?: throw IOException("无法创建图片保存位置")
                checkpoint(uri.toString())
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 1) }, null, null)
                resolver.openFileDescriptor(uri, "rwt")?.use { descriptor ->
                    ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                        val size = connection.inputStream.use { it.copyTo(output) }
                        checkSize(size, connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1)
                        output.fd.sync()
                    }
                } ?: throw IOException("无法写入图片")
                if (resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) < 1) {
                    throw IOException("无法发布图片")
                }
                uri.toString()
            } else {
                val directory = File(app.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "爱壁纸")
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建图片目录")
                val finalFile = File(directory, name)
                checkpoint(finalFile.absolutePath)
                val temporary = File(directory, "$name.part")
                FileOutputStream(temporary).use { output ->
                    val size = connection.inputStream.use { it.copyTo(output) }
                    checkSize(size, connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1)
                    output.fd.sync()
                }
                if (finalFile.exists() && !finalFile.delete()) throw IOException("无法更新未完成图片")
                if (!temporary.renameTo(finalFile)) throw IOException("无法保存图片")
                finalFile.absolutePath
            }
            remember(journal, target)
            return target
        } finally { connection.disconnect() }
    }

    private fun remember(journal: File, target: String) {
        if (!journal.parentFile!!.isDirectory && !journal.parentFile!!.mkdirs()) throw IOException("无法创建图片进度目录")
        FileOutputStream(journal).use { output -> output.write(target.toByteArray(Charsets.UTF_8)); output.fd.sync() }
    }

    private fun publishedCandidates(task: CosDownloadRecord, index: Int): List<String> {
        val prefix = "awp_${task.taskId}_$index."
        if (Build.VERSION.SDK_INT < 29) return File(app.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "爱壁纸")
            .listFiles()?.filter { it.name.startsWith(prefix) && !it.name.endsWith(".part") }?.map { it.absolutePath }.orEmpty()
        return app.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf("$prefix%", "${Environment.DIRECTORY_PICTURES}/爱壁纸/"), null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString()).toString()) }
        }.orEmpty()
    }

    private fun isPublished(target: String): Boolean {
        if (!target.startsWith("content://")) return !target.endsWith(".part") && File(target).length() > 0
        if (Build.VERSION.SDK_INT < 29) return false
        val uri = Uri.parse(target)
        return runCatching {
            val published = app.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)?.use {
                it.moveToFirst() && it.getInt(0) == 0
            } == true
            published && app.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                if (it.length >= 0) it.length > 0 else it.createInputStream().use { input -> input.read() != -1 }
            } == true
        }.getOrDefault(false)
    }

    private fun openConnection(imageUrl: String, referer: String): HttpURLConnection {
        var destination = URL(imageUrl)
        repeat(6) { hop ->
            if (destination.protocol != "https" || !(destination.host == "img.nncos.com" ||
                    destination.host == "nncos.us" && destination.path.startsWith("/nnpic2/"))) throw IOException("不支持的图片跳转地址")
            val connection = destination.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                connection.setRequestProperty("Referer", referer)
                connection.setRequestProperty("Accept-Encoding", "identity")
                // Re-select domain/path-scoped cookies on every redirect; never forward
                // the www.nncos.com login header to the historical nncos.us host.
                cookies(destination.toString())?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                if (hop == 5) throw IOException("图片跳转次数过多")
                val location = connection.getHeaderField("Location") ?: throw IOException("图片跳转地址为空")
                destination = URL(destination, location)
                connection.disconnect()
            } catch (error: Exception) { connection.disconnect(); throw error }
        }
        throw IOException("图片跳转次数过多")
    }

    private fun checkSize(actual: Long, expected: Long) {
        if (actual <= 0 || expected >= 0 && actual != expected) throw IOException("图片传输未完成，请继续下载")
    }
}
