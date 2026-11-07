package app.upeer.transport.vault

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import app.upeer.transport.ProtocolCipher
import app.upeer.transport.ProtocolDatabase

/**
 * Buzón del vault (`vault_storage`), igual que `upper-desktop/src/main_process/storage/vault`.
 *
 * Guarda los sobres cifrados que otros contactos nos dejan para reenviarlos cuando el destinatario
 * aparezca. Antes vivía en un `SharedPreferences` cifrado propio; ahora comparte la base de datos
 * del protocolo, de modo que el mismo esquema sirve en los dos clientes.
 *
 * La columna `data` va cifrada por columna con [ProtocolCipher]: es el sobre de otro usuario, así
 * que en el escritorio lo protege SQLCipher y aquí el Keystore.
 */
internal class VaultMailboxStore(
  private val db: ProtocolDatabase,
  private val cipher: ProtocolCipher,
) {
  private val gate = Any()

  fun snapshot(nowMillis: Long): VaultMailboxState = synchronized(gate) {
    purgeExpired(nowMillis)
    val entries = allEntries()
    VaultMailboxState(nextId(entries), entries)
  }

  fun insert(entry: VaultEntry, nowMillis: Long, quotaBytes: Long = VaultMailbox.QUOTA_BYTES_PER_SENDER): VaultMailboxInsert =
    synchronized(gate) {
      purgeExpired(nowMillis)
      val entries = allEntries()
      val current = VaultMailboxState(nextId(entries), entries)
      val result = VaultMailbox.insert(current, entry, nowMillis, quotaBytes)
      if (result is VaultMailboxInsert.Stored) store(entry)
      result
    }

  fun release(payloadHashes: Collection<String>): Int = synchronized(gate) {
    if (payloadHashes.isEmpty()) return@synchronized 0
    var removed = 0
    for (hash in payloadHashes) {
      removed += db.database().delete("vault_storage", "payload_hash = ?", arrayOf(hash.lowercase()))
    }
    removed
  }

  fun entry(payloadHash: String, nowMillis: Long): VaultEntry? = synchronized(gate) {
    purgeExpired(nowMillis)
    find(payloadHash.lowercase())
  }

  fun renew(payloadHash: String, expiresAt: Long): Boolean = synchronized(gate) {
    val values = ContentValues()
    values.put("expires_at", expiresAt)
    db.database().update(
      "vault_storage",
      values,
      "payload_hash = ?",
      arrayOf(payloadHash.lowercase()),
    ) > 0
  }

  fun expiringSoon(windowMillis: Long, nowMillis: Long): List<VaultEntry> = synchronized(gate) {
    purgeExpired(nowMillis)
    val entries = allEntries()
    VaultMailbox.expiringSoon(VaultMailboxState(nextId(entries), entries), windowMillis, nowMillis)
  }

  fun clear() = synchronized(gate) {
    db.database().execSQL("DELETE FROM vault_storage")
  }

  /** Vuelca el buzón que vivía en su `SharedPreferences` cifrado y vacía esa copia. */
  fun migrateLegacy(legacyEntries: List<VaultEntry>): Int = synchronized(gate) {
    val flags = db.database()
    val done = flags.rawQuery("SELECT value FROM _app_flags WHERE key = 'vault_to_sqlite'", null)
      .use { it.moveToFirst() }
    if (done) return@synchronized 0
    var migrated = 0
    if (count() == 0) {
      for (entry in legacyEntries) {
        store(entry)
        migrated += 1
      }
    }
    flags.execSQL("INSERT OR REPLACE INTO _app_flags (key, value) VALUES ('vault_to_sqlite', '1')")
    migrated
  }

  /** Lee el buzón antiguo de su `SharedPreferences` y lo vacía, para volcarlo a SQLite. */
  fun legacyEntries(context: android.content.Context): List<VaultEntry>? {
    val store = app.upeer.transport.KeystoreStore(
      context,
      "upeer_vault_mailbox",
      "upeer-vault-mailbox",
      "vault_state",
    )
    val raw = runCatching { store.load() }.getOrNull() ?: return null
    runCatching { store.clear() }
    val document = runCatching { org.json.JSONObject(String(raw, Charsets.UTF_8)) }.getOrNull() ?: return null
    val array = document.optJSONArray("entries") ?: return emptyList()
    val entries = ArrayList<VaultEntry>()
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val hash = item.optString("hash")
      val sender = item.optString("sender")
      if (hash.isEmpty() || sender.isEmpty()) continue
      entries.add(
        VaultEntry(
          id = item.optLong("id", 0L),
          payloadHash = hash,
          recipientSid = item.optString("recipient"),
          recipientDeviceId = item.optString("device").takeIf(String::isNotEmpty),
          senderSid = sender,
          priority = item.optInt("priority", VaultStoreEnvelope.PRIORITY_MESSAGE),
          data = item.optString("data"),
          expiresAt = item.optLong("expiresAt", 0L),
        ),
      )
    }
    return entries
  }

  private fun purgeExpired(nowMillis: Long) {
    db.database().delete("vault_storage", "expires_at > 0 AND expires_at <= ?", arrayOf(nowMillis.toString()))
  }

  private fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM vault_storage", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  private fun nextId(entries: List<VaultEntry>): Long {
    val maxId = entries.maxOfOrNull { it.id } ?: 0L
    return maxOf(maxId + 1, 1L)
  }

  private fun store(entry: VaultEntry) {
    val values = ContentValues()
    values.put("payload_hash", entry.payloadHash.lowercase())
    values.put("recipient_sid", entry.recipientSid)
    values.put("recipient_device_id", entry.recipientDeviceId)
    values.put("sender_sid", entry.senderSid)
    values.put("priority", entry.priority)
    values.put("data", cipher.seal(entry.data) ?: "")
    values.put("expires_at", entry.expiresAt)
    db.database().insertWithOnConflict("vault_storage", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  private fun find(payloadHash: String): VaultEntry? =
    db.database().rawQuery("SELECT * FROM vault_storage WHERE payload_hash = ?", arrayOf(payloadHash)).use {
      if (it.moveToNext()) toEntry(it, 0L) else null
    }

  private fun allEntries(): List<VaultEntry> {
    val found = ArrayList<VaultEntry>()
    db.database().rawQuery(
      "SELECT * FROM vault_storage ORDER BY priority ASC, expires_at ASC",
      null,
    ).use { cursor ->
      var index = 0L
      while (cursor.moveToNext()) {
        toEntry(cursor, index)?.let(found::add)
        index += 1
      }
    }
    return found
  }

  private fun toEntry(cursor: android.database.Cursor, fallbackId: Long): VaultEntry? {
    fun text(column: String): String? {
      val index = cursor.getColumnIndex(column)
      return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
    }

    val hash = text("payload_hash") ?: return null
    val sender = text("sender_sid") ?: return null
    val idIndex = cursor.getColumnIndex("id")
    val id = if (idIndex >= 0 && !cursor.isNull(idIndex)) cursor.getLong(idIndex) else fallbackId
    return VaultEntry(
      id = id,
      payloadHash = hash,
      recipientSid = text("recipient_sid").orEmpty(),
      recipientDeviceId = text("recipient_device_id"),
      senderSid = sender,
      priority = text("priority")?.toIntOrNull() ?: VaultStoreEnvelope.PRIORITY_MESSAGE,
      data = cipher.open(text("data")).orEmpty(),
      expiresAt = text("expires_at")?.toLongOrNull() ?: 0L,
    )
  }
}
