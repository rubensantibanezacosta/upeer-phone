package app.upeer.transport.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCustodyTest {
  private val self = "aa".repeat(32)
  private val recipient = "bb".repeat(32)

  private fun candidate(seed: Int, lastSeen: Long, address: String = "200::$seed"): VaultCustodianCandidate =
    VaultCustodianCandidate(("%02x".format(seed)).repeat(32), address, lastSeen)

  @Test
  fun `excludes self and recipient`() {
    val selected = VaultCustody.select(
      listOf(
        VaultCustodianCandidate(self, "200::1", 90),
        VaultCustodianCandidate(recipient, "200::2", 91),
        candidate(3, 10),
      ),
      self,
      recipient,
    )

    assertEquals(listOf(candidate(3, 10).upeerId), selected.map { it.upeerId })
  }

  @Test
  fun `excludes self and recipient regardless of case`() {
    val selected = VaultCustody.select(
      listOf(
        VaultCustodianCandidate(self.uppercase(), "200::1", 90),
        VaultCustodianCandidate(recipient.uppercase(), "200::2", 91),
      ),
      self,
      recipient,
    )

    assertTrue(selected.isEmpty())
  }

  @Test
  fun `orders by last seen descending`() {
    val selected = VaultCustody.select(
      listOf(candidate(1, 10), candidate(2, 30), candidate(3, 20)),
      self,
      recipient,
    )

    assertEquals(listOf(candidate(2, 30).upeerId, candidate(3, 20).upeerId, candidate(1, 10).upeerId), selected.map { it.upeerId })
  }

  @Test
  fun `breaks ties by upeer id so the result is deterministic`() {
    val first = VaultCustody.select(listOf(candidate(7, 10), candidate(4, 10)), self, recipient)
    val second = VaultCustody.select(listOf(candidate(4, 10), candidate(7, 10)), self, recipient)

    assertEquals(first.map { it.upeerId }, second.map { it.upeerId })
    assertEquals(listOf(candidate(4, 10).upeerId, candidate(7, 10).upeerId), first.map { it.upeerId })
  }

  @Test
  fun `keeps the newest entry when a candidate appears twice`() {
    val selected = VaultCustody.select(
      listOf(candidate(5, 10, "200::old"), candidate(5, 40, "200::new")),
      self,
      recipient,
    )

    assertEquals(1, selected.size)
    assertEquals("200::new", selected[0].address)
    assertEquals(40, selected[0].lastSeenMillis)
  }

  @Test
  fun `normalizes the upeer id to lowercase`() {
    val selected = VaultCustody.select(
      listOf(VaultCustodianCandidate("CC".repeat(32), "200::1", 10)),
      self,
      recipient,
    )

    assertEquals("cc".repeat(32), selected[0].upeerId)
  }

  @Test
  fun `drops invalid identities and empty addresses`() {
    val selected = VaultCustody.select(
      listOf(
        VaultCustodianCandidate("not-hex", "200::1", 90),
        VaultCustodianCandidate("dd".repeat(31), "200::2", 91),
        candidate(3, 10, ""),
      ),
      self,
      recipient,
    )

    assertTrue(selected.isEmpty())
  }

  @Test
  fun `limits the number of custodians to the replication factor`() {
    val candidates = (1..20).map { candidate(it, it.toLong()) }

    val selected = VaultCustody.select(candidates, self, recipient)

    assertEquals(VaultCustody.REPLICATION_FACTOR, selected.size)
    assertEquals(VaultCustody.REPLICATION_FACTOR, 8)
  }

  @Test
  fun `honours an explicit limit`() {
    val candidates = (1..20).map { candidate(it, it.toLong()) }

    assertEquals(3, VaultCustody.select(candidates, self, recipient, limit = 3).size)
  }

  @Test
  fun `a non positive limit selects nobody`() {
    assertTrue(VaultCustody.select(listOf(candidate(1, 10)), self, recipient, limit = 0).isEmpty())
  }

  @Test
  fun `an empty candidate list selects nobody`() {
    assertTrue(VaultCustody.select(emptyList(), self, recipient).isEmpty())
  }
}
