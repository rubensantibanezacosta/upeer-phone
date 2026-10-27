package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingIndicatorsTest {
  private val peerID = "ab".repeat(32)

  @Test
  fun windowExpiresSixSecondsAfterTheEvent() {
    assertEquals(1_000L + TypingIndicators.WINDOW_MS, TypingIndicators.expiresAt(1_000L))
  }

  @Test
  fun indicatorIsActiveOnlyBeforeExpiry() {
    assertTrue(TypingIndicators.isActive(7_000L, 6_999L))
    assertFalse(TypingIndicators.isActive(7_000L, 7_000L))
    assertFalse(TypingIndicators.isActive(0L, 1L))
  }

  @Test
  fun dataIsAPlainTypingPacket() {
    val data = TypingIndicators.data()
    assertEquals("TYPING", data.optString("type"))
    assertEquals(TypingIndicators.STATE_TYPING, data.optString("state"))
    assertEquals(2, data.length())
  }

  @Test
  fun dataCarriesTheRequestedState() {
    assertEquals(TypingIndicators.STATE_RECORDING, TypingIndicators.data(TypingIndicators.STATE_RECORDING).optString("state"))
    assertEquals(TypingIndicators.STATE_STOP, TypingIndicators.data(TypingIndicators.STATE_STOP).optString("state"))
  }

  @Test
  fun unknownStateFallsBackToTyping() {
    assertEquals(TypingIndicators.STATE_TYPING, TypingIndicators.stateOf(null))
    assertEquals(TypingIndicators.STATE_TYPING, TypingIndicators.stateOf(""))
    assertEquals(TypingIndicators.STATE_TYPING, TypingIndicators.stateOf("grabando"))
    assertEquals(TypingIndicators.STATE_RECORDING, TypingIndicators.stateOf("recording"))
    assertEquals(TypingIndicators.STATE_STOP, TypingIndicators.stateOf("stop"))
    assertEquals(TypingIndicators.STATE_TYPING, TypingIndicators.data("grabando").optString("state"))
  }

  @Test
  fun conversationCarriesTheTypingFlag() {
    val message = JSONObject()
      .put("id", "3f2504e0-4f89-41d3-9a0c-0305e82c3301")
      .put("upeerId", peerID)
      .put("content", "hola")
      .put("timestamp", 10L)
      .put("isMine", false)
      .put("status", "delivered")
    val messages = JSONArray().put(message)
    val typing = JSONArray(ProtocolConversations.build(messages, JSONObject(), mapOf(peerID to TypingIndicators.STATE_TYPING))).getJSONObject(0)
    val idle = JSONArray(ProtocolConversations.build(messages, JSONObject(), emptyMap())).getJSONObject(0)
    assertTrue(typing.optBoolean("typing"))
    assertEquals(TypingIndicators.STATE_TYPING, typing.optString("activity"))
    assertFalse(idle.optBoolean("typing"))
    assertEquals("", idle.optString("activity"))
  }
}
