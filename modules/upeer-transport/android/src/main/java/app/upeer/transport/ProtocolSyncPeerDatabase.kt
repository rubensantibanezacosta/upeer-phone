package app.upeer.transport

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Persistencia de los pares de sincronización en la tabla `sync_peers`.
 *
 * Además de los datos de identidad del dispositivo par, la tabla guarda el estado del protocolo de
 * sincronización con ese par (`last_sequence`, `ack_sequence`, `history_cursor`,
 * `history_completed`), que en el móvil vivía dentro del bloque `sync`.
 */
internal class ProtocolSyncPeerDatabase(private val db: ProtocolDatabase) {
  fun insert(deviceId: String, peer: JSONObject) {
    val values = ContentValues()
    values.put("device_id", deviceId)
    values.put("upeer_id", peer.optString("upeerId"))
    values.put("device_public_key", peer.optString("devicePublicKey"))
    values.put("device_certificate", peer.optString("certificate"))
    values.put("device_signed_pre_key", peer.optJSONObject("signedPreKey")?.toString() ?: "{}")
    values.put("device_signed_pre_key_signature", peer.optString("signedPreKeySignature"))
    values.put("device_signed_pre_key_id", peer.optInt("signedPreKeyId", 0))
    values.put("address", peer.optString("address").ifEmpty { null })
    values.put("last_seen", peer.optLong("lastSeen", System.currentTimeMillis()))
    values.put("last_sequence", peer.optLong("lastSequence", 0L))
    values.put("ack_sequence", peer.optLong("ackSequence", 0L))
    values.put("history_cursor", peer.optString("historyCursor").ifEmpty { null })
    values.put("history_completed", if (peer.optBoolean("historyCompleted")) 1 else 0)
    db.database().insertWithOnConflict("sync_peers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun insertAll(peers: Map<String, JSONObject>) = transaction {
    for ((deviceId, peer) in peers) insert(deviceId, peer)
  }

  fun delete(deviceId: String) {
    db.database().delete("sync_peers", "device_id = ?", arrayOf(deviceId))
  }

  fun all(): Map<String, JSONObject> {
    val result = LinkedHashMap<String, JSONObject>()
    db.database().rawQuery("SELECT * FROM sync_peers", null).use { cursor ->
      while (cursor.moveToNext()) {
        val json = JSONObject()
        cursor.getString(0)?.let { json.put("deviceId", it) }
        json.put("upeerId", cursor.getStringOrNull(1).orEmpty())
        json.put("devicePublicKey", cursor.getStringOrNull(2).orEmpty())
        json.put("certificate", cursor.getStringOrNull(3).orEmpty())
        cursor.getStringOrNull(4)?.let { raw -> runCatching { json.put("signedPreKey", JSONObject(raw)) } }
        cursor.getStringOrNull(5)?.let { json.put("signedPreKeySignature", it) }
        json.put("signedPreKeyId", cursor.getLong(6))
        cursor.getStringOrNull(7)?.let { json.put("address", it) }
        json.put("lastSeen", cursor.getLong(8))
        json.put("lastSequence", cursor.getLong(9))
        json.put("ackSequence", cursor.getLong(10))
        cursor.getStringOrNull(11)?.let { json.put("historyCursor", it) }
        if (cursor.getLong(12) == 1L) json.put("historyCompleted", true)
        result[cursor.getString(0)] = json
      }
    }
    return result
  }

  fun count(): Int = db.database().rawQuery("SELECT COUNT(*) FROM sync_peers", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun clear() = db.database().execSQL("DELETE FROM sync_peers")

  private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

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
