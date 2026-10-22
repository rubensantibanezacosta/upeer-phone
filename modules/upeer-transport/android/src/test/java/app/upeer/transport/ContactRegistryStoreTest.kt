package app.upeer.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRegistryStoreTest {
  private val account = "ab".repeat(32)
  private val otherAccount = "bc".repeat(32)
  private val deviceA = "cd".repeat(32)
  private val deviceB = "ef".repeat(32)
  private val now = 1_800_000_000_000L
  private val certificateA = testCertificate(account, deviceA, now - 1_000, now + NINETY_DAYS)
  private val certificateB = testCertificate(account, deviceB, now - 1_000, now + NINETY_DAYS)

  @Test
  fun adoptsPersistsAndRestoresTheRegistry() {
    val persistence = MemoryContactRegistryPersistence()
    val signer = FakeAccountRegistrySigner(account)
    val registry = signer.build(JSONArray(listOf(certificateA)).toString(), "[]", 3, now, FakeAccountRegistrySigner.DEFAULT_TTL)
    val store = store(persistence, signer)

    val snapshot = store.adopt(registry)

    assertEquals(account, snapshot.accountUpeerId)
    assertEquals(3L, snapshot.epoch)
    assertEquals(1, snapshot.devices)
    assertEquals(registry, store.current(account)?.registryJSON)
    assertTrue(JSONObject(store.statusJSON()).getBoolean("ready"))

    val restarted = store(MemoryContactRegistryPersistence(persistence.value), signer)
    assertEquals(registry, restarted.current(account)?.registryJSON)
  }

  @Test
  fun rejectsEpochRollbackAndChangesWithoutANewEpoch() {
    val signer = FakeAccountRegistrySigner(account)
    val store = store(MemoryContactRegistryPersistence(), signer)
    val epochTwo = signer.build(JSONArray(listOf(certificateA)).toString(), "[]", 2, now, FakeAccountRegistrySigner.DEFAULT_TTL)
    val epochThree = signer.build(JSONArray(listOf(certificateA)).toString(), "[]", 3, now, FakeAccountRegistrySigner.DEFAULT_TTL)
    val epochThreeChanged = signer.build(JSONArray(listOf(certificateA, certificateB)).toString(), "[]", 3, now, FakeAccountRegistrySigner.DEFAULT_TTL)
    store.adopt(epochThree)

    assertThrows(IllegalArgumentException::class.java) { store.adopt(epochTwo) }
    assertThrows(IllegalArgumentException::class.java) { store.adopt(epochThreeChanged) }
    assertEquals(epochThree, store.current(account)?.registryJSON)
  }

  @Test
  fun requiresAValidMonotonicUpdateForANewEpoch() {
    val signer = FakeAccountRegistrySigner(account)
    var updates = 0
    val store = store(MemoryContactRegistryPersistence(), signer, verifyUpdate = { _, _, _, _ -> updates += 1; false })
    store.adopt(signer.build(JSONArray(listOf(certificateA)).toString(), "[]", 1, now, FakeAccountRegistrySigner.DEFAULT_TTL))

    assertThrows(IllegalArgumentException::class.java) {
      store.adopt(signer.build(JSONArray(listOf(certificateA)).toString(), "[]", 2, now, FakeAccountRegistrySigner.DEFAULT_TTL))
    }
    assertEquals(1, updates)
  }

  @Test
  fun admitsAuthorizedDevicesAndRejectsRevokedOnes() {
    val signer = FakeAccountRegistrySigner(account)
    val store = store(MemoryContactRegistryPersistence(), signer)
    store.adopt(signer.build(JSONArray(listOf(certificateA)).toString(), "[]", 4, now, FakeAccountRegistrySigner.DEFAULT_TTL))

    assertTrue(store.admits(certificateA, account) == true)
    assertFalse(store.admits(certificateB, account) == true)
    assertNull(store.admits(certificateA, otherAccount))
  }

  @Test
  fun returnsNullWhenTheRegistryExpired() {
    val signer = FakeAccountRegistrySigner(account)
    val persistence = MemoryContactRegistryPersistence()
    val store = store(persistence, signer)
    store.adopt(signer.build(JSONArray(listOf(certificateA)).toString(), "[]", 1, now, FakeAccountRegistrySigner.DEFAULT_TTL))

    val expired = store(persistence, signer, now = { now + FakeAccountRegistrySigner.DEFAULT_TTL + 1 })
    assertNull(expired.admits(certificateA, account))
  }

  @Test
  fun rejectsAnInvalidRegistry() {
    val store = store(MemoryContactRegistryPersistence(), FakeAccountRegistrySigner(account))

    assertThrows(IllegalArgumentException::class.java) { store.adopt("") }
    assertThrows(IllegalArgumentException::class.java) { store.adopt("{}") }
    assertThrows(IllegalArgumentException::class.java) { store.adopt("x".repeat(10_001)) }
  }

  private fun store(
    persistence: MemoryContactRegistryPersistence,
    signer: FakeAccountRegistrySigner,
    verifyUpdate: (String, String, String, Long) -> Boolean = { _, _, _, _ -> true },
    now: () -> Long = { this.now },
  ): ContactRegistryStore = ContactRegistryStore(
    persistence,
    now,
    verifyRegistry = { registry, expected, at ->
      JSONObject(registry).getString("accountUpeerId") == expected &&
        JSONObject(registry).getLong("expiresAt") > at
    },
    verifyUpdate = verifyUpdate,
    verifyAdmission = signer::validateAdmission,
  )

  private companion object {
    private const val NINETY_DAYS = 90L * 24L * 60L * 60L * 1000L
  }
}

internal class MemoryContactRegistryPersistence(var value: ByteArray? = null) : ContactRegistryPersistence {
  override fun load(): ByteArray? = value?.copyOf()

  override fun save(value: ByteArray) {
    this.value = value.copyOf()
  }

  override fun clear() {
    value = null
  }
}
