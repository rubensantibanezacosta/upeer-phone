package app.upeer.transport

import org.json.JSONObject

internal class ProtocolGroupKeys(
  private val state: () -> JSONObject,
  private val lock: Any,
  private val persist: () -> Unit,
) {
  fun put(groupId: String, epoch: Int, senderKey: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!ProtocolGroups.validId(groupId)) return false
    if (!validEpoch(epoch) || !validSenderKey(senderKey)) return false
    return synchronized(lock) {
      val keys = bucket()
      keys.put(
        groupId,
        JSONObject()
          .put("epoch", epoch)
          .put("senderKey", senderKey.lowercase())
          .put("createdAt", nowMillis),
      )
      state().put(KEY_BUCKET, keys)
      persist()
      true
    }
  }

  fun epoch(groupId: String): Int? = synchronized(lock) {
    bucket().optJSONObject(groupId)?.optInt("epoch")?.takeIf(::validEpoch)
  }

  fun senderKey(groupId: String): String? = synchronized(lock) {
    bucket().optJSONObject(groupId)?.optString("senderKey")?.takeIf(::validSenderKey)
  }

  fun matches(groupId: String, epoch: Int): Boolean = synchronized(lock) {
    val entry = bucket().optJSONObject(groupId) ?: return@synchronized false
    entry.optInt("epoch") == epoch && validSenderKey(entry.optString("senderKey"))
  }

  fun forget(groupId: String): Boolean = synchronized(lock) {
    val keys = bucket()
    if (!keys.has(groupId)) return@synchronized false
    keys.remove(groupId)
    state().put(KEY_BUCKET, keys)
    persist()
    true
  }

  fun clear() = synchronized(lock) {
    state().put(KEY_BUCKET, JSONObject())
    persist()
  }

  fun count(): Int = synchronized(lock) { bucket().length() }

  private fun bucket(): JSONObject = state().optJSONObject(KEY_BUCKET) ?: JSONObject()

  internal companion object {
    const val KEY_BUCKET = "groupKeys"
    private const val MAX_EPOCH = 2_147_483_647L
    private val SENDER_KEY = Regex("(?i)^[0-9a-f]{64}$")

    fun validEpoch(value: Int): Boolean = value > 0

    fun validEpoch(value: Long): Boolean = value in 1..MAX_EPOCH

    fun validSenderKey(value: String): Boolean = SENDER_KEY.matches(value)
  }
}
