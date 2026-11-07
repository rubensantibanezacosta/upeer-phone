package app.upeer.transport

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia de contactos en la tabla `contacts`, con las mismas columnas que
 * `upper-desktop/src/main_process/storage/schema.ts`.
 *
 * El código del módulo sigue viendo cada contacto como el `JSONObject` que tenía dentro del
 * bloque `peers`; aquí solo se traduce fila ↔ JSON.
 */
internal class ProtocolContactDatabase(private val db: ProtocolDatabase) {
  fun insert(contact: JSONObject, upeerId: String) {
    val values = ContentValues()
    values.put("upeer_id", upeerId)
    values.put("address", contact.optString("address"))
    values.put("name", contact.optString("alias"))
    values.put("is_favorite", if (contact.optBoolean("isFavorite")) 1 else 0)
    values.put("public_key", contact.optString("publicKey").ifEmpty { null })
    values.put("signed_pre_key", contact.optJSONObject("signedPreKey")?.toString())
    values.put("device_public_key", contact.optString("devicePublicKey").ifEmpty { null })
    values.put("device_id", contact.optString("deviceId").ifEmpty { null })
    values.put("device_certificate", contact.optJSONObject("deviceCertificate")?.toString())
    values.put("device_signed_pre_key", contact.optJSONObject("deviceSignedPreKey")?.toString())
    values.put("avatar", contact.optString("avatar").ifEmpty { null })
    values.put("status", contact.optString("status").ifEmpty { "connected" })
    values.put("last_seen", contact.optString("lastSeen").ifEmpty { null })
    values.put("last_active", contact.optString("lastActive").ifEmpty { null })
    values.put("last_cleared_at", contact.optLong("lastClearedAt", 0L))
    contact.optJSONArray("knownAddresses")?.let { values.put("known_addresses", it.toString()) }
    contact.optString("blockedAt").takeIf(String::isNotEmpty)?.let { values.put("blocked_at", it) }
    db.database().insertWithOnConflict("contacts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun insertAll(contacts: Map<String, JSONObject>) = transaction {
    for ((upeerId, contact) in contacts) insert(contact, upeerId)
  }

  fun delete(upeerId: String) {
    db.database().delete("contacts", "upeer_id = ?", arrayOf(upeerId))
  }

  fun all(): Map<String, JSONObject> {
    val result = LinkedHashMap<String, JSONObject>()
    db.database().rawQuery("SELECT * FROM contacts", null).use { cursor ->
      while (cursor.moveToNext()) {
        val upeerId = cursor.text("upeer_id") ?: continue
        result[upeerId] = toJson(upeerId, cursor)
      }
    }
    return result
  }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM contacts", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun clear() = db.database().execSQL("DELETE FROM contacts")

  private fun Cursor.text(column: String): String? {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getString(index) else null
  }

  private fun Cursor.long(column: String): Long {
    val index = getColumnIndex(column)
    return if (index >= 0 && !isNull(index)) getLong(index) else 0L
  }

  private fun toJson(upeerId: String, cursor: Cursor): JSONObject {
    val json = JSONObject()
    json.put("address", cursor.text("address").orEmpty())
    cursor.text("name")?.takeIf(String::isNotEmpty)?.let { json.put("alias", it) }
    if (cursor.long("is_favorite") == 1L) json.put("isFavorite", true)
    cursor.text("public_key")?.let { json.put("publicKey", it) }
    cursor.text("signed_pre_key")?.let { raw -> runCatching { json.put("signedPreKey", JSONObject(raw)) } }
    cursor.text("device_public_key")?.let { json.put("devicePublicKey", it) }
    cursor.text("device_id")?.let { json.put("deviceId", it) }
    cursor.text("device_certificate")?.let { raw -> runCatching { json.put("deviceCertificate", JSONObject(raw)) } }
    cursor.text("device_signed_pre_key")?.let { raw -> runCatching { json.put("deviceSignedPreKey", JSONObject(raw)) } }
    cursor.text("avatar")?.let { json.put("avatar", it) }
    cursor.text("status")?.let { json.put("status", it) }
    cursor.text("last_seen")?.let { json.put("lastSeen", it) }
    cursor.text("last_active")?.let { json.put("lastActive", it) }
    val cleared = cursor.long("last_cleared_at")
    if (cleared > 0) json.put("lastClearedAt", cleared)
    cursor.text("known_addresses")?.let { raw -> runCatching { json.put("knownAddresses", JSONArray(raw)) } }
    cursor.text("blocked_at")?.let { json.put("blockedAt", it) }
    json.put("upeerId", upeerId)
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
