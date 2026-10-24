package app.upeer.transport

internal object OutboxRetryPolicy {
  const val MAX_ATTEMPTS = 30
  const val MAX_SENDS = 8
  const val BASE_BACKOFF_MS = 2_000L
  const val MAX_BACKOFF_MS = 60_000L

  fun backoffMs(attempt: Int): Long {
    val bounded = attempt.coerceAtLeast(1)
    val scaled = BASE_BACKOFF_MS * bounded
    return if (scaled < 0) MAX_BACKOFF_MS else minOf(MAX_BACKOFF_MS, scaled)
  }

  fun canRetry(attempts: Int): Boolean = attempts < MAX_ATTEMPTS

  fun canResend(sends: Int): Boolean = sends < MAX_SENDS
}
