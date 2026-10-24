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
    assertEquals(1, data.length())
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
    val typing = JSONArray(ProtocolConversations.build(messages, JSONObject(), setOf(peerID))).getJSONObject(0)
    val idle = JSONArray(ProtocolConversations.build(messages, JSONObject(), emptySet())).getJSONObject(0)
    assertTrue(typing.optBoolean("typing"))
    assertFalse(idle.optBoolean("typing"))
  }
}
