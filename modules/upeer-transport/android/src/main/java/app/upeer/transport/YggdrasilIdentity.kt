package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal class YggdrasilIdentity(context: Context) {
  private val vault = IdentityVault(context)
  private val devices = DeviceIdentityStore(context)
  private val registries = AccountRegistryStore(context)
  private val lock = Any()
  private var identity: CryptoIdentity? = null

  init {
    load()
  }

  fun set(secretKey: ByteArray) {
    val nextIdentity = CryptoIdentity(secretKey)
    vault.save(secretKey)
    synchronized(lock) { identity = nextIdentity }
    ensureDevice(nextIdentity)
    Log.i(TAG, "Identidad uPeer provisionada en el servicio")
  }

  fun clear() {
    try {
      registries.clear()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo eliminar el registro de cuenta del Keystore", cause)
    }
    try {
      devices.clear()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo eliminar la identidad de dispositivo del Keystore", cause)
    }
    vault.clear()
    synchronized(lock) { identity = null }
    Log.i(TAG, "Identidad uPeer eliminada del servicio")
  }

  fun current(): CryptoIdentity? = synchronized(lock) { identity }

  fun currentDevice(): DeviceIdentity? = current()?.let(::ensureDevice)

  fun currentDeviceAdmission(): Boolean? {
    val account = current() ?: return null
    val device = devices.current()?.takeIf { it.accountUpeerId == account.upeerID() } ?: return null
    return registries.currentAdmission(account.upeerID(), device.deviceId)
  }

  fun refreshRegistry() {
    try {
      currentRegistryJSON()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo refrescar el registro de cuenta", cause)
    }
  }

  fun deviceStatusJSON(): String = devices.statusJSON(current()?.upeerID())

  fun currentRegistryJSON(): String {
    val account = current() ?: return ""
    val device = ensureDevice(account) ?: return ""
    return registries.ensure(account.upeerID(), device.certificateJSON, CryptoAccountRegistrySigner(account)).registryJSON
  }

  fun registryStatusJSON(): String {
    val account = current() ?: return JSONObject().put("ready", false).toString()
    val device = ensureDevice(account)
    if (device != null) {
      try {
        registries.ensure(account.upeerID(), device.certificateJSON, CryptoAccountRegistrySigner(account))
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudo refrescar el registro de cuenta", cause)
      }
    }
    return registries.statusJSON(account.upeerID(), device?.deviceId)
  }

  fun requireDeviceAdmission() {
    val account = current() ?: throw IllegalStateException("uPeer identity is not available")
    val device = ensureDevice(account) ?: run {
      Log.w(TAG, "Sin certificado de dispositivo; se permite el bloque firmado por la cuenta")
      return
    }
    val snapshot = registries.ensure(account.upeerID(), device.certificateJSON, CryptoAccountRegistrySigner(account))
    if (!snapshot.localDeviceAdmitted) throw DeviceAdmissionException()
  }

  fun revokeDevice(deviceId: String, reason: String) {
    val account = current() ?: throw IllegalStateException("uPeer identity is not available")
    val device = ensureDevice(account) ?: throw IllegalStateException("device certificate is not available")
    registries.revoke(account.upeerID(), device.certificateJSON, deviceId, reason, CryptoAccountRegistrySigner(account))
  }

  fun validateDeviceAdmission(
    certificateJSON: String,
    registryJSON: String,
    expectedAccountUpeerId: String,
    nowMillis: Long,
  ): Boolean = Transportbridge.validateDeviceAdmission(certificateJSON, registryJSON, expectedAccountUpeerId, nowMillis)

  fun authorizerAccount(): EnrollmentAuthorizerAccount? {
    val account = current() ?: return null
    val device = devices.current()?.takeIf { it.accountUpeerId == account.upeerID() } ?: return null
    return RegistryEnrollmentAuthorizerAccount(account, device, registries)
  }

  fun statusJSON(): String = synchronized(lock) {
    val activeIdentity = identity
    if (activeIdentity == null) {
      JSONObject().put("ready", false).toString()
    } else {
      val device = devices.current()?.takeIf { it.accountUpeerId == activeIdentity.upeerID() }
      JSONObject()
        .put("ready", true)
        .put("publicKey", activeIdentity.publicKeyHex())
        .put("upeerId", activeIdentity.upeerID())
        .put("deviceId", device?.deviceId ?: "")
        .put("deviceCertificateReady", device != null)
        .toString()
    }
  }

  private fun load() {
    val secretKey = try {
      vault.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer la identidad uPeer cifrada", cause)
      return
    } ?: return
    val storedIdentity = try {
      CryptoIdentity(secretKey)
    } catch (cause: Exception) {
      Log.e(TAG, "La identidad uPeer almacenada no es válida; se elimina", cause)
      vault.clear()
      return
    } finally {
      secretKey.fill(0)
    }
    synchronized(lock) { identity = storedIdentity }
    Log.i(TAG, "Identidad uPeer cargada desde Keystore")
    ensureDevice(storedIdentity)
  }

  private fun ensureDevice(activeIdentity: CryptoIdentity): DeviceIdentity? {
    val device = try {
      devices.ensure(activeIdentity)
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo provisionar el certificado de dispositivo; se usará el bloque de cuenta", cause)
      return devices.current()?.takeIf { it.accountUpeerId == activeIdentity.upeerID() }
    }
    try {
      registries.ensure(activeIdentity.upeerID(), device.certificateJSON, CryptoAccountRegistrySigner(activeIdentity))
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo provisionar el registro de cuenta", cause)
    }
    return device
  }

  private companion object {
    private const val TAG = "UPeerTransport"
  }
}
