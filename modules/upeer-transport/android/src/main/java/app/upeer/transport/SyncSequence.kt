package app.upeer.transport

internal object SyncSequence {
  fun next(previous: Long, nowMillis: Long): Long {
    val floor = if (nowMillis > 0) nowMillis * 1000 else 0L
    return maxOf(previous + 1, floor)
  }
}
