package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSearchTest {
  private val alice = "aa".repeat(32)
  private val group = "grp-1234"

  private fun seed(
    id: String,
    chat: String,
    content: String,
    timestamp: Long,
    isMine: Boolean = false,
    deleted: Boolean = false,
  ): JSONObject = JSONObject()
    .put("id", id)
    .put("upeerId", chat)
    .put("content", content)
    .put("timestamp", timestamp)
    .put("isMine", isMine)
    .put("deleted", deleted)

  private fun messages(vararg entries: JSONObject): JSONArray {
    val array = JSONArray()
    entries.forEach(array::put)
    return array
  }

  @Test
  fun `matches ignoring case and accents`() {
    val found = MessageSearch.matches("cancion", 10, 0, messages(seed("m1", alice, "Canción de cuna", 1_000)))

    assertEquals(listOf("m1"), found.map { it.optString("id") })
  }

  @Test
  fun `matches the accent in the query too`() {
    val found = MessageSearch.matches("cancIÓN", 10, 0, messages(seed("m1", alice, "cancion de cuna", 1_000)))

    assertEquals(1, found.size)
  }

  @Test
  fun `searches inside system messages without the marker`() {
    val found = MessageSearch.matches("videollamada", 10, 0, messages(seed("m1", alice, "__SYS__|Videollamada perdida", 1_000)))

    assertEquals(listOf("m1"), found.map { it.optString("id") })
  }

  @Test
  fun `does not match the system marker itself`() {
    assertTrue(MessageSearch.matches("__sys__", 10, 0, messages(seed("m1", alice, "__SYS__|Videollamada perdida", 1_000))).isEmpty())
  }

  @Test
  fun `ignores attachment envelopes`() {
    val envelope = JSONObject().put("fileId", "f1").put("name", "informe final.pdf").toString()

    assertTrue(MessageSearch.matches("informe", 10, 0, messages(seed("m1", alice, envelope, 1_000))).isEmpty())
  }

  @Test
  fun `ignores deleted and empty messages`() {
    val found = MessageSearch.matches(
      "hola",
      10,
      0,
      messages(
        seed("m1", alice, "hola", 1_000, deleted = true),
        seed("m2", alice, "   ", 2_000),
        seed("m3", alice, "hola de verdad", 3_000),
      ),
    )

    assertEquals(listOf("m3"), found.map { it.optString("id") })
  }

  @Test
  fun `returns the newest matches first with their chat`() {
    val found = MessageSearch.matches(
      "hola",
      10,
      0,
      messages(
        seed("m1", alice, "hola antiguo", 1_000),
        seed("m2", group, "hola del grupo", 3_000, isMine = true),
        seed("m3", alice, "hola reciente", 2_000),
      ),
    )

    assertEquals(listOf("m2", "m3", "m1"), found.map { it.optString("id") })
    assertEquals(group, found[0].optString("chatUpeerId"))
    assertTrue(found[0].optBoolean("isMine"))
    assertEquals(3_000L, found[0].optLong("timestamp"))
  }

  @Test
  fun `pages with offset and limit`() {
    val all = messages(
      seed("m1", alice, "hola 1", 1_000),
      seed("m2", alice, "hola 2", 2_000),
      seed("m3", alice, "hola 3", 3_000),
      seed("m4", alice, "hola 4", 4_000),
    )

    assertEquals(listOf("m4", "m3"), MessageSearch.matches("hola", 2, 0, all).map { it.optString("id") })
    assertEquals(listOf("m2", "m1"), MessageSearch.matches("hola", 2, 2, all).map { it.optString("id") })
    assertEquals(listOf("m1"), MessageSearch.matches("hola", 2, 3, all).map { it.optString("id") })
    assertTrue(MessageSearch.matches("hola", 2, 9, all).isEmpty())
  }

  @Test
  fun `an empty query returns nothing`() {
    val all = messages(seed("m1", alice, "hola", 1_000))

    assertTrue(MessageSearch.matches("", 10, 0, all).isEmpty())
    assertTrue(MessageSearch.matches("   ", 10, 0, all).isEmpty())
  }

  @Test
  fun `clamps the limit to the maximum`() {
    val entries = (1..600).map { index -> seed("m$index", alice, "hola $index", index.toLong() * 1_000) }.toTypedArray()

    assertEquals(MessageSearch.MAX_RESULTS, MessageSearch.matches("hola", 5_000, 0, messages(*entries)).size)
  }

  @Test
  fun `keeps the original content so the client can build the excerpt`() {
    val found = MessageSearch.matches("hola", 10, 0, messages(seed("m1", alice, "  Hola\n\nmundo  ", 1_000)))

    assertEquals("  Hola\n\nmundo  ", found[0].optString("content"))
  }

  @Test
  fun `searchable text drops empty system envelopes`() {
    assertNull(MessageSearch.searchableText("__SYS__|"))
    assertNull(MessageSearch.searchableText("   "))
    assertNull(MessageSearch.searchableText("{\"fileId\":\"f1\"}"))
    assertEquals("Hola", MessageSearch.searchableText("__SYS__|Hola"))
  }
}
