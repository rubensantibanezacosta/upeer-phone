package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncReactionTest {
  private val actor = "da4163f3528710db1adeca2211274d217728845044c70b912759e856493d1834"
  private val messageId = "9a02f543-f997-4707-8c0c-0437c32c3bc0"

  @Test
  fun `payload uses the desktop field names`() {
    val payload = SyncReaction.payload(messageId, actor, "👍", false)

    assertEquals(messageId, payload.optString("messageId"))
    assertEquals(actor, payload.optString("upeerId"))
    assertEquals("👍", payload.optString("emoji"))
    assertEquals(false, payload.optBoolean("remove"))
  }

  @Test
  fun `payload lowercases the actor`() {
    val payload = SyncReaction.payload(messageId, actor.uppercase(), "👍", true)

    assertEquals(actor, payload.optString("upeerId"))
    assertEquals(true, payload.optBoolean("remove"))
  }

  @Test
  fun `parse reads what the desktop sends`() {
    val parsed = SyncReaction.parse(JSONObject().put("messageId", messageId).put("upeerId", actor).put("emoji", "❤️").put("remove", true))

    assertEquals(SyncReactionFields(messageId, actor, "❤️", true), parsed)
  }

  @Test
  fun `parse rejects a missing actor or message`() {
    assertNull(SyncReaction.parse(JSONObject().put("messageId", messageId).put("emoji", "👍")))
    assertNull(SyncReaction.parse(JSONObject().put("upeerId", actor).put("emoji", "👍")))
    assertNull(SyncReaction.parse(JSONObject().put("messageId", messageId).put("upeerId", "corto").put("emoji", "👍")))
  }

  @Test
  fun `parse rejects an empty emoji`() {
    assertNull(SyncReaction.parse(JSONObject().put("messageId", messageId).put("upeerId", actor).put("emoji", "")))
  }
}
