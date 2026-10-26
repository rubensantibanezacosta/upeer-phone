package app.upeer.transport

import org.json.JSONObject

internal interface TransportMessages {
  fun find(messageId: String): JSONObject?
  fun save(message: JSONObject)
}

internal class StateTransportMessages(private val state: ProtocolStateStore) : TransportMessages {
  override fun find(messageId: String): JSONObject? = state.messages.find(messageId)

  override fun save(message: JSONObject) = state.messages.save(message)
}
