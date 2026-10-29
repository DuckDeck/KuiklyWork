package com.ss.kuiklywork.download

import java.util.concurrent.Executor
import java.util.UUID

data class CosDownloadRequest(val detailUrl: String, val title: String, val imageUrls: List<String>, val referer: String)
data class CosDownloadRecord(
    val taskId: String = "", val detailUrl: String = "", val title: String = "",
    val imageUrls: List<String> = emptyList(), val referer: String = "",
    val completed: Int = 0, val status: String = "idle", val message: String = "",
    val targets: Map<Int, String> = emptyMap(), val previouslyDownloaded: Boolean = false
) { val total get() = imageUrls.size }
data class CosDownloadReply(val code: Int, val task: CosDownloadRecord, val message: String = "")
interface CosTaskPersistence {
    fun load(): List<CosDownloadRecord>
    fun save(records: List<CosDownloadRecord>)
}
fun interface CosTaskDownloader {
    fun download(task: CosDownloadRecord, index: Int, checkpoint: (String) -> Unit): String
}
class CosDownloadTaskManager(
    private val store: CosTaskPersistence,
    private val downloader: CosTaskDownloader,
    private val markDownloaded: (String) -> Unit,
    private val executor: Executor,
    private val wasDownloaded: (String) -> Boolean = { false }
) {
    private var records = store.load().associateByTo(linkedMapOf()) { it.detailUrl }
        .mapValuesTo(linkedMapOf()) { (_, task) ->
            if (task.status == "running") task.copy(status = "interrupted", message = "下载已中断，点击继续下载") else task
        }
    private var activeId: String? = null

    // Preferences may publish before their recovery copy or the final task write.
    // The durable task is authoritative until both finalization steps succeed.
    @Synchronized fun visibleDownloadedUrls(urls: List<String>): List<String> = urls.filter { url ->
        val task = records[url]
        task == null || task.status == "completed" || task.previouslyDownloaded
    }

    @Synchronized fun start(request: CosDownloadRequest): CosDownloadReply {
        val active = records.values.firstOrNull { it.taskId == activeId }
        if (active != null) return CosDownloadReply(if (active.detailUrl == request.detailUrl) 0 else 1, active,
            if (active.detailUrl == request.detailUrl) "" else "已有一组图片正在下载")
        val old = records[request.detailUrl]?.takeIf { it.status == "interrupted" }
        val task = if (old != null) old.copy(status = "running", message = "") else {
            val urls = request.imageUrls.toList().distinct()
            if (!request.detailUrl.startsWith("https://www.nncos.com/") || urls.isEmpty() ||
                urls.any { !it.startsWith("https://img.nncos.com/") && !it.startsWith("https://nncos.us/nnpic2/") }) {
                return CosDownloadReply(-1, CosDownloadRecord(), "图片清单无效或为空")
            }
            CosDownloadRecord(UUID.randomUUID().toString(), request.detailUrl, request.title, urls,
                request.referer, status = "running", previouslyDownloaded = wasDownloaded(request.detailUrl))
        }
        return try {
            persist(task)
            activeId = task.taskId
            executor.execute { runTask(task.detailUrl, task.taskId) }
            CosDownloadReply(0, task)
        } catch (error: Exception) {
            // A failed initial save must not turn a previous completed task into
            // a resumable replacement that never actually existed.
            if (records[task.detailUrl]?.taskId == task.taskId) interrupt(task.detailUrl, error)
            CosDownloadReply(-1, snapshot(task.detailUrl), "无法保存下载任务，请重试")
        }
    }

    @Synchronized fun snapshot(detailUrl: String? = null): CosDownloadRecord =
        if (detailUrl != null) records[detailUrl] ?: CosDownloadRecord()
        else records.values.firstOrNull { it.taskId == activeId }
            ?: records.values.lastOrNull { it.status == "interrupted" }
            ?: records.values.lastOrNull() ?: CosDownloadRecord()

    private fun runTask(detailUrl: String, taskId: String) {
        try {
            while (true) {
                val task = synchronized(this) { records.getValue(detailUrl) }
                if (task.taskId != taskId || task.status != "running") return
                if (task.completed == task.total) break
                val index = task.completed
                val target = downloader.download(task, index) { location ->
                    synchronized(this) {
                        val current = records.getValue(detailUrl)
                        persist(current.copy(targets = current.targets + (index to location)))
                    }
                }
                synchronized(this) {
                    val current = records.getValue(detailUrl)
                    persist(current.copy(completed = index + 1, targets = current.targets + (index to target)))
                }
            }
            // Completion-record failure leaves all image checkpoints intact, so a
            // manual retry performs finalization only rather than saving images again.
            markDownloaded(detailUrl)
            synchronized(this) {
                persist(records.getValue(detailUrl).copy(status = "completed", message = ""))
                if (activeId == taskId) activeId = null
            }
        } catch (error: Exception) {
            synchronized(this) { interrupt(detailUrl, error) }
        }
    }

    private fun persist(task: CosDownloadRecord) {
        val next = LinkedHashMap(records)
        next.remove(task.detailUrl)
        next[task.detailUrl] = task
        store.save(next.values.toList())
        records = next
    }

    private fun interrupt(detailUrl: String, error: Exception) {
        records[detailUrl]?.let { current ->
            val paused = current.copy(status = "interrupted", message = error.message ?: "下载中断，请重试")
            try { persist(paused) } catch (_: Exception) { records[detailUrl] = paused }
        }
        activeId = null
    }
}
