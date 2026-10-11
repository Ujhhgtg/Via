package dev.ujhhgtg.via.downloads

import android.os.SystemClock
import kotlinx.coroutines.CancellationException

/** Save a Gecko response as it arrives, retaining its cache and offset while paused. */
internal class DownloadStreamTransfer(
    private val initial: DownloadRecord,
    private val source: DownloadStreamRegistry.Source,
    private val control: DownloadControl,
    private val openOutput: (DownloadRecord) -> DownloadOutput,
    private val update: (DownloadRecord, Long) -> Unit,
    private val timeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) {
    suspend fun run(): DownloadRecord {
        var record = initial.copy(state = DownloadState.DOWNLOADING)
        var speed = 0L
        var lastUpdate = timeMillis()
        var lastBytes = record.downloadedSize
        fun checkStopped() { if (control.paused || control.deleted) throw DownloadFailure(1) }
        val result = runCatching {
            checkStopped()
            source.resume()
            val output = openOutput(record)
            try {
                if (output.length() < record.downloadedSize) record = record.copy(downloadedSize = 0)
                lastBytes = record.downloadedSize
                output.seek(record.downloadedSize)
                update(record, 0)
                source.file.inputStream().use { input ->
                    input.channel.position(record.downloadedSize)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = source.read(input, buffer, record.downloadedSize, ::checkStopped)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        record = record.copy(downloadedSize = record.downloadedSize + count)
                        val now = timeMillis()
                        if (now - lastUpdate > 1000) {
                            output.sync()
                            val instant = (record.downloadedSize - lastBytes) * 1000 / (now - lastUpdate)
                            speed = if (speed == 0L) instant else (speed * 3 + instant) / 4
                            lastUpdate = now; lastBytes = record.downloadedSize
                            update(record, speed)
                        }
                    }
                }
                checkStopped()
                output.sync()
            } finally { runCatching { output.close() } }
            record.copy(state = DownloadState.COMPLETE, totalSize = record.downloadedSize)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            record.copy(
                state = if (error is DownloadFailure && error.code == 1) DownloadState.PAUSED else DownloadState.FAILED,
                errorMessage = if (error is DownloadFailure && error.code == 1) record.errorMessage
                else DownloadFailure(if (error is DownloadFailure) error.code else 12, error).message,
            )
        }
        if (result.state == DownloadState.COMPLETE) source.finish(true, result.downloadedSize)
        else if (result.state == DownloadState.FAILED) source.finish(false, 0)
        if (result.state == DownloadState.PAUSED) source.pause() else source.close()
        if (!control.deleted) update(result, 0)
        return result
    }
}
