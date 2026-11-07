package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationsPerformanceTest {
  private fun chat(index: Int) = "chat-$index"

  private fun messages(chats: Int, perChat: Int): JSONArray {
    val list = JSONArray()
    var timestamp = 1_000L
    for (chat in 0 until chats) {
      for (message in 0 until perChat) {
        list.put(
          JSONObject()
            .put("id", "m-$chat-$message")
            .put("upeerId", chat(chat))
            .put("isMine", message % 2 == 0)
            .put("content", "hola")
            .put("status", "delivered")
            .put("timestamp", timestamp++),
        )
      }
    }
    return list
  }

  private fun elapsed(messages: JSONArray): Long {
    val started = System.nanoTime()
    ProtocolConversations.build(messages, JSONObject())
    return (System.nanoTime() - started) / 1_000_000
  }

  @Test
  fun `cuenta los mensajes sin leer por conversacion`() {
    val page = messages(chats = 2, perChat = 3)

    val built = JSONArray(ProtocolConversations.build(page, JSONObject()))

    val first = (0 until built.length()).map { built.getJSONObject(it) }.first { it.optString("upeerId") == chat(0) }
    assertEquals(1, first.optInt("unread"))
  }

  @Test
  fun `contar sin leer da lo mismo que recorrer la lista entera por conversacion`() {
    val page = messages(chats = 5, perChat = 20)
    val built = JSONArray(ProtocolConversations.build(page, JSONObject()))
    val ids = (0 until built.length()).map { built.getJSONObject(it).optString("upeerId") }

    for (id in ids) {
      var expected = 0
      for (index in 0 until page.length()) {
        val candidate = page.optJSONObject(index)
        if (candidate.optString("upeerId") == id &&
          !candidate.optBoolean("isMine") &&
          !candidate.optBoolean("deleted") &&
          candidate.optString("status") == "delivered"
        ) expected++
      }
      val entry = (0 until built.length()).map { built.getJSONObject(it) }.first { it.optString("upeerId") == id }
      assertEquals(expected, entry.optInt("unread"))
    }
  }

  @Test
  fun `con muchas conversaciones no se recorre la lista una vez por cada una`() {
    val page = messages(chats = 50, perChat = 20)

    val elapsed = measure(page)

    assertTrue("50 conversaciones tardaron ${elapsed}ms", elapsed < 500)
  }

  private fun measure(messages: JSONArray): Long {
    repeat(3) { ProtocolConversations.build(messages, JSONObject()) }
    return elapsed(messages)
  }
}
