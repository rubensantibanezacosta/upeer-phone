package app.upeer.transport

import org.json.JSONObject

internal interface FileSendDirectory {
  fun publicKeyFor(upeerId: String): String?
  fun devicePublicKeyFor(upeerId: String): String?
}

internal interface FileSendMessages {
  fun find(messageId: String): JSONObject?
  fun save(message: JSONObject)
}

internal class StateFileSendDirectory(private val state: ProtocolStateStore) : FileSendDirectory {
  override fun publicKeyFor(upeerId: String): String? = state.publicKeyFor(upeerId)

  override fun devicePublicKeyFor(upeerId: String): String? =
    state.peerFor(upeerId)?.optString("devicePublicKey").orEmpty().takeIf { it.isNotEmpty() }
}

internal class StateFileSendMessages(private val state: ProtocolStateStore) : FileSendMessages {
  override fun find(messageId: String): JSONObject? = state.messages.find(messageId)

  override fun save(message: JSONObject) = state.messages.save(message)
}
