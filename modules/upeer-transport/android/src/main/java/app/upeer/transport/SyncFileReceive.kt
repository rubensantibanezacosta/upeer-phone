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
    val target: File,
    val handle: RandomAccessFile,
  ) {
    val received = HashSet<Int>()
  }

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
    return try {
      val handle = RandomAccessFile(target, "rw")
      handle.setLength(0)
      open[requestId] = Open(fileId, meta.fileName, meta.totalChunks, target, handle)
      target.absolutePath
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo abrir $fileId para recibirlo: ${message(cause)}")
      null
    }
  }

  fun write(chunk: SyncFilePackets.Chunk, fileId: String): Outcome {
    val entry = open[chunk.requestId] ?: run {
      stashEarly(chunk)
      return Outcome.QUEUED
    }
    if (entry.fileId != fileId) return Outcome.DISCARDED
    if (chunk.index >= entry.totalChunks) {
      Log.w(TAG, "Tramo fuera del rango declarado del adjunto $fileId (#${chunk.index})")
      close(chunk.requestId)
      return Outcome.DISCARDED
    }
    return try {
      entry.handle.seek(chunk.index.toLong() * SyncFilePackets.CHUNK_SIZE)
      entry.handle.write(chunk.bytes)
      entry.received.add(chunk.index)
      if (entry.received.size < entry.totalChunks) Outcome.PROGRESS else Outcome.DONE
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo escribir el tramo del adjunto $fileId: ${message(cause)}")
      close(chunk.requestId)
      Outcome.DISCARDED
    }
  }

  fun flushEarly(requestId: String, fileId: String): Outcome {
    val store = early.remove(requestId) ?: return Outcome.PROGRESS
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
    val entry = open.remove(requestId) ?: return null
    runCatching { entry.handle.close() }
    return entry.target.absolutePath
  }

  fun close(requestId: String) {
    val entry = open.remove(requestId) ?: return
    runCatching { entry.handle.close() }
  }

  fun storedName(fileId: String, fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    val raw = if (dot >= 0) fileName.substring(dot + 1) else ""
    val extension = raw.filter { it.isLetterOrDigit() }.lowercase()
    return if (extension.isEmpty() || extension.length > 8) fileId else "$fileId.$extension"
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private const val MAX_EARLY_CHUNKS = 256
  private const val TAG = "UPeerTransport"
}
