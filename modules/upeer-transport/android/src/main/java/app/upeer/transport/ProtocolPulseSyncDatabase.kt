package app.upeer.transport

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.util.UUID

/**
 * Checkpoint de contactos para dispositivos gemelos (`backup_pulse_sync`).
 *
 * Réplica de `upper-desktop/src/main_process/storage/backup/pulse-sync.ts`: se guarda una foto del
 * estado global del usuario (contactos activos con su bloque de ubicación) durante 60 días, para
 * poder recuperarla en otro dispositivo propio o como respaldo de emergencia.
 *
 * `data` se cifra por columna: contiene claves públicas y bloques de ubicación de los contactos.
 */
internal class ProtocolPulseSyncDatabase(
  private val db: ProtocolDatabase,
  private val cipher: ProtocolCipher,
) {
  /** Crea un checkpoint con los contactos indicados y devuelve su `kitId`. */
  fun create(
    name: String,
    description: String?,
    contacts: List<JSONObject>,
    nowMillis: Long = System.currentTimeMillis(),
  ): String? {
    if (name.isBlank()) return null
    val kitId = UUID.randomUUID().toString()
    val payload = JSONObject()
      .put("version", "1.0")
      .put("timestamp", nowMillis)
      .put("contacts", org.json.JSONArray(contacts.take(MAX_CONTACTS)))
    val values = ContentValues()
    values.put("kit_id", kitId)
    values.put("name", name.take(120))
    values.put("description", description?.take(240))
    values.put("data", cipher.seal(payload.toString()) ?: return null)
    values.put("expires", nowMillis + TTL_MILLIS)
    values.put("is_active", 1)
    val id = db.database().insertWithOnConflict("backup_pulse_sync", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    return if (id > 0) kitId else null
  }

  fun get(kitId: String): JSONObject? {
    val raw = db.database().rawQuery(
      "SELECT data, expires FROM backup_pulse_sync WHERE kit_id = ? AND is_active = 1",
      arrayOf(kitId),
    ).use { if (it.moveToNext()) it.getString(0) to it.getLong(1) else null } ?: return null
    val opened = cipher.open(raw.first) ?: return null
    val payload = runCatching { JSONObject(opened) }.getOrNull() ?: return null
    return payload.put("kitId", kitId).put("expires", raw.second)
  }

  fun active(): List<String> {
    val found = ArrayList<String>()
    db.database().rawQuery(
      "SELECT kit_id FROM backup_pulse_sync WHERE is_active = 1 AND expires > ? ORDER BY created DESC",
      arrayOf(System.currentTimeMillis().toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) found.add(cursor.getString(0))
    }
    return found
  }

  fun deactivate(kitId: String): Boolean {
    val values = ContentValues()
    values.put("is_active", 0)
    return db.database().update("backup_pulse_sync", values, "kit_id = ?", arrayOf(kitId)) > 0
  }

  fun purgeExpired(nowMillis: Long = System.currentTimeMillis()): Int {
    val values = ContentValues()
    values.put("is_active", 0)
    return db.database().update(
      "backup_pulse_sync",
      values,
      "is_active = 1 AND expires > 0 AND expires <= ?",
      arrayOf(nowMillis.toString()),
    )
  }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM backup_pulse_sync", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun clear() = db.database().execSQL("DELETE FROM backup_pulse_sync")

  private companion object {
    const val MAX_CONTACTS = 50
    const val TTL_MILLIS = 60L * 24 * 60 * 60 * 1000
  }
}
