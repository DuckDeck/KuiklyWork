package com.ss.kuiklywork

import kotlin.test.*

class CosDownloadPresentationTest {
    @Test fun pollingStopsWithPageAndRejectsStaleReplies() {
        val replies = mutableListOf<(CosDownloadSnapshot?) -> Unit>()
        val scheduled = mutableListOf<() -> Unit>()
        val updates = mutableListOf<CosDownloadSnapshot>()
        val poller = CosDownloadPoller({ replies.add(it) }, { _, callback -> scheduled.add(callback) }, { updates.add(it) })
        poller.start()
        poller.start()
        assertEquals(1, replies.size)
        poller.stop()
        replies[0](CosDownloadSnapshot("old"))
        assertTrue(updates.isEmpty())
        assertTrue(scheduled.isEmpty())
        poller.start()
        replies[1](CosDownloadSnapshot("current"))
        assertEquals("current", updates.single().taskId)
        assertEquals(1, scheduled.size)
        poller.stop()
        scheduled.removeAt(0)()
        assertEquals(2, replies.size)
    }
    @Test fun failedQueryPreservesKnownProgressAndSchedulesRetry() {
        var reply: ((CosDownloadSnapshot?) -> Unit)? = null
        var delay = 0
        var updates = 0
        val poller = CosDownloadPoller({ reply = it }, { ms, _ -> delay = ms }, { updates++ })
        poller.start()
        reply!!(null)
        assertEquals(0, updates)
        assertEquals(500, delay)
    }
}
