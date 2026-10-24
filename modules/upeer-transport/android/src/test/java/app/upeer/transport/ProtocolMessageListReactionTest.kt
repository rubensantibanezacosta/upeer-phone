package app.upeer.transport

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolMessageListReactionTest {
  private val state = JSONObject().put("messages", JSONArray())
  private var persisted = 0

  private val messages = ProtocolMessageList({ state }, Any(), { persisted += 1 })

  @Test
  fun `local reaction records the own identity`() {
    val id = seed(PEER, isMine = true)
    assertTrue(messages.reactLocally(id, PEER, ME, "👍", false))
    assertEquals(listOf(ME), actors(id, "👍"))
    assertEquals(1, persisted)
  }

  @Test
  fun `remote reaction records the sender`() {
    val id = seed(PEER, isMine = true)
    assertTrue(messages.applyReaction(id, PEER, "❤️", false))
    assertEquals(listOf(PEER), actors(id, "❤️"))
  }

  @Test
  fun `remote reaction for another conversation is rejected`() {
    val id = seed(PEER, isMine = true)
    assertFalse(messages.applyReaction(id, "b".repeat(64), "👍", false))
    assertEquals(0, persisted)
  }

  @Test
  fun `local reaction for another conversation is rejected`() {
    val id = seed(PEER, isMine = true)
    assertFalse(messages.reactLocally(id, "b".repeat(64), ME, "👍", false))
    assertEquals(0, persisted)
  }

  @Test
  fun `deleting a peer message removes it from the list`() {
    val id = seed(PEER, isMine = false)
    assertTrue(messages.delete(id, PEER))
    assertEquals(0, state.getJSONArray("messages").length())
  }

  @Test
  fun `a peer cannot delete a local message`() {
    val id = seed(PEER, isMine = true)
    assertFalse(messages.delete(id, PEER))
    assertEquals(1, state.getJSONArray("messages").length())
  }

  @Test
  fun `local delete requires the matching conversation`() {
    val id = seed(PEER, isMine = true)
    assertFalse(messages.deleteLocally(id, "b".repeat(64)))
    assertTrue(messages.deleteLocally(id, PEER))
  }

  private fun seed(upeerId: String, isMine: Boolean): String {
    val id = UUID.randomUUID().toString()
    state.getJSONArray("messages").put(
      JSONObject().put("id", id).put("upeerId", upeerId).put("isMine", isMine).put("content", "hola").put("status", "sent"),
    )
    return id
  }

  private fun actors(messageID: String, emoji: String): List<String> {
    val message = ProtocolMessageState.find(state.getJSONArray("messages"), messageID)
    val values = message?.getJSONObject("reactions")?.getJSONArray(emoji) ?: return emptyList()
    return List(values.length()) { values.getString(it) }
  }

  private companion object {
    private val PEER = "a".repeat(64)
    private val ME = "c".repeat(64)
  }
}
