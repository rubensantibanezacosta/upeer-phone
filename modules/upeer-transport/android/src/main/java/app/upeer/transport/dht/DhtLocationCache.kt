package app.upeer.transport.dht

internal class DhtLocationCache(
  private val clock: () -> Long = System::currentTimeMillis,
  private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
  private val maxEntries: Int = MAX_ENTRIES,
) {
  private data class Entry(val blockJSON: String, val expiresAt: Long)

  private val lock = Any()
  private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

  fun get(accountUpeerId: String): String? = synchronized(lock) {
    val account = accountUpeerId.trim().lowercase()
    val entry = entries[account] ?: return@synchronized null
    if (clock() < entry.expiresAt) return@synchronized entry.blockJSON
    entries.remove(account)
    null
  }

  fun put(accountUpeerId: String, blockJSON: String, blockExpiresAt: Long) {
    if (blockJSON.isEmpty() || ttlMillis <= 0 || maxEntries <= 0) return
    val now = clock()
    val expiresAt = minOf(blockExpiresAt, now + ttlMillis)
    if (expiresAt <= now) return
    val account = accountUpeerId.trim().lowercase()
    synchronized(lock) {
      if (!entries.containsKey(account) && entries.size >= maxEntries) {
        val oldest = entries.keys.firstOrNull()
        if (oldest != null) entries.remove(oldest)
      }
      entries[account] = Entry(blockJSON, expiresAt)
    }
  }

  fun invalidate(accountUpeerId: String) {
    val account = accountUpeerId.trim().lowercase()
    synchronized(lock) { entries.remove(account) }
  }

  fun clear() = synchronized(lock) { entries.clear() }

  private companion object {
    private const val DEFAULT_TTL_MILLIS = 5L * 60L * 1000L
    private const val MAX_ENTRIES = 256
  }
}
