package app.upeer.transport

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal interface AccountRegistryPersistence {
  fun load(): ByteArray?
  fun save(value: ByteArray)
  fun clear()
}

internal data class AccountRegistrySnapshot(
  val accountUpeerId: String,
  val registryJSON: String,
  val epoch: Long,
  val issuedAt: Long,
  val expiresAt: Long,
  val certificates: List<String>,
  val revoked: List<String>,
  val deviceCount: Int,
  val localDeviceId: String,
  val localDeviceAdmitted: Boolean,
)

internal class AccountRegistryStore(
  private val persistence: AccountRegistryPersistence,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val ttlMillis: Long = REGISTRY_TTL_MS,
  private val renewBeforeMillis: Long = REGISTRY_RENEW_BEFORE_MS,
) {
  private val lock = Any()
  private var cached: AccountRegistrySnapshot? = null

  constructor(context: Context) : this(KeystorePersistence(context))

  fun ensure(accountUpeerId: String, certificateJSON: String, signer: AccountRegistrySigner): AccountRegistrySnapshot = synchronized(lock) {
    val account = normalizeAccountUpeerId(accountUpeerId)
    val certificate = deviceCertificateInfo(certificateJSON)
      ?: throw IllegalArgumentException("device certificate is invalid")
    require(certificate.accountUpeerId == account) { "device certificate does not belong to the account" }
    val now = nowMillis()
    val previous = cached?.takeIf { it.accountUpeerId == account }
      ?: readState()?.takeIf { it.accountUpeerId == account }
    cached = null
    if (previous == null) {
      return rebuild(signer, account, listOf(certificateJSON), "[]", maxOf(1L, certificate.epoch), now, certificate.deviceId)
    }
    val certificates = mergeDeviceCertificate(previous.certificates, certificateJSON, certificate.deviceId)
    if (admits(signer, certificateJSON, account, previous.registryJSON, now)) {
      val expiresSoon = previous.expiresAt <= 0L || previous.expiresAt - now <= renewBeforeMillis
      if (!expiresSoon) {
        val snapshot = previous.copy(
          certificates = certificates,
          localDeviceId = certificate.deviceId,
          localDeviceAdmitted = true,
        )
        persistIfChanged(previous, snapshot)
        return snapshot
      }
    }
    val revoked = revocationDeviceIds(previous.registryJSON)
    if (certificate.deviceId in revoked) {
      val snapshot = previous.copy(
        certificates = certificates,
        localDeviceId = certificate.deviceId,
        localDeviceAdmitted = false,
      )
      persistIfChanged(previous, snapshot)
      return snapshot
    }
    rebuild(
      signer,
      account,
      certificates,
      revocationJSON(previous.registryJSON),
      maxOf(previous.epoch + 1, certificate.epoch),
      now,
      certificate.deviceId,
    )
  }

  fun revoke(
    accountUpeerId: String,
    certificateJSON: String,
    deviceId: String,
    reason: String,
    signer: AccountRegistrySigner,
  ): AccountRegistrySnapshot = synchronized(lock) {
    val current = ensure(accountUpeerId, certificateJSON, signer)
    val normalizedDeviceId = deviceId.trim().lowercase()
    val now = nowMillis()
    val registryJSON = signer.revoke(current.registryJSON, normalizedDeviceId, reason, now)
    val snapshot = snapshotFrom(registryJSON, current.certificates, current.localDeviceId, now, signer)
    persist(snapshot)
    snapshot
  }

  fun currentRegistryJSON(): String = synchronized(lock) { cached?.registryJSON.orEmpty() }

  fun addDevice(
    accountUpeerId: String,
    localCertificateJSON: String,
    deviceCertificateJSON: String,
    signer: AccountRegistrySigner,
  ): AccountRegistrySnapshot = synchronized(lock) {
    val current = ensure(accountUpeerId, localCertificateJSON, signer)
    val certificate = deviceCertificateInfo(deviceCertificateJSON)
      ?: throw IllegalArgumentException("device certificate is invalid")
    require(certificate.accountUpeerId == current.accountUpeerId) {
      "device certificate does not belong to the account"
    }
    if (certificate.deviceId in current.revoked) {
      throw IllegalArgumentException("device certificate has been revoked")
    }
    val certificates = mergeDeviceCertificate(current.certificates, deviceCertificateJSON, certificate.deviceId)
    if (certificates == current.certificates) return@synchronized current
    rebuild(
      signer,
      current.accountUpeerId,
      certificates,
      revocationJSON(current.registryJSON),
      maxOf(current.epoch + 1, certificate.epoch),
      nowMillis(),
      current.localDeviceId,
    )
  }

  fun currentAdmission(accountUpeerId: String, deviceId: String): Boolean? = synchronized(lock) {
    val current = cached ?: return@synchronized null
    if (current.accountUpeerId != accountUpeerId.trim().lowercase()) return@synchronized null
    if (current.localDeviceId != deviceId.trim().lowercase()) return@synchronized null
    current.localDeviceAdmitted
  }

  fun statusJSON(accountUpeerId: String?, deviceId: String?): String = synchronized(lock) {
    val current = cached
    if (accountUpeerId == null || current == null || current.accountUpeerId != accountUpeerId.lowercase()) {
      return JSONObject().put("ready", false).toString()
    }
    val sameDevice = deviceId == null || current.localDeviceId == deviceId.lowercase()
    JSONObject()
      .put("ready", true)
      .put("accountUpeerId", current.accountUpeerId)
      .put("epoch", current.epoch)
      .put("issuedAt", current.issuedAt)
      .put("expiresAt", current.expiresAt)
      .put("devices", current.deviceCount)
      .put("revoked", current.revoked.size)
      .put("localDeviceId", current.localDeviceId)
      .put("localDeviceAdmitted", current.localDeviceAdmitted && sameDevice)
      .toString()
  }

  fun clear() = synchronized(lock) {
    cached = null
    persistence.clear()
  }

  private fun rebuild(
    signer: AccountRegistrySigner,
    accountUpeerId: String,
    certificates: List<String>,
    revokedJSON: String,
    epoch: Long,
    nowMillis: Long,
    localDeviceId: String,
  ): AccountRegistrySnapshot {
    val revoked = revocationIds(revokedJSON)
    val usable = usableRegistryCertificates(certificates, revoked, accountUpeerId, nowMillis)
    val registryJSON = signer.build(JSONArray(usable).toString(), revokedJSON, epoch, nowMillis, ttlMillis)
    val snapshot = snapshotFrom(registryJSON, usable, localDeviceId, nowMillis, signer)
    persist(snapshot)
    return snapshot
  }

  private fun snapshotFrom(
    registryJSON: String,
    certificates: List<String>,
    localDeviceId: String,
    nowMillis: Long,
    signer: AccountRegistrySigner,
  ): AccountRegistrySnapshot {
    val registry = JSONObject(registryJSON)
    val account = normalizeAccountUpeerId(registry.optString("accountUpeerId"))
    val revoked = revocationDeviceIds(registry)
    val usable = usableRegistryCertificates(certificates, revoked, account, nowMillis)
    val localCertificate = usable.firstOrNull { deviceCertificateInfo(it)?.deviceId == localDeviceId }
    val admitted = localCertificate != null && admits(signer, localCertificate, account, registryJSON, nowMillis)
    return AccountRegistrySnapshot(
      accountUpeerId = account,
      registryJSON = registryJSON,
      epoch = registry.optLong("epoch"),
      issuedAt = registry.optLong("issuedAt"),
      expiresAt = registry.optLong("expiresAt"),
      certificates = usable,
      revoked = revoked.toList().sorted(),
      deviceCount = registry.optJSONArray("devices")?.length() ?: 0,
      localDeviceId = localDeviceId,
      localDeviceAdmitted = admitted,
    )
  }

  private fun persistIfChanged(previous: AccountRegistrySnapshot, next: AccountRegistrySnapshot) {
    if (
      previous.certificates != next.certificates ||
      previous.localDeviceAdmitted != next.localDeviceAdmitted ||
      previous.localDeviceId != next.localDeviceId
    ) {
      persist(next)
    } else {
      cached = next
    }
  }

  private fun persist(snapshot: AccountRegistrySnapshot) {
    val state = JSONObject()
      .put("version", STATE_VERSION)
      .put("accountUpeerId", snapshot.accountUpeerId)
      .put("certificates", JSONArray(snapshot.certificates))
      .put("registry", JSONObject(snapshot.registryJSON))
    val encoded = state.toString().toByteArray(Charsets.UTF_8)
    try {
      persistence.save(encoded)
      cached = snapshot
    } finally {
      encoded.fill(0)
    }
  }


  private fun readState(): AccountRegistrySnapshot? {
    val raw = try {
      persistence.load()
    } catch (cause: Exception) {
      Log.w(TAG, "No se pudo leer el registro de cuenta cifrado", cause)
      return null
    } ?: return null
    try {
      val state = JSONObject(String(raw, Charsets.UTF_8))
      if (state.optInt("version") != STATE_VERSION) return null
      val account = runCatching { normalizeAccountUpeerId(state.optString("accountUpeerId")) }.getOrNull() ?: return null
      val certificates = mutableListOf<String>()
      val encodedCertificates = state.optJSONArray("certificates") ?: JSONArray()
      for (index in 0 until encodedCertificates.length()) {
        val value = encodedCertificates.optString(index)
        if (deviceCertificateInfo(value) != null) certificates += value
      }
      val registryJSON = when (val value = state.opt("registry")) {
        is JSONObject -> value.toString()
        is String -> value
        else -> return null
      }
      if (JSONObject(registryJSON).optString("accountUpeerId").lowercase() != account) return null
      return snapshotFrom(registryJSON, certificates, "", nowMillis(), UNVERIFIED_SIGNER)
    } catch (cause: Exception) {
      Log.w(TAG, "El registro de cuenta almacenado no es válido; se reconstruye", cause)
      return null
    } finally {
      raw.fill(0)
    }
  }

  private fun admits(
    signer: AccountRegistrySigner,
    certificateJSON: String,
    accountUpeerId: String,
    registryJSON: String,
    nowMillis: Long,
  ): Boolean = runCatching {
    signer.validateAdmission(certificateJSON, registryJSON, accountUpeerId, nowMillis)
  }.getOrElse { cause ->
    Log.w(TAG, "No se pudo validar la admisión del dispositivo", cause)
    false
  }

  private class KeystorePersistence(context: Context) : AccountRegistryPersistence {
    private val store = KeystoreStore(context, "upeer_account_registry", "upeer-account-registry", "account_registry_state")

    override fun load(): ByteArray? = store.load()

    override fun save(value: ByteArray) = store.save(value)

    override fun clear() = store.clear()
  }

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val STATE_VERSION = 1
    private const val REGISTRY_TTL_MS = 24L * 60L * 60L * 1000L
    private const val REGISTRY_RENEW_BEFORE_MS = 60L * 60L * 1000L

    private val UNVERIFIED_SIGNER = object : AccountRegistrySigner {
      override val accountUpeerId: String = ""

      override fun build(certificatesJSON: String, revokedJSON: String, epoch: Long, issuedAt: Long, ttlMillis: Long): String =
        throw UnsupportedOperationException("Unverified account registry cannot build registries")

      override fun revoke(previousRegistryJSON: String, deviceId: String, reason: String, nowMillis: Long): String =
        throw UnsupportedOperationException("Unverified account registry cannot revoke devices")

      override fun validateAdmission(
        certificateJSON: String,
        registryJSON: String,
        expectedAccountUpeerId: String,
        nowMillis: Long,
      ): Boolean = false
    }
  }
}

