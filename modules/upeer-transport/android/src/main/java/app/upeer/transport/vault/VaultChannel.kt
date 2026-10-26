package app.upeer.transport.vault

import app.upeer.transport.ProtocolStateStore
import app.upeer.transport.SealedControl
import app.upeer.transport.YggdrasilIdentity
import app.upeer.transport.dht.DhtLocation
import org.json.JSONObject

internal class VaultChannel(
  private val identity: YggdrasilIdentity,
  private val state: ProtocolStateStore,
  private val selfAddress: () -> String,
) {
  fun custodians(selfUpeerId: String, recipientUpeerId: String): List<VaultCustodianCandidate> {
    val peers = runCatching { JSONObject(state.peersJSON()) }.getOrNull() ?: return emptyList()
    val candidates = mutableListOf<VaultCustodianCandidate>()
    val keys = peers.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      if (!DhtLocation.isHex(key, 32)) continue
      val peer = peers.optJSONObject(key) ?: continue
      candidates.add(
        VaultCustodianCandidate(
          upeerId = key,
          address = peer.optString("address"),
          lastSeenMillis = peer.optLong("lastSeen", 0L),
        ),
      )
    }
    return VaultCustody.select(candidates, selfUpeerId, recipientUpeerId)
  }

  fun envelopeForStore(
    packetJSON: String,
    recipientUpeerId: String,
    nowMillis: Long,
  ): VaultStoreEnvelope.Envelope? {
    val self = identity.current()?.upeerID() ?: return null
    return runCatching { VaultStoreEnvelope.build(packetJSON, recipientUpeerId, self, null, nowMillis) }.getOrNull()
  }

  fun storePacket(custodianUpeerId: String, envelopeJSON: String): String? {
    val peerPublicKey = state.publicKeyFor(custodianUpeerId) ?: return null
    return wrap(envelopeJSON, peerPublicKey, "VAULT_STORE")
  }

  fun renewPacket(custodianUpeerId: String, payloadHash: String, expiresAt: Long): String? {
    val peerPublicKey = state.publicKeyFor(custodianUpeerId) ?: return null
    val renew = VaultRenewal.build(payloadHash, expiresAt) ?: return null
    return wrap(renew.toString(), peerPublicKey, VaultRenewal.TYPE)
  }

  fun queryPacket(custodianUpeerId: String, payloadHash: String? = null): String? {
    val self = identity.current()?.upeerID() ?: return null
    val peerPublicKey = state.publicKeyFor(custodianUpeerId) ?: return null
    val query = runCatching {
      VaultQueryDelivery.buildQuery(self, identity.currentDevice()?.deviceId, payloadHash)
    }.getOrNull() ?: return null
    return wrap(query.toString(), peerPublicKey, "VAULT_QUERY")
  }

  private fun wrap(packetJSON: String, peerPublicKey: String, label: String): String? {
    val crypto = identity.current() ?: return null
    val address = selfAddress().takeIf(String::isNotEmpty) ?: return null
    return SealedControl.tryFrame(crypto, identity.currentDevice(), address, peerPublicKey, JSONObject(packetJSON), label)
  }
}
