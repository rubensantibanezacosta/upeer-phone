package app.upeer.transport.vault

import app.upeer.transport.dht.DhtLocation
import org.json.JSONArray
import org.json.JSONObject

internal data class VaultPointerEndpoint(val upeerId: String, val address: String)

internal data class VaultPointer(
  val custodians: List<String>,
  val endpoints: List<VaultPointerEndpoint>,
  val recipientDeviceId: String?,
  val updatedAt: Long,
)

internal object VaultPointers {
  const val MAX_CUSTODIANS = 16
  const val MAX_ENDPOINTS = 16

  fun build(
    custodians: Collection<String>,
    endpoints: List<VaultPointerEndpoint>,
    recipientDeviceId: String?,
    nowMillis: Long,
  ): VaultPointer? {
    val unique = LinkedHashSet<String>()
    for (custodian in custodians) {
      val normalized = custodian.lowercase()
      if (!DhtLocation.isHex(normalized, 32)) continue
      unique.add(normalized)
      if (unique.size >= MAX_CUSTODIANS) break
    }
    if (unique.isEmpty()) return null
    val device = recipientDeviceId?.lowercase()?.takeIf(String::isNotEmpty)
    if (device != null && !DhtLocation.isHex(device, 32)) return null
    val seen = LinkedHashSet<String>()
    val cleanEndpoints = mutableListOf<VaultPointerEndpoint>()
    for (endpoint in endpoints) {
      val upeerId = endpoint.upeerId.lowercase()
      val address = endpoint.address.trim()
      if (!DhtLocation.isHex(upeerId, 32) || address.isEmpty()) continue
      if (!seen.add(address.lowercase())) continue
      cleanEndpoints.add(VaultPointerEndpoint(upeerId, address))
      if (cleanEndpoints.size >= MAX_ENDPOINTS) break
    }
    return VaultPointer(unique.toList(), cleanEndpoints, device, nowMillis)
  }

  fun toJSON(pointer: VaultPointer): String {
    val custodians = JSONArray()
    pointer.custodians.forEach(custodians::put)
    val endpoints = JSONArray()
    pointer.endpoints.forEach { endpoint ->
      endpoints.put(JSONObject().put("upeerId", endpoint.upeerId).put("address", endpoint.address))
    }
    return JSONObject()
      .put("custodians", custodians)
      .put("custodianEndpoints", endpoints)
      .put("updatedAt", pointer.updatedAt)
      .apply { pointer.recipientDeviceId?.let { put("recipientDeviceId", it) } }
      .toString()
  }

  fun parse(value: Any?, nowMillis: Long): VaultPointer? {
    val document = when (value) {
      is JSONObject -> value
      is String -> runCatching { JSONObject(value) }.getOrNull() ?: return null
      else -> return null
    }
    val custodians = LinkedHashSet<String>()
    document.optJSONArray("custodians")?.let { array ->
      for (index in 0 until array.length()) {
        val custodian = array.optString(index).lowercase()
        if (DhtLocation.isHex(custodian, 32)) custodians.add(custodian)
      }
    }
    if (custodians.isEmpty()) return null
    val endpoints = mutableListOf<VaultPointerEndpoint>()
    document.optJSONArray("custodianEndpoints")?.let { array ->
      for (index in 0 until array.length()) {
        val entry = array.optJSONObject(index) ?: continue
        val upeerId = entry.optString("upeerId").lowercase()
        val address = entry.optString("address").trim()
        if (!DhtLocation.isHex(upeerId, 32) || address.isEmpty()) continue
        endpoints.add(VaultPointerEndpoint(upeerId, address))
      }
    }
    val device = document.optString("recipientDeviceId").lowercase().takeIf(String::isNotEmpty)
    if (device != null && !DhtLocation.isHex(device, 32)) return null
    val updatedAt = document.optLong("updatedAt", 0L).takeIf { it in 1..nowMillis } ?: nowMillis
    return VaultPointer(custodians.toList().take(MAX_CUSTODIANS), endpoints.take(MAX_ENDPOINTS), device, updatedAt)
  }
}
