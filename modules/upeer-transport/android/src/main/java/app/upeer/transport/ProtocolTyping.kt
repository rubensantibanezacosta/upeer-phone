package app.upeer.transport

import org.json.JSONObject

internal class ProtocolTyping(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun mark(upeerId: String, activity: String, untilMillis: Long) {
    synchronized(lock) {
      val typing = state().optJSONObject("typing") ?: JSONObject()
      typing.put(upeerId, JSONObject().put("state", TypingIndicators.stateOf(activity)).put("until", untilMillis))
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

  fun activities(nowMillis: Long): Map<String, String> {
    val typing = synchronized(lock) { state().optJSONObject("typing") } ?: return emptyMap()
    val active = LinkedHashMap<String, String>()
    val stale = mutableListOf<String>()
    synchronized(lock) {
      val keys = typing.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        val entry = typing.opt(key)
        if (TypingIndicators.isActive(untilOf(entry), nowMillis)) active[key] = stateOf(entry) else stale.add(key)
      }
      if (stale.isNotEmpty()) {
        stale.forEach(typing::remove)
        state().put("typing", typing)
        persist()
      }
    }
    return active
  }

  fun peers(nowMillis: Long): Set<String> = activities(nowMillis).keys

  private fun untilOf(entry: Any?): Long = when (entry) {
    is JSONObject -> entry.optLong("until")
    is Number -> entry.toLong()
    else -> 0L
  }

  private fun stateOf(entry: Any?): String =
    if (entry is JSONObject) TypingIndicators.stateOf(entry.optString("state")) else TypingIndicators.STATE_TYPING
}
