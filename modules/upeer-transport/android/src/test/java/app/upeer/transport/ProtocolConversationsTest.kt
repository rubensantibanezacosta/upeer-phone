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
    val result = conversations(peers = peers(42L), typing = mapOf(peerId to TypingIndicators.STATE_TYPING))
    assertEquals(true, result.optBoolean("typing"))
    assertEquals(TypingIndicators.STATE_TYPING, result.optString("activity"))
    assertEquals(42L, result.optLong("lastSeen"))
  }

  @Test
  fun `recording activity reaches the conversation`() {
    val result = conversations(peers = peers(42L), typing = mapOf(peerId to TypingIndicators.STATE_RECORDING))
    assertEquals(true, result.optBoolean("typing"))
    assertEquals(TypingIndicators.STATE_RECORDING, result.optString("activity"))
  }

  @Test
  fun `idle conversation carries an empty activity`() {
    val result = conversations(peers = peers(42L))
    assertEquals(false, result.optBoolean("typing"))
    assertEquals("", result.optString("activity"))
  }

  @Test
  fun `a newer reaction on an older message becomes the preview`() {
    val messages = JSONArray()
      .put(message("m1", "hola", 1_000L).put("latestReaction", reaction("👍", "b".repeat(64), 5_000L)))
      .put(message("m2", "adios", 2_000L))
    val result = JSONArray(ProtocolConversations.build(messages, peers(42L))).optJSONObject(0)

    assertEquals("adios", result.optString("lastMessage"))
    assertEquals(5_000L, result.optLong("lastTimestamp"))
    assertEquals("👍", result.optJSONObject("lastReaction")!!.optString("emoji"))
    assertEquals("hola", result.optJSONObject("lastReaction")!!.optString("message"))
  }

  @Test
  fun `a reaction older than the last message keeps the plain preview`() {
    val messages = JSONArray()
      .put(message("m1", "hola", 1_000L).put("latestReaction", reaction("👍", "b".repeat(64), 1_500L)))
      .put(message("m2", "adios", 2_000L))
    val result = JSONArray(ProtocolConversations.build(messages, peers(42L))).optJSONObject(0)

    assertEquals(false, result.has("lastReaction"))
    assertEquals(2_000L, result.optLong("lastTimestamp"))
  }

  @Test
  fun `conversation without reactions has no reaction preview`() {
    val result = conversations(peers = peers(42L))
    assertEquals(false, result.has("lastReaction"))
  }

  private fun message(id: String, content: String, timestamp: Long): JSONObject = JSONObject()
    .put("id", id)
    .put("upeerId", peerId)
    .put("content", content)
    .put("isMine", false)
    .put("status", "delivered")
    .put("timestamp", timestamp)

  private fun reaction(emoji: String, actor: String, at: Long): JSONObject = JSONObject()
    .put("emoji", emoji)
    .put("actorUpeerId", actor)
    .put("at", at)

  private fun conversations(peers: JSONObject, typing: Map<String, String> = emptyMap()): JSONObject {
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
