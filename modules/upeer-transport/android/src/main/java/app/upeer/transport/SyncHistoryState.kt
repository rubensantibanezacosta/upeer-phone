package app.upeer.transport

import org.json.JSONObject

internal data class SyncHistoryProgress(val cursor: String?, val done: Boolean)

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
    )
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

  private fun bucket(): JSONObject = state().optJSONObject(KEY) ?: JSONObject()

  private fun validDeviceId(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  private companion object {
    const val KEY = "history"
  }
}
