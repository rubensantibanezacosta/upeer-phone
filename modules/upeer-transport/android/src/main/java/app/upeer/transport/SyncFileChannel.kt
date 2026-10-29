package app.upeer.transport

import android.util.Log
import org.json.JSONObject
import java.util.UUID

internal class SyncFileChannel(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val keys: DeviceSignedPreKeyStore,
  private val channel: SyncChannel,
  private val sendPacket: (SyncPeerRecord, JSONObject) -> Boolean,
  private val streamPackets: (SyncPeerRecord, Iterator<JSONObject>) -> Boolean,
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  private val requests = HashMap<String, PendingRequest>()
  private val serving = HashSet<String>()

  fun isFileType(type: String): Boolean = SyncFilePackets.isFileType(type)

  fun handle(
    packet: JSONObject,
    senderDeviceId: String,
    senderDevicePublicKey: String,
  ): Boolean {
    val device = identity.currentDevice() ?: return false
    val peer = state.syncPeers.list().firstOrNull { it.deviceId == senderDeviceId } ?: run {
      Log.w(TAG, "Paquete ${packet.optString("type")} de ${senderDeviceId.take(12)} sin SYNC_HELLO previo")
      return false
    }
    val plaintext = channel.openRaw(device, keys, packet, senderDevicePublicKey, senderDeviceId) ?: run {
      Log.w(TAG, "Paquete ${packet.optString("type")} de ${senderDeviceId.take(12)} no descifrable")
      return false
    }
    val payload = runCatching { JSONObject(plaintext) }.getOrNull() ?: return false
    when (payload.optString("type")) {
      SyncFilePackets.REQ -> onRequest(peer, payload)
      SyncFilePackets.RES -> onResponse(peer, payload)
      SyncFilePackets.CHUNK -> onChunk(peer, payload)
      else -> Log.i(TAG, "Paquete de ficheros de sincronización sin tratamiento")
    }
    return true
  }

  fun request(fileId: String): Boolean {
    if (fileId.isBlank()) return false
    val peers = state.syncPeers.list().filter { it.address.isNotBlank() }
    if (peers.isEmpty()) {
      Log.w(TAG, "No hay otros dispositivos propios para pedir el adjunto $fileId")
      return false
    }
    val device = identity.currentDevice() ?: return false
    val requestId = UUID.randomUUID().toString().replace("-", "")
    var sent = 0
    for (peer in peers) {
      val packet = SyncFilePackets.request(
        SyncSender(device.deviceId, device.certificate),
        peer.deviceId,
        requestId,
        fileId,
      ) ?: return false
      if (sendPacket(peer, packet)) sent += 1
    }
    if (sent == 0) return false
    requests[requestId] = PendingRequest(fileId, nowMillis())
    Log.i(TAG, "Adjunto $fileId pedido a $sent dispositivo(s) propio(s)")
    return true
  }

  fun isPending(fileId: String): Boolean = requests.values.any { it.fileId == fileId }

  fun forgetRequests(): Unit = requests.clear()

  private fun onRequest(peer: SyncPeerRecord, payload: JSONObject) {
    val request = SyncFilePackets.parseRequest(payload) ?: run {
      Log.w(TAG, "SYNC_FILE_REQ de ${peer.deviceId.take(12)} ilegible")
      return
    }
    synchronized(serving) {
      if (!serving.add(request.requestId)) {
        Log.i(TAG, "Petición ${request.requestId} ya en curso; se ignora el duplicado")
        return
      }
    }
    try {
      SyncFileServe(identity, state).serve(peer, request) { packets -> streamPackets(peer, packets) }
    } finally {
      synchronized(serving) { serving.remove(request.requestId) }
    }
  }

  private fun onResponse(peer: SyncPeerRecord, payload: JSONObject) {
    val response = SyncFilePackets.parseResponse(payload) ?: run {
      Log.w(TAG, "SYNC_FILE_RES de ${peer.deviceId.take(12)} ilegible")
      return
    }
    val pending = requests[response.requestId] ?: run {
      Log.i(TAG, "SYNC_FILE_RES de ${peer.deviceId.take(12)} sin petición viva; se ignora")
      return
    }
    if (pending.fileId != response.fileId) {
      Log.w(TAG, "SYNC_FILE_RES con otro fichero del pedido; se ignora")
      return
    }
    val meta = response.meta ?: run {
      Log.w(TAG, "El dispositivo ${peer.deviceId.take(12)} no tiene ${response.fileId}: ${response.reason}")
      requests.remove(response.requestId)
      return
    }
    if (SyncFileReceive.open(response.requestId, response.fileId, meta) == null) {
      requests.remove(response.requestId)
      Log.w(TAG, "No se pudo preparar la recepción del adjunto ${response.fileId}")
      return
    }
    if (SyncFileReceive.flushEarly(response.requestId, response.fileId) == SyncFileReceive.Outcome.DONE) {
      requests.remove(response.requestId)
      val savedPath = SyncFileReceive.finish(response.requestId)
      if (savedPath != null) state.messages.applySyncedSavedPath(response.fileId, savedPath)
      Log.i(TAG, "Adjunto ${response.fileId} recibido de ${peer.deviceId.take(12)}")
    }
  }

  private fun onChunk(peer: SyncPeerRecord, payload: JSONObject) {
    val chunk = SyncFilePackets.parseChunk(payload) ?: return
    val pending = requests[chunk.requestId] ?: return
    when (SyncFileReceive.write(chunk, pending.fileId)) {
      SyncFileReceive.Outcome.PROGRESS -> Unit
      SyncFileReceive.Outcome.QUEUED -> Log.i(TAG, "Tramo temprano del adjunto ${pending.fileId} en espera de la cabecera")
      SyncFileReceive.Outcome.DONE -> {
        requests.remove(chunk.requestId)
        val savedPath = SyncFileReceive.finish(chunk.requestId)
        if (savedPath != null) state.messages.applySyncedSavedPath(pending.fileId, savedPath)
        Log.i(TAG, "Adjunto ${pending.fileId} recibido de ${peer.deviceId.take(12)}")
      }
      SyncFileReceive.Outcome.DISCARDED -> {
        requests.remove(chunk.requestId)
        SyncFileReceive.close(chunk.requestId)
        Log.w(TAG, "Recepción del adjunto ${pending.fileId} descartada")
      }
    }
  }

  internal data class PendingRequest(val fileId: String, val startedAt: Long)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
