package app.upeer.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolPreferencesTest {
  private val state = JSONObject()
  private val preferences = ProtocolPreferences({ state }, Any(), {})

  @Test
  fun applyingPrivacyChangesTheValues() {
    val changed = preferences.applySyncSettings(
      SyncSettings.PRIVACY,
      mapOf("readReceipts" to false, "shareActivity" to false),
      1_000L,
    )
    assertTrue(changed)
    assertFalse(preferences.readReceipts())
    assertFalse(preferences.activitySharing())
  }

  @Test
  fun applyingTheSameValuesReportsNoChange() {
    val changed = preferences.applySyncSettings(
      SyncSettings.PRIVACY,
      mapOf("readReceipts" to true),
      1_000L,
    )
    assertFalse(changed)
    assertTrue(preferences.readReceipts())
  }

  @Test
  fun applyingNotificationsKeepsUntouchedFields() {
    preferences.applySyncSettings(SyncSettings.NOTIFICATIONS, mapOf("sound" to false), 1_000L)
    val prefs = preferences.notifications()
    assertTrue(prefs.messages)
    assertTrue(prefs.requests)
    assertFalse(prefs.sound)
  }

  @Test
  fun theClockRecordsTheRemoteTimestamp() {
    preferences.applySyncSettings(SyncSettings.PRIVACY, mapOf("readReceipts" to false), 4_242L)
    assertEquals(4_242L, preferences.settingsClock(SyncSettings.PRIVACY))
  }

  @Test
  fun anOlderUpdateLoses() {
    preferences.applySyncSettings(SyncSettings.PRIVACY, mapOf("readReceipts" to false), 5_000L)
    assertFalse(preferences.claimSettingsUpdate(SyncSettings.PRIVACY, 5_000L))
    assertFalse(preferences.claimSettingsUpdate(SyncSettings.PRIVACY, 4_999L))
    assertTrue(preferences.claimSettingsUpdate(SyncSettings.PRIVACY, 5_001L))
  }

  @Test
  fun clocksAreIndependentPerBlock() {
    preferences.applySyncSettings(SyncSettings.PRIVACY, mapOf("readReceipts" to false), 9_000L)
    assertTrue(preferences.claimSettingsUpdate(SyncSettings.NOTIFICATIONS, 1L))
  }

  @Test
  fun settersReportWhetherTheValueChanged() {
    assertFalse(preferences.setReadReceipts(true))
    assertTrue(preferences.setReadReceipts(false))
    assertFalse(preferences.setActivitySharing(true))
    assertTrue(preferences.setActivitySharing(false))
  }

  @Test
  fun privacyValuesReflectTheStore() {
    preferences.setReadReceipts(false)
    preferences.setActivitySharing(false)
    assertEquals(
      mapOf("readReceipts" to false, "shareActivity" to false),
      preferences.privacyValues(),
    )
  }
}
