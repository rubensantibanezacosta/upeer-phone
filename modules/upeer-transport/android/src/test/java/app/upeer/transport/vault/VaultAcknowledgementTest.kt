package app.upeer.transport.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAcknowledgementTest {
  private val sender = "aa".repeat(32)
  private val recipient = "bb".repeat(32)
  private val device = "cc".repeat(32)
  private val hash = "dd".repeat(32)

  @Test
  fun `build declares the type and normalizes the hashes`() {
    val ack = VaultAcknowledgement.build(listOf(hash.uppercase()))!!

    assertEquals("VAULT_ACK", ack.getString("type"))
    assertEquals(hash, ack.getJSONArray("payloadHashes").getString(0))
  }

  @Test
  fun `build carries the device when present`() {
    assertEquals(device, VaultAcknowledgement.build(listOf(hash), device.uppercase())!!.getString("deviceId"))
  }

  @Test
  fun `build rejects an empty list`() {
    assertNull(VaultAcknowledgement.build(emptyList()))
  }

  @Test
  fun `build rejects more hashes than allowed`() {
    assertNull(VaultAcknowledgement.build(List(VaultAcknowledgement.MAX_HASHES + 1) { hash }))
  }

  @Test
  fun `build rejects a non hexadecimal hash`() {
    assertNull(VaultAcknowledgement.build(listOf("zz")))
  }

  @Test
  fun `build rejects an invalid device`() {
    assertNull(VaultAcknowledgement.build(listOf(hash), "cc"))
  }

  @Test
  fun `parse returns the hashes of a valid packet`() {
    val parsed = VaultAcknowledgement.parse(VaultAcknowledgement.build(listOf(hash, "ee".repeat(32)))!!)!!

    assertEquals(listOf(hash, "ee".repeat(32)), parsed)
  }

  @Test
  fun `parse rejects another type`() {
    assertNull(VaultAcknowledgement.parse(JSONObject().put("type", "VAULT_DELIVERY").put("payloadHashes", org.json.JSONArray())))
  }

  @Test
  fun `parse rejects an invalid device id`() {
    val body = VaultAcknowledgement.build(listOf(hash))!!.put("deviceId", "cc")

    assertNull(VaultAcknowledgement.parse(body))
  }

  @Test
  fun `releases an entry addressed to the acknowledging recipient`() {
    val entry = entry()

    assertTrue(VaultAcknowledgement.releasable(entry, recipient, null))
  }

  @Test
  fun `releases a wildcard entry`() {
    val entry = entry(recipientSid = VaultStoreRequest.WILDCARD)

    assertTrue(VaultAcknowledgement.releasable(entry, recipient, null))
  }

  @Test
  fun `does not release entries of another recipient`() {
    assertFalse(VaultAcknowledgement.releasable(entry(recipientSid = sender), recipient, null))
  }

  @Test
  fun `only releases a device addressed entry for that device`() {
    val entry = entry(recipientDeviceId = device)

    assertFalse(VaultAcknowledgement.releasable(entry, recipient, null))
    assertTrue(VaultAcknowledgement.releasable(entry, recipient, device))
  }

  private fun entry(
    recipientSid: String = recipient,
    recipientDeviceId: String? = null,
  ) = VaultEntry(
    id = 1L,
    payloadHash = hash,
    recipientSid = recipientSid,
    recipientDeviceId = recipientDeviceId,
    senderSid = sender,
    priority = VaultStoreEnvelope.PRIORITY_CHAT,
    data = "0a0b",
    expiresAt = 1L,
  )
}
