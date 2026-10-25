package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEventOriginTest {
  private val now = 1_700_000_000_000L
  private val account = "e".repeat(64)
  private val deviceId = "d".repeat(64)
  private val signature = "a".repeat(128)

  private fun certificate(deviceId: String = "d".repeat(64), issuedAt: Long = now - 1_000) = JSONObject()
    .put("deviceId", deviceId)
    .put("devicePublicKey", "b".repeat(64))
    .put("accountUpeerId", account)
    .put("epoch", 2)
    .put("issuedAt", issuedAt)

  private fun record(
    originDeviceId: String? = deviceId,
    originSignature: String? = signature,
    originCertificate: JSONObject? = certificate(),
  ) = SyncEventRecord(
    eventId = "event-0001",
    kind = SyncEventKinds.MESSAGE_UPSERT,
    occurredAt = now,
    sequence = now * 1000,
    payload = JSONObject().put("id", "message-1"),
    direction = "outbound",
    originDeviceId = originDeviceId,
    originSignature = originSignature,
    originCertificate = originCertificate,
  )

  private fun origin(
    signResult: String? = signature,
    verifies: Boolean = true,
    certificateValid: Boolean = true,
    capture: MutableList<JSONObject> = mutableListOf(),
  ) = SyncEventOrigin(
    sign = { payload ->
      capture.add(payload)
      signResult
    },
    verify = { _, _, _ -> verifies },
    verifyCertificate = { _, _, _ -> certificateValid },
  )

  @Test
  fun `signs an event with the device certificate`() {
    val signed = origin().sign(record(originSignature = null, originCertificate = null), deviceId, certificate())

    assertNotNull(signed)
    assertTrue(signed!!.signature == signature)
    assertTrue(signed.deviceId == deviceId)
  }

  @Test
  fun `signs the canonical payload with the assigned sequence`() {
    val captured = mutableListOf<JSONObject>()
    origin(capture = captured).sign(record(), deviceId, certificate())

    val payload = captured.single()
    assertTrue(payload.getString("eventId") == "event-0001")
    assertTrue(payload.getString("kind") == SyncEventKinds.MESSAGE_UPSERT)
    assertTrue(payload.getLong("sequence") == now * 1000)
    assertTrue(payload.getString("originDeviceId") == deviceId)
  }

  @Test
  fun `refuses to sign for a foreign certificate`() {
    assertNull(origin().sign(record(), deviceId, certificate(deviceId = "f".repeat(64))))
  }

  @Test
  fun `refuses a malformed signature`() {
    assertNull(origin(signResult = "zz").sign(record(), deviceId, certificate()))
  }

  @Test
  fun `verifies a well formed origin`() {
    assertTrue(origin().verify(record(), account, now))
  }

  @Test
  fun `rejects an event without an origin`() {
    assertFalse(origin().verify(record(originDeviceId = null), account, now))
    assertFalse(origin().verify(record(originSignature = null), account, now))
    assertFalse(origin().verify(record(originCertificate = null), account, now))
  }

  @Test
  fun `rejects a certificate for another device`() {
    assertFalse(origin().verify(record(originCertificate = certificate("f".repeat(64))), account, now))
  }

  @Test
  fun `rejects an unverifiable certificate`() {
    assertFalse(origin(certificateValid = false).verify(record(), account, now))
  }

  @Test
  fun `rejects a broken signature`() {
    assertFalse(origin(verifies = false).verify(record(), account, now))
  }

  @Test
  fun `refuses to overwrite an existing origin`() {
    val state = JSONObject()
    val log = SyncEventLog({ state }, Any(), {})
    log.record(
      SyncEventDraft("event-0001", SyncEventKinds.MESSAGE_UPSERT, now, JSONObject(), "outbound"),
      now,
    )

    assertTrue(log.attachOrigin("event-0001", SyncOriginSignature(deviceId, signature, certificate())))
    assertFalse(log.attachOrigin("event-0001", SyncOriginSignature(deviceId, signature, certificate())))
  }
}
