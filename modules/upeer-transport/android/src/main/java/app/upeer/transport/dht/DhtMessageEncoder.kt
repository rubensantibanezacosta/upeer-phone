package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject

object DhtMessageEncoder {
  fun encode(message: DhtMessage): String {
    val packet = JSONObject().put("type", message.type)
    message.queryId?.let { packet.put("queryId", it) }
    when (message) {
      is DhtPing -> message.nodeId?.let { packet.put("nodeId", it) }
      is DhtPong -> message.nodeId?.let { packet.put("nodeId", it) }
      is DhtFindNode -> packet.put("targetId", message.targetId)
      is DhtFoundNodes -> packet.put("nodes", nodes(message.nodes))
      is DhtFindValue -> packet.put("key", message.key)
      is DhtFoundValue -> foundValue(message, packet)
      is DhtStore -> store(message, packet)
      is DhtStoreAck -> packet.put("key", message.key)
      is DhtQuery -> {
        packet.put("targetId", message.targetId)
        message.referralContext?.let { packet.put("referralContext", JSONObject(it.toString())) }
      }
      is DhtResponse -> {
        packet.put("targetId", message.targetId)
        message.publicKey?.let { packet.put("publicKey", it) }
        message.locationBlock?.let { packet.put("locationBlock", JSONObject(it.toString())) }
        message.neighbors?.let { packet.put("neighbors", neighbors(it)) }
      }
      is DhtUpdate -> packet.put("locationBlock", JSONObject(message.locationBlock.toString()))
      is DhtExchange -> packet.put("peers", peers(message.peers))
    }
    DhtPacketPolicy.validateOutgoing(packet.toString())
    return packet.toString()
  }

  private fun foundValue(message: DhtFoundValue, packet: JSONObject) {
    message.key?.let { packet.put("key", it) }
    message.value?.let { packet.put("value", it) }
    message.nodes?.let { packet.put("nodes", nodes(it)) }
    message.publisher?.let { packet.put("publisher", it) }
    message.timestamp?.let { packet.put("timestamp", it) }
  }

  private fun store(message: DhtStore, packet: JSONObject) {
    packet.put("key", message.key)
    packet.put("value", message.value)
    message.publisher?.let { packet.put("publisher", it) }
    message.timestamp?.let { packet.put("timestamp", it) }
    message.ttl?.let { packet.put("ttl", it) }
  }

  private fun nodes(nodes: List<DhtNode>): JSONArray = JSONArray().also { array ->
    nodes.forEach { node ->
      val value = JSONObject().put("upeerId", node.upeerId).put("address", node.address)
      node.publicKey?.let { value.put("publicKey", it) }
      node.nodeId?.let { value.put("nodeId", it) }
      array.put(value)
    }
  }

  private fun neighbors(neighbors: List<DhtNeighbor>): JSONArray = JSONArray().also { array ->
    neighbors.forEach { neighbor ->
      val value = JSONObject()
      neighbor.upeerId?.let { value.put("upeerId", it) }
      neighbor.address?.let { value.put("address", it) }
      neighbor.publicKey?.let { value.put("publicKey", it) }
      neighbor.locationBlock?.let { value.put("locationBlock", JSONObject(it.toString())) }
      array.put(value)
    }
  }

  private fun peers(peers: List<DhtExchangePeer>): JSONArray = JSONArray().also { array ->
    peers.forEach { peer ->
      val value = JSONObject().put("upeerId", peer.upeerId).put("publicKey", peer.publicKey)
      peer.locationBlock?.let { value.put("locationBlock", JSONObject(it.toString())) }
      array.put(value)
    }
  }
}
