package app.upeer.transport

internal data class SyncFileWork(
  val total: Int = 0,
  val done: Int = 0,
  val pending: Int = 0,
  val failed: Int = 0,
  val progressUnits: Float? = null,
) {
  val known: Boolean get() = total > 0
  val idle: Boolean get() = pending <= 0
  val units: Float get() = progressUnits ?: done.toFloat()
  val percent: Int
    get() = if (total <= 0) 0 else ((units * 100f) / total.toFloat()).toInt().coerceIn(0, 100)

  companion object {
    val EMPTY = SyncFileWork()
  }
}

internal data class AttachmentRef(val fileId: String, val savedPath: String?)
