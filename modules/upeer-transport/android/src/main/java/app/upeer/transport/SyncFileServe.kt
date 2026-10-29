package app.upeer.transport

import android.util.Log
import org.json.JSONObject
import java.io.File

internal class SyncFileServe(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
) {
  fun serve(
    peer: SyncPeerRecord,
    request: SyncFilePackets.Req,
    stream: (Iterator<JSONObject>) -> Boolean,
  ) {
    val device = identity.currentDevice() ?: return
    val sender = SyncSender(device.deviceId, device.certificate)
    val content = state.messages.attachmentContent(request.fileId)
    val savedPath = content?.let(MessageAttachments::savedPathOfContent)
    val file = savedPath?.let(::File)
    if (file == null || !file.isFile) {
      Log.i(TAG, "El adjunto ${request.fileId} no está en este dispositivo")
      refuse(peer, sender, request, "not-here", stream)
      return
    }
    val size = file.length()
    if (size <= 0L || size > SyncFilePackets.MAX_BYTES) {
      refuse(peer, sender, request, "too-large", stream)
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
    val packets = SyncFileOutgoing(header, sender, peer.deviceId, request.requestId, file, size, total)
    try {
      if (!stream(packets)) {
        Log.w(TAG, "Enlace de sincronización cortado sirviendo ${request.fileId}")
        return
      }
      Log.i(TAG, "Adjunto ${request.fileId} servido en $total tramos a ${peer.deviceId.take(12)}")
    } finally {
      packets.release()
    }
  }

  private fun refuse(
    peer: SyncPeerRecord,
    sender: SyncSender,
    request: SyncFilePackets.Req,
    reason: String,
    stream: (Iterator<JSONObject>) -> Boolean,
  ) {
    val packet = SyncFilePackets.response(sender, peer.deviceId, request.requestId, request.fileId, null, reason) ?: return
    if (!stream(listOf(packet).iterator())) Log.w(TAG, "No se pudo responder la negativa de ${request.fileId}")
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
