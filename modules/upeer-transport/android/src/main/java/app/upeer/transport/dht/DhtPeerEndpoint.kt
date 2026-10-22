package app.upeer.transport.dht

internal data class DhtPeerEndpoint(
  val upeerId: String,
  val addresses: List<String>,
)
