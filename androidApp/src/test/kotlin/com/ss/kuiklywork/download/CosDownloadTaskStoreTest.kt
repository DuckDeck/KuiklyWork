package com.ss.kuiklywork.download

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CosDownloadTaskStoreTest {
    @Test fun storeRoundTripsAndNeverAutoStarts() {
        val dir = Files.createTempDirectory("cos-tasks-").toFile()
        try {
            val store = CosDownloadTaskStore(File(dir, "tasks.json"))
            val task = CosDownloadRecord("id", "https://www.nncos.com/1.html", "一组", listOf("https://img.nncos.com/a.gif"),
                "https://www.nncos.com/1.html", status = "running", targets = mapOf(0 to "content://media/123"), previouslyDownloaded = true)
            store.save(listOf(task))
            assertEquals(listOf(task), CosDownloadTaskStore(File(dir, "tasks.json")).load())
            val restored = CosDownloadTaskManager(store, CosTaskDownloader { _, _, _ -> error("must not download") }, {},
                java.util.concurrent.Executor { error("must not schedule") })
            assertEquals("interrupted", restored.snapshot().status)
        } finally { dir.deleteRecursively() }
    }

    @Test fun damagedPrimaryUsesLastGoodBackupWithoutErasingIt() {
        val dir = Files.createTempDirectory("cos-tasks-").toFile()
        try {
            val file = File(dir, "tasks.json")
            val task = CosDownloadRecord("id", "https://www.nncos.com/1.html", "title", listOf("a"))
            val store = CosDownloadTaskStore(file)
            store.save(listOf(task))
            store.save(listOf(task.copy(completed = 1, status = "completed")))
            file.writeText("broken")
            assertEquals(listOf(task), store.load())
            File(dir, "tasks.json.bak").writeText("also broken")
            assertThrows(Exception::class.java) { store.load() }
            assertEquals("broken", file.readText())
        } finally { dir.deleteRecursively() }
    }
}
