package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncOutboundEventTest {
  private val certificate = JSONObject()
    .put("deviceId", DEVICE)
    .put("accountUpeerId", ACCOUNT)
    .put("devicePublicKey", KEY)
    .put("issuedAt", 1_000L)
    .put("expiresAt", 9_000_000L)
    .put("epoch", 3L)

  private fun record(origin: String? = DEVICE): SyncEventRecord = SyncEventRecord(
    eventId = "3f2a9c10-7d51-4a2e-9a44-1f0d5c7b8e21",
    kind = SyncEventKinds.SETTINGS_UPDATE,
    occurredAt = 1_700_000_000_000L,
    sequence = 1_700_000_000_001L,
    payload = JSONObject().put("block", "privacy").put("shareActivity", false),
    direction = "outbound",
    originDeviceId = origin,
    originSignature = if (origin == null) null else SIGNATURE,
    originCertificate = if (origin == null) null else certificate,
  )

  @Test
  fun buildsTheEventThatThePeerParses() {
    val event = SyncOutboundEvent.event(record())
    assertNotNull(event)
    val parsed = SyncPackets.plainEvent(SyncPackets.plainEventJson(event!!))
    assertEquals(event, parsed)
  }

  @Test
  fun theEnvelopeCarriesEveryFieldThePeerChecks() {
    val event = SyncOutboundEvent.event(record())!!
    val envelope = SyncOutboundEvent.envelope(DEVICE, certificate, PEER, event)
    assertEquals(SyncPackets.ENVELOPE, envelope.optString("type"))
    assertEquals(event.eventId, envelope.optString("eventId"))
    assertEquals(event.kind, envelope.optString("kind"))
    assertEquals(event.occurredAt, envelope.optLong("occurredAt"))
    assertEquals(event.sequence, envelope.optLong("sequence"))
    assertEquals(DEVICE, envelope.optString("senderDeviceId"))
    assertEquals(PEER, envelope.optString("recipientDeviceId"))
    assertEquals(DEVICE, envelope.optString("originDeviceId"))
    assertEquals(3, envelope.optInt("epoch"))
    assertTrue(SyncPackets.matchesEnvelope(envelope, event))
  }

  @Test
  fun refusesARecordWithoutOrigin() {
    assertNull(SyncOutboundEvent.event(record(origin = null)))
  }

  @Test
  fun refusesARecordWithoutDevice() {
    assertNull(SyncOutboundEvent.event(record().copy(originDeviceId = null)))
  }

  @Test
  fun theEnvelopeTargetsAnotherRecipient() {
    val event = SyncOutboundEvent.event(record())!!
    val envelope = SyncOutboundEvent.envelope(DEVICE, certificate, PEER, event)
    assertTrue(SyncPackets.recipientMatches(envelope, PEER))
    assertTrue(!SyncPackets.recipientMatches(envelope, DEVICE))
  }

  private companion object {
    const val DEVICE = "0d3180fc5bfa0000000000000000000000000000000000000000000000000000"
    const val PEER = "1a2b3c4d5e6f0000000000000000000000000000000000000000000000000000"
    const val ACCOUNT = "aabbccdd00000000000000000000000000000000000000000000000000000000"
    val KEY = "11".repeat(32)
    val SIGNATURE = "22".repeat(64)
  }
}
