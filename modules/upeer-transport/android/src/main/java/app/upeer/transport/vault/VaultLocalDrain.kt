package app.upeer.transport.vault

internal object VaultLocalDrain {
  fun select(
    entries: List<VaultEntry>,
    selfUpeerId: String,
    deviceId: String?,
    nowMillis: Long,
  ): List<VaultEntry> {
    val self = selfUpeerId.lowercase()
    val device = deviceId?.lowercase()?.takeIf(String::isNotEmpty)
    return entries.filter { entry ->
      entry.recipientSid.equals(self, ignoreCase = true) &&
        (entry.recipientDeviceId.isNullOrBlank() || device == null || entry.recipientDeviceId.equals(device, ignoreCase = true)) &&
        entry.expiresAt > nowMillis
    }
  }
}
