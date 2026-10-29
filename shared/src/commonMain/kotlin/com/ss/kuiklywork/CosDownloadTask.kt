package com.ss.kuiklywork

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal data class CosDownloadSnapshot(
    val taskId: String = "", val detailUrl: String = "", val title: String = "", val status: String = "idle",
    val completed: Int = 0, val total: Int = 0, val message: String = ""
) {
    val percent: Int get() = when {
        total <= 0 -> 0
        status == "completed" -> 100
        else -> (completed.toLong() * 100 / total).toInt().coerceIn(0, 99)
    }
    val visible: Boolean get() = total > 0 && (status == "running" || status == "interrupted")
    fun buttonText(downloaded: Boolean): String = when (status) {
        "running" -> "下载中"
        "interrupted" -> "继续下载"
        "completed" -> "重新下载"
        else -> if (downloaded) "重新下载" else "下载"
    }
}
internal fun parseCosDownloadSnapshot(response: JSONObject?): CosDownloadSnapshot? {
    if (response?.optInt("code", -1) != 0) return null
    val task = response.optJSONObject("task") ?: return null
    val status = task.optString("status")
    if (status == "idle") return CosDownloadSnapshot()
    val completed = task.optInt("completed", -1)
    val total = task.optInt("total", 0)
    val id = task.optString("taskId")
    val url = task.optString("detailUrl")
    if (id.isEmpty() || !url.startsWith("https://www.nncos.com/") || total <= 0 || completed !in 0..total ||
        status !in listOf("running", "interrupted", "completed") || status == "completed" && completed != total) return null
    return CosDownloadSnapshot(id, url, task.optString("title"), status, completed, total, task.optString("message"))
}
internal fun cosDownloadManifest(imageUrls: List<String>, isLoading: Boolean, hasError: Boolean): List<String>? =
    if (isLoading || hasError || imageUrls.isEmpty()) null else imageUrls.toList().distinct()
internal class CosDownloadPoller(
    private val query: (((CosDownloadSnapshot?) -> Unit) -> Unit),
    private val schedule: (Int, () -> Unit) -> Unit,
    private val update: (CosDownloadSnapshot) -> Unit
) {
    private var active = false
    private var generation = 0
    fun start() {
        if (active) return
        active = true
        tick(++generation)
    }
    fun stop() { active = false; generation++ }
    private fun tick(version: Int) {
        if (!active || version != generation) return
        var answered = false
        fun retry() {
            if (active && version == generation) schedule(500) { tick(version) }
        }
        try {
            query { snapshot ->
                if (!answered && active && version == generation) {
                    answered = true
                    if (snapshot != null) update(snapshot)
                    retry()
                }
            }
        } catch (_: Exception) { if (!answered) { answered = true; retry() } }
    }
}
