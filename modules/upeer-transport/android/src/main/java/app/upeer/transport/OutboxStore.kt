package app.upeer.transport

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONObject

/**
 * Mensajes en espera de poder entregarse, persistidos en la tabla `pending_outbox`.
 *
 * Es el equivalente de `upper-desktop/src/main_process/storage/pending-outbox.ts`: allí se guardan
 * los mensajes dirigidos a un contacto del que aún no se tiene la clave, aquí los que esperan a que
 * el par sea alcanzable o a que exista sesión. Antes vivían en un `SharedPreferences` cifrado
 * aparte; ahora comparten la base de datos del protocolo.
 *
 * Las columnas propias del móvil (`kind`, `version`, `attempts`, `next_attempt_at`, `packet`…)
 * amplían el esquema del escritorio sin quitarle ninguna: las suyas (`msg_id`, `recipient_sid`,
 * `plaintext`, `reply_to`, `created_at`) siguen siendo las mismas.
 */
internal class OutboxStore(
  private val db: ProtocolDatabase,
  private val cipher: ProtocolCipher,
) {
  fun enqueue(messageID: String, upeerId: String, content: String, replyTo: String, timestamp: Long): JSONObject {
    find(messageID)?.let { return it }
    val entry = JSONObject()
      .put("id", messageID)
      .put("kind", "CHAT")
      .put("upeerId", upeerId)
      .put("content", content)
      .put("timestamp", timestamp)
      .put("attempts", 0)
      .put("nextAttemptAt", 0)
      .put("createdAt", System.currentTimeMillis())
    if (replyTo.isNotEmpty()) entry.put("replyTo", replyTo)
    save(entry, isNew = true)
    trim()
    return entry
  }

  fun enqueueUpdate(
    entryID: String,
    messageID: String,
    upeerId: String,
    content: String,
    version: Int,
    timestamp: Long,
  ): JSONObject {
    find(entryID)?.let { return it }
    val entry = JSONObject()
      .put("id", entryID)
      .put("kind", "CHAT_UPDATE")
      .put("msgId", messageID)
      .put("upeerId", upeerId)
      .put("content", content)
      .put("version", version)
      .put("timestamp", timestamp)
      .put("attempts", 0)
      .put("nextAttemptAt", 0)
      .put("createdAt", System.currentTimeMillis())
    save(entry, isNew = true)
    trim()
    return entry
  }

  fun updatePendingContent(messageID: String, content: String): Boolean {
    val entry = find(messageID) ?: return false
    if (entry.optString("kind", "CHAT") != "CHAT") return false
    entry.put("content", content).put("nextAttemptAt", 0)
    entry.remove("packet")
    save(entry)
    return true
  }

  fun due(now: Long, limit: Int): List<JSONObject> {
    val found = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT * FROM pending_outbox WHERE next_attempt_at <= ? ORDER BY created_at ASC LIMIT ?",
      arrayOf(now.toString(), limit.coerceAtLeast(1).toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) found.add(toJson(cursor))
    }
    return found
  }

  fun markAttempt(messageID: String, attempts: Int, nextAttemptAt: Long, error: String) {
    val values = ContentValues()
    values.put("attempts", attempts)
    values.put("next_attempt_at", nextAttemptAt)
    values.put("last_error", if (error.isBlank()) null else error.take(240))
    db.database().update("pending_outbox", values, "msg_id = ?", arrayOf(messageID))
  }

  fun markSent(messageID: String, nextAttemptAt: Long, sends: Int = 0) {
    val values = ContentValues()
    values.put("next_attempt_at", nextAttemptAt)
    values.put("sends", sends)
    values.put("sent_at", System.currentTimeMillis())
    db.database().update("pending_outbox", values, "msg_id = ?", arrayOf(messageID))
  }

  fun setPacket(messageID: String, packet: String) {
    val values = ContentValues()
    values.put("packet", cipher.seal(packet))
    db.database().update("pending_outbox", values, "msg_id = ?", arrayOf(messageID))
  }

  fun remove(messageID: String) {
    db.database().delete("pending_outbox", "msg_id = ?", arrayOf(messageID))
  }

  fun resetPeer(upeerId: String): Int {
    val values = ContentValues()
    values.put("next_attempt_at", 0)
    values.putNull("packet")
    return db.database().update("pending_outbox", values, "recipient_sid = ?", arrayOf(upeerId))
  }

  /**
   * Vuelca una sola vez el outbox que vivia en su propio `SharedPreferences` cifrado y retira esa
   * copia. Se marca en `_app_flags` para no repetirlo, igual que el resto de migraciones.
   */
  fun migrateLegacy(legacy: KeystoreStore) {
    val flags = db.database()
    val done = flags.rawQuery("SELECT value FROM _app_flags WHERE key = 'outbox_to_sqlite'", null)
      .use { it.moveToFirst() }
    if (done) return
    val raw = runCatching { legacy.load() }.getOrNull()
    if (raw != null) {
      val entries = runCatching { org.json.JSONArray(String(raw, Charsets.UTF_8)) }.getOrNull()
      if (entries != null && count() == 0) {
        var migrated = 0
        for (index in 0 until entries.length()) {
          val entry = entries.optJSONObject(index) ?: continue
          if (entry.optString("id").isEmpty()) continue
          save(entry, isNew = true)
          migrated += 1
        }
        if (migrated > 0) Log.i(TAG, "Outbox migrado a SQLite: $migrated entrada(s)")
      }
      runCatching { legacy.clear() }
    }
    flags.execSQL("INSERT OR REPLACE INTO _app_flags (key, value) VALUES ('outbox_to_sqlite', '1')")
  }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM pending_outbox", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun clear() = db.database().execSQL("DELETE FROM pending_outbox")

  fun find(messageID: String): JSONObject? =
    db.database().rawQuery("SELECT * FROM pending_outbox WHERE msg_id = ?", arrayOf(messageID)).use {
      if (it.moveToNext()) toJson(it) else null
    }

  private fun save(entry: JSONObject, isNew: Boolean = false) {
    val values = ContentValues()
    values.put("msg_id", entry.optString("id"))
    values.put("recipient_sid", entry.optString("upeerId"))
    values.put("plaintext", cipher.seal(entry.optString("content")) ?: "")
    values.put("reply_to", entry.optString("replyTo").ifEmpty { null })
    values.put("created_at", entry.optLong("createdAt", System.currentTimeMillis()))
    values.put("kind", entry.optString("kind").ifEmpty { "CHAT" })
    values.put("target_msg_id", entry.optString("msgId").ifEmpty { null })
    values.put("version", entry.optInt("version", 0))
    values.put("attempts", entry.optInt("attempts", 0))
    values.put("next_attempt_at", entry.optLong("nextAttemptAt", 0L))
    values.put("sends", entry.optInt("sends", 0))
    values.put("timestamp", entry.optLong("timestamp", 0L))
    values.put("last_error", entry.optString("lastError").ifEmpty { null })
    values.put("sent_at", entry.optLong("sentAt", 0L).takeIf { it > 0 })
    if (entry.has("packet")) values.put("packet", cipher.seal(entry.optString("packet")))
    if (isNew) {
      db.database().insertWithOnConflict("pending_outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    } else {
      db.database().update("pending_outbox", values, "msg_id = ?", arrayOf(entry.optString("id")))
    }
  }

  private fun trim() {
    db.database().execSQL(
      "DELETE FROM pending_outbox WHERE id NOT IN (" +
        "SELECT id FROM pending_outbox ORDER BY created_at DESC LIMIT ?)",
      arrayOf(MAX_ENTRIES),
    )
  }

  private fun toJson(cursor: android.database.Cursor): JSONObject {
    fun text(column: String): String? {
      val index = cursor.getColumnIndex(column)
      return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
    }

    fun long(column: String): Long {
      val index = cursor.getColumnIndex(column)
      return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
    }

    val json = JSONObject()
      .put("id", text("msg_id").orEmpty())
      .put("kind", text("kind") ?: "CHAT")
      .put("upeerId", text("recipient_sid").orEmpty())
      .put("content", cipher.open(text("plaintext")).orEmpty())
      .put("attempts", long("attempts").toInt())
      .put("nextAttemptAt", long("next_attempt_at"))
      .put("createdAt", long("created_at"))
      .put("timestamp", long("timestamp"))
    text("reply_to")?.let { json.put("replyTo", it) }
    text("target_msg_id")?.let { json.put("msgId", it) }
    val version = long("version").toInt()
    if (version > 0) json.put("version", version)
    val sends = long("sends").toInt()
    if (sends > 0) json.put("sends", sends)
    text("last_error")?.let { json.put("lastError", it) }
    val sentAt = long("sent_at")
    if (sentAt > 0) json.put("sentAt", sentAt)
    cipher.open(text("packet"))?.let { json.put("packet", it) }
    return json
  }

  private companion object {
    const val MAX_ENTRIES = 200
    const val TAG = "UPeerTransport"
  }
}
