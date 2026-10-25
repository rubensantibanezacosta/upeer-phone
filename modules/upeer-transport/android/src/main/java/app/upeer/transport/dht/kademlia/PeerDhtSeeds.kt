package app.upeer.transport.dht.kademlia

import org.json.JSONObject

object PeerDhtSeeds {
  fun fromPeers(peersJSON: String?, selfUpeerId: String): List<KademliaSeed> {
    if (peersJSON.isNullOrBlank()) return emptyList()
    val peers = runCatching { JSONObject(peersJSON) }.getOrNull() ?: return emptyList()
    return peers.keys().asSequence().mapNotNull { upeerId ->
      val peer = peers.optJSONObject(upeerId) ?: return@mapNotNull null
      val seed = KademliaSeed(
        upeerId,
        peer.optString("address"),
        peer.optString("publicKey"),
      )
      KademliaSeedValidator.normalize(seed, selfUpeerId)
    }.take(KademliaConfig.MAX_SEED_NODES).toList()
  }
}
