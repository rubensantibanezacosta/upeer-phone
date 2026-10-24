package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolConversationsTest {
  private val peerId = "a".repeat(64)

  @Test
  fun `conversation carries the peer last seen`() {
    val result = conversations(peers = peers(1_700_000_000_000))
    assertEquals(1_700_000_000_000, result.optLong("lastSeen"))
  }

  @Test
  fun `unknown peer reports zero last seen`() {
    val result = conversations(peers = JSONObject())
    assertEquals(0L, result.optLong("lastSeen"))
    assertEquals(true, result.has("lastSeen"))
  }

  @Test
  fun `last message and alias survive alongside presence`() {
    val result = conversations(peers = peers(42L))
    assertEquals("hola", result.optString("lastMessage"))
    assertEquals("Ana", result.optString("alias"))
    assertEquals("delivered", result.optString("lastStatus"))
  }

  @Test
  fun `typing flag stays independent from presence`() {
    val result = conversations(peers = peers(42L), typing = setOf(peerId))
    assertEquals(true, result.optBoolean("typing"))
    assertEquals(42L, result.optLong("lastSeen"))
  }

  private fun conversations(peers: JSONObject, typing: Set<String> = emptySet()): JSONObject {
    val messages = JSONArray().put(
      JSONObject()
        .put("id", "3f2504e0-4f89-41d3-9a0c-0305e82c3301")
        .put("upeerId", peerId)
        .put("content", "hola")
        .put("isMine", false)
        .put("status", "delivered")
        .put("timestamp", 1_700_000_000_000L),
    )
    val built = ProtocolConversations.build(messages, peers, typing)
    return JSONArray(built).optJSONObject(0)
  }

  private fun peers(lastSeen: Long): JSONObject = JSONObject().put(
    peerId,
    JSONObject().put("alias", "Ana").put("lastSeen", lastSeen),
  )
}
