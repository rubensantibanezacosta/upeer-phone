package app.upeer.transport

internal enum class SignedPreKeyUpdate {
  UPDATED,
  SAME,
  STALE,
  INVALID,
}

internal object DrResetPolicy {
  private val RESET_ID = Regex(
    "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    RegexOption.IGNORE_CASE,
  )

  fun validResetId(value: String): Boolean = RESET_ID.matches(value)

  fun classifySignedPreKey(
    currentId: Long,
    currentPublicKey: String,
    candidateId: Long,
    candidatePublicKey: String,
  ): SignedPreKeyUpdate = when {
    candidateId <= 0 || candidatePublicKey.length != 64 -> SignedPreKeyUpdate.INVALID
    currentId <= 0 -> SignedPreKeyUpdate.UPDATED
    candidateId < currentId -> SignedPreKeyUpdate.STALE
    candidateId == currentId && currentPublicKey.isNotEmpty() && currentPublicKey != candidatePublicKey -> SignedPreKeyUpdate.INVALID
    candidateId == currentId -> SignedPreKeyUpdate.SAME
    else -> SignedPreKeyUpdate.UPDATED
  }
}

internal class DrResetReplayCache(
  private val now: () -> Long = System::currentTimeMillis,
) {
  private val entries = HashMap<String, LinkedHashMap<String, Long>>()

  @Synchronized
  fun accept(upeerId: String, resetId: String): Boolean {
    if (!DrResetPolicy.validResetId(resetId)) return false
    val current = now()
    val recent = entries.getOrPut(upeerId) { LinkedHashMap() }
    val iterator = recent.entries.iterator()
    while (iterator.hasNext()) {
      if (current - iterator.next().value > RESET_TTL_MS) iterator.remove()
    }
    if (recent.containsKey(resetId)) return false
    recent[resetId] = current
    while (recent.size > MAX_RESET_IDS_PER_PEER) {
      val oldest = recent.keys.firstOrNull() ?: break
      recent.remove(oldest)
    }
    return true
  }

  @Synchronized
  fun clear() = entries.clear()

  private companion object {
    private const val MAX_RESET_IDS_PER_PEER = 64
    private const val RESET_TTL_MS = 24 * 60 * 60 * 1000L
  }
}
