package app.upeer.transport

import org.json.JSONObject

internal object CallPackets {
  const val OFFER = "CALL_OFFER"
  const val RING = "CALL_RING"
  const val ACCEPT = "CALL_ACCEPT"
  const val REJECT = "CALL_REJECT"
  const val BUSY = "CALL_BUSY"
  const val CANCEL = "CALL_CANCEL"
  const val END = "CALL_END"
  const val MEDIA = "CALL_MEDIA"
  const val MEDIA_UPDATE = "CALL_MEDIA_UPDATE"
  const val META = "CALL_META"
  const val JOIN = "CALL_JOIN"
  const val LEAVE = "CALL_LEAVE"
  const val SDP = "CALL_SDP"
  const val ICE = "CALL_ICE"

  val TYPES = setOf(OFFER, RING, ACCEPT, REJECT, BUSY, CANCEL, END, MEDIA, MEDIA_UPDATE, META, JOIN, LEAVE, SDP, ICE)

  fun offer(callId: String, kind: String, timestamp: Long): JSONObject =
    JSONObject().put("type", OFFER).put("callId", callId).put("kind", CallKinds.of(kind)).put("timestamp", timestamp)

  fun ring(callId: String, timestamp: Long): JSONObject = simple(RING, callId, timestamp)

  fun accept(callId: String, timestamp: Long): JSONObject = simple(ACCEPT, callId, timestamp)

  fun reject(callId: String, timestamp: Long): JSONObject = simple(REJECT, callId, timestamp)

  fun busy(callId: String, timestamp: Long): JSONObject = simple(BUSY, callId, timestamp)

  fun cancel(callId: String, timestamp: Long): JSONObject = simple(CANCEL, callId, timestamp)

  fun end(callId: String, timestamp: Long): JSONObject = simple(END, callId, timestamp)

  fun mediaUpdate(callId: String, muted: Boolean, cameraEnabled: Boolean, timestamp: Long): JSONObject =
    simple(MEDIA_UPDATE, callId, timestamp).put("muted", muted).put("cameraEnabled", cameraEnabled)

  fun valid(packet: JSONObject): Boolean {
    val type = packet.optString("type")
    if (type !in TYPES) return false
    if (!CallIds.valid(packet.optString("callId"))) return false
    if (type == OFFER && packet.optString("kind") != CallKinds.AUDIO && packet.optString("kind") != CallKinds.VIDEO) return false
    return true
  }

  fun groupMembers(packet: JSONObject): List<String> {
    val raw = packet.optJSONArray("groupMembers") ?: return emptyList()
    return (0 until raw.length())
      .mapNotNull { index -> raw.optString(index).lowercase().takeIf(String::isNotEmpty) }
  }

  private fun simple(type: String, callId: String, timestamp: Long): JSONObject =
    JSONObject().put("type", type).put("callId", callId).put("timestamp", timestamp)
}
