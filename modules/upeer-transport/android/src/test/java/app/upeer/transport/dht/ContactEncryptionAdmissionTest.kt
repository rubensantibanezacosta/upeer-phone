package app.upeer.transport.dht

import app.upeer.transport.ContactRegistrySnapshot
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEncryptionAdmissionTest {
  private val account = "ab".repeat(32)
  private val device = "cd".repeat(32)
  private val devicePublicKey = "ef".repeat(32)
  private val publicKey = "11".repeat(32)
  private val now = 1_800_000_000_000L

  private var registry: ContactRegistrySnapshot? = snapshot()
  private var location: String? = block().toString()
  private var verifyResult = true
  private var locationCalls = 0
  private var refreshCalls = 0
  private val warnings = mutableListOf<String>()

  @Test
  fun admitsVerifiedDeviceAndCachesDecision() {
    val admission = admission()

    admission.requireAdmitted(account)
    admission.requireAdmitted(account)

    assertEquals(1, locationCalls)
    assertEquals(0, refreshCalls)
  }

  @Test
  fun rejectsRevokedDeviceAndRequestsRefresh() {
    registry = snapshot(revoked = setOf(device))
    val admission = admission()

    assertThrows(ContactAdmissionException::class.java) { admission.requireAdmitted(account) }
    assertEquals(1, refreshCalls)
  }

  @Test
  fun rejectsCertificateThatDoesNotMatchTheCurrentRegistry() {
    verifyResult = false
    val admission = admission()

    assertThrows(ContactAdmissionException::class.java) { admission.requireAdmitted(account) }
    assertEquals(1, refreshCalls)
  }

  @Test
  fun rejectsMissingRegistryAndRequestsRefreshWithoutReadingLocation() {
    registry = null
    val admission = admission()

    assertThrows(ContactAdmissionException::class.java) { admission.requireAdmitted(account) }
    assertEquals(0, locationCalls)
    assertEquals(1, refreshCalls)
  }

  @Test
  fun allowsMissingRegistryWithAuditWhenCompatibilityIsEnabled() {
    registry = null
    val admission = admission(LegacyContactPolicy.ALLOW_WITH_AUDIT)

    admission.requireAdmitted(account)
    admission.requireAdmitted(account)

    assertEquals(1, warnings.size)
    assertEquals(1, refreshCalls)
  }

  @Test
  fun rejectsExpiredRegistry() {
    registry = snapshot(expiresAt = now)
    val admission = admission()

    assertThrows(ContactAdmissionException::class.java) { admission.requireAdmitted(account) }
    assertEquals(1, refreshCalls)
  }

  @Test
  fun rejectsLegacyLocationWhenDeviceAdmissionIsRequired() {
    location = block().apply { remove("deviceCertificate") }.toString()
    val admission = admission(LegacyContactPolicy.REJECT)

    assertThrows(ContactAdmissionException::class.java) { admission.requireAdmitted(account) }
    assertEquals(1, refreshCalls)
  }

  @Test
  fun allowsLegacyLocationWithAuditOnlyOncePerRegistryEpoch() {
    location = block().apply { remove("deviceCertificate") }.toString()
    val admission = admission(LegacyContactPolicy.ALLOW_WITH_AUDIT)

    admission.requireAdmitted(account)
    admission.requireAdmitted(account)

    assertEquals(1, warnings.size)
    assertTrue(warnings.single().contains("legacy"))
    assertEquals(1, locationCalls)
  }

  @Test
  fun rejectsAdmissionWhenTheRegistryEpochChangesDuringVerification() {
    var lookups = 0
    val admission = ContactEncryptionAdmission(
      { publicKey },
      { locationCalls += 1; location },
      { lookups += 1; if (lookups == 1) snapshot(epoch = 1) else snapshot(epoch = 2) },
      { refreshCalls += 1 },
      LegacyContactPolicy.REJECT,
      { now },
      { _, _, _, _ -> verifyResult },
      { message -> warnings += message },
    )

    assertThrows(ContactAdmissionException::class.java) { admission.requireAdmitted(account) }
    assertEquals(1, refreshCalls)
  }

  @Test
  fun revalidatesWhenTheRegistryEpochChanges() {
    val admission = admission()
    admission.requireAdmitted(account)
    registry = snapshot(epoch = 2)

    admission.requireAdmitted(account)

    assertEquals(2, locationCalls)
  }

  @Test
  fun admitsTheAdvertisedMessagingDeviceAndCachesByDevice() {
    val admission = admission()
    val expected = ExpectedContactDevice(device, devicePublicKey)

    admission.requireAdmitted(account)
    admission.requireAdmitted(account, expected)
    admission.requireAdmitted(account, expected)

    assertEquals(2, locationCalls)
  }

  @Test
  fun rejectsAMessagingDeviceThatDoesNotMatchTheCertificate() {
    val admission = admission()

    assertThrows(ContactAdmissionException::class.java) {
      admission.requireAdmitted(account, ExpectedContactDevice("12".repeat(32), devicePublicKey))
    }
    assertThrows(ContactAdmissionException::class.java) {
      admission.requireAdmitted(account, ExpectedContactDevice(device, "34".repeat(32)))
    }
  }

  @Test
  fun revalidatesExpectedDeviceAfterRegistryEpochChanges() {
    val admission = admission()
    admission.requireAdmitted(account, ExpectedContactDevice(device, devicePublicKey))
    registry = snapshot(epoch = 2)

    admission.requireAdmitted(account, ExpectedContactDevice(device, devicePublicKey))

    assertEquals(2, locationCalls)
  }

  private fun admission(policy: LegacyContactPolicy = LegacyContactPolicy.REJECT) = ContactEncryptionAdmission(
    { publicKey },
    { locationCalls += 1; location },
    { registry },
    { refreshCalls += 1 },
    policy,
    { now },
    { _, _, _, _ -> verifyResult },
    { message -> warnings += message },
  )

  private fun snapshot(
    epoch: Long = 1,
    expiresAt: Long = now + 86_400_000L,
    revoked: Set<String> = emptySet(),
  ) = ContactRegistrySnapshot(
    accountUpeerId = account,
    registryJSON = """{"epoch":$epoch}""",
    epoch = epoch,
    issuedAt = now - 60_000L,
    expiresAt = expiresAt,
    devices = 1,
    revoked = revoked,
    updatedAt = now,
  )

  private fun block(): JSONObject = JSONObject()
    .put("address", "200::1")
    .put("addresses", JSONArray().put("200::1"))
    .put("dhtSeq", 1L)
    .put("expiresAt", now + 86_400_000L)
    .put("signature", "aa".repeat(64))
    .put("deviceId", device)
    .put("deviceCertificate", JSONObject().put("accountUpeerId", account).put("deviceId", device).put("devicePublicKey", devicePublicKey))
}
