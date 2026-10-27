package app.upeer.transport

import org.json.JSONObject

internal data class SyncHistoryProgress(
  val cursor: String?,
  val done: Boolean,
  val applied: Int = 0,
  val total: Int = 0,
  val known: Boolean = false,
)

internal class SyncHistoryState(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun progress(deviceId: String): SyncHistoryProgress = synchronized(lock) {
    val entry = bucket().optJSONObject(deviceId)
    SyncHistoryProgress(
      cursor = entry?.optString("cursor")?.ifBlank { null },
      done = entry?.optBoolean("done", false) == true,
      applied = entry?.optInt("applied", 0) ?: 0,
      total = entry?.optInt("total", 0) ?: 0,
      known = entry?.optBoolean("known", false) == true,
    )
  }

  fun addCounters(deviceId: String, applied: Int, pending: Int?) {
    if (!validDeviceId(deviceId)) return
    synchronized(lock) {
      val history = bucket()
      val entry = history.optJSONObject(deviceId) ?: JSONObject()
      addCountersTo(entry, applied, pending)
      history.put(deviceId, entry)
      state().put(KEY, history)
      persist()
    }
  }

  fun finishCounters(deviceId: String) {
    if (!validDeviceId(deviceId)) return
    synchronized(lock) {
      val history = bucket()
      val entry = history.optJSONObject(deviceId) ?: return
      finishCountersIn(entry)
      history.put(deviceId, entry)
      state().put(KEY, history)
      persist()
    }
  }

  fun save(deviceId: String, cursor: String?, done: Boolean) {
    if (!validDeviceId(deviceId)) return
    synchronized(lock) {
      val history = bucket()
      val entry = history.optJSONObject(deviceId) ?: JSONObject()
      if (cursor.isNullOrBlank()) entry.remove("cursor") else entry.put("cursor", cursor)
      entry.put("done", done)
      history.put(deviceId, entry)
      state().put(KEY, history)
      persist()
    }
  }

  fun savePage(deviceId: String, cursor: String?, done: Boolean, applied: Int, pending: Int?) {
    if (!validDeviceId(deviceId)) return
    synchronized(lock) {
      val history = bucket()
      val entry = history.optJSONObject(deviceId) ?: JSONObject()
      if (cursor.isNullOrBlank()) entry.remove("cursor") else entry.put("cursor", cursor)
      entry.put("done", done)
      addCountersTo(entry, applied, pending)
      if (done) finishCountersIn(entry)
      history.put(deviceId, entry)
      state().put(KEY, history)
      persist()
    }
  }

  private fun addCountersTo(entry: JSONObject, applied: Int, pending: Int?) {
    val done = (entry.optInt("applied", 0) + applied.coerceAtLeast(0)).coerceAtLeast(0)
    val currentTotal = entry.optInt("total", 0).coerceAtLeast(0)
    if (pending == null) {
      entry.put("applied", done)
      entry.put("total", maxOf(currentTotal, done))
      return
    }
    val total = maxOf(currentTotal, done + pending.coerceAtLeast(0))
    entry.put("applied", minOf(done, total))
    entry.put("total", total)
    entry.put("known", true)
  }

  private fun finishCountersIn(entry: JSONObject) {
    val total = entry.optInt("total", 0).coerceAtLeast(entry.optInt("applied", 0))
    entry.put("applied", total)
    entry.put("total", total)
    entry.put("known", true)
  }

  fun clearAll() {
    synchronized(lock) {
      state().remove(KEY)
      persist()
    }
  }

  fun clear(deviceId: String) {
    synchronized(lock) {
      bucket().remove(deviceId)
      state().put(KEY, bucket())
      persist()
    }
  }

  private fun bucket(): JSONObject = state().optJSONObject(KEY) ?: JSONObject()

  private fun validDeviceId(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  private companion object {
    const val KEY = "history"
  }
}
