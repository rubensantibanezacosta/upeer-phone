package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal class ProtocolGroups(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun upsert(
    payload: JSONObject,
    nowMillis: Long = System.currentTimeMillis(),
    clearAvatar: Boolean = false,
  ): Boolean {
    val groupId = payload.optString("groupId")
    if (!validId(groupId)) return false
    val name = payload.optString("name").trim()
    if (name.isEmpty() || name.length > MAX_NAME) return false
    val admin = payload.optString("adminUpeerId")
    if (admin.length > MAX_ADMIN) return false
    val members = payload.optJSONArray("members") ?: return false
    if (members.length() > MAX_MEMBERS) return false
    val status = payload.optString("status")
    if (status !in STATUSES) return false
    val avatar = ProfileFields.avatar(payload.optString("avatar"))
    return synchronized(lock) {
      val groups = bucket()
      val existing = groups.optJSONObject(groupId)
      val entry = JSONObject()
        .put("groupId", groupId)
        .put("name", name)
        .put("adminUpeerId", admin)
        .put("members", JSONArray(members.toString()))
        .put("status", status)
        .put("isFavorite", if (payload.has("isFavorite")) payload.optBoolean("isFavorite") else existing?.optBoolean("isFavorite") ?: false)
        .put("updatedAt", nowMillis)
      if (avatar.isNotEmpty()) {
        entry.put("avatar", avatar)
      } else if (!clearAvatar) {
        existing?.optString("avatar")?.takeIf(String::isNotEmpty)?.let { entry.put("avatar", it) }
      }
      val createdAt = payload.optLong("createdAt", 0L).takeIf { it > 0 }
        ?: existing?.optLong("createdAt", 0L)?.takeIf { it > 0 }
      createdAt?.let { entry.put("createdAt", it) }
      groups.put(groupId, entry)
      state().put("groups", groups)
      persist()
      true
    }
  }

  fun delete(groupId: String): Boolean {
    if (!validId(groupId)) return false
    return synchronized(lock) {
      val groups = bucket()
      if (!groups.has(groupId)) return@synchronized false
      groups.remove(groupId)
      state().put("groups", groups)
      persist()
      true
    }
  }

  fun get(groupId: String): JSONObject? = synchronized(lock) {
    bucket().optJSONObject(groupId)?.let { JSONObject(it.toString()) }
  }

  fun title(groupId: String): String = get(groupId)?.optString("name").orEmpty()

  fun snapshot(): JSONArray = synchronized(lock) {
    val groups = bucket()
    val result = JSONArray()
    groups.keys().asSequence().sorted().forEach { key ->
      groups.optJSONObject(key)?.let { result.put(JSONObject(it.toString())) }
    }
    result
  }

  fun index(): Map<String, JSONObject> = synchronized(lock) {
    val groups = bucket()
    groups.keys().asSequence().associateWith { key ->
      groups.optJSONObject(key)?.let { JSONObject(it.toString()) } ?: JSONObject()
    }
  }

  private fun bucket(): JSONObject = state().optJSONObject("groups") ?: JSONObject()

  companion object {
    const val PREFIX = "grp-"
    const val MAX_NAME = 200
    private const val MAX_ADMIN = 128
    private const val MAX_MEMBERS = 5_000
    private val STATUSES = setOf("active", "invited", "removed")

    fun validId(value: String): Boolean {
      if (!value.startsWith(PREFIX) || value.length <= PREFIX.length || value.length > 128) return false
      return value.drop(PREFIX.length).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == '-' }
    }
  }
}
