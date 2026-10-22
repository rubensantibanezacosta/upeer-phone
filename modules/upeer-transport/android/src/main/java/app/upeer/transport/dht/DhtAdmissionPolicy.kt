package app.upeer.transport.dht

import app.upeer.transport.InboundRateLimiter
import app.upeer.transport.dht.kademlia.DhtPendingQueries
import org.json.JSONObject

internal sealed interface DhtAdmissionResult {
  data class Accepted(
    val message: DhtMessage,
    val metadata: JSONObject,
  ) : DhtAdmissionResult

  data class Rejected(val reason: String) : DhtAdmissionResult
}

internal class DhtAdmissionPolicy(
  private val rateLimiter: InboundRateLimiter = InboundRateLimiter(),
  private val pendingQueries: DhtPendingQueries = DhtPendingQueries(),
) {
  fun registerQuery(queryId: String, requestType: String, sourceAddress: String): Boolean =
    pendingQueries.register(queryId, requestType, sourceAddress)

  fun pendingQueryCount(): Int = pendingQueries.size()

  fun admit(
    packetJSON: String,
    sourceAddress: String,
    publicKeyHex: String,
    expectedUpeerId: String,
    verifier: ((String, String, String) -> JSONObject)? = null,
  ): DhtAdmissionResult {
    val packet = try {
      DhtPacketPolicy.parse(packetJSON)
    } catch (cause: DhtPacketException) {
      return DhtAdmissionResult.Rejected(cause.reason)
    }
    val type = try {
      DhtPacketPolicy.type(packet)
    } catch (cause: DhtPacketException) {
      return DhtAdmissionResult.Rejected(cause.reason)
    }
    if (!rateLimiter.allow("ip:$sourceAddress", type)) {
      return DhtAdmissionResult.Rejected("rate limited by source")
    }
    val metadata = try {
      (verifier ?: ::verifyEnvelope).invoke(packetJSON, publicKeyHex, expectedUpeerId)
    } catch (cause: Exception) {
      return DhtAdmissionResult.Rejected("invalid signature")
    }
    val senderUpeerId = metadata.optString("senderUpeerId")
    if (senderUpeerId.isEmpty()) return DhtAdmissionResult.Rejected("invalid signature")
    if (!senderUpeerId.equals(expectedUpeerId, ignoreCase = true)) {
      return DhtAdmissionResult.Rejected("senderUpeerId mismatch")
    }
    if (!rateLimiter.allow("id:${senderUpeerId.lowercase()}", type)) {
      return DhtAdmissionResult.Rejected("rate limited by identity")
    }
    val message = try {
      DhtPacketPolicy.decode(packet)
    } catch (cause: DhtPacketException) {
      return DhtAdmissionResult.Rejected(cause.reason)
    }
    if (DhtPendingQueries.isResponse(type)) {
      val key = pendingQueries.correlationKey(message)
        ?: return DhtAdmissionResult.Rejected("missing pending query")
      if (!pendingQueries.consume(key, type, sourceAddress)) {
        return DhtAdmissionResult.Rejected("missing pending query")
      }
    }
    return DhtAdmissionResult.Accepted(message, metadata)
  }

  private fun verifyEnvelope(packetJSON: String, publicKeyHex: String, expectedUpeerId: String): JSONObject {
    val metadata = DhtPacketCodec.verify(packetJSON, publicKeyHex)
    val senderUpeerId = metadata.optString("senderUpeerId")
    if (!senderUpeerId.equals(expectedUpeerId, ignoreCase = true)) {
      throw DhtPacketException("senderUpeerId mismatch")
    }
    return metadata
  }
}
