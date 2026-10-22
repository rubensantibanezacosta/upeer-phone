package app.upeer.transport.dht.kademlia

import org.json.JSONArray
import org.json.JSONObject

internal object KademliaValueMerge {
  fun merge(values: List<Any>, maxDevices: Int = KademliaConfig.MAX_DEVICES_PER_KEY): Any? {
    if (values.isEmpty()) return null
    val objects = values.flatMap(::objects)
    val devices = devicesByKey(objects)
    if (devices.isNotEmpty()) {
      return JSONArray().also { array ->
        devices.values
          .sortedByDescending { it.optLong("dhtSeq", 0L) }
          .take(maxDevices)
          .forEach { block -> array.put(JSONObject(block.toString())) }
      }
    }
    val registries = objects.filter { it.has("epoch") }
    if (registries.isNotEmpty()) {
      val best = registries.maxWithOrNull(
        compareBy<JSONObject> { it.optLong("epoch", 0L) }
          .thenBy { it.optLong("issuedAt", 0L) },
      ) ?: return null
      return JSONObject(best.toString())
    }
    return copy(values.first())
  }

  private fun devicesByKey(objects: List<JSONObject>): Map<String, JSONObject> {
    val devices = linkedMapOf<String, JSONObject>()
    objects.forEach { block ->
      val deviceId = block.optString("deviceId").lowercase()
      if (deviceId.isEmpty() || !block.has("dhtSeq")) return@forEach
      val previous = devices[deviceId]
      if (previous == null || block.optLong("dhtSeq", 0L) > previous.optLong("dhtSeq", 0L)) {
        devices[deviceId] = block
      }
    }
    return devices
  }

  private fun objects(value: Any): List<JSONObject> = when (value) {
    is JSONObject -> listOf(value)
    is JSONArray -> (0 until value.length()).mapNotNull { index -> value.opt(index) as? JSONObject }
    is String -> runCatching { JSONObject(value) }.getOrNull()?.let(::listOf).orEmpty()
    else -> emptyList()
  }

  private fun copy(value: Any): Any = when (value) {
    is JSONObject -> JSONObject(value.toString())
    is JSONArray -> JSONArray(value.toString())
    else -> value
  }
}
