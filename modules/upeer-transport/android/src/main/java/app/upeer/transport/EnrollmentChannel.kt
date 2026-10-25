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

  fun requestSealed(packet: JSONObject): String? = packet.optJSONObject("sealed")?.toString()

  fun responseFrame(inviteId: String, sealed: String?, error: String = "invite"): String = JSONObject()
    .put("type", RESPONSE)
    .put("inviteId", inviteId)
    .put("ok", sealed != null)
    .apply {
      if (sealed == null) put("error", error) else put("sealed", JSONObject(sealed))
    }
    .toString()

  fun dialableAddress(value: String): String? {
    val candidate = value.trim().lowercase()
    if (candidate.length < 3 || candidate.length > 45) return null
    if (!candidate.contains(':')) return null
    return if (ADDRESS_PATTERN.matches(candidate)) candidate else null
  }

  private val ADDRESS_PATTERN = Regex("^[0-9a-f:.]+$")
}
