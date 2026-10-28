package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReputationStoreTest {
  private val alice = "aa".repeat(32)
  private val bob = "bb".repeat(32)
  private val carol = "cc".repeat(32)
  private val now = System.currentTimeMillis()

  private fun store(): ReputationStore {
    val state = JSONObject()
    return ReputationStore({ state }, Any(), {})
  }

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
  fun `accepted vouches are stored once`() {
    val store = store()
    val incoming = listOf(vouch(alice))
    assertEquals(1, store.accept(alice, incoming, now))
    assertEquals(0, store.accept(alice, incoming, now))
    assertEquals(1, store.all().size)
  }

  @Test
  fun `vouches relayed by another peer are refused`() {
    val store = store()
    assertEquals(0, store.accept(carol, listOf(vouch(alice)), now))
    assertTrue(store.all().isEmpty())
  }

  @Test
  fun `a sender stops at the daily cap`() {
    val store = store()
    val incoming = (1..250).map { vouch(alice, timestamp = now - it) }
    assertEquals(ReputationVouch.MAX_PER_SENDER_PER_DAY, store.accept(alice, incoming, now))
  }

  @Test
  fun `gossip only asks for the ids we miss`() {
    val store = store()
    val known = vouch(alice)
    store.accept(alice, listOf(known), now)
    val other = vouch(carol)
    assertEquals(listOf(other.id), store.missing(listOf(known.id, other.id, other.id), now))
  }

  @Test
  fun `gossip requests at most fifty ids`() {
    val store = store()
    val ids = (1..80).map { vouch(alice, timestamp = now - it).id }
    assertEquals(50, store.missing(ids, now).size)
  }

  @Test
  fun `delivery is limited to what we hold`() {
    val store = store()
    val first = vouch(alice)
    val second = vouch(carol, timestamp = now - 1)
    store.accept(alice, listOf(first), now)
    store.accept(carol, listOf(second), now)
    val delivered = store.deliverable(listOf(first.id, second.id, vouch(bob).id))
    assertEquals(listOf(first.id, second.id), delivered.map { it.id })
  }

  @Test
  fun `scores are reported per subject`() {
    val store = store()
    store.accept(alice, listOf(vouch(alice)), now)
    val scores = JSONObject(store.scoresJSON(listOf(bob, carol), listOf(alice), now))
    assertEquals(51, scores.getInt(bob))
    assertEquals(ReputationScore.NEUTRAL, scores.getInt(carol))
  }

  @Test
  fun `stale vouches are dropped on read`() {
    val store = store()
    assertEquals(0, store.accept(alice, listOf(vouch(alice, timestamp = now - ReputationVouch.THIRTY_DAYS_MS - 1)), now))
    assertEquals(1, store.accept(carol, listOf(vouch(carol)), now))
    assertEquals(1, store.all().size)
  }
}
