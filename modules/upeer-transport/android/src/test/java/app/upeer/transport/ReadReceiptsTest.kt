package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadReceiptsTest {
  private val messageID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

  @Test
  fun `data carries type id and timestamp`() {
    val data = ReadReceipts.data(messageID, 1_700_000_000_000)
    assertEquals("READ", data.optString("type"))
    assertEquals(messageID, data.optString("id"))
    assertEquals(1_700_000_000_000, data.optLong("timestamp"))
  }

  @Test
  fun `data never signs itself`() {
    val data = ReadReceipts.data(messageID, 1L)
    assertEquals(false, data.has("signature"))
    assertEquals(false, data.has("senderUpeerId"))
  }

  @Test
  fun `messageId accepts a uuid`() {
    assertEquals(messageID, ReadReceipts.messageID(JSONObject().put("id", messageID)))
  }

  @Test
  fun `messageId rejects junk`() {
    assertNull(ReadReceipts.messageID(JSONObject()))
    assertNull(ReadReceipts.messageID(JSONObject().put("id", "no-es-un-uuid")))
    assertNull(ReadReceipts.messageID(JSONObject().put("id", "")))
  }
}
