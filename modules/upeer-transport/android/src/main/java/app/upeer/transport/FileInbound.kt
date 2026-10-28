package app.upeer.transport

import android.util.Log
import org.json.JSONObject

internal class FileInbound(
  private val accountCrypto: () -> FileReceiveCrypto?,
  private val deviceCrypto: () -> FileReceiveCrypto?,
  private val transfers: FileTransferState,
  private val messages: TransportMessages,
  private val send: (String, JSONObject, String) -> Boolean,
  private val groupMembers: (String) -> List<String>? = { null },
) {
  fun handle(packet: JSONObject, senderUpeerId: String, type: String): String {
    when (type) {
      "FILE_PROPOSAL" -> onProposal(packet, senderUpeerId)
      "FILE_CHUNK" -> onChunk(packet, senderUpeerId)
      "FILE_DONE" -> onDone(packet, senderUpeerId)
      "FILE_CANCEL" -> onCancel(packet, senderUpeerId)
      else -> Log.i(TAG, "Paquete $type de $senderUpeerId sin tratamiento")
    }
    return packet.toString()
  }

  private fun onProposal(packet: JSONObject, senderUpeerId: String) {
    val proposal = try {
      FileReceivePolicy.proposal(packet)
    } catch (cause: Exception) {
      Log.w(TAG, "FILE_PROPOSAL rechazado de $senderUpeerId: ${message(cause)}")
      return
    }
    if (!authorizedGroup(proposal, senderUpeerId)) return
    val key = unsealKey(packet, proposal.fileId)
    if (key == null) {
      Log.w(TAG, "FILE_PROPOSAL sin clave utilizable (${proposal.fileId}); no se acepta")
      return
    }
    val entry = transfers.open(proposal, senderUpeerId) ?: return
    transfers.acceptKey(proposal.fileId, key)
    saveProposalMessage(entry)
    reply(senderUpeerId, JSONObject().put("type", "FILE_ACCEPT").put("fileId", proposal.fileId), "FILE_ACCEPT")
  }

  private fun onChunk(packet: JSONObject, senderUpeerId: String) {
    val fileId = packet.optString("fileId")
    val entry = transfers.find(fileId) ?: run {
      Log.w(TAG, "FILE_CHUNK de $senderUpeerId sin propuesta para $fileId")
      return
    }
    if (entry.senderUpeerId != senderUpeerId) {
      Log.w(TAG, "FILE_CHUNK de $senderUpeerId no coincide con el emisor de $fileId")
      return
    }
    val index = packet.optInt("chunkIndex", -1)
    val key = transfers.keyFor(fileId) ?: run {
      Log.w(TAG, "FILE_CHUNK sin clave de archivo ($fileId #$index)")
      return
    }
    val data = try {
      decryptChunk(packet, key)
    } catch (cause: Exception) {
      Log.w(TAG, "FILE_CHUNK no se pudo descifrar ($fileId #$index): ${message(cause)}")
      return
    }
    val outcome = entry.assembler.accept(index, data, packet.optString("chunkHash").ifEmpty { null })
    when (outcome) {
      FileChunkAssembler.Outcome.STORED, FileChunkAssembler.Outcome.DUPLICATE ->
        reply(senderUpeerId, JSONObject().put("type", "FILE_ACK").put("fileId", fileId).put("chunkIndex", index), "FILE_ACK")
      FileChunkAssembler.Outcome.HASH_MISMATCH ->
        Log.w(TAG, "FILE_CHUNK descartado por hash ($fileId #$index)")
      FileChunkAssembler.Outcome.OUT_OF_RANGE ->
        Log.w(TAG, "FILE_CHUNK fuera de rango ($fileId #$index)")
    }
  }

  private fun onDone(packet: JSONObject, senderUpeerId: String) {
    val fileId = packet.optString("fileId")
    reply(senderUpeerId, JSONObject().put("type", "FILE_DONE_ACK").put("fileId", fileId), "FILE_DONE_ACK")
    val entry = transfers.find(fileId) ?: return
    if (!entry.assembler.isComplete()) {
      Log.w(TAG, "FILE_DONE con trozos pendientes ($fileId)")
      return
    }
    val saved = try {
      entry.assembler.finish()
    } catch (cause: Exception) {
      Log.w(TAG, "El archivo $fileId no se pudo cerrar: ${message(cause)}")
      transfers.cancel(fileId)
      return
    }
    transfers.complete(fileId)
    saveCompletedMessage(entry, saved.absolutePath)
    Log.i(TAG, "Adjunto $fileId guardado en ${saved.absolutePath}")
  }

  private fun onCancel(packet: JSONObject, senderUpeerId: String) {
    val fileId = packet.optString("fileId")
    Log.i(TAG, "Transferencia $fileId cancelada por $senderUpeerId")
    transfers.cancel(fileId)
  }

  private fun unsealKey(packet: JSONObject, fileId: String): ByteArray? {
    val deviceSealed = packet.optString("deviceEncryptedKey")
    if (deviceSealed.isNotEmpty()) {
      val opened = open(deviceCrypto(), deviceSealed, "el dispositivo")
      if (opened != null) return opened.takeIf { it.size == FileTransferState.FILE_KEY_SIZE }
    }
    val accountSealed = packet.optString("encryptedKey")
    if (accountSealed.isEmpty()) {
      Log.w(TAG, "FILE_PROPOSAL sin clave de archivo ($fileId)")
      return null
    }
    val opened = open(accountCrypto(), accountSealed, "la cuenta")
    if (opened == null) {
      Log.w(TAG, "La clave de archivo de $fileId no se puede abrir en este dispositivo")
      return null
    }
    return opened.takeIf { it.size == FileTransferState.FILE_KEY_SIZE }
  }

  private fun open(crypto: FileReceiveCrypto?, sealed: String, label: String): ByteArray? {
    if (crypto == null) return null
    return try {
      crypto.openFileKey(sealed)
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo abrir la clave de archivo con $label: ${message(cause)}")
      null
    }
  }

  private fun decryptChunk(packet: JSONObject, key: ByteArray): ByteArray {
    val data = packet.optString("data")
    require(data.isNotEmpty()) { "El trozo está vacío" }
    val iv = packet.optString("iv")
    val tag = packet.optString("tag")
    require(iv.isNotEmpty() && tag.isNotEmpty()) { "El trozo no trae iv o tag" }
    val crypto = deviceCrypto() ?: accountCrypto()
    requireNotNull(crypto) { "No hay identidad para descifrar el trozo" }
    return crypto.decryptFileChunk(key, iv, tag, data)
  }

  private fun authorizedGroup(proposal: FileReceivePolicy.Proposal, senderUpeerId: String): Boolean {
    val chatUpeerId = proposal.chatUpeerId ?: return true
    val members = groupMembers(chatUpeerId)
    if (members == null || senderUpeerId !in members) {
      Log.w(TAG, "FILE_PROPOSAL para el grupo $chatUpeerId rechazada: $senderUpeerId no es miembro")
      return false
    }
    return true
  }

  private fun chatOf(entry: FileTransferState.Entry): String = entry.proposal.chatUpeerId ?: entry.senderUpeerId

  private fun saveProposalMessage(entry: FileTransferState.Entry) {
    val messageId = entry.proposal.messageId ?: entry.proposal.fileId
    messages.save(
      JSONObject()
        .put("id", messageId)
        .put("upeerId", chatOf(entry))
        .put("isMine", false)
        .put("content", envelope(entry, null))
        .put("status", "delivered")
        .put("version", 1)
        .put("isEdited", false)
        .put("timestamp", System.currentTimeMillis())
        .put("receivedAt", System.currentTimeMillis()),
    )
  }

  private fun saveCompletedMessage(entry: FileTransferState.Entry, savedPath: String) {
    val messageId = entry.proposal.messageId ?: entry.proposal.fileId
    val existing = messages.find(messageId) ?: return
    existing.put("content", envelope(entry, savedPath))
    messages.save(existing)
  }

  private fun envelope(entry: FileTransferState.Entry, savedPath: String?): String {
    val proposal = entry.proposal
    val payload = JSONObject()
      .put("type", "file")
      .put("transferId", proposal.fileId)
      .put("fileId", proposal.fileId)
      .put("fileName", proposal.fileName)
      .put("fileSize", proposal.fileSize)
      .put("mimeType", proposal.mimeType)
    proposal.caption?.let { payload.put("caption", it) }
    if (proposal.isVoiceNote) payload.put("isVoiceNote", true)
    savedPath?.let { payload.put("savedPath", it) }
    return payload.toString()
  }

  private fun reply(senderUpeerId: String, data: JSONObject, label: String) {
    if (!send(senderUpeerId, data, label)) {
      Log.w(TAG, "No se pudo enviar $label a $senderUpeerId")
    }
  }

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
