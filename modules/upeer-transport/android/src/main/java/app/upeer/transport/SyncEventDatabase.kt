package app.upeer.transport

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Registro de eventos de sincronización, en la tabla `sync_events` de la base del protocolo.
 *
 * Antes abría su **propio** fichero (`sync-events.db`), de cuando el estado no tenía tablas. Al migrar
 * todo a SQLite eso quedó descuadrado con el esquema del escritorio: había dos bases y los eventos
 * recibidos se escribían en la que ya no se creaba. Ahora comparte [ProtocolDatabase], que es la única
 * que tiene `sync_events`, y el fichero viejo se vuelca una sola vez si existe.
 */
internal class SyncEventDatabase(private val shared: ProtocolDatabase) {
  private val lock = Any()

  private fun database(): SQLiteDatabase = shared.database()

  fun insert(record: SyncEventRecord): Boolean = synchronized(lock) {
    val database = database()
    val values = android.content.ContentValues()
    values.put("event_id", record.eventId)
    values.put("origin_device_id", record.originDeviceId.orEmpty())
    values.put("kind", record.kind)
    values.put("occurred_at", record.occurredAt)
    values.put("sequence", record.sequence)
    values.put("payload", record.payload.toString())
    values.put("origin_signature", record.originSignature)
    values.put("origin_certificate", record.originCertificate?.toString())
    values.put("entity_id", null as String?)
    values.put("direction", record.direction)
    values.put("applied_at", System.currentTimeMillis())
    database.insertWithOnConflict("sync_events", null, values, SQLiteDatabase.CONFLICT_IGNORE) > -1
  }

  fun maxSequence(): Long = synchronized(lock) {
    database().rawQuery("SELECT MAX(sequence) AS max FROM sync_events", null).use { cursor ->
      if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }
  }

  fun count(): Int = synchronized(lock) {
    database().rawQuery("SELECT COUNT(*) FROM sync_events", null).use { cursor ->
      if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
  }

  fun contains(eventId: String): Boolean = synchronized(lock) {
    database().rawQuery("SELECT 1 FROM sync_events WHERE event_id = ?", arrayOf(eventId)).use { it.moveToFirst() }
  }

  fun all(): JSONArray = synchronized(lock) {
    val result = JSONArray()
    database().rawQuery("SELECT * FROM sync_events ORDER BY sequence ASC, occurred_at ASC", null).use { cursor ->
      while (cursor.moveToNext()) result.put(cursorToRecord(cursor))
    }
    result
  }

  fun inbound(limit: Int): JSONArray = synchronized(lock) {
    val result = JSONArray()
    database().rawQuery(
      "SELECT * FROM sync_events WHERE direction = 'inbound' ORDER BY occurred_at DESC LIMIT ?",
      arrayOf(limit.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) result.put(cursorToRecord(cursor))
    }
    result
  }

  /**
   * Entrantes que aún no están en [applied], de más antiguo a más nuevo, como mucho [limit].
   *
   * Antes se pedía [inbound] y se descartaban los ya aplicados en Kotlin, pero el `LIMIT` se
   * aplicaba en SQL sobre *todos* los entrantes: con 214 entrantes y un tope de 500 la página traía
   * siempre los mismos 214 y no quedaba ninguna posición para lo pendiente de verdad. El `NOT IN`
   * tiene que ir en el `WHERE` para que el `LIMIT` cuente solo lo que falta.
   *
   * El `NOT IN` se envía como marcadores (`?`) y no interpolando la lista: son cientos de ids y
   * concatenarlos en el SQL lo dejaría expuesto a comillas y a cadenas gigantes.
   */
  fun pendingInbound(applied: Collection<String>, limit: Int): JSONArray = synchronized(lock) {
    if (limit <= 0) return@synchronized JSONArray()
    // SQLite admite ~999 variables por consulta. Por encima de eso (posible con historial largo) se
    // recorta el filtro a los ids más recientes: los que quedan fuera ya se descartan en Kotlin, así
    // que lo peor que pasa es perder un poco de precisión en la página, nunca aplicar de más.
    val skip = if (applied.size > MAX_APPLIED_FILTER) applied.toList().takeLast(MAX_APPLIED_FILTER).toSet() else applied.toSet()
    val filter = if (skip.isEmpty()) {
      ""
    } else {
      " AND event_id NOT IN (" + skip.joinToString(",") { "?" } + ")"
    }
    val args = if (skip.isEmpty()) arrayOf(limit.toString()) else (skip + limit.toString()).toTypedArray()
    val result = JSONArray()
    database().rawQuery(
      "SELECT * FROM sync_events WHERE direction = 'inbound'$filter " +
        "ORDER BY occurred_at ASC, sequence ASC LIMIT ?",
      args,
    ).use { cursor ->
      while (cursor.moveToNext()) result.put(cursorToRecord(cursor))
    }
    result
  }

  /** Entrantes de un tipo, con el filtro en SQL para que el límite no se gaste en otros tipos. */
  fun inboundOfKind(kind: String, limit: Int): JSONArray = synchronized(lock) {
    val result = JSONArray()
    database().rawQuery(
      "SELECT * FROM sync_events WHERE direction = 'inbound' AND kind = ? ORDER BY occurred_at DESC LIMIT ?",
      arrayOf(kind, limit.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) result.put(cursorToRecord(cursor))
    }
    result
  }

  fun afterSequence(sequence: Long, limit: Int): JSONArray = synchronized(lock) {
    val result = JSONArray()
    database().rawQuery(
      "SELECT * FROM sync_events WHERE sequence > ? ORDER BY sequence ASC LIMIT ?",
      arrayOf(sequence.toString(), limit.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) result.put(cursorToRecord(cursor))
    }
    result
  }

  /** Recuento por dirección, equivalente al recorrido del bloque `events` en memoria. */
  fun byDirection(direction: String): List<SyncEventRecord> = synchronized(lock) {
    val found = ArrayList<SyncEventRecord>()
    database().rawQuery(
      "SELECT * FROM sync_events WHERE direction = ?",
      arrayOf(direction),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        SyncEventRecord.from(cursorToRecord(cursor))?.let(found::add)
      }
    }
    found
  }

  fun pendingFor(direction: String, ack: Long): Int = synchronized(lock) {
    database().rawQuery(
      "SELECT COUNT(*) FROM sync_events WHERE direction = ? AND sequence > ?",
      arrayOf(direction, ack.toString()),
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
  }

  /** Eventos inbound que hay que retransmitir a otro dispositivo propio (tabla `sync_relay`). */
  fun relayEnqueue(eventId: String, targetDeviceId: String, sequence: Long): Boolean = synchronized(lock) {
    relayEnqueueUnlocked(eventId, targetDeviceId, sequence)
  }

  fun relayMarkAcked(eventId: String, targetDeviceId: String): Boolean = synchronized(lock) {
    val values = android.content.ContentValues()
    values.put("acked_at", System.currentTimeMillis())
    database().update(
      "sync_relay",
      values,
      "event_id = ? AND target_device_id = ? AND acked_at IS NULL",
      arrayOf(eventId, targetDeviceId),
    ) > 0
  }

  /** Eventos pendientes de retransmitir, unidos con su contenido, como `listPendingSyncRelay`. */
  fun relayPending(targetDeviceId: String, limit: Int): JSONArray = synchronized(lock) {
    val result = JSONArray()
    database().rawQuery(
      "SELECT e.* FROM sync_relay r INNER JOIN sync_events e ON e.event_id = r.event_id " +
        "WHERE r.target_device_id = ? AND r.acked_at IS NULL " +
        "ORDER BY r.sequence ASC, r.event_id ASC LIMIT ?",
      arrayOf(targetDeviceId, limit.toString()),
    ).use { cursor ->
      while (cursor.moveToNext()) result.put(cursorToRecord(cursor))
    }
    result
  }

  /**
   * Encola para `targetDeviceId` todos los eventos inbound que aún no se le han retransmitido.
   * Equivale a `enqueueSyncRelayBacklog` del escritorio.
   */
  fun relayBacklog(targetDeviceId: String): Int = synchronized(lock) {
    database().rawQuery(
      "SELECT event_id, sequence FROM sync_events WHERE direction = 'inbound' " +
        "AND origin_device_id != ? " +
        "AND event_id NOT IN (SELECT event_id FROM sync_relay WHERE target_device_id = ?)",
      arrayOf(targetDeviceId, targetDeviceId),
    ).use { cursor ->
      var queued = 0
      while (cursor.moveToNext()) {
        if (relayEnqueueUnlocked(cursor.getString(0), targetDeviceId, cursor.getLong(1))) queued += 1
      }
      queued
    }
  }

  private fun relayEnqueueUnlocked(eventId: String, targetDeviceId: String, sequence: Long): Boolean {
    val values = android.content.ContentValues()
    values.put("event_id", eventId)
    values.put("target_device_id", targetDeviceId)
    values.put("sequence", sequence)
    values.put("created_at", System.currentTimeMillis())
    return database().insertWithOnConflict("sync_relay", null, values, SQLiteDatabase.CONFLICT_IGNORE) > -1
  }

  fun relayCount(): Int = synchronized(lock) {
    database().rawQuery("SELECT COUNT(*) FROM sync_relay WHERE acked_at IS NULL", null).use {
      if (it.moveToFirst()) it.getInt(0) else 0
    }
  }

  fun setOrigin(eventId: String, deviceId: String, signature: String, certificate: String): Boolean = synchronized(lock) {
    val values = android.content.ContentValues()
    values.put("origin_device_id", deviceId)
    values.put("origin_signature", signature)
    values.put("origin_certificate", certificate)
    val updated = database().update(
      "sync_events",
      values,
      "event_id = ? AND (origin_signature IS NULL OR origin_signature = '')",
      arrayOf(eventId),
    )
    updated > 0
  }

  fun trim(maxEvents: Int): Int = synchronized(lock) {
    val total = count()
    if (total <= maxEvents) return@synchronized 0
    database().delete(
      "sync_events",
      "event_id IN (SELECT event_id FROM sync_events ORDER BY sequence ASC LIMIT ?)",
      arrayOf((total - maxEvents).toString()),
    )
  }

  /** Vuelca el fichero suelto de una versión anterior y lo retira. */
  fun migrateLegacyFile(context: Context) {
    val legacy = File(context.filesDir, "sync-events.db")
    if (!legacy.isFile) return
    runCatching {
      val rows = SQLiteDatabase.openDatabase(legacy.path, null, SQLiteDatabase.OPEN_READONLY)
      var migrated = 0
      rows.rawQuery("SELECT * FROM sync_events", null).use { cursor ->
        while (cursor.moveToNext()) {
          val record = SyncEventRecord.from(cursorToRecord(cursor)) ?: continue
          if (insert(record)) migrated += 1
        }
      }
      rows.close()
      Log.i(TAG, "Eventos migrados desde sync-events.db: $migrated")
      legacy.delete()
    }.onFailure { cause ->
      Log.w(TAG, "No se pudieron migrar los eventos de sync-events.db", cause)
    }
  }

  fun migrateFrom(sync: JSONObject?) {
    val legacy = sync?.optJSONArray("events") ?: return
    if (legacy.length() == 0) return
    synchronized(lock) {
      if (count() > 0) return@synchronized
      for (index in 0 until legacy.length()) {
        val raw = legacy.optJSONObject(index) ?: continue
        val record = SyncEventRecord.from(raw) ?: continue
        insert(record)
      }
      if (count() > 0) Log.i(TAG, "Histórico de sincronización migrado a SQLite: ${count()} eventos")
    }
  }

  /** Borra los eventos de sincronización de la cuenta; no cierra la base compartida. */
  fun clear() {
    synchronized(lock) {
      database().execSQL("DELETE FROM sync_events")
      database().execSQL("DELETE FROM sync_relay")
    }
  }

  private fun cursorToRecord(cursor: android.database.Cursor): JSONObject {
    val record = JSONObject()
    record.put("eventId", cursor.getString(cursor.getColumnIndexOrThrow("event_id")))
    record.put("kind", cursor.getString(cursor.getColumnIndexOrThrow("kind")))
    record.put("occurredAt", cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at")))
    record.put("sequence", cursor.getLong(cursor.getColumnIndexOrThrow("sequence")))
    record.put("payload", cursor.getString(cursor.getColumnIndexOrThrow("payload")))
    record.put("direction", cursor.getString(cursor.getColumnIndexOrThrow("direction")))
    val origin = cursor.getColumnIndexOrThrow("origin_device_id")
    if (!cursor.isNull(origin)) record.put("originDeviceId", cursor.getString(origin))
    val signature = cursor.getColumnIndexOrThrow("origin_signature")
    if (!cursor.isNull(signature)) record.put("originSignature", cursor.getString(signature))
    val certificate = cursor.getColumnIndexOrThrow("origin_certificate")
    if (!cursor.isNull(certificate)) record.put("originCertificate", cursor.getString(certificate))
    return record
  }

  private companion object {
    const val TAG = "UPeerTransport"

    /** Tope de ids en el `NOT IN`, muy por debajo de las ~999 variables que admite SQLite. */
    const val MAX_APPLIED_FILTER = 800
  }
}
