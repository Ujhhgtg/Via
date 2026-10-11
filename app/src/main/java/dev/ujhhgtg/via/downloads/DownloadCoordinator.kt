package dev.ujhhgtg.via.downloads

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dev.ujhhgtg.via.common.applicationIoScope
import dev.ujhhgtg.via.data.BrowserPreferences
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap


data class DownloadRequest(
    val url: String,
    val userAgent: String? = null,
    val contentDisposition: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: String? = null,
    val referrer: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val path: String? = null,
    val fileUri: Uri? = null,
    val priority: Int = 0,
    val directory: String? = null,
    val contentLength: Long = 0,
    val streamId: String? = null,
)

/** c5.b/m5.i: process-owned three-task queue; each HTTP task retains its original range chunks. */
class DownloadCoordinator(private val context: Context, private val repository: DownloadRepository = DownloadRepository(context)) {
    private val running = linkedMapOf<Long, DownloadControl>()
    private val volatileData = HashMap<Long, String>() // m5.c: data URI bodies are never written to tasks.url.
    private val volatileStreams = HashMap<Long, DownloadStreamRegistry.Source>()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<(DownloadRecord) -> Unit>()
    private val stateListeners = linkedSetOf<(DownloadRecord, Int, Int) -> Unit>()
    private val speeds = ConcurrentHashMap<Long, Long>()

    fun addStateListener(listener: (DownloadRecord, Int, Int) -> Unit) { stateListeners += listener }
    fun removeStateListener(listener: (DownloadRecord, Int, Int) -> Unit) { stateListeners -= listener }

    fun addListener(listener: (DownloadRecord) -> Unit) { listeners += listener }
    fun removeListener(listener: (DownloadRecord) -> Unit) { listeners -= listener }
    fun speed(id: Long): Long = speeds[id] ?: 0L
    fun hasRunning(): Boolean = running.isNotEmpty()
    fun hasPending(): Boolean = repository.list(intArrayOf(DownloadState.WAITING, DownloadState.WAITING_NETWORK)).isNotEmpty()
    fun get(id: Long): DownloadRecord? = repository.get(id)
    fun list(): List<DownloadRecord> = repository.list()

    fun enqueue(request: DownloadRequest): Long {
        val data = request.url.startsWith("data:")
        val stream = request.streamId?.let(DownloadStreamRegistry::take)
        if (request.streamId != null && stream == null) throw IOException("Download stream is no longer available")
        try {
            stream?.pause() // A queued task must not keep consuming its response in the background.
            val mime = request.mimeType ?: if (data) DownloadDataUrl.mime(request.url) else null
            val name = request.fileName?.takeIf(String::isNotEmpty) ?: DownloadFiles.name(request.url, request.contentDisposition, mime)
            val destination = if (request.path == null && request.fileUri == null)
                DownloadFiles.create(context, request.directory ?: BrowserPreferences(context).downloadDirectory, name, mime)
            else DownloadFiles.Destination(name, request.path, request.fileUri)
            val headers = request.headers + listOfNotNull(request.userAgent?.let { "User-Agent" to it },
                request.referrer?.let { "Referer" to it }, request.cookies?.let { "Cookie" to it }).toMap()
            val uri = destination.uri ?: destination.path?.let { Uri.fromFile(File(it)) }
            val id = repository.insert(DownloadRecord(name = destination.name, url = if (data || stream != null) null else request.url,
                headers = headers, mimeType = mime, path = request.directory ?: BrowserPreferences(context).downloadDirectory,
                fileUri = uri, totalSize = request.contentLength, chunks = if (data || stream != null) 1 else 8, flags = if (data || stream != null) 0 else 1, priority = request.priority,
                state = DownloadState.WAITING, createdAt = now(), updatedAt = now()))
            if (id <= 0) throw IOException("Cannot save download")
            if (data) volatileData[id] = request.url
            if (stream != null) volatileStreams[id] = stream
            notifyChanged(requireNotNull(repository.get(id)))
            DownloadService.start(context, DownloadService.ACTION_RESUME, id)
            return id
        } catch (error: Throwable) {
            stream?.close()
            throw error
        }
    }

    fun start(id: Long): Boolean {
        val record = repository.get(id) ?: return false
        if (running.containsKey(id)) return true
        if (record.state != DownloadState.WAITING && record.state != DownloadState.WAITING_NETWORK)
            persist(record.copy(state = DownloadState.WAITING))
        dispatch()
        return true
    }

    fun pause(id: Long): Boolean {
        val record = repository.get(id) ?: return false
        running[id]?.let { control ->
            // m5.b's non-network data task is deliberately not pausable.
            if (control.pausable) { control.pauseAction?.invoke() ?: run { control.paused = true } }
        } ?: run { if (!record.isComplete && !record.isFailed) { persist(record.copy(state = DownloadState.PAUSED)); dispatch() } }
        return true
    }

    fun resume(id: Long): Boolean {
        if (repository.get(id) == null) return false
        DownloadService.start(context, DownloadService.ACTION_RESUME, id)
        return true
    }

    fun redownload(id: Long): Boolean {
        val record = repository.get(id) ?: return false
        if (running.containsKey(id) && !pause(id)) return false
        persist(record.copy(chunks = 8, flags = record.flags and 2.inv(), headers = record.headers.filterKeys { it != "ETag" }))
        return repository.clearChunks(id) && start(id)
    }

    /** Original service RESUME_ALL pauses tasks; PAUSE_ALL starts pending queue entries (source literal dispatch). */
    fun pauseAll(): Boolean {
        list().filter { it.id !in running && it.isActive }.forEach { persist(it.copy(state = DownloadState.PAUSED)) }
        running.keys.toList().forEach(::pause)
        running.clear()
        return true
    }
    fun startPending(): Boolean { dispatch(); return running.isNotEmpty() }

    private fun dispatch() {
        val candidates = repository.list(intArrayOf(DownloadState.WAITING, DownloadState.WAITING_NETWORK))
            .sortedWith(compareBy<DownloadRecord> { it.state }.thenByDescending { it.createdAt })
        for (record in candidates) {
            if (running.size >= 3) break
            if (record.id in running) continue
            val source = record.url ?: volatileData[record.id]
            val stream = volatileStreams[record.id]
            val network = DownloadNetwork.isHttp(source)
            val data = source?.takeIf { it.startsWith("data:") }
            // m5.f/k submits an empty task for an unrecognized source; it completes immediately
            // and never occupies the three active queue slots.
            if (!network && data == null && stream == null) continue
            val control = DownloadControl(pausable = network || stream != null)
            running[record.id] = control
            val transfer = if (network) DownloadTransfer(context, record, repository, control, ::publishTransfer) else null
            if (stream != null) control.pauseAction = { control.paused = true; stream.pause() }
            // e5.d serviced task orchestrators and chunk workers; the IO dispatcher now does.
            applicationIoScope.launch {
                if (data != null) transferData(record, data, control)
                else if (stream != null) transferStream(record, stream, control)
                else transfer?.run()
                if (control.deleted) main.post { running.remove(record.id); dispatch() }
            }
        }
    }

    private fun transferData(initial: DownloadRecord, data: String, control: DownloadControl): DownloadRecord {
        val result = runCatching {
            val output = DownloadOutput.open(context, initial)
            try { if (!DownloadDataUrl.write(data, output)) throw DownloadFailure(40) }
            finally { try { output.close() } catch (_: IOException) { } }
            initial.copy(state = DownloadState.COMPLETE)
        }.getOrElse { initial.copy(state = DownloadState.FAILED, errorMessage = DownloadFailure(if (it is DownloadFailure) it.code else 12, it).message) }
        if (!control.deleted) publishTransfer(result, 0)
        return result
    }

    private suspend fun transferStream(record: DownloadRecord, source: DownloadStreamRegistry.Source, control: DownloadControl): DownloadRecord =
        DownloadStreamTransfer(record, source, control, { DownloadOutput.open(context, it) }, ::publishTransfer).run()

    /** m5.i's state observer releases the queue slot before UI/service observers receive the event. */
    private fun publishTransfer(record: DownloadRecord, speed: Long) {
        speeds[record.id] = speed
        val previous = repository.get(record.id)?.state ?: record.state
        repository.update(record.copy(updatedAt = now()))
        main.post {
            if (record.isComplete || record.isFailed || record.state == DownloadState.PAUSED) {
                running.remove(record.id); volatileData.remove(record.id); speeds.remove(record.id)
                if (record.state != DownloadState.PAUSED) volatileStreams.remove(record.id)?.close()
                dispatch()
            } else if (record.state == DownloadState.WAITING_NETWORK) running.remove(record.id)
            listeners.toList().forEach { it(record) }
            if (previous != record.state) stateListeners.toList().forEach { it(record, previous, record.state) }
        }
    }

    /** c5.b.l -> m5.f.b: a caller-owned transfer with h5.b's empty observer, outside the task queue. */
    internal suspend fun downloadTransient(record: DownloadRecord): DownloadRecord = DownloadTransfer(
        context, record, repository, DownloadControl(), update = { _, _ -> }, observeProgress = false,
    ).run()

    fun saveDestination(record: DownloadRecord) { persist(record) }

    fun rename(id: Long, name: String): Boolean {
        val record = repository.get(id) ?: return false
        if (!record.isComplete) return false
        val destination = DownloadFiles.rename(context, record, name)
        persist(record.copy(name = destination.name, path = destination.path, fileUri = destination.uri,
            mimeType = DownloadMimeTypes.mime(name.substringAfterLast('.', ""), "application/octet-stream")))
        return true
    }

    fun delete(id: Long, deleteFile: Boolean = false): Boolean {
        val record = repository.get(id) ?: return false
        running[id]?.let { it.deleted = true; it.paused = true }
        val removed = repository.delete(id)
        repository.clearChunks(id); volatileData.remove(id); volatileStreams.remove(id)?.close()
        if (deleteFile) removeOutput(record)
        notifyChanged(record)
        return removed
    }
    private fun persist(record: DownloadRecord) { repository.update(record.copy(updatedAt = now())); notifyChanged(record) }
    private fun notifyChanged(record: DownloadRecord) { main.post { listeners.toList().forEach { it(record) } } }
    private fun removeOutput(record: DownloadRecord) {
        DownloadFiles.remove(context, record)
    }
    private fun now() = System.currentTimeMillis() / 1000
    companion object {
        // Only ever holds the application context.
        @SuppressLint("StaticFieldLeak")
        private var instance: DownloadCoordinator? = null
        @Synchronized fun get(context: Context): DownloadCoordinator = instance ?: DownloadCoordinator(context.applicationContext).also {
            instance = it
            applicationIoScope.launch { DownloadStreamRegistry.cleanup(context.applicationContext) }
        }
    }
}
