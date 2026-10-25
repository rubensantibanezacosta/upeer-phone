package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEnvelopeTest {
  private val mine = "aa".repeat(32)
  private val sender = "bb".repeat(32)
  private val now = 1_700_000_000_000L

  private fun event(
    origin: String? = null,
    signature: String? = null,
    sequence: Long = 5L,
    kind: String = "MESSAGE_UPSERT",
  ) = SyncPlainEvent(
    eventId = "event-0001",
    kind = kind,
    occurredAt = now,
    sequence = sequence,
    payload = JSONObject().put("messageId", "m1"),
    originDeviceId = origin,
    originSignature = signature,
  )

  private fun packet(
    recipient: String = mine,
    event: SyncPlainEvent = event(),
    sequence: Long = event.sequence,
  ) = JSONObject()
    .put("type", SyncPackets.ENVELOPE)
    .put("eventId", event.eventId)
    .put("kind", event.kind)
    .put("occurredAt", event.occurredAt)
    .put("sequence", sequence)
    .put("recipientDeviceId", recipient)

  @Test
  fun `a direct event from the sender device is accepted`() {
    val decision = SyncEnvelope.decide(packet(), event(), mine, sender, originVerified = false)

    assertTrue(decision is SyncEnvelopeDecision.Accepted)
    assertEquals(sender, (decision as SyncEnvelopeDecision.Accepted).originDeviceId)
  }

  @Test
  fun `an envelope for another device is rejected`() {
    val decision = SyncEnvelope.decide(packet(recipient = "cc".repeat(32)), event(), mine, sender, false)

    assertEquals("destinatario que no es este dispositivo", (decision as SyncEnvelopeDecision.Rejected).reason)
  }

  @Test
  fun `metadata that does not match the sealed event is rejected`() {
    val decision = SyncEnvelope.decide(packet(sequence = 6L), event(sequence = 5L), mine, sender, false)

    assertEquals("metadatos que no coinciden con el sobre", (decision as SyncEnvelopeDecision.Rejected).reason)
  }

  @Test
  fun `a missing event is rejected`() {
    val decision = SyncEnvelope.decide(packet(), null, mine, sender, false)

    assertEquals("sobre sin evento válido", (decision as SyncEnvelopeDecision.Rejected).reason)
  }

  @Test
  fun `a relayed event without a valid origin signature is rejected`() {
    val relayed = event(origin = "dd".repeat(32))

    val decision = SyncEnvelope.decide(packet(event = relayed), relayed, mine, sender, originVerified = false)

    assertEquals("evento relayado sin firma de origen válida", (decision as SyncEnvelopeDecision.Rejected).reason)
  }

  @Test
  fun `a relayed event with a valid origin signature is accepted`() {
    val origin = "dd".repeat(32)
    val relayed = event(origin = origin, signature = "ee".repeat(64))

    val decision = SyncEnvelope.decide(packet(event = relayed), relayed, mine, sender, originVerified = true)

    assertTrue(decision is SyncEnvelopeDecision.Accepted)
    assertEquals(origin, (decision as SyncEnvelopeDecision.Accepted).originDeviceId)
  }

  @Test
  fun `an event claiming a signature that does not verify is rejected`() {
    val direct = event(signature = "ee".repeat(64))

    val decision = SyncEnvelope.decide(packet(event = direct), direct, mine, sender, originVerified = false)

    assertEquals("firma de origen no verificable", (decision as SyncEnvelopeDecision.Rejected).reason)
  }

  @Test
  fun `our own event reflected by another device is rejected`() {
    val reflected = event(origin = mine)

    val decision = SyncEnvelope.decide(packet(event = reflected), reflected, mine, sender, originVerified = true)

    assertEquals("evento propio reflejado por otro dispositivo", (decision as SyncEnvelopeDecision.Rejected).reason)
  }

  @Test
  fun `origin device id is normalised to lowercase`() {
    val upper = "DD".repeat(32)
    val relayed = event(origin = upper, signature = "ee".repeat(64))

    val decision = SyncEnvelope.decide(packet(event = relayed), relayed, mine, sender, originVerified = true)

    assertEquals(upper.lowercase(), (decision as SyncEnvelopeDecision.Accepted).originDeviceId)
  }

  @Test
  fun `draft keeps the origin signature and marks the direction inbound`() {
    val origin = "dd".repeat(32)
    val relayed = event(origin = origin, signature = "ee".repeat(64))

    val draft = SyncEnvelope.draft(relayed, origin)

    assertEquals("inbound", draft.direction)
    assertEquals(origin, draft.originDeviceId)
    assertEquals(relayed.originSignature, draft.originSignature)
    assertEquals(relayed.eventId, draft.eventId)
  }

  @Test
  fun `draft of a direct event has no origin signature`() {
    val draft = SyncEnvelope.draft(event(), sender)

    assertEquals(sender, draft.originDeviceId)
    assertEquals(null, draft.originSignature)
  }
}
