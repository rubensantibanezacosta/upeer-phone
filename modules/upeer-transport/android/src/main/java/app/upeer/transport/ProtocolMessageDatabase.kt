package app.upeer.transport

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia de mensajes y reacciones en SQLite, replicando las tablas `messages` y `reactions`
 * de `upper-desktop`.
 *
 * El resto del módulo sigue trabajando con el `JSONObject` de cada mensaje; esta clase solo
 * traduce entre la fila y ese JSON, de modo que añadir o leer un mensaje es un `INSERT`/`SELECT`
 * de una fila en vez de reescribir el bloque cifrado completo.
 */
internal class ProtocolMessageDatabase(private val db: ProtocolDatabase) {
  fun insert(message: JSONObject, chatUpeerId: String) {
    val values = ContentValues()
    values.put("id", message.optString("id"))
    values.put("chat_upeer_id", chatUpeerId)
    values.put("sender_upeer_id", message.optString("senderUpeerId").ifEmpty { null })
    values.put("is_mine", if (message.optBoolean("isMine")) 1 else 0)
    values.put("message", message.optString("content"))
    values.put("reply_to", message.optString("replyTo").ifEmpty { null })
    values.put("signature", message.optString("signature").ifEmpty { null })
    values.put("status", message.optString("status").ifEmpty { "sent" })
    values.put("version", message.optInt("version", 1))
    values.put("is_deleted", if (message.optBoolean("deleted")) 1 else 0)
    values.put("is_edited", if (message.optBoolean("isEdited")) 1 else 0)
    values.put("timestamp", message.optLong("timestamp", 0L))
    if (message.has("expiresAt")) values.put("expires_at", message.optLong("expiresAt", 0L))
    message.optJSONObject("attachments")?.let { values.put("delivered_to", it.toString()) }
    db.database().insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun insertAll(messages: List<Pair<JSONObject, String>>) = transaction {
    for ((message, chat) in messages) insert(message, chat)
  }

  fun delete(messageId: String) {
    db.database().delete("messages", "id = ?", arrayOf(messageId))
  }

  fun deleteConversation(chatUpeerId: String) {
    db.database().delete("messages", "chat_upeer_id = ?", arrayOf(chatUpeerId))
    db.database().execSQL(
      "DELETE FROM reactions WHERE message_id NOT IN (SELECT id FROM messages)",
    )
  }

  fun deleteExpired(now: Long): Int = db.database().delete(
    "messages",
    "expires_at IS NOT NULL AND expires_at > 0 AND expires_at <= ?",
    arrayOf(now.toString()),
  )

  fun hasExpired(now: Long): Boolean = db.database().rawQuery(
    "SELECT 1 FROM messages WHERE expires_at IS NOT NULL AND expires_at > 0 AND expires_at <= ? LIMIT 1",
    arrayOf(now.toString()),
  ).use { it.moveToFirst() }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM messages", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  /** Mensajes de una conversación, del más antiguo al más nuevo, limitado a los `limit` últimos. */
  fun conversation(chatUpeerId: String, limit: Int): JSONArray {
    val capped = limit.coerceAtLeast(1)
    val rows = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT * FROM messages WHERE chat_upeer_id = ? ORDER BY timestamp DESC, id DESC LIMIT ?",
      arrayOf(chatUpeerId, capped.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) rows.add(toJson(cursor))
    }
    val ordered = JSONArray()
    for (index in rows.indices.reversed()) ordered.put(rows[index])
    attachReactions(ordered)
    return ordered
  }

  fun historyBefore(chatUpeerId: String, beforeTimestamp: Long, limit: Int): JSONArray {
    val rows = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT * FROM messages WHERE chat_upeer_id = ? AND timestamp < ? " +
        "ORDER BY timestamp DESC, id DESC LIMIT ?",
      arrayOf(chatUpeerId, beforeTimestamp.toString(), limit.coerceAtLeast(1).toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) rows.add(toJson(cursor))
    }
    val ordered = JSONArray()
    for (index in rows.indices.reversed()) ordered.put(rows[index])
    attachReactions(ordered)
    return ordered
  }

  fun byIds(ids: List<String>, limit: Int): JSONArray {
    if (ids.isEmpty()) return JSONArray()
    val placeholders = ids.joinToString(",") { "?" }
    val rows = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT * FROM messages WHERE id IN ($placeholders) ORDER BY timestamp DESC, id DESC LIMIT ?",
      (ids + limit.coerceAtLeast(1).toString()).toTypedArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) rows.add(toJson(cursor))
    }
    val ordered = JSONArray()
    rows.forEach { ordered.put(it) }
    attachReactions(ordered)
    return ordered
  }

  fun search(query: String, limit: Int): JSONArray {
    val rows = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT * FROM messages WHERE is_deleted = 0 AND message LIKE ? ORDER BY timestamp DESC LIMIT ?",
      arrayOf("%$query%", limit.coerceAtLeast(1).toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) rows.add(toJson(cursor))
    }
    val ordered = JSONArray()
    rows.forEach { ordered.put(it) }
    return ordered
  }

  fun all(): JSONArray {
    val ordered = JSONArray()
    db.database().rawQuery("SELECT * FROM messages ORDER BY timestamp ASC, id ASC", null).use { cursor ->
      while (cursor.moveToNext()) ordered.put(toJson(cursor))
    }
    attachReactions(ordered)
    return ordered
  }

  fun replaceReactions(messageId: String, reactions: JSONObject) {
    val database = db.database()
    database.delete("reactions", "message_id = ?", arrayOf(messageId))
    val keys = reactions.keys()
    while (keys.hasNext()) {
      val actor = keys.next()
      val emoji = reactions.optString(actor)
      if (emoji.isEmpty()) continue
      val values = ContentValues()
      values.put("message_id", messageId)
      values.put("upeer_id", actor)
      values.put("emoji", emoji)
      values.put("timestamp", System.currentTimeMillis())
      database.insertWithOnConflict("reactions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
  }

  private fun attachReactions(messages: JSONArray) {
    if (messages.length() == 0) return
    val ids = ArrayList<String>()
    for (index in 0 until messages.length()) {
      messages.optJSONObject(index)?.optString("id")?.takeIf(String::isNotEmpty)?.let(ids::add)
    }
    if (ids.isEmpty()) return
    val byMessage = HashMap<String, JSONObject>()
    val placeholders = ids.joinToString(",") { "?" }
    db.database().rawQuery(
      "SELECT message_id, upeer_id, emoji FROM reactions WHERE message_id IN ($placeholders)",
      ids.toTypedArray(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val reactions = byMessage.getOrPut(cursor.getString(0)) { JSONObject() }
        reactions.put(cursor.getString(1), cursor.getString(2))
      }
    }
    for (index in 0 until messages.length()) {
      val message = messages.optJSONObject(index) ?: continue
      val reactions = byMessage[message.optString("id")] ?: continue
      message.put("reactions", reactions)
    }
  }

  private fun toJson(cursor: android.database.Cursor): JSONObject {
    fun text(column: String): String? = cursor.getColumnIndex(column)
      .takeIf { it >= 0 && !cursor.isNull(it) }
      ?.let(cursor::getString)

    fun long(column: String): Long? = cursor.getColumnIndex(column)
      .takeIf { it >= 0 && !cursor.isNull(it) }
      ?.let(cursor::getLong)

    val json = JSONObject()
    json.put("id", text("id").orEmpty())
    json.put("upeerId", text("chat_upeer_id").orEmpty())
    text("sender_upeer_id")?.let { json.put("senderUpeerId", it) }
    json.put("isMine", (long("is_mine") ?: 0L) == 1L)
    json.put("content", text("message").orEmpty())
    text("reply_to")?.let { json.put("replyTo", it) }
    text("signature")?.let { json.put("signature", it) }
    json.put("status", text("status") ?: "sent")
    json.put("version", long("version") ?: 1L)
    if ((long("is_deleted") ?: 0L) == 1L) json.put("deleted", true)
    if ((long("is_edited") ?: 0L) == 1L) json.put("isEdited", true)
    json.put("timestamp", long("timestamp") ?: 0L)
    long("expires_at")?.takeIf { it > 0 }?.let { json.put("expiresAt", it) }
    text("delivered_to")?.let { raw ->
      runCatching { json.put("attachments", JSONObject(raw)) }
    }
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
