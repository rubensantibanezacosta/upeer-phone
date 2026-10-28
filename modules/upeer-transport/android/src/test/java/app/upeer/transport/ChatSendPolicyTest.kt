package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSendPolicyTest {
  private val peer = "a".repeat(64)
  private val messageID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

  @Test
  fun `trims the text and accepts a quoted message`() {
    assertEquals("hola", ChatSendPolicy.text(peer, "  hola  ", messageID))
  }

  @Test
  fun `accepts an empty reply target`() {
    assertEquals("hola", ChatSendPolicy.text(peer, "hola", ""))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a peer id that is not 64 hex characters`() {
    ChatSendPolicy.text("no-es-un-id", "hola", "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a quoted message that is not a uuid`() {
    ChatSendPolicy.text(peer, "hola", "respuesta")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects an empty text`() {
    ChatSendPolicy.text(peer, "   ", "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a text above the limit`() {
    ChatSendPolicy.text(peer, "a".repeat(ChatSendPolicy.MAX_MESSAGE_BYTES + 1), "")
  }

  @Test
  fun `accepts a group conversation id`() {
    assertEquals("hola", ChatSendPolicy.groupText("grp-1a2b", "  hola ", ""))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects a group id without prefix`() {
    ChatSendPolicy.groupText(peer, "hola", "")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects an empty group text`() {
    ChatSendPolicy.groupText("grp-1a2b", "  ", "")
  }
}
