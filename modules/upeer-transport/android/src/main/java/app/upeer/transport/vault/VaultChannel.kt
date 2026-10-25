package app.upeer.transport.vault

import app.upeer.transport.ProtocolStateStore
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
    return wrap(envelopeJSON, peerPublicKey)
  }

  fun renewPacket(custodianUpeerId: String, payloadHash: String, expiresAt: Long): String? {
    val peerPublicKey = state.publicKeyFor(custodianUpeerId) ?: return null
    val renew = VaultRenewal.build(payloadHash, expiresAt) ?: return null
    return wrap(renew.toString(), peerPublicKey)
  }

  fun queryPacket(custodianUpeerId: String, payloadHash: String? = null): String? {    val self = identity.current()?.upeerID() ?: return null
    val peerPublicKey = state.publicKeyFor(custodianUpeerId) ?: return null
    val query = runCatching {
      VaultQueryDelivery.buildQuery(self, identity.currentDevice()?.deviceId, payloadHash)
    }.getOrNull() ?: return null
    return wrap(query.toString(), peerPublicKey)
  }

  private fun wrap(packetJSON: String, peerPublicKey: String): String? {
    val crypto = identity.current() ?: return null
    val address = selfAddress().takeIf(String::isNotEmpty) ?: return null
    val device = identity.currentDevice()
    val signed = runCatching {
      if (device != null) {
        device.crypto.buildLinkedControl(device.deviceId, crypto.upeerID(), address, packetJSON)
      } else {
        crypto.buildSignedPacket(address, packetJSON)
      }
    }.getOrNull() ?: return null
    val ciphertext = runCatching { crypto.seal(signed.toByteArray(Charsets.UTF_8), peerPublicKey) }.getOrNull()
      ?: return null
    return JSONObject()
      .put("type", "SEALED")
      .put("ciphertext", ciphertext)
      .toString()
  }
}
