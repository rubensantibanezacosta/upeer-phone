package app.upeer.transport

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Persistencia de las sesiones Double Ratchet en la tabla `ratchet_sessions`.
 *
 * La columna `state` guarda el JSON de la sesión cifrado con la clave del AndroidKeyStore
 * ([ProtocolCipher]): en el escritorio ese material lo protege SQLCipher, aquí se protege columna
 * a columna. La clave primaria es la pareja (upeerId, contexto), igual que en el esquema original.
 */
internal class ProtocolSessionDatabase(
  private val db: ProtocolDatabase,
  private val cipher: ProtocolCipher,
) {
  fun insert(upeerId: String, contextId: String, sessionJSON: String, nowMillis: Long = System.currentTimeMillis()) {
    val sealed = cipher.seal(sessionJSON) ?: return
    val values = ContentValues()
    values.put("upeer_id", upeerId)
    values.put("context_id", contextId.ifEmpty { DIRECT_CONTEXT })
    values.put("state", sealed)
    values.put("established_at", nowMillis)
    values.put("updated_at", nowMillis)
    db.database().insertWithOnConflict("ratchet_sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun insertAll(sessions: Map<String, JSONObject>) = transaction {
    for ((key, session) in sessions) {
      val (upeerId, contextId) = splitKey(key)
      insert(upeerId, contextId, session.toString())
    }
  }

  fun delete(upeerId: String, contextId: String) {
    db.database().delete(
      "ratchet_sessions",
      "upeer_id = ? AND context_id = ?",
      arrayOf(upeerId, contextId.ifEmpty { DIRECT_CONTEXT }),
    )
  }

  fun deletePeer(upeerId: String) {
    db.database().delete("ratchet_sessions", "upeer_id = ?", arrayOf(upeerId))
  }

  /** Devuelve las sesiones indexadas por la misma clave que usaba el bloque `sessions`. */
  fun all(): Map<String, JSONObject> {
    val result = LinkedHashMap<String, JSONObject>()
    db.database().rawQuery("SELECT upeer_id, context_id, state FROM ratchet_sessions", null).use { cursor ->
      while (cursor.moveToNext()) {
        val upeerId = cursor.getString(0)
        val contextId = cursor.getString(1).orEmpty()
        val raw = cipher.open(cursor.getString(2)) ?: continue
        val session = runCatching { JSONObject(raw) }.getOrNull() ?: continue
        result[joinKey(upeerId, contextId)] = session
      }
    }
    return result
  }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM ratchet_sessions", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun clear() = db.database().execSQL("DELETE FROM ratchet_sessions")

  private fun splitKey(key: String): Pair<String, String> {
    val separator = key.indexOf(KEY_SEPARATOR)
    if (separator < 0) return key to DIRECT_CONTEXT
    return key.substring(0, separator) to key.substring(separator + 1)
  }

  private fun joinKey(upeerId: String, contextId: String): String =
    if (contextId.isEmpty() || contextId == DIRECT_CONTEXT) upeerId else "$upeerId$KEY_SEPARATOR$contextId"

  private fun <T> transaction(block: () -> T): T {
    val database = db.database()
    database.beginTransaction()
    return try {
      val result = block()
      database.setTransactionSuccessful()
      result
    } finally {
      database.endTransaction()
    }
  }

  private companion object {
    const val KEY_SEPARATOR = '|'
    const val DIRECT_CONTEXT = "direct"
  }
}
