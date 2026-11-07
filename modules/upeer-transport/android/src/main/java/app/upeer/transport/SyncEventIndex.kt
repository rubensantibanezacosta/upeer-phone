package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

/**
 * Decisiones puras del registro de eventos de sincronización.
 *
 * Viven aquí, lejos de [SyncEventLog], porque la parte delicada es que **el registro tiene dos
 * almacenes**: con SQLite la lista de eventos está en su tabla y `bucket()` no lleva `events`, de modo
 * que cualquier lectura del bloque tiene que ir por la base. Ese detalle se rompió al migrar y dejó la
 * sincronización muda con `No value for events`; estas funciones se prueban en JVM para que no vuelva.
 */
internal object SyncEventIndex {
  /** Índice del evento dentro del bloque `events`, o `-1`. */
  fun indexOf(events: JSONArray, eventId: String): Int {
    for (index in 0 until events.length()) {
      if (events.optJSONObject(index)?.optString("eventId") == eventId) return index
    }
    return -1
  }

  /**
   * Eventos pendientes de aplicar: los inbound que no estén en `applied`, ordenados por secuencia.
   *
   * Es la versión en memoria de `SyncEventDatabase.inbound`; las dos tienen que dar lo mismo.
   */
  fun pendingApplication(events: JSONArray, applied: JSONArray?, limit: Int): List<SyncEventRecord> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<String>()
    if (applied != null) for (index in 0 until applied.length()) seen.add(applied.optString(index))
    val found = ArrayList<SyncEventRecord>()
    for (index in 0 until events.length()) {
      val record = SyncEventRecord.from(events.optJSONObject(index) ?: continue) ?: continue
      if (record.direction != "inbound") continue
      if (record.eventId in seen) continue
      found.add(record)
    }
    return found.sortedBy { it.sequence }.take(limit)
  }

  /** Eventos inbound pendientes de aplicar, sin límite. Es lo que cuenta `pendingCount`. */
  fun pendingCount(events: JSONArray, applied: JSONArray?): Int {
    if (applied == null) {
      var pending = 0
      for (index in 0 until events.length()) {
        if (events.optJSONObject(index)?.optString("direction") == "inbound") pending += 1
      }
      return pending
    }
    return pendingApplication(events, applied, Int.MAX_VALUE).size
  }

  /** Una lista de eventos indexada por id, para consultas rápidas del bloque en memoria. */
  fun byId(events: JSONArray): Map<String, JSONObject> {
    val found = LinkedHashMap<String, JSONObject>()
    for (index in 0 until events.length()) {
      val event = events.optJSONObject(index) ?: continue
      val id = event.optString("eventId")
      if (id.isNotEmpty()) found[id] = event
    }
    return found
  }
}
