package app.upeer.transport.dht.kademlia

data class KademliaContact(
  val nodeId: KademliaId,
  val upeerId: String,
  val address: String,
  val publicKey: String,
  val lastSeen: Long,
  val dhtSeq: Long? = null,
)
