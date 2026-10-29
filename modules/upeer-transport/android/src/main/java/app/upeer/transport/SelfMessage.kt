package app.upeer.transport

internal object SelfMessage {
  fun isSelfChat(upeerId: String, ownUpeerId: String?): Boolean {
    if (ownUpeerId.isNullOrBlank() || upeerId.isBlank()) return false
    return upeerId.lowercase() == ownUpeerId.lowercase()
  }
}
