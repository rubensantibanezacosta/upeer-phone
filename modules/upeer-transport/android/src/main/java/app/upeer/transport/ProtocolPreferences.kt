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

  fun setNotifications(prefs: NotificationPrefs) {
    synchronized(lock) {
      stateProvider().put("notificationPrefs", prefs.toJSON())
      persist()
    }
  }

  fun readReceipts(): Boolean = synchronized(lock) {
    stateProvider().optBoolean("readReceipts", true)
  }

  fun setReadReceipts(enabled: Boolean) {
    synchronized(lock) {
      stateProvider().put("readReceipts", enabled)
      persist()
    }
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
