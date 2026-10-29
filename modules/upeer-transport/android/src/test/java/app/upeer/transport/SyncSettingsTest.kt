package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSettingsTest {
  @Test
  fun parseAcceptsEveryPrivacyField() {
    val patch = SyncSettings.parse(
      JSONObject().put("block", "privacy").put("readReceipts", false).put("shareActivity", true),
    )
    assertEquals("privacy", patch?.block)
    assertEquals(mapOf("readReceipts" to false, "shareActivity" to true), patch?.values)
  }

  @Test
  fun parseAcceptsNotificationFields() {
    val patch = SyncSettings.parse(JSONObject().put("block", "notifications").put("sound", false))
    assertEquals(SyncSettings.NOTIFICATIONS, patch?.block)
    assertEquals(mapOf("sound" to false), patch?.values)
  }

  @Test
  fun parseDropsFieldsFromAnotherBlock() {
    val patch = SyncSettings.parse(
      JSONObject().put("block", "privacy").put("readReceipts", false).put("sound", false),
    )
    assertEquals(mapOf("readReceipts" to false), patch?.values)
  }

  @Test
  fun parseIgnoresValuesThatAreNotBooleans() {
    val patch = SyncSettings.parse(JSONObject().put("block", "privacy").put("readReceipts", "sí"))
    assertNull(patch)
  }

  @Test
  fun parseRejectsUnknownBlock() {
    assertNull(SyncSettings.parse(JSONObject().put("block", "appearance").put("theme", "dark")))
  }

  @Test
  fun parseRejectsEmptyPayload() {
    assertNull(SyncSettings.parse(JSONObject().put("block", "privacy")))
    assertNull(SyncSettings.parse(JSONObject()))
  }

  @Test
  fun payloadFiltersUnknownFields() {
    val payload = SyncSettings.payload("privacy", mapOf("readReceipts" to false, "theme" to true))
    assertEquals("privacy", payload?.optString("block"))
    assertEquals(false, payload?.opt("readReceipts"))
    assertTrue(payload?.has("theme") == false)
  }

  @Test
  fun payloadRejectsEmptyAndUnknownBlocks() {
    assertNull(SyncSettings.payload("privacy", emptyMap()))
    assertNull(SyncSettings.payload("appearance", mapOf("theme" to true)))
  }

  @Test
  fun roundTripKeepsTheBlock() {
    val payload = SyncSettings.payload(SyncSettings.NOTIFICATIONS, mapOf("messages" to true, "sound" to false))
    val patch = SyncSettings.parse(payload!!)
    assertEquals(SyncSettings.NOTIFICATIONS, patch?.block)
    assertEquals(mapOf("messages" to true, "sound" to false), patch?.values)
  }

  @Test
  fun entityForABooleanBlockIsTheBlockItself() {
    assertEquals("privacy", SyncSettings.entity(JSONObject().put("block", "privacy")))
    assertEquals("notifications", SyncSettings.entity(JSONObject().put("block", "notifications")))
  }

  @Test
  fun entityForRetentionCarriesTheChat() {
    assertEquals(
      "retention:chat-1",
      SyncSettings.entity(JSONObject().put("block", "retention").put("chatId", "chat-1")),
    )
  }

  @Test
  fun entityRejectsUnknownBlocksAndEmptyChats() {
    assertNull(SyncSettings.entity(JSONObject().put("block", "appearance")))
    assertNull(SyncSettings.entity(JSONObject().put("block", "retention")))
    assertNull(SyncSettings.entity(JSONObject().put("block", "retention").put("chatId", "   ")))
    assertNull(SyncSettings.entity(JSONObject().put("block", "retention").put("chatId", "x".repeat(201))))
  }

  @Test
  fun parseRetentionResolvesThePreset() {
    val patch = SyncSettings.parseRetention(
      JSONObject().put("block", "retention").put("chatId", "chat-1").put("retention", "7d"),
    )
    assertEquals("chat-1", patch?.chatId)
    assertEquals("7d", patch?.retention)
  }

  @Test
  fun parseRetentionNormalizesAnUnknownPreset() {
    val patch = SyncSettings.parseRetention(
      JSONObject().put("block", "retention").put("chatId", "chat-1").put("retention", "dos días"),
    )
    assertEquals(MessageRetention.OFF, patch?.retention)
  }

  @Test
  fun parseRetentionRejectsAnotherBlock() {
    assertNull(SyncSettings.parseRetention(JSONObject().put("block", "privacy")))
  }

  @Test
  fun retentionPayloadRoundTrips() {
    val payload = SyncSettings.retentionPayload("chat-9", "24h")
    assertEquals("retention", payload?.optString("block"))
    assertEquals("chat-9", payload?.optString("chatId"))
    val patch = SyncSettings.parseRetention(payload!!)
    assertEquals("chat-9", patch?.chatId)
    assertEquals("24h", patch?.retention)
  }

  @Test
  fun retentionPayloadRejectsAnEmptyChat() {
    assertNull(SyncSettings.retentionPayload("  ", "24h"))
  }

  @Test
  fun retentionIsAValidBlock() {
    assertTrue(SyncSettings.RETENTION in SyncSettings.blocks())
    assertTrue(SyncSettings.isBooleanBlock(SyncSettings.RETENTION).not())
  }

  @Test
  fun chatFlagsAreTheirOwnEntity() {
    assertEquals(
      "chat:grp-1",
      SyncSettings.entity(JSONObject().put("block", "chat").put("chatId", "grp-1")),
    )
  }

  @Test
  fun chatIsARemoteBlock() {
    assertTrue(SyncSettings.isRemoteBlock(SyncSettings.CHAT))
    assertTrue(SyncSettings.isRemoteBlock(SyncSettings.PRIVACY).not())
    assertTrue(SyncSettings.isRemoteBlock(SyncSettings.RETENTION).not())
    assertTrue(SyncSettings.isRemoteBlock("appearance").not())
  }

  @Test
  fun chatPayloadKeepsOnlyKnownFlags() {
    val payload = SyncSettings.chatPayload(
      "grp-2",
      mapOf("muted" to true, "unread" to false, "chatId" to "otro", "theme" to "dark"),
    )
    assertEquals("chat", payload?.optString("block"))
    assertEquals("grp-2", payload?.optString("chatId"))
    assertEquals(true, payload?.opt("muted"))
    assertEquals(false, payload?.opt("unread"))
    assertTrue(payload?.has("theme") == false)
  }

  @Test
  fun chatPayloadNeedsAFlagAndAChat() {
    assertNull(SyncSettings.chatPayload("grp-3", emptyMap()))
    assertNull(SyncSettings.chatPayload("grp-3", mapOf("theme" to "dark")))
    assertNull(SyncSettings.chatPayload("  ", mapOf("muted" to true)))
  }
}
