package app.upeer.transport.dht.kademlia

import org.json.JSONArray
import org.json.JSONObject

data class KademliaStoredValue(
  val key: String,
  val value: Any,
  val publisher: String,
  val timestamp: Long,
  val signature: String? = null,
)

class KademliaValueStore(
  private val clock: () -> Long = System::currentTimeMillis,
  private val maxEntries: Int = KademliaConfig.MAX_STORE_ENTRIES,
) {
  private val lock = Any()
  private val entries = LinkedHashMap<String, KademliaStoredValue>()

  fun set(key: String, value: Any, publisher: String, signature: String? = null) {
    val normalized = key.lowercase()
    synchronized(lock) {
      val existing = entries[normalized]
      val finalValue = mergeDeviceValue(existing, value)
      if (!entries.containsKey(normalized) && entries.size >= maxEntries) {
        val oldest = entries.keys.firstOrNull()
        if (oldest != null) entries.remove(oldest)
      }
      entries[normalized] = KademliaStoredValue(normalized, finalValue, publisher, clock(), signature)
    }
  }

  fun get(key: String): KademliaStoredValue? = synchronized(lock) { entries[key.lowercase()] }

  fun has(key: String): Boolean = synchronized(lock) { entries.containsKey(key.lowercase()) }

  fun delete(key: String): Boolean = synchronized(lock) { entries.remove(key.lowercase()) != null }

  fun cleanupExpiredValues(): Int = synchronized(lock) {
    val now = clock()
    val expired = entries.filterValues { now - it.timestamp > KademliaConfig.VALUE_TTL_MILLIS }.keys.toList()
    expired.forEach(entries::remove)
    expired.size
  }

  fun getAll(): List<KademliaStoredValue> = synchronized(lock) { entries.values.toList() }

  fun size(): Int = synchronized(lock) { entries.size }

  private fun mergeDeviceValue(existing: KademliaStoredValue?, value: Any): Any {
    val deviceId = deviceId(value) ?: return copyValue(value)
    val list = mutableListOf<JSONObject>()
    when (val current = existing?.value) {
      is JSONArray -> for (index in 0 until current.length()) {
        val item = current.opt(index) as? JSONObject ?: continue
        if (deviceId(item) != null) list.add(JSONObject(item.toString()))
      }
      is JSONObject -> if (deviceId(current) != null) list.add(JSONObject(current.toString()))
    }
    list.removeAll { deviceId(it) == deviceId }
    list.add(JSONObject(value.toString()))
    val sorted = list.sortedByDescending { it.optLong("dhtSeq", 0) }.take(KademliaConfig.MAX_DEVICES_PER_KEY)
    return JSONArray().also { array -> sorted.forEach(array::put) }
  }

  private fun deviceId(value: Any): String? {
    val objectValue = value as? JSONObject ?: return null
    val deviceId = objectValue.opt("deviceId")
    return if (deviceId is String && deviceId.isNotEmpty()) deviceId else null
  }

  private fun copyValue(value: Any): Any = when (value) {
    is JSONObject -> JSONObject(value.toString())
    is JSONArray -> JSONArray(value.toString())
    else -> value
  }
}
