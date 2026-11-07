package app.upeer.transport

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Traduce el `JSONObject` en memoria (la forma en que trabaja todo el módulo) a filas SQLite, y
 * viceversa.
 *
 * La estrategia es "estado en memoria, verdad en SQLite": las escrituras van a la base de datos
 * por fila —que es lo barato— y el `JSONObject` se sigue usando como caché de lectura para que el
 * resto del código no cambie. Así un mensaje nuevo es un `INSERT` y no la reescritura de un bloque
 * cifrado entero.
 */
internal class ProtocolStateMirror(
  private val db: ProtocolDatabase,
  private val messages: ProtocolMessageDatabase,
  private val contacts: ProtocolContactDatabase,
  private val groups: ProtocolGroupDatabase,
  private val sessions: ProtocolSessionDatabase,
  private val syncPeers: ProtocolSyncPeerDatabase,
  private val syncDb: ProtocolSyncDatabase,
  private val settings: ProtocolSettingsDatabase,
) {
  /** Vuelca a SQLite el contenido de `state`, bloque por bloque. */
  fun write(state: JSONObject) {
    val messageList = state.optJSONArray("messages") ?: JSONArray()
    val pairs = ArrayList<Pair<JSONObject, String>>(messageList.length())
    for (index in 0 until messageList.length()) {
      val message = messageList.optJSONObject(index) ?: continue
      val chat = message.optString("upeerId")
      if (chat.isEmpty()) continue
      pairs.add(message to chat)
    }
    if (pairs.isNotEmpty()) messages.insertAll(pairs)

    state.optJSONObject("peers")?.let { peers ->
      val map = LinkedHashMap<String, JSONObject>()
      val keys = peers.keys()
      while (keys.hasNext()) {
        val upeerId = keys.next()
        peers.optJSONObject(upeerId)?.let { map[upeerId] = it }
      }
      if (map.isNotEmpty()) contacts.insertAll(map)
    }

    state.optJSONObject("groups")?.let { groupMap ->
      val map = LinkedHashMap<String, JSONObject>()
      val keys = groupMap.keys()
      while (keys.hasNext()) {
        val groupId = keys.next()
        groupMap.optJSONObject(groupId)?.let { map[groupId] = it }
      }
      if (map.isNotEmpty()) groups.insertAll(map)
    }

    state.optJSONObject("sessions")?.let { sessionMap ->
      val map = LinkedHashMap<String, JSONObject>()
      val keys = sessionMap.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        sessionMap.optJSONObject(key)?.let { map[key] = it }
      }
      if (map.isNotEmpty()) sessions.insertAll(map)
    }

    state.optJSONObject("sync")?.let { sync ->
      sync.optJSONObject("devices")?.let { deviceMap ->
        val map = LinkedHashMap<String, JSONObject>()
        val keys = deviceMap.keys()
        while (keys.hasNext()) {
          val deviceId = keys.next()
          deviceMap.optJSONObject(deviceId)?.let { map[deviceId] = it }
        }
        if (map.isNotEmpty()) syncPeers.insertAll(map)
      }
    }

    val rest = LinkedHashMap<String, String>()
    val keys = state.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      if (key in OWN_TABLES) continue
      state.opt(key)?.let { rest[key] = it.toString() }
    }
    if (rest.isNotEmpty()) settings.putAll(rest)
  }

  /** Vuelca a SQLite solo el bloque indicado, sin recorrer el resto del estado. */
  fun writeBlock(state: JSONObject, block: String) {
    when (block) {
      "messages" -> writeMessages(state)
      "peers" -> writeContacts(state)
      "groups" -> writeGroups(state)
      "sessions" -> writeSessions(state)
      "sync" -> writeSyncPeers(state)
      "reputation" -> writeReputation(state)
      "rest" -> writeSettings(state)
      else -> writeMessages(state)
    }
  }

  /**
   * Vuelca los mensajes del estado y borra los que se hayan quitado a propósito.
   *
   * Sin el borrado, vaciar una conversación solo cambiaba la copia en memoria: las filas seguían en
   * SQLite y los mensajes reaparecían en el siguiente arranque.
   *
   * El borrado es **por conversación y solo sobre los ids que esa conversación tenía guardados**, nunca
   * «todo lo que falte del estado»: `ProtocolMessageState.upsert` recorta el estado a 500 mensajes, así
   * que un borrado por diferencia real eliminaría de disco los que caen fuera de ese tope.
   */
  private var knownByChat: Map<String, Set<String>>? = null
  private var knownContactKeys: Set<String>? = null
  private var knownGroupKeys: Set<String>? = null
  private var knownSessionKeys: Set<String>? = null
  private var knownPeerKeys: Set<String>? = null

  fun writeMessages(state: JSONObject) {
    val messageList = state.optJSONArray("messages") ?: org.json.JSONArray()
    val pairs = ArrayList<Pair<JSONObject, String>>(messageList.length())
    for (index in 0 until messageList.length()) {
      val message = messageList.optJSONObject(index) ?: continue
      val chat = message.optString("upeerId")
      if (chat.isEmpty() || message.optString("id").isEmpty()) continue
      pairs.add(message to chat)
    }
    val present = ProtocolMessagePrune.index(state)
    for ((chat, gone) in ProtocolMessagePrune.prune(knownByChat, present)) {
      messages.deleteByIds(chat, gone)
    }
    if (pairs.isNotEmpty()) messages.insertAll(pairs)
    knownByChat = present
  }

  /** Fija la foto de ids conocidos sin escribir nada, para el primer volcado tras arrancar. */
  fun primeMessageIndex(state: JSONObject) {
    knownByChat = ProtocolMessagePrune.index(state)
    knownContactKeys = state.optJSONObject("peers")?.keys()?.asSequence()?.toSet() ?: emptySet()
    knownGroupKeys = state.optJSONObject("groups")?.keys()?.asSequence()?.toSet() ?: emptySet()
    knownSessionKeys = state.optJSONObject("sessions")?.keys()?.asSequence()?.toSet() ?: emptySet()
    knownPeerKeys = state.optJSONObject("sync")?.optJSONObject("devices")?.keys()?.asSequence()?.toSet()
      ?: emptySet()
  }

  fun writeContacts(state: JSONObject) {
    val peers = state.optJSONObject("peers") ?: org.json.JSONObject()
    val map = LinkedHashMap<String, JSONObject>()
    val keys = peers.keys()
    while (keys.hasNext()) {
      val upeerId = keys.next()
      peers.optJSONObject(upeerId)?.let { map[upeerId] = it }
    }
    // Borrar un contacto o bloquearlo tiene que llegar a disco, no solo a la copia en memoria.
    for (gone in knownContactKeys.orEmpty() - map.keys) contacts.delete(gone)
    knownContactKeys = map.keys.toSet()
    if (map.isNotEmpty()) contacts.insertAll(map)
  }

  fun writeGroups(state: JSONObject) {
    val groupMap = state.optJSONObject("groups") ?: org.json.JSONObject()
    val map = LinkedHashMap<String, JSONObject>()
    val keys = groupMap.keys()
    while (keys.hasNext()) {
      val groupId = keys.next()
      groupMap.optJSONObject(groupId)?.let { map[groupId] = it }
    }
    // Salir de un grupo o que lo borren desde otro dispositivo debe desaparecer también de la tabla.
    for (gone in knownGroupKeys.orEmpty() - map.keys) groups.delete(gone)
    knownGroupKeys = map.keys.toSet()
    if (map.isNotEmpty()) groups.insertAll(map)
  }

  fun writeSessions(state: JSONObject) {
    val sessionMap = state.optJSONObject("sessions") ?: org.json.JSONObject()
    val map = LinkedHashMap<String, JSONObject>()
    val keys = sessionMap.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      sessionMap.optJSONObject(key)?.let { map[key] = it }
    }
    // Un reset de ratchet o una rotación de clave elimina sesiones: eso también va a disco.
    for (gone in knownSessionKeys.orEmpty() - map.keys) deleteSession(gone)
    knownSessionKeys = map.keys.toSet()
    if (map.isNotEmpty()) sessions.insertAll(map)
  }

  fun writeSyncPeers(state: JSONObject) {
    val deviceMap = state.optJSONObject("sync")?.optJSONObject("devices") ?: org.json.JSONObject()
    val map = LinkedHashMap<String, JSONObject>()
    val keys = deviceMap.keys()
    while (keys.hasNext()) {
      val deviceId = keys.next()
      deviceMap.optJSONObject(deviceId)?.let { map[deviceId] = it }
    }
    // Un dispositivo propio desvinculado deja de ser par de sincronización.
    for (gone in knownPeerKeys.orEmpty() - map.keys) syncPeers.delete(gone)
    knownPeerKeys = map.keys.toSet()
    if (map.isNotEmpty()) syncPeers.insertAll(map)
  }

  fun writeReputation(state: JSONObject) {
    val vouches = state.optJSONObject("vouches") ?: return
    val keys = vouches.keys()
    while (keys.hasNext()) {
      val id = keys.next()
      val vouch = vouches.optJSONObject(id) ?: continue
      val json = JSONObject(vouch.toString()).put("id", id)
      syncDb.vouchInsert(id, json)
    }
  }

  fun writeSettings(state: JSONObject) {
    val rest = LinkedHashMap<String, String>()
    val keys = state.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      if (key in OWN_TABLES) continue
      state.opt(key)?.let { rest[key] = it.toString() }
    }
    if (rest.isNotEmpty()) settings.putAll(rest)
  }

  /** Escribe un único grupo (o sus claves) sin tocar el resto. */
  fun writeGroup(groupId: String, group: JSONObject) {
    groups.insert(groupId, group)
  }

  fun deleteGroup(groupId: String) {
    groups.delete(groupId)
  }

  /** Escribe un único contacto sin tocar el resto. */
  fun writeContact(upeerId: String, contact: JSONObject) {
    contacts.insert(contact, upeerId)
  }

  fun deleteContact(upeerId: String) {
    contacts.delete(upeerId)
  }

  /** Escribe una única sesión Double Ratchet. */
  fun writeSession(key: String, session: JSONObject) {
    val separator = key.indexOf('|')
    if (separator < 0) {
      sessions.insert(key, ProtocolStateStore.DIRECT_SESSION_CONTEXT, session.toString())
    } else {
      sessions.insert(key.substring(0, separator), key.substring(separator + 1), session.toString())
    }
  }

  fun deleteSession(key: String) {
    val separator = key.indexOf('|')
    if (separator < 0) {
      sessions.delete(key, ProtocolStateStore.DIRECT_SESSION_CONTEXT)
    } else {
      sessions.delete(key.substring(0, separator), key.substring(separator + 1))
    }
  }

  fun writeSyncPeer(deviceId: String, peer: JSONObject) {
    syncPeers.insert(deviceId, peer)
  }

  fun writeSetting(key: String, value: Any?) {
    when (value) {
      null -> settings.remove(key)
      else -> settings.put(key, value.toString())
    }
  }

  fun insertMessage(message: JSONObject, chatUpeerId: String) {
    messages.insert(message, chatUpeerId)
  }

  fun deleteMessage(messageId: String) {
    messages.delete(messageId)
  }

  fun deleteConversation(chatUpeerId: String) {
    messages.deleteConversation(chatUpeerId)
  }

  /** Reconstruye el `JSONObject` de estado completo leyendo las tablas. */
  fun read(): JSONObject {
    val state = JSONObject()
    val messageList = JSONArray()
    messages.all().let { all -> for (index in 0 until all.length()) messageList.put(all.opt(index)) }
    state.put("messages", messageList)
    state.put("peers", toObject(contacts.all()))
    state.put("groups", toObject(groups.all()))
    state.put("sessions", toObject(sessions.all()))
    val sync = JSONObject().put("devices", toObject(syncPeers.all()))
    state.put("sync", sync)
    for ((key, raw) in settings.all()) {
      state.put(key, decode(raw))
    }
    return state
  }

  fun counts(): String =
    "mensajes=${messages.count()} contactos=${contacts.count()} grupos=${groups.count()} " +
      "sesiones=${sessions.count()} pares=${syncPeers.count()} eventos=${syncDb.relayCount()} " +
      "ajustes=${settings.count()}"

  private fun toObject(map: Map<String, JSONObject>): JSONObject {
    val json = JSONObject()
    for ((key, value) in map) json.put(key, value)
    return json
  }

  private fun decode(raw: String): Any = runCatching { JSONObject("{\"v\":$raw}").get("v") }
    .getOrElse { raw }

  private companion object {
    val OWN_TABLES = setOf("messages", "peers", "groups", "sessions", "sync")

    @Suppress("unused")
    const val TAG = "UPeerTransport"
  }
}
