package app.upeer.transport.vault

internal fun interface VaultSender {
  fun send(address: String, packetJSON: String): Boolean
}

internal data class VaultHandoverResult(
  val attempted: Int,
  val accepted: Int,
) {
  val stored: Boolean get() = accepted > 0
}

internal object VaultHandover {
  fun replicate(
    envelope: VaultStoreEnvelope.Envelope,
    custodians: List<VaultCustodianCandidate>,
    sender: VaultSender,
  ): VaultHandoverResult {
    val addresses = LinkedHashSet<String>()
    for (custodian in custodians) {
      val address = custodian.address.trim()
      if (address.isEmpty()) continue
      addresses.add(address.lowercase())
    }
    var accepted = 0
    for (address in addresses) {
      if (sender.send(address, envelope.json)) accepted++
    }
    return VaultHandoverResult(addresses.size, accepted)
  }
}
