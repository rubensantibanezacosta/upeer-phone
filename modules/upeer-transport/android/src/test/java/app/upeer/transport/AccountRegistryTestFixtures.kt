package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject

internal class FakeAccountRegistrySigner(override val accountUpeerId: String) : AccountRegistrySigner {
  var buildCount = 0
  var revokeCount = 0
  var lastEpoch = 0L

  override fun build(certificatesJSON: String, revokedJSON: String, epoch: Long, issuedAt: Long, ttlMillis: Long): String {
    buildCount += 1
    lastEpoch = epoch
    val certificates = JSONArray(certificatesJSON)
    val devices = JSONArray()
    for (index in 0 until certificates.length()) {
      val certificate = JSONObject(certificates.getString(index))
      devices.put(
        JSONObject()
          .put("deviceId", certificate.getString("deviceId"))
          .put("devicePublicKey", certificate.optString("devicePublicKey"))
          .put("staticX25519PublicKey", certificate.optString("staticX25519PublicKey"))
          .put("platform", certificate.optString("platform"))
          .put("capabilities", certificate.optJSONArray("capabilities") ?: JSONArray())
          .put("authorizedAt", certificate.getLong("issuedAt"))
          .put("expiresAt", certificate.getLong("expiresAt"))
          .put("epoch", certificate.getLong("epoch")),
      )
    }
    return registry(
      certificate = certificates.optJSONObject(0),
      epoch = epoch,
      issuedAt = issuedAt,
      expiresAt = issuedAt + ttlMillis,
      devices = devices,
      revoked = JSONArray(revokedJSON),
    )
  }

  override fun revoke(previousRegistryJSON: String, deviceId: String, reason: String, nowMillis: Long): String {
    revokeCount += 1
    val previous = JSONObject(previousRegistryJSON)
    val devices = JSONArray()
    val previousDevices = previous.getJSONArray("devices")
    var found = false
    for (index in 0 until previousDevices.length()) {
      val device = previousDevices.getJSONObject(index)
      if (device.getString("deviceId") == deviceId) found = true else devices.put(device)
    }
    require(found) { "device is not authorized" }
    val revoked = JSONArray(previous.getJSONArray("revoked").toString())
      .put(JSONObject().put("deviceId", deviceId).put("revokedAt", nowMillis).put("reason", reason))
    return registry(
      certificate = null,
      accountUpeerId = previous.getString("accountUpeerId"),
      accountPublicKey = previous.getString("accountPublicKey"),
      epoch = previous.getLong("epoch") + 1,
      issuedAt = maxOf(previous.getLong("issuedAt"), nowMillis),
      expiresAt = nowMillis + DEFAULT_TTL,
      devices = devices,
      revoked = revoked,
    )
  }

  override fun validateAdmission(
    certificateJSON: String,
    registryJSON: String,
    expectedAccountUpeerId: String,
    nowMillis: Long,
  ): Boolean {
    val certificate = JSONObject(certificateJSON)
    val registry = JSONObject(registryJSON)
    if (certificate.getString("accountUpeerId") != accountUpeerId) return false
    if (registry.getString("accountUpeerId") != expectedAccountUpeerId) return false
    if (certificate.getLong("epoch") > registry.getLong("epoch")) return false
    if (registry.getLong("expiresAt") <= nowMillis) return false
    val deviceId = certificate.getString("deviceId")
    val revoked = registry.getJSONArray("revoked")
    for (index in 0 until revoked.length()) {
      if (revoked.getJSONObject(index).getString("deviceId") == deviceId) return false
    }
    val devices = registry.getJSONArray("devices")
    for (index in 0 until devices.length()) {
      val device = devices.getJSONObject(index)
      if (device.getString("deviceId") != deviceId) continue
      return device.getLong("authorizedAt") == certificate.getLong("issuedAt") &&
        device.getLong("expiresAt") == certificate.getLong("expiresAt") &&
        device.getLong("epoch") == certificate.getLong("epoch")
    }
    return false
  }

  private fun registry(
    certificate: JSONObject?,
    accountUpeerId: String = certificate?.getString("accountUpeerId") ?: this.accountUpeerId,
    accountPublicKey: String = certificate?.optString("accountPublicKey").orEmpty(),
    epoch: Long,
    issuedAt: Long,
    expiresAt: Long,
    devices: JSONArray,
    revoked: JSONArray,
  ): String = JSONObject()
    .put("version", 1)
    .put("accountUpeerId", accountUpeerId)
    .put("accountPublicKey", accountPublicKey)
    .put("epoch", epoch)
    .put("issuedAt", issuedAt)
    .put("expiresAt", expiresAt)
    .put("devices", devices)
    .put("revoked", revoked)
    .put("signature", "00".repeat(64))
    .toString()

  companion object {
    const val DEFAULT_TTL = 24L * 60L * 60L * 1000L
  }
}

internal class MemoryAccountRegistryPersistence : AccountRegistryPersistence {
  var value: ByteArray? = null
    private set
  var saves = 0
    private set

  override fun load(): ByteArray? = value?.copyOf()

  override fun save(value: ByteArray) {
    this.value = value.copyOf()
    saves += 1
  }

  override fun clear() {
    value = null
  }
}

internal fun testCertificate(
  accountUpeerId: String,
  deviceId: String,
  issuedAt: Long,
  expiresAt: Long,
  epoch: Long = 1,
): String = JSONObject()
  .put("version", 1)
  .put("accountUpeerId", accountUpeerId)
  .put("accountPublicKey", "11".repeat(32))
  .put("deviceId", deviceId)
  .put("devicePublicKey", "22".repeat(32))
  .put("platform", "android")
  .put("capabilities", JSONArray().put("chat"))
  .put("issuedAt", issuedAt)
  .put("expiresAt", expiresAt)
  .put("epoch", epoch)
  .put("signature", "33".repeat(64))
  .toString()

internal fun registryStateJSON(accountUpeerId: String, certificates: List<String>, registryJSON: String): String = JSONObject()
  .put("version", 1)
  .put("accountUpeerId", accountUpeerId)
  .put("certificates", JSONArray(certificates))
  .put("registry", JSONObject(registryJSON))
  .toString()
