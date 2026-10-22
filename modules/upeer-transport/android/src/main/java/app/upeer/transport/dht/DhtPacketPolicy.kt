package app.upeer.transport.dht

import org.json.JSONObject

class DhtPacketException(
  val reason: String,
  cause: Throwable? = null,
) : IllegalArgumentException(reason, cause)

object DhtPacketPolicy {
  private val COMMON_FIELDS = setOf(
    "type",
    "queryId",
    "senderUpeerId",
    "senderYggAddress",
    "isInternalSync",
    "signature",
    "id",
    "contactCache",
    "renewalToken",
  )

  private val ALLOWED_FIELDS = mapOf(
    "DHT_PING" to COMMON_FIELDS + setOf("nodeId"),
    "DHT_PONG" to COMMON_FIELDS + setOf("nodeId"),
    "DHT_FIND_NODE" to COMMON_FIELDS + setOf("targetId"),
    "DHT_FOUND_NODES" to COMMON_FIELDS + setOf("nodes"),
    "DHT_FIND_VALUE" to COMMON_FIELDS + setOf("key"),
    "DHT_FOUND_VALUE" to COMMON_FIELDS + setOf("key", "value", "nodes", "publisher", "timestamp"),
    "DHT_STORE" to COMMON_FIELDS + setOf("key", "value", "publisher", "timestamp", "ttl"),
    "DHT_STORE_ACK" to COMMON_FIELDS + setOf("key"),
    "DHT_QUERY" to COMMON_FIELDS + setOf("targetId", "referralContext"),
    "DHT_RESPONSE" to COMMON_FIELDS + setOf("targetId", "publicKey", "locationBlock", "neighbors"),
    "DHT_UPDATE" to COMMON_FIELDS + setOf("locationBlock"),
    "DHT_EXCHANGE" to COMMON_FIELDS + setOf("peers"),
  )

  fun decode(packetJSON: String): DhtMessage = decode(parse(packetJSON))

  fun decode(packet: JSONObject): DhtMessage {
    val type = type(packet)
    val allowed = ALLOWED_FIELDS[type] ?: throw DhtPacketException("unsupported packet type")
    val unexpected = packet.keys().asSequence().filterNot(allowed::contains).sorted().toList()
    if (unexpected.isNotEmpty()) {
      throw DhtPacketException("unexpected fields: ${unexpected.joinToString(",")}")
    }
    return DhtMessageParser.decode(packet, type, queryId(packet))
  }

  fun validateOutgoing(payloadJSON: String) {
    decode(payloadJSON)
  }

  fun parse(packetJSON: String): JSONObject {
    if (packetJSON.isEmpty()) throw DhtPacketException("empty packet")
    if (packetJSON.length > DhtLimits.MAX_JSON_CHARS) throw DhtPacketException("packet too large")
    guardDepth(packetJSON)
    return try {
      DhtCanonicalJson.parse(packetJSON)
    } catch (cause: DhtPacketException) {
      throw cause
    } catch (cause: Exception) {
      throw DhtPacketException("invalid JSON", cause)
    }
  }

  fun type(packet: JSONObject): String {
    val value = packet.opt("type")
    if (value !is String || value.isEmpty()) throw DhtPacketException("missing packet type")
    if (value !in ALLOWED_FIELDS) throw DhtPacketException("unsupported packet type")
    return value
  }

  fun queryId(packet: JSONObject): String? {
    if (!packet.has("queryId") || packet.opt("queryId") == JSONObject.NULL) return null
    val value = packet.opt("queryId")
    if (value !is String) throw DhtPacketException("invalid queryId")
    if (value.isBlank() || value.length > DhtLimits.MAX_QUERY_ID_LENGTH) {
      throw DhtPacketException("invalid queryId")
    }
    return value
  }

  fun supportedTypes(): Set<String> = ALLOWED_FIELDS.keys

  private fun guardDepth(packetJSON: String) {
    var depth = 0
    var inString = false
    var escaped = false
    for (character in packetJSON) {
      if (inString) {
        when {
          escaped -> escaped = false
          character == '\\' -> escaped = true
          character == '"' -> inString = false
        }
        continue
      }
      when (character) {
        '"' -> inString = true
        '{', '[' -> {
          depth += 1
          if (depth > DhtLimits.MAX_JSON_DEPTH) throw DhtPacketException("JSON is too deeply nested")
        }
        '}', ']' -> depth -= 1
      }
      if (depth < 0) throw DhtPacketException("invalid JSON nesting")
    }
    if (inString || depth != 0) throw DhtPacketException("truncated JSON")
  }
}
