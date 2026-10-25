package app.upeer.transport

internal sealed class SyncEnvelopeDecision {
  data class Accepted(val event: SyncPlainEvent, val originDeviceId: String) : SyncEnvelopeDecision()
  data class Rejected(val reason: String) : SyncEnvelopeDecision()
}

internal object SyncEnvelope {
  fun decide(
    packet: org.json.JSONObject,
    decrypted: SyncPlainEvent?,
    myDeviceId: String,
    senderDeviceId: String,
    originVerified: Boolean,
  ): SyncEnvelopeDecision {
    if (!SyncPackets.recipientMatches(packet, myDeviceId)) return reject("destinatario que no es este dispositivo")
    val event = decrypted ?: return reject("sobre sin evento válido")
    if (!SyncPackets.matchesEnvelope(packet, event)) return reject("metadatos que no coinciden con el sobre")
    val origin = event.originDeviceId ?: senderDeviceId
    if (origin.equals(myDeviceId, ignoreCase = true) && !senderDeviceId.equals(myDeviceId, ignoreCase = true)) {
      return reject("evento propio reflejado por otro dispositivo")
    }
    if (!origin.equals(senderDeviceId, ignoreCase = true) && !originVerified) {
      return reject("evento relayado sin firma de origen válida")
    }
    if (event.originSignature != null && !originVerified) return reject("firma de origen no verificable")
    return SyncEnvelopeDecision.Accepted(event, origin.lowercase())
  }

  fun draft(event: SyncPlainEvent, originDeviceId: String): SyncEventDraft = SyncEventDraft(
    eventId = event.eventId,
    kind = event.kind,
    occurredAt = event.occurredAt,
    payload = event.payload,
    direction = "inbound",
    originDeviceId = originDeviceId,
    originSignature = event.originSignature,
    originCertificate = event.originCertificate,
  )

  private fun reject(reason: String) = SyncEnvelopeDecision.Rejected(reason)
}
