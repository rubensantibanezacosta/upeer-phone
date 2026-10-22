package app.upeer.transport.dht

import app.upeer.transport.AccountRegistrySigner
import app.upeer.transport.AccountRegistryStore
import app.upeer.transport.ContactRegistrySnapshot
import app.upeer.transport.MemoryAccountRegistryPersistence
import app.upeer.transport.crypto.Blake2b
import app.upeer.transport.registryStateJSON
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.HexFormat

class AccountDeviceVectorTest {
  private val fixture = JSONObject(fixtureFile().readText(Charsets.UTF_8))
  private val now = fixture.getLong("now")
  private val account = fixture.getString("accountUpeerId")
  private val accountPublicKey = fixture.getString("accountPublicKey")
  private val devicePublicKey = fixture.getString("devicePublicKey")
  private val deviceId = fixture.getString("deviceId")
  private val certificate = fixture.getJSONObject("certificate")
  private val registry = fixture.getJSONObject("registry")
  private val block = fixture.getJSONObject("locationBlock")
  private val accountPrivateKey = privateKey(fixture.getString("accountSecretKeySeed"))

  @Test
  fun derivesTheSameAccountDeviceAndRegistryKeys() {
    assertEquals(account, hashHex(HexFormat.of().parseHex(accountPublicKey)))
    assertEquals(deviceId, hashHex(HexFormat.of().parseHex(devicePublicKey)))
    assertEquals(
      fixture.getString("registryKey"),
      hashHex((REGISTRY_KEY_PREFIX + account).toByteArray(Charsets.UTF_8)),
    )
    assertTrue(verifySignature(canonical(certificate), certificate.getString("signature"), accountPublicKey))
  }

  @Test
  fun verifiesTheDesktopCertificateRegistryAndAdmission() {
    assertTrue(verifyCertificate(certificate, now))
    assertTrue(verifyRegistry(registry, now))
    assertTrue(validateAdmission(certificate, registry, account, now))
  }

  @Test
  fun admitsTheDesktopDeviceThroughTheContactAdmissionPath() {
    var refreshes = 0

    admission(block.toString(), registry.toString()) { refreshes += 1 }.requireAdmitted(account)

    assertEquals(0, refreshes)
  }

  @Test
  fun accountRegistryStoreRestoresAndAdmitsTheDesktopDevice() {
    val persistence = MemoryAccountRegistryPersistence()
    persistence.save(registryStateJSON(account, listOf(certificate.toString()), registry.toString()).toByteArray(Charsets.UTF_8))
    val store = AccountRegistryStore(persistence, { now })

    val snapshot = store.ensure(account, certificate.toString(), VectorSigner())

    assertTrue(snapshot.localDeviceAdmitted)
    assertEquals(account, snapshot.accountUpeerId)
    assertEquals(1, snapshot.deviceCount)
    assertEquals(now, snapshot.issuedAt)
  }

  @Test
  fun rejectsTamperedBlocksAndRevokedOrStaleRegistries() {
    val tampered = JSONObject(block.toString()).put("dhtSeq", 43L).toString()
    assertFalse(verifyBlock(tampered, registry.toString(), now))
    var refreshes = 0
    assertThrows(ContactAdmissionException::class.java) {
      admission(tampered, registry.toString()) { refreshes += 1 }.requireAdmitted(account)
    }
    assertEquals(1, refreshes)

    val revoked = signedRegistry(epoch = 10L, deviceEpoch = 9L, revoked = true)
    assertTrue(verifyRegistry(revoked, now))
    assertFalse(verifyBlock(block.toString(), revoked.toString(), now))
    assertFalse(validateAdmission(certificate, revoked, account, now))

    val stale = signedRegistry(epoch = 8L, deviceEpoch = 8L, revoked = false)
    assertTrue(verifyRegistry(stale, now))
    assertFalse(validateAdmission(certificate, stale, account, now))
  }

  private fun admission(
    location: String,
    registryJSON: String,
    onRefresh: () -> Unit,
  ): ContactEncryptionAdmission = ContactEncryptionAdmission(
    { accountPublicKey },
    { location },
    { snapshot(registryJSON) },
    { onRefresh() },
    LegacyContactPolicy.REJECT,
    { now },
    { blockJSON, _, registryValue, at -> verifyBlock(blockJSON, registryValue, at) },
  )

  private fun snapshot(registryJSON: String): ContactRegistrySnapshot {
    val value = JSONObject(registryJSON)
    val devices = value.optJSONArray("devices") ?: JSONArray()
    val revoked = value.optJSONArray("revoked") ?: JSONArray()
    return ContactRegistrySnapshot(
      accountUpeerId = account,
      registryJSON = registryJSON,
      epoch = value.getLong("epoch"),
      issuedAt = value.getLong("issuedAt"),
      expiresAt = value.getLong("expiresAt"),
      devices = devices.length(),
      revoked = (0 until revoked.length()).map { revoked.getJSONObject(it).getString("deviceId") }.toSet(),
      updatedAt = now,
    )
  }

  private fun verifyCertificate(value: JSONObject, at: Long): Boolean {
    if (value.optInt("version") != 1) return false
    if (!value.optString("accountUpeerId").equals(account, ignoreCase = true)) return false
    if (!value.optString("accountPublicKey").equals(accountPublicKey, ignoreCase = true)) return false
    if (!value.optString("deviceId").equals(deviceId, ignoreCase = true)) return false
    if (!value.optString("devicePublicKey").equals(devicePublicKey, ignoreCase = true)) return false
    val issuedAt = value.optLong("issuedAt")
    val expiresAt = value.optLong("expiresAt")
    if (issuedAt <= 0L || expiresAt <= issuedAt) return false
    if (expiresAt <= at || issuedAt > at + CLOCK_SKEW_MS) return false
    return verifySignature(canonical(value), value.optString("signature"), accountPublicKey)
  }

  private fun verifyRegistry(value: JSONObject, at: Long): Boolean {
    if (value.optInt("version") != 1) return false
    if (!value.optString("accountUpeerId").equals(account, ignoreCase = true)) return false
    if (!value.optString("accountPublicKey").equals(accountPublicKey, ignoreCase = true)) return false
    val issuedAt = value.optLong("issuedAt")
    val expiresAt = value.optLong("expiresAt")
    if (issuedAt <= 0L || expiresAt <= issuedAt) return false
    if (expiresAt <= at || issuedAt > at + CLOCK_SKEW_MS) return false
    return verifySignature(canonical(value), value.optString("signature"), accountPublicKey)
  }

  private fun validateAdmission(
    certificateValue: JSONObject,
    registryValue: JSONObject,
    expectedAccount: String,
    at: Long,
  ): Boolean {
    if (!expectedAccount.equals(account, ignoreCase = true)) return false
    if (!verifyCertificate(certificateValue, at) || !verifyRegistry(registryValue, at)) return false
    if (!registryValue.optString("accountPublicKey").equals(certificateValue.optString("accountPublicKey"), ignoreCase = true)) return false
    if (certificateValue.optLong("epoch") > registryValue.optLong("epoch")) return false
    if (certificateValue.optLong("issuedAt") > registryValue.optLong("issuedAt")) return false
    val revoked = registryValue.optJSONArray("revoked") ?: JSONArray()
    for (index in 0 until revoked.length()) {
      if (revoked.getJSONObject(index).optString("deviceId").equals(certificateValue.optString("deviceId"), ignoreCase = true)) return false
    }
    val devices = registryValue.optJSONArray("devices") ?: return false
    for (index in 0 until devices.length()) {
      val device = devices.getJSONObject(index)
      if (!device.optString("deviceId").equals(certificateValue.optString("deviceId"), ignoreCase = true)) continue
      return device.optString("devicePublicKey").equals(certificateValue.optString("devicePublicKey"), ignoreCase = true) &&
        device.optString("staticX25519PublicKey").equals(certificateValue.optString("staticX25519PublicKey"), ignoreCase = true) &&
        device.optString("platform") == certificateValue.optString("platform") &&
        capabilities(device) == capabilities(certificateValue) &&
        device.optLong("authorizedAt") == certificateValue.optLong("issuedAt") &&
        device.optLong("expiresAt") == certificateValue.optLong("expiresAt") &&
        device.optLong("epoch") == certificateValue.optLong("epoch")
    }
    return false
  }

  private fun verifyBlock(blockJSON: String, registryJSON: String, at: Long): Boolean = runCatching {
    val parsed = JSONObject(blockJSON)
    val certificateValue = parsed.getJSONObject("deviceCertificate")
    if (!verifyCertificate(certificateValue, at)) return@runCatching false
    if (!validateAdmission(certificateValue, JSONObject(registryJSON), account, at)) return@runCatching false
    DhtLocation.verifyWith(blockJSON, accountPublicKey, account, at) { payload, signature, _ ->
      verifySignature(payload, signature, certificateValue.getString("devicePublicKey"))
    }
    true
  }.getOrDefault(false)


  private fun signedRegistry(epoch: Long, deviceEpoch: Long, revoked: Boolean): JSONObject {
    val value = JSONObject(registry.toString())
    value.put("epoch", epoch)
    value.getJSONArray("devices").getJSONObject(0).put("epoch", deviceEpoch)
    if (revoked) {
      value.put("devices", JSONArray())
      value.put("revoked", JSONArray().put(
        JSONObject()
          .put("deviceId", deviceId)
          .put("revokedAt", now)
          .put("reason", "lost"),
      ))
    }
    value.put("signature", signSignature(canonical(value)))
    return value
  }

  private fun capabilities(value: JSONObject): List<String> {
    val values = value.optJSONArray("capabilities") ?: return emptyList()
    return (0 until values.length()).map(values::getString)
  }

  private fun canonical(value: JSONObject): String = DhtCanonicalJson.canonical(
    JSONObject(value.toString()).apply { remove("signature") },
  )

  private fun signSignature(payload: String): String {
    val signature = Signature.getInstance("Ed25519")
    signature.initSign(accountPrivateKey)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return HexFormat.of().formatHex(signature.sign())
  }

  private fun verifySignature(payload: String, signatureHex: String, publicKeyHex: String): Boolean {
    val key = KeyFactory.getInstance("Ed25519")
      .generatePublic(X509EncodedKeySpec(ED25519_PUBLIC_PREFIX + HexFormat.of().parseHex(publicKeyHex)))
    val signature = Signature.getInstance("Ed25519")
    signature.initVerify(key)
    signature.update(payload.toByteArray(Charsets.UTF_8))
    return signature.verify(HexFormat.of().parseHex(signatureHex))
  }

  private fun hashHex(value: ByteArray): String = HexFormat.of().formatHex(Blake2b.hash256(value))

  private fun privateKey(seedHex: String): PrivateKey = KeyFactory.getInstance("Ed25519")
    .generatePrivate(PKCS8EncodedKeySpec(ED25519_PRIVATE_PREFIX + HexFormat.of().parseHex(seedHex)))

  private fun fixtureFile(): File {
    val candidates = listOf(
      File("../../../../transport-bridge/testdata/account_device_vectors.json"),
      File("../../../transport-bridge/testdata/account_device_vectors.json"),
      File("../transport-bridge/testdata/account_device_vectors.json"),
      File("transport-bridge/testdata/account_device_vectors.json"),
    )
    return candidates.firstOrNull(File::exists)
      ?: error("No se encontró transport-bridge/testdata/account_device_vectors.json")
  }

  private inner class VectorSigner : AccountRegistrySigner {
    override val accountUpeerId: String
      get() = account

    override fun build(
      certificatesJSON: String,
      revokedJSON: String,
      epoch: Long,
      issuedAt: Long,
      ttlMillis: Long,
    ): String = throw UnsupportedOperationException("el vector ya contiene el registro firmado")

    override fun revoke(
      previousRegistryJSON: String,
      deviceId: String,
      reason: String,
      nowMillis: Long,
    ): String = throw UnsupportedOperationException("el vector ya contiene el registro firmado")

    override fun validateAdmission(
      certificateJSON: String,
      registryJSON: String,
      expectedAccountUpeerId: String,
      nowMillis: Long,
    ): Boolean = validateAdmission(JSONObject(certificateJSON), JSONObject(registryJSON), expectedAccountUpeerId, nowMillis)
  }

  private companion object {
    const val REGISTRY_KEY_PREFIX = "upeer-account-registry:"
    const val CLOCK_SKEW_MS = 5L * 60L * 1000L
    val ED25519_PRIVATE_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420")
    val ED25519_PUBLIC_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100")
  }
}

