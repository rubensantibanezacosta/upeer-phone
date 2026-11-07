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
  private val onStored: (String) -> Unit = {},
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  private val lock = Any()
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
    expireIdle()
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
    synchronized(lock) { requests[requestId] = PendingRequest(requestId, fileId, nowMillis()) }
    Log.i(TAG, "Adjunto $fileId pedido a $sent dispositivo(s) propio(s)")
    return true
  }

  fun isPending(fileId: String): Boolean {
    expireIdle()
    return synchronized(lock) { requests.values.any { it.fileId == fileId } }
  }

  fun progressOf(fileId: String): Float? {
    val ids = synchronized(lock) { requests.values.filter { it.fileId == fileId }.map { it.requestId } }
    for (requestId in ids) {
      val progress = SyncFileReceive.progress(requestId) ?: continue
      return progress
    }
    return null
  }

  fun forgetRequests(): Unit = synchronized(lock) { requests.clear() }

  private fun expireIdle() {
    val now = nowMillis()
    val expired = synchronized(lock) {
      val stale = requests.filterValues { now - it.touchedAt > REQUEST_IDLE_TTL_MS }.keys.toList()
      for (requestId in stale) requests.remove(requestId)
      stale
    }
    if (expired.isEmpty()) return
    for (requestId in expired) SyncFileReceive.close(requestId)
    Log.i(TAG, "Peticiones de adjunto sin avance descartadas: ${expired.size}")
  }

  private fun touch(requestId: String): PendingRequest? {
    val now = nowMillis()
    return synchronized(lock) {
      val entry = requests[requestId] ?: return null
      entry.touchedAt = now
      entry
    }
  }

  private fun complete(requestId: String): PendingRequest? = synchronized(lock) { requests.remove(requestId) }

  private fun onRequest(peer: SyncPeerRecord, payload: JSONObject) {
    val request = SyncFilePackets.parseRequest(payload) ?: run {
      Log.w(TAG, "SYNC_FILE_REQ de ${peer.deviceId.take(12)} ilegible")
      return
    }
    synchronized(lock) {
      if (!serving.add(request.requestId)) {
        Log.i(TAG, "Petición ${request.requestId} ya en curso; se ignora el duplicado")
        return
      }
    }
    try {
      SyncFileServe(identity, state).serve(peer, request) { packets -> streamPackets(peer, packets) }
    } finally {
      synchronized(lock) { serving.remove(request.requestId) }
    }
  }

  private fun onResponse(peer: SyncPeerRecord, payload: JSONObject) {
    val response = SyncFilePackets.parseResponse(payload) ?: run {
      Log.w(TAG, "SYNC_FILE_RES de ${peer.deviceId.take(12)} ilegible")
      return
    }
    val pending = touch(response.requestId) ?: run {
      Log.i(TAG, "SYNC_FILE_RES de ${peer.deviceId.take(12)} sin petición viva; se ignora")
      return
    }
    if (pending.fileId != response.fileId) {
      Log.w(TAG, "SYNC_FILE_RES con otro fichero del pedido; se ignora")
      return
    }
    val meta = response.meta ?: run {
      Log.w(TAG, "El dispositivo ${peer.deviceId.take(12)} no tiene ${response.fileId}: ${response.reason}")
      complete(response.requestId)
      return
    }
    if (SyncFileReceive.open(response.requestId, response.fileId, meta) == null) {
      complete(response.requestId)
      Log.w(TAG, "No se pudo preparar la recepción del adjunto ${response.fileId}")
      return
    }
    if (SyncFileReceive.flushEarly(response.requestId, response.fileId) == SyncFileReceive.Outcome.DONE) {
      complete(response.requestId)
      val savedPath = SyncFileReceive.finish(response.requestId)
      if (savedPath != null) {
        state.messages.applySyncedSavedPath(response.fileId, savedPath)
        onStored(response.fileId)
      }
      Log.i(TAG, "Adjunto ${response.fileId} recibido de ${peer.deviceId.take(12)}")
    }
  }

  /**
   * Aplica un frame de datos, que puede traer **varios tramos** (`batch`) o uno solo (`data`).
   *
   * Los tramos de un lote se escriben en orden; el que completa el fichero cierra la recepción y el
   * resto del lote ya no tiene nada que hacer, así que se para.
   */
  private fun onChunk(peer: SyncPeerRecord, payload: JSONObject) {
    for (chunk in SyncFilePackets.parseChunks(payload)) {
      if (!applyChunk(peer, chunk)) return
    }
  }

  /** Devuelve `false` cuando la recepción termina y no hay que seguir con el resto del lote. */
  private fun applyChunk(peer: SyncPeerRecord, chunk: SyncFilePackets.Chunk): Boolean {
    val pending = touch(chunk.requestId) ?: return false
    when (SyncFileReceive.write(chunk, pending.fileId)) {
      SyncFileReceive.Outcome.PROGRESS -> Unit
      SyncFileReceive.Outcome.QUEUED -> Log.i(TAG, "Tramo temprano del adjunto ${pending.fileId} en espera de la cabecera")
      SyncFileReceive.Outcome.DONE -> {
        complete(chunk.requestId)
        val savedPath = SyncFileReceive.finish(chunk.requestId)
        if (savedPath != null) {
          state.messages.applySyncedSavedPath(pending.fileId, savedPath)
          onStored(pending.fileId)
        }
        Log.i(TAG, "Adjunto ${pending.fileId} recibido de ${peer.deviceId.take(12)}")
        return false
      }
      SyncFileReceive.Outcome.DISCARDED -> {
        complete(chunk.requestId)
        SyncFileReceive.close(chunk.requestId)
        Log.w(TAG, "Recepción del adjunto ${pending.fileId} descartada")
        return false
      }
    }
    return true
  }

  internal data class PendingRequest(val requestId: String, val fileId: String, var touchedAt: Long)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val REQUEST_IDLE_TTL_MS = 240_000L
  }
}
