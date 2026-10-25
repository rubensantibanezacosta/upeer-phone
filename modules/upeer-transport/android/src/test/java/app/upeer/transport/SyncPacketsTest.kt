package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPacketsTest {
  private val now = 1_700_000_000_000L
  private val deviceId = "a".repeat(64)
  private val peerDeviceId = "b".repeat(64)
  private val signature = "c".repeat(128)

  private fun certificate(device: String = deviceId) = JSONObject()
    .put("deviceId", device)
    .put("devicePublicKey", "d".repeat(64))
    .put("accountUpeerId", "e".repeat(64))
    .put("epoch", 3)

  private fun sender() = SyncSender(deviceId, certificate())

  private fun signedPreKey() = JSONObject()
    .put("spkPub", "f".repeat(64))
    .put("spkSig", "1".repeat(128))
    .put("spkId", 7L)

  private fun event(
    eventId: String = "event-0001",
    kind: String = SyncEventKinds.MESSAGE_UPSERT,
    sequence: Long = 1_700_000_000_000_001L,
    originDeviceId: String? = null,
  ) = SyncPlainEvent(
    eventId = eventId,
    kind = kind,
    occurredAt = now,
    sequence = sequence,
    payload = JSONObject().put("messageId", "message-1"),
    originDeviceId = originDeviceId,
  )

  @Test
  fun `recognises only sync types`() {
    assertTrue(SyncPackets.isSyncType(SyncPackets.HELLO))
    assertTrue(SyncPackets.isSyncType(SyncPackets.ENVELOPE))
    assertTrue(SyncPackets.isSyncType(SyncPackets.HISTORY_REQ))
    assertTrue(SyncPackets.isSyncType(SyncPackets.HISTORY_RES))
    assertTrue(SyncPackets.isSyncType(SyncPackets.ACK))
    assertFalse(SyncPackets.isSyncType("CHAT"))
    assertFalse(SyncPackets.isSyncType("VAULT_STORE"))
  }

  @Test
  fun `ratchet context is namespaced by device`() {
    assertEquals("sync:$peerDeviceId", SyncPackets.contextId(peerDeviceId))
  }

  @Test
  fun `hello carries device binding without a recipient`() {
    val hello = SyncPackets.hello(sender(), signedPreKey(), ack = false, nowMillis = now)
    assertEquals(SyncPackets.HELLO, hello.getString("type"))
    assertEquals(deviceId, hello.getString("deviceId"))
    assertEquals(now, hello.getLong("timestamp"))
    assertFalse(hello.getBoolean("ack"))
    assertEquals(deviceId, hello.getString("senderDeviceId"))
    assertEquals(7L, hello.getJSONObject("deviceSignedPreKey").getLong("spkId"))
    assertFalse(hello.has("recipientDeviceId"))
  }

  @Test
  fun `hello binding is readable by the hello parser`() {
    val hello = SyncPackets.hello(sender(), signedPreKey(), ack = true, nowMillis = now)
    val binding = SyncHello.binding(hello)
    assertNotNull(binding)
    assertEquals(deviceId, binding?.deviceId)
    assertEquals("d".repeat(64), binding?.devicePublicKey)
  }

  @Test
  fun `ack rejects non positive sequences`() {
    assertNull(SyncPackets.ack(sender(), "event-0001", 0L))
    assertNull(SyncPackets.ack(sender(), "event-0001", -5L))
    assertNotNull(SyncPackets.ack(sender(), "event-0001", 9L))
  }

  @Test
  fun `ack carries sender but no recipient`() {
    val ack = SyncPackets.ack(sender(), "event-0001", 42L)
    assertEquals(SyncPackets.ACK, ack?.getString("type"))
    assertEquals("event-0001", ack?.getString("eventId"))
    assertEquals(42L, ack?.getLong("sequence"))
    assertFalse(ack?.has("recipientDeviceId") ?: true)
  }

  @Test
  fun `envelope metadata mirrors the plain event`() {
    val meta = SyncPackets.envelopeMeta(sender(), peerDeviceId, event(), epoch = 3)
    assertEquals(SyncPackets.ENVELOPE, meta.getString("type"))
    assertEquals("event-0001", meta.getString("eventId"))
    assertEquals(SyncEventKinds.MESSAGE_UPSERT, meta.getString("kind"))
    assertEquals(now, meta.getLong("occurredAt"))
    assertEquals(3, meta.getInt("epoch"))
    assertEquals(peerDeviceId, meta.getString("recipientDeviceId"))
    assertEquals(deviceId, meta.getString("senderDeviceId"))
    assertFalse(meta.has("originDeviceId"))
  }

  @Test
  fun `envelope metadata carries the origin device when relayed`() {
    val meta = SyncPackets.envelopeMeta(sender(), peerDeviceId, event(originDeviceId = peerDeviceId), epoch = 3)
    assertEquals(peerDeviceId, meta.getString("originDeviceId"))
  }

  @Test
  fun `history metadata requires a valid request id`() {
    assertNull(SyncPackets.historyMeta(sender(), peerDeviceId, SyncPackets.HISTORY_REQ, "short"))
    assertNull(SyncPackets.historyMeta(sender(), peerDeviceId, SyncPackets.ACK, "request-0001"))
    val meta = SyncPackets.historyMeta(sender(), peerDeviceId, SyncPackets.HISTORY_REQ, "request-0001")
    assertEquals(SyncPackets.HISTORY_REQ, meta?.getString("type"))
    assertEquals("request-0001", meta?.getString("requestId"))
  }

  @Test
  fun `recipient matching ignores case and rejects blanks`() {
    val meta = SyncPackets.envelopeMeta(sender(), peerDeviceId, event(), epoch = 3)
    assertTrue(SyncPackets.recipientMatches(meta, peerDeviceId.uppercase()))
    assertFalse(SyncPackets.recipientMatches(meta, deviceId))
    assertFalse(SyncPackets.recipientMatches(JSONObject(), peerDeviceId))
  }

  @Test
  fun `sender device id must be a full hex digest`() {
    assertEquals(deviceId, SyncPackets.senderDeviceId(JSONObject().put("senderDeviceId", deviceId.uppercase())))
    assertNull(SyncPackets.senderDeviceId(JSONObject().put("senderDeviceId", "abc")))
    assertNull(SyncPackets.senderDeviceId(JSONObject()))
  }

  @Test
  fun `ack fields parse valid packets`() {
    val fields = SyncPackets.ackFields(JSONObject().put("eventId", "event-0001").put("sequence", 12L))
    assertEquals("event-0001", fields?.eventId)
    assertEquals(12L, fields?.sequence)
    assertNull(SyncPackets.ackFields(JSONObject().put("eventId", "event-0001")))
    assertNull(SyncPackets.ackFields(JSONObject().put("eventId", "bad id").put("sequence", 1L)))
  }

  @Test
  fun `plain event accepts a complete record`() {
    val parsed = SyncPackets.plainEvent(
      JSONObject()
        .put("eventId", "event-0001")
        .put("kind", SyncEventKinds.REACTION_SET)
        .put("occurredAt", now)
        .put("sequence", 5L)
        .put("payload", JSONObject().put("emoji", "👍")),
    )
    assertEquals("event-0001", parsed?.eventId)
    assertEquals(SyncEventKinds.REACTION_SET, parsed?.kind)
    assertEquals("👍", parsed?.payload?.getString("emoji"))
    assertNull(parsed?.originDeviceId)
  }

  @Test
  fun `plain event rejects unknown kinds and broken timestamps`() {
    val base = JSONObject()
      .put("eventId", "event-0001")
      .put("occurredAt", now)
      .put("sequence", 5L)
      .put("payload", JSONObject())
    assertNull(SyncPackets.plainEvent(JSONObject(base.toString()).put("kind", "MESSAGE_UNKNOWN")))
    assertNull(SyncPackets.plainEvent(JSONObject(base.toString()).put("kind", SyncEventKinds.MESSAGE_EDIT).put("occurredAt", 0)))
    assertNull(SyncPackets.plainEvent(JSONObject(base.toString()).put("kind", SyncEventKinds.MESSAGE_EDIT).put("sequence", 0)))
    assertNull(SyncPackets.plainEvent(JSONObject(base.toString()).put("kind", SyncEventKinds.MESSAGE_EDIT).put("eventId", "x")))
  }

  @Test
  fun `plain event validates the origin signature shape`() {
    val base = JSONObject()
      .put("eventId", "event-0001")
      .put("kind", SyncEventKinds.MESSAGE_UPSERT)
      .put("occurredAt", now)
      .put("sequence", 5L)
      .put("payload", JSONObject())
      .put("originDeviceId", peerDeviceId)
      .put("originSignature", signature)
      .put("originCertificate", certificate(peerDeviceId))
    val parsed = SyncPackets.plainEvent(base)
    assertEquals(peerDeviceId, parsed?.originDeviceId)
    assertEquals(signature, parsed?.originSignature)
    assertEquals(peerDeviceId, parsed?.originCertificate?.getString("deviceId"))
  }

  @Test
  fun `plain event rejects mismatched or malformed origins`() {
    val base = JSONObject()
      .put("eventId", "event-0001")
      .put("kind", SyncEventKinds.MESSAGE_UPSERT)
      .put("occurredAt", now)
      .put("sequence", 5L)
      .put("payload", JSONObject())
    assertNull(SyncPackets.plainEvent(JSONObject(base.toString()).put("originDeviceId", "zz")))
    assertNull(SyncPackets.plainEvent(JSONObject(base.toString()).put("originSignature", "nothex")))
    assertNull(
      SyncPackets.plainEvent(
        JSONObject(base.toString())
          .put("originDeviceId", peerDeviceId)
          .put("originCertificate", certificate(deviceId)),
      ),
    )
    assertNull(SyncPackets.plainEvent(JSONObject(base.toString()).put("originCertificate", "plain-string")))
  }

  @Test
  fun `envelope must match the decrypted event`() {
    val plain = event()
    val meta = SyncPackets.envelopeMeta(sender(), peerDeviceId, plain, epoch = 3)
    assertTrue(SyncPackets.matchesEnvelope(meta, plain))
    assertFalse(SyncPackets.matchesEnvelope(meta, plain.copy(sequence = 99L)))
    assertFalse(SyncPackets.matchesEnvelope(meta, plain.copy(kind = SyncEventKinds.MESSAGE_EDIT)))
    assertFalse(SyncPackets.matchesEnvelope(meta, plain.copy(eventId = "event-0002")))
    assertFalse(SyncPackets.matchesEnvelope(meta, plain.copy(occurredAt = now + 1)))
  }

  @Test
  fun `envelope tolerates a missing origin on the outer packet only`() {
    val relayed = event(originDeviceId = peerDeviceId)
    val declared = SyncPackets.envelopeMeta(sender(), peerDeviceId, relayed, epoch = 3)
    assertTrue(SyncPackets.matchesEnvelope(declared, relayed))
    val rewritten = SyncPackets.envelopeMeta(sender(), peerDeviceId, event(), epoch = 3)
    assertTrue(SyncPackets.matchesEnvelope(rewritten, relayed))
    val conflicting = SyncPackets.envelopeMeta(sender(), peerDeviceId, event(originDeviceId = deviceId), epoch = 3)
    assertFalse(SyncPackets.matchesEnvelope(conflicting, relayed))
  }
}
