package app.upeer.transport

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

internal class SyncFileOutgoing(
  private val header: JSONObject?,
  private val sender: SyncSender,
  private val recipient: String,
  private val requestId: String,
  private val file: File,
  private val size: Long,
  private val total: Int,
) : Iterator<JSONObject> {
  private var index = -1
  private var handle: RandomAccessFile? = null
  private val buffer = ByteArray(SyncFilePackets.CHUNK_SIZE)

  override fun hasNext(): Boolean = index < total

  override fun next(): JSONObject {
    if (index < 0) {
      index = 0
      return header ?: throw IllegalStateException("sin cabecera del adjunto")
    }
    val bounds = SyncFilePackets.bounds(index, SyncFilePackets.CHUNK_SIZE, size)
      ?: throw IllegalStateException("tramo $index fuera del fichero")
    val source = handle ?: RandomAccessFile(file, "r").also { handle = it }
    source.seek(bounds.first)
    val read = source.read(buffer, 0, bounds.second)
    if (read <= 0) throw IllegalStateException("no se pudo leer el tramo $index")
    val packet = SyncFilePackets.chunk(sender, recipient, requestId, index, buffer.copyOf(read))
      ?: throw IllegalStateException("tramo $index no construible")
    index += 1
    return packet
  }

  fun release() {
    runCatching { handle?.close() }
    handle = null
  }
}
