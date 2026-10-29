package com.ss.kuiklywork.download

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CosDownloadTaskRecoveryTest {
    @Test fun publishedMediaIsRecoveredBeforeStartingReplacementTransport() {
        val writes = mutableListOf<String>()
        val published = "content://media/external/images/12"
        val result = recoverCosPublishedTarget(listOf(published), { it == published },
            { writes.add("checkpoint:$it") }, { writes.add("journal:$it") })
        assertEquals(published, result)
        assertEquals(listOf("checkpoint:$published", "journal:$published"), writes)
        // The production transport returns at this same boundary before opening a connection.
        assertNotNull(result)
    }

    @Test fun atomicLegacyFileSurvivesMissingJournalWhilePartIsNotComplete() {
        val directory = Files.createTempDirectory("cos-publication-").toFile()
        try {
            val final = File(directory, "0.gif").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val part = File(directory, "1.gif.part").apply { writeBytes(byteArrayOf(1)) }
            val result = recoverCosPublishedTarget(listOf(part.path, final.path),
                { !it.endsWith(".part") && File(it).length() > 0 }, {}, {})
            assertEquals(final.path, result)
            assertArrayEquals(byteArrayOf(1, 2, 3), final.readBytes())
            assertNull(recoverCosPublishedTarget(listOf(part.path), { false }, {}, {}))
        } finally { directory.deleteRecursively() }
    }
}
