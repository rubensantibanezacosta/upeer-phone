package app.upeer.transport

internal class InboundRateLimiter(
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private data class Rule(val windowMs: Long, val maxTokens: Int)

  private data class Bucket(
    var tokens: Double,
    var lastRefill: Long,
  )

  private val lock = Any()
  private val buckets = HashMap<String, Bucket>()
  private var lastCleanup = 0L
  private var limited = 0L

  fun allow(source: String, type: String): Boolean {
    val rule = RULES[type] ?: return true
    if (source.isBlank()) return true
    val now = clock()
    synchronized(lock) {
      cleanupLocked(now)
      val bucket = buckets.getOrPut("$source:$type") { Bucket(rule.maxTokens.toDouble(), now) }
      val elapsed = (now - bucket.lastRefill).coerceAtLeast(0L)
      if (elapsed > 0) {
        val refill = elapsed * (rule.maxTokens.toDouble() / rule.windowMs)
        bucket.tokens = minOf(rule.maxTokens.toDouble(), bucket.tokens + refill)
        bucket.lastRefill = now
      }
      if (bucket.tokens < 1.0) {
        limited += 1
        return false
      }
      bucket.tokens -= 1.0
      return true
    }
  }

  fun limitedCount(): Long = synchronized(lock) { limited }

  private fun cleanupLocked(now: Long) {
    if (now - lastCleanup < CLEANUP_INTERVAL_MS) return
    lastCleanup = now
    buckets.entries.removeAll { now - it.value.lastRefill > IDLE_TIMEOUT_MS }
  }

  private companion object {
    private const val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L
    private const val IDLE_TIMEOUT_MS = 60 * 60 * 1000L
    private val RULES = mapOf(
      "SEALED" to Rule(windowMs = 1_000, maxTokens = 5_000),
      "HANDSHAKE_REQ" to Rule(windowMs = 60_000, maxTokens = 50),
      "HANDSHAKE_ACCEPT" to Rule(windowMs = 60_000, maxTokens = 50),
      "CHAT" to Rule(windowMs = 60_000, maxTokens = 100),
      "ACK" to Rule(windowMs = 60_000, maxTokens = 200),
      "READ" to Rule(windowMs = 60_000, maxTokens = 200),
      "TYPING" to Rule(windowMs = 60_000, maxTokens = 600),
      "PING" to Rule(windowMs = 10_000, maxTokens = 60),
      "CHAT_REACTION" to Rule(windowMs = 60_000, maxTokens = 60),
      "CHAT_DELETE" to Rule(windowMs = 60_000, maxTokens = 20),
      "DR_RESET" to Rule(windowMs = 60_000, maxTokens = 20),
      "DHT_QUERY" to Rule(windowMs = 30_000, maxTokens = 20),
      "DHT_RESPONSE" to Rule(windowMs = 30_000, maxTokens = 40),
      "DHT_UPDATE" to Rule(windowMs = 60_000, maxTokens = 40),
      "DHT_EXCHANGE" to Rule(windowMs = 60_000, maxTokens = 60),
      "DHT_FIND_NODE" to Rule(windowMs = 30_000, maxTokens = 30),
      "DHT_FIND_VALUE" to Rule(windowMs = 30_000, maxTokens = 30),
      "DHT_STORE" to Rule(windowMs = 60_000, maxTokens = 10),
      "VAULT_STORE" to Rule(windowMs = 60_000, maxTokens = 600),
      "VAULT_QUERY" to Rule(windowMs = 60_000, maxTokens = 10),
      "VAULT_DELIVERY" to Rule(windowMs = 60_000, maxTokens = 2_000),
      "VAULT_ACK" to Rule(windowMs = 60_000, maxTokens = 2_000),
      "VAULT_RENEW" to Rule(windowMs = 60_000, maxTokens = 600),
      "ENROLL_REQ" to Rule(windowMs = 60_000, maxTokens = 20),
      "ENROLL_RES" to Rule(windowMs = 60_000, maxTokens = 20),
      "SYNC_HELLO" to Rule(windowMs = 60_000, maxTokens = 30),
      "SYNC_ENVELOPE" to Rule(windowMs = 60_000, maxTokens = 1_200),
      "SYNC_ACK" to Rule(windowMs = 60_000, maxTokens = 1_200),
      "SYNC_HISTORY_REQ" to Rule(windowMs = 60_000, maxTokens = 20),
      "SYNC_HISTORY_RES" to Rule(windowMs = 60_000, maxTokens = 60),
      "FILE_PROPOSAL" to Rule(windowMs = 60_000, maxTokens = 30),
      "FILE_ACCEPT" to Rule(windowMs = 60_000, maxTokens = 30),
      "FILE_CHUNK" to Rule(windowMs = 60_000, maxTokens = 4_000),
      "FILE_ACK" to Rule(windowMs = 60_000, maxTokens = 4_000),
      "FILE_DONE" to Rule(windowMs = 60_000, maxTokens = 60),
      "FILE_DONE_ACK" to Rule(windowMs = 60_000, maxTokens = 60),
      "FILE_CANCEL" to Rule(windowMs = 60_000, maxTokens = 30),
    )
  }
}
