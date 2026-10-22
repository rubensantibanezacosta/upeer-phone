package app.upeer.transport.dht

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DhtContactLocationTest {
  private val account = "ab".repeat(32)
  private val deviceA = "cd".repeat(32)
  private val deviceB = "ef".repeat(32)
  private val publicKey = "11".repeat(32)
  private val now = 1_800_000_000_000L

  @Test
  fun selectsTheLatestAdmittedDeviceBlock() {
    val older = block(account, deviceA, 3)
    val newer = block(account, deviceB, 9)
    val verified = DhtContactLocation.select(
      JSONArray().put(older).put(newer),
      account,
      publicKey,
      "{}",
      now,
    ) { _, _, registry, _ -> registry == "{}" }

    assertEquals(9L, verified?.dhtSeq)
    assertEquals(deviceB, verified?.deviceId)
    assertEquals(account, JSONObject(verified!!.certificateJSON).getString("accountUpeerId"))
  }

  @Test
  fun rejectsBlocksWithoutADeviceCertificate() {
    val block = block(account, deviceA, 1).apply { remove("deviceCertificate") }

    assertNull(DhtContactLocation.select(block, account, publicKey, "", now) { _, _, _, _ -> true })
  }

  @Test
  fun rejectsCertificatesForAnotherAccountOrDevice() {
    val otherAccount = block("bc".repeat(32), deviceA, 1)
    val otherDevice = block(account, deviceB, 1)
      .put("deviceCertificate", JSONObject().put("accountUpeerId", account).put("deviceId", deviceA))

    assertNull(DhtContactLocation.select(otherAccount, account, publicKey, "", now) { _, _, _, _ -> true })
    assertNull(DhtContactLocation.select(otherDevice, account, publicKey, "", now) { _, _, _, _ -> true })
  }

  @Test
  fun rejectsExpiredOrUnverifiableBlocks() {
    val expired = block(account, deviceA, 1, expiresAt = now)
    val unverifiable = block(account, deviceA, 1)

    assertNull(DhtContactLocation.select(expired, account, publicKey, "", now) { _, _, _, _ -> true })
    assertNull(DhtContactLocation.select(unverifiable, account, publicKey, "", now) { _, _, _, _ -> false })
  }

  @Test
  fun ignoresBlocksThatCannotBeParsed() {
    val verified = DhtContactLocation.select("not-json", account, publicKey, "", now) { _, _, _, _ -> true }

    assertNull(verified)
  }

  @Test
  fun acceptsASingleBlockObject() {
    val verified = DhtContactLocation.verifyOne(block(account, deviceA, 4).toString(), account, publicKey, "", now) { _, _, _, _ -> true }

    assertTrue(verified?.addresses?.single() == "200::1")
  }

  private fun block(accountUpeerId: String, deviceId: String, dhtSeq: Long, expiresAt: Long = now + 86_400_000L): JSONObject = JSONObject()
    .put("address", "200::1")
    .put("addresses", JSONArray().put("200::1"))
    .put("dhtSeq", dhtSeq)
    .put("expiresAt", expiresAt)
    .put("signature", "aa".repeat(64))
    .put("deviceId", deviceId)
    .put("deviceCertificate", JSONObject().put("accountUpeerId", accountUpeerId).put("deviceId", deviceId))
}
