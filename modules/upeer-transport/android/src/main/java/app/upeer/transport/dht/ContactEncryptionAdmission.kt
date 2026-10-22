package app.upeer.transport.dht

import android.util.Log
import app.upeer.transport.ContactRegistrySnapshot
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONObject

internal enum class LegacyContactPolicy {
  REJECT,
  ALLOW_WITH_AUDIT,
}

internal class ContactAdmissionException(message: String) : IllegalStateException(message)

internal data class ExpectedContactDevice(val deviceId: String, val devicePublicKey: String)

internal class ContactEncryptionAdmission(
  private val publicKeyFor: (String) -> String?,
  private val locationFor: (String) -> String?,
  private val registryFor: (String) -> ContactRegistrySnapshot?,
  private val refresh: (String) -> Unit,
  private val legacyPolicy: LegacyContactPolicy = LegacyContactPolicy.REJECT,
  private val nowMillis: () -> Long = System::currentTimeMillis,
  private val verify: (String, String, String, Long) -> Boolean = { block, publicKey, registry, now ->
    Transportbridge.verifyDeviceLocationBlock(block, publicKey, registry, now)
  },
  private val warn: (String) -> Unit = { message -> Log.w(TAG, message) },
) {
  private data class Decision(
    val epoch: Long,
    val expiresAt: Long,
    val deviceId: String,
    val devicePublicKey: String,
  )

  private val lock = Any()
  private val decisions = LinkedHashMap<String, Decision>(16, 0.75f, true)
  private val lastRefresh = HashMap<String, Long>()
  private val auditedLegacy = HashSet<String>()

  fun requireAdmitted(upeerId: String, expectedDevice: ExpectedContactDevice? = null) {
    val account = upeerId.trim().lowercase()
    if (!DhtLocation.isHex(account, ACCOUNT_BYTES)) {
      throw ContactAdmissionException("contact id is invalid")
    }
    val expectedDeviceId = expectedDevice?.deviceId?.lowercase().orEmpty()
    val expectedDevicePublicKey = expectedDevice?.devicePublicKey?.lowercase().orEmpty()
    val now = nowMillis()
    val registry = registryFor(account)
    val epoch = registry?.epoch ?: UNKNOWN_EPOCH
    val decision = synchronized(lock) { decisions[account] }
    if (decision != null &&
      decision.epoch == epoch &&
      decision.deviceId == expectedDeviceId &&
      decision.devicePublicKey == expectedDevicePublicKey &&
      now < decision.expiresAt
    ) return
    if (registry == null) return allowLegacy(account, null, now, "account registry is unavailable", expectedDeviceId, expectedDevicePublicKey)
    if (registry.expiresAt <= now) deny(account, now, "account registry is expired")
    val publicKey = publicKeyFor(account)
    if (publicKey.isNullOrBlank()) deny(account, now, "contact public key is unavailable")
    val location = locationFor(account)
    if (location.isNullOrBlank()) {
      return allowLegacy(account, registry, now, "contact has no verified location", expectedDeviceId, expectedDevicePublicKey)
    }
    val block = runCatching { JSONObject(location) }.getOrNull()
      ?: deny(account, now, "contact location is invalid")
    if (!block.has("deviceCertificate")) {
      return allowLegacy(account, registry, now, "contact location has no device certificate", expectedDeviceId, expectedDevicePublicKey)
    }
    val verified = DhtContactLocation.verifyOne(location, account, publicKey, registry.registryJSON, now, verify)
      ?: deny(account, now, "contact device is not admitted")
    val certificate = runCatching { JSONObject(verified.certificateJSON) }.getOrNull()
      ?: deny(account, now, "contact device certificate is invalid")
    if (expectedDeviceId.isNotEmpty() && !certificate.optString("deviceId").equals(expectedDeviceId, ignoreCase = true)) {
      deny(account, now, "contact device does not match the advertised messaging device")
    }
    if (expectedDevicePublicKey.isNotEmpty() &&
      !certificate.optString("devicePublicKey").equals(expectedDevicePublicKey, ignoreCase = true)
    ) {
      deny(account, now, "contact device key does not match the advertised messaging key")
    }
    if (verified.deviceId.lowercase() in registry.revoked) deny(account, now, "contact device is revoked")
    val confirmed = registryFor(account)
    if (confirmed == null || confirmed.epoch != registry.epoch) deny(account, now, "account registry changed during admission")
    if (verified.deviceId.lowercase() in confirmed.revoked) deny(account, now, "contact device is revoked")
    remember(
      account,
      Decision(confirmed.epoch, decisionExpiry(confirmed.expiresAt, now), expectedDeviceId, expectedDevicePublicKey),
    )
  }

  fun clear() = synchronized(lock) {
    decisions.clear()
    lastRefresh.clear()
    auditedLegacy.clear()
  }

  private fun allowLegacy(
    account: String,
    registry: ContactRegistrySnapshot?,
    now: Long,
    reason: String,
    deviceId: String,
    devicePublicKey: String,
  ) {
    if (legacyPolicy == LegacyContactPolicy.REJECT) deny(account, now, reason)
    requestRefresh(account, now)
    val epoch = registry?.epoch ?: UNKNOWN_EPOCH
    val auditKey = "$account:$epoch"
    val first = synchronized(lock) {
      if (auditedLegacy.size >= MAX_DECISIONS) auditedLegacy.clear()
      auditedLegacy.add(auditKey)
    }
    if (first) warn("Contacto admitido por política legacy: $reason")
    remember(
      account,
      Decision(epoch, decisionExpiry(registry?.expiresAt ?: now + DECISION_TTL_MS, now), deviceId, devicePublicKey),
    )
  }

  private fun deny(account: String, now: Long, reason: String): Nothing {
    synchronized(lock) { decisions.remove(account) }
    requestRefresh(account, now)
    throw ContactAdmissionException(reason)
  }

  private fun requestRefresh(account: String, now: Long) {
    val allowed = synchronized(lock) {
      val previous = lastRefresh[account]
      if (previous != null && now - previous < REFRESH_INTERVAL_MS) {
        false
      } else {
        if (lastRefresh.size >= MAX_DECISIONS) lastRefresh.remove(lastRefresh.keys.first())
        lastRefresh[account] = now
        true
      }
    }
    if (!allowed) return
    runCatching { refresh(account) }.onFailure { cause ->
      warn("No se pudo refrescar la admisión del contacto: ${message(cause)}")
    }
  }

  private fun remember(account: String, decision: Decision) {
    synchronized(lock) {
      if (!decisions.containsKey(account) && decisions.size >= MAX_DECISIONS) {
        val oldest = decisions.keys.firstOrNull()
        if (oldest != null) decisions.remove(oldest)
      }
      decisions[account] = decision
    }
  }

  private fun decisionExpiry(limit: Long, now: Long): Long = minOf(now + DECISION_TTL_MS, limit)

  private fun message(cause: Throwable): String = (cause.message ?: cause.javaClass.simpleName).take(240)

  private companion object {
    private const val TAG = "UPeerTransport"
    private const val ACCOUNT_BYTES = 32
    private const val UNKNOWN_EPOCH = -1L
    private const val DECISION_TTL_MS = 60_000L
    private const val REFRESH_INTERVAL_MS = 5_000L
    private const val MAX_DECISIONS = 256
  }
}
