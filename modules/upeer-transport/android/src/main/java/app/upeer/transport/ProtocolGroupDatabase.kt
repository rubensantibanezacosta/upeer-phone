package app.upeer.transport

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia de grupos y de las claves de remitente por época.
 *
 * Ambas cosas viven en la tabla `groups` del escritorio: las claves de remitente son las
 * columnas `sender_key`, `sender_key_created_at` y `epoch`, así que aquí se guardan en la fila
 * del propio grupo en vez de en un bloque aparte.
 */
internal class ProtocolGroupDatabase(private val db: ProtocolDatabase) {
  fun insert(groupId: String, group: JSONObject) {
    val values = ContentValues()
    values.put("group_id", groupId)
    values.put("name", group.optString("name"))
    values.put("admin_upeer_id", group.optString("adminUpeerId"))
    values.put("members", group.optJSONArray("members")?.toString() ?: "[]")
    values.put("is_favorite", if (group.optBoolean("isFavorite")) 1 else 0)
    values.put("status", group.optString("status").ifEmpty { "active" })
    values.put("avatar", group.optString("avatar").ifEmpty { null })
    values.put("epoch", group.optInt("epoch", 1))
    values.put("sender_key", group.optString("senderKey").ifEmpty { null })
    values.put("sender_key_created_at", group.optLong("senderKeyCreatedAt", 0L).takeIf { it > 0 })
    values.put("member_public_keys", group.optJSONObject("memberPublicKeys")?.toString() ?: "{}")
    values.put("created_at", group.optLong("createdAt", 0L))
    values.put("last_cleared_at", group.optLong("lastClearedAt", 0L))
    db.database().insertWithOnConflict("groups", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun insertAll(groups: Map<String, JSONObject>) = transaction {
    for ((groupId, group) in groups) insert(groupId, group)
  }

  fun delete(groupId: String) {
    db.database().delete("groups", "group_id = ?", arrayOf(groupId))
  }

  fun all(): Map<String, JSONObject> {
    val result = LinkedHashMap<String, JSONObject>()
    db.database().rawQuery("SELECT * FROM groups", null).use { cursor ->
      while (cursor.moveToNext()) {
        val groupId = cursor.text("group_id") ?: continue
        result[groupId] = toJson(groupId, cursor)
      }
    }
    return result
  }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM groups", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun clear() = db.database().execSQL("DELETE FROM groups")

  private fun Cursor.text(column: String): String? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getString(index) else null
  }

  private fun Cursor.long(column: String): Long {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else 0L
  }

  private fun toJson(groupId: String, cursor: Cursor): JSONObject {
    val json = JSONObject()
      .put("groupId", groupId)
      .put("name", cursor.text("name").orEmpty())
      .put("adminUpeerId", cursor.text("admin_upeer_id").orEmpty())
    cursor.text("members")?.let { raw -> runCatching { json.put("members", JSONArray(raw)) } }
    if (cursor.long("is_favorite") == 1L) json.put("isFavorite", true)
    cursor.text("status")?.let { json.put("status", it) }
    cursor.text("avatar")?.let { json.put("avatar", it) }
    json.put("epoch", cursor.long("epoch").takeIf { it > 0 } ?: 1L)
    cursor.text("sender_key")?.let { json.put("senderKey", it) }
    cursor.long("sender_key_created_at").takeIf { it > 0 }?.let { json.put("senderKeyCreatedAt", it) }
    cursor.text("member_public_keys")?.let { raw ->
      runCatching { json.put("memberPublicKeys", JSONObject(raw)) }
    }
    json.put("createdAt", cursor.long("created_at"))
    cursor.long("last_cleared_at").takeIf { it > 0 }?.let { json.put("lastClearedAt", it) }
    return json
  }

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
}
