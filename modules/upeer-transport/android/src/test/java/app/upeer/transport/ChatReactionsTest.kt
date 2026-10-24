package app.upeer.transport

import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReactionsTest {
  private fun message(): JSONObject = JSONObject()
    .put("id", UUID.randomUUID().toString())
    .put("upeerId", PEER)
    .put("isMine", false)
    .put("content", "hola")

  @Test
  fun `adds one reaction per actor`() {
    val message = message()
    assertTrue(ChatReactions.apply(message, PEER, "👍", false))
    assertEquals(listOf(PEER), actors(message, "👍"))
  }

  @Test
  fun `same actor twice is rejected`() {
    val message = message()
    assertTrue(ChatReactions.apply(message, PEER, "👍", false))
    assertFalse(ChatReactions.apply(message, PEER.uppercase(), "👍", false))
    assertEquals(1, actors(message, "👍").size)
  }

  @Test
  fun `remove deletes the actor and the empty emoji key`() {
    val message = message()
    ChatReactions.apply(message, PEER, "👍", false)
    assertTrue(ChatReactions.apply(message, PEER, "👍", true))
    assertNull(message.optJSONObject("reactions"))
  }

  @Test
  fun `remove of an unknown reaction is rejected`() {
    val message = message()
    assertFalse(ChatReactions.apply(message, PEER, "👍", true))
  }

  @Test
  fun `distinct emoji cap is enforced`() {
    val message = message()
    val emojis = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t")
    emojis.forEach { assertTrue(ChatReactions.apply(message, PEER, it, false)) }
    assertEquals(ChatReactions.MAX_DISTINCT, message.getJSONObject("reactions").length())
    assertFalse(ChatReactions.apply(message, PEER, "u", false))
  }

  @Test
  fun `blank and oversized emoji are rejected`() {
    val message = message()
    assertFalse(ChatReactions.apply(message, PEER, " ", false))
    assertFalse(ChatReactions.apply(message, PEER, "x".repeat(ChatReactions.MAX_EMOJI_LENGTH + 1), false))
    assertTrue(ChatReactions.validEmoji("👍"))
  }

  @Test
  fun `packet data carries the protocol fields`() {
    val data = ChatReactions.data("00000000-0000-4000-8000-000000000000", "👍", true, 1_700_000_000_000)
    assertEquals("CHAT_REACTION", data.getString("type"))
    assertEquals("👍", data.getString("emoji"))
    assertTrue(data.getBoolean("remove"))
    assertEquals("00000000-0000-4000-8000-000000000000", ChatReactions.messageID(data))
  }

  private fun actors(message: JSONObject, emoji: String): List<String> {
    val values = message.getJSONObject("reactions").getJSONArray(emoji)
    return List(values.length()) { values.getString(it) }
  }

  private companion object {
    private val PEER = "a".repeat(64)
  }
}
