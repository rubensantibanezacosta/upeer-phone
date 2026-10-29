package app.upeer.transport

internal object MessageStatusTransition {
  private val RANK = mapOf(
    "pending" to 0,
    "sent" to 1,
    "vaulted" to 2,
    "delivered" to 3,
    "read" to 4,
  )

  fun canTransition(from: String, to: String): Boolean {
    if (from == to) return false
    if (from == "read") return false
    if (from == "delivered") return to == "read"
    if (to == "failed") return true
    if (from == "failed") return to == "pending" || to == "sent" || to == "vaulted" || to == "delivered"
    return (RANK[to] ?: -1) > (RANK[from] ?: -1)
  }

  fun next(current: String, candidate: String): String =
    if (canTransition(current, candidate)) candidate else current
}
