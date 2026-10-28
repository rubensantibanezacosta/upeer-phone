package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class ReputationScoreTest {
  private val alice = "aa".repeat(32)
  private val bob = "bb".repeat(32)
  private val carol = "cc".repeat(32)
  private val dave = "dd".repeat(32)
  private val now = 1_700_000_000_000L

  private fun vouch(
    from: String,
    to: String = bob,
    type: String = ReputationVouch.HANDSHAKE,
    timestamp: Long = now,
  ): ReputationVouch {
    val id = ReputationVouch.idFor(from, to, type, timestamp)
    return ReputationVouch(id, from, to, type, ReputationVouch.isPositive(type), timestamp, "cd".repeat(64))
  }

  @Test
  fun `without vouches the score stays neutral`() {
    assertEquals(ReputationScore.NEUTRAL, ReputationScore.of(bob, emptyList(), setOf(alice), now))
  }

  @Test
  fun `vouches from direct contacts add up`() {
    val vouches = listOf(
      vouch(alice),
      vouch(carol, type = ReputationVouch.VAULT_HELPED),
    )
    assertEquals(53, ReputationScore.of(bob, vouches, setOf(alice, carol), now))
  }

  @Test
  fun `vouches from strangers do not count`() {
    val vouches = listOf(vouch(alice, type = ReputationVouch.MALICIOUS))
    assertEquals(ReputationScore.NEUTRAL, ReputationScore.of(bob, vouches, setOf(carol), now))
  }

  @Test
  fun `negative types subtract their weight`() {
    val vouches = listOf(
      vouch(alice, type = ReputationVouch.SPAM),
      vouch(carol, type = ReputationVouch.INTEGRITY_FAIL),
    )
    assertEquals(30, ReputationScore.of(bob, vouches, setOf(alice, carol), now))
  }

  @Test
  fun `the score never leaves the scale`() {
    val praises = (1..5).map { vouch(alice, type = ReputationVouch.VAULT_CHUNK, timestamp = now - it) }
    assertEquals(65, ReputationScore.of(bob, praises, setOf(alice), now))
    val many = (1..10).map { vouch(alice, type = ReputationVouch.VAULT_CHUNK, timestamp = now - it) }
    assertEquals(80, ReputationScore.of(bob, many, setOf(alice), now))
    val abusive = (1..5).map { vouch(carol, type = ReputationVouch.INTEGRITY_FAIL, timestamp = now - it) }
    assertEquals(0, ReputationScore.of(bob, abusive, setOf(carol), now))
  }

  @Test
  fun `a sender contributes at most ten vouches`() {
    val vouches = (1..12).map { vouch(alice, timestamp = now - it) }
    assertEquals(60, ReputationScore.of(bob, vouches, setOf(alice), now))
  }

  @Test
  fun `half points round up like the desktop`() {
    val vouches = listOf(vouch(alice, type = ReputationVouch.VAULT_RETRIEVED))
    assertEquals(52, ReputationScore.of(bob, vouches, setOf(alice), now))
  }

  @Test
  fun `vouches older than thirty days are ignored`() {
    val stale = listOf(vouch(alice, timestamp = now - ReputationVouch.THIRTY_DAYS_MS - 1))
    assertEquals(ReputationScore.NEUTRAL, ReputationScore.of(bob, stale, setOf(alice), now))
  }

  @Test
  fun `vouches about other subjects are ignored`() {
    val vouches = listOf(vouch(alice, to = dave, type = ReputationVouch.VAULT_CHUNK))
    assertEquals(ReputationScore.NEUTRAL, ReputationScore.of(bob, vouches, setOf(alice), now))
  }

  @Test
  fun `groups never carry a score`() {
    val vouches = listOf(vouch(alice, to = "grp-012345", type = ReputationVouch.VAULT_CHUNK))
    assertEquals(ReputationScore.NEUTRAL, ReputationScore.of("grp-012345", vouches, setOf(alice), now))
  }

  @Test
  fun `without direct contacts nothing is scored`() {
    val vouches = listOf(vouch(alice, type = ReputationVouch.VAULT_CHUNK))
    assertEquals(ReputationScore.NEUTRAL, ReputationScore.of(bob, vouches, emptySet(), now))
  }
}
