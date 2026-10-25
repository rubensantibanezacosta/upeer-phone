package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal class SyncEventLog(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
  private val maxEvents: Int = MAX_EVENTS,
) {
  fun record(draft: SyncEventDraft, nowMillis: Long): SyncEventRecord? {
    if (!validEventId(draft.eventId)) return null
    if (!SyncEventKinds.isValid(draft.kind)) return null
    if (draft.occurredAt <= 0) return null
    return synchronized(lock) {
      val sync = bucket()
      val events = sync.getJSONArray("events")
      if (indexOf(events, draft.eventId) >= 0) return@synchronized null
      val sequence = SyncSequence.next(sync.optLong("sequence", 0L), nowMillis)
      val record = SyncEventRecord(
        eventId = draft.eventId,
        kind = draft.kind,
        occurredAt = draft.occurredAt,
        sequence = sequence,
        payload = draft.payload,
        direction = draft.direction,
        originDeviceId = draft.originDeviceId,
        originSignature = draft.originSignature,
        originCertificate = draft.originCertificate,
      )
      events.put(record.toJson())
      sync.put("sequence", sequence)
      sync.put("events", trimmed(events))
      state().put("sync", sync)
      persist()
      record
    }
  }

  fun maxSequence(): Long = synchronized(lock) { bucket().optLong("sequence", 0L) }

  fun contains(eventId: String): Boolean {
    if (!validEventId(eventId)) return false
    return synchronized(lock) { indexOf(bucket().getJSONArray("events"), eventId) >= 0 }
  }

  fun eventsAfter(sequence: Long, limit: Int): List<SyncEventRecord> {
    if (limit <= 0) return emptyList()
    return synchronized(lock) {
      val events = bucket().getJSONArray("events")
      val found = mutableListOf<SyncEventRecord>()
      for (index in 0 until events.length()) {
        val record = SyncEventRecord.from(events.optJSONObject(index) ?: continue) ?: continue
        if (record.sequence > sequence) found.add(record)
      }
      found.sortedBy { it.sequence }.take(limit)
    }
  }

  fun attachOrigin(eventId: String, origin: SyncOriginSignature): Boolean {
    if (!validEventId(eventId)) return false
    return synchronized(lock) {
      val sync = bucket()
      val events = sync.getJSONArray("events")
      val index = indexOf(events, eventId)
      if (index < 0) return@synchronized false
      val event = events.getJSONObject(index)
      if (event.optString("originSignature").isNotBlank()) return@synchronized false
      event.put("originDeviceId", origin.deviceId)
      event.put("originSignature", origin.signature)
      event.put("originCertificate", origin.certificate.toString())
      events.put(index, event)
      sync.put("events", events)
      state().put("sync", sync)
      persist()
      true
    }
  }

  fun recordAck(deviceId: String, sequence: Long): Boolean {    if (!validDeviceId(deviceId) || sequence <= 0) return false
    return synchronized(lock) {
      val sync = bucket()
      val acks = sync.optJSONObject("acks") ?: JSONObject()
      if (acks.optLong(deviceId, 0L) >= sequence) return@synchronized false
      acks.put(deviceId, sequence)
      sync.put("acks", acks)
      state().put("sync", sync)
      persist()
      true
    }
  }

  fun ackFor(deviceId: String): Long = synchronized(lock) {
    bucket().optJSONObject("acks")?.optLong(deviceId, 0L) ?: 0L
  }

  fun applied(eventId: String): Boolean {
    if (!validEventId(eventId)) return true
    return synchronized(lock) {
      val applied = bucket().optJSONArray("applied") ?: return@synchronized false
      (0 until applied.length()).any { applied.optString(it) == eventId }
    }
  }

  fun markApplied(eventId: String): Unit = synchronized(lock) {
    if (!validEventId(eventId)) return@synchronized
    val sync = bucket()
    val applied = sync.optJSONArray("applied") ?: org.json.JSONArray()
    if ((0 until applied.length()).any { applied.optString(it) == eventId }) return@synchronized
    applied.put(eventId)
    sync.put("applied", capped(applied))
    state().put("sync", sync)
    persist()
  }

  fun pendingApplication(limit: Int): List<SyncEventRecord> {
    if (limit <= 0) return emptyList()
    return synchronized(lock) {
      val events = bucket().getJSONArray("events")
      val known = bucket().optJSONArray("applied")
      val seen = HashSet<String>()
      if (known != null) for (index in 0 until known.length()) seen.add(known.optString(index))
      val found = mutableListOf<SyncEventRecord>()
      for (index in 0 until events.length()) {
        val record = SyncEventRecord.from(events.optJSONObject(index) ?: continue) ?: continue
        if (record.direction != "inbound") continue
        if (record.eventId in seen) continue
        found.add(record)
      }
      found.sortedBy { it.sequence }.take(limit)
    }
  }

  private fun capped(values: org.json.JSONArray): org.json.JSONArray {
    if (values.length() <= MAX_EVENTS) return values
    val kept = org.json.JSONArray()
    for (index in values.length() - MAX_EVENTS until values.length()) {
      values.optString(index).takeIf(String::isNotEmpty)?.let { kept.put(it) }
    }
    return kept
  }

  fun pendingFor(deviceId: String): Int {
    val ack = ackFor(deviceId)
    val events = synchronized(lock) { bucket().getJSONArray("events") }
    var pending = 0
    for (index in 0 until events.length()) {
      val event = events.optJSONObject(index) ?: continue
      if (event.optString("direction") != "outbound") continue
      if (event.optLong("sequence", 0L) > ack) pending += 1
    }
    return pending
  }

  private fun bucket(): JSONObject {
    val sync = state().optJSONObject("sync") ?: JSONObject()
    if (sync.optJSONArray("events") == null) sync.put("events", JSONArray())
    return sync
  }

  private fun indexOf(events: JSONArray, eventId: String): Int {
    for (index in 0 until events.length()) {
      if (events.optJSONObject(index)?.optString("eventId") == eventId) return index
    }
    return -1
  }

  private fun trimmed(events: JSONArray): JSONArray {
    if (events.length() <= maxEvents) return events
    val kept = JSONArray()
    for (index in events.length() - maxEvents until events.length()) {
      events.optJSONObject(index)?.let { kept.put(it) }
    }
    return kept
  }

  private fun validEventId(value: String): Boolean = SyncPackets.validEventId(value)

  private fun validDeviceId(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  private companion object {
    const val MAX_EVENTS = 2000
  }
}
