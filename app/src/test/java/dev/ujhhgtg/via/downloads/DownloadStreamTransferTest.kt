package dev.ujhhgtg.via.downloads

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class DownloadStreamTransferTest {
    @Test fun progressAndPauseWorkBeforeTheResponseFinishesAndResumePreservesBytes() = runBlocking {
        verifyPauseAndResume(resetDestination = false)
    }

    @Test fun resumeRestoresCachedBytesIfTheDestinationWasRemoved() = runBlocking {
        verifyPauseAndResume(resetDestination = true)
    }

    private suspend fun verifyPauseAndResume(resetDestination: Boolean) = kotlinx.coroutines.coroutineScope {
        val cache = File.createTempFile("via-stream-test-", ".body")
        val destination = File.createTempFile("via-stream-test-", ".out")
        val bytes = ByteArray(160 * 1024) { (it % 251).toByte() }
        val outcomes = mutableListOf<Pair<Boolean?, Long>>()
        lateinit var entry: DownloadStreamRegistry.Entry
        var reads = 0
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (++reads == 1) entry.paused.value = true
                return count
            }
        }
        entry = DownloadStreamRegistry.Entry(cache, input, CompletableDeferred()) { saved, size -> outcomes += saved to size }
        val source = DownloadStreamRegistry.Source(entry)
        source.pause()
        val producer = launch { entry.ready.complete(runCatching { entry.spool() }) }
        val control = DownloadControl()
        val updates = mutableListOf<Pair<DownloadRecord, Long>>()
        var time = 0L
        try {
            val paused = withTimeout(3000) {
                DownloadStreamTransfer(DownloadRecord(name = "file", url = null, totalSize = bytes.size.toLong()), source, control,
                    { FileOutput(destination) }, { record, speed ->
                        updates += record to speed
                        if (record.state == DownloadState.DOWNLOADING && record.downloadedSize > 0) {
                            control.paused = true
                            source.pause()
                        }
                    }, { time += 1100; time }).run()
            }
            assertEquals(DownloadState.PAUSED, paused.state)
            assertEquals(64 * 1024L, paused.downloadedSize)
            assertTrue(updates.any { it.first.state == DownloadState.DOWNLOADING && it.first.progress > 0 && it.second > 0 })
            assertFalse(entry.ready.isCompleted)
            assertTrue(cache.exists())
            assertTrue(outcomes.isEmpty())
            delay(150)
            assertEquals(1, reads) // Pause stops consuming the original response too.
            assertArrayEquals(bytes.copyOf(64 * 1024), destination.readBytes())

            if (resetDestination) destination.writeBytes(byteArrayOf())
            val completed = withTimeout(3000) {
                DownloadStreamTransfer(paused, source, DownloadControl(), { FileOutput(destination) }, { _, _ -> },
                    { time += 1100; time }).run()
            }
            producer.join()
            assertEquals(DownloadState.COMPLETE, completed.state)
            assertEquals(bytes.size.toLong(), completed.downloadedSize)
            assertArrayEquals(bytes, destination.readBytes())
            assertEquals(listOf(true to bytes.size.toLong()), outcomes)
            assertFalse(cache.exists())
        } finally {
            source.close(); producer.cancel(); cache.delete(); destination.delete()
        }
    }

    @Test fun aStalledResponseCanBePausedWithoutReportingCompletion() = runBlocking {
        val cache = File.createTempFile("via-stream-test-", ".body")
        val destination = File.createTempFile("via-stream-test-", ".out")
        val outcomes = mutableListOf<Boolean?>()
        val entry = DownloadStreamRegistry.Entry(cache, ByteArrayInputStream(byteArrayOf()), CompletableDeferred()) { saved, _ -> outcomes += saved }
        val source = DownloadStreamRegistry.Source(entry)
        val control = DownloadControl()
        val stop = launch { delay(20); control.paused = true; source.pause() }
        try {
            val result = withTimeout(3000) {
                DownloadStreamTransfer(DownloadRecord(name = "file", url = null), source, control,
                    { FileOutput(destination) }, { _, _ -> }).run()
            }
            stop.join()
            assertEquals(DownloadState.PAUSED, result.state)
            assertEquals(0L, result.downloadedSize)
            assertTrue(cache.exists())
            assertTrue(outcomes.isEmpty())
            source.close(); source.close()
            assertEquals(listOf<Boolean?>(null), outcomes)
        } finally { source.close(); stop.cancel(); cache.delete(); destination.delete() }
    }

    @Test fun cachedResponseWithUnknownLengthCompletesWithItsActualSize() = runBlocking {
        val cache = File.createTempFile("via-stream-test-", ".body")
        val destination = File.createTempFile("via-stream-test-", ".out")
        val bytes = "cached response".toByteArray()
        val entry = DownloadStreamRegistry.Entry(cache, ByteArrayInputStream(bytes), CompletableDeferred(), null)
        entry.spool(); entry.ready.complete(Result.success(Unit))
        val source = DownloadStreamRegistry.Source(entry)
        try {
            val result = DownloadStreamTransfer(DownloadRecord(name = "file", url = null), source, DownloadControl(),
                { FileOutput(destination) }, { _, _ -> }).run()
            assertEquals(DownloadState.COMPLETE, result.state)
            assertEquals(bytes.size.toLong(), result.totalSize)
            assertArrayEquals(bytes, destination.readBytes())
        } finally { source.close(); cache.delete(); destination.delete() }
    }

    @Test fun responseFailureReportsFailureAndReleasesItsCache() = runBlocking {
        val cache = File.createTempFile("via-stream-test-", ".body")
        val destination = File.createTempFile("via-stream-test-", ".out")
        val outcomes = mutableListOf<Boolean?>()
        val entry = DownloadStreamRegistry.Entry(cache, ByteArrayInputStream(byteArrayOf()), CompletableDeferred()) { saved, _ -> outcomes += saved }
        entry.ready.complete(Result.failure(IOException("network failed")))
        val source = DownloadStreamRegistry.Source(entry)
        try {
            val result = DownloadStreamTransfer(DownloadRecord(name = "file", url = null), source, DownloadControl(),
                { FileOutput(destination) }, { _, _ -> }).run()
            assertEquals(DownloadState.FAILED, result.state)
            assertEquals(listOf(false), outcomes)
            assertFalse(cache.exists())
        } finally { source.close(); cache.delete(); destination.delete() }
    }

    private class FileOutput(file: File) : DownloadOutput() {
        private val access = RandomAccessFile(file, "rw")
        override fun length() = access.length()
        override fun resize(value: Long) = access.setLength(value)
        override fun seek(value: Long) = access.seek(value)
        override fun write(bytes: ByteArray, offset: Int, length: Int) = access.write(bytes, offset, length)
        override fun sync() = access.fd.sync()
        override fun close() = access.close()
    }
}
