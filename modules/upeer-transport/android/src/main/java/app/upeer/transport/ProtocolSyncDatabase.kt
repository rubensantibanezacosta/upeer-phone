package app.upeer.transport

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Persistencia de las tres tablas de sincronización que en móvil no tenían equivalente:
 * `sync_relay`, `reputation_vouches` y `pending_outbox`.
 *
 * Se agrupan aquí porque son pequeñas y comparten la misma forma de uso: filas sueltas que se
 * insertan o se borran por clave, sin reescritura de ningún bloque completo.
 */
internal class ProtocolSyncDatabase(
  private val db: ProtocolDatabase,
  private val cipher: ProtocolCipher,
) {
  // ── sync_relay ──────────────────────────────────────────────────────────────
  fun relayInsert(eventId: String, targetDeviceId: String, sequence: Long) {
    val values = ContentValues()
    values.put("event_id", eventId)
    values.put("target_device_id", targetDeviceId)
    values.put("sequence", sequence)
    values.put("created_at", System.currentTimeMillis())
    db.database().insertWithOnConflict("sync_relay", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun relayAck(eventId: String, targetDeviceId: String) {
    val values = ContentValues()
    values.put("acked_at", System.currentTimeMillis())
    db.database().update(
      "sync_relay",
      values,
      "event_id = ? AND target_device_id = ?",
      arrayOf(eventId, targetDeviceId),
    )
  }

  fun relayPending(targetDeviceId: String): List<String> {
    val result = ArrayList<String>()
    db.database().rawQuery(
      "SELECT event_id FROM sync_relay WHERE target_device_id = ? AND acked_at IS NULL ORDER BY sequence ASC, created_at ASC",
      arrayOf(targetDeviceId),
    ).use { cursor ->
      while (cursor.moveToNext()) result.add(cursor.getString(0))
    }
    return result
  }

  fun relayCount(): Int = db.database().rawQuery("SELECT COUNT(*) FROM sync_relay", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun relayClear() = db.database().execSQL("DELETE FROM sync_relay")

  // ── reputation_vouches ──────────────────────────────────────────────────────
  fun vouchInsert(id: String, vouch: JSONObject): Boolean {
    val values = ContentValues()
    values.put("id", id)
    values.put("from_id", vouch.optString("fromId"))
    values.put("to_id", vouch.optString("toId"))
    values.put("type", vouch.optString("type"))
    values.put("positive", if (vouch.optBoolean("positive")) 1 else 0)
    values.put("timestamp", vouch.optLong("timestamp", 0L))
    values.put("signature", vouch.optString("signature"))
    values.put("received_at", System.currentTimeMillis())
    return db.database().insertWithOnConflict("reputation_vouches", null, values, SQLiteDatabase.CONFLICT_IGNORE) > -1
  }

  fun vouchesTo(toId: String): List<JSONObject> {
    val result = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT id, from_id, to_id, type, positive, timestamp, signature FROM reputation_vouches WHERE to_id = ? ORDER BY timestamp DESC",
      arrayOf(toId),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        result.add(
          JSONObject()
            .put("id", cursor.getString(0))
            .put("fromId", cursor.getString(1))
            .put("toId", cursor.getString(2))
            .put("type", cursor.getString(3))
            .put("positive", cursor.getLong(4) == 1L)
            .put("timestamp", cursor.getLong(5))
            .put("signature", cursor.getString(6)),
        )
      }
    }
    return result
  }

  fun vouchCount(): Int = db.database().rawQuery("SELECT COUNT(*) FROM reputation_vouches", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun vouchClear() = db.database().execSQL("DELETE FROM reputation_vouches")

  // ── pending_outbox ──────────────────────────────────────────────────────────
  fun outboxInsert(msgId: String, recipientSid: String, plaintext: String, replyTo: String?, createdAt: Long) {
    val values = ContentValues()
    values.put("msg_id", msgId)
    values.put("recipient_sid", recipientSid)
    values.put("plaintext", cipher.seal(plaintext))
    values.put("reply_to", replyTo)
    values.put("created_at", createdAt)
    db.database().insertWithOnConflict("pending_outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun outboxFor(recipientSid: String): List<JSONObject> {
    val result = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT msg_id, plaintext, reply_to, created_at FROM pending_outbox WHERE recipient_sid = ? ORDER BY created_at ASC",
      arrayOf(recipientSid),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val plaintext = cipher.open(cursor.getString(1)) ?: continue
        result.add(
          JSONObject()
            .put("msgId", cursor.getString(0))
            .put("plaintext", plaintext)
            .put("replyTo", if (cursor.isNull(2)) JSONObject.NULL else cursor.getString(2))
            .put("createdAt", cursor.getLong(3)),
        )
      }
    }
    return result
  }

  fun outboxDelete(msgId: String) {
    db.database().delete("pending_outbox", "msg_id = ?", arrayOf(msgId))
  }

  fun outboxCount(): Int = db.database().rawQuery("SELECT COUNT(*) FROM pending_outbox", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun outboxClear() = db.database().execSQL("DELETE FROM pending_outbox")

  // ── devices ─────────────────────────────────────────────────────────────────
  fun deviceUpsert(upeerId: String, deviceId: String, clientName: String?, platform: String?, version: String?, lastSeen: Long) {
    val values = ContentValues()
    values.put("upeer_id", upeerId)
    values.put("device_id", deviceId)
    values.put("client_name", clientName)
    values.put("platform", platform)
    values.put("client_version", version)
    values.put("last_seen", lastSeen)
    db.database().insertWithOnConflict("devices", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun devicesFor(upeerId: String): List<JSONObject> {
    val result = ArrayList<JSONObject>()
    db.database().rawQuery(
      "SELECT device_id, client_name, platform, client_version, last_seen, is_trusted FROM devices WHERE upeer_id = ? ORDER BY last_seen DESC",
      arrayOf(upeerId),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        result.add(
          JSONObject()
            .put("deviceId", cursor.getString(0))
            .put("clientName", if (cursor.isNull(1)) JSONObject.NULL else cursor.getString(1))
            .put("platform", if (cursor.isNull(2)) JSONObject.NULL else cursor.getString(2))
            .put("clientVersion", if (cursor.isNull(3)) JSONObject.NULL else cursor.getString(3))
            .put("lastSeen", cursor.getLong(4))
            .put("isTrusted", cursor.getLong(5) == 1L),
        )
      }
    }
    return result
  }

  fun deviceDelete(upeerId: String, deviceId: String) {
    db.database().delete("devices", "upeer_id = ? AND device_id = ?", arrayOf(upeerId, deviceId))
  }

  fun deviceCount(): Int = db.database().rawQuery("SELECT COUNT(*) FROM devices", null).use {
    if (it.moveToFirst()) it.getInt(0) else 0
  }

  fun deviceClear() = db.database().execSQL("DELETE FROM devices")
}
