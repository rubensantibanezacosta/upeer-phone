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

  /**
   * Devuelve un frame con **varios tramos** cuando quedan suficientes.
   *
   * Un tramo por frame paga una vuelta de ratchet y una escritura por cada trozo de 192 KB; agrupando
   * de 8 en 8 ese coste se divide, y el receptor no cambia porque sigue recibiendo tramos con su índice.
   * El último lote puede llevar menos.
   */
  override fun next(): JSONObject {
    if (index < 0) {
      index = 0
      return header ?: throw IllegalStateException("sin cabecera del adjunto")
    }
    val source = handle ?: RandomAccessFile(file, "r").also { handle = it }
    val pending = minOf(SyncFilePackets.MAX_BATCH_CHUNKS, total - index)
    val first = index
    val slices = ArrayList<ByteArray>(pending)
    for (offset in 0 until pending) {
      val current = first + offset
      val bounds = SyncFilePackets.bounds(current, SyncFilePackets.CHUNK_SIZE, size)
        ?: throw IllegalStateException("tramo $current fuera del fichero")
      source.seek(bounds.first)
      val read = source.read(buffer, 0, bounds.second)
      if (read <= 0) throw IllegalStateException("no se pudo leer el tramo $current")
      slices.add(buffer.copyOf(read))
    }
    index += pending
    if (pending == 1) {
      return SyncFilePackets.chunk(sender, recipient, requestId, first, slices[0])
        ?: throw IllegalStateException("tramo $first no construible")
    }
    return SyncFilePackets.chunkBatch(sender, recipient, requestId, first, slices)
      ?: throw IllegalStateException("lote desde el tramo $first no construible")
  }

  fun release() {
    runCatching { handle?.close() }
    handle = null
  }
}
