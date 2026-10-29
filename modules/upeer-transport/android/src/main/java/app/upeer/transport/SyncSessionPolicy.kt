package app.upeer.transport

internal object SyncSessionPolicy {
  fun mayInitiate(myDeviceId: String, peerDeviceId: String, hasSession: Boolean): Boolean {
    if (hasSession) return true
    val mine = myDeviceId.trim().lowercase()
    val peer = peerDeviceId.trim().lowercase()
    if (mine.isEmpty() || peer.isEmpty()) return false
    return mine < peer
  }
}
