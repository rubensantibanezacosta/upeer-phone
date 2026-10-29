package app.upeer.transport

import android.util.Log
import java.io.File
import java.io.RandomAccessFile

internal object SyncFileReceive {
  enum class Outcome { PROGRESS, DONE, DISCARDED, QUEUED }

  private class Open(
    val fileId: String,
    val fileName: String,
    val totalChunks: Int,
    val chunkSize: Int,
    val target: File,
    val partial: File,
    val handle: RandomAccessFile,
  ) {
    val received = HashSet<Int>()
  }

  private val lock = Any()
  private val open = HashMap<String, Open>()
  private val early = HashMap<String, HashMap<Int, ByteArray>>()

  @Volatile
  private var directory: File? = null

  fun attachDirectory(dir: File) {
    directory = dir
  }

  fun open(requestId: String, fileId: String, meta: SyncFilePackets.ResMeta): String? {
    val root = directory ?: run {
      Log.w(TAG, "Sin carpeta para los adjuntos sincronizados")
      return null
    }
    close(requestId)
    val target = File(File(root, "synced").apply { mkdirs() }, storedName(fileId, meta.fileName))
    val partial = File(target.parentFile, "${target.name}$PARTIAL_SUFFIX")
    return try {
      runCatching { partial.delete() }
      val handle = RandomAccessFile(partial, "rw")
      handle.setLength(0)
      synchronized(lock) {
        open[requestId] = Open(fileId, meta.fileName, meta.totalChunks, meta.chunkSize, target, partial, handle)
      }
      target.absolutePath
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo abrir $fileId para recibirlo: ${message(cause)}")
      null
    }
  }

  fun write(chunk: SyncFilePackets.Chunk, fileId: String): Outcome = synchronized(lock) {
    val entry = open[chunk.requestId] ?: run {
      stashEarly(chunk)
      return@synchronized Outcome.QUEUED
    }
    if (entry.fileId != fileId) return@synchronized Outcome.DISCARDED
    if (chunk.index >= entry.totalChunks) {
      Log.w(TAG, "Tramo fuera del rango declarado del adjunto $fileId (#${chunk.index})")
      close(chunk.requestId)
      return@synchronized Outcome.DISCARDED
    }
    try {
      entry.handle.seek(chunk.index.toLong() * entry.chunkSize.toLong())
      entry.handle.write(chunk.bytes)
      entry.received.add(chunk.index)
      if (entry.received.size < entry.totalChunks) Outcome.PROGRESS else Outcome.DONE
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo escribir el tramo del adjunto $fileId: ${message(cause)}")
      close(chunk.requestId)
      Outcome.DISCARDED
    }
  }

  fun progress(requestId: String): Float? = synchronized(lock) {
    val entry = open[requestId] ?: return@synchronized null
    if (entry.totalChunks <= 0) return@synchronized null
    (entry.received.size.toFloat() / entry.totalChunks.toFloat()).coerceIn(0f, 1f)
  }

  fun flushEarly(requestId: String, fileId: String): Outcome {
    val store = synchronized(lock) { early.remove(requestId) } ?: return Outcome.PROGRESS
    var outcome = Outcome.PROGRESS
    for (index in store.keys.sorted()) {
      val bytes = store[index] ?: continue
      outcome = write(SyncFilePackets.Chunk(requestId, index, bytes), fileId)
      if (outcome == Outcome.DISCARDED) return outcome
    }
    return outcome
  }

  private fun stashEarly(chunk: SyncFilePackets.Chunk) {
    val store = early.getOrPut(chunk.requestId) { HashMap() }
    if (store.size >= MAX_EARLY_CHUNKS) return
    store[chunk.index] = chunk.bytes
  }

  fun finish(requestId: String): String? {
    val entry = synchronized(lock) { open.remove(requestId) } ?: return null
    runCatching { entry.handle.close() }
    return when {
      runCatching { entry.partial.renameTo(entry.target) }.getOrDefault(false) -> entry.target.absolutePath
      else -> {
        runCatching { entry.partial.delete() }
        Log.w(TAG, "No se pudo completar el adjunto ${entry.fileId}: se descarta el parcial")
        null
      }
    }
  }

  fun isStored(fileId: String): Boolean = storedPath(fileId) != null

  fun storedPath(fileId: String): String? {
    if (fileId.isBlank()) return null
    val root = directory ?: return null
    val dir = File(root, "synced")
    val names = runCatching { dir.list() }.getOrNull() ?: return null
    val match = names.firstOrNull { it == fileId || (it.startsWith("$fileId.") && !it.endsWith(PARTIAL_SUFFIX)) } ?: return null
    return File(dir, match).absolutePath
  }

  fun close(requestId: String) {
    val entry = synchronized(lock) { open.remove(requestId) } ?: return
    runCatching { entry.handle.close() }
    runCatching { entry.partial.delete() }
  }

  fun storedName(fileId: String, fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    val raw = if (dot >= 0) fileName.substring(dot + 1) else ""
    val extension = raw.filter { it.isLetterOrDigit() }.lowercase()
    return if (extension.isEmpty() || extension.length > 8) fileId else "$fileId.$extension"
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private const val MAX_EARLY_CHUNKS = 256
  private const val PARTIAL_SUFFIX = ".part"
  private const val TAG = "UPeerTransport"
}
