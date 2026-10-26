package app.upeer.transport.vault

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRenewalTest {
  private val owner = "aa".repeat(32)
  private val other = "bb".repeat(32)
  private val hash = "cc".repeat(32)
  private val now = 1_700_000_000_000L
  private val day = 24L * 60L * 60L * 1000L

  private fun entry(expiresAt: Long, sender: String = owner) = VaultEntry(
    id = 1L,
    payloadHash = hash,
    recipientSid = other,
    recipientDeviceId = null,
    senderSid = sender,
    priority = VaultStoreEnvelope.PRIORITY_MESSAGE,
    data = "00",
    expiresAt = expiresAt,
  )

  private fun packet(newExpiresAt: Any?, payloadHash: String = hash) = JSONObject()
    .put("type", VaultRenewal.TYPE)
    .put("payloadHash", payloadHash)
    .apply { if (newExpiresAt != null) put("newExpiresAt", newExpiresAt) }

  @Test
  fun `owner extends the expiry`() {
    val target = now + 30 * day

    val result = VaultRenewal.parse(packet(target), owner, entry(now + day), now)

    assertTrue(result is VaultRenewalResult.Accepted)
    assertEquals(target, (result as VaultRenewalResult.Accepted).request.expiresAt)
  }

  @Test
  fun `renewal never shortens an entry`() {
    val far = now + 50 * day

    val result = VaultRenewal.parse(packet(now + day), owner, entry(far), now) as VaultRenewalResult.Accepted

    assertEquals(far, result.request.expiresAt)
  }

  @Test
  fun `renewal is capped at the maximum ttl`() {
    val requested = now + 365 * day

    val result = VaultRenewal.parse(packet(requested), owner, entry(now + day), now) as VaultRenewalResult.Accepted

    assertEquals(now + VaultStoreEnvelope.TTL_MILLIS, result.request.expiresAt)
  }

  @Test
  fun `a custodian that is not the original sender cannot renew`() {
    val result = VaultRenewal.parse(packet(now + 30 * day), other, entry(now + day), now)

    assertTrue(result is VaultRenewalResult.Rejected)
    assertEquals("renovación de una entrada ajena", (result as VaultRenewalResult.Rejected).reason)
  }

  @Test
  fun `an unknown entry is rejected`() {
    val result = VaultRenewal.parse(packet(now + day), owner, null, now)

    assertTrue(result is VaultRenewalResult.Rejected)
    assertEquals("entrada desconocida", (result as VaultRenewalResult.Rejected).reason)
  }

  @Test
  fun `missing or malformed expiry is rejected`() {
    assertTrue(VaultRenewal.parse(packet(null), owner, entry(now), now) is VaultRenewalResult.Rejected)
    assertTrue(VaultRenewal.parse(packet("mañana"), owner, entry(now), now) is VaultRenewalResult.Rejected)
    assertTrue(VaultRenewal.parse(packet(-1), owner, entry(now), now) is VaultRenewalResult.Rejected)
  }

  @Test
  fun `malformed hashes are rejected`() {
    assertTrue(VaultRenewal.parse(packet(now, ""), owner, entry(now), now) is VaultRenewalResult.Rejected)
    assertTrue(VaultRenewal.parse(packet(now, "zz"), owner, entry(now), now) is VaultRenewalResult.Rejected)
    assertTrue(VaultRenewal.parse(packet(now, "gg".repeat(32)), owner, entry(now), now) is VaultRenewalResult.Rejected)
  }

  @Test
  fun `sender comparison tolerates case and padding`() {
    val result = VaultRenewal.parse(packet(now + 30 * day), " ${owner.uppercase()} ", entry(now + day), now)

    assertTrue(result is VaultRenewalResult.Accepted)
  }

  @Test
  fun `build produces a packet that parse accepts`() {
    val built = VaultRenewal.build(hash.uppercase(), now + 30 * day)!!
    val result = VaultRenewal.parse(built, owner, entry(now + day), now)

    assertTrue(result is VaultRenewalResult.Accepted)
    assertEquals(hash, built.getString("payloadHash"))
    assertEquals("VAULT_RENEW", built.getString("type"))
  }

  @Test
  fun `a packet without the vault renew type is rejected`() {
    val withoutType = JSONObject(packet(now + day).toString()).apply { remove("type") }

    val result = VaultRenewal.parse(withoutType, owner, entry(now + day), now)

    assertTrue(result is VaultRenewalResult.Rejected)
    assertEquals("tipo inesperado", (result as VaultRenewalResult.Rejected).reason)
  }

  @Test
  fun `build refuses malformed input`() {
    assertNull(VaultRenewal.build("zz", now))
    assertNull(VaultRenewal.build(hash, -1))
    assertNotNull(VaultRenewal.build(hash, 0))
  }

  @Test
  fun `mailbox renew only moves forward`() {
    val state = VaultMailboxState(2L, listOf(entry(now + day)))

    val (forward, changed) = VaultMailbox.renew(state, hash, now + 20 * day)
    assertTrue(changed)
    assertEquals(now + 20 * day, forward.entries.single().expiresAt)

    val (back, changedBack) = VaultMailbox.renew(forward, hash, now)
    assertTrue(!changedBack)
    assertEquals(forward, back)
  }

  @Test
  fun `mailbox renew ignores unknown entries`() {
    val state = VaultMailboxState(2L, listOf(entry(now + day)))

    val (next, changed) = VaultMailbox.renew(state, "dd".repeat(32), now + 20 * day)

    assertTrue(!changed)
    assertEquals(state, next)
  }

  @Test
  fun `expiring soon ignores expired and distant entries`() {
    val window = 15 * day
    val state = VaultMailboxState(
      4L,
      listOf(
        entry(now + 2 * day).copy(id = 1L),
        entry(now - day).copy(id = 2L, payloadHash = "ee".repeat(32)),
        entry(now + 40 * day).copy(id = 3L, payloadHash = "ff".repeat(32)),
      ),
    )

    val expiring = VaultMailbox.expiringSoon(state, window, now)

    assertEquals(listOf(1L), expiring.map { it.id })
  }
}
