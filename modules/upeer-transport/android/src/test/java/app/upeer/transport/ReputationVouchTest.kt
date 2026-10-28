package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReputationVouchTest {
  private val alice = "aa".repeat(32)
  private val bob = "bb".repeat(32)
  private val now = 1_700_000_000_000L

  private fun vouch(
    from: String = alice,
    to: String = bob,
    type: String = ReputationVouch.HANDSHAKE,
    timestamp: Long = now,
  ): ReputationVouch {
    val id = ReputationVouch.idFor(from, to, type, timestamp)
    return ReputationVouch(id, from, to, type, ReputationVouch.isPositive(type), timestamp, "cd".repeat(64))
  }

  @Test
  fun `deterministic ids match the desktop formula`() {
    assertEquals(64, ReputationVouch.idFor(alice, bob, ReputationVouch.HANDSHAKE, now).length)
    assertEquals(
      ReputationVouch.idFor(alice, bob, ReputationVouch.HANDSHAKE, now),
      ReputationVouch.idFor(alice, bob, ReputationVouch.HANDSHAKE, now),
    )
    assertFalse(
      ReputationVouch.idFor(alice, bob, ReputationVouch.HANDSHAKE, now) ==
        ReputationVouch.idFor(alice, bob, ReputationVouch.HANDSHAKE, now + 1),
    )
  }

  @Test
  fun `positive types carry their weight`() {
    assertTrue(ReputationVouch.isPositive(ReputationVouch.VAULT_CHUNK))
    assertFalse(ReputationVouch.isPositive(ReputationVouch.SPAM))
    assertEquals(3.0, ReputationVouch.weight(ReputationVouch.VAULT_CHUNK), 0.001)
    assertEquals(15.0, ReputationVouch.weight(ReputationVouch.INTEGRITY_FAIL), 0.001)
    assertEquals(0.0, ReputationVouch.weight("unknown"), 0.001)
  }

  @Test
  fun `valid vouches round-trip through json`() {
    val original = vouch()
    val parsed = ReputationVouch.parse(JSONObject(original.toJson().toString()), now)
    assertEquals(original, parsed)
  }

  @Test
  fun `vouches with a mismatched id are rejected`() {
    val json = vouch().toJson().put("id", "ee".repeat(32))
    assertNull(ReputationVouch.parse(json, now))
  }

  @Test
  fun `vouches outside the time window are rejected`() {
    val future = vouch(timestamp = now + ReputationVouch.FUTURE_TOLERANCE_MS + 1)
    assertNull(ReputationVouch.parse(future.toJson(), now))
    val stale = vouch(timestamp = now - ReputationVouch.THIRTY_DAYS_MS - 1)
    assertNull(ReputationVouch.parse(stale.toJson(), now))
  }

  @Test
  fun `vouches with an unknown type or short signature are rejected`() {
    val unknown = vouch().toJson().put("type", "friendship")
    assertNull(ReputationVouch.parse(unknown, now))
    val shortSignature = vouch().toJson().put("signature", "cd")
    assertNull(ReputationVouch.parse(shortSignature, now))
  }

  @Test
  fun `group and address subjects never have reputation`() {
    assertFalse(ReputationVouch.isAccountSubject("grp-012345"))
    assertFalse(ReputationVouch.isAccountSubject("2001:db8::1"))
    assertFalse(ReputationVouch.isAccountSubject(""))
    assertTrue(ReputationVouch.isAccountSubject(alice))
  }

  @Test
  fun `group subjects are rejected on parse`() {
    val json = vouch().toJson().put("toId", "grp-012345")
    assertNull(ReputationVouch.parse(json, now))
  }
}
