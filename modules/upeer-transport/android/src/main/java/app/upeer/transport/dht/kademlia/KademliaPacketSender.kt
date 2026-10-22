package app.upeer.transport.dht.kademlia

import app.upeer.transport.dht.DhtMessage
import app.upeer.transport.dht.DhtMessageEncoder
import app.upeer.transport.dht.DhtPacketCodec
import app.upeer.transportbridge.transportbridge.CryptoIdentity

internal class KademliaPacketSender(
  private val currentIdentity: () -> CryptoIdentity?,
  private val ownAddress: () -> String,
  private val transmit: (String, String) -> Unit,
  private val encode: (DhtMessage) -> String = DhtMessageEncoder::encode,
  private val build: (CryptoIdentity, String, String) -> String = { identity, address, payload ->
    DhtPacketCodec.build(identity, address, payload)
  },
) {
  fun send(address: String, message: DhtMessage) {
    val identity = currentIdentity() ?: throw IllegalStateException("uPeer identity is not available")
    val own = ownAddress()
    if (own.isBlank()) throw IllegalStateException("Yggdrasil address is not available")
    transmit(address, build(identity, own, encode(message)))
  }
}
