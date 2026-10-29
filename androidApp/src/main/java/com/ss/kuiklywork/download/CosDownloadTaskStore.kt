package com.ss.kuiklywork.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class CosDownloadTaskStore(private val file: File) : CosTaskPersistence {
    private val backup = File(file.path + ".bak")

    @Synchronized override fun load(): List<CosDownloadRecord> {
        if (!file.exists() && !backup.exists()) return emptyList()
        for (candidate in listOf(file, backup)) {
            if (candidate.exists()) try { return decode(candidate.readText()) } catch (_: Exception) { }
        }
        throw IOException("下载进度文件损坏，已保留原文件")
    }

    @Synchronized override fun save(records: List<CosDownloadRecord>) {
        check(file.parentFile?.let { it.isDirectory || it.mkdirs() } != false) { "无法创建任务目录" }
        val json = JSONObject().put("version", 1).put("tasks", JSONArray().apply {
            records.forEach { task ->
                put(JSONObject().put("taskId", task.taskId).put("detailUrl", task.detailUrl).put("title", task.title)
                    .put("imageUrls", JSONArray(task.imageUrls)).put("referer", task.referer)
                    .put("completed", task.completed).put("status", task.status).put("message", task.message)
                    .put("previouslyDownloaded", task.previouslyDownloaded)
                    .put("targets", JSONObject().apply { task.targets.forEach { (index, target) -> put(index.toString(), target) } }))
            }
        }).toString().toByteArray(Charsets.UTF_8)
        val temporary = File(file.path + ".tmp")
        durableWrite(temporary, json)
        if (file.exists() && runCatching { decode(file.readText()) }.isSuccess) durableWrite(backup, file.readBytes())
        if (file.exists() && !file.delete()) throw IOException("无法更新下载进度")
        if (!temporary.renameTo(file)) throw IOException("无法发布下载进度，旧进度保留在备份文件")
    }

    private fun durableWrite(target: File, bytes: ByteArray) {
        FileOutputStream(target).use { output -> output.write(bytes); output.fd.sync() }
    }

    private fun decode(text: String): List<CosDownloadRecord> {
        val root = JSONObject(text)
        require(root.getInt("version") == 1)
        val tasks = root.getJSONArray("tasks")
        return (0 until tasks.length()).map { index ->
            val task = tasks.getJSONObject(index)
            val urls = task.getJSONArray("imageUrls").let { values -> (0 until values.length()).map { values.getString(it) } }
            val completed = task.getInt("completed")
            val status = task.getString("status")
            require(completed in 0..urls.size && status in listOf("running", "interrupted", "completed", "idle"))
            val targets = task.getJSONObject("targets").let { values ->
                values.keys().asSequence().associate { key -> key.toInt() to values.getString(key) }
            }
            require(targets.keys.all { it in urls.indices })
            CosDownloadRecord(task.getString("taskId"), task.getString("detailUrl"), task.getString("title"), urls,
                task.getString("referer"), completed, status, task.getString("message"), targets, task.optBoolean("previouslyDownloaded", false))
        }
    }
}
