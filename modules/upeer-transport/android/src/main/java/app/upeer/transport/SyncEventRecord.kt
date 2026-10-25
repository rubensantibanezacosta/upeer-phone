package app.upeer.transport

import org.json.JSONObject

internal data class SyncEventDraft(
  val eventId: String,
  val kind: String,
  val occurredAt: Long,
  val payload: JSONObject,
  val direction: String,
  val originDeviceId: String? = null,
  val originSignature: String? = null,
  val originCertificate: JSONObject? = null,
)

internal data class SyncEventRecord(
  val eventId: String,
  val kind: String,
  val occurredAt: Long,
  val sequence: Long,
  val payload: JSONObject,
  val direction: String,
  val originDeviceId: String?,
  val originSignature: String?,
  val originCertificate: JSONObject?,
) {
  fun toJson(): JSONObject {
    val json = JSONObject()
      .put("eventId", eventId)
      .put("kind", kind)
      .put("occurredAt", occurredAt)
      .put("sequence", sequence)
      .put("payload", payload.toString())
      .put("direction", direction)
    if (originDeviceId != null) json.put("originDeviceId", originDeviceId)
    if (originSignature != null) json.put("originSignature", originSignature)
    if (originCertificate != null) json.put("originCertificate", originCertificate.toString())
    return json
  }

  companion object {
    fun from(json: JSONObject): SyncEventRecord? {
      val eventId = json.optString("eventId")
      val kind = json.optString("kind")
      if (eventId.isBlank() || !SyncEventKinds.isValid(kind)) return null
      val payload = SyncEventKinds.payloadObject(kind, json.optString("payload")) ?: return null
      return SyncEventRecord(
        eventId = eventId,
        kind = kind,
        occurredAt = json.optLong("occurredAt", 0L),
        sequence = json.optLong("sequence", 0L),
        payload = payload,
        direction = json.optString("direction"),
        originDeviceId = json.optString("originDeviceId").ifBlank { null },
        originSignature = json.optString("originSignature").ifBlank { null },
        originCertificate = optionalObject(json.optString("originCertificate")),
      )
    }

    private fun optionalObject(raw: String): JSONObject? {
      if (raw.isBlank()) return null
      return try {
        JSONObject(raw)
      } catch (_: Exception) {
        null
      }
    }
  }
}
