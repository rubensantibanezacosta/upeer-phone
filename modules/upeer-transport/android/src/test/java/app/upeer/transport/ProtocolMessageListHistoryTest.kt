package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolMessageListHistoryTest {
  private val peer = "cc".repeat(32)
  private val state = JSONObject().put("messages", JSONArray())
  private val messages = ProtocolMessageList({ state }, Any(), {})

  @Test
  fun `history page returns the oldest messages first`() {
    seed(3)
    seed(1)
    seed(2)

    val page = messages.historyPage(0L, "", 10)

    assertEquals(listOf(1L, 2L, 3L), page.map { it.optLong("timestamp") })
  }

  @Test
  fun `history page honours the cursor`() {
    seed(1)
    seed(2)
    seed(3)

    val page = messages.historyPage(1L, id(1), 10)

    assertEquals(listOf(2L, 3L), page.map { it.optLong("timestamp") })
  }

  @Test
  fun `history page limits the batch`() {
    seed(1)
    seed(2)
    seed(3)

    assertEquals(2, messages.historyPage(0L, "", 2).size)
  }

  @Test
  fun `history page skips messages without timestamp`() {
    seed(1)
    messages.save(JSONObject().put("id", id(9)).put("upeerId", peer).put("content", "sin fecha").put("isMine", false))

    val page = messages.historyPage(0L, "", 10)

    assertEquals(1, page.size)
    assertEquals(1L, page.first().optLong("timestamp"))
  }

  @Test
  fun `history pending counts every message still unknown to the peer`() {
    seed(1)
    seed(2)
    seed(3)

    assertEquals(3, messages.historyPending(0L, ""))
    assertEquals(2, messages.historyPending(1L, id(1)))
    assertEquals(0, messages.historyPending(3L, id(3)))
  }

  @Test
  fun `history pending ignores messages without timestamp`() {
    seed(1)
    messages.save(JSONObject().put("id", id(9)).put("upeerId", peer).put("content", "sin fecha").put("isMine", false))

    assertEquals(1, messages.historyPending(0L, ""))
  }

  @Test
  fun `upsert keeps the newest messages when the list is full`() {
    val list = JSONArray()
    for (index in 5000 downTo 1) list.put(message(index.toLong()))

    val trimmed = ProtocolMessageState.upsert(list, message(9000))

    assertEquals(500, trimmed.length())
    assertEquals(9000L, trimmed.optJSONObject(0)?.optLong("timestamp"))
    assertTrue(trimmed.optJSONObject(499)?.optLong("timestamp")!! > 4000L)  }

  private fun seed(timestamp: Long) {
    messages.save(message(timestamp))
  }

  private fun message(timestamp: Long): JSONObject = JSONObject()
    .put("id", id(timestamp))
    .put("upeerId", peer)
    .put("isMine", false)
    .put("content", "mensaje $timestamp")
    .put("status", "sent")
    .put("timestamp", timestamp)

  private fun id(timestamp: Long): String = "%08d-0000-0000-0000-000000000000".format(timestamp)
}
