package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject

internal object DhtMessageParser {
  fun decode(packet: JSONObject, type: String, queryId: String?): DhtMessage = when (type) {
    "DHT_PING" -> DhtPing(queryId, optionalNodeId(packet))
    "DHT_PONG" -> DhtPong(queryId, optionalNodeId(packet))
    "DHT_FIND_NODE" -> DhtFindNode(queryId, findNodeTarget(packet))
    "DHT_FOUND_NODES" -> DhtFoundNodes(queryId, requiredNodes(packet, "nodes"))
    "DHT_FIND_VALUE" -> DhtFindValue(queryId, requiredKey(packet, "key"))
    "DHT_FOUND_VALUE" -> foundValue(packet, queryId)
    "DHT_STORE" -> store(packet, queryId)
    "DHT_STORE_ACK" -> DhtStoreAck(queryId, requiredKey(packet, "key"))
    "DHT_QUERY" -> DhtQuery(queryId, legacyTarget(packet), optionalObject(packet, "referralContext"))
    "DHT_RESPONSE" -> DhtResponse(
      queryId,
      legacyTarget(packet),
      optionalString(packet, "publicKey", DhtLimits.MAX_NODE_ID_LENGTH),
      optionalLocationBlock(packet, "locationBlock"),
      optionalNeighbors(packet, "neighbors"),
    )
    "DHT_UPDATE" -> DhtUpdate(queryId, requiredLocationBlock(packet, "locationBlock"))
    "DHT_EXCHANGE" -> DhtExchange(queryId, requiredPeers(packet, "peers"))
    else -> throw DhtPacketException("unsupported packet type")
  }

  private fun foundValue(packet: JSONObject, queryId: String?): DhtFoundValue {
    val key = optionalKey(packet, "key")
    val value = if (isPresent(packet, "value")) packet.opt("value") else null
    val nodes = if (isPresent(packet, "nodes")) requiredNodes(packet, "nodes") else null
    if (value == null && nodes == null) throw DhtPacketException("missing value or nodes")
    if (value != null) validateValueSize(value)
    return DhtFoundValue(
      queryId,
      key,
      value,
      nodes,
      optionalString(packet, "publisher", DhtLimits.MAX_UPEEER_ID_LENGTH),
      optionalTimestamp(packet, "timestamp"),
    )
  }

  private fun store(packet: JSONObject, queryId: String?): DhtStore {
    val value = packet.opt("value")
    if (!isPresent(packet, "value") || value == null) throw DhtPacketException("missing value")
    validateValueSize(value)
    val ttl = optionalInteger(packet, "ttl")
    if (ttl != null && (ttl < 0 || ttl > DhtLimits.MAX_STORE_TTL_SECONDS)) {
      throw DhtPacketException("invalid TTL")
    }
    return DhtStore(
      queryId,
      requiredKey(packet, "key"),
      value,
      optionalString(packet, "publisher", DhtLimits.MAX_UPEEER_ID_LENGTH),
      optionalTimestamp(packet, "timestamp"),
      ttl,
    )
  }

  private fun optionalNodeId(packet: JSONObject): String? {
    val nodeId = optionalString(packet, "nodeId", DhtLimits.MAX_NODE_ID_LENGTH) ?: return null
    if (!isHex(nodeId)) throw DhtPacketException("invalid nodeId")
    return nodeId
  }

  private fun findNodeTarget(packet: JSONObject): String {
    val target = requiredString(packet, "targetId", DhtLimits.MAX_NODE_ID_LENGTH)
    if (!isHex(target)) throw DhtPacketException("invalid targetId")
    return target
  }

  private fun legacyTarget(packet: JSONObject): String {
    val target = requiredString(packet, "targetId", DhtLimits.MAX_UPEEER_ID_LENGTH)
    if (!validHexId(target)) throw DhtPacketException("invalid targetId")
    return target
  }

  private fun requiredKey(packet: JSONObject, field: String): String {
    val key = requiredString(packet, field, 64)
    if (!validKey(key)) throw DhtPacketException("invalid key")
    return key
  }

  private fun optionalKey(packet: JSONObject, field: String): String? {
    val key = optionalString(packet, field, 64) ?: return null
    if (!validKey(key)) throw DhtPacketException("invalid key")
    return key
  }

  private fun requiredNodes(packet: JSONObject, field: String): List<DhtNode> {
    val nodes = packet.opt(field)
    if (nodes !is JSONArray) throw DhtPacketException("invalid nodes array")
    if (nodes.length() > DhtLimits.MAX_NODES) throw DhtPacketException("too many nodes")
    return (0 until nodes.length()).map { index ->
      val node = nodes.opt(index) as? JSONObject ?: throw DhtPacketException("invalid node entry")
      parseNode(node)
    }
  }

  private fun parseNode(node: JSONObject): DhtNode {
    val upeerId = requiredString(node, "upeerId", DhtLimits.MAX_UPEEER_ID_LENGTH)
    if (!isHex(upeerId)) throw DhtPacketException("invalid node.upeerId")
    val address = requiredString(node, "address", DhtLimits.MAX_ADDRESS_LENGTH)
    return DhtNode(
      upeerId,
      address,
      optionalString(node, "publicKey", DhtLimits.MAX_NODE_ID_LENGTH),
      optionalString(node, "nodeId", DhtLimits.MAX_NODE_ID_LENGTH),
    )
  }

  private fun optionalNeighbors(packet: JSONObject, field: String): List<DhtNeighbor>? {
    if (!isPresent(packet, field)) return null
    val neighbors = packet.opt(field) as? JSONArray ?: throw DhtPacketException("invalid neighbors array")
    if (neighbors.length() > DhtLimits.MAX_NEIGHBORS) throw DhtPacketException("too many neighbors")
    return (0 until neighbors.length()).map { index ->
      val neighbor = neighbors.opt(index) as? JSONObject
        ?: throw DhtPacketException("invalid neighbor entry")
      DhtNeighbor(
        optionalString(neighbor, "upeerId", DhtLimits.MAX_UPEEER_ID_LENGTH),
        optionalString(neighbor, "address", DhtLimits.MAX_ADDRESS_LENGTH),
        optionalString(neighbor, "publicKey", DhtLimits.MAX_NODE_ID_LENGTH),
        optionalLocationBlock(neighbor, "locationBlock"),
      )
    }
  }

  private fun requiredPeers(packet: JSONObject, field: String): List<DhtExchangePeer> {
    val peers = packet.opt(field) as? JSONArray ?: throw DhtPacketException("invalid peers array")
    if (peers.length() > DhtLimits.MAX_PEERS) throw DhtPacketException("too many peers")
    return (0 until peers.length()).map { index ->
      val peer = peers.opt(index) as? JSONObject ?: throw DhtPacketException("invalid peer entry")
      val upeerId = requiredString(peer, "upeerId", DhtLimits.MAX_UPEEER_ID_LENGTH)
      if (!validHexId(upeerId)) throw DhtPacketException("invalid peer upeerId")
      val publicKey = requiredString(peer, "publicKey", 64)
      if (publicKey.length != 64) throw DhtPacketException("invalid peer publicKey")
      DhtExchangePeer(upeerId, publicKey, optionalLocationBlock(peer, "locationBlock"))
    }
  }

  private fun requiredLocationBlock(packet: JSONObject, field: String): JSONObject =
    optionalLocationBlock(packet, field) ?: throw DhtPacketException("missing locationBlock")

  private fun optionalLocationBlock(packet: JSONObject, field: String): JSONObject? {
    if (!isPresent(packet, field)) return null
    val block = packet.opt(field) as? JSONObject
      ?: throw DhtPacketException("invalid locationBlock")
    validateLocationBlock(block)
    return block
  }

  private fun validateLocationBlock(block: JSONObject) {
    val address = requiredString(block, "address", DhtLimits.MAX_ADDRESS_LENGTH)
    if (address.isBlank()) throw DhtPacketException("invalid locationBlock.address")
    val dhtSeq = requiredInteger(block, "dhtSeq")
    if (dhtSeq < 0) throw DhtPacketException("invalid locationBlock.dhtSeq")
    val signature = requiredString(block, "signature", 128)
    if (signature.length != 128) throw DhtPacketException("invalid locationBlock.signature")
    if (isPresent(block, "powProof")) validatePowProof(block.opt("powProof"))
  }

  private fun validatePowProof(value: Any?) {
    if (value !is String || value.length > DhtLimits.MAX_POW_PROOF_LENGTH) {
      throw DhtPacketException("invalid powProof")
    }
    if (!value.startsWith("{") && !isHex(value)) throw DhtPacketException("invalid powProof")
  }

  private fun validateValueSize(value: Any) {
    val serialized = if (value is String) value else value.toString()
    if (serialized.length > DhtLimits.MAX_VALUE_CHARS) throw DhtPacketException("value too large")
  }

  private fun validKey(value: String): Boolean =
    (value.length == 40 || value.length == 64) && isHex(value)

  private fun validHexId(value: String): Boolean =
    value.length in DhtLimits.MIN_UPEEER_ID_LENGTH..DhtLimits.MAX_UPEEER_ID_LENGTH && isHex(value)

  private fun isHex(value: String): Boolean =
    value.isNotEmpty() && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

  private fun isPresent(packet: JSONObject, field: String): Boolean =
    packet.has(field) && packet.opt(field) != JSONObject.NULL

  private fun requiredString(packet: JSONObject, field: String, maxLength: Int): String {
    val value = packet.opt(field)
    if (value !is String) throw DhtPacketException("missing or invalid $field")
    if (value.length > maxLength) throw DhtPacketException("invalid $field")
    return value
  }

  private fun optionalString(packet: JSONObject, field: String, maxLength: Int): String? {
    if (!isPresent(packet, field)) return null
    val value = packet.opt(field)
    if (value !is String || value.length > maxLength) throw DhtPacketException("invalid $field")
    return value
  }

  private fun optionalObject(packet: JSONObject, field: String): JSONObject? {
    if (!isPresent(packet, field)) return null
    return packet.opt(field) as? JSONObject ?: throw DhtPacketException("invalid $field")
  }

  private fun optionalTimestamp(packet: JSONObject, field: String): Long? {
    val timestamp = optionalInteger(packet, field) ?: return null
    if (timestamp < 0) throw DhtPacketException("invalid $field")
    return timestamp
  }

  private fun optionalInteger(packet: JSONObject, field: String): Long? {
    if (!isPresent(packet, field)) return null
    return requiredInteger(packet, field)
  }

  private fun requiredInteger(packet: JSONObject, field: String): Long {
    val value = packet.opt(field) as? Number ?: throw DhtPacketException("invalid $field")
    return value.toString().toLongOrNull() ?: throw DhtPacketException("invalid $field")
  }
}
