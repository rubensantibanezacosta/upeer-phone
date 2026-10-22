package app.upeer.transport.dht.kademlia

import app.upeer.transport.crypto.Blake2b
import app.upeer.transport.dht.DhtLimits
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject

data class KademliaSeed(
  val upeerId: String,
  val address: String,
  val publicKey: String,
)

object KademliaSeedParser {
  const val DNS_DOMAIN = "dht-seeds.upeer.chat"
  const val FILE_NAME = "seednodes.json"

  fun parseDns(records: List<List<String>>): List<KademliaSeed> =
    records.flatMap { chunks ->
      chunks.mapNotNull { chunk -> parseDnsEntry(chunk) }
    }

  fun parseFile(content: String?): List<KademliaSeed> {
    if (content.isNullOrBlank() || content.length > KademliaConfig.MAX_SEED_FILE_BYTES) return emptyList()
    val array = runCatching { JSONArray(content) }.getOrElse { return emptyList() }
    return (0 until array.length()).mapNotNull { index ->
      val item = array.optJSONObject(index) ?: return@mapNotNull null
      seedFromJson(item)
    }
  }

  private fun parseDnsEntry(entry: String): KademliaSeed? {
    val fields = HashMap<String, String>()
    entry.split(';').forEach { part ->
      val separator = part.indexOf('=')
      if (separator <= 0 || separator == part.lastIndex) return@forEach
      val key = part.substring(0, separator).trim()
      val value = part.substring(separator + 1).trim()
      if (key.isNotEmpty() && value.isNotEmpty()) fields[key] = value
    }
    return seed(fields["upeerId"], fields["address"], fields["publicKey"])
  }

  private fun seedFromJson(item: JSONObject): KademliaSeed? =
    seed(
      item.optString("upeerId").ifBlank { null },
      item.optString("address").ifBlank { null },
      item.optString("publicKey").ifBlank { null },
    )

  private fun seed(upeerId: String?, address: String?, publicKey: String?): KademliaSeed? {
    if (upeerId == null || address == null || publicKey == null) return null
    return KademliaSeed(upeerId, address, publicKey)
  }
}

object KademliaSeedValidator {
  fun normalize(candidate: KademliaSeed, selfUpeerId: String): KademliaSeed? {
    val upeerId = candidate.upeerId.trim().lowercase()
    if (upeerId.length != 64 || !KademliaId.isHex(upeerId)) return null
    if (upeerId == selfUpeerId.trim().lowercase()) return null
    val address = candidate.address.trim()
    if (!validAddress(address)) return null
    val publicKey = candidate.publicKey.trim().lowercase()
    if (publicKey.length != 64 || !KademliaId.isHex(publicKey)) return null
    if (!derivesUpeerId(publicKey, upeerId)) return null
    return KademliaSeed(upeerId, address, publicKey)
  }

  fun normalize(contact: KademliaContact, selfUpeerId: String): KademliaContact? {
    val seed = normalize(KademliaSeed(contact.upeerId, contact.address, contact.publicKey), selfUpeerId) ?: return null
    val nodeId = runCatching { KademliaId.fromUpeerId(seed.upeerId) }.getOrNull() ?: return null
    return KademliaContact(nodeId, seed.upeerId, seed.address, seed.publicKey, contact.lastSeen, contact.dhtSeq)
  }

  fun derivesUpeerId(publicKeyHex: String, upeerId: String): Boolean {
    if (publicKeyHex.length != 64 || !KademliaId.isHex(publicKeyHex)) return false
    if (upeerId.length != 64 || !KademliaId.isHex(upeerId)) return false
    val digest = Blake2b.hash256(decodeHex(publicKeyHex))
    return hex(digest).equals(upeerId, ignoreCase = true)
  }

  private fun validAddress(address: String): Boolean {
    if (address.isEmpty() || address.length > DhtLimits.MAX_ADDRESS_LENGTH) return false
    if (address.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }) return false
    if (!address.contains(':')) return validIpv4(address)
    return try {
      val parsed = InetAddress.getByName(address)
      parsed is Inet6Address && !parsed.isAnyLocalAddress && !parsed.isMulticastAddress
    } catch (_: UnknownHostException) {
      false
    }
  }

  private fun validIpv4(address: String): Boolean {
    val parts = address.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
      part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } && part.toInt() in 0..255
    } && address != "0.0.0.0"
  }

  private fun decodeHex(value: String): ByteArray =
    ByteArray(value.length / 2) { index ->
      value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

  private fun hex(bytes: ByteArray): String =
    bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
