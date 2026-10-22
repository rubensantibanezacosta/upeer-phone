package app.upeer.transport.dht

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_TARGETS = 512
private const val MAX_ADDRESSES = 8

internal fun dhtUpdateTargets(
  peersJSON: String?,
  extraAddresses: (String) -> List<String>,
): List<DhtPeerEndpoint> {
  if (peersJSON.isNullOrBlank()) return emptyList()
  val peers = runCatching { JSONObject(peersJSON) }.getOrNull() ?: return emptyList()
  val targets = mutableListOf<DhtPeerEndpoint>()
  peers.keys().forEach { key ->
    if (targets.size >= MAX_TARGETS) return@forEach
    val upeerId = key.trim().lowercase()
    if (!DhtLocation.isHex(upeerId, 32)) return@forEach
    val peer = peers.optJSONObject(key) ?: return@forEach
    if (!DhtLocation.isHex(peer.optString("publicKey"), 32)) return@forEach
    val addresses = linkedSetOf<String>()
    (listOf(peer.optString("address")) + extraAddresses(upeerId)).forEach { candidate ->
      val normalized = runCatching { DhtLocation.normalize(listOf(candidate)).first() }.getOrNull()
      if (normalized != null && addresses.size < MAX_ADDRESSES) addresses += normalized
    }
    if (addresses.isNotEmpty()) targets += DhtPeerEndpoint(upeerId, addresses.toList())
  }
  return targets
}

internal class DhtUpdateDispatcher(
  private val targets: () -> List<DhtPeerEndpoint>,
  private val send: (String, DhtMessage) -> Unit,
  private val onError: (String, Throwable) -> Unit = { address, cause ->
    Log.w(TAG, "No se pudo enviar DHT_UPDATE a $address: ${cause.message ?: cause.javaClass.simpleName}")
  },
) {
  private data class SentState(
    val dhtSeq: Long,
    val blockAddresses: List<String>,
    val targetAddresses: List<String>,
  )

  private val lock = Any()
  private val sent = HashMap<String, SentState>()

  fun dispatch(blockJSON: String): Int {
    val block = runCatching { JSONObject(blockJSON) }.getOrNull() ?: return 0
    val dhtSeq = block.optLong("dhtSeq", -1L)
    if (dhtSeq < 0L) return 0
    val addresses = blockAddresses(block)
    if (addresses.isEmpty()) return 0
    var attempts = 0
    targets().take(MAX_TARGETS).forEach { target ->
      val all = runCatching { DhtLocation.normalize(target.addresses) }.getOrNull()
        ?.take(MAX_ADDRESSES)
        ?: return@forEach
      synchronized(lock) {
        val previous = sent[target.upeerId]
        if (previous != null && dhtSeq < previous.dhtSeq) return@forEach
        if (
          previous != null &&
          dhtSeq == previous.dhtSeq &&
          addresses == previous.blockAddresses &&
          all == previous.targetAddresses
        ) {
          return@forEach
        }
        sent[target.upeerId] = SentState(dhtSeq, addresses, all)
      }
      all.forEach { address ->
        runCatching { send(address, DhtUpdate(null, block)) }
          .onSuccess { attempts += 1 }
          .onFailure { cause -> onError(address, cause) }
      }
    }
    return attempts
  }

  fun reset() {
    synchronized(lock) { sent.clear() }
  }

  private fun blockAddresses(block: JSONObject): List<String> {
    val values = when (val raw = block.opt("addresses")) {
      is JSONArray -> (0 until raw.length()).mapNotNull { index -> raw.optString(index).ifBlank { null } }
      else -> emptyList()
    }
    val candidates = if (values.isEmpty()) listOf(block.optString("address")) else values
    return runCatching { DhtLocation.normalize(candidates).take(MAX_ADDRESSES) }.getOrDefault(emptyList())
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
