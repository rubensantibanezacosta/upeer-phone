package app.upeer.transport

import org.json.JSONObject

internal class ProtocolTyping(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun mark(upeerId: String, untilMillis: Long) {
    synchronized(lock) {
      val typing = state().optJSONObject("typing") ?: JSONObject()
      typing.put(upeerId, untilMillis)
      state().put("typing", typing)
      persist()
    }
  }

  fun clear(upeerId: String) {
    synchronized(lock) {
      val typing = state().optJSONObject("typing") ?: return@synchronized
      if (typing.remove(upeerId) == null) return@synchronized
      state().put("typing", typing)
      persist()
    }
  }

  fun peers(nowMillis: Long): Set<String> {
    val typing = synchronized(lock) { state().optJSONObject("typing") } ?: return emptySet()
    val active = mutableSetOf<String>()
    val stale = mutableListOf<String>()
    synchronized(lock) {
      val keys = typing.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        if (TypingIndicators.isActive(typing.optLong(key), nowMillis)) active.add(key) else stale.add(key)
      }
      if (stale.isNotEmpty()) {
        stale.forEach(typing::remove)
        state().put("typing", typing)
        persist()
      }
    }
    return active
  }
}
