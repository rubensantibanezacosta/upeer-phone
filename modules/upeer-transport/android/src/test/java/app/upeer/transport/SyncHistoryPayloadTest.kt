package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHistoryPayloadTest {
  private val peer = "cc".repeat(32)
  private val deviceId = "bb".repeat(32)
  private val messageId = "1b4e28ba-2fa1-11d2-883f-0016d3cca427"

  @Test
  fun `desktop payload carries the desktop field names`() {
    val payload = SyncHistoryPayload.desktop(local(occurredAt = 1_700_000_000_000L))

    assertEquals(messageId, payload.optString("id"))
    assertEquals(peer, payload.optString("chatUpeerId"))
    assertEquals("hola", payload.optString("message"))
    assertEquals(1_700_000_000_000L, payload.optLong("timestamp"))
    assertTrue(payload.optBoolean("isMine"))
    assertFalse(payload.has("content"))
  }

  @Test
  fun `desktop payload is accepted back by the local mapper`() {
    val original = local(occurredAt = 1_700_000_000_000L)
    val restored = SyncHistoryPayload.local(SyncHistoryPayload.desktop(original))

    assertEquals(original.optString("id"), restored?.optString("id"))
    assertEquals(original.optString("upeerId"), restored?.optString("upeerId"))
    assertEquals(original.optString("content"), restored?.optString("content"))
    assertEquals(original.optLong("timestamp"), restored?.optLong("timestamp"))
    assertEquals(original.optBoolean("isMine"), restored?.optBoolean("isMine"))
  }

  @Test
  fun `local mapper rejects payloads without identity or timestamp`() {
    assertNull(SyncHistoryPayload.local(JSONObject().put("id", messageId).put("chatUpeerId", peer)))
    assertNull(SyncHistoryPayload.local(JSONObject().put("id", messageId).put("chatUpeerId", "corta").put("timestamp", 5L)))
    assertNull(SyncHistoryPayload.local(JSONObject().put("chatUpeerId", peer).put("timestamp", 5L)))
  }

  @Test
  fun `local mapper keeps deletions and edits`() {
    val payload = SyncHistoryPayload.desktop(local(occurredAt = 1L))
      .put("isDeleted", true)
      .put("isEdited", true)

    val restored = SyncHistoryPayload.local(payload)

    assertTrue(restored!!.optBoolean("deleted"))
    assertTrue(restored.optBoolean("isEdited"))
  }

  @Test
  fun `unknown status falls back to sent`() {
    val payload = SyncHistoryPayload.desktop(local(occurredAt = 1L)).put("status", "inventado")

    assertEquals("sent", SyncHistoryPayload.local(payload)?.optString("status"))
  }

  @Test
  fun `event id is stable and valid for the event log`() {
    val entry = SyncHistoryPayload.eventId(deviceId, local(occurredAt = 1_700_000_000_000L))

    assertEquals("history-$deviceId-$messageId", entry)
    assertTrue(SyncPackets.validEventId(entry!!))
  }

  @Test
  fun `cursor round trips through parse`() {
    val cursor = SyncHistoryPayload.cursor(local(occurredAt = 1_700_000_000_000L))

    assertEquals("1700000000000:$messageId", cursor)
    assertEquals(1_700_000_000_000L to messageId, SyncHistoryPayload.parseCursor(cursor))
  }

  @Test
  fun `invalid cursors restart from the beginning`() {
    assertEquals(0L to "", SyncHistoryPayload.parseCursor(null))
    assertEquals(0L to "", SyncHistoryPayload.parseCursor("sin-dos-puntos"))
    assertEquals(0L to "", SyncHistoryPayload.parseCursor(":1234"))
    assertEquals(0L to "", SyncHistoryPayload.parseCursor("no-numerico:id"))
  }

  @Test
  fun `attachment content is recognised by its type`() {
    assertTrue(SyncHistoryPayload.isAttachment("""{"type":"file","transferId":"abc","fileName":"a.pdf"}"""))
    assertTrue(SyncHistoryPayload.isAttachment("""  {"type":"image","fileId":"abc"}"""))
    assertFalse(SyncHistoryPayload.isAttachment("hola"))
    assertFalse(SyncHistoryPayload.isAttachment("""{"type":"contact_card"}"""))
    assertFalse(SyncHistoryPayload.isAttachment("{"))
  }

  private fun local(occurredAt: Long): JSONObject = JSONObject()
    .put("id", messageId)
    .put("upeerId", peer)
    .put("isMine", true)
    .put("content", "hola")
    .put("status", "delivered")
    .put("timestamp", occurredAt)
    .put("version", 3)
}

class SyncHistoryStateTest {
  private val state = JSONObject()
  private var persisted = 0
  private val cursors = SyncHistoryState({ state }, Any(), { persisted += 1 })
  private val deviceId = "bb".repeat(32)

  @Test
  fun `progress starts empty`() {
    assertEquals(SyncHistoryProgress(null, false), cursors.progress(deviceId))
  }

  @Test
  fun `saves and reloads the cursor`() {
    cursors.save(deviceId, "17:id", false)
    assertEquals(SyncHistoryProgress("17:id", false), cursors.progress(deviceId))
    assertEquals(1, persisted)

    cursors.save(deviceId, null, true)
    assertEquals(SyncHistoryProgress(null, true), cursors.progress(deviceId))
  }

  @Test
  fun `ignores malformed device ids`() {
    cursors.save("corta", "17:id", true)
    assertEquals(0, persisted)
    assertEquals(SyncHistoryProgress(null, false), cursors.progress("corta"))
  }

  @Test
  fun `stores history outside the sync bucket`() {
    cursors.save(deviceId, "17:id", false)
    assertTrue(state.has("history"))
    assertFalse(state.has("sync"))
    assertEquals("17:id", state.getJSONObject("history").getJSONObject(deviceId).optString("cursor"))
  }
}
