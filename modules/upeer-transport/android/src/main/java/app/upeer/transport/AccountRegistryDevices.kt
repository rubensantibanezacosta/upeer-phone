package app.upeer.transport

import org.json.JSONObject

object AccountRegistryDevices {
  fun deviceIds(registryJSON: String?, accountUpeerId: String): Set<String> {
    if (registryJSON.isNullOrBlank()) return emptySet()
    val registry = runCatching { JSONObject(registryJSON) }.getOrNull() ?: return emptySet()
    if (!registry.optString("accountUpeerId").equals(accountUpeerId, ignoreCase = true)) return emptySet()
    val devices = registry.optJSONArray("devices") ?: return emptySet()
    return (0 until devices.length())
      .mapNotNull { index -> devices.optJSONObject(index)?.optString("deviceId")?.takeIf(String::isNotBlank) }
      .map { it.lowercase() }
      .toSet()
  }

  fun unknownDevices(localRegistryJSON: String?, publishedRegistryJSON: String?, accountUpeerId: String): Set<String> {
    val published = deviceIds(publishedRegistryJSON, accountUpeerId)
    if (published.isEmpty()) return emptySet()
    return published - deviceIds(localRegistryJSON, accountUpeerId)
  }
}
