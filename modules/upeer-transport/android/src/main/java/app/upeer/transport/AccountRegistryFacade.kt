package app.upeer.transport

import android.util.Log

internal class AccountRegistryFacade(private val identity: YggdrasilIdentity) {
  fun registryJSON(): String = try {
    identity.currentRegistryJSON()
  } catch (cause: Exception) {
    Log.e(TAG, "No se pudo construir el registro de cuenta", cause)
    ""
  }

  fun statusJSON(): String = identity.registryStatusJSON()

  fun revokedDevices(): Set<String> = try {
    identity.revokedDevices()
  } catch (cause: Exception) {
    Log.e(TAG, "No se pudo leer la lista de dispositivos revocados", cause)
    emptySet()
  }

  fun revoke(deviceId: String, reason: String): String {
    identity.revokeDevice(deviceId, reason)
    return identity.registryStatusJSON()
  }

  fun validate(
    certificateJSON: String,
    registryJSON: String,
    expectedAccountUpeerId: String,
    nowMillis: Long,
  ): Boolean = identity.validateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerId, nowMillis)

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
