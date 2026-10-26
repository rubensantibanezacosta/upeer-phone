package app.upeer.transport

internal class FileSendWindow(
  private val totalChunks: Int,
  private val retransmitMillis: Long = DEFAULT_RETRANSMIT_MS,
  private val maxWindow: Int = DEFAULT_MAX_WINDOW,
  initialWindow: Int = DEFAULT_INITIAL_WINDOW,
) {
  private var window = initialWindow.coerceIn(1, maxWindow.coerceAtLeast(1))
  private var nextIndex = 0
  private val inFlight = LinkedHashMap<Int, Long>()
  private val acked = LinkedHashSet<Int>()

  fun windowSize(): Int = window

  fun next(): Int = nextIndex

  fun complete(): Boolean = totalChunks == 0 || acked.size >= totalChunks

  fun pending(): Int = inFlight.size

  fun ackedCount(): Int = acked.size

  fun nextBatch(now: Long): List<Int> {
    if (nextIndex >= totalChunks) return emptyList()
    val batch = ArrayList<Int>()
    while (nextIndex < totalChunks && inFlight.size < window) {
      inFlight[nextIndex] = now
      batch.add(nextIndex)
      nextIndex++
    }
    return batch
  }

  fun expired(now: Long): List<Int> {
    if (inFlight.isEmpty()) return emptyList()
    val due = ArrayList<Int>()
    for ((index, sentAt) in inFlight) {
      if (now - sentAt >= retransmitMillis) due.add(index)
    }
    for (index in due) inFlight[index] = now
    return due
  }

  fun ack(index: Int, now: Long): Boolean {
    if (index < 0 || index >= totalChunks) return false
    val sentAt = inFlight.remove(index) ?: return acked.contains(index)
    acked.add(index)
    val rtt = now - sentAt
    if (rtt in 1 until FAST_ACK_RTT_MS && window < maxWindow) window++
    return true
  }

  fun backoff() {
    window = maxOf(1, window / 2)
  }

  fun hasWork(): Boolean = nextIndex < totalChunks || inFlight.isNotEmpty()

  companion object {
    const val DEFAULT_INITIAL_WINDOW = 8
    const val DEFAULT_MAX_WINDOW = 64
    const val DEFAULT_RETRANSMIT_MS = 1_000L
    private const val FAST_ACK_RTT_MS = 150L
  }
}
