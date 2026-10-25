package app.upeer.transport.vault

import app.upeer.transport.ProtocolStateStore
import app.upeer.transport.YggdrasilIdentity

internal fun interface VaultPointerPublisher {
  fun publish(key: String, value: String): Int
}

internal fun interface VaultPointerReader {
  fun read(key: String): Any?
}

internal class VaultDirectory(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val publish: VaultPointerPublisher,
  private val read: VaultPointerReader,
) {
  fun publish(recipientUpeerId: String, custodians: List<VaultCustodianCandidate>, nowMillis: Long): Boolean {
    val self = identity.current()?.upeerID() ?: return false
    val publication = VaultPointers.plan(self, recipientUpeerId, custodians, nowMillis) ?: return false
    return publish.publish(publication.key, publication.value) > 0
  }

  fun resolve(selfUpeerId: String, deviceId: String?, nowMillis: Long): List<VaultCustodianCandidate> {
    for (key in VaultPointerKey.readKeys(selfUpeerId, deviceId, nowMillis)) {
      val pointer = VaultPointers.parse(read.read(key), nowMillis) ?: continue
      val fromEndpoints = pointer.endpoints.map { VaultCustodianCandidate(it.upeerId, it.address, 0L) }
      if (fromEndpoints.isNotEmpty()) return fromEndpoints
      val fromState = pointer.custodians.mapNotNull { custodianId ->
        val address = state.addressFor(custodianId) ?: return@mapNotNull null
        VaultCustodianCandidate(custodianId, address, 0L)
      }
      if (fromState.isNotEmpty()) return fromState
    }
    return emptyList()
  }
}
