package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRegistryStoreTest {
  private val account = "ab".repeat(32)
  private val accountB = "bc".repeat(32)
  private val deviceA = "cd".repeat(32)
  private val deviceB = "ef".repeat(32)
  private val now = 1_800_000_000_000L
  private val certificateA = testCertificate(account, deviceA, now - 1_000, now + NINETY_DAYS)
  private val certificateB = testCertificate(account, deviceB, now - 1_000, now + NINETY_DAYS)

  @Test
  fun buildsPersistsAndReusesTheFirstRegistry() {
    val persistence = MemoryAccountRegistryPersistence()
    val signer = FakeAccountRegistrySigner(account)
    val store = AccountRegistryStore(persistence, { now })

    val snapshot = store.ensure(account, certificateA, signer)

    assertEquals(1L, snapshot.epoch)
    assertTrue(snapshot.localDeviceAdmitted)
    assertEquals(1, snapshot.deviceCount)
    assertEquals(1, signer.buildCount)

    val status = JSONObject(store.statusJSON(account, deviceA))
    assertTrue(status.getBoolean("ready"))
    assertTrue(status.getBoolean("localDeviceAdmitted"))
    assertEquals(1, status.getInt("devices"))

    val repeated = store.ensure(account, certificateA, signer)
    assertEquals(1, signer.buildCount)
    assertEquals(repeated.registryJSON, store.currentRegistryJSON())
    assertTrue(JSONObject(store.statusJSON(account, deviceA)).getBoolean("ready"))

    val restarted = AccountRegistryStore(persistence, { now })
    val restored = restarted.ensure(account, certificateA, signer)
    assertEquals(1L, restored.epoch)
    assertTrue(restored.localDeviceAdmitted)
    assertEquals(1, signer.buildCount)
  }

  @Test
  fun renewsTheRegistryWhenTheDeviceCertificateChanges() {
    val persistence = MemoryAccountRegistryPersistence()
    val signer = FakeAccountRegistrySigner(account)
    val store = AccountRegistryStore(persistence, { now })
    store.ensure(account, certificateA, signer)
    val renewed = testCertificate(account, deviceA, now - 500, now + NINETY_DAYS)

    val snapshot = store.ensure(account, renewed, signer)

    assertEquals(2L, snapshot.epoch)
    assertTrue(snapshot.localDeviceAdmitted)
    assertEquals(2, signer.buildCount)
    assertEquals(renewed, snapshot.certificates.single())
  }

  @Test
  fun refreshesTheRegistryBeforeItsTtlExpires() {
    var clock = now
    val persistence = MemoryAccountRegistryPersistence()
    val signer = FakeAccountRegistrySigner(account)
    val store = AccountRegistryStore(persistence, { clock })
    store.ensure(account, certificateA, signer)
    clock = now + FakeAccountRegistrySigner.DEFAULT_TTL - 30L * 60L * 1000L

    val snapshot = store.ensure(account, certificateA, signer)

    assertEquals(2L, snapshot.epoch)
    assertTrue(snapshot.localDeviceAdmitted)
    assertEquals(2, signer.buildCount)
  }

  @Test
  fun revokingTheLocalDeviceKeepsTheRegistryAndBlocksAdmissionAfterRestart() {
    val persistence = MemoryAccountRegistryPersistence()
    val signer = FakeAccountRegistrySigner(account)
    val store = AccountRegistryStore(persistence, { now })
    store.ensure(account, certificateA, signer)

    val revoked = store.revoke(account, certificateA, deviceA, "lost", signer)

    assertEquals(2L, revoked.epoch)
    assertEquals(1, revoked.revoked.size)
    assertEquals(0, revoked.deviceCount)
    assertFalse(revoked.localDeviceAdmitted)
    assertEquals(1, signer.revokeCount)
    assertEquals(1, signer.buildCount)

    val restarted = AccountRegistryStore(persistence, { now })
    val restored = restarted.ensure(account, certificateA, signer)
    assertFalse(restored.localDeviceAdmitted)
    assertEquals(1, restored.revoked.size)
    assertEquals(1, signer.buildCount)
  }

  @Test
  fun revokingAnotherDeviceKeepsTheLocalDeviceAuthorized() {
    val signer = FakeAccountRegistrySigner(account)
    val registryJSON = signer.build(JSONArray(listOf(certificateA, certificateB)).toString(), "[]", 1, now, FakeAccountRegistrySigner.DEFAULT_TTL)
    val persistence = MemoryAccountRegistryPersistence()
    persistence.save(registryStateJSON(account, listOf(certificateA, certificateB), registryJSON).toByteArray())
    val store = AccountRegistryStore(persistence, { now })
    store.ensure(account, certificateA, signer)

    val revoked = store.revoke(account, certificateA, deviceB, "retired", signer)

    assertEquals(2L, revoked.epoch)
    assertEquals(1, revoked.deviceCount)
    assertEquals(1, revoked.revoked.size)
    assertTrue(revoked.localDeviceAdmitted)
    assertEquals(listOf(certificateA), revoked.certificates)
  }

  @Test
  fun changingTheAccountDiscardsThePreviousRegistry() {
    val persistence = MemoryAccountRegistryPersistence()
    val store = AccountRegistryStore(persistence, { now })
    store.ensure(account, certificateA, FakeAccountRegistrySigner(account))
    val otherCertificate = testCertificate(accountB, deviceB, now - 1_000, now + NINETY_DAYS)
    val signerB = FakeAccountRegistrySigner(accountB)

    val snapshot = AccountRegistryStore(persistence, { now }).ensure(accountB, otherCertificate, signerB)

    assertEquals(1L, snapshot.epoch)
    assertEquals(accountB, snapshot.accountUpeerId)
    assertEquals(1, snapshot.deviceCount)
    assertEquals(listOf(otherCertificate), snapshot.certificates)
  }

  @Test
  fun statusWithoutAnEnsuredRegistryIsNotReady() {
    val store = AccountRegistryStore(MemoryAccountRegistryPersistence(), { now })

    val status = JSONObject(store.statusJSON(account, deviceA))

    assertFalse(status.getBoolean("ready"))
  }

  @Test
  fun exposesTheCachedAdmissionOfTheLocalDevice() {
    val store = AccountRegistryStore(MemoryAccountRegistryPersistence(), { now })
    val signer = FakeAccountRegistrySigner(account)
    assertEquals(null, store.currentAdmission(account, deviceA))

    store.ensure(account, certificateA, signer)
    assertTrue(store.currentAdmission(account, deviceA) == true)
    assertEquals(null, store.currentAdmission(accountB, deviceA))
    assertEquals(null, store.currentAdmission(account, deviceB))

    store.revoke(account, certificateA, deviceA, "lost", signer)
    assertEquals(false, store.currentAdmission(account, deviceA))
  }

  @Test
  fun adoptsAnUpdatedRegistryAndDetectsItsOwnRevocation() {
    val signer = FakeAccountRegistrySigner(account)
    val store = AccountRegistryStore(
      MemoryAccountRegistryPersistence(),
      { now },
      verifyRegistry = { _, _, _ -> true },
      verifyAdmission = { certificate, registry, expected, at -> signer.validateAdmission(certificate, registry, expected, at) },
    )
    store.ensure(account, certificateA, signer)
    val revoked = signer.revoke(store.currentRegistryJSON(), deviceA, "revocado por el propietario", now)

    val snapshot = store.adopt(account, revoked, certificateA)

    assertTrue(snapshot != null)
    assertEquals(2L, snapshot!!.epoch)
    assertFalse(snapshot.localDeviceAdmitted)
    assertEquals(listOf(deviceA), snapshot.revoked)
    assertEquals(false, store.currentAdmission(account, deviceA))
    assertFalse(JSONObject(store.statusJSON(account, deviceA)).getBoolean("localDeviceAdmitted"))
  }

  @Test
  fun ignoresAnOlderRegistryAndKeepsTheCurrentOne() {
    val signer = FakeAccountRegistrySigner(account)
    val store = AccountRegistryStore(
      MemoryAccountRegistryPersistence(),
      { now },
      verifyRegistry = { _, _, _ -> true },
      verifyAdmission = { certificate, registry, expected, at -> signer.validateAdmission(certificate, registry, expected, at) },
    )
    store.ensure(account, certificateA, signer)
    val current = store.currentRegistryJSON()
    val older = JSONObject(current).put("epoch", 0L).put("issuedAt", now - 5_000).toString()

    val snapshot = store.adopt(account, older, certificateA)

    assertEquals(1L, snapshot?.epoch)
    assertEquals(current, store.currentRegistryJSON())
  }

  @Test
  fun rejectsARegistryThatDoesNotVerify() {
    val signer = FakeAccountRegistrySigner(account)
    val store = AccountRegistryStore(
      MemoryAccountRegistryPersistence(),
      { now },
      verifyRegistry = { _, _, _ -> false },
      verifyAdmission = { certificate, registry, expected, at -> signer.validateAdmission(certificate, registry, expected, at) },
    )
    store.ensure(account, certificateA, signer)
    val revoked = signer.revoke(store.currentRegistryJSON(), deviceA, "revocado", now)

    assertEquals(null, store.adopt(account, revoked, certificateA))
    assertTrue(store.currentAdmission(account, deviceA) == true)
  }

  private companion object {
    private const val NINETY_DAYS = 90L * 24L * 60L * 60L * 1000L
  }
}
