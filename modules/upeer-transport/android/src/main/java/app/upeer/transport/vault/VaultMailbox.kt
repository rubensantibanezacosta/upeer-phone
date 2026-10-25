package app.upeer.transport.vault

internal data class VaultMailboxState(
  val nextId: Long,
  val entries: List<VaultEntry>,
)

internal sealed class VaultMailboxInsert {
  data class Stored(val state: VaultMailboxState, val entry: VaultEntry) : VaultMailboxInsert()
  data class Rejected(val reason: String) : VaultMailboxInsert()
}

internal object VaultMailbox {
  const val MAX_ENTRIES = 2_000
  const val QUOTA_BYTES_PER_SENDER = 50L * 1024 * 1024

  fun insert(
    state: VaultMailboxState,
    entry: VaultEntry,
    nowMillis: Long,
    quotaBytes: Long = QUOTA_BYTES_PER_SENDER,
  ): VaultMailboxInsert {
    val current = purge(state, nowMillis)
    val existing = current.entries.firstOrNull { it.payloadHash == entry.payloadHash }
    if (existing != null) return VaultMailboxInsert.Stored(current, existing)
    val size = entry.data.length.toLong() / 2
    if (current.entries.size >= MAX_ENTRIES) return VaultMailboxInsert.Rejected("buzón lleno")
    if (usage(current.entries, entry.senderSid) + size > quotaBytes) {
      return VaultMailboxInsert.Rejected("cuota del remitente agotada")
    }
    val stored = entry.copy(id = current.nextId)
    return VaultMailboxInsert.Stored(VaultMailboxState(current.nextId + 1, current.entries + stored), stored)
  }

  fun purge(state: VaultMailboxState, nowMillis: Long): VaultMailboxState =
    state.copy(entries = state.entries.filter { it.expiresAt > nowMillis })

  fun usage(entries: List<VaultEntry>, senderSid: String): Long {
    val sender = senderSid.lowercase()
    return entries.filter { it.senderSid == sender }.sumOf { it.data.length.toLong() / 2 }
  }

  fun renew(state: VaultMailboxState, payloadHash: String, expiresAt: Long): Pair<VaultMailboxState, Boolean> {
    val hash = payloadHash.lowercase()
    var renewed = false
    val entries = state.entries.map { entry ->
      if (entry.payloadHash != hash || expiresAt <= entry.expiresAt) {
        entry
      } else {
        renewed = true
        entry.copy(expiresAt = expiresAt)
      }
    }
    return if (renewed) VaultMailboxState(state.nextId, entries) to true else state to false
  }

  fun expiringSoon(state: VaultMailboxState, windowMillis: Long, nowMillis: Long): List<VaultEntry> {
    val limit = nowMillis + windowMillis
    return state.entries.filter { it.expiresAt > nowMillis && it.expiresAt < limit }
  }

  fun release(state: VaultMailboxState, payloadHashes: Collection<String>): Pair<VaultMailboxState, Int> {
    val hashes = payloadHashes.map { it.lowercase() }.toSet()
    val kept = state.entries.filterNot { hashes.contains(it.payloadHash) }
    return VaultMailboxState(state.nextId, kept) to (state.entries.size - kept.size)
  }
}
