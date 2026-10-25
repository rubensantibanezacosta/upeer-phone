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
  private val accountPublic = AccountPublicVault(context)
  private val lock = Any()
  private var identity: CryptoIdentity? = null

  init {
    load()
  }

  fun set(secretKey: ByteArray) {
    val nextIdentity = CryptoIdentity(secretKey)
    vault.save(secretKey)
    accountPublic.clear()
    synchronized(lock) { identity = nextIdentity }
    ensureDevice(nextIdentity)
    Log.i(TAG, "Identidad uPeer provisionada en el servicio")
  }

  fun setAccountPublic(accountUpeerId: String, accountPublicKey: String): Boolean {
    val normalizedId = accountUpeerId.lowercase()
    val normalizedKey = accountPublicKey.lowercase()
    val nextIdentity = try {
      Transportbridge.newDeviceOnlyCryptoIdentity(normalizedKey)
    } catch (cause: Exception) {
      Log.e(TAG, "La clave pública de cuenta no es válida", cause)
      return false
    }
    if (nextIdentity.upeerID() != normalizedId) {
      Log.e(TAG, "La clave pública de cuenta no deriva el upeerId indicado")
      return false
    }
    if (nextIdentity.publicKeyHex() != normalizedKey) {
      Log.e(TAG, "La identidad de dispositivo no conserva la clave pública de cuenta")
      return false
    }
    if (!accountPublic.save(normalizedId, normalizedKey)) {
      Log.e(TAG, "No se pudo persistir la identidad pública de la cuenta")
      return false
    }
    vault.clear()
    synchronized(lock) { identity = nextIdentity }
    ensureDevice(nextIdentity)
    Log.i(TAG, "Identidad uPeer en modo dispositivo sin clave raíz")
    return true
  }

  fun isDeviceOnly(): Boolean = synchronized(lock) { identity?.isDeviceOnly() ?: false }

  fun installEnrollment(installed: InstalledEnrollment): Boolean {
    val account = current() ?: run {
      if (!setAccountPublic(installed.accountUpeerId, installed.accountPublicKey)) return false
      current() ?: return false
    }
    if (account.upeerID() != installed.accountUpeerId) {
      Log.e(TAG, "El enrolamiento pertenece a otra cuenta; se descarta")
      return false
    }
    val device = devices.install(installed.accountUpeerId, installed.deviceSecretKey, installed.certificateJSON)
    if (device == null) {
      Log.e(TAG, "No se pudo instalar el certificado del enrolamiento")
      return false
    }
    if (!account.isDeviceOnly()) {
      try {
        registries.ensure(account.upeerID(), device.certificateJSON, CryptoAccountRegistrySigner(account))
      } catch (cause: Exception) {
        Log.e(TAG, "No se pudo publicar el registro de cuenta tras el enrolamiento", cause)
      }
    }
    Log.i(TAG, "Dispositivo enrolado: ${device.deviceId}")
    return true
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
    try {
      accountPublic.clear()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo eliminar la identidad pública de la cuenta", cause)
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
    if (isDeviceOnly()) {
      Log.i(TAG, "Modo dispositivo: el registro de cuenta lo publica la raíz")
      return
    }
    try {
      currentRegistryJSON()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo refrescar el registro de cuenta", cause)
    }
  }

  fun deviceStatusJSON(): String = devices.statusJSON(current()?.upeerID())

  fun currentRegistryJSON(): String {
    val account = current() ?: return ""
    if (account.isDeviceOnly()) return registries.currentRegistryJSON()
    val device = ensureDevice(account) ?: return ""
    return registries.ensure(account.upeerID(), device.certificateJSON, CryptoAccountRegistrySigner(account)).registryJSON
  }

  fun registryStatusJSON(): String {
    val account = current() ?: return JSONObject().put("ready", false).toString()
    val device = ensureDevice(account)
    if (device != null && !account.isDeviceOnly()) {
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
    if (account.isDeviceOnly()) {
      val admitted = registries.currentAdmission(account.upeerID(), device.deviceId)
      if (admitted == false) throw DeviceAdmissionException()
      if (admitted == null) {
        Log.w(TAG, "Modo dispositivo sin registro de cuenta en caché; la admisión la valida el receptor")
      }
      return
    }
    val snapshot = registries.ensure(account.upeerID(), device.certificateJSON, CryptoAccountRegistrySigner(account))
    if (!snapshot.localDeviceAdmitted) throw DeviceAdmissionException()
  }

  fun revokeDevice(deviceId: String, reason: String) {
    val account = current() ?: throw IllegalStateException("uPeer identity is not available")
    if (account.isDeviceOnly()) {
      throw IllegalStateException("Revocar dispositivos requiere la clave raíz de la cuenta")
    }
    val device = ensureDevice(account) ?: throw IllegalStateException("device certificate is not available")
    registries.revoke(account.upeerID(), device.certificateJSON, deviceId, reason, CryptoAccountRegistrySigner(account))
  }

  fun revokedDevices(): Set<String> = registries.revokedDevices()

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
        .put("mode", if (activeIdentity.isDeviceOnly()) "device" else "root")
        .put("publicKey", activeIdentity.publicKeyHex())
        .put("upeerId", activeIdentity.upeerID())
        .put("deviceId", device?.deviceId ?: "")
        .put("deviceCertificateReady", device != null)
        .toString()
    }
  }

  private fun load() {
    if (loadRootIdentity()) return
    loadDeviceOnlyIdentity()
  }

  private fun loadRootIdentity(): Boolean {
    val secretKey = try {
      vault.load()
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo leer la identidad uPeer cifrada", cause)
      return false
    } ?: return false
    val storedIdentity = try {
      CryptoIdentity(secretKey)
    } catch (cause: Exception) {
      Log.e(TAG, "La identidad uPeer almacenada no es válida; se elimina", cause)
      vault.clear()
      return false
    } finally {
      secretKey.fill(0)
    }
    synchronized(lock) { identity = storedIdentity }
    Log.i(TAG, "Identidad uPeer cargada desde Keystore")
    ensureDevice(storedIdentity)
    return true
  }

  private fun loadDeviceOnlyIdentity() {
    val stored = accountPublic.load()
    if (stored == null) {
      Log.i(TAG, "Sin identidad uPeer provisionada en este dispositivo")
      return
    }
    val storedIdentity = try {
      Transportbridge.newDeviceOnlyCryptoIdentity(stored.accountPublicKey)
    } catch (cause: Exception) {
      Log.e(TAG, "La identidad pública de la cuenta almacenada no es válida", cause)
      return
    }
    if (storedIdentity.upeerID() != stored.accountUpeerId) {
      Log.e(TAG, "La identidad pública de la cuenta no deriva el upeerId almacenado")
      return
    }
    synchronized(lock) { identity = storedIdentity }
    Log.i(TAG, "Identidad uPeer cargada en modo dispositivo sin clave raíz")
    ensureDevice(storedIdentity)
  }

  private fun ensureDevice(activeIdentity: CryptoIdentity): DeviceIdentity? {
    val device = try {
      devices.ensure(activeIdentity)
    } catch (cause: Exception) {
      Log.e(TAG, "No se pudo provisionar el certificado de dispositivo; se usará el bloque de cuenta", cause)
      return devices.current()?.takeIf { it.accountUpeerId == activeIdentity.upeerID() }
    } ?: return null
    if (activeIdentity.isDeviceOnly()) return device
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
