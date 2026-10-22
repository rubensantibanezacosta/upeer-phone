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
}
