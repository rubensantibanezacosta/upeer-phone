package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolMessageStateTest {
  private val messageID = "12345678-1234-1234-1234-123456789012"
  private val peerID = "aa".repeat(32)

  private fun message(version: Int = 1): JSONObject = JSONObject()
    .put("id", messageID)
    .put("upeerId", peerID)
    .put("isMine", false)
    .put("content", "original")
    .put("status", "delivered")
    .put("version", version)

  @Test
  fun `applies incoming updates once and rejects stale versions`() {
    val messages = JSONArray().put(message())

    val updated = ProtocolMessageState.updateIncomingContent(messages, messageID, peerID, "editado", 2, 100L)

    assertNotNull(updated)
    assertEquals("editado", updated?.optString("content"))
    assertEquals(2, updated?.optInt("version"))
    assertTrue(updated?.optBoolean("isEdited") == true)
    assertNull(ProtocolMessageState.updateIncomingContent(messages, messageID, peerID, "viejo", 2, 101L))
    assertNull(ProtocolMessageState.updateIncomingContent(messages, messageID, peerID, "viejo", 1, 101L))
    assertEquals("editado", messages.getJSONObject(0).optString("content"))
  }

  @Test
  fun `rejects updates for foreign or own messages`() {
    val mine = message().put("isMine", true)
    val messages = JSONArray().put(mine)

    assertNull(ProtocolMessageState.updateIncomingContent(messages, messageID, peerID, "ajeno", 2))
    assertNull(ProtocolMessageState.updateIncomingContent(messages, messageID, "bb".repeat(32), "ajeno", 2))
  }

  @Test
  fun `updates local content only for owned messages`() {
    val messages = JSONArray().put(message().put("isMine", true))

    val updated = ProtocolMessageState.updateLocalContent(messages, messageID, peerID, "propio", 2, 200L)

    assertNotNull(updated)
    assertEquals("propio", updated?.optString("content"))
    assertNull(ProtocolMessageState.updateLocalContent(messages, messageID, "bb".repeat(32), "otro", 2))
  }

  @Test
  fun `removes every message of a conversation and keeps the rest`() {
    val other = "bb".repeat(32)
    val messages = JSONArray()
      .put(message())
      .put(JSONObject(message().toString()).put("id", "aaaaaaaa-1111-1111-1111-111111111111"))
      .put(JSONObject(message().toString()).put("id", "cccccccc-1111-1111-1111-111111111111").put("upeerId", other))

    val remaining = ProtocolMessageState.withoutConversation(messages, peerID)

    assertEquals(1, remaining.length())
    assertEquals(other, remaining.optJSONObject(0)?.optString("upeerId"))
    assertEquals(3, messages.length())
  }

  @Test
  fun `keeps the array untouched when the conversation is unknown`() {
    val messages = JSONArray().put(message())

    val remaining = ProtocolMessageState.withoutConversation(messages, "cc".repeat(32))

    assertEquals(1, remaining.length())
  }

  @Test
  fun `chat edit policy plans versions and rejects invalid edits`() {
    val pending = message().put("isMine", true).put("status", "pending")
    val plan = ChatEditPolicy.plan(pending, peerID, messageID, "  nuevo  ")

    assertEquals("nuevo", plan.text)
    assertEquals(2, plan.version)
    assertTrue(plan.pending)

    val failed = message().put("isMine", true).put("status", "failed")
    assertTrue(runCatching { ChatEditPolicy.plan(failed, peerID, messageID, "x") }.isFailure)
    assertTrue(runCatching { ChatEditPolicy.plan(message().put("isMine", true), peerID, messageID, "  ") }.isFailure)
    assertTrue(runCatching { ChatEditPolicy.plan(message(), peerID, messageID, "x") }.isFailure)
    assertFalse(ChatEditPolicy.plan(message().put("isMine", true), peerID, messageID, "x").pending)
  }

  @Test
  fun `history before returns the newest page below the bound of one chat`() {
    val other = "bb".repeat(32)
    val messages = JSONArray()
      .put(entry("m1", peerID, 1_000))
      .put(entry("m2", other, 2_000))
      .put(entry("m3", peerID, 3_000))
      .put(entry("m4", peerID, 4_000))

    val page = JSONArray(ProtocolMessageState.historyBefore(messages, peerID, 4_000, 2))

    assertEquals(listOf("m3", "m1"), ids(page))
  }

  @Test
  fun `history before includes messages with the same timestamp below the bound`() {
    val messages = JSONArray()
      .put(entry("m1", peerID, 1_000))
      .put(entry("m2", peerID, 3_000))
      .put(entry("m3", peerID, 3_000))

    assertEquals(listOf("m3", "m2"), ids(JSONArray(ProtocolMessageState.historyBefore(messages, peerID, 3_001, 2))))
    assertEquals(emptyList<String>(), ids(JSONArray(ProtocolMessageState.historyBefore(messages, peerID, 1_000, 10))))
  }

  private fun entry(id: String, chat: String, timestamp: Long): JSONObject = JSONObject()
    .put("id", id)
    .put("upeerId", chat)
    .put("isMine", false)
    .put("content", id)
    .put("status", "delivered")
    .put("timestamp", timestamp)

  private fun ids(page: JSONArray): List<String> = (0 until page.length()).map { page.getJSONObject(it).optString("id") }
}
