package app.upeer.transport.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultLocalDrainTest {
  private val self = "ab".repeat(32)
  private val other = "cd".repeat(32)
  private val now = 1_700_000_000_000L

  private fun entry(
    payloadHash: String = "dd".repeat(32),
    recipientSid: String = self,
    recipientDeviceId: String? = null,
    expiresAt: Long = now + 60_000L,
  ) = VaultEntry(
    id = 0L,
    payloadHash = payloadHash,
    recipientSid = recipientSid,
    recipientDeviceId = recipientDeviceId,
    senderSid = self,
    priority = VaultStoreEnvelope.PRIORITY_CHAT,
    data = "0a0b",
    expiresAt = expiresAt,
  )

  @Test
  fun `selects entries addressed to this account`() {
    val selected = VaultLocalDrain.select(listOf(entry(), entry(payloadHash = "ee".repeat(32), recipientSid = other)), self, "dev-1", now)

    assertEquals(1, selected.size)
    assertEquals("dd".repeat(32), selected.first().payloadHash)
  }

  @Test
  fun `keeps entries aimed at this device and drops foreign devices`() {
    val entries = listOf(
      entry(payloadHash = "11".repeat(32), recipientDeviceId = "dev-1"),
      entry(payloadHash = "22".repeat(32), recipientDeviceId = "dev-2"),
    )

    val selected = VaultLocalDrain.select(entries, self, "dev-1", now)

    assertEquals(listOf("11".repeat(32)), selected.map { it.payloadHash })
  }

  @Test
  fun `ignores expired entries`() {
    val entries = listOf(
      entry(payloadHash = "33".repeat(32), expiresAt = now - 1L),
      entry(payloadHash = "44".repeat(32), expiresAt = now + 1L),
    )

    val selected = VaultLocalDrain.select(entries, self, null, now)

    assertEquals(listOf("44".repeat(32)), selected.map { it.payloadHash })
  }

  @Test
  fun `matches account case insensitively`() {
    val selected = VaultLocalDrain.select(listOf(entry(recipientSid = self.uppercase())), self, "dev-1", now)

    assertTrue(selected.isNotEmpty())
  }

  @Test
  fun `returns nothing when the mailbox holds no own entries`() {
    val selected = VaultLocalDrain.select(listOf(entry(recipientSid = other)), self, "dev-1", now)

    assertTrue(selected.isEmpty())
  }
}
