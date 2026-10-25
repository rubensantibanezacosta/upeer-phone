package app.upeer.transport.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultHandoverTest {
  private val envelope = VaultStoreEnvelope.Envelope("hash", """{"type":"VAULT_STORE"}""")

  private fun candidate(seed: Int, address: String = "200::$seed") =
    VaultCustodianCandidate(("%02x".format(seed)).repeat(32), address, seed.toLong())

  @Test
  fun `counts every accepted custodian`() {
    val result = VaultHandover.replicate(
      envelope,
      listOf(candidate(1), candidate(2), candidate(3)),
    ) { _, _ -> true }

    assertEquals(VaultHandoverResult(3, 3), result)
    assertTrue(result.stored)
  }

  @Test
  fun `reports not stored when every custodian fails`() {
    val result = VaultHandover.replicate(envelope, listOf(candidate(1), candidate(2))) { _, _ -> false }

    assertEquals(VaultHandoverResult(2, 0), result)
    assertFalse(result.stored)
  }

  @Test
  fun `keeps the packet when some custodians fail`() {
    var calls = 0
    val result = VaultHandover.replicate(envelope, listOf(candidate(1), candidate(2), candidate(3))) { _, _ ->
      calls++
      calls == 2
    }

    assertEquals(3, result.attempted)
    assertEquals(1, result.accepted)
    assertTrue(result.stored)
  }

  @Test
  fun `sends the same envelope to every custodian`() {
    val received = mutableListOf<String>()
    VaultHandover.replicate(envelope, listOf(candidate(1), candidate(2))) { _, packet ->
      received.add(packet)
      true
    }

    assertEquals(listOf(envelope.json, envelope.json), received)
  }

  @Test
  fun `deduplicates addresses regardless of case`() {
    val received = mutableListOf<String>()
    val result = VaultHandover.replicate(
      envelope,
      listOf(candidate(1, "200::1"), candidate(2, "200::1"), candidate(3, "200::2")),
    ) { address, _ ->
      received.add(address)
      true
    }

    assertEquals(2, result.attempted)
    assertEquals(listOf("200::1", "200::2"), received)
  }

  @Test
  fun `skips custodians without address`() {
    val received = mutableListOf<String>()
    val result = VaultHandover.replicate(
      envelope,
      listOf(candidate(1, "  "), candidate(2, "200::2")),
    ) { address, _ ->
      received.add(address)
      true
    }

    assertEquals(1, result.attempted)
    assertEquals(listOf("200::2"), received)
  }

  @Test
  fun `empty custodian list is not stored`() {
    val result = VaultHandover.replicate(envelope, emptyList()) { _, _ ->
      throw IllegalStateException("no debería enviarse nada")
    }

    assertEquals(VaultHandoverResult(0, 0), result)
    assertFalse(result.stored)
  }
}
