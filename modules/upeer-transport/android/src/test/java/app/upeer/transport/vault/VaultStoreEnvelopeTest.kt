package app.upeer.transport.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultStoreEnvelopeTest {
  private val sender = "aa".repeat(32)
  private val recipient = "bb".repeat(32)
  private val device = "cc".repeat(32)
  private val packet = """{"type":"CHAT","id":"m1","content":"hola","nonce":"n1"}"""
  private val now = 1_700_000_000_000L

  private fun build(
    packetJSON: String = packet,
    recipientUpeerId: String = recipient,
    senderUpeerId: String = sender,
    recipientDeviceId: String? = null,
    ttlMillis: Long = VaultStoreEnvelope.TTL_MILLIS,
  ) = VaultStoreEnvelope.build(packetJSON, recipientUpeerId, senderUpeerId, recipientDeviceId, now, ttlMillis)

  private fun json(envelope: VaultStoreEnvelope.Envelope) = JSONObject(envelope.json)

  @Test
  fun `declares vault store type and normalized identities`() {
    val body = json(build(recipientUpeerId = recipient.uppercase(), senderUpeerId = sender.uppercase()))

    assertEquals("VAULT_STORE", body.getString("type"))
    assertEquals(recipient, body.getString("recipientSid"))
    assertEquals(sender, body.getString("senderSid"))
  }

  @Test
  fun `keeps packet bytes recoverable from data`() {
    val body = json(build())
    val decoded = body.getString("data").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    assertEquals(packet, String(decoded, Charsets.UTF_8))
  }

  @Test
  fun `payload hash is the sha256 of the packet bytes`() {
    val envelope = build()
    val expected = java.security.MessageDigest.getInstance("SHA-256")
      .digest(packet.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }

    assertEquals(expected, envelope.payloadHash)
    assertEquals(expected, json(envelope).getString("payloadHash"))
  }

  @Test
  fun `uses chat priority for chat and control priority for deletes`() {
    assertEquals(1, json(build()).getInt("priority"))
    assertEquals(3, json(build(packetJSON = """{"type":"CHAT_DELETE","id":"m1"}""")).getInt("priority"))
    assertEquals(3, json(build(packetJSON = """{"type":"CHAT_CLEAR_ALL"}""")).getInt("priority"))
    assertEquals(2, json(build(packetJSON = """{"type":"PROFILE"}""")).getInt("priority"))
  }

  @Test
  fun `expiry is now plus ttl`() {
    assertEquals(now + VaultStoreEnvelope.TTL_MILLIS, json(build()).getLong("expiresAt"))
    assertEquals(now + 1_000L, json(build(ttlMillis = 1_000L)).getLong("expiresAt"))
  }

  @Test
  fun `ttl longer than the protocol maximum is clamped`() {
    val body = json(build(ttlMillis = VaultStoreEnvelope.TTL_MILLIS * 10))

    assertEquals(now + VaultStoreEnvelope.TTL_MILLIS, body.getLong("expiresAt"))
  }

  @Test
  fun `omits recipient device when absent and includes it when present`() {
    assertTrue(!json(build()).has("recipientDeviceId"))
    assertEquals(device, json(build(recipientDeviceId = device.uppercase())).getString("recipientDeviceId"))
  }

  @Test
  fun `rejects empty or non json packets`() {
    assertRejected("") { build(packetJSON = "") }
    assertRejected("no es JSON válido") { build(packetJSON = "no-json") }
  }

  @Test
  fun `rejects packets without type`() {
    assertRejected("no declara tipo") { build(packetJSON = """{"id":"m1"}""") }
  }

  @Test
  fun `rejects invalid identities`() {
    assertRejected("destinatario") { build(recipientUpeerId = "zz") }
    assertRejected("remitente") { build(senderUpeerId = "aa") }
  }

  @Test
  fun `rejects invalid device and non positive ttl`() {
    assertRejected("dispositivo") { build(recipientDeviceId = "cc") }
    assertRejected("tiempo de vida") { build(ttlMillis = 0L) }
  }

  private fun assertRejected(fragment: String, block: () -> Unit) {
    try {
      block()
      fail("se esperaba IllegalArgumentException")
    } catch (cause: IllegalArgumentException) {
      assertTrue("mensaje inesperado: ${cause.message}", cause.message.orEmpty().contains(fragment))
    }
  }
}
