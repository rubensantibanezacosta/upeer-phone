package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolMessageSelectionTest {
  private val peer = "aa".repeat(32)

  @Test
  fun `select by ids returns the requested messages newest first`() {
    val messages = JSONArray()
      .put(message("a", 1L))
      .put(message("b", 2L))
      .put(message("c", 3L))

    val selected = JSONArray(ProtocolMessageState.selectByIds(messages, listOf("a", "c")))

    assertEquals(listOf("c", "a"), ids(selected))
  }

  @Test
  fun `select by ids ignores missing messages`() {
    val messages = JSONArray().put(message("a", 1L))

    val selected = JSONArray(ProtocolMessageState.selectByIds(messages, listOf("a", "z")))

    assertEquals(listOf("a"), ids(selected))
  }

  @Test
  fun `select by ids without ids returns an empty list`() {
    val messages = JSONArray().put(message("a", 1L))

    assertEquals("[]", ProtocolMessageState.selectByIds(messages, emptyList()))
    assertEquals("[]", ProtocolMessageState.selectByIds(messages, listOf("", " ")))
  }

  @Test
  fun `select by ids honours the limit`() {
    val messages = JSONArray()
      .put(message("a", 1L))
      .put(message("b", 2L))

    val selected = JSONArray(ProtocolMessageState.selectByIds(messages, listOf("a", "b"), 1))

    assertEquals(listOf("b"), ids(selected))
  }

  private fun ids(array: JSONArray): List<String> {
    val result = mutableListOf<String>()
    for (index in 0 until array.length()) result.add(array.getJSONObject(index).optString("id"))
    return result
  }

  private fun message(id: String, timestamp: Long): JSONObject = JSONObject()
    .put("id", id)
    .put("upeerId", peer)
    .put("content", "hola")
    .put("timestamp", timestamp)
    .put("isMine", true)
}
