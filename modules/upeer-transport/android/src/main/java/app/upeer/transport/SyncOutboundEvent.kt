package app.upeer.transport

import org.json.JSONObject

internal object SyncOutboundEvent {
  fun event(record: SyncEventRecord): SyncPlainEvent? {
    val origin = record.originSignature ?: return null
    val holder = record.originCertificate ?: return null
    if (record.originDeviceId == null) return null
    return SyncPlainEvent(
      eventId = record.eventId,
      kind = record.kind,
      occurredAt = record.occurredAt,
      sequence = record.sequence,
      payload = record.payload,
      originDeviceId = record.originDeviceId,
      originSignature = origin,
      originCertificate = holder,
    )
  }

  fun envelope(deviceId: String, certificate: JSONObject, peerDeviceId: String, event: SyncPlainEvent): JSONObject =
    SyncPackets.envelopeMeta(
      SyncSender(deviceId, certificate),
      peerDeviceId,
      event,
      certificate.optLong("epoch", 0L).toInt(),
    )
}
