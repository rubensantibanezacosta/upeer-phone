package app.upeer.transport

import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject

internal sealed class PacketSignature {
  data class Linked(val deviceId: String) : PacketSignature()

  object Account : PacketSignature()

  companion object {
    fun of(packet: JSONObject): PacketSignature {
      val raw = packet.optString("senderDeviceId")
      if (raw.isEmpty()) return Account
      val deviceId = raw.lowercase()
      if (!DhtLocation.isHex(deviceId, 32)) {
        throw IllegalArgumentException("senderDeviceId must be a 32-byte hexadecimal value")
      }
      return Linked(deviceId)
    }
  }
}
