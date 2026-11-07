package app.upeer.transport

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Persistencia de las claves sueltas del estado (perfil, preferencias, presencia, temporal...)
 * en la tabla `app_settings`, igual que el escritorio guarda ahí sus ajustes locales.
 *
 * Cada clave del `JSONObject` de estado que no tiene tabla propia se convierte en una fila con el
 * valor serializado en JSON. Así el código existente sigue leyendo y escribiendo el mismo
 * `JSONObject` y solo cambia el sitio donde se guarda.
 */
internal class ProtocolSettingsDatabase(private val db: ProtocolDatabase) {
  fun putAll(values: Map<String, String>) = transaction {
    for ((key, json) in values) {
      val content = ContentValues()
      content.put("key", key)
      content.put("value", json)
      content.put("updated_at", System.currentTimeMillis())
      db.database().insertWithOnConflict("app_settings", null, content, SQLiteDatabase.CONFLICT_REPLACE)
    }
  }

  fun put(key: String, json: String) = putAll(mapOf(key to json))

  fun remove(key: String) {
    db.database().delete("app_settings", "key = ?", arrayOf(key))
  }

  fun all(): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    db.database().rawQuery("SELECT key, value FROM app_settings", null).use { cursor ->
      while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.getString(1)
    }
    return result
  }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM app_settings", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun clear() = db.database().execSQL("DELETE FROM app_settings")

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
