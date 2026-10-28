package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject
import java.util.UUID

internal class FileOutbound(
  private val accountCrypto: () -> FileSendCrypto?,
  private val deviceCrypto: () -> FileSendCrypto?,
  private val directory: FileSendDirectory,
  private val messages: TransportMessages,
  private val send: (String, JSONObject, String) -> Boolean,
  private val windowFactory: (Int) -> FileSendWindow = { FileSendWindow(it) },
  private val groupMembers: (String) -> List<String>? = { null },
  private val selfUpeerId: () -> String = { "" },
) {
  internal class Request(
    val upeerId: String,
    val path: String,
    val fileName: String,
    val mimeType: String,
    val caption: String?,
    val isVoiceNote: Boolean,
    val chatUpeerId: String?,
    val messageId: String?,
  )

  internal enum class Phase { PROPOSED, TRANSFERRING, AWAITING_DONE_ACK, COMPLETED, CANCELLED }

  internal class Sending(
    val fileId: String,
    val upeerId: String,
    val messageId: String,
    val chatUpeerId: String,
    val plan: FileSendPlan.Plan,
    val key: ByteArray,
    val proposal: JSONObject,
    val window: FileSendWindow,
  ) {
    val lock = Object()
    @Volatile var phase = Phase.PROPOSED
    @Volatile var lastProgress = System.currentTimeMillis()
  }

  private val entries = java.util.concurrent.ConcurrentHashMap<String, Sending>()

  fun start(request: Request): String {
    val plan = FileSendPlan.plan(request.path, request.fileName, request.mimeType)
    require(plan.fileSize > 0L) { "El archivo está vacío" }
    val account = accountCrypto() ?: throw IllegalStateException("No hay identidad para enviar el archivo")
    val crypto = deviceCrypto() ?: account
    val group = request.upeerId.lowercase().takeIf { it.startsWith(ProtocolGroups.PREFIX) }
    val targets = if (group == null) listOf(request.upeerId) else groupRecipients(group)
    if (group == null && directory.publicKeyFor(request.upeerId) == null) {
      throw IllegalStateException("El contacto ${request.upeerId} no es conocido")
    }
    require(targets.isNotEmpty()) { "El grupo no tiene miembros a los que enviar el archivo" }
    val chatUpeerId = group ?: request.upeerId
    val fileIds = targets.map { UUID.randomUUID().toString() }
    val messageId = request.messageId?.takeIf { it.isNotBlank() }
      ?: if (group == null) fileIds.first() else UUID.randomUUID().toString()
    val sendings = ArrayList<Sending>()
    for ((position, target) in targets.withIndex()) {
      val peerPublicKey = directory.publicKeyFor(target)
      if (peerPublicKey == null) {
        Log.w(TAG, "Miembro $target sin clave conocida; no se le envía el archivo de $chatUpeerId")
        continue
      }
      val peerDeviceKey = directory.devicePublicKeyFor(target)
        ?.lowercase()
        ?.takeIf { DhtLocation.isHex(it, 32) }
      val key = crypto.newFileKey()
      require(key.size == FileTransferState.FILE_KEY_SIZE) { "Clave de archivo no válida" }
      val fileId = fileIds[position]
      val proposal = JSONObject()
        .put("type", "FILE_PROPOSAL")
        .put("fileId", fileId)
        .put("fileName", plan.fileName)
        .put("fileSize", plan.fileSize)
        .put("mimeType", plan.mimeType)
        .put("totalChunks", plan.totalChunks)
        .put("chunkSize", plan.chunkSize)
        .put("fileHash", plan.fileHash)
        .put("messageId", messageId)
        .put("encryptedKey", crypto.sealFileKeyFor(key, peerPublicKey))
      if (peerDeviceKey != null) {
        proposal.put("deviceEncryptedKey", crypto.sealFileKeyFor(key, peerDeviceKey))
      }
      request.caption?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { proposal.put("caption", it.take(FileReceivePolicy.MAX_CAPTION_LENGTH)) }
      if (request.isVoiceNote) proposal.put("isVoiceNote", true)
      if (group != null) proposal.put("chatUpeerId", group)

      val sending = Sending(
        fileId = fileId,
        upeerId = target,
        messageId = messageId,
        chatUpeerId = chatUpeerId,
        plan = plan,
        key = key,
        proposal = proposal,
        window = windowFactory(plan.totalChunks),
      )
      entries[fileId] = sending
      sendings += sending
      saveMessage(sending, "pending", null)
      Log.i(TAG, "FILE_PROPOSAL $fileId (${plan.fileName}, ${plan.fileSize} bytes, ${plan.totalChunks} trozos) para $target")
      if (!deliver(sending, proposal, "FILE_PROPOSAL")) abort(sending, "no se pudo entregar la propuesta")
    }
    if (sendings.none { it.phase == Phase.PROPOSED }) {
      throw IllegalStateException("No se pudo enviar la propuesta del archivo")
    }
    for (sending in sendings) {
      if (sending.phase != Phase.PROPOSED) continue
      launch("upeer-file-proposal-${sending.fileId}") { awaitAccept(sending) }
    }
    return messageId
  }

  fun handle(packet: JSONObject, senderUpeerId: String, type: String): Boolean {
    val sending = entries[packet.optString("fileId")] ?: return false
    if (sending.upeerId != senderUpeerId) {
      Log.w(TAG, "$type de $senderUpeerId no coincide con el destinatario de ${sending.fileId}")
      return false
    }
    when (type) {
      "FILE_ACCEPT" -> onAccept(sending)
      "FILE_ACK" -> onAck(sending, packet.optInt("chunkIndex", -1))
      "FILE_DONE_ACK" -> onDoneAck(sending)
      "FILE_CANCEL" -> abort(sending, "cancelada por el destinatario")
      else -> return false
    }
    return true
  }

  fun cancel(fileId: String): Boolean {
    val sending = entries[fileId] ?: return false
    for (sibling in entries.values.filter { it.messageId == sending.messageId }) {
      abort(sibling, "cancelada localmente")
    }
    return true
  }

  fun sendingJSON(): String {
    val array = org.json.JSONArray()
    for (entry in entries.values) {
      array.put(
        JSONObject()
          .put("fileId", entry.fileId)
          .put("upeerId", entry.upeerId)
          .put("fileName", entry.plan.fileName)
          .put("fileSize", entry.plan.fileSize)
          .put("mimeType", entry.plan.mimeType)
          .put("path", entry.plan.file.absolutePath)
          .put("progress", JSONObject().put("acked", ackedCount(entry)).put("total", entry.plan.totalChunks)),
      )
    }
    return array.toString()
  }

  private fun onAccept(sending: Sending) {
    if (!transition(sending, Phase.PROPOSED, Phase.TRANSFERRING)) return
    sending.lastProgress = System.currentTimeMillis()
    saveMessage(sending, "sent", null)
    Log.i(TAG, "FILE_ACCEPT ${sending.fileId}; comienza el envío de trozos")
    launch("upeer-file-send-${sending.fileId}") { stream(sending) }
  }

  private fun onAck(sending: Sending, chunkIndex: Int) {
    if (sending.phase != Phase.TRANSFERRING) return
    val accepted = synchronized(sending.lock) {
      val now = System.currentTimeMillis()
      val outcome = sending.window.ack(chunkIndex, now)
      if (outcome) sending.lastProgress = now
      outcome
    }
    if (!accepted) {
      Log.w(TAG, "FILE_ACK ignorado (${sending.fileId} #$chunkIndex)")
      return
    }
    synchronized(sending.lock) { sending.lock.notifyAll() }
  }

  private fun onDoneAck(sending: Sending) {
    synchronized(sending.lock) {
      if (sending.phase != Phase.AWAITING_DONE_ACK && sending.phase != Phase.TRANSFERRING) return
      sending.phase = Phase.COMPLETED
      sending.lock.notifyAll()
    }
    entries.remove(sending.fileId)
    sending.key.fill(0)
    saveMessage(sending, "delivered", sending.plan.file.absolutePath)
    Log.i(TAG, "Transferencia ${sending.fileId} confirmada por el destinatario")
  }

  private fun awaitAccept(sending: Sending) {
    repeat(MAX_PROPOSAL_ATTEMPTS) {
      Thread.sleep(PROPOSAL_RETRY_MS)
      if (sending.phase != Phase.PROPOSED) return
      Log.i(TAG, "Reintentando FILE_PROPOSAL ${sending.fileId}")
      deliver(sending, sending.proposal, "FILE_PROPOSAL")
    }
    if (sending.phase == Phase.PROPOSED) abort(sending, "sin respuesta al FILE_PROPOSAL")
  }

  private fun stream(sending: Sending) {
    try {
      while (sending.phase == Phase.TRANSFERRING) {
        val batch = synchronized(sending.lock) { sending.window.nextBatch(System.currentTimeMillis()) }
        for (index in batch) {
          if (!sendChunk(sending, index)) return
        }
        val now = System.currentTimeMillis()
        val expired = synchronized(sending.lock) { sending.window.expired(now) }
        for (index in expired) {
          if (!sendChunk(sending, index)) return
        }
        if (expired.isNotEmpty() && now - sending.lastProgress > STALL_TIMEOUT_MS / 2) {
          synchronized(sending.lock) { sending.window.backoff() }
        }
        if (now - sending.lastProgress > STALL_TIMEOUT_MS) {
          abort(sending, "sin progreso durante ${STALL_TIMEOUT_MS} ms")
          return
        }
        val idle = synchronized(sending.lock) { sending.window.complete() || !sending.window.hasWork() }
        if (idle) break
        if (batch.isEmpty() && expired.isEmpty()) {
          synchronized(sending.lock) { sending.lock.wait(WINDOW_POLL_MS) }
        }
      }
      if (sending.phase != Phase.TRANSFERRING) return
      sendDone(sending)
    } catch (cause: InterruptedException) {
      Thread.currentThread().interrupt()
      abort(sending, "envío interrumpido")
    } catch (cause: Exception) {
      abort(sending, "error al enviar trozos: ${message(cause)}")
    } finally {
      sending.key.fill(0)
    }
  }

  private fun sendChunk(sending: Sending, index: Int): Boolean {
    val crypto = deviceCrypto() ?: accountCrypto() ?: run {
      abort(sending, "sin identidad para cifrar el trozo")
      return false
    }
    val packet = FileChunkPacket.build(sending.fileId, sending.plan, index, sending.key, crypto) ?: run {
      Log.w(TAG, "No se pudo preparar el trozo #$index de ${sending.fileId}")
      abort(sending, "no se pudo preparar el trozo #$index")
      return false
    }
    return deliver(sending, packet, "FILE_CHUNK")
  }

  private fun sendDone(sending: Sending) {
    synchronized(sending.lock) {
      if (sending.phase != Phase.TRANSFERRING) return
      sending.phase = Phase.AWAITING_DONE_ACK
      sending.lastProgress = System.currentTimeMillis()
    }
    if (!deliver(sending, JSONObject().put("type", "FILE_DONE").put("fileId", sending.fileId), "FILE_DONE")) {
      abort(sending, "no se pudo entregar FILE_DONE")
      return
    }
    Log.i(TAG, "FILE_DONE ${sending.fileId} (${sending.plan.totalChunks} trozos)")
  }

  private fun abort(sending: Sending, reason: String) {
    val wasActive = synchronized(sending.lock) {
      val active = sending.phase != Phase.COMPLETED && sending.phase != Phase.CANCELLED
      if (sending.phase != Phase.TRANSFERRING) sending.key.fill(0)
      sending.phase = Phase.CANCELLED
      sending.lock.notifyAll()
      active
    }
    entries.remove(sending.fileId)
    if (!wasActive) return
    Log.w(TAG, "Transferencia ${sending.fileId} cancelada: $reason")
    saveMessage(sending, "failed", null)
    deliver(sending, JSONObject().put("type", "FILE_CANCEL").put("fileId", sending.fileId), "FILE_CANCEL")
  }

  private fun deliver(sending: Sending, data: JSONObject, label: String): Boolean =
    try {
      send(sending.upeerId, data, label)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo enviar $label a ${sending.upeerId}: ${message(cause)}")
      false
    }

  private fun ackedCount(sending: Sending): Int = synchronized(sending.lock) { sending.window.ackedCount() }

  private fun transition(sending: Sending, from: Phase, to: Phase): Boolean = synchronized(sending.lock) {
    if (sending.phase != from) return@synchronized false
    sending.phase = to
    true
  }

  private fun groupRecipients(groupId: String): List<String> {
    val members = groupMembers(groupId)
    if (members == null) {
      Log.w(TAG, "No se puede enviar el archivo: el grupo $groupId no está disponible")
      return emptyList()
    }
    val self = selfUpeerId().lowercase()
    return members.map { it.lowercase() }.filter { it.isNotEmpty() && it != self }.distinct()
  }

  private fun saveMessage(sending: Sending, status: String, savedPath: String?) {
    val current = messages.find(sending.messageId)?.optString("status").orEmpty()
    FileOutboundMessage.save(
      messages = messages,
      messageId = sending.messageId,
      upeerId = sending.chatUpeerId,
      plan = sending.plan,
      proposal = sending.proposal,
      status = latestStatus(status, current),
      savedPath = savedPath,
    )
  }

  private fun latestStatus(candidate: String, current: String): String =
    if (rankOf(candidate) >= rankOf(current)) candidate else current

  private fun rankOf(status: String): Int = when (status) {
    "pending" -> 0
    "sent" -> 1
    "failed" -> 2
    "delivered" -> 3
    else -> -1
  }

  private fun launch(name: String, body: () -> Unit) {
    val thread = Thread(body, name)
    thread.isDaemon = true
    thread.start()
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val WINDOW_POLL_MS = 40L
    private const val STALL_TIMEOUT_MS = 60_000L
    private const val PROPOSAL_RETRY_MS = 1_000L
    private const val MAX_PROPOSAL_ATTEMPTS = 10
  }
}
