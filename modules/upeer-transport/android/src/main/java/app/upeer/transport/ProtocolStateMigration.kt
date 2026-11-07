package app.upeer.transport

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Migración de una sola vez: copia el contenido de los bloques cifrados del estado
 * (`upeer_yggdrasil_protocol`) a las tablas SQLite del esquema del escritorio.
 *
 * Se marca en `_app_flags` para no repetirse. Si la base ya tenía datos (por ejemplo la tabla
 * `sync_events` de una versión anterior), esos datos no se pisotean: cada inserción usa
 * `CONFLICT_IGNORE`/`REPLACE` según la tabla y se comprueba antes si ya hay filas.
 */
internal class ProtocolStateMigration(
  private val db: ProtocolDatabase,
  private val messages: ProtocolMessageDatabase,
  private val contacts: ProtocolContactDatabase,
  private val groups: ProtocolGroupDatabase,
  private val sessions: ProtocolSessionDatabase,
  private val syncPeers: ProtocolSyncPeerDatabase,
  private val settings: ProtocolSettingsDatabase,
  private val syncEvents: SyncEventDatabase,
) {
  /** Migra `state` a SQLite. Devuelve el resumen de lo migrado, o `null` si ya estaba hecho. */
  fun run(state: JSONObject): Summary? {
    val database = db.database()
    if (flag("state_to_sqlite")) return null
    val summary = Summary()
    database.beginTransaction()
    try {
      // messages (y sus reacciones van dentro del propio JSON del mensaje)
      val messageList = state.optJSONArray("messages") ?: JSONArray()
      if (messages.count() == 0) {
        for (index in 0 until messageList.length()) {
          val message = messageList.optJSONObject(index) ?: continue
          val chat = message.optString("upeerId")
          if (chat.isEmpty()) continue
          messages.insert(message, chat)
          summary.messages++
        }
      } else {
        summary.messagesSkipped = messageList.length()
      }

      // contacts
      val peerMap = state.optJSONObject("peers") ?: JSONObject()
      if (contacts.count() == 0) {
        val keys = peerMap.keys()
        while (keys.hasNext()) {
          val upeerId = keys.next()
          val contact = peerMap.optJSONObject(upeerId) ?: continue
          contacts.insert(contact, upeerId)
          summary.contacts++
        }
      }

      // groups
      val groupMap = state.optJSONObject("groups") ?: JSONObject()
      if (groups.count() == 0) {
        val keys = groupMap.keys()
        while (keys.hasNext()) {
          val groupId = keys.next()
          val group = groupMap.optJSONObject(groupId) ?: continue
          groups.insert(groupId, group)
          summary.groups++
        }
      }

      // ratchet_sessions
      val sessionMap = state.optJSONObject("sessions") ?: JSONObject()
      if (sessions.count() == 0) {
        val sessionValues = LinkedHashMap<String, JSONObject>()
        val sessionKeys = sessionMap.keys()
        while (sessionKeys.hasNext()) {
          val key = sessionKeys.next()
          sessionMap.optJSONObject(key)?.let { sessionValues[key] = it }
        }
        sessions.insertAll(sessionValues)
        summary.sessions = sessionValues.size
      }

      // sync_peers (dispositivos propios)
      val sync = state.optJSONObject("sync") ?: JSONObject()
      val deviceMap = sync.optJSONObject("devices") ?: JSONObject()
      if (syncPeers.count() == 0) {
        val keys = deviceMap.keys()
        while (keys.hasNext()) {
          val deviceId = keys.next()
          val peer = deviceMap.optJSONObject(deviceId) ?: continue
          syncPeers.insert(deviceId, peer)
          summary.syncPeers++
        }
      }

      // sync_events (por si quedaban en el estado)
      val events = sync.optJSONArray("events")
      if (events != null && events.length() > 0) {
        val before = syncEvents.count()
        syncEvents.migrateFrom(sync)
        summary.syncEvents = syncEvents.count() - before
      }

      // resto de claves sueltas → app_settings
      val settingsValues = LinkedHashMap<String, String>()
      val keys = state.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        if (key in HANDSLED_KEYS) continue
        state.opt(key)?.let { settingsValues[key] = it.toString() }
      }
      if (settings.count() == 0 && settingsValues.isNotEmpty()) {
        settings.putAll(settingsValues)
        summary.settings = settingsValues.size
      }

      markFlag("state_to_sqlite")
      database.setTransactionSuccessful()
    } catch (cause: Exception) {
      Log.e(TAG, "La migración del estado a SQLite falló; se mantiene el estado anterior", cause)
      return null
    } finally {
      database.endTransaction()
    }
    Log.i(
      TAG,
      "Migración a SQLite: mensajes=${summary.messages} contactos=${summary.contacts} " +
        "grupos=${summary.groups} sesiones=${summary.sessions} pares=${summary.syncPeers} " +
        "eventos=${summary.syncEvents} ajustes=${summary.settings} (omitidos=${summary.messagesSkipped})",
    )
    return summary
  }

  fun isDone(): Boolean = flag("state_to_sqlite")

  fun reset() {
    db.database().delete("_app_flags", "key = ?", arrayOf("state_to_sqlite"))
  }

  private fun flag(key: String): Boolean = db.database()
    .rawQuery("SELECT value FROM _app_flags WHERE key = ?", arrayOf(key))
    .use { it.moveToFirst() }

  private fun markFlag(key: String) {
    db.database().execSQL(
      "INSERT OR REPLACE INTO _app_flags (key, value) VALUES (?, '1')",
      arrayOf(key),
    )
  }

  internal class Summary {
    var messages = 0
    var messagesSkipped = 0
    var contacts = 0
    var groups = 0
    var sessions = 0
    var syncPeers = 0
    var syncEvents = 0
    var settings = 0
  }

  private companion object {
    const val TAG = "UPeerTransport"
    val HANDSLED_KEYS = setOf("messages", "peers", "groups", "sessions", "sync")
  }
}
