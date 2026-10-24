package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.dht.DhtLocation
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
  private var state = ProtocolStateCodec.load(store)

  fun rememberPeer(metadata: JSONObject): Boolean {
    val upeerId = metadata.optString("senderUpeerId")
    val publicKey = metadata.optString("publicKey")
    val address = metadata.optString("senderYggAddress")
    if (!validId(upeerId) || publicKey.length != 64) return false
    return synchronized(lock) {
      val peers = state.optJSONObject("peers") ?: JSONObject()
      val existing = peers.optJSONObject(upeerId) ?: JSONObject()
      val previousKey = existing.optString("publicKey")
      val keyChanged = previousKey.isNotEmpty() && previousKey != publicKey
      val devicePublicKey = metadata.optString("devicePublicKey").lowercase()
      val deviceId = metadata.optString("deviceId").lowercase()
      val deviceSignedPreKey = metadata.optJSONObject("deviceSignedPreKey")
      val hasDevice = DhtLocation.isHex(devicePublicKey, 32) &&
        DhtLocation.isHex(deviceId, 32) &&
        deviceSignedPreKey != null &&
        SignedPreKeyFields.valid(deviceSignedPreKey)
      val deviceChanged = hasDevice && (
        !existing.optString("devicePublicKey").equals(devicePublicKey, ignoreCase = true) ||
          !existing.optString("deviceId").equals(deviceId, ignoreCase = true)
        )
      if (keyChanged || deviceChanged) {
        state.optJSONObject("sessions")?.remove(upeerId)
        existing.remove("signedPreKey")
      }
      existing.put("publicKey", publicKey).put("lastSeen", System.currentTimeMillis())
      if (address.isNotBlank()) existing.put("address", address)
      val alias = metadata.optString("alias").trim()
      if (alias.isNotEmpty()) existing.put("alias", alias.take(100))
      val signedPreKey = metadata.optJSONObject("signedPreKey")
      if (signedPreKey != null && SignedPreKeyFields.valid(signedPreKey) &&
        (keyChanged || SignedPreKeyFields.isNewer(existing.optJSONObject("signedPreKey"), signedPreKey))
      ) {
        existing.put("signedPreKey", copy(signedPreKey))
      }
      if (hasDevice) {
        existing.put("devicePublicKey", devicePublicKey)
        existing.put("deviceId", deviceId)
        existing.put("deviceSignedPreKey", copy(deviceSignedPreKey))
        metadata.optJSONObject("deviceCertificate")?.let { existing.put("deviceCertificate", copy(it)) }
      }
      peers.put(upeerId, existing)
      state.put("peers", peers)
      persist()
      keyChanged || deviceChanged
    }
  }

  fun upsertPeer(upeerId: String, address: String, publicKey: String, signedPreKeyJSON: String?): Boolean {
    if (!validId(upeerId) || address.isBlank()) return false
    if (publicKey.isNotEmpty() && !validId(publicKey)) return false
    val signedPreKey = signedPreKeyJSON?.takeIf(String::isNotBlank)?.let {
      runCatching { JSONObject(it) }.getOrNull()
    }
    if (signedPreKey != null && !SignedPreKeyFields.valid(signedPreKey)) return false
    synchronized(lock) {
      val peers = state.optJSONObject("peers") ?: JSONObject()
      val existing = peers.optJSONObject(upeerId) ?: JSONObject()
      val previousKey = existing.optString("publicKey")
      val keyChanged = previousKey.isNotEmpty() && publicKey.isNotEmpty() && previousKey != publicKey
      if (keyChanged) {
        state.optJSONObject("sessions")?.remove(upeerId)
        existing.remove("signedPreKey")
      }
      existing.put("address", address).put("lastSeen", System.currentTimeMillis())
      if (publicKey.isNotEmpty()) existing.put("publicKey", publicKey)
      if (signedPreKey != null && (keyChanged || SignedPreKeyFields.isNewer(existing.optJSONObject("signedPreKey"), signedPreKey))) {
        existing.put("signedPreKey", copy(signedPreKey))
      }
      peers.put(upeerId, existing)
      state.put("peers", peers)
      persist()
    }
    return true
  }

  fun updateSignedPreKey(upeerId: String, signedPreKey: JSONObject): SignedPreKeyUpdate =
    updatePeerSignedPreKey(upeerId, "signedPreKey", signedPreKey)

  fun updateDeviceSignedPreKey(upeerId: String, signedPreKey: JSONObject): SignedPreKeyUpdate =
    updatePeerSignedPreKey(upeerId, "deviceSignedPreKey", signedPreKey)

  private fun updatePeerSignedPreKey(
    upeerId: String,
    field: String,
    signedPreKey: JSONObject,
  ): SignedPreKeyUpdate {
    if (!validId(upeerId) || !SignedPreKeyFields.valid(signedPreKey)) return SignedPreKeyUpdate.INVALID
    return synchronized(lock) {
      val peers = state.optJSONObject("peers") ?: return@synchronized SignedPreKeyUpdate.INVALID
      val existing = peers.optJSONObject(upeerId) ?: return@synchronized SignedPreKeyUpdate.INVALID
      val decision = SignedPreKeyFields.classify(existing.optJSONObject(field), signedPreKey)
      if (decision != SignedPreKeyUpdate.UPDATED) return@synchronized decision
      existing.put(field, copy(signedPreKey))
      state.put("peers", peers)
      persist()
      SignedPreKeyUpdate.UPDATED
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

  fun peersJSON(): String = synchronized(lock) {
    JSONObject(state.optJSONObject("peers")?.toString() ?: "{}").toString()
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


  fun messageOwner(messageID: String): String? = synchronized(lock) {
    messagesLocked().let { ProtocolMessageState.find(it, messageID) }
      ?.optString("upeerId")
      ?.ifEmpty { null }
  }

  fun messageFor(messageID: String): JSONObject? = synchronized(lock) {
    ProtocolMessageState.find(messagesLocked(), messageID)?.let(::copy)
  }

  fun saveMessage(message: JSONObject) {
    synchronized(lock) {
      state.put("messages", ProtocolMessageState.upsert(messagesLocked(), message))
      persist()
    }
  }

  fun updateMessageContent(
    messageID: String,
    senderUpeerId: String,
    content: String,
    version: Int,
  ): JSONObject? = synchronized(lock) {
    val updated = ProtocolMessageState.updateIncomingContent(
      messagesLocked(),
      messageID,
      senderUpeerId,
      content,
      version,
    ) ?: return@synchronized null
    persist()
    updated
  }

  fun updateLocalMessageContent(messageID: String, upeerId: String, content: String, version: Int): Boolean =
    synchronized(lock) {
      val updated = ProtocolMessageState.updateLocalContent(
        messagesLocked(),
        messageID,
        upeerId,
        content,
        version,
      ) ?: return@synchronized false
      persist()
      updated.optInt("version", 0) == version
    }

  fun markMessageStatus(messageID: String, status: String) {
    synchronized(lock) {
      val message = ProtocolMessageState.find(messagesLocked(), messageID) ?: return@synchronized
      message.put("status", status)
      persist()
    }
  }

  fun ackMessage(messageID: String, senderUpeerId: String, status: String): Boolean {
    return synchronized(lock) {
      val message = ProtocolMessageState.find(messagesLocked(), messageID) ?: return@synchronized false
      if (!ReplayPolicy.validAck(message.optString("upeerId"), senderUpeerId, message.optBoolean("isMine"), status)) {
        return@synchronized false
      }
      message.put("status", status)
      persist()
      true
    }
  }

  fun messagesJSON(upeerId: String, limit: Int): String = synchronized(lock) {
    ProtocolMessageState.snapshot(messagesLocked(), upeerId, limit)
  }

  fun conversationsJSON(): String = synchronized(lock) {
    ProtocolConversations.build(
      state.optJSONArray("messages") ?: JSONArray(),
      state.optJSONObject("peers") ?: JSONObject(),
    )
  }

  fun blockedPeers(): List<String> = synchronized(lock) {
    val blocked = state.optJSONArray("blocked") ?: JSONArray()
    PeerIdList.normalize(List(blocked.length()) { blocked.optString(it) })
  }

  fun isBlocked(upeerId: String): Boolean = PeerIdList.contains(blockedPeers(), upeerId)

  fun setBlockedPeers(upeerIds: List<String>) {
    synchronized(lock) {
      val next = JSONArray()
      PeerIdList.normalize(upeerIds).forEach(next::put)
      state.put("blocked", next)
      persist()
    }
  }

  fun knownContacts(): List<String> = synchronized(lock) {
    val known = state.optJSONArray("knownContacts") ?: JSONArray()
    PeerIdList.normalize(List(known.length()) { known.optString(it) })
  }

  fun isKnownContact(upeerId: String): Boolean = PeerIdList.contains(knownContacts(), upeerId)

  fun setKnownContacts(upeerIds: List<String>) {
    synchronized(lock) {
      val next = JSONArray()
      PeerIdList.normalize(upeerIds).forEach(next::put)
      state.put("knownContacts", next)
      persist()
    }
  }

  fun notificationPrefs(): NotificationPrefs = synchronized(lock) {
    NotificationPrefs.parse(state.optString("notificationPrefs"))
  }

  fun setNotificationPrefs(prefs: NotificationPrefs) {
    synchronized(lock) {
      state.put("notificationPrefs", prefs.toJSON())
      persist()
    }
  }

  fun deleteConversation(upeerId: String): Boolean = synchronized(lock) {
    val messages = state.optJSONArray("messages") ?: return@synchronized false
    val remaining = ProtocolMessageState.withoutConversation(messages, upeerId)
    if (remaining.length() == messages.length()) return@synchronized false
    state.put("messages", remaining)
    persist()
    true
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
      state = ProtocolStateCodec.empty()
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

  private fun messagesLocked(): JSONArray = state.optJSONArray("messages") ?: JSONArray()

  private fun copy(value: JSONObject): JSONObject = JSONObject(value.toString())

  private fun validId(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  private fun persist() {
    try {
      store.save(state.toString().toByteArray(Charsets.UTF_8))
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo persistir el estado del protocolo", cause)
    }
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
