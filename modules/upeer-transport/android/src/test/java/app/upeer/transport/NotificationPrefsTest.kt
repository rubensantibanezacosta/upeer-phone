package app.upeer.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPrefsTest {
  @Test
  fun defaultsAllowEverything() {
    val prefs = NotificationPrefs.DEFAULT
    assertTrue(prefs.messages)
    assertTrue(prefs.requests)
    assertTrue(prefs.sound)
    assertTrue(prefs.allows(fromKnownContact = true))
    assertTrue(prefs.allows(fromKnownContact = false))
  }

  @Test
  fun knownContactsUseTheMessagesFlag() {
    val prefs = NotificationPrefs(messages = false, requests = true)
    assertFalse(prefs.allows(fromKnownContact = true))
    assertTrue(prefs.allows(fromKnownContact = false))
  }

  @Test
  fun unknownSendersUseTheRequestsFlag() {
    val prefs = NotificationPrefs(messages = true, requests = false)
    assertTrue(prefs.allows(fromKnownContact = true))
    assertFalse(prefs.allows(fromKnownContact = false))
  }

  @Test
  fun parseReadsEveryField() {
    val prefs = NotificationPrefs.parse("""{"messages":false,"requests":true,"sound":false}""")
    assertEquals(NotificationPrefs(messages = false, requests = true, sound = false), prefs)
  }

  @Test
  fun parseFallsBackOnMissingFields() {
    assertEquals(NotificationPrefs(), NotificationPrefs.parse("{}"))
    assertEquals(NotificationPrefs(), NotificationPrefs.parse(""))
  }

  @Test
  fun parseFallsBackOnGarbage() {
    assertEquals(NotificationPrefs.DEFAULT, NotificationPrefs.parse("no-json"))
  }

  @Test
  fun roundTripPreservesValues() {
    val prefs = NotificationPrefs(messages = false, requests = false, sound = false)
    assertEquals(prefs, NotificationPrefs.parse(prefs.toJSON()))
  }
}
