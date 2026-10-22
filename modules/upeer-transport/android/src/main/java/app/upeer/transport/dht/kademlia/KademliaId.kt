package app.upeer.transport.dht.kademlia

import java.security.MessageDigest

object KademliaConfig {
  const val ID_BITS = 160
  const val ID_BYTES = ID_BITS / 8
  const val BUCKET_COUNT = ID_BITS
  const val K = 20
  const val ALPHA = 3
  const val REFRESH_INTERVAL_MILLIS = 3_600_000L
  const val VALUE_TTL_MILLIS = 2_592_000_000L
  const val MAX_STORE_ENTRIES = 10_000
  const val MAX_DEVICES_PER_KEY = 10
  const val PENDING_QUERY_TTL_MILLIS = 30_000L
  const val QUERY_TIMEOUT_MILLIS = 5_000L
  const val LOOKUP_TOTAL_TIMEOUT_MILLIS = 30_000L
  const val STORE_TIMEOUT_MILLIS = 10_000L
  const val STORE_RETRIES = 1
  const val MAX_LOOKUP_ROUNDS = 10
  const val BOOTSTRAP_MIN_NODES = 10
  const val BOOTSTRAP_RETRY_MILLIS = 30_000L
  const val BOOTSTRAP_MAX_RETRY_MILLIS = 300_000L
  const val BOOTSTRAP_LOOKUP_ROUNDS = 3
  const val BOOTSTRAP_PROBE_BATCH = 10
  const val MAX_SEED_NODES = 100
  const val MAX_PERSISTED_CONTACTS = 100
  const val MAX_SEED_FILE_BYTES = 262_144
  const val LAN_PROBE_INTERVAL_MILLIS = 300_000L
}

class KademliaId private constructor(private val bytes: ByteArray) {
  init {
    require(bytes.size == KademliaConfig.ID_BYTES) { "Kademlia IDs must be 20 bytes" }
  }

  val hex: String = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

  fun compareDistanceTo(other: KademliaId, target: KademliaId): Int {
    val ownDistance = xor(bytes, target.bytes)
    val otherDistance = xor(other.bytes, target.bytes)
    for (index in ownDistance.indices) {
      val own = ownDistance[index].toInt() and 0xff
      val rival = otherDistance[index].toInt() and 0xff
      if (own < rival) return -1
      if (own > rival) return 1
    }
    return 0
  }

  override fun equals(other: Any?): Boolean = other is KademliaId && bytes.contentEquals(other.bytes)

  override fun hashCode(): Int = bytes.contentHashCode()

  override fun toString(): String = hex

  companion object {
    fun fromBytes(bytes: ByteArray): KademliaId = KademliaId(bytes.copyOf())

    fun fromRawHex(value: String): KademliaId {
      val decoded = decodeHex(value, KademliaConfig.ID_BYTES)
      return KademliaId(decoded)
    }

    fun fromUpeerId(upeerId: String): KademliaId = hashed(upeerId)

    fun fromLookupHex(value: String): KademliaId = hashed(value)

    fun bucketIndex(nodeId: KademliaId, otherId: KademliaId): Int {
      val distance = xor(nodeId.bytes, otherId.bytes)
      for (byteIndex in distance.indices) {
        val value = distance[byteIndex].toInt() and 0xff
        if (value != 0) {
          for (bit in 7 downTo 0) {
            if (value and (1 shl bit) != 0) return byteIndex * 8 + (7 - bit)
          }
        }
      }
      return KademliaConfig.BUCKET_COUNT - 1
    }

    fun isHex(value: String): Boolean {
      if (value.isEmpty() || value.length % 2 != 0) return false
      return value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun hashed(value: String): KademliaId {
      if (!isHex(value) || value.length > 128) throw IllegalArgumentException("invalid Kademlia identifier")
      val digest = MessageDigest.getInstance("SHA-256").digest(decodeHex(value, value.length / 2))
      return KademliaId(digest.copyOf(KademliaConfig.ID_BYTES))
    }

    private fun decodeHex(value: String, expectedBytes: Int): ByteArray {
      if (!isHex(value) || value.length != expectedBytes * 2) {
        throw IllegalArgumentException("invalid Kademlia identifier")
      }
      return ByteArray(expectedBytes) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
      }
    }

    private fun xor(left: ByteArray, right: ByteArray): ByteArray =
      ByteArray(left.size) { index -> (left[index].toInt() xor right[index].toInt()).toByte() }
  }
}
