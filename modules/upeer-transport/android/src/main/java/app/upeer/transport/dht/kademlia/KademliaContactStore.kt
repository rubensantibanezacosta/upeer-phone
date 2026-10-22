package app.upeer.transport.dht.kademlia

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

interface KademliaContactStore {
  fun load(): List<KademliaContact>

  fun save(contacts: List<KademliaContact>): Boolean
}

class KademliaContactFileStore(
  private val file: File,
  private val selfUpeerId: String,
  private val maxContacts: Int = KademliaConfig.MAX_PERSISTED_CONTACTS,
  private val maxFileBytes: Int = KademliaConfig.MAX_SEED_FILE_BYTES,
) : KademliaContactStore {
  override fun load(): List<KademliaContact> {
    if (!file.isFile || file.length() <= 0 || file.length() > maxFileBytes) return emptyList()
    val content = runCatching { file.readText() }.getOrElse { return emptyList() }
    val array = runCatching { JSONArray(content) }.getOrElse { return emptyList() }
    return (0 until array.length()).mapNotNull { index ->
      val item = array.optJSONObject(index) ?: return@mapNotNull null
      val seed = KademliaSeed(
        item.optString("upeerId"),
        item.optString("address"),
        item.optString("publicKey"),
      )
      val lastSeen = item.optLong("lastSeen", 0L)
      if (lastSeen <= 0L) return@mapNotNull null
      val normalized = KademliaSeedValidator.normalize(seed, selfUpeerId) ?: return@mapNotNull null
      val nodeId = runCatching { KademliaId.fromUpeerId(normalized.upeerId) }.getOrNull() ?: return@mapNotNull null
      val dhtSeq = if (item.has("dhtSeq") && !item.isNull("dhtSeq")) item.optLong("dhtSeq") else null
      KademliaContact(nodeId, normalized.upeerId, normalized.address, normalized.publicKey, lastSeen, dhtSeq)
    }.distinctBy { it.upeerId }
      .sortedByDescending { it.lastSeen }
      .take(maxContacts.coerceAtLeast(0))
  }

  override fun save(contacts: List<KademliaContact>): Boolean {
    val normalized = contacts
      .filter { it.lastSeen > 0L }
      .mapNotNull { KademliaSeedValidator.normalize(it, selfUpeerId) }
      .distinctBy { it.upeerId }
      .sortedByDescending { it.lastSeen }
      .take(maxContacts.coerceAtLeast(0))
    val array = JSONArray()
    normalized.forEach { contact ->
      val item = JSONObject()
        .put("upeerId", contact.upeerId)
        .put("address", contact.address)
        .put("publicKey", contact.publicKey)
        .put("lastSeen", contact.lastSeen)
      contact.dhtSeq?.let { item.put("dhtSeq", it) }
      array.put(item)
    }
    val content = array.toString()
    if (content.length > maxFileBytes) return false
    return runCatching {
      file.parentFile?.mkdirs()
      val temporary = File(file.parentFile, "${file.name}.tmp")
      temporary.writeText(content)
      if (!temporary.renameTo(file)) {
        temporary.delete()
        return@runCatching false
      }
      true
    }.getOrElse {
      false
    }
  }
}
