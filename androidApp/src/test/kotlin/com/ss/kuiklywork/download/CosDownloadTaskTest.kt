package com.ss.kuiklywork.download

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CosDownloadTaskTest {
    private class Work : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { jobs.add(command) }
        fun run() { while (jobs.isNotEmpty()) jobs.removeFirst().run() }
    }
    private class MemoryStore : CosTaskPersistence {
        var records = emptyList<CosDownloadRecord>()
        var failWhenCompleted = -1
        override fun load() = records.toList()
        override fun save(records: List<CosDownloadRecord>) {
            if (records.any { it.completed == failWhenCompleted }) throw IOException("disk failure")
            this.records = records.toList()
        }
    }
    private fun request(group: Int = 1) = CosDownloadRequest("https://www.nncos.com/$group.html", "图片组", (1..33).map { "https://img.nncos.com/$it.webp" }, "https://www.nncos.com/$group.html")

    @Test fun resumeOnlyDownloadsRemainingImages() {
        val store = MemoryStore()
        val work = Work()
        val first = mutableListOf<Int>()
        val manager = CosDownloadTaskManager(store, CosTaskDownloader { task, index, checkpoint ->
            if (index == 10) throw IOException("interrupted")
            first.add(index)
            val target = "${task.taskId}/$index"
            checkpoint(target)
            target
        }, {}, work)
        assertEquals(0, manager.start(request()).code)
        work.run()
        assertEquals(10, manager.snapshot().completed)
        assertEquals("interrupted", manager.snapshot().status)
        val remaining = mutableListOf<Int>()
        val restoredWork = Work()
        val restored = CosDownloadTaskManager(store, CosTaskDownloader { task, index, checkpoint ->
            remaining.add(index)
            val target = "${task.taskId}/$index"
            checkpoint(target)
            target
        }, {}, restoredWork)
        assertTrue(restoredWork.jobs.isEmpty())
        assertEquals(10, restored.snapshot().completed)
        // A fresh page's URL order must not replace the interrupted manifest.
        restored.start(request().copy(imageUrls = request().imageUrls.reversed()))
        restoredWork.run()
        assertEquals((10..32).toList(), remaining)
        assertEquals(33, restored.snapshot().completed)
        assertEquals("completed", restored.snapshot().status)
    }

    @Test fun startIsSingleFlightAcrossGroups() {
        val work = Work()
        val manager = CosDownloadTaskManager(MemoryStore(), CosTaskDownloader { _, i, _ -> "saved/$i" }, {}, work)
        val a = manager.start(request())
        assertEquals("running", a.task.status)
        assertEquals(a.task.taskId, manager.start(request()).task.taskId)
        assertEquals(1, manager.start(request(2)).code)
        assertEquals(1, work.jobs.size)
    }

    @Test fun concurrentGroupStartsReserveOnlyOneWorker() {
        val work = Work()
        val manager = CosDownloadTaskManager(MemoryStore(), CosTaskDownloader { _, _, _ -> "target" }, {}, work)
        val callers = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            val replies = (1..2).map { group -> callers.submit<CosDownloadReply> { gate.await(); manager.start(request(group)) } }
            gate.countDown()
            assertEquals(listOf(0, 1), replies.map { it.get(3, TimeUnit.SECONDS).code }.sorted())
            assertEquals(1, work.jobs.size)
        } finally { callers.shutdownNow() }
    }

    @Test fun reusesTargetAfterCrashBeforeCheckpoint() {
        val store = MemoryStore()
        val work = Work()
        val savedTargets = mutableSetOf<String>()
        val manager = CosDownloadTaskManager(store, CosTaskDownloader { task, i, checkpoint ->
            val target = task.targets[i] ?: "${task.taskId}/$i"
            checkpoint(target)
            savedTargets.add(target)
            store.failWhenCompleted = 1
            target
        }, {}, work)
        manager.start(request().copy(imageUrls = request().imageUrls.take(1)))
        work.run()
        store.failWhenCompleted = -1
        val restoreWork = Work()
        val restored = CosDownloadTaskManager(store, CosTaskDownloader { task, i, checkpoint ->
            val target = task.targets[i] ?: "unexpected-new-target"
            checkpoint(target)
            savedTargets.add(target)
            target
        }, {}, restoreWork)
        restored.start(request())
        restoreWork.run()
        assertEquals(1, savedTargets.size)
        assertFalse(savedTargets.contains("unexpected-new-target"))
        assertEquals("completed", restored.snapshot().status)
    }

    @Test fun retryFinalizationDoesNotDownloadAgain() {
        val work = Work()
        var saves = 0
        var fail = true
        val manager = CosDownloadTaskManager(MemoryStore(), CosTaskDownloader { _, _, _ -> saves++; "target" }, {
            if (fail) throw IOException("record failure")
        }, work)
        val req = request().copy(imageUrls = request().imageUrls.take(1))
        manager.start(req)
        work.run()
        assertEquals("interrupted", manager.snapshot().status)
        fail = false
        manager.start(req)
        work.run()
        assertEquals(1, saves)
        assertEquals("completed", manager.snapshot().status)
    }

    @Test fun restartDoesNotAutoStartAndAnotherGroupKeepsCheckpoint() {
        val store = MemoryStore()
        val work = Work()
        val manager = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> throw IOException("offline") }, {}, work)
        manager.start(request())
        work.run()
        manager.start(request(2))
        work.run()
        val restoredWork = Work()
        val restored = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> error("must not run") }, {}, restoredWork)
        assertEquals("interrupted", restored.snapshot(request().detailUrl).status)
        assertEquals("interrupted", restored.snapshot(request(2).detailUrl).status)
        assertTrue(restoredWork.jobs.isEmpty())
    }

    @Test fun newDownloadAfterCompletionUsesNewTaskId() {
        val work = Work()
        val manager = CosDownloadTaskManager(MemoryStore(), CosTaskDownloader { _, _, _ -> "target" }, {}, work)
        val first = manager.start(request()).task.taskId
        work.run()
        val second = manager.start(request()).task.taskId
        assertNotEquals(first, second)
    }

    @Test fun failedReplacementDoesNotChangePreviousCompletedTask() {
        val store = MemoryStore()
        val work = Work()
        var saved = 0
        val manager = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> saved++; "target" }, {}, work)
        val req = request().copy(imageUrls = request().imageUrls.take(1))
        val first = manager.start(req).task.taskId
        work.run()
        store.failWhenCompleted = 0
        assertEquals(-1, manager.start(req).code)
        assertEquals("completed", manager.snapshot().status)
        assertEquals(first, manager.snapshot().taskId)
        store.failWhenCompleted = -1
        assertNotEquals(first, manager.start(req).task.taskId)
        work.run()
        assertEquals(2, saved)
    }

    @Test fun incompleteFinalizationHidesNewBadgeEvenAfterRestart() {
        val store = MemoryStore()
        val work = Work()
        val req = request().copy(imageUrls = request().imageUrls.take(1))
        var rawRecords = emptyList<String>()
        var fail = true
        val manager = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> "target" }, { url ->
            rawRecords = listOf(url) // Primary preferences published, recovery write fails.
            if (fail) throw IOException("recovery failure")
        }, work)
        manager.start(req)
        work.run()
        assertTrue(manager.visibleDownloadedUrls(rawRecords).isEmpty())
        val restoredWork = Work()
        val restored = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> error("already saved") }, {}, restoredWork)
        assertTrue(restored.visibleDownloadedUrls(rawRecords).isEmpty())
        restored.start(req)
        restoredWork.run()
        assertEquals(rawRecords, restored.visibleDownloadedUrls(rawRecords))
    }

    @Test fun failedRedownloadKeepsPreviouslyEarnedBadge() {
        val store = MemoryStore()
        val work = Work()
        val req = request()
        val manager = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> throw IOException("offline") }, {}, work,
            wasDownloaded = { it == req.detailUrl })
        manager.start(req)
        work.run()
        val restored = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> "target" }, {}, Work())
        assertEquals(listOf(req.detailUrl), restored.visibleDownloadedUrls(listOf(req.detailUrl)))
    }
}
