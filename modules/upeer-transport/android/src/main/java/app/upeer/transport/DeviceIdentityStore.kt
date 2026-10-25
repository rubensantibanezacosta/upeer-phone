package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.crypto.Blake2b
import app.upeer.transport.dht.DhtLocation
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject
import java.security.SecureRandom

internal interface DeviceIdentityPersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal class DeviceIdentity(
  val crypto: CryptoIdentity,
  val certificateJSON: String,
  val accountUpeerId: String,
  val deviceId: String,
  val certificate: JSONObject,
)

internal class DeviceIdentityStore(
  private val persistence: DeviceIdentityPersistence,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val newDeviceSecretKey: () -> ByteArray = ::generateDeviceSecretKey,
  private val deriveDeviceId: (String) -> String = ::deriveDeviceId,
  private val verifyCertificate: (String, String, Long) -> Boolean = { certificateJSON, accountUpeerId, now ->
    Transportbridge.verifyDeviceCertificate(certificateJSON, accountUpeerId, now)
  },
) {
  private val lock = Any()
  private var identity: DeviceIdentity? = null

  constructor(context: Context) : this(KeystorePersistence(context))

  fun ensure(account: CryptoIdentity): DeviceIdentity? = synchronized(lock) {
    val accountUpeerId = account.upeerID()
    if (account.isDeviceOnly()) return ensureDeviceOnly(accountUpeerId)
    val cached = identity
    if (cached != null && cached.accountUpeerId == accountUpeerId && isUsable(cached, accountUpeerId)) return cached
    identity = null
    val stored = readState()
    if (stored != null && stored.accountUpeerId == accountUpeerId) {
      val restored = restore(account, stored)
      if (restored != null) {
        identity = restored
        return restored
      }
    } else {
      stored?.secretKey?.fill(0)
    }
    val created = provision(account)
    identity = created
    created
  }

  fun install(
    accountUpeerId: String,
    deviceSecretKeyHex: String,
    certificateJSON: String,
  ): DeviceIdentity? = synchronized(lock) {
    val account = accountUpeerId.trim().lowercase()
    if (!DhtLocation.isHex(account, 32)) {
      Log.w(TAG, "El certificado del enrolamiento no identifica una cuenta válida")
      return null
    }
    val secretKey = DhtLocation.decodeHex(deviceSecretKeyHex.trim().lowercase(), 64) ?: run {
      Log.w(TAG, "La clave de dispositivo del enrolamiento no es válida")
      return null
    }
    try {
      val installed = buildIdentity(secretKey, certificateJSON, account, "el enrolamiento") ?: return null
      saveState(secretKey, installed)
      identity = installed
      Log.i(TAG, "Certificado de dispositivo instalado desde el enrolamiento: ${installed.deviceId}")
      installed
    } finally {
      secretKey.fill(0)
    }
  }

  private fun ensureDeviceOnly(accountUpeerId: String): DeviceIdentity? {
    val cached = identity
    if (cached != null && cached.accountUpeerId == accountUpeerId && isInstalled(cached, accountUpeerId)) return cached
    identity = null
    val stored = readState()
    if (stored == null || stored.accountUpeerId != accountUpeerId) {
      stored?.secretKey?.fill(0)
      Log.w(TAG, "Modo dispositivo sin certificado autorizado; hace falta completar el enrolamiento")
      return null
    }
    val restored = try {
      buildIdentity(stored.secretKey, stored.certificateJSON, accountUpeerId, "el almacenamiento")
    } finally {
      stored.secretKey.fill(0)
    }
    if (restored == null) {
      Log.w(TAG, "El certificado de dispositivo no se puede usar sin la raíz")
      return null
    }
    identity = restored
    return restored
  }

  private fun buildIdentity(
    secretKey: ByteArray,
    certificateJSON: String,
    accountUpeerId: String,
    origin: String,
  ): DeviceIdentity? {
    val deviceCrypto = try {
      CryptoIdentity(secretKey)
    } catch (cause: Exception) {
      Log.w(TAG, "La clave de dispositivo de $origin no construye una identidad", cause)
      return null
    }
    val derivedId = deriveDeviceId(deviceCrypto.publicKeyHex())
    val check = InstalledCertificate.check(certificateJSON, accountUpeerId, derivedId, nowMillis())
    val certificate = check.certificate
    if (certificate == null) {
      Log.w(TAG, "El certificado de dispositivo de $origin se rechaza: ${check.failure}")
      return null
    }
    val verified = runCatching { verifyCertificate(certificateJSON, accountUpeerId, nowMillis()) }.getOrElse { cause ->
      Log.w(TAG, "No se pudo verificar el certificado de dispositivo de $origin", cause)
      false
    }
    if (!verified) {
      Log.w(TAG, "El certificado de dispositivo de $origin no verifica contra la cuenta")
      return null
    }
    return DeviceIdentity(deviceCrypto, certificateJSON, accountUpeerId, derivedId, certificate)
  }

  private fun isInstalled(identity: DeviceIdentity, accountUpeerId: String): Boolean {
    if (identity.certificate.optLong("expiresAt") <= nowMillis()) return false
    if (!identity.certificate.optString("accountUpeerId").equals(accountUpeerId, ignoreCase = true)) return false
    if (!identity.certificate.optString("deviceId").equals(identity.deviceId, ignoreCase = true)) return false
    return runCatching { verifyCertificate(identity.certificateJSON, accountUpeerId, nowMillis()) }.getOrElse { cause ->
      Log.w(TAG, "No se pudo verificar el certificado de dispositivo instalado", cause)
      false
    }
  }

  fun current(): DeviceIdentity? = synchronized(lock) { identity }

  fun statusJSON(accountUpeerId: String?): String = synchronized(lock) {
    val active = identity
    if (accountUpeerId == null || active == null || active.accountUpeerId != accountUpeerId.lowercase()) {
      return JSONObject().put("ready", false).toString()
    }
    JSONObject()
      .put("ready", true)
      .put("accountUpeerId", active.accountUpeerId)
      .put("deviceId", active.deviceId)
      .put("certificateEpoch", active.certificate.optLong("epoch"))
      .put("certificateExpiresAt", active.certificate.optLong("expiresAt"))
      .put("certificate", active.certificate)
      .toString()
  }

  fun clear() = synchronized(lock) {
    identity = null
    persistence.clear()
  }

  private fun provision(account: CryptoIdentity): DeviceIdentity {
    val secretKey = newDeviceSecretKey()
    try {
      val deviceCrypto = CryptoIdentity(secretKey)
      val certificate = issueCertificate(account, deviceCrypto, 1L)
      saveState(secretKey, certificate)
      Log.i(TAG, "Certificado de dispositivo emitido: ${certificate.deviceId}")
      return certificate
    } finally {
      secretKey.fill(0)
    }
  }

  private fun restore(account: CryptoIdentity, stored: StoredDeviceIdentity): DeviceIdentity? {
    val secretKey = stored.secretKey
    try {
      val deviceCrypto = try {
        CryptoIdentity(secretKey)
      } catch (cause: Exception) {
        Log.w(TAG, "La clave de dispositivo almacenada no es válida; se reemplaza", cause)
        return null
      }
      val derivedId = deriveDeviceId(deviceCrypto.publicKeyHex())
      if (!derivedId.equals(stored.deviceId, ignoreCase = true)) {
        Log.w(TAG, "El deviceId almacenado no corresponde con la clave de dispositivo; se reemplaza")
        return null
      }
      val certificate = runCatching { JSONObject(stored.certificateJSON) }.getOrElse { cause ->
        Log.w(TAG, "El certificado de dispositivo almacenado no es JSON válido; se reemite", cause)
        return issueAndPersist(account, deviceCrypto, 1L, secretKey)
      }
      val epoch = certificate.optLong("epoch").coerceAtLeast(1L)
      val matching = certificate.optString("accountUpeerId").equals(stored.accountUpeerId, ignoreCase = true) &&
        certificate.optString("deviceId").equals(derivedId, ignoreCase = true)
      val verified = matching && runCatching {
        verifyCertificate(stored.certificateJSON, stored.accountUpeerId, nowMillis())
      }.getOrElse { cause ->
        Log.w(TAG, "No se pudo verificar el certificado de dispositivo almacenado; se reemite", cause)
        false
      }
      if (verified && !needsRenewal(certificate)) {
        return DeviceIdentity(deviceCrypto, stored.certificateJSON, stored.accountUpeerId, derivedId, certificate)
      }
      return issueAndPersist(account, deviceCrypto, epoch, secretKey)
    } finally {
      secretKey.fill(0)
    }
  }

  private fun issueAndPersist(account: CryptoIdentity, deviceCrypto: CryptoIdentity, epoch: Long, secretKey: ByteArray): DeviceIdentity {
    val certificate = issueCertificate(account, deviceCrypto, epoch)
    saveState(secretKey, certificate)
    return certificate
  }

  private fun issueCertificate(account: CryptoIdentity, deviceCrypto: CryptoIdentity, epoch: Long): DeviceIdentity {
    val accountUpeerId = account.upeerID()
    val now = nowMillis()
    require(now <= Long.MAX_VALUE - CERTIFICATE_TTL_MS) { "certificate expiresAt overflows" }
    val options = JSONObject()
      .put("accountUpeerId", accountUpeerId)
      .put("platform", PLATFORM)
      .put("capabilities", org.json.JSONArray(listOf("chat", "dht")))
      .put("createdAt", now)
    val requestJSON = deviceCrypto.buildDevicePairingRequest(options.toString())
    val certificateJSON = account.approveDevicePairingRequest(requestJSON, epoch.coerceAtLeast(1L), now, now + CERTIFICATE_TTL_MS)
    if (!verifyCertificate(certificateJSON, accountUpeerId, now)) {
      throw IllegalStateException("El certificado de dispositivo recién emitido no verifica")
    }
    val certificate = JSONObject(certificateJSON)
    val derivedId = deriveDeviceId(deviceCrypto.publicKeyHex())
    if (!certificate.optString("deviceId").equals(derivedId, ignoreCase = true)) {
      throw IllegalStateException("El certificado de dispositivo no corresponde con la clave del dispositivo")
    }
    return DeviceIdentity(deviceCrypto, certificateJSON, accountUpeerId, derivedId, certificate)
  }

  private fun saveState(secretKey: ByteArray, identity: DeviceIdentity) {
    val state = JSONObject()
      .put("version", STATE_VERSION)
      .put("accountUpeerId", identity.accountUpeerId)
      .put("deviceId", identity.deviceId)
      .put("deviceSecretKey", DhtLocation.hex(secretKey))
      .put("certificate", identity.certificate)
    val encoded = state.toString().toByteArray(Charsets.UTF_8)
    try {
      persistence.save(encoded)
    } catch (cause: Exception) {
      throw IllegalStateException("No se pudo persistir la identidad de dispositivo", cause)
    } finally {
      encoded.fill(0)
    }
  }

  private fun readState(): StoredDeviceIdentity? {
    val raw = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo leer la identidad de dispositivo cifrada", cause)
      return null
    } ?: return null
    try {
      val state = JSONObject(String(raw, Charsets.UTF_8))
      if (state.optInt("version") != STATE_VERSION) return null
      val accountUpeerId = state.optString("accountUpeerId").lowercase()
      val deviceId = state.optString("deviceId").lowercase()
      if (!DhtLocation.isHex(accountUpeerId, 32) || !DhtLocation.isHex(deviceId, 32)) return null
      val secretKey = DhtLocation.decodeHex(state.optString("deviceSecretKey"), 64) ?: return null
      val certificate = state.optJSONObject("certificate") ?: return null
      return StoredDeviceIdentity(accountUpeerId, deviceId, secretKey, certificate.toString())
    } catch (cause: Exception) {
      Log.w(TAG, "La identidad de dispositivo almacenada no es válida; se reemplaza", cause)
      return null
    } finally {
      raw.fill(0)
    }
  }

  private fun isUsable(identity: DeviceIdentity, accountUpeerId: String): Boolean {
    if (needsRenewal(identity.certificate)) return false
    if (!identity.certificate.optString("accountUpeerId").equals(accountUpeerId, ignoreCase = true)) return false
    if (!identity.certificate.optString("deviceId").equals(identity.deviceId, ignoreCase = true)) return false
    return runCatching { verifyCertificate(identity.certificateJSON, accountUpeerId, nowMillis()) }.getOrElse { cause ->
      Log.w(TAG, "No se pudo verificar el certificado de dispositivo en memoria", cause)
      false
    }
  }

  private fun needsRenewal(certificate: JSONObject): Boolean {
    val expiresAt = certificate.optLong("expiresAt")
    return expiresAt <= 0L || expiresAt - nowMillis() <= RENEW_BEFORE_MS
  }

  private data class StoredDeviceIdentity(
    val accountUpeerId: String,
    val deviceId: String,
    val secretKey: ByteArray,
    val certificateJSON: String,
  )

  private class KeystorePersistence(context: Context) : DeviceIdentityPersistence {
    private val store = KeystoreStore(context, "upeer_device_identity", "upeer-device-identity", "device_identity_state")

    override fun load(): ByteArray? = store.load()

    override fun save(value: ByteArray) = store.save(value)

    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val STATE_VERSION = 1
    private const val PLATFORM = "android"
    private const val CERTIFICATE_TTL_MS = 90L * 24L * 60L * 60L * 1000L
    private const val RENEW_BEFORE_MS = 7L * 24L * 60L * 60L * 1000L
  }
}

private fun generateDeviceSecretKey(): ByteArray {
  val seed = ByteArray(32).also(SecureRandom()::nextBytes)
  try {
    val derived = JSONObject(Transportbridge.newBridge().deriveIdentity(seed))
    return DhtLocation.decodeHex(derived.optString("secretKey"), 64)
      ?: throw IllegalStateException("El puente no devolvió una clave de dispositivo válida")
  } finally {
    seed.fill(0)
  }
}

private fun deriveDeviceId(publicKeyHex: String): String {
  val publicKey = DhtLocation.decodeHex(publicKeyHex, 32)
    ?: throw IllegalArgumentException("La clave pública del dispositivo debe ser hexadecimal de 32 bytes")
  return DhtLocation.hex(Blake2b.hash256(publicKey))
}
