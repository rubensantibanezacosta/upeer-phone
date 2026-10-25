package app.upeer.transport

import app.upeer.transportbridge.transportbridge.Bridge
import org.json.JSONObject

internal object EnrollmentChannel {
  const val REQUEST = "ENROLL_REQ"
  const val RESPONSE = "ENROLL_RES"

  private const val PORT = 50005L

  fun requestFrame(sealed: JSONObject, inviteId: String, senderAddress: String): String = JSONObject()
    .put("type", REQUEST)
    .put("inviteId", inviteId)
    .put("senderAddress", senderAddress)
    .put("sealed", sealed)
    .toString()

  fun send(bridge: Bridge, address: String, frame: String): Boolean {
    var connectionID = 0
    return try {
      connectionID = bridge.dialTCP(address, PORT)
      bridge.writeFrame(connectionID, frame.toByteArray(Charsets.UTF_8))
      true
    } catch (cause: Exception) {
      false
    } finally {
      if (connectionID != 0) runCatching { bridge.closeConnection(connectionID) }
    }
  }

  fun sealedFrom(packet: JSONObject): String? {
    val sealed = packet.optJSONObject("sealed") ?: return null
    if (!packet.optBoolean("ok")) return null
    return sealed.toString()
  }
}
