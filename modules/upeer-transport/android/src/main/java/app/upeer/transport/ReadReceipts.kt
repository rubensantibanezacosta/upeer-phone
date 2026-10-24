package app.upeer.transport

import app.upeer.transportbridge.transportbridge.CryptoIdentity
import org.json.JSONObject

internal object ReadReceipts {
  const val MAX_PER_CONVERSATION = 50

  fun data(messageID: String, nowMillis: Long): JSONObject = JSONObject()
    .put("type", "READ")
    .put("id", messageID)
    .put("timestamp", nowMillis)

  fun messageID(packet: JSONObject): String? {
    val messageID = packet.optString("id")
    return if (ReplayPolicy.validMessageID(messageID)) messageID else null
  }

  fun frame(identity: CryptoIdentity, selfAddress: String, peerPublicKey: String, messageID: String, nowMillis: Long): String =
    SealedControl.frame(identity, selfAddress, peerPublicKey, data(messageID, nowMillis))
}
