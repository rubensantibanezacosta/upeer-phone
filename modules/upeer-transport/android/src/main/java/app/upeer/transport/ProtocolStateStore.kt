package app.upeer.transport

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal class ProtocolStateStore(context: Context) {
  private val store = KeystoreStore(
    context = context,
    preferencesName = "upeer_yggdrasil_protocol",
    keyAlias = "upeer-yggdrasil-protocol",
    valueKey = "protocol_state",
  )
  private val lock = Any()
  private var state = loadState()

  fun rememberPeer(metadata: JSONObject): Boolean {
    val upeerId = metadata.optString("senderUpeerId")
    val publicKey = metadata.optString("publicKey")
    val address = metadata.optString("senderYggAddress")
    if (!validId(upeerId) || publicKey.length != 64) return false
    return synchronized(lock) {
      val peers = state.optJSONObject("peers") ?: JSONObject()
      val existing = peers.optJSONObject(upeerId) ?: JSONObject()
      val previousKey = existing.optString("publicKey")
      existing.put("publicKey", publicKey).put("lastSeen", System.currentTimeMillis())
      if (address.isNotBlank()) existing.put("address", address)
      val alias = metadata.optString("alias").trim()
      if (alias.isNotEmpty()) existing.put("alias", alias.take(100))
      val signedPreKey = metadata.optJSONObject("signedPreKey")
      if (signedPreKey != null && validSignedPreKey(signedPreKey)) existing.put("signedPreKey", copy(signedPreKey))
      peers.put(upeerId, existing)
      state.put("peers", peers)
      val keyChanged = previousKey.isNotEmpty() && previousKey != publicKey
      if (keyChanged) state.optJSONObject("sessions")?.remove(upeerId)
      persist()
      keyChanged
    }
  }

  fun upsertPeer(upeerId: String, address: String, publicKey: String, signedPreKeyJSON: String?): Boolean {
    if (!validId(upeerId) || address.isBlank()) return false
    if (publicKey.isNotEmpty() && !validId(publicKey)) return false
    val signedPreKey = signedPreKeyJSON?.takeIf(String::isNotBlank)?.let {
      runCatching { JSONObject(it) }.getOrNull()
    }
    if (signedPreKey != null && !validSignedPreKey(signedPreKey)) return false
    synchronized(lock) {
      val peers = state.optJSONObject("peers") ?: JSONObject()
      val existing = peers.optJSONObject(upeerId) ?: JSONObject()
      val previousKey = existing.optString("publicKey")
      existing.put("address", address).put("lastSeen", System.currentTimeMillis())
      if (publicKey.isNotEmpty()) existing.put("publicKey", publicKey)
      if (signedPreKey != null) existing.put("signedPreKey", copy(signedPreKey))
      peers.put(upeerId, existing)
      state.put("peers", peers)
      if (previousKey.isNotEmpty() && publicKey.isNotEmpty() && previousKey != publicKey) {
        state.optJSONObject("sessions")?.remove(upeerId)
      }
      persist()
    }
    return true
  }

  fun updateSignedPreKey(upeerId: String, signedPreKey: JSONObject): Boolean {
    if (!validId(upeerId) || !validSignedPreKey(signedPreKey)) return false
    return synchronized(lock) {
      val peers = state.optJSONObject("peers") ?: return@synchronized false
      val existing = peers.optJSONObject(upeerId) ?: return@synchronized false
      val currentID = existing.optJSONObject("signedPreKey")?.optLong("spkId") ?: 0L
      if (currentID >= signedPreKey.optLong("spkId")) return@synchronized false
      existing.put("signedPreKey", copy(signedPreKey))
      state.put("peers", peers)
      persist()
      true
    }
  }

  fun peerFor(upeerId: String): JSONObject? = synchronized(lock) {
    state.optJSONObject("peers")?.optJSONObject(upeerId)?.let(::copy)
  }

  fun publicKeyFor(upeerId: String): String? = synchronized(lock) {
    state.optJSONObject("peers")?.optJSONObject(upeerId)?.optString("publicKey")?.ifEmpty { null }
  }

  fun addressFor(upeerId: String): String? = synchronized(lock) {
    state.optJSONObject("peers")?.optJSONObject(upeerId)?.optString("address")?.ifEmpty { null }
  }

  fun sessionFor(upeerId: String): String? = synchronized(lock) {
    state.optJSONObject("sessions")?.optString(upeerId)?.ifEmpty { null }
  }

  fun saveSession(upeerId: String, sessionJSON: String) {
    synchronized(lock) {
      val sessions = state.optJSONObject("sessions") ?: JSONObject()
      sessions.put(upeerId, sessionJSON)
      state.put("sessions", sessions)
      persist()
    }
  }

  fun clearSession(upeerId: String) {
    synchronized(lock) {
      state.optJSONObject("sessions")?.remove(upeerId)
      persist()
    }
  }


  fun hasMessage(messageID: String): Boolean = synchronized(lock) {
    val messages = state.optJSONArray("messages") ?: return@synchronized false
    for (index in 0 until messages.length()) {
      if (messages.optJSONObject(index)?.optString("id") == messageID) return@synchronized true
    }
    false
  }

  fun saveMessage(message: JSONObject) {
    synchronized(lock) {
      upsertMessageLocked(message)
      persist()
    }
  }

  fun markMessageStatus(messageID: String, status: String) {
    synchronized(lock) {
      val message = findMessageLocked(messageID) ?: return@synchronized
      message.put("status", status)
      persist()
    }
  }

  fun ackMessage(messageID: String, status: String): Boolean {
    return synchronized(lock) {
      val message = findMessageLocked(messageID) ?: return@synchronized false
      message.put("status", if (status == "read") "read" else "delivered")
      persist()
      true
    }
  }

  fun messagesJSON(upeerId: String, limit: Int): String = synchronized(lock) {
    val messages = state.optJSONArray("messages") ?: JSONArray()
    val selected = buildList {
      for (index in 0 until messages.length()) {
        val message = messages.optJSONObject(index) ?: continue
        if (upeerId.isNotBlank() && message.optString("upeerId") != upeerId) continue
        add(copy(message))
      }
    }.takeLast(limit.coerceIn(1, MAX_MESSAGES))
    JSONArray(selected).toString()
  }

  fun conversationsJSON(): String = synchronized(lock) {
    val messages = state.optJSONArray("messages") ?: JSONArray()
    val peers = state.optJSONObject("peers") ?: JSONObject()
    val latest = LinkedHashMap<String, JSONObject>()
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      val upeerId = message.optString("upeerId")
      if (upeerId.isBlank()) continue
      val current = latest[upeerId]
      if (current == null || message.optLong("timestamp") >= current.optLong("timestamp")) latest[upeerId] = message
    }
    val result = latest.map { (upeerId, message) ->
      var unread = 0
      for (index in 0 until messages.length()) {
        val candidate = messages.optJSONObject(index) ?: continue
        if (candidate.optString("upeerId") == upeerId && !candidate.optBoolean("isMine") && candidate.optString("status") == "delivered") unread++
      }
      JSONObject()
        .put("upeerId", upeerId)
        .put("alias", peers.optJSONObject(upeerId)?.optString("alias").orEmpty())
        .put("lastMessage", message.optString("content"))
        .put("lastStatus", message.optString("status"))
        .put("lastTimestamp", message.optLong("timestamp"))
        .put("lastIsMine", message.optBoolean("isMine"))
        .put("unread", unread)
    }.sortedByDescending { it.optLong("lastTimestamp") }
    JSONArray(result).toString()
  }

  fun markConversationRead(upeerId: String) {
    synchronized(lock) {
      val messages = state.optJSONArray("messages") ?: return@synchronized
      for (index in 0 until messages.length()) {
        val message = messages.optJSONObject(index) ?: continue
        if (message.optString("upeerId") == upeerId && !message.optBoolean("isMine") && message.optString("status") == "delivered") {
          message.put("status", "read")
        }
      }
      persist()
    }
  }

  fun clear() {
    synchronized(lock) {
      state = emptyState()
      try {
        store.clear()
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudo borrar el estado del protocolo", cause)
      }
    }
  }

  fun statusJSON(): String = synchronized(lock) {
    val messages = state.optJSONArray("messages") ?: JSONArray()
    var unread = 0
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (!message.optBoolean("isMine") && message.optString("status") == "delivered") unread++
    }
    JSONObject()
      .put("peers", state.optJSONObject("peers")?.length() ?: 0)
      .put("sessions", state.optJSONObject("sessions")?.length() ?: 0)
      .put("messages", messages.length())
      .put("unread", unread)
      .toString()
  }


  private fun findMessageLocked(messageID: String): JSONObject? {
    val messages = state.optJSONArray("messages") ?: return null
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      if (message.optString("id") == messageID) return message
    }
    return null
  }

  private fun upsertMessageLocked(message: JSONObject) {
    val messages = state.optJSONArray("messages") ?: JSONArray()
    val next = JSONArray().put(message)
    val id = message.optString("id")
    for (index in 0 until messages.length()) {
      val existing = messages.optJSONObject(index) ?: continue
      if (existing.optString("id") != id) next.put(existing)
    }
    val trimmed = JSONArray()
    val start = maxOf(0, next.length() - MAX_MESSAGES)
    for (index in start until next.length()) trimmed.put(next.get(index))
    state.put("messages", trimmed)
  }

  private fun copy(value: JSONObject): JSONObject = JSONObject(value.toString())

  private fun validId(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  private fun validSignedPreKey(value: JSONObject): Boolean =
    value.optString("spkPub").length == 64 &&
      value.optString("spkSig").length == 128 &&
      value.optLong("spkId") > 0

  private fun persist() {
    try {
      store.save(state.toString().toByteArray(Charsets.UTF_8))
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo persistir el estado del protocolo", cause)
    }
  }

  private fun loadState(): JSONObject {
    val encoded = try {
      store.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer el estado del protocolo", cause)
      return emptyState()
    } ?: return emptyState()
    return try {
      val decoded = JSONObject(String(encoded, Charsets.UTF_8))
      if (decoded.optJSONObject("peers") == null) decoded.put("peers", JSONObject())
      if (decoded.optJSONObject("sessions") == null) decoded.put("sessions", JSONObject())
      if (decoded.optJSONArray("messages") == null) decoded.put("messages", JSONArray())
      Log.i(
        TAG,
        "Estado del protocolo cargado: peers=${decoded.optJSONObject("peers")?.length() ?: 0} " +
          "sessions=${decoded.optJSONObject("sessions")?.length() ?: 0} " +
          "messages=${decoded.optJSONArray("messages")?.length() ?: 0}",
      )
      decoded
    } catch (cause: Exception) {
      Log.e(TAG, "El estado del protocolo no es JSON válido", cause)
      emptyState()
    }
  }

  private fun emptyState(): JSONObject = JSONObject()
    .put("peers", JSONObject())
    .put("sessions", JSONObject())
    .put("messages", JSONArray())

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val MAX_MESSAGES = 500
  }
}
