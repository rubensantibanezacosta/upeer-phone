package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetentionAnnouncementTest {
  private val peer = "aa".repeat(32)

  @Test
  fun `known presets survive the round trip`() {
    for (retention in listOf("off", "24h", "7d", "90d")) {
      val packet = RetentionAnnouncement.data(retention)!!
      assertEquals(retention, RetentionAnnouncement.parse(packet))
    }
  }

  @Test
  fun `unknown presets are refused in both directions`() {
    assertNull(RetentionAnnouncement.data("1h"))
    assertNull(RetentionAnnouncement.parse(JSONObject().put("type", "RETENTION_SET").put("retention", "1h")))
    assertNull(RetentionAnnouncement.parse(JSONObject().put("type", "RETENTION_SET")))
    assertNull(RetentionAnnouncement.parse(JSONObject().put("type", "CHAT").put("retention", "24h")))
  }

  @Test
  fun `applying a preset sets it for that chat`() {
    assertEquals(mapOf(peer to "24h"), RetentionAnnouncement.apply(emptyMap(), peer, "24h"))
  }

  @Test
  fun `applying off removes the chat entry`() {
    val current = mapOf(peer to "24h", "bb".repeat(32) to "7d")
    assertEquals(mapOf("bb".repeat(32) to "7d"), RetentionAnnouncement.apply(current, peer, "off"))
  }

  @Test
  fun `applying keeps the other chats untouched`() {
    val other = "bb".repeat(32)
    val next = RetentionAnnouncement.apply(mapOf(other to "7d"), peer, "90d")
    assertEquals(mapOf(other to "7d", peer to "90d"), next)
  }
}
