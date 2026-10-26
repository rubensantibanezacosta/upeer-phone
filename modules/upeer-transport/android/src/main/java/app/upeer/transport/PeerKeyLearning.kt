package app.upeer.transport

import app.upeer.transport.dht.DhtLocation

internal object PeerKeyLearning {
  fun keyToLearn(upeerId: String, publicKey: String, knownKey: String): String? {
    if (!DhtLocation.isHex(upeerId, 32)) return null
    val candidate = publicKey.trim().lowercase()
    if (!DhtLocation.isHex(candidate, 32)) return null
    if (knownKey.isNotEmpty()) return null
    return candidate
  }
}
