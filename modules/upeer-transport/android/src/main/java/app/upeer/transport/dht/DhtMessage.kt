package app.upeer.transport.dht

import org.json.JSONObject

sealed interface DhtMessage {
  val type: String
  val queryId: String?
}

data class DhtNode(
  val upeerId: String,
  val address: String,
  val publicKey: String?,
  val nodeId: String?,
)

data class DhtNeighbor(
  val upeerId: String?,
  val address: String?,
  val publicKey: String?,
  val locationBlock: JSONObject?,
)

data class DhtExchangePeer(
  val upeerId: String,
  val publicKey: String,
  val locationBlock: JSONObject?,
)

data class DhtPing(
  override val queryId: String?,
  val nodeId: String?,
) : DhtMessage {
  override val type: String = "DHT_PING"
}

data class DhtPong(
  override val queryId: String?,
  val nodeId: String?,
) : DhtMessage {
  override val type: String = "DHT_PONG"
}

data class DhtFindNode(
  override val queryId: String?,
  val targetId: String,
) : DhtMessage {
  override val type: String = "DHT_FIND_NODE"
}

data class DhtFoundNodes(
  override val queryId: String?,
  val nodes: List<DhtNode>,
) : DhtMessage {
  override val type: String = "DHT_FOUND_NODES"
}

data class DhtFindValue(
  override val queryId: String?,
  val key: String,
) : DhtMessage {
  override val type: String = "DHT_FIND_VALUE"
}

data class DhtFoundValue(
  override val queryId: String?,
  val key: String?,
  val value: Any?,
  val nodes: List<DhtNode>?,
  val publisher: String?,
  val timestamp: Long?,
) : DhtMessage {
  override val type: String = "DHT_FOUND_VALUE"
}

data class DhtStore(
  override val queryId: String?,
  val key: String,
  val value: Any,
  val publisher: String?,
  val timestamp: Long?,
  val ttl: Long?,
) : DhtMessage {
  override val type: String = "DHT_STORE"
}

data class DhtStoreAck(
  override val queryId: String?,
  val key: String,
) : DhtMessage {
  override val type: String = "DHT_STORE_ACK"
}

data class DhtQuery(
  override val queryId: String?,
  val targetId: String,
  val referralContext: JSONObject?,
) : DhtMessage {
  override val type: String = "DHT_QUERY"
}

data class DhtResponse(
  override val queryId: String?,
  val targetId: String,
  val publicKey: String?,
  val locationBlock: JSONObject?,
  val neighbors: List<DhtNeighbor>?,
) : DhtMessage {
  override val type: String = "DHT_RESPONSE"
}

data class DhtUpdate(
  override val queryId: String?,
  val locationBlock: JSONObject,
) : DhtMessage {
  override val type: String = "DHT_UPDATE"
}

data class DhtExchange(
  override val queryId: String?,
  val peers: List<DhtExchangePeer>,
) : DhtMessage {
  override val type: String = "DHT_EXCHANGE"
}
