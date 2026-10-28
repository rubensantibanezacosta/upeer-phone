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
  private var lastSyncState = ""

  val messages = ProtocolMessageList({ state }, lock, ::persist)
  val groups = ProtocolGroups({ state }, lock, ::persist)
  val presence = ProtocolPresence({ state }, lock, ::persist)
  val syncEvents = SyncEventLog({ state }, lock, ::persist)
  val syncPeers = SyncDevicePeers({ state }, lock, ::persist)
  val history = SyncHistoryState({ state }, lock, ::persist)
  private val typing = ProtocolTyping({ state }, lock, ::persist)
  val preferences = ProtocolPreferences({ state }, lock, ::persist)

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
      val avatar = ProfileFields.avatar(metadata.optString("avatar"))
      if (avatar.isNotEmpty()) existing.put("avatar", avatar)
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

  fun touchPresence(upeerId: String): Boolean {
    if (!validId(upeerId)) return false
    return synchronized(lock) {
      val peers = state.optJSONObject("peers") ?: return@synchronized false
      val existing = peers.optJSONObject(upeerId) ?: return@synchronized false
      existing.put("lastSeen", System.currentTimeMillis())
      state.put("peers", peers)
      persist()
      true
    }
  }

  fun peerFor(upeerId: String): JSONObject? = synchronized(lock) {
    state.optJSONObject("peers")?.optJSONObject(upeerId)?.let(::copy)
  }

  fun rememberContactKey(upeerId: String, publicKey: String): Boolean = synchronized(lock) {
    val peers = state.optJSONObject("peers") ?: JSONObject()
    val existing = peers.optJSONObject(upeerId) ?: JSONObject()
    val learned = PeerKeyLearning.keyToLearn(upeerId, publicKey, existing.optString("publicKey"))
      ?: return@synchronized false
    existing.put("publicKey", learned)
    peers.put(upeerId, existing)
    state.put("peers", peers)
    persist()
    true
  }

  fun rememberContactProfile(upeerId: String, alias: String, avatar: String): Boolean = synchronized(lock) {
    if (!validId(upeerId)) return@synchronized false
    val peers = state.optJSONObject("peers") ?: JSONObject()
    val existing = peers.optJSONObject(upeerId) ?: JSONObject()
    var changed = false
    val nextAlias = ProfileFields.alias(alias)
    if (nextAlias.isNotEmpty() && existing.optString("alias") != nextAlias) {
      existing.put("alias", nextAlias)
      changed = true
    }
    if (avatar.isNotEmpty() && existing.optString("avatar") != avatar) {
      existing.put("avatar", avatar)
      changed = true
    }
    if (!changed) return@synchronized false
    peers.put(upeerId, existing)
    state.put("peers", peers)
    persist()
    true
  }

  fun setProfile(alias: String, avatar: String) {
    synchronized(lock) {
      val profile = state.optJSONObject("profile") ?: JSONObject()
      profile.put("alias", ProfileFields.alias(alias))
      val normalizedAvatar = ProfileFields.avatar(avatar)
      if (normalizedAvatar.isEmpty()) profile.remove("avatar") else profile.put("avatar", normalizedAvatar)
      state.put("profile", profile)
      persist()
    }
  }

  fun announcedProfile(): String = synchronized(lock) { state.optString("announcedProfileV2") }

  fun setAnnouncedProfile(value: String) = synchronized(lock) {
    state.put("announcedProfileV2", value)
    persist()
  }

  fun profile(): JSONObject = synchronized(lock) {
    state.optJSONObject("profile")?.let(::copy) ?: JSONObject()
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

  fun clearSyncHistory(deviceId: String? = null) {
    if (deviceId == null) history.clearAll() else history.clear(deviceId)
  }


  fun messagesJSON(upeerId: String, limit: Int): String = messages.snapshot(upeerId, limit)

  fun searchMessagesJSON(query: String, limit: Int, offset: Int): String = messages.searchJSON(query, limit, offset)

  fun conversationsJSON(): String = synchronized(lock) {
    ProtocolConversations.build(
      state.optJSONArray("messages") ?: JSONArray(),
      state.optJSONObject("peers") ?: JSONObject(),
      typingActivities(System.currentTimeMillis()),
      groups.index(),
    )
  }

  fun groupsJSON(): String = groups.snapshot().toString()

  fun blockedPeers(): List<String> = preferences.blocked()

  fun isBlocked(upeerId: String): Boolean = preferences.isBlocked(upeerId)

  fun setBlockedPeers(upeerIds: List<String>) = preferences.setBlocked(upeerIds)

  fun knownContacts(): List<String> = preferences.known()

  fun isKnownContact(upeerId: String): Boolean = preferences.isKnown(upeerId)

  fun setKnownContacts(upeerIds: List<String>) = preferences.setKnown(upeerIds)

  fun notificationPrefs(): NotificationPrefs = preferences.notifications()

  fun readReceiptsEnabled(): Boolean = preferences.readReceipts()

  fun setReadReceipts(enabled: Boolean) = preferences.setReadReceipts(enabled)

  fun mutedPeers(): List<String> = preferences.muted()

  fun isMuted(upeerId: String): Boolean = preferences.isMuted(upeerId)

  fun setMutedPeers(upeerIds: List<String>) = preferences.setMuted(upeerIds)

  fun markTyping(upeerId: String, activity: String, untilMillis: Long) = typing.mark(upeerId, activity, untilMillis)

  fun clearTyping(upeerId: String) = typing.clear(upeerId)

  fun typingActivities(nowMillis: Long): Map<String, String> = typing.activities(nowMillis)

  fun typingPeers(nowMillis: Long): Set<String> = typing.peers(nowMillis)

  fun setNotificationPrefs(prefs: NotificationPrefs) = preferences.setNotifications(prefs)

  fun deleteConversation(upeerId: String): Boolean = messages.deleteConversation(upeerId)

  fun markConversationRead(upeerId: String): List<String> = messages.markRead(upeerId)

  fun purgeDevice(deviceId: String): Boolean = synchronized(lock) {
    val changed = DevicePurge.apply(state, deviceId)
    if (changed) persist()
    changed
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

  fun statusJSON(): String = synchronized(lock) { ProtocolStatus.json(state) }

  fun syncStatusJSON(nowMillis: Long = System.currentTimeMillis()): String {
    val peers = syncPeers.list()
    val progress = peers.map { history.progress(it.deviceId) }
    val report = SyncStatusReport.json(
      progress,
      peers,
      syncEvents.pendingCount(),
      syncEvents.unsupportedCount(),
      nowMillis,
    )
    val current = report.optString("state")
    if (current != lastSyncState) {
      lastSyncState = current
      Log.i(
        TAG,
        "Estado de sincronización: $current dispositivos=${report.optInt("devices")} alDía=${report.optInt("devicesDone")} pendientes=${report.optInt("pendingEvents")} noSoportados=${report.optInt("unsupported")}",
      )
    }
    return report.toString()
  }

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
