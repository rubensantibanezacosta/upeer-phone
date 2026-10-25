package app.upeer.transport

import org.json.JSONObject

internal data class SyncSender(val deviceId: String, val certificate: JSONObject)

internal data class SyncPlainEvent(
  val eventId: String,
  val kind: String,
  val occurredAt: Long,
  val sequence: Long,
  val payload: JSONObject,
  val originDeviceId: String? = null,
  val originSignature: String? = null,
  val originCertificate: JSONObject? = null,
)

internal data class SyncAckFields(val eventId: String, val sequence: Long)

internal object SyncPackets {
  const val HELLO = "SYNC_HELLO"
  const val ENVELOPE = "SYNC_ENVELOPE"
  const val HISTORY_REQ = "SYNC_HISTORY_REQ"
  const val HISTORY_RES = "SYNC_HISTORY_RES"
  const val ACK = "SYNC_ACK"
  const val RESET = "SYNC_RESET"
  const val REGISTRY = "SYNC_REGISTRY"

  private val types = setOf(HELLO, ENVELOPE, HISTORY_REQ, HISTORY_RES, ACK, RESET, REGISTRY)

  fun isSyncType(type: String): Boolean = type in types

  fun contextId(deviceId: String): String = "sync:$deviceId"

  fun reset(sender: SyncSender, nowMillis: Long): JSONObject =
    decorate(
      JSONObject()
        .put("type", RESET)
        .put("deviceId", sender.deviceId)
        .put("deviceCertificate", sender.certificate)
        .put("timestamp", nowMillis),
      sender,
      null,
    )

  fun hello(sender: SyncSender, signedPreKey: JSONObject, ack: Boolean, nowMillis: Long): JSONObject =
    decorate(
      JSONObject()
        .put("type", HELLO)
        .put("deviceId", sender.deviceId)
        .put("deviceCertificate", sender.certificate)
        .put("deviceSignedPreKey", signedPreKey)
        .put("timestamp", nowMillis)
        .put("ack", ack),
      sender,
      null,
    )

  fun ack(sender: SyncSender, eventId: String, sequence: Long): JSONObject? {
    if (sequence <= 0) return null
    if (eventId.isNotBlank() && !validEventId(eventId)) return null
    return decorate(
      JSONObject().put("type", ACK).put("eventId", eventId).put("sequence", sequence),
      sender,
      null,
    )
  }

  fun envelopeMeta(sender: SyncSender, recipientDeviceId: String, event: SyncPlainEvent, epoch: Int): JSONObject {
    val meta = JSONObject()
      .put("type", ENVELOPE)
      .put("eventId", event.eventId)
      .put("kind", event.kind)
      .put("occurredAt", event.occurredAt)
      .put("sequence", event.sequence)
      .put("epoch", epoch)
    if (event.originDeviceId != null) meta.put("originDeviceId", event.originDeviceId)
    return decorate(meta, sender, recipientDeviceId)
  }

  fun historyMeta(sender: SyncSender, recipientDeviceId: String, type: String, requestId: String): JSONObject? {
    if (type != HISTORY_REQ && type != HISTORY_RES) return null
    if (!validRequestId(requestId)) return null
    return decorate(
      JSONObject().put("type", type).put("requestId", requestId),
      sender,
      recipientDeviceId,
    )
  }

  fun decorate(meta: JSONObject, sender: SyncSender, recipientDeviceId: String?): JSONObject {
    meta.put("senderDeviceId", sender.deviceId)
    meta.put("senderDeviceCertificate", sender.certificate)
    if (recipientDeviceId != null) meta.put("recipientDeviceId", recipientDeviceId)
    return meta
  }

  fun recipientMatches(packet: JSONObject, myDeviceId: String): Boolean {
    val recipient = packet.optString("recipientDeviceId")
    if (recipient.isBlank()) return false
    return recipient.equals(myDeviceId, ignoreCase = true)
  }

  fun senderDeviceId(packet: JSONObject): String? {
    val deviceId = packet.optString("senderDeviceId").lowercase()
    return if (hex(deviceId, 64)) deviceId else null
  }

  fun ackFields(packet: JSONObject): SyncAckFields? {
    val sequence = packet.optLong("sequence", 0L)
    if (sequence <= 0) return null
    val eventId = packet.optString("eventId")
    if (eventId.isNotBlank() && !validEventId(eventId)) return null
    return SyncAckFields(eventId, sequence)
  }

  fun matchesEnvelope(packet: JSONObject, event: SyncPlainEvent): Boolean {
    if (packet.optString("eventId") != event.eventId) return false
    if (packet.optString("kind") != event.kind) return false
    if (packet.optLong("occurredAt", 0L) != event.occurredAt) return false
    if (packet.optLong("sequence", 0L) != event.sequence) return false
    val outerOrigin = packet.optString("originDeviceId").lowercase().ifBlank { null }
    val innerOrigin = event.originDeviceId
    if (outerOrigin != null && innerOrigin != null && outerOrigin != innerOrigin) return false
    return true
  }

  fun plainEvent(json: JSONObject): SyncPlainEvent? {
    val eventId = json.optString("eventId")
    if (!validEventId(eventId)) return null
    val kind = json.optString("kind")
    if (!SyncEventKinds.isValid(kind)) return null
    val occurredAt = json.optLong("occurredAt", 0L)
    if (occurredAt <= 0) return null
    val sequence = json.optLong("sequence", 0L)
    if (sequence <= 0) return null
    val payload = json.optJSONObject("payload") ?: return null
    val originDeviceId = json.opt("originDeviceId")?.let { value ->
      if (value !is String) return null
      val deviceId = value.lowercase()
      if (!hex(deviceId, 64)) return null
      deviceId
    }
    val originSignature = json.opt("originSignature")?.let { value ->
      if (value !is String) return null
      val signature = value.lowercase()
      if (!hex(signature, 128)) return null
      signature
    }
    val originCertificate = json.opt("originCertificate")?.let { value ->
      if (value !is JSONObject) return null
      val certificateDeviceId = value.optString("deviceId").lowercase()
      if (!hex(certificateDeviceId, 64)) return null
      if (originDeviceId != null && certificateDeviceId != originDeviceId) return null
      value
    }
    return SyncPlainEvent(
      eventId = eventId,
      kind = kind,
      occurredAt = occurredAt,
      sequence = sequence,
      payload = payload,
      originDeviceId = originDeviceId,
      originSignature = originSignature,
      originCertificate = originCertificate,
    )
  }

  fun validEventId(value: String): Boolean =
    value.length in 8..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == ':' || it == '.' }

  fun validRequestId(value: String): Boolean =
    value.length in 8..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

  private fun hex(value: String, length: Int): Boolean =
    value.length == length && value.all { it in '0'..'9' || it in 'a'..'f' }
}
