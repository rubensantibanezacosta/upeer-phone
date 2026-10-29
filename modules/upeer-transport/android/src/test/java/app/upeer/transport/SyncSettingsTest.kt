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
}
