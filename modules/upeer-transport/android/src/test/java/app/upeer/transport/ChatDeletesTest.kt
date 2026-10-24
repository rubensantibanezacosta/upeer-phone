package app.upeer.transport

import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDeletesTest {
  private fun message(isMine: Boolean): JSONObject = JSONObject()
    .put("id", UUID.randomUUID().toString())
    .put("upeerId", PEER)
    .put("isMine", isMine)
    .put("content", "hola")

  @Test
  fun `sender can delete its own message`() {
    assertTrue(ChatDeletes.deletable(message(false), PEER))
    assertTrue(ChatDeletes.deletable(message(false), PEER.uppercase()))
  }

  @Test
  fun `a peer cannot delete a local message`() {
    assertFalse(ChatDeletes.deletable(message(true), PEER))
  }

  @Test
  fun `a third party cannot delete a message from another conversation`() {
    assertFalse(ChatDeletes.deletable(message(false), "b".repeat(64)))
  }

  @Test
  fun `packet data carries the protocol fields`() {
    val data = ChatDeletes.data("00000000-0000-4000-8000-000000000000", 1_700_000_000_000)
    assertEquals("CHAT_DELETE", data.getString("type"))
    assertEquals(1_700_000_000_000, data.getLong("timestamp"))
    assertEquals("00000000-0000-4000-8000-000000000000", ChatDeletes.messageID(data))
  }

  @Test
  fun `invalid message ids are rejected`() {
    assertNull(ChatDeletes.messageID(JSONObject().put("msgId", "nope")))
    assertNull(ChatDeletes.messageID(JSONObject()))
  }

  private companion object {
    private val PEER = "a".repeat(64)
  }
}
