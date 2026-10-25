package app.upeer.transport

import app.upeer.transport.dht.DhtLocation
import java.security.MessageDigest

internal object SyncRendezvousKey {
  private const val PREFIX = "sync-device-v1"
  private const val KEY_BYTES = 20

  fun key(accountUpeerId: String, deviceId: String): String {
    val account = accountUpeerId.trim().lowercase()
    val device = deviceId.trim().lowercase()
    require(DhtLocation.isHex(account, 32)) { "La cuenta del encuentro no es válida" }
    require(DhtLocation.isHex(device, 32)) { "El dispositivo del encuentro no es válido" }
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$PREFIX:$account:$device".toByteArray(Charsets.UTF_8))
    return digest.copyOf(KEY_BYTES).joinToString("") { "%02x".format(it) }
  }
}
