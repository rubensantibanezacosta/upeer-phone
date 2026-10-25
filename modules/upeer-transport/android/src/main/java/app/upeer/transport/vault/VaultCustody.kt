package app.upeer.transport.vault

import app.upeer.transport.dht.DhtLocation

internal data class VaultCustodianCandidate(
  val upeerId: String,
  val address: String,
  val lastSeenMillis: Long,
)

internal object VaultCustody {
  const val REPLICATION_FACTOR = 8

  fun select(
    candidates: List<VaultCustodianCandidate>,
    selfUpeerId: String,
    recipientUpeerId: String,
    limit: Int = REPLICATION_FACTOR,
  ): List<VaultCustodianCandidate> {
    if (limit <= 0) return emptyList()
    val self = selfUpeerId.lowercase()
    val recipient = recipientUpeerId.lowercase()
    val seen = LinkedHashMap<String, VaultCustodianCandidate>()
    for (candidate in candidates) {
      val upeerId = candidate.upeerId.lowercase()
      if (!DhtLocation.isHex(upeerId, 32)) continue
      if (candidate.address.isBlank()) continue
      if (upeerId == self || upeerId == recipient) continue
      val previous = seen[upeerId]
      if (previous == null || candidate.lastSeenMillis > previous.lastSeenMillis) {
        seen[upeerId] = candidate.copy(upeerId = upeerId)
      }
    }
    return seen.values
      .sortedWith(compareByDescending<VaultCustodianCandidate> { it.lastSeenMillis }.thenBy { it.upeerId })
      .take(limit)
  }
}
