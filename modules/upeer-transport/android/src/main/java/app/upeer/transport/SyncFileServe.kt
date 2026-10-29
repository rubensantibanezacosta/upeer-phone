package app.upeer.transport

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

internal class SyncFileServe(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
) {
  fun serve(peer: SyncPeerRecord, request: SyncFilePackets.Req, sendPacket: (JSONObject) -> Boolean) {
    val device = identity.currentDevice() ?: return
    val sender = SyncSender(device.deviceId, device.certificate)
    val content = state.messages.attachmentContent(request.fileId)
    val savedPath = content?.let(MessageAttachments::savedPathOfContent)
    val file = savedPath?.let(::File)
    if (file == null || !file.isFile) {
      Log.i(TAG, "El adjunto ${request.fileId} no está en este dispositivo")
      refuse(peer, sender, request, "not-here", sendPacket)
      return
    }
    val size = file.length()
    if (size <= 0L || size > SyncFilePackets.MAX_BYTES) {
      refuse(peer, sender, request, "too-large", sendPacket)
      return
    }
    val total = SyncFilePackets.chunkCount(size, SyncFilePackets.CHUNK_SIZE)
    val meta = SyncFilePackets.ResMeta(
      fileName = content?.let(MessageAttachments::fileNameOfContent).orEmpty().ifEmpty { file.name },
      fileSize = size,
      chunkSize = SyncFilePackets.CHUNK_SIZE,
      totalChunks = total,
    )
    val header = SyncFilePackets.response(sender, peer.deviceId, request.requestId, request.fileId, meta) ?: return
    if (!sendPacket(header)) {
      Log.w(TAG, "No se pudo confirmar el envío del adjunto ${request.fileId}")
      return
    }
    var handle: RandomAccessFile? = null
    try {
      handle = RandomAccessFile(file, "r")
      val buffer = ByteArray(SyncFilePackets.CHUNK_SIZE)
      for (index in 0 until total) {
        val bounds = SyncFilePackets.bounds(index, SyncFilePackets.CHUNK_SIZE, size) ?: break
        handle.seek(bounds.first)
        val read = handle.read(buffer, 0, bounds.second)
        if (read <= 0) break
        val data = buffer.copyOf(read)
        val packet = SyncFilePackets.chunk(sender, peer.deviceId, request.requestId, index, data) ?: break
        if (!sendPacket(packet)) {
          Log.w(TAG, "Enlace de sincronización cortado sirviendo ${request.fileId} (#$index)")
          return
        }
      }
      Log.i(TAG, "Adjunto ${request.fileId} servido en $total tramos a ${peer.deviceId.take(12)}")
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo servir el adjunto ${request.fileId}: ${message(cause)}")
    } finally {
      runCatching { handle?.close() }
    }
  }

  private fun refuse(
    peer: SyncPeerRecord,
    sender: SyncSender,
    request: SyncFilePackets.Req,
    reason: String,
    sendPacket: (JSONObject) -> Boolean,
  ) {
    val packet = SyncFilePackets.response(sender, peer.deviceId, request.requestId, request.fileId, null, reason) ?: return
    if (!sendPacket(packet)) Log.w(TAG, "No se pudo responder la negativa de ${request.fileId}")
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
