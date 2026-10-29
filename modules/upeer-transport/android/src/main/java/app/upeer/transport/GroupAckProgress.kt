package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal object GroupAckProgress {
  const val FIELD = "deliveredTo"

  fun recipients(members: List<String>, selfUpeerId: String): Set<String> {
    val self = selfUpeerId.lowercase()
    return members
      .map { it.lowercase() }
      .filter { it.isNotEmpty() && it != self }
      .toSet()
  }

  fun delivered(message: JSONObject): Set<String> {
    val raw = message.optJSONArray(FIELD) ?: return emptySet()
    return (0 until raw.length())
      .map { raw.optString(it).lowercase() }
      .filter { it.isNotEmpty() }
      .toSet()
  }

  fun record(message: JSONObject, memberUpeerId: String): Set<String> {
    val member = memberUpeerId.lowercase()
    if (member.isEmpty()) return delivered(message)
    val next = delivered(message) + member
    val array = JSONArray()
    next.sorted().forEach { array.put(it) }
    message.put(FIELD, array)
    return next
  }

  fun complete(delivered: Set<String>, expected: Set<String>): Boolean =
    expected.isNotEmpty() && delivered.containsAll(expected)
}
