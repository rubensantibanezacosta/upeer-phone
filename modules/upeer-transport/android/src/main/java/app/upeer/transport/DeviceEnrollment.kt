package app.upeer.transport

import android.content.Context
import android.util.Log
import app.upeer.transport.crypto.Blake2b
import app.upeer.transport.dht.DhtLocation
import app.upeer.transportbridge.transportbridge.CryptoIdentity
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject
import java.security.SecureRandom

internal interface EnrollmentPersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal class DeviceEnrollment(
  private val persistence: EnrollmentPersistence,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val newDeviceSecretKey: () -> ByteArray = ::newEnrollmentDeviceSecretKey,
  private val devicePublicKeyHex: (ByteArray) -> String = { CryptoIdentity(it).publicKeyHex() },
  private val deriveDeviceId: (String) -> String = ::deviceIdFromPublicKey,
  private val parseInvite: (String, Long) -> String = { code, now -> Transportbridge.parseEnrollmentInvite(code, now) },
  private val buildRequest: (ByteArray, String, String, Long) -> String = { secretKey, inviteJSON, platform, now ->
    CryptoIdentity(secretKey).buildEnrollmentRequest(inviteJSON, platform, now)
  },
  private val openCertificate: (String, String, String, Long) -> String = { inviteJSON, sealed, account, now ->
    Transportbridge.openEnrollmentCertificate(inviteJSON, sealed, account, now)
  },
) {
  constructor(context: Context) : this(EnrollmentKeystorePersistence(context))

  private val lock = Any()
  private var state: State = readState()

  fun begin(code: String): String = synchronized(lock) {
    val now = nowMillis()
    val inviteJSON = try {
      parseInvite(code, now)
    } catch (cause: Exception) {
      throw IllegalArgumentException("La invitación no es válida o ha caducado.", cause)
    }
    val offer = JSONObject(inviteJSON).optJSONObject("offer")
      ?: throw IllegalArgumentException("La invitación no incluye la cuenta que autoriza el dispositivo.")
    val accountUpeerId = offer.optString("accountUpeerId").lowercase()
    if (!DhtLocation.isHex(accountUpeerId, 32)) {
      throw IllegalArgumentException("La invitación no identifica una cuenta válida.")
    }
    val secretKey = newDeviceSecretKey()
    try {
      val requestJSON = try {
        buildRequest(secretKey, inviteJSON, PLATFORM, now)
      } catch (cause: Exception) {
        throw IllegalStateException("No se pudo firmar la solicitud de emparejamiento.", cause)
      }
      state = state.copy(
        pending = Pending(accountUpeerId, inviteJSON, DhtLocation.hex(secretKey), requestJSON),
      )
      persist()
      JSONObject().put("offer", offer).put("request", requestJSON).toString()
    } finally {
      secretKey.fill(0)
    }
  }

  fun requestJSON(): String = synchronized(lock) { state.pending?.requestJSON.orEmpty() }

  fun finish(sealedResponse: String): String = synchronized(lock) {
    val pending = state.pending ?: throw IllegalStateException("No hay un emparejamiento en curso.")
    val secretKey = DhtLocation.decodeHex(pending.deviceSecretKey, 64)
      ?: throw IllegalStateException("La clave del dispositivo en curso no se puede leer.")
    try {
      val certificateJSON = try {
        openCertificate(pending.inviteJSON, sealedResponse, pending.accountUpeerId, nowMillis())
      } catch (cause: Exception) {
        throw IllegalArgumentException("La respuesta de la cuenta no corresponde a esta invitación.", cause)
      }
      val certificate = JSONObject(certificateJSON)
      val accountUpeerId = certificate.optString("accountUpeerId").lowercase()
      if (accountUpeerId != pending.accountUpeerId) {
        throw IllegalArgumentException("El certificado pertenece a otra cuenta.")
      }
      val deviceId = deriveDeviceId(devicePublicKeyHex(secretKey))
      if (!certificate.optString("deviceId").equals(deviceId, ignoreCase = true)) {
        throw IllegalArgumentException("El certificado no corresponde a la clave de este dispositivo.")
      }
      state = state.copy(
        pending = null,
        installed = Installed(
          accountUpeerId = accountUpeerId,
          accountPublicKey = certificate.optString("accountPublicKey").lowercase(),
          deviceId = deviceId,
          deviceSecretKey = DhtLocation.hex(secretKey),
          certificateJSON = certificate.toString(),
        ),
      )
      persist()
      certificateSummary(accountUpeerId, deviceId, certificate).toString()
    } finally {
      secretKey.fill(0)
    }
  }

  fun cancel() = synchronized(lock) {
    if (state.pending == null) return@synchronized
    state = state.copy(pending = null)
    persist()
  }

  fun statusJSON(): String = synchronized(lock) {
    val status = JSONObject()
    state.pending?.let { pending ->
      val offer = runCatching { JSONObject(pending.inviteJSON).optJSONObject("offer") }.getOrNull()
      status.put(
        "pending",
        JSONObject()
          .put("accountUpeerId", pending.accountUpeerId)
          .put("authorizerDeviceId", offer?.optString("authorizerDeviceId").orEmpty())
          .put("expiresAt", offer?.optLong("expiresAt") ?: 0L),
      )
    }
    state.installed?.let { installed ->
      val certificate = runCatching { JSONObject(installed.certificateJSON) }.getOrNull()
      val summary = if (certificate == null) {
        JSONObject().put("accountUpeerId", installed.accountUpeerId).put("deviceId", installed.deviceId)
      } else {
        certificateSummary(installed.accountUpeerId, installed.deviceId, certificate)
      }
      status.put("installed", summary)
    }
    status.toString()
  }

  private fun certificateSummary(accountUpeerId: String, deviceId: String, certificate: JSONObject): JSONObject = JSONObject()
    .put("accountUpeerId", accountUpeerId)
    .put("accountPublicKey", certificate.optString("accountPublicKey").lowercase())
    .put("deviceId", deviceId)
    .put("devicePublicKey", certificate.optString("devicePublicKey").lowercase())
    .put("epoch", certificate.optLong("epoch"))
    .put("issuedAt", certificate.optLong("issuedAt"))
    .put("expiresAt", certificate.optLong("expiresAt"))
    .put("certificate", certificate)

  private fun persist() {
    val encoded = state.toJSON().toString().toByteArray(Charsets.UTF_8)
    try {
      persistence.save(encoded)
    } catch (cause: Exception) {
      throw IllegalStateException("No se pudo guardar el estado de emparejamiento.", cause)
    } finally {
      encoded.fill(0)
    }
  }

  private fun readState(): State {
    val raw = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo leer el estado de emparejamiento cifrado", cause)
      return State()
    } ?: return State()
    return try {
      val root = JSONObject(String(raw, Charsets.UTF_8))
      if (root.optInt("version") != STATE_VERSION) return State()
      State(pending = root.optJSONObject("pending")?.let(Pending::fromJSON), installed = root.optJSONObject("installed")?.let(Installed::fromJSON))
    } catch (cause: Exception) {
      Log.w(TAG, "El estado de emparejamiento almacenado no es válido; se descarta", cause)
      State()
    } finally {
      raw.fill(0)
    }
  }

  private data class State(val pending: Pending? = null, val installed: Installed? = null) {
    fun toJSON(): JSONObject = JSONObject()
      .put("version", STATE_VERSION)
      .also { root -> pending?.let { root.put("pending", it.toJSON()) } }
      .also { root -> installed?.let { root.put("installed", it.toJSON()) } }
  }

  private data class Pending(
    val accountUpeerId: String,
    val inviteJSON: String,
    val deviceSecretKey: String,
    val requestJSON: String,
  ) {
    fun toJSON(): JSONObject = JSONObject()
      .put("accountUpeerId", accountUpeerId)
      .put("invite", inviteJSON)
      .put("deviceSecretKey", deviceSecretKey)
      .put("request", requestJSON)

    companion object {
      fun fromJSON(value: JSONObject): Pending? {
        val accountUpeerId = value.optString("accountUpeerId").lowercase()
        val invite = value.optString("invite")
        val secretKey = value.optString("deviceSecretKey")
        val request = value.optString("request")
        if (!DhtLocation.isHex(accountUpeerId, 32) || invite.isEmpty() || request.isEmpty()) return null
        if (DhtLocation.decodeHex(secretKey, 64) == null) return null
        return Pending(accountUpeerId, invite, secretKey, request)
      }
    }
  }

  private data class Installed(
    val accountUpeerId: String,
    val accountPublicKey: String,
    val deviceId: String,
    val deviceSecretKey: String,
    val certificateJSON: String,
  ) {
    fun toJSON(): JSONObject = JSONObject()
      .put("accountUpeerId", accountUpeerId)
      .put("accountPublicKey", accountPublicKey)
      .put("deviceId", deviceId)
      .put("deviceSecretKey", deviceSecretKey)
      .put("certificate", certificateJSON)

    companion object {
      fun fromJSON(value: JSONObject): Installed? {
        val accountUpeerId = value.optString("accountUpeerId").lowercase()
        val accountPublicKey = value.optString("accountPublicKey").lowercase()
        val deviceId = value.optString("deviceId").lowercase()
        val secretKey = value.optString("deviceSecretKey")
        val certificate = value.optString("certificate")
        if (!DhtLocation.isHex(accountUpeerId, 32) || !DhtLocation.isHex(deviceId, 32)) return null
        if (!DhtLocation.isHex(accountPublicKey, 32) || certificate.isEmpty()) return null
        if (DhtLocation.decodeHex(secretKey, 64) == null) return null
        return Installed(accountUpeerId, accountPublicKey, deviceId, secretKey, certificate)
      }
    }
  }

  private class EnrollmentKeystorePersistence(context: Context) : EnrollmentPersistence {
    private val store = KeystoreStore(context, "upeer_device_enrollment", "upeer-device-enrollment", "enrollment_state")

    override fun load(): ByteArray? = store.load()

    override fun save(value: ByteArray) = store.save(value)

    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val STATE_VERSION = 1
    private const val PLATFORM = "android"
  }
}

private fun newEnrollmentDeviceSecretKey(): ByteArray {
  val seed = ByteArray(32).also(SecureRandom()::nextBytes)
  try {
    val derived = JSONObject(Transportbridge.newBridge().deriveIdentity(seed))
    return DhtLocation.decodeHex(derived.optString("secretKey"), 64)
      ?: throw IllegalStateException("El puente no devolvió una clave de dispositivo válida")
  } finally {
    seed.fill(0)
  }
}

private fun deviceIdFromPublicKey(publicKeyHex: String): String {
  val publicKey = DhtLocation.decodeHex(publicKeyHex, 32)
    ?: throw IllegalArgumentException("La clave pública del dispositivo debe ser hexadecimal de 32 bytes")
  return DhtLocation.hex(Blake2b.hash256(publicKey))
}
