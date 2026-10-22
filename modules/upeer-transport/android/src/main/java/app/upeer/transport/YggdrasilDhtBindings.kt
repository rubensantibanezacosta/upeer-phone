package app.upeer.transport

import android.util.Log
import app.upeer.transport.dht.DhtLocation
import app.upeer.transport.dht.DhtLocationStore

internal class YggdrasilDhtBindings(
  private val identity: YggdrasilIdentity,
  private val accountRegistry: AccountRegistryFacade,
  private val dhtLocation: DhtLocationStore,
  private val address: () -> String,
  private val requireAdmission: () -> Unit,
  private val onRevokedDevice: () -> Unit,
) {
  fun locationBlockJSON(ttlMillis: Long): String {
    val activeIdentity = identity.current() ?: error("uPeer identity is not ready")
    val currentAddress = address()
    val dhtSeq = dhtLocation.observeAddress(activeIdentity.upeerID(), currentAddress)
    val device = identity.currentDevice()
    if (device == null) {
      Log.w(TAG, "Certificado de dispositivo no disponible; se publica un bloque firmado por la cuenta")
      return DhtLocation.build(activeIdentity, listOf(currentAddress), dhtSeq, "", ttlMillis)
    }
    requireAdmission()
    return DhtLocation.buildDevice(device.crypto, device.certificateJSON, listOf(currentAddress), dhtSeq, ttlMillis)
  }

  fun accountRegistryJSON(): String = accountRegistry.registryJSON()

  fun accountRegistryStatusJSON(): String = accountRegistry.statusJSON()

  fun revokeAccountDevice(deviceId: String, reason: String): String {
    val status = accountRegistry.revoke(deviceId, reason)
    onRevokedDevice()
    return status
  }

  fun validateDeviceAdmission(
    certificateJSON: String,
    registryJSON: String,
    expectedAccountUpeerId: String,
    nowMillis: Long,
  ): Boolean = accountRegistry.validate(certificateJSON, registryJSON, expectedAccountUpeerId, nowMillis)

  fun deviceIdentityStatusJSON(): String = identity.deviceStatusJSON()

  fun dhtLocationStatusJSON(): String =
    dhtLocation.statusJSON(identity.current()?.upeerID(), identity.currentDevice()?.deviceId)

  fun verifyLocationBlock(blockJSON: String, publicKeyHex: String, nowMillis: Long): Boolean =
    DhtLocation.verify(blockJSON, publicKeyHex, nowMillis)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
