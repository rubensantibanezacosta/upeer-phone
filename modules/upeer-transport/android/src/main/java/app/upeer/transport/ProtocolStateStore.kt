package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.dht.DhtLocation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class ProtocolStateStore(context: Context) {
  private val store = KeystoreStore(
    context = context,
    preferencesName = "upeer_yggdrasil_protocol",
    keyAlias = "upeer-yggdrasil-protocol",
    valueKey = "protocol_state",
  )
  private val lock = Any()

  private val database = ProtocolDatabase(context)
  private val cipher = ProtocolCipher(context)

  /** Acceso a la base para los almacenes que comparten el esquema (outbox, relay). */
  internal fun databaseHandle(): ProtocolDatabase = database

  internal fun cipherHandle(): ProtocolCipher = cipher
  private val messageDatabase = ProtocolMessageDatabase(database)
  private val contactDatabase = ProtocolContactDatabase(database)
  private val groupDatabase = ProtocolGroupDatabase(database)
  private val sessionDatabase = ProtocolSessionDatabase(database, cipher)
  private val syncPeerDatabase = ProtocolSyncPeerDatabase(database)
  private val syncDatabase = ProtocolSyncDatabase(database, cipher)
  private val settingsDatabase = ProtocolSettingsDatabase(database)

  /** Tablas del esquema del escritorio que el modulo lee y escribe directamente. */
  val assets = ProtocolAssetDatabase(database)
  val pulseSync = ProtocolPulseSyncDatabase(database, cipher)

  /** Caché de lectura: SQLite es la verdad, este JSON es lo que ve el resto del módulo. */
  private var state = ProtocolStateCodec.load(store)

  private val migration = ProtocolStateMigration(
    database, messageDatabase, contactDatabase, groupDatabase,
    sessionDatabase, syncPeerDatabase, settingsDatabase, SyncEventDatabase(context),
  )

  private val mirror = ProtocolStateMirror(
    database, messageDatabase, contactDatabase, groupDatabase,
    sessionDatabase, syncPeerDatabase, syncDatabase, settingsDatabase,
  )

  private var lastSyncState = ""
  private var lastSyncGate = ""
  private var lastSyncMissing = -1
  private val batchGate = Any()
  private var migratePending = false
  private var stateRevision = 0L
  private var conversationsCache: Pair<Long, String>? = null
  private var batchDepth = 0
  private var batchDirty = false

  private val dirtyBlocks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

  private val persister = StatePersister(
    lock = lock,
    snapshot = { ByteArray(0) },
    blocks = { collectDirty() },
    write = { _, _ -> },
    onError = { cause -> Log.e(TAG, "No se pudo persistir el estado del protocolo", cause) },
  )

  val retention = ProtocolRetention({ state }, lock, { persist("rest") })
  val reputation = ReputationStore({ state }, lock, { persist("reputation") })
  val messages = ProtocolMessageList({ state }, lock, { persist("messages") }, ::retentionId, messageDatabase)
  val groups = ProtocolGroups({ state }, lock, { persist("groups") })
  val groupKeys = ProtocolGroupKeys({ state }, lock, { persist("groups") })
  val presence = ProtocolPresence({ state }, lock, { persist("rest") })
  private val syncEventDatabase = SyncEventDatabase(context).also { database ->
    val sync = state.optJSONObject("sync") ?: JSONObject()
    val legacy = sync.optJSONArray("events")
    if (legacy != null && legacy.length() > 0) database.migrateFrom(sync)
    if (sync.has("events") && database.count() > 0) {
      sync.remove("events")
      state.put("sync", sync)
      migratePending = true
      Log.i(TAG, "Migración: eventos sacados del estado (${database.count()} en SQLite)")
    } else if (sync.has("events")) {
      Log.w(TAG, "Migración: el estado tiene eventos pero SQLite está vacía (${database.count()})")
    }
  }
  val syncEvents = SyncEventLog({ state }, lock, { persist("sync") }, SyncEventLog.MAX_EVENTS, syncEventDatabase)
  val syncPeers = SyncDevicePeers({ state }, lock, { persist("sync") })
  val history = SyncHistoryState({ state }, lock, { persist("sync") })
  private val typing = ProtocolTyping({ state }, lock, { persist("rest") })
  val preferences = ProtocolPreferences({ state }, lock, { persist("rest") })

  /**
   * Arranque del almacenamiento: migra una única vez el contenido de los bloques cifrados a las
   * tablas SQLite y, a partir de ahí, reconstruye el estado en memoria leyendo de la base de datos.
   */
  private fun openStorage() {
    val summary = migration.run(state)
    if (summary != null) {
      Log.i(TAG, "Estado migrado a SQLite (${database.fileSize() / 1024} KB)")
    }
    // Con la migración hecha, los bloques cifrados del estado ya no se usan: se retiran siempre,
    // también en los arranques posteriores a la migración.
    if (migration.isDone() && store.hasBlocks()) {
      runCatching { store.clearBlocks() }
        .onSuccess { Log.i(TAG, "Bloques cifrados del estado retirados (${database.fileSize() / 1024} KB en SQLite)") }
        .onFailure { cause -> Log.w(TAG, "No se pudieron retirar los bloques cifrados del estado", cause) }
    }
    val rebuilt = mirror.read()
    // Conserva los eventos de sincronización, que ya vivían en su propia tabla.
    val events = state.optJSONObject("sync")?.optJSONArray("events")
    if (events != null) {
      (rebuilt.optJSONObject("sync") ?: JSONObject().also { rebuilt.put("sync", it) }).put("events", events)
    }
    state = ProtocolStateCodec.normalize(rebuilt)
    Log.i(TAG, "Estado cargado desde SQLite: ${mirror.counts()}")
  }

  @Volatile
  var fileWork: SyncFileWork = SyncFileWork.EMPTY

  @Volatile
  var syncGate: Boolean = false

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
        removeSessionsFor(upeerId)
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
      persist("peers")
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
        removeSessionsFor(upeerId)
        existing.remove("signedPreKey")
      }
      existing.put("address", address).put("lastSeen", System.currentTimeMillis())
      if (publicKey.isNotEmpty()) existing.put("publicKey", publicKey)
      if (signedPreKey != null && (keyChanged || SignedPreKeyFields.isNewer(existing.optJSONObject("signedPreKey"), signedPreKey))) {
        existing.put("signedPreKey", copy(signedPreKey))
      }
      peers.put(upeerId, existing)
      state.put("peers", peers)
      persist("peers")
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
      persist("peers")
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
      persist("peers")
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
      persist("rest")
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

  fun aliasFor(upeerId: String): String = synchronized(lock) {
    state.optJSONObject("peers")?.optJSONObject(upeerId)?.optString("alias").orEmpty()
  }

  fun peersJSON(): String = synchronized(lock) {
    JSONObject(state.optJSONObject("peers")?.toString() ?: "{}").toString()
  }

  fun sessionFor(upeerId: String, context: String = DIRECT_SESSION_CONTEXT): String? = synchronized(lock) {
    state.optJSONObject("sessions")?.optString(sessionKey(upeerId, context))?.ifEmpty { null }
  }

  fun saveSession(upeerId: String, sessionJSON: String, context: String = DIRECT_SESSION_CONTEXT) {
    synchronized(lock) {
      val sessions = state.optJSONObject("sessions") ?: JSONObject()
      sessions.put(sessionKey(upeerId, context), sessionJSON)
      state.put("sessions", sessions)
      persist("sessions")
    }
  }

  fun clearSession(upeerId: String, context: String = DIRECT_SESSION_CONTEXT) {
    synchronized(lock) {
      state.optJSONObject("sessions")?.remove(sessionKey(upeerId, context))
      persist("rest")
    }
  }

  fun clearSessions(upeerId: String) {
    synchronized(lock) {
      removeSessionsFor(upeerId)
      persist("rest")
    }
  }

  private fun removeSessionsFor(upeerId: String) {
    val sessions = state.optJSONObject("sessions") ?: return
    sessions.remove(upeerId)
    val groupPrefix = "$upeerId$SESSION_CONTEXT_SEPARATOR"
    for (key in sessions.keys().asSequence().toList()) {
      if (key.startsWith(groupPrefix)) sessions.remove(key)
    }
  }

  private fun sessionKey(upeerId: String, context: String): String =
    if (context == DIRECT_SESSION_CONTEXT) upeerId else "$upeerId$SESSION_CONTEXT_SEPARATOR$context"

  fun clearSyncHistory(deviceId: String? = null) {
    if (deviceId == null) history.clearAll() else history.clear(deviceId)
  }


  fun messagesJSON(upeerId: String, limit: Int): String = messages.snapshot(upeerId, limit)

  fun messagesByIdsJSON(idsJSON: String): String {
    val parsed = runCatching { JSONArray(idsJSON.ifBlank { "[]" }) }.getOrElse { JSONArray() }
    val ids = buildList { for (index in 0 until parsed.length()) add(parsed.optString(index)) }
    return synchronized(lock) {
      messages.purgeExpired()
      messageDatabase.byIds(ids, ProtocolMessageState.MAX_MESSAGES).toString()
    }
  }

  fun searchMessagesJSON(query: String, limit: Int, offset: Int): String = messages.searchJSON(query, limit, offset)

  fun searchChatMessagesJSON(upeerId: String, query: String, limit: Int, offset: Int): String =
    messages.searchJSON(query, limit, offset, upeerId)

  fun historyBeforeJSON(upeerId: String, beforeTimestamp: Long, limit: Int): String =
    messages.historyBeforeJSON(upeerId, beforeTimestamp, limit)

  fun conversationsJSON(): String = synchronized(lock) {
    val activities = typingActivities(System.currentTimeMillis())
    val cached = conversationsCache
    if (activities.isEmpty() && cached != null && cached.first == stateRevision) return cached.second
    messages.purgeExpired()
    val built = ProtocolConversations.build(
      state.optJSONArray("messages") ?: JSONArray(),
      state.optJSONObject("peers") ?: JSONObject(),
      activities,
      groups.index(),
    )
    if (activities.isEmpty()) conversationsCache = stateRevision to built
    built
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

  fun activitySharing(): Boolean = preferences.activitySharing()

  fun setActivitySharing(enabled: Boolean) = preferences.setActivitySharing(enabled)

  fun mutedPeers(): List<String> = preferences.muted()

  fun isMuted(upeerId: String): Boolean = preferences.isMuted(upeerId)

  fun setMutedPeers(upeerIds: List<String>) = preferences.setMuted(upeerIds)

  fun retentionId(upeerId: String): String = retention.id(upeerId)

  fun retentionMap(): Map<String, String> = retention.map()

  fun retentionStatusJSON(): String = retention.statusJSON()

  fun setRetentionMap(map: Map<String, String>): String {
    retention.set(map)
    messages.purgeExpired()
    return retention.statusJSON()
  }

  fun markTyping(upeerId: String, activity: String, untilMillis: Long) = typing.mark(upeerId, activity, untilMillis)

  fun clearTyping(upeerId: String) = typing.clear(upeerId)

  fun typingActivities(nowMillis: Long): Map<String, String> = typing.activities(nowMillis)

  fun typingPeers(nowMillis: Long): Set<String> = typing.peers(nowMillis)

  fun setNotificationPrefs(prefs: NotificationPrefs) = preferences.setNotifications(prefs)

  fun settingsClock(block: String): Long = preferences.settingsClock(block)

  fun claimSettingsUpdate(block: String, occurredAt: Long): Boolean =
    preferences.claimSettingsUpdate(block, occurredAt)

  fun applySyncSettings(block: String, values: Map<String, Boolean>, occurredAt: Long): Boolean =
    preferences.applySyncSettings(block, values, occurredAt)

  fun applyRetentionSync(patch: SyncRetentionPatch, occurredAt: Long): Boolean {
    val before = retention.id(patch.chatId)
    retention.set(MessageRetention.withChat(retention.map(), patch.chatId, patch.retention))
    preferences.setSettingsClock("${SyncSettings.RETENTION}:${patch.chatId}", occurredAt)
    val changed = before != patch.retention
    if (changed) messages.purgeExpired()
    return changed
  }

  fun privacyValues(): Map<String, Boolean> = preferences.privacyValues()

  fun settingsEventsAfterJSON(sequence: Long, limit: Int): String =
    SyncSettingsInbox.page(syncEvents.eventsAfter(sequence, limit), sequence).toString()

  fun notificationValues(): Map<String, Boolean> = preferences.notificationValues()

  fun deleteConversation(upeerId: String): Boolean = messages.deleteConversation(upeerId)

  fun markConversationRead(upeerId: String): List<String> = messages.markRead(upeerId)

  fun purgeDevice(deviceId: String): Boolean = synchronized(lock) {
    val changed = DevicePurge.apply(state, deviceId)
    if (changed) persist()
    changed
  }

  fun clear() {
    persister.close()
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

  private fun missingAttachments(): Int =
    SyncAttachmentAudit.missing(messages.attachmentRefs(), SyncFileReceive::isStored)

  fun syncStatusJSON(nowMillis: Long = System.currentTimeMillis()): String {
    val peers = syncPeers.list()
    val progress = peers.map { history.progress(it.deviceId) }
    val missing = missingAttachments()
    val report = SyncStatusReport.json(
      progress,
      peers,
      syncEvents.pendingCount(),
      missing,
      nowMillis,
      files = fileWork,
      unsupportedKinds = if (missing > 0) JSONObject().put(SyncStatusReport.KIND_ATTACHMENT, missing) else JSONObject(),
      gate = syncGate,
    )
    val current = report.optString("state")
    val gate = report.optString("gate")
    if (current != lastSyncState || gate != lastSyncGate || missing != lastSyncMissing) {
      lastSyncState = current
      lastSyncGate = gate
      lastSyncMissing = missing
      val progress = report.optJSONObject("progress")
      val avance = progress?.optInt("percent") ?: 0
      Log.i(
        TAG,
        "Estado de sincronización: $current gate=$gate dispositivos=${report.optInt("devices")} alDía=${report.optInt("devicesDone")} pendientes=${report.optInt("pendingEvents")} noSoportados=${report.optInt("unsupported")} avance=$avance% (${progress?.optString("detail")})",
      )
    }
    return report.toString()
  }

  private fun copy(value: JSONObject): JSONObject = JSONObject(value.toString())

  private fun validId(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  private fun persist(block: String? = null) {
    stateRevision += 1
    if (block != null) dirtyBlocks.add(block)
    if (batchDepth > 0) {
      batchDirty = true
      return
    }
    persister.request()
  }

  /**
   * Escribe a SQLite solo los bloques que cambiaron.
   *
   * Se llama desde el hilo escritor del [StatePersister], que agrupa varias modificaciones seguidas
   * en una sola pasada (`coalesce`) para no escribir la misma fila repetidamente.
   */
  private fun collectDirty(): Map<String, ByteArray> {
    val blocks = synchronized(lock) {
      val pending = dirtyBlocks.toList()
      dirtyBlocks.clear()
      pending
    }
    if (blocks.isEmpty()) return emptyMap()
    for (block in blocks) {
      try {
        mirror.writeBlock(state, block)
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudo escribir el bloque $block en SQLite", cause)
      }
    }
    return mapOf("sqlite" to ByteArray(0))
  }

  fun flushMigration() {
    if (!migratePending) return
    persist("sync")
    if (!flushPersistence()) {
      Log.w(TAG, "La migración del registro de sincronización no se pudo confirmar")
      return
    }
    migratePending = false
  }

  fun <T> batch(write: () -> T): T {
    synchronized(batchGate) { batchDepth += 1 }
    try {
      return write()
    } finally {
      val trailing = synchronized(batchGate) {
        batchDepth -= 1
        if (batchDepth == 0 && batchDirty) {
          batchDirty = false
          true
        } else {
          false
        }
      }
      if (trailing) persister.request()
    }
  }

  fun flushPersistence(): Boolean = persister.flush()

  init {
    openStorage()
  }

  internal companion object {
    private const val TAG = "UPeerTransport"
    const val DIRECT_SESSION_CONTEXT = "direct"
    private const val SESSION_CONTEXT_SEPARATOR = "|"
  }
}
