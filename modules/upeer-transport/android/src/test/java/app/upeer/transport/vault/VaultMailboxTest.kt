package app.upeer.transport.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMailboxTest {
  private val sender = "aa".repeat(32)
  private val recipient = "bb".repeat(32)
  private val now = 1_700_000_000_000L

  private fun entry(
    payloadHash: String = "dd".repeat(32),
    senderSid: String = sender,
    data: String = "0a0b",
    expiresAt: Long = now + 60_000L,
  ) = VaultEntry(
    id = 0L,
    payloadHash = payloadHash,
    recipientSid = recipient,
    recipientDeviceId = null,
    senderSid = senderSid,
    priority = VaultStoreEnvelope.PRIORITY_CHAT,
    data = data,
    expiresAt = expiresAt,
  )

  private fun empty() = VaultMailboxState(1L, emptyList())

  private fun stored(result: VaultMailboxInsert): VaultMailboxInsert.Stored {
    assertTrue("se esperaba almacenamiento, llegó $result", result is VaultMailboxInsert.Stored)
    return result as VaultMailboxInsert.Stored
  }

  private fun rejected(result: VaultMailboxInsert): String {
    assertTrue("se esperaba rechazo, llegó $result", result is VaultMailboxInsert.Rejected)
    return (result as VaultMailboxInsert.Rejected).reason
  }

  @Test
  fun `assigns the next id and advances the counter`() {
    val result = stored(VaultMailbox.insert(empty(), entry(), now))

    assertEquals(1L, result.entry.id)
    assertEquals(2L, result.state.nextId)
    assertEquals(1, result.state.entries.size)
  }

  @Test
  fun `deduplicates by payload hash without consuming quota`() {
    val first = stored(VaultMailbox.insert(empty(), entry(), now))
    val second = stored(VaultMailbox.insert(first.state, entry(), now))

    assertEquals(1, second.state.entries.size)
    assertEquals(1L, second.entry.id)
    assertEquals(2L, second.state.nextId)
  }

  @Test
  fun `purges expired entries before inserting`() {
    val expired = stored(VaultMailbox.insert(empty(), entry(expiresAt = now + 1L), now))
    val later = now + 10L

    val result = stored(VaultMailbox.insert(expired.state, entry(payloadHash = "ee".repeat(32)), later))

    assertEquals(1, result.state.entries.size)
    assertEquals("ee".repeat(32), result.state.entries[0].payloadHash)
  }

  @Test
  fun `rejects when the mailbox is full`() {
    val full = VaultMailboxState(1L, List(VaultMailbox.MAX_ENTRIES) { entry(payloadHash = "%064x".format(it)) })

    assertEquals("buzón lleno", rejected(VaultMailbox.insert(full, entry(payloadHash = "ff".repeat(32)), now)))
  }

  @Test
  fun `rejects when the sender quota is exhausted`() {
    val almostFull = stored(VaultMailbox.insert(empty(), entry(data = "0a0b0c"), now, quotaBytes = 4L))

    assertEquals(
      "cuota del remitente agotada",
      rejected(VaultMailbox.insert(almostFull.state, entry(payloadHash = "ff".repeat(32), data = "0a0b0c0d"), now, quotaBytes = 4L)),
    )
  }

  @Test
  fun `quota is accounted per sender`() {
    val almostFull = stored(VaultMailbox.insert(empty(), entry(data = "0a0b0c"), now, quotaBytes = 4L))

    val other = stored(
      VaultMailbox.insert(
        almostFull.state,
        entry(payloadHash = "ff".repeat(32), senderSid = "ee".repeat(32), data = "0a0b0c0d"),
        now,
        quotaBytes = 4L,
      ),
    )

    assertEquals(2, other.state.entries.size)
  }

  @Test
  fun `usage counts only the bytes of the given sender`() {
    val state = VaultMailboxState(
      3L,
      listOf(entry(data = "0a0b0c0d"), entry(payloadHash = "ee".repeat(32), senderSid = "ee".repeat(32), data = "0a0b")),
    )

    assertEquals(4L, VaultMailbox.usage(state.entries, sender))
    assertEquals(2L, VaultMailbox.usage(state.entries, "ee".repeat(32)))
    assertEquals(0L, VaultMailbox.usage(state.entries, recipient))
  }

  @Test
  fun `release removes the acknowledged hashes and keeps the counter`() {
    val state = VaultMailboxState(3L, listOf(entry(payloadHash = "dd".repeat(32)), entry(payloadHash = "ee".repeat(32))))

    val (next, removed) = VaultMailbox.release(state, listOf("dd".repeat(32).uppercase(), "ff".repeat(32)))

    assertEquals(1, removed)
    assertEquals(1, next.entries.size)
    assertEquals("ee".repeat(32), next.entries[0].payloadHash)
    assertEquals(3L, next.nextId)
  }

  @Test
  fun `purge keeps only the entries that are still alive`() {
    val state = VaultMailboxState(3L, listOf(entry(expiresAt = now), entry(payloadHash = "ee".repeat(32), expiresAt = now + 1L)))

    val purged = VaultMailbox.purge(state, now)

    assertEquals(1, purged.entries.size)
    assertEquals("ee".repeat(32), purged.entries[0].payloadHash)
  }
}
