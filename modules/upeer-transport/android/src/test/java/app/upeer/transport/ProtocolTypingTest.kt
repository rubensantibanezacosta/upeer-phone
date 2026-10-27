package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTypingTest {
  private val peerId = "ab".repeat(32)
  private val state = JSONObject()
  private val typing = ProtocolTyping({ state }, Any(), {})

  @Test
  fun `mark stores the activity with its expiry`() {
    typing.mark(peerId, TypingIndicators.STATE_RECORDING, 1_000L)
    assertEquals(mapOf(peerId to TypingIndicators.STATE_RECORDING), typing.activities(999L))
  }

  @Test
  fun `expired activity disappears`() {
    typing.mark(peerId, TypingIndicators.STATE_TYPING, 1_000L)
    assertTrue(typing.activities(1_000L).isEmpty())
    assertTrue(typing.peers(1_000L).isEmpty())
  }

  @Test
  fun `clear removes the activity`() {
    typing.mark(peerId, TypingIndicators.STATE_TYPING, 5_000L)
    typing.clear(peerId)
    assertTrue(typing.activities(1_000L).isEmpty())
  }

  @Test
  fun `legacy numeric entry reads as typing`() {
    state.put("typing", JSONObject().put(peerId, 5_000L))
    assertEquals(mapOf(peerId to TypingIndicators.STATE_TYPING), typing.activities(1_000L))
  }
}
