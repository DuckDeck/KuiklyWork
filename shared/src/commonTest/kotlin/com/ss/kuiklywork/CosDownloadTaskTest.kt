package com.ss.kuiklywork

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlin.test.*

class CosDownloadTaskTest {
    private fun snapshot(done: Int, status: String = "running") = CosDownloadSnapshot("id", "https://www.nncos.com/1.html", "一组", status, done, 33, "")
    @Test fun progressTracksSavedImages() {
        assertEquals(0, snapshot(0).percent)
        assertEquals(30, snapshot(10).percent)
        assertEquals(96, snapshot(32).percent)
        assertEquals(99, snapshot(33).percent)
        assertEquals(100, snapshot(33, "completed").percent)
        assertTrue(snapshot(10).visible)
        assertTrue(snapshot(10, "interrupted").visible)
        assertFalse(snapshot(33, "completed").visible)
        assertFalse(CosDownloadSnapshot().visible)
    }
    @Test fun parsingRejectsFailuresAndMalformedProgress() {
        val valid = JSONObject("""{"code":0,"task":{"taskId":"id","detailUrl":"https://www.nncos.com/1.html","title":"一组","status":"running","completed":10,"total":33}}""")
        assertEquals(10, parseCosDownloadSnapshot(valid)?.completed)
        assertNull(parseCosDownloadSnapshot(JSONObject("""{"code":-1} """)))
        valid.optJSONObject("task")!!.put("completed", 34)
        assertNull(parseCosDownloadSnapshot(valid))
        valid.optJSONObject("task")!!.put("completed", 10).put("status", "completed")
        assertNull(parseCosDownloadSnapshot(valid))
    }
    @Test fun detailDownloadUsesImmutableReadyManifest() {
        val urls = mutableListOf("a", "b", "a")
        assertNull(cosDownloadManifest(urls, isLoading = true, hasError = false))
        assertNull(cosDownloadManifest(urls, isLoading = false, hasError = true))
        assertNull(cosDownloadManifest(emptyList(), isLoading = false, hasError = false))
        val manifest = cosDownloadManifest(urls, isLoading = false, hasError = false)
        urls.add("c")
        assertEquals(listOf("a", "b"), manifest)
    }
    @Test fun taskStatusDrivesButtonsAndDownloadedBadge() {
        assertEquals("下载中", snapshot(10).buttonText(false))
        assertEquals("继续下载", snapshot(10, "interrupted").buttonText(false))
        assertEquals("重新下载", snapshot(33, "completed").buttonText(true))
        assertEquals("下载", CosDownloadSnapshot().buttonText(false))
    }
}
