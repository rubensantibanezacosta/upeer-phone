package app.upeer.transport

import org.json.JSONObject

internal object GroupMessagePacket {
  fun build(
    groupId: String,
    messageID: String,
    senderPublicKey: String,
    timestamp: Long,
    sealed: JSONObject,
    epoch: Int,
    replyTo: String,
  ): JSONObject {
    require(ProtocolGroups.validId(groupId)) { "El identificador de grupo no es válido" }
    require(ReplayPolicy.validMessageID(messageID)) { "El identificador del mensaje no es válido" }
    require(senderPublicKey.length == 64) { "La clave pública del remitente no es válida" }
    require(timestamp > 0) { "La marca de tiempo del mensaje no es válida" }
    require(epoch >= 0) { "La época del grupo no es válida" }
    val nonce = sealed.optString("nonce")
    val ciphertext = sealed.optString("ciphertext")
    require(nonce.isNotEmpty() && ciphertext.isNotEmpty()) { "El contenido cifrado del grupo está vacío" }
    return JSONObject()
      .put("type", "GROUP_MSG")
      .put("id", messageID)
      .put("groupId", groupId)
      .put("senderPublicKey", senderPublicKey)
      .put("timestamp", timestamp)
      .put("content", ciphertext)
      .put("nonce", nonce)
      .put("epoch", epoch)
      .apply { if (replyTo.isNotEmpty()) put("replyTo", replyTo) }
  }

  fun recipients(members: List<String>, selfUpeerId: String): List<String> =
    members.map { it.lowercase() }.filter { it != selfUpeerId.lowercase() }.distinct()
}
