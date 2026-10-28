package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRetentionTest {
  private val chat = "aa".repeat(32)

  @Test
  fun `unknown presets fall back to off`() {
    assertEquals(MessageRetention.OFF, MessageRetention.resolve(null))
    assertEquals(MessageRetention.OFF, MessageRetention.resolve("1h"))
    assertEquals("7d", MessageRetention.resolve("7d"))
  }

  @Test
  fun `durations follow the presets`() {
    assertEquals(0L, MessageRetention.durationMs("off"))
    assertEquals(24L * 60L * 60L * 1000L, MessageRetention.durationMs("24h"))
  }

  @Test
  fun `notice is only shown for enabled presets`() {
    assertNull(MessageRetention.notice("off"))
    assertNull(MessageRetention.notice("nope"))
    assertEquals("Los mensajes nuevos se borran a los 24 h.", MessageRetention.notice("24h"))
  }

  @Test
  fun `sanitize drops off entries and unknown values`() {
    val map = MessageRetention.sanitize(mapOf(chat to "24h", "other" to "off", "third" to "raro"))

    assertEquals(mapOf(chat to "24h"), map)
  }

  @Test
  fun `parse ignores invalid payloads`() {
    assertTrue(MessageRetention.parse(null).isEmpty())
    assertTrue(MessageRetention.parse(JSONObject().put(chat, "off")).isEmpty())
    assertEquals(mapOf(chat to "90d"), MessageRetention.parse(JSONObject().put(chat, "90d")))
  }

  @Test
  fun `expires at requires positive inputs`() {
    assertNull(MessageRetention.expiresAt(0L, 1000L))
    assertNull(MessageRetention.expiresAt(1000L, 0L))
    assertEquals(2000L, MessageRetention.expiresAt(1000L, 1000L))
  }

  @Test
  fun `new messages are stamped with the chat retention`() {
    val messages = JSONArray()
    val message = message("m1", 1_000L)

    MessageRetention.stamp(messages, message, "24h", 1_000L)

    assertEquals(1_000L + MessageRetention.durationMs("24h"), message.optLong("expiresAt"))
  }

  @Test
  fun `messages are not stamped when retention is off`() {
    val message = message("m1", 1_000L)

    MessageRetention.stamp(JSONArray().put(message), message, "off", 1_000L)

    assertFalse(message.has("expiresAt"))
  }

  @Test
  fun `existing expiry survives a later upsert`() {
    val stored = message("m1", 1_000L).put("expiresAt", 5_000L)
    val messages = JSONArray().put(stored)
    val incoming = message("m1", 1_000L)

    MessageRetention.stamp(messages, incoming, "90d", 9_000L)

    assertEquals(5_000L, incoming.optLong("expiresAt"))
  }

  @Test
  fun `upsert does not stamp old messages when the chat enables retention`() {
    val stored = message("m1", 1_000L)
    val messages = JSONArray().put(stored)
    val incoming = message("m1", 1_000L).put("status", "read")

    MessageRetention.stamp(messages, incoming, "24h", 9_000L)

    assertFalse(incoming.has("expiresAt"))
  }

  @Test
  fun `expired check ignores missing or future stamps`() {
    assertFalse(MessageRetention.isExpired(0L, 10_000L))
    assertFalse(MessageRetention.isExpired(11_000L, 10_000L))
    assertTrue(MessageRetention.isExpired(10_000L, 10_000L))
  }

  @Test
  fun `sweep reports nothing when everything is alive`() {
    val messages = JSONArray().put(message("m1", 1_000L)).put(message("m2", 1_000L).put("expiresAt", 500_000L))

    assertNull(MessageRetention.withoutExpired(messages, 10_000L))
  }

  @Test
  fun `sweep drops expired messages and keeps the rest`() {
    val messages = JSONArray()
      .put(message("m1", 1_000L).put("expiresAt", 2_000L))
      .put(message("m2", 1_000L).put("expiresAt", 500_000L))
      .put(message("m3", 1_000L))

    val kept = MessageRetention.withoutExpired(messages, 10_000L)

    assertEquals(listOf("m2", "m3"), ids(kept))
  }

  @Test
  fun `saved messages get stamped through the list`() {
    val state = JSONObject().put("messages", JSONArray())
    val messages = ProtocolMessageList({ state }, Any(), {}, { "24h" })

    messages.save(message("m1", 1_000L))

    val saved = state.getJSONArray("messages").getJSONObject(0)
    assertEquals(1_000L + MessageRetention.durationMs("24h"), saved.optLong("expiresAt"))
  }

  @Test
  fun `snapshot removes expired messages`() {
    val expired = message("old", 1L).put("expiresAt", 2L)
    val alive = message("live", 1L)
    val state = JSONObject().put("messages", JSONArray().put(alive).put(expired))
    val messages = ProtocolMessageList({ state }, Any(), {})

    val page = JSONArray(messages.snapshot(chat, 50))

    assertEquals(listOf("live"), ids(page))
    assertEquals(1, state.getJSONArray("messages").length())
  }

  private fun ids(array: JSONArray?): List<String> {
    val result = mutableListOf<String>()
    for (index in 0 until (array?.length() ?: 0)) {
      result.add(array!!.getJSONObject(index).optString("id"))
    }
    return result
  }

  private fun message(id: String, timestamp: Long): JSONObject = JSONObject()
    .put("id", id)
    .put("upeerId", chat)
    .put("content", "hola")
    .put("timestamp", timestamp)
    .put("isMine", true)
}
