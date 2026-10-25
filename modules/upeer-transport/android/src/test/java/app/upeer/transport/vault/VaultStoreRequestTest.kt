package app.upeer.transport.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultStoreRequestTest {
  private val sender = "aa".repeat(32)
  private val recipient = "bb".repeat(32)
  private val device = "cc".repeat(32)
  private val hash = "dd".repeat(32)
  private val now = 1_700_000_000_000L

  private fun packet(
    payloadHash: String = hash,
    recipientSid: String = recipient,
    recipientDeviceId: String? = null,
    data: String = "0a0b",
    priority: Int? = VaultStoreEnvelope.PRIORITY_CHAT,
    expiresAt: Long? = now + 1_000L,
  ) = JSONObject()
    .put("type", "VAULT_STORE")
    .put("payloadHash", payloadHash)
    .put("recipientSid", recipientSid)
    .put("data", data)
    .apply { if (priority != null) put("priority", priority) }
    .apply { if (expiresAt != null) put("expiresAt", expiresAt) }
    .apply { if (recipientDeviceId != null) put("recipientDeviceId", recipientDeviceId) }

  private fun accepted(body: JSONObject = packet(), authenticatedSender: String = sender): VaultEntry {
    val result = VaultStoreRequest.parse(body, authenticatedSender, now)
    assertTrue("se esperaba aceptación, llegó $result", result is VaultStoreResult.Accepted)
    return (result as VaultStoreResult.Accepted).entry
  }

  private fun rejected(body: JSONObject, authenticatedSender: String = sender): String {
    val result = VaultStoreRequest.parse(body, authenticatedSender, now)
    assertTrue("se esperaba rechazo, llegó $result", result is VaultStoreResult.Rejected)
    return (result as VaultStoreResult.Rejected).reason
  }

  @Test
  fun `accepts a well formed packet and normalizes it`() {
    val entry = accepted(packet(payloadHash = hash.uppercase(), recipientSid = recipient.uppercase(), data = "0A0B"))

    assertEquals(hash, entry.payloadHash)
    assertEquals(recipient, entry.recipientSid)
    assertEquals("0a0b", entry.data)
    assertEquals(sender, entry.senderSid)
    assertEquals(VaultStoreEnvelope.PRIORITY_CHAT, entry.priority)
    assertEquals(null, entry.recipientDeviceId)
  }

  @Test
  fun `uses the authenticated sender and ignores the payload field`() {
    val entry = accepted(packet().put("senderSid", "ee".repeat(32)))

    assertEquals(sender, entry.senderSid)
  }

  @Test
  fun `rejects a packet of another type`() {
    assertEquals("tipo inesperado", rejected(packet().put("type", "CHAT")))
  }

  @Test
  fun `rejects a non hexadecimal hash`() {
    assertEquals("hash no hexadecimal", rejected(packet(payloadHash = "zz")))
  }

  @Test
  fun `rejects a recipient that is neither a upeer id nor the wildcard`() {
    assertEquals("destinatario no hexadecimal", rejected(packet(recipientSid = "bb")))
  }

  @Test
  fun `accepts the wildcard recipient`() {
    assertEquals(VaultStoreRequest.WILDCARD, accepted(packet(recipientSid = "*")).recipientSid)
  }

  @Test
  fun `rejects an invalid recipient device`() {
    assertEquals("dispositivo destino inválido", rejected(packet(recipientDeviceId = "cc")))
  }

  @Test
  fun `keeps the recipient device when it is valid`() {
    assertEquals(device, accepted(packet(recipientDeviceId = device)).recipientDeviceId)
  }

  @Test
  fun `rejects oversized data`() {
    val big = "0a".repeat(VaultStoreRequest.MAX_DATA_LENGTH)

    assertEquals("datos demasiado grandes", rejected(packet(data = big)))
  }

  @Test
  fun `rejects priorities outside the allowed range`() {
    assertEquals("prioridad fuera de rango", rejected(packet(priority = 4)))
    assertEquals("prioridad fuera de rango", rejected(packet(priority = 0)))
  }

  @Test
  fun `defaults the priority to message when absent`() {
    assertEquals(VaultStoreEnvelope.PRIORITY_MESSAGE, accepted(packet(priority = null)).priority)
  }

  @Test
  fun `clamps the expiration to the maximum ttl`() {
    val entry = accepted(packet(expiresAt = now + 90L * 24L * 60L * 60L * 1000L))

    assertEquals(now + VaultStoreEnvelope.TTL_MILLIS, entry.expiresAt)
  }

  @Test
  fun `defaults the expiration when the packet does not declare it`() {
    assertEquals(now + VaultStoreEnvelope.TTL_MILLIS, accepted(packet(expiresAt = null)).expiresAt)
  }

  @Test
  fun `rejects an already expired packet`() {
    assertEquals("ya caducado", rejected(packet(expiresAt = now - 1L)))
  }

  @Test
  fun `rejects when the authenticated sender is not a upeer id`() {
    assertEquals("remitente autenticado inválido", rejected(packet(), authenticatedSender = "aa"))
  }
}
