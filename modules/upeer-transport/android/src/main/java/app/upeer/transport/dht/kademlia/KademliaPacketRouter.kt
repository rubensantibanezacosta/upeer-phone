package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtContactLocation
import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtStore
import app.upeer.transport.dht.DhtUpdate
import app.upeer.transport.dht.DhtPacketCodec
import app.upeer.transport.dht.DhtPacketPolicy
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal class KademliaPacketRouter(
  private val node: KademliaNode,
  private val publicKeyFor: (String) -> String?,
  private val sendResponse: (String, DhtMessage) -> Unit,
  private val verify: (String, String) -> JSONObject = DhtPacketCodec::verify,
  private val registryKeyFor: (String) -> String? = { publisher ->
    runCatching { Transportbridge.accountRegistryKey(publisher) }.getOrNull()
  },
  private val validatePublicKey: (String, String) -> Boolean = KademliaSeedValidator::derivesUpeerId,
  private val verifyLocation: (String, String, String, Long) -> Boolean = { block, publicKey, registry, now ->
    Transportbridge.verifyDeviceLocationBlock(block, publicKey, registry, now)
  },
  private val applyUpdate: (String, String, JSONObject, String) -> Boolean = { _, _, _, _ -> false },
) {
  fun handle(packetJSON: String, sourceAddress: String): JSONObject? {
    val outer = DhtPacketPolicy.parse(packetJSON)
    val type = DhtPacketPolicy.type(outer)
    if (!type.startsWith(DHT_PREFIX)) return null
    val senderUpeerId = outer.optString("senderUpeerId").lowercase()
    if (senderUpeerId.isEmpty()) throw IllegalArgumentException("DHT packet is missing senderUpeerId")
    val publicKey = publicKeyFor(senderUpeerId)
      ?: throw IllegalArgumentException("unknown DHT sender $senderUpeerId")
    if (!validatePublicKey(publicKey, senderUpeerId)) {
      throw IllegalArgumentException("DHT sender public key does not match its upeerId")
    }
    verify(packetJSON, publicKey)
    val address = outer.optString("senderYggAddress").ifBlank { sourceAddress }
    val message = DhtPacketPolicy.decode(outer)
    if (message is DhtStore) validateStore(message, publicKey)
    if (message is DhtUpdate) {
      val applied = applyUpdate(senderUpeerId, publicKey, message.locationBlock, address)
      return JSONObject()
        .put("type", type)
        .put("senderUpeerId", senderUpeerId)
        .put("handled", true)
        .put("applied", applied)
        .put("responded", false)
    }
    val response = node.handleIncoming(senderUpeerId, message, address)
    if (response != null && address.isNotBlank()) sendResponse(address, response)
    return JSONObject()
      .put("type", type)
      .put("senderUpeerId", senderUpeerId)
      .put("handled", true)
      .put("responded", response != null)
  }

  private fun validateStore(message: DhtStore, publicKey: String) {
    val publisher = message.publisher?.lowercase() ?: return
    val registryKey = registryKeyFor(publisher)
    if (registryKey != null && registryKey.equals(message.key, ignoreCase = true)) {
      val value = message.value as? JSONObject
        ?: throw IllegalArgumentException("account registry value is not an object")
      if (!Transportbridge.verifyAccountRegistry(value.toString(), publisher, System.currentTimeMillis())) {
        throw IllegalArgumentException("account registry store value is invalid")
      }
      return
    }
    if (!publisher.equals(message.key, ignoreCase = true)) return
    val block = (message.value as? JSONObject)?.toString()
      ?: throw IllegalArgumentException("location block value is not an object")
    if (DhtContactLocation.verifyOne(block, publisher, publicKey, "", System.currentTimeMillis(), verifyLocation) == null) {
      throw IllegalArgumentException("location block store value is invalid")
    }
  }

  private companion object {
    private const val DHT_PREFIX = "DHT_"
  }
}
