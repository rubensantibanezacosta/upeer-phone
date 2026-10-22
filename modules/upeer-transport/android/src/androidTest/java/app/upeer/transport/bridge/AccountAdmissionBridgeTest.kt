package app.upeer.transport.bridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.upeer.transportbridge.transportbridge.Transportbridge
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountAdmissionBridgeTest {
  private val now = 1_800_000_000_000L
  private val accountSecret by lazy { privateKey(ACCOUNT_SEED, ACCOUNT_PUBLIC_KEY) }
  private val deviceSecret by lazy { privateKey(DEVICE_SEED, DEVICE_PUBLIC_KEY) }
  private val account by lazy { Transportbridge.newCryptoIdentity(accountSecret) }
  private val device by lazy { Transportbridge.newCryptoIdentity(deviceSecret) }

  @Test
  fun pairingProducesAValidCertificateForTheAccountDevice() {
    assertEquals(ACCOUNT_ID, account.upeerID())
    assertEquals(ACCOUNT_PUBLIC_KEY, account.publicKeyHex())
    assertEquals(DEVICE_ID, Transportbridge.deriveDeviceID(hexToBytes(device.publicKeyHex())))

    val certificate = certificate(7, now)
    assertTrue(Transportbridge.verifyDeviceCertificate(certificate, account.upeerID(), now))
    val record = JSONObject(certificate)
    assertEquals(ACCOUNT_ID, record.getString("accountUpeerId"))
    assertEquals(DEVICE_ID, record.getString("deviceId"))
    assertEquals(DEVICE_PUBLIC_KEY, record.getString("devicePublicKey"))
    assertEquals(7, record.getLong("epoch"))
  }

  @Test
  fun deviceAndLegacyBlocksResolveAgainstTheSameAccount() {
    val certificate = certificate(7, now)
    val registry = registry(listOf(certificate), 7, now)
    val block = Transportbridge.buildDeviceLocationBlock(
      deviceSecret,
      certificate,
      JSONArray(listOf("200::1")).toString(),
      42,
      0,
      now,
    )

    assertTrue(Transportbridge.verifyAccountRegistry(registry, account.upeerID(), now))
    assertTrue(Transportbridge.validateDeviceAdmission(certificate, registry, account.upeerID(), now))
    assertTrue(Transportbridge.verifyDeviceLocationBlock(block, account.publicKeyHex(), registry, now))
    assertTrue(Transportbridge.verifyLocationBlock(block, account.publicKeyHex(), now))

    val tampered = JSONObject(block).put("dhtSeq", 43L).toString()
    assertFalse(
      runCatching {
        Transportbridge.verifyDeviceLocationBlock(tampered, account.publicKeyHex(), registry, now)
      }.getOrDefault(false),
    )

    val legacy = account.buildLocationBlock(
      JSONArray(listOf("200::2")).toString(),
      8,
      0,
      DEVICE_ID,
    )
    val legacyNow = System.currentTimeMillis()
    assertTrue(Transportbridge.verifyLocationBlock(legacy, account.publicKeyHex(), legacyNow))
    assertFalse(
      runCatching {
        Transportbridge.verifyDeviceLocationBlock(legacy, account.publicKeyHex(), registry, legacyNow)
      }.getOrDefault(false),
    )
  }

  @Test
  fun revocationAdvancesEpochAndRejectsTheCertificate() {
    val certificate = certificate(7, now)
    val current = registry(listOf(certificate), 7, now)
    val revokedAt = now + 1_000
    val revoked = Transportbridge.revokeAccountDevice(
      current,
      accountSecret,
      DEVICE_ID,
      "lost device",
      revokedAt,
    )

    assertTrue(Transportbridge.verifyAccountRegistry(revoked, account.upeerID(), revokedAt))
    val record = JSONObject(revoked)
    assertEquals(8, record.getLong("epoch"))
    assertEquals(0, record.getJSONArray("devices").length())
    assertEquals(DEVICE_ID, record.getJSONArray("revoked").getJSONObject(0).getString("deviceId"))
    assertTrue(
      runCatching {
        Transportbridge.validateDeviceAdmission(certificate, revoked, account.upeerID(), revokedAt)
      }.isFailure,
    )
    assertTrue(Transportbridge.validateDeviceAdmission(certificate, current, account.upeerID(), now))

    val refreshed = registry(listOf(certificate), 8, now)
    assertTrue(Transportbridge.validateDeviceAdmission(certificate, refreshed, account.upeerID(), now))
    assertEquals(8, Transportbridge.nextAccountRegistryEpoch(current))
  }

  @Test
  fun certificateAheadOfTheRegistryIsRejected() {
    val currentCertificate = certificate(7, now)
    val current = registry(listOf(currentCertificate), 7, now)
    val issuedAt = now + 1_000
    val aheadCertificate = certificate(9, issuedAt)

    assertTrue(Transportbridge.verifyDeviceCertificate(aheadCertificate, account.upeerID(), issuedAt))
    assertTrue(
      runCatching {
        Transportbridge.validateDeviceAdmission(aheadCertificate, current, account.upeerID(), issuedAt)
      }.isFailure,
    )

    val ahead = registry(listOf(aheadCertificate), 9, issuedAt)
    assertTrue(Transportbridge.validateDeviceAdmission(aheadCertificate, ahead, account.upeerID(), issuedAt))
  }

  private fun certificate(epoch: Long, issuedAt: Long): String {
    val options = JSONObject()
      .put("accountUpeerId", account.upeerID())
      .put("platform", "android")
      .put("capabilities", JSONArray(listOf("chat", "dht")))
      .put("nonce", bytesToHex(deviceSecret.copyOfRange(0, 16)))
      .put("createdAt", issuedAt)
    val request = Transportbridge.buildDevicePairingRequest(deviceSecret, options.toString())
    assertTrue(Transportbridge.verifyDevicePairingRequest(request, account.upeerID(), issuedAt))
    return Transportbridge.approveDevicePairingRequest(
      accountSecret,
      request,
      epoch,
      issuedAt,
      issuedAt + CERTIFICATE_TTL_MS,
    )
  }

  private fun registry(certificates: List<String>, epoch: Long, issuedAt: Long): String =
    Transportbridge.buildAccountRegistryFromCertificates(
      accountSecret,
      JSONArray(certificates).toString(),
      "[]",
      epoch,
      issuedAt,
      REGISTRY_TTL_MS,
    )

  private fun privateKey(seed: Byte, publicKey: String): ByteArray =
    ByteArray(32) { seed } + hexToBytes(publicKey)

  private fun hexToBytes(value: String): ByteArray {
    require(value.length % 2 == 0)
    return ByteArray(value.length / 2) { index ->
      val high = Character.digit(value[index * 2], 16)
      val low = Character.digit(value[index * 2 + 1], 16)
      require(high >= 0 && low >= 0)
      ((high shl 4) or low).toByte()
    }
  }

  private fun bytesToHex(value: ByteArray): String =
    value.joinToString("") { byte -> "%02x".format(byte) }

  private companion object {
    const val ACCOUNT_SEED: Byte = 0x07
    const val DEVICE_SEED: Byte = 0x08
    const val ACCOUNT_PUBLIC_KEY = "ea4a6c63e29c520abef5507b132ec5f9954776aebebe7b92421eea691446d22c"
    const val ACCOUNT_ID = "8d2d1c260127c74476b27136c5e38c003b66b889f5c80032fb81ebc3f44f45a3"
    const val DEVICE_PUBLIC_KEY = "1398f62c6d1a457c51ba6a4b5f3dbd2f69fca93216218dc8997e416bd17d93ca"
    const val DEVICE_ID = "7e79092d522fc2b12e3bd0c202d3b842e57fecf4e3516afb9e16eb12e5da4875"
    const val CERTIFICATE_TTL_MS = 90L * 24 * 60 * 60 * 1000
    const val REGISTRY_TTL_MS = 24L * 60 * 60 * 1000
  }
}

