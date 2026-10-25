package app.upeer.transport.vault

import app.upeer.transport.dht.DhtLocation
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

internal object VaultPointerKey {
  private const val PREFIX = "vault-ptr-v2"
  private const val KEY_BYTES = 20
  private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

  fun key(recipientSid: String, deviceId: String? = null, nowMillis: Long): String {
    val recipient = recipientSid.lowercase()
    require(DhtLocation.isHex(recipient, 32)) { "El destinatario del puntero no es válido" }
    val device = deviceId?.lowercase()?.takeIf(String::isNotEmpty)
    if (device != null) require(DhtLocation.isHex(device, 32)) { "El dispositivo del puntero no es válido" }
    val scope = if (device != null) ":device:$device" else ""
    val day = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$PREFIX:$recipient$scope:$day".toByteArray(Charsets.UTF_8))
    return digest.copyOf(KEY_BYTES).joinToString("") { "%02x".format(it) }
  }

  fun readKeys(recipientSid: String, deviceId: String? = null, nowMillis: Long): List<String> {
    val keys = LinkedHashSet<String>()
    val yesterday = nowMillis - DAY_MILLIS
    keys.add(key(recipientSid, null, nowMillis))
    keys.add(key(recipientSid, null, yesterday))
    val device = deviceId?.lowercase()?.takeIf(String::isNotEmpty)
    if (device != null) {
      keys.add(key(recipientSid, device, nowMillis))
      keys.add(key(recipientSid, device, yesterday))
    }
    return keys.toList()
  }
}
