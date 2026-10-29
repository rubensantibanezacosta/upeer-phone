package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal class ProtocolPreferences(
  private val stateProvider: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun blocked(): List<String> = peers("blocked")

  fun isBlocked(upeerId: String): Boolean = PeerIdList.contains(blocked(), upeerId)

  fun setBlocked(upeerIds: List<String>) = setPeers("blocked", upeerIds)

  fun known(): List<String> = peers("knownContacts")

  fun isKnown(upeerId: String): Boolean = PeerIdList.contains(known(), upeerId)

  fun setKnown(upeerIds: List<String>) = setPeers("knownContacts", upeerIds)

  fun muted(): List<String> = peers("muted")

  fun isMuted(upeerId: String): Boolean = PeerIdList.contains(muted(), upeerId)

  fun setMuted(upeerIds: List<String>) = setPeers("muted", upeerIds)

  fun notifications(): NotificationPrefs = synchronized(lock) {
    NotificationPrefs.parse(stateProvider().optString("notificationPrefs"))
  }

  fun setNotifications(prefs: NotificationPrefs): Boolean {
    synchronized(lock) {
      if (notifications() == prefs) return false
      stateProvider().put("notificationPrefs", prefs.toJSON())
      persist()
      return true
    }
  }

  fun readReceipts(): Boolean = synchronized(lock) {
    stateProvider().optBoolean("readReceipts", true)
  }

  fun setReadReceipts(enabled: Boolean): Boolean {
    synchronized(lock) {
      if (readReceipts() == enabled) return false
      stateProvider().put("readReceipts", enabled)
      persist()
      return true
    }
  }

  fun activitySharing(): Boolean = synchronized(lock) {
    stateProvider().optBoolean("shareActivity", true)
  }

  fun setActivitySharing(enabled: Boolean): Boolean {
    synchronized(lock) {
      if (activitySharing() == enabled) return false
      stateProvider().put("shareActivity", enabled)
      persist()
      return true
    }
  }

  fun settingsClock(block: String): Long = synchronized(lock) {
    stateProvider().optJSONObject("settingsClock")?.optLong(block, 0L) ?: 0L
  }

  fun setSettingsClock(block: String, occurredAt: Long) {
    if (occurredAt <= 0L) return
    synchronized(lock) {
      val clock = stateProvider().optJSONObject("settingsClock") ?: JSONObject()
      clock.put(block, occurredAt)
      stateProvider().put("settingsClock", clock)
      persist()
    }
  }

  fun claimSettingsUpdate(block: String, occurredAt: Long): Boolean =
    occurredAt > 0L && occurredAt > settingsClock(block)

  fun applySyncSettings(block: String, values: Map<String, Boolean>, occurredAt: Long): Boolean {
    val changed = when (block) {
      SyncSettings.PRIVACY -> applyPrivacy(values)
      SyncSettings.NOTIFICATIONS -> applyNotifications(values)
      else -> false
    }
    setSettingsClock(block, occurredAt)
    return changed
  }

  private fun applyPrivacy(values: Map<String, Boolean>): Boolean {
    var changed = false
    values["readReceipts"]?.let { changed = setReadReceipts(it) || changed }
    values["shareActivity"]?.let { changed = setActivitySharing(it) || changed }
    return changed
  }

  private fun applyNotifications(values: Map<String, Boolean>): Boolean {
    val current = notifications()
    val next = NotificationPrefs(
      messages = values["messages"] ?: current.messages,
      requests = values["requests"] ?: current.requests,
      sound = values["sound"] ?: current.sound,
    )
    return setNotifications(next)
  }

  fun privacyValues(): Map<String, Boolean> = mapOf(
    "readReceipts" to readReceipts(),
    "shareActivity" to activitySharing(),
  )

  fun notificationValues(): Map<String, Boolean> {
    val current = notifications()
    return mapOf(
      "messages" to current.messages,
      "requests" to current.requests,
      "sound" to current.sound,
    )
  }

  private fun peers(key: String): List<String> = synchronized(lock) {
    val stored = stateProvider().optJSONArray(key) ?: JSONArray()
    PeerIdList.normalize(List(stored.length()) { stored.optString(it) })
  }

  private fun setPeers(key: String, upeerIds: List<String>) {
    synchronized(lock) {
      val next = JSONArray()
      PeerIdList.normalize(upeerIds).forEach(next::put)
      stateProvider().put(key, next)
      persist()
    }
  }
}
