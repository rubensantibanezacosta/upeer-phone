package app.upeer.transport

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal class OutboxStore(context: Context) {
  private val store = KeystoreStore(
    context = context,
    preferencesName = "upeer_yggdrasil_outbox",
    keyAlias = "upeer-yggdrasil-outbox",
    valueKey = "outbox_state",
  )
  private val lock = Any()
  private var entries = loadEntries()

  fun enqueue(messageID: String, upeerId: String, content: String, replyTo: String, timestamp: Long): JSONObject {
    return synchronized(lock) {
      for (index in 0 until entries.length()) {
        val existing = entries.optJSONObject(index)
        if (existing?.optString("id") == messageID) return copy(existing)
      }
      val entry = JSONObject()
        .put("id", messageID)
        .put("upeerId", upeerId)
        .put("content", content)
        .put("timestamp", timestamp)
        .put("attempts", 0)
        .put("nextAttemptAt", 0)
        .put("createdAt", System.currentTimeMillis())
      if (replyTo.isNotEmpty()) entry.put("replyTo", replyTo)
      entries.put(entry)
      trimLocked()
      persist()
      copy(entry)
    }
  }

  fun due(now: Long, limit: Int): List<JSONObject> = synchronized(lock) {
    buildList {
      for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        if (entry.optLong("nextAttemptAt", 0) <= now) add(copy(entry))
        if (size >= limit) break
      }
    }
  }

  fun markAttempt(messageID: String, attempts: Int, nextAttemptAt: Long, error: String) {
    synchronized(lock) {
      val entry = findLocked(messageID) ?: return@synchronized
      entry.put("attempts", attempts).put("nextAttemptAt", nextAttemptAt)
      if (error.isBlank()) entry.remove("lastError") else entry.put("lastError", error.take(240))
      persist()
    }
  }

  fun markSent(messageID: String, nextAttemptAt: Long) {
    synchronized(lock) {
      val entry = findLocked(messageID) ?: return@synchronized
      entry.put("nextAttemptAt", nextAttemptAt).put("sentAt", System.currentTimeMillis())
      persist()
    }
  }

  fun setPacket(messageID: String, packet: String) {
    synchronized(lock) {
      val entry = findLocked(messageID) ?: return@synchronized
      entry.put("packet", packet)
      persist()
    }
  }

  fun remove(messageID: String) {
    synchronized(lock) {
      val next = JSONArray()
      for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        if (entry.optString("id") != messageID) next.put(entry)
      }
      entries = next
      persist()
    }
  }

  fun resetPeer(upeerId: String) {
    synchronized(lock) {
      for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        if (entry.optString("upeerId") != upeerId) continue
        entry.remove("packet")
        entry.put("nextAttemptAt", 0)
      }
      persist()
    }
  }

  fun count(): Int = synchronized(lock) { entries.length() }

  fun clear() {
    synchronized(lock) {
      entries = JSONArray()
      try {
        store.clear()
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudo borrar el outbox", cause)
      }
    }
  }

  private fun findLocked(messageID: String): JSONObject? {
    for (index in 0 until entries.length()) {
      val entry = entries.optJSONObject(index) ?: continue
      if (entry.optString("id") == messageID) return entry
    }
    return null
  }

  private fun trimLocked() {
    while (entries.length() > MAX_ENTRIES) entries.remove(0)
  }

  private fun copy(value: JSONObject): JSONObject = JSONObject(value.toString())

  private fun persist() {
    try {
      store.save(entries.toString().toByteArray(Charsets.UTF_8))
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo persistir el outbox", cause)
    }
  }

  private fun loadEntries(): JSONArray {
    val encoded = try {
      store.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer el outbox", cause)
      return JSONArray()
    } ?: return JSONArray()
    return try {
      JSONArray(String(encoded, Charsets.UTF_8))
    } catch (cause: Exception) {
      Log.e(TAG, "El outbox no es JSON válido", cause)
      JSONArray()
    }
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val MAX_ENTRIES = 200
  }
}
