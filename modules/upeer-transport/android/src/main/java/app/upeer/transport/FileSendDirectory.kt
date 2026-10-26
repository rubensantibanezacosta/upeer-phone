package app.upeer.transport

internal interface FileSendDirectory {
  fun publicKeyFor(upeerId: String): String?
  fun devicePublicKeyFor(upeerId: String): String?
}

internal class StateFileSendDirectory(private val state: ProtocolStateStore) : FileSendDirectory {
  override fun publicKeyFor(upeerId: String): String? = state.publicKeyFor(upeerId)

  override fun devicePublicKeyFor(upeerId: String): String? =
    state.peerFor(upeerId)?.optString("devicePublicKey").orEmpty().takeIf { it.isNotEmpty() }
}
